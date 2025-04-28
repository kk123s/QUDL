package com.qudl.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class QUDLConfig {
    public ProviderConfig providers = new ProviderConfig();
    public DownloadConfig download = new DownloadConfig();

    public static class ProviderConfig {
        public ApiConfig api = new ApiConfig();
        public ModsConfig mods = new ModsConfig();
    }

    public static class ApiConfig {
        public String base_url = "http://home.xuebi.cloud:9009/api/";
        public String current_version = "1.20.1";
    }

    public static class ModsConfig {
        public String base_url = "http://home.xuebi.cloud:9009/mods";
    }

    public static class DownloadConfig {
        public int threads = 4;
        public int retries = 3;
    }

    public static QUDLConfig load() {
        Path path = Path.of("config/qudl_config.json");
        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        try {
            if (!Files.exists(path)) {
                Files.createDirectories(path.getParent());
                Files.writeString(path, gson.toJson(new QUDLConfig()));
            }
            return gson.fromJson(Files.newBufferedReader(path), QUDLConfig.class);
        } catch (IOException e) {
            return new QUDLConfig();
        }
    }
}