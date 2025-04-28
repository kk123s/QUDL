package com.qudl.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import com.qudl.client.QUDLClientMod;

public class QUDLProgressScreen extends Screen {
    private static final Logger LOGGER = QUDLClientMod.LOGGER;
    private static final MinecraftClient client = MinecraftClient.getInstance();
    private String statusMessage = "正在从服务器获取更新...";
    private String currentFile = "";
    private int downloadedCount = 0;
    private int totalFiles = 0;
    private int countdown = -1;
    private long lastUpdateTime;
    private String errorMessage;

    public QUDLProgressScreen() {
        super(Text.of("QUDL 更新管理器"));
        LOGGER.info("[QUDL] 初始化进度界面");
    }

    @Override
    protected void init() {
        super.init();
        this.addDrawableChild(
                ButtonWidget.builder(Text.of("取消"), button -> close())
                        .position(this.width - 110, this.height - 30)
                        .size(100, 20)
                        .build()
        );
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);

        context.getMatrices().push();
        context.getMatrices().translate(0, 0, 500);

        // 主状态
        context.drawCenteredTextWithShadow(
                textRenderer,
                errorMessage == null ? statusMessage : "更新错误",
                width / 2,
                30,
                0xFFFFFF
        );

        // 下载进度
        if (errorMessage == null) {
            String progressText = String.format("正在下载：%s (%d/%d)",
                    currentFile, downloadedCount, totalFiles);
            context.drawCenteredTextWithShadow(
                    textRenderer,
                    progressText,
                    width / 2,
                    50,
                    0xAAAAAA
            );
        }

        // 错误信息
        if (errorMessage != null) {
            context.drawCenteredTextWithShadow(
                    textRenderer,
                    "错误详情: " + errorMessage,
                    width / 2,
                    height / 2,
                    0xFF5555
            );
        }

        // 倒计时
        if (countdown > 0) {
            if (System.currentTimeMillis() - lastUpdateTime > 1000) {
                countdown--;
                lastUpdateTime = System.currentTimeMillis();
            }
            context.drawCenteredTextWithShadow(
                    textRenderer,
                    "将在 " + countdown + " 秒后退出",
                    width / 2,
                    height - 35,
                    0x55FF55
            );
            context.drawCenteredTextWithShadow(
                    textRenderer,
                    "退出后请重启游戏以完成更新",
                    width / 2,
                    height - 50,
                    0x55FF55
            );
        }

        context.getMatrices().pop();
    }

    public void setStatus(String status) {
        this.statusMessage = status;
    }

    public void setCurrentFile(String filename) {
        this.currentFile = filename;
    }

    public void setTotalFiles(int total) {
        this.totalFiles = total;
    }

    public void incrementDownloaded() {
        this.downloadedCount++;
    }

    public void startCountdown() {
        //退出倒计时部分以秒为单位
        this.countdown = 3;
        this.lastUpdateTime = System.currentTimeMillis();
    }

    public void showError(String message) {
        this.errorMessage = message;
        this.countdown = -1;
    }

    public void close() {
        client.setScreen(null);
    }
}