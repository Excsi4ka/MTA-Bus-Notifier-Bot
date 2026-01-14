package excsi.mtabot;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.regex.Pattern;

public class EventHandler extends ListenerAdapter {

    public static final String pattern = "^(0[1-9]|1[0-2]):[0-5][0-9](AM|PM)$";

    public static final String INSERT_SQL = "INSERT INTO UserSchedules (UserID, StopID, StartTime, EndTime) VALUES (?, ?, ?, ?)";

    public static final String QUERY_BY_USER = "SELECT * FROM UserSchedules WHERE UserID = ?";

    @Override
    public void onReady(ReadyEvent event) {
        JDA jda = event.getJDA();
        jda.getGuilds().forEach(guild -> {
            CommandListUpdateAction updateAction = guild.updateCommands();
            updateAction.addCommands(
                    Commands.slash("add-schedule",  "Adds a scheduled time for notifications")
                            .addOptions(new OptionData(OptionType.INTEGER,
                                    "bus-stop-id",
                                    "6 digit bus stop id",
                                    true)
                                    .setRequiredRange(500000, 600000))
                            .addOptions(new OptionData(OptionType.STRING,
                                    "start-time",
                                    "Specify when to start receiving notifications in the following format \"08:00AM\"",
                                    true)
                                    .setMaxLength(7)
                                    .setMinLength(7))
                            .addOptions(new OptionData(OptionType.STRING,
                                    "end-time",
                                    "Specify when to stop receiving notifications in the following format \"10:30AM\"",
                                    true)
                                    .setMaxLength(7)
                                    .setMinLength(7))
            ).queue();
        });
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (event.getName().equals("add-schedule")) {
            int stopId = event.getOption("bus-stop-id").getAsInt();
            String startTimeString = event.getOption("start-time").getAsString();
            String endTimeString = event.getOption("end-time").getAsString();
            long userId = event.getUser().getIdLong();
            if (!Pattern.matches(pattern, startTimeString) || !Pattern.matches(pattern, endTimeString)) {
                event.reply(":x: Invalid time format provided").setEphemeral(true).queue();
                return;
            }
            long startTime = TimeUtil.userTimeToSeconds(startTimeString);
            long endTime = TimeUtil.userTimeToSeconds(endTimeString);
            if (endTime <= startTime) {
                event.reply(":x: The time to stop receiving notifications must not be earlier than starting time").setEphemeral(true).queue();
            }

            DiscordBot.DB_WRITER.execute(() -> {
                try (PreparedStatement statement = DiscordBot.WRITER.prepareStatement(INSERT_SQL)) {
                    statement.setLong(1, userId);
                    statement.setInt(2, stopId);
                    statement.setLong(3,startTime);
                    statement.setLong(4, endTime);
                    int rowsAffected = statement.executeUpdate();

                    if (rowsAffected > 0) {
                        System.out.println("A new schedule was inserted successfully");
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            event.reply("Scheduled notification added :calendar_spiral:")
                    .setEphemeral(true)
                    .addActionRow(Button.primary("schedule", "View all your schedules")).queue();
        }
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        if (event.getButton().getId().equals("schedule")) {
            long userId = event.getUser().getIdLong();
            InteractionHook hook = event.getHook();
            event.deferReply(true).queue();
            DiscordBot.DB_READER.execute(() -> {
                try (PreparedStatement statement = DiscordBot.READER.prepareStatement(QUERY_BY_USER)) {
                    statement.setLong(1, userId);
                    ResultSet set = statement.executeQuery();
                    StringBuilder builder = new StringBuilder();
                    builder.append(">>> ");
                    while (set.next()) {
                        builder.append("Bus Stop ID: ").append(set.getInt("StopID")).append(" ")
                                .append("From: ").append( TimeUtil.timeToString(set.getLong("StartTime"))).append(" ")
                                .append("To: ").append(TimeUtil.timeToString(set.getLong("EndTime")))
                                .append("\n");
                    }
                    hook.sendMessage(builder.toString()).setEphemeral(true).queue();
                    set.close();
                } catch (Exception e) {
                    hook.sendMessage("There was error retrieving your schedules").setEphemeral(true).queue();
                    throw new RuntimeException(e);
                }
            });
        }
    }
}
