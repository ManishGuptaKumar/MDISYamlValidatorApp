package org.nwg.mdis;

import java.io.IOException;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.*;

public class MDISLogger {

    private static Logger logger;
    private static final String LOG_FOLDER = System.getProperty("user.home");
    private static final String DATE_PATTERN = "yyyyMMdd_HHmmss";
    private static final String LOG_PREFIX = "MDIS_YAML_VALIDATOR_ACTIONS_";

    public static Logger getLogger() {
        if (logger != null) return logger;

        logger = Logger.getLogger("AppLogger");
        logger.setUseParentHandlers(false); // Disable default console handler

        try {
            String timestamp = new SimpleDateFormat(DATE_PATTERN).format(new Date());
            String logFileName = LOG_PREFIX + timestamp + ".log";
            String logPath = Paths.get(LOG_FOLDER, logFileName).toString();

            FileHandler fileHandler = new FileHandler(logPath, true);
            fileHandler.setFormatter(new SingleLineFormatter());
            logger.addHandler(fileHandler);

            // Optional: Also log to console
            ConsoleHandler consoleHandler = new ConsoleHandler();
            consoleHandler.setFormatter(new SingleLineFormatter());
            logger.addHandler(consoleHandler);

        } catch (IOException e) {
            System.err.println("Failed to initialize logger: " + e.getMessage());
        }

        return logger;
    }

    // Custom formatter for single-line output
    private static class SingleLineFormatter extends Formatter {
        private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        @Override
        public String format(LogRecord record) {
            return String.format("%s [%s] %s%n",
                    dateFormat.format(new Date(record.getMillis())),
                    record.getLevel().getName(),
                    formatMessage(record));
        }
    }
}
