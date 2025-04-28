package com.qudl.manager;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.qudl.client.QUDLClientMod;
import com.qudl.config.QUDLConfig;
import com.qudl.gui.QUDLProgressScreen;
import com.qudl.network.QUDLDownloader;
import com.qudl.util.HashUtils;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import java.io.InputStreamReader;
import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class QUDLUpdateManager {
    private static final Logger LOGGER = QUDLClientMod.LOGGER;
    private final AtomicInteger activeDownloads = new AtomicInteger(0);

    public void checkForUpdates() {
        LOGGER.info("[QUDL] 开始检查更新...");
        MinecraftClient.getInstance().execute(() -> {
            if (MinecraftClient.getInstance().currentScreen == null) {
                MinecraftClient.getInstance().setScreen(new QUDLProgressScreen());
            }
        });

        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                JsonArray manifest = fetchManifest();
                List<DownloadTask> updates = compareFiles(manifest);

                if (updates.isEmpty()) {
                    LOGGER.info("[QUDL] 没有需要更新的内容");
                    closeProgressScreen();
                    return;
                }

                LOGGER.info("[QUDL] 发现 {} 个需要更新的文件", updates.size());
                updateUIStatus(updates.size());
                startDownloads(updates);
            } catch (Exception e) {
                LOGGER.error("[QUDL] 更新检查失败: {}", e.getMessage());
                handleError(e);
            }
        });
    }

    private JsonArray fetchManifest() throws IOException {
        QUDLConfig config = QUDLClientMod.config;
        String apiUrl = buildApiUrl(
                config.providers.api.base_url,
                config.providers.api.current_version
        );

        HttpURLConnection conn = createConnection(apiUrl);
        try (InputStreamReader reader = new InputStreamReader(conn.getInputStream())) {
            return parseManifest(reader);
        } finally {
            conn.disconnect();
        }
    }

    private HttpURLConnection createConnection(String apiUrl) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(apiUrl).openConnection();
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(15000);
        conn.setRequestMethod("GET");

        int responseCode = conn.getResponseCode();
        if (responseCode != 200) {
            throw new IOException("HTTP " + responseCode);
        }
        return conn;
    }

    private JsonArray parseManifest(InputStreamReader reader) throws IOException {
        JsonElement jsonElement = JsonParser.parseReader(reader);
        if (jsonElement.isJsonObject()) {
            JsonObject jsonObj = jsonElement.getAsJsonObject();
            if (jsonObj.has("files")) {
                return jsonObj.getAsJsonArray("files");
            }
            throw new IOException("无效的manifest格式：缺少files字段");
        }
        if (jsonElement.isJsonArray()) {
            return jsonElement.getAsJsonArray();
        }
        throw new IOException("未知的manifest格式");
    }

    private List<DownloadTask> compareFiles(JsonArray manifest) {
        List<DownloadTask> tasks = new ArrayList<>();
        Path modsDir = MinecraftClient.getInstance().runDirectory.toPath().resolve("mods");

        for (JsonElement element : manifest) {
            if (!element.isJsonObject()) continue;

            JsonObject entry = element.getAsJsonObject();
            String filename = entry.get("filename").getAsString();
            Path filePath = modsDir.resolve(filename);

            try {
                if (Files.notExists(filePath)) {
                    tasks.add(createModTask(entry));
                    LOGGER.debug("[QUDL] 新文件: {}", filename);
                } else if (HashUtils.validateSHA256(filePath, entry.get("hash").getAsString())) {
                    tasks.add(createModTask(entry));
                    LOGGER.debug("[QUDL] 文件需要更新: {}", filename);
                }
            } catch (IOException e) {
                LOGGER.error("[QUDL] 文件校验失败: {}", filename, e);
            }
        }
        return tasks;
    }

    private DownloadTask createModTask(JsonObject entry) throws MalformedURLException {
        QUDLConfig config = QUDLClientMod.config;
        String filename = entry.get("filename").getAsString();
        String version = config.providers.api.current_version;
        String modUrl = buildModUrl(
                config.providers.mods.base_url,
                version,
                filename
        );

        validateUrl(modUrl);
        return new DownloadTask(
                modUrl,
                filename,
                entry.get("hash").getAsString(),
                entry.get("size").getAsLong()
        );
    }

    private String buildApiUrl(String baseUrl, String version) {
        return withTrailingSlash(baseUrl) + version;
    }

    private String buildModUrl(String baseUrl, String version, String filename) {
        return withTrailingSlash(baseUrl)
                + version + "/"
                + URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private String withTrailingSlash(String url) {
        return url.endsWith("/") ? url : url + "/";
    }

    private void validateUrl(String url) throws MalformedURLException {
        new URL(url); // 验证URL格式
        if (!url.startsWith("http")) {
            throw new MalformedURLException("仅支持HTTP协议: " + url);
        }
    }

    private void startDownloads(List<DownloadTask> tasks) {
        activeDownloads.set(tasks.size());
        QUDLDownloader downloader = new QUDLDownloader(
                QUDLClientMod.config.download.threads,
                QUDLClientMod.config.download.retries
        );

        for (DownloadTask task : tasks) {
            LOGGER.info("[QUDL] 队列下载任务: {}", task.filename());
            downloader.downloadFile(
                    task.url(),
                    task.filename(),
                    MinecraftClient.getInstance().runDirectory.toPath().resolve("mods"),
                    new QUDLDownloader.ProgressListener() {
                        @Override
                        public void onStartDownload(String filename) {
                            LOGGER.info("[QUDL] 开始下载: {}", filename);
                            updateCurrentFile(filename);
                        }

                        @Override
                        public void onProgress(String filename, long current, long total) {}

                        @Override
                        public void onComplete(String filename) {
                            handleDownloadComplete();
                        }

                        @Override
                        public void onError(String filename, Exception e) {
                            handleDownloadError(filename, e);
                        }

                        @Override
                        public String getExpectedHash(String filename) {
                            return task.hash();
                        }
                    }
            );
        }
    }

    private void updateUIStatus(int total) {
        MinecraftClient.getInstance().execute(() -> {
            if (MinecraftClient.getInstance().currentScreen instanceof QUDLProgressScreen screen) {
                screen.setStatus("正在下载更新...");
                screen.setTotalFiles(total);
            }
        });
    }

    private void updateCurrentFile(String filename) {
        MinecraftClient.getInstance().execute(() -> {
            if (MinecraftClient.getInstance().currentScreen instanceof QUDLProgressScreen screen) {
                screen.setCurrentFile(filename);
                screen.incrementDownloaded();
            }
        });
    }

    private void handleDownloadComplete() {
        if (activeDownloads.decrementAndGet() == 0) {
            LOGGER.info("[QUDL] 所有下载已完成");
            MinecraftClient.getInstance().execute(() -> {
                if (MinecraftClient.getInstance().currentScreen instanceof QUDLProgressScreen screen) {
                    screen.startCountdown();
                }
            });

            new Thread(() -> {
                try {
                    logCountdown(3);
                    logCountdown(2);
                    logCountdown(1);
                    MinecraftClient.getInstance().scheduleStop();
                } catch (InterruptedException e) {
                    LOGGER.error("[QUDL] 关闭延迟被中断: {}", e.getMessage());
                }
            }).start();
        }
    }

    private void logCountdown(int seconds) throws InterruptedException {
        Thread.sleep(1000);
        LOGGER.info("[QUDL] 倒计时{}秒...", seconds);
    }

    private void handleDownloadError(String filename, Exception e) {
        LOGGER.error("[QUDL] 文件下载失败: {} - {}", filename, e.getMessage());
        if (activeDownloads.decrementAndGet() == 0) {
            closeProgressScreen();
        }
        MinecraftClient.getInstance().execute(() -> {
            if (MinecraftClient.getInstance().currentScreen instanceof QUDLProgressScreen screen) {
                screen.showError(e.getMessage());
            }
        });
    }

    private void closeProgressScreen() {
        MinecraftClient.getInstance().execute(() ->
                MinecraftClient.getInstance().setScreen(null)
        );
    }

    private void handleError(Exception e) {
        MinecraftClient.getInstance().execute(() -> {
            if (MinecraftClient.getInstance().currentScreen instanceof QUDLProgressScreen screen) {
                screen.showError(e.getMessage());
            }
        });
    }

    private record DownloadTask(String url, String filename, String hash, long size) {}
}