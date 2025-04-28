package com.qudl.client;

import com.qudl.config.QUDLConfig;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QUDLClientMod implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("QUDL");
    public static QUDLConfig config;
    private static boolean firstLaunch = true;

    @Override
    public void onInitializeClient() {
        // 注册客户端启动完成事件
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            LOGGER.info("[QUDL] 开始延迟初始化");

            config = QUDLConfig.load();

            // 注册屏幕初始化事件（确保最后执行）
            ScreenEvents.AFTER_INIT.register((mc, screen, width, height) -> {
                if (screen instanceof TitleScreen titleScreen) {
                    addUpdateButton(titleScreen, mc);
                }
            });

            // 首次启动自动检查
            if (firstLaunch) {
                MinecraftClient.getInstance().execute(() -> MinecraftClient.getInstance().setScreen(new com.qudl.gui.QUDLProgressScreen()));
                new com.qudl.manager.QUDLUpdateManager().checkForUpdates();
                firstLaunch = false;
            }
        });
    }

    private void addUpdateButton(TitleScreen titleScreen, MinecraftClient client) {
        ButtonWidget button = ButtonWidget.builder(Text.of("检查更新"), btn -> {
                    client.execute(() -> client.setScreen(new com.qudl.gui.QUDLProgressScreen()));
                    new com.qudl.manager.QUDLUpdateManager().checkForUpdates();
                })
                .position(titleScreen.width / 2 + 104, titleScreen.height / 4 + 72)
                .size(50, 20)
                .build();

        Screens.getButtons(titleScreen).add(button);
        LOGGER.debug("[QUDL] 主菜单按钮已添加");
    }
}