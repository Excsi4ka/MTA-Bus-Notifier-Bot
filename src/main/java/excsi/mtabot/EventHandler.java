package excsi.mtabot;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;

import java.util.regex.Pattern;

public class EventHandler extends ListenerAdapter {

    public static final String pattern = "^(0[1-9]|1[0-2]):[0-5][0-9](AM|PM)$";

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
            String startTime = event.getOption("start-time").getAsString();
            String endTime = event.getOption("end-time").getAsString();
            if (!Pattern.matches(pattern, startTime) || !Pattern.matches(pattern, endTime)) {
                event.reply("Invalid time format provided :x:").setEphemeral(true).queue();
                return;
            }
            //

            //
            event.reply("Scheduled notification added :calendar_spiral:")
                    .setEphemeral(true)
                    .addActionRow(
                            Button.primary("schedule", "View all your schedules")
                    ).queue();
        }
    }
}
