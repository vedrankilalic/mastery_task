package com.masteryapi.masteryapi.config;

import io.github.cdimascio.dotenv.Dotenv;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DotenvLoader {

    private static final Logger log = LoggerFactory.getLogger(DotenvLoader.class);

    private DotenvLoader() {
    }

    public static void load() {
        Path envDir = resolveEnvDirectory();
        if (envDir == null) {
            log.debug("No env/ directory with .env found; skipping dotenv");
            return;
        }
        Dotenv dotenv = Dotenv.configure()
                .directory(envDir.toAbsolutePath().toString())
                .filename(".env")
                .ignoreIfMalformed()
                .ignoreIfMissing()
                .load();
        int[] applied = {0};
        dotenv.entries().forEach(entry -> {
            String key = entry.getKey();
            String fromOs = System.getenv(key);
            if (fromOs != null && !fromOs.isBlank()) {
                return;
            }
            String value = entry.getValue();
            if (value != null && !value.isBlank()) {
                System.setProperty(key, value);
                applied[0]++;
            }
        });
        if (applied[0] > 0) {
            log.info("Applied {} entries from {}", applied[0], envDir.resolve(".env").toAbsolutePath());
        } else {
            log.debug("Dotenv file present at {} but no new system properties set (empty or all keys in OS env)",
                    envDir.resolve(".env").toAbsolutePath());
        }
    }

    private static Path resolveEnvDirectory() {
        String userDir = System.getProperty("user.dir");
        Path[] candidates = new Path[] {
                Path.of("env"),
                Path.of("masteryapi", "env"),
                Path.of(userDir, "env"),
                Path.of(userDir, "masteryapi", "env"),
        };
        for (Path dir : candidates) {
            Path file = dir.resolve(".env");
            if (Files.isRegularFile(file)) {
                return dir;
            }
        }
        return null;
    }
}
