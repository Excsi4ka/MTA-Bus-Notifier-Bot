package excsi.mtabot;

import io.github.cdimascio.dotenv.Dotenv;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.GatewayIntent;

import java.net.http.HttpClient;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class Main {

    public static JDA JDA_INSTANCE;

    public static String DISCORD_BOT_API_KEY;

    public static String MTA_API_KEY;

    public static Connection WRITER;

    public static Connection READER;

    public static final String MTA_TRIP_ENDPOINT = "https://gtfsrt.prod.obanyc.com/tripUpdates?key=%s";

    public static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    public static final Executor DB_WRITER = Executors.newSingleThreadExecutor();

    public static final Executor REALTIME_UPDATER = Executors.newSingleThreadExecutor();


    public static void main(String[] args) throws SQLException {
        Dotenv dotenv = Dotenv.load();
        MTA_API_KEY = dotenv.get("MTA_API");
        DISCORD_BOT_API_KEY = dotenv.get("DISCORD_API");

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

        WRITER = DriverManager.getConnection("jdbc:sqlite:db/schedules.db");
        READER = DriverManager.getConnection("jdbc:sqlite:db/schedules.db");
        try (Statement st = WRITER.createStatement()) {
            st.execute("PRAGMA journal_mode=WAL;");
            st.execute("PRAGMA busy_timeout=3000;");
            st.execute("PRAGMA foreign_keys=ON;");
        }
        try (Statement st = READER.createStatement()) {
            st.execute("PRAGMA busy_timeout=3000;");
        }

        createSchema();
    }

    private static void createSchema() throws SQLException {
        try (Statement st = WRITER.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS user_schedule (
                    id INTEGER PRIMARY KEY,
                    stop_id INTEGER NOT NULL,
                    start_time TEXT NOT NULL,
                    end_time TEXT NOT NULL
                );
            """);
        }
    }
}