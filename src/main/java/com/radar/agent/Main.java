package com.radar.agent;

import com.radar.agent.pipeline.AgentRunner;
import com.radar.agent.util.EnvLoader;
import com.radar.agent.util.Json;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class Main {
    public static void main(String[] args) {
        EnvLoader.load(Path.of(".env"));
        Path configPath = Path.of("config.json");
        Config config = loadConfig(configPath);
        ensureConfigWritten(configPath, config);

        new AgentRunner().run(config);
        System.out.println("Wrote jobs to " + Path.of(config.outputDir).resolve("index.html"));
    }

    private static Config loadConfig(Path path) {
        try {
            if (!Files.exists(path)) {
                return new Config();
            }
            String json = Files.readString(path, StandardCharsets.UTF_8);
            return Json.read(json, Config.class);
        } catch (Exception e) {
            System.err.println("Failed to read config.json, using defaults: " + e.getMessage());
            return new Config();
        }
    }

    private static void ensureConfigWritten(Path path, Config config) {
        try {
            if (!Files.exists(path)) {
                Files.writeString(path, Json.write(config), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            System.err.println("Failed to write default config.json: " + e.getMessage());
        }
    }
}
