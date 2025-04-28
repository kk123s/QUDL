package com.qudl.network;

import com.qudl.util.HashUtils;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class QUDLDownloader {
    public interface ProgressListener {
        void onStartDownload(String filename);
        void onProgress(String filename, long current, long total);
        void onComplete(String filename);
        void onError(String filename, Exception e);
        String getExpectedHash(String filename);
    }

    private final ExecutorService executor;
    private final int maxRetries;

    public QUDLDownloader(int threads, int maxRetries) {
        this.executor = Executors.newFixedThreadPool(threads);
        this.maxRetries = maxRetries;
    }

    public void downloadFile(String url, String filename, Path outputDir, ProgressListener listener) {
        executor.submit(() -> {
            int retryCount = 0;
            while (retryCount <= maxRetries) {
                try {
                    listener.onStartDownload(filename);

                    HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(30000);
                    conn.setRequestMethod("GET");

                    try (InputStream in = conn.getInputStream();
                         OutputStream out = Files.newOutputStream(outputDir.resolve(filename))) {

                        long totalSize = conn.getContentLengthLong();
                        byte[] buffer = new byte[8192];
                        long totalRead = 0;
                        int bytesRead;

                        while ((bytesRead = in.read(buffer)) != -1) {
                            out.write(buffer, 0, bytesRead);
                            totalRead += bytesRead;
                            listener.onProgress(filename, totalRead, totalSize);
                        }

                        // 哈希校验
                        if (HashUtils.validateSHA256(outputDir.resolve(filename), listener.getExpectedHash(filename))) {
                            Files.deleteIfExists(outputDir.resolve(filename));
                            throw new Exception("文件哈希校验失败: " + filename);
                        }

                        listener.onComplete(filename);
                        return;
                    }
                } catch (Exception e) {
                    if (++retryCount > maxRetries) {
                        listener.onError(filename, new Exception("下载失败（最大重试次数：" + maxRetries + "）: " + e.getMessage()));
                        break;
                    }
                }
            }
        });
    }
}