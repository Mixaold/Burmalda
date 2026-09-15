package com.mixaold.burmalda.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class BurmaldaLogger {
    private static final Logger LOGGER = LoggerFactory.getLogger("Burmalda");
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static PrintWriter fileWriter;
    private static Path logPath;

    public static void init(Path gameDir) {
        try {
            logPath = gameDir.resolve("logs/burmalda.log");
            Files.createDirectories(logPath.getParent());
            fileWriter = new PrintWriter(new FileWriter(logPath.toFile(), true));
            info("=== Burmalda mod initialized. Log: " + logPath.toAbsolutePath() + " ===");
        } catch (IOException e) {
            LOGGER.error("[Burmalda] Failed to create log file: {}", e.getMessage());
        }
    }

    public static void info(String msg) {
        LOGGER.info("[Burmalda] {}", msg);
        write("INFO", msg);
    }

    public static void warn(String msg) {
        LOGGER.warn("[Burmalda] {}", msg);
        write("WARN", msg);
    }

    public static void error(String msg) {
        LOGGER.error("[Burmalda] {}", msg);
        write("ERROR", msg);
    }

    public static void error(String msg, Throwable t) {
        LOGGER.error("[Burmalda] {}", msg, t);
        write("ERROR", msg + " | " + t.getMessage());
    }

    public static void debug(String msg) {
        LOGGER.debug("[Burmalda] {}", msg);
        write("DEBUG", msg);
    }

    public static Path getLogPath() {
        return logPath;
    }

    private static void write(String level, String msg) {
        if (fileWriter == null) return;
        fileWriter.println("[" + LocalDateTime.now().format(FORMATTER) + "] [" + level + "] " + msg);
        fileWriter.flush();
    }
}
