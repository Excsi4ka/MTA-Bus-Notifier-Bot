package excsi.mtabot;

import excsi.mtabot.protobuf.generated.GtfsRealtime.FeedEntity;
import excsi.mtabot.protobuf.generated.GtfsRealtime.FeedMessage;
import excsi.mtabot.protobuf.generated.GtfsRealtime.TripUpdate;
import io.github.cdimascio.dotenv.Dotenv;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.GatewayIntent;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class DiscordBot {

    public static final String MTA_TRIP_ENDPOINT = "https://gtfsrt.prod.obanyc.com/tripUpdates?key=%s";

    public static final String SCHEDULE_QUERY = "SELECT UserID, StopID FROM UserSchedules WHERE ? BETWEEN StartTime AND EndTime";

    public static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    public static final ExecutorService DB_WRITER = Executors.newSingleThreadExecutor();

    public static final ExecutorService DB_READER = Executors.newSingleThreadExecutor();

    public static final ScheduledExecutorService MTA_API_FETCHER = Executors.newSingleThreadScheduledExecutor();

    public static JDA JDA_INSTANCE;

    public static String DISCORD_BOT_API_KEY;

    public static String MTA_API_KEY;

    public static Connection WRITER;

    public static Connection READER;

    public static void main(String[] args) throws SQLException {
        Dotenv dotenv = Dotenv.load();
        MTA_API_KEY = dotenv.get("MTA_API");
        DISCORD_BOT_API_KEY = dotenv.get("DISCORD_API");

        WRITER = DriverManager.getConnection("jdbc:sqlite:db/schedules.db");
        READER = DriverManager.getConnection("jdbc:sqlite:db/schedules.db");
        try (Statement st = WRITER.createStatement()) {
            st.execute("PRAGMA journal_mode=WAL;");
            st.execute("PRAGMA busy_timeout=3000;");
        }
        try (Statement st = READER.createStatement()) {
            st.execute("PRAGMA busy_timeout=3000;");
        }
        createSchema();

        JDA_INSTANCE = JDABuilder
                .createDefault(DISCORD_BOT_API_KEY)
                .enableIntents(GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT, GatewayIntent.GUILD_MESSAGE_REACTIONS, GatewayIntent.DIRECT_MESSAGE_REACTIONS)
                .addEventListeners(new EventHandler())
                .build();
        JDA_INSTANCE.updateCommands().addCommands(
                Commands.slash("add-schedule", "Adds a scheduled time for notifications")
                        .addOptions(new OptionData(OptionType.INTEGER,
                                "bus-stop-id",
                                "6 digit bus stop id",
                                true)
                                .setRequiredRange(400000, 600000))
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

        MTA_API_FETCHER.scheduleAtFixedRate(() -> {
            HttpRequest request = HttpRequest.newBuilder()
                    .GET()
                    .uri(URI.create(String.format(MTA_TRIP_ENDPOINT, MTA_API_KEY)))
                    .build();
            try {
                HttpResponse<byte[]> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());
                FeedMessage message = FeedMessage.parseFrom(response.body());
                long currentTime = TimeUtil.nowSeconds();
                Future<Map<Integer, List<Long>>> future = DB_READER.submit(() -> {
                    Map<Integer, List<Long>> busToUsers = new HashMap<>();
                    try (PreparedStatement statement = READER.prepareStatement(SCHEDULE_QUERY)) {
                        statement.setLong(1, currentTime);
                        ResultSet set = statement.executeQuery();
                        while (set.next()) {
                            int busStop = set.getInt("StopID");
                            long userID = set.getLong("UserID");
                            busToUsers.computeIfAbsent(busStop, key -> new ArrayList<>()).add(userID);
                        }
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                    return busToUsers;
                });
                Map<Integer, List<Long>> busStopToUsers = future.get(); //blocking
                for (FeedEntity entity : message.getEntityList()) {
                    if (!entity.hasTripUpdate())
                        continue;
                    TripUpdate update = entity.getTripUpdate();
                    for (TripUpdate.StopTimeUpdate stopTimeUpdate : update.getStopTimeUpdateList()) {
                        int stopID = Integer.parseInt(stopTimeUpdate.getStopId());
                        if (!busStopToUsers.containsKey(stopID))
                            continue;
                        long arrivalTime = getArrivalTime(stopTimeUpdate);
                        if (arrivalTime == -1)
                            continue;
                        long timeInMinutes = (arrivalTime - currentTime) / 60;
                        if (timeInMinutes > 10)
                            return;
                        busStopToUsers.get(stopID).forEach(userID -> {
                            JDA_INSTANCE.getUserById(userID)
                                    .openPrivateChannel()
                                    .queue(privateChannel -> privateChannel.sendMessage(String.format("The bus is arriving is %s minutes", timeInMinutes)
                                    ).queue());
                        });
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, 0, 60, TimeUnit.SECONDS);
    }

    private static void createSchema() throws SQLException {
        try (Statement st = WRITER.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS UserSchedules (
                    UserID INTEGER NOT NULL,
                    StopID INTEGER NOT NULL,
                    StartTime INTEGER NOT NULL,
                    EndTime INTEGER NOT NULL
                );
            """);
        }
    }

    private static long getArrivalTime(TripUpdate.StopTimeUpdate update) {
        if (update.hasArrival() && update.getArrival().hasTime()) {
            return update.getArrival().getTime();
        }
        if (update.hasDeparture() && update.getDeparture().hasTime()) {
            return update.getDeparture().getTime();
        }
        return -1;
    }
}