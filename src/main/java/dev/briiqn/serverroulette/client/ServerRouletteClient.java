package dev.briiqn.serverroulette.client;

import dev.briiqn.serverroulette.screens.ShodanServerListScreen;
import dev.briiqn.serverroulette.shodan.api.ShodanServerManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public class ServerRouletteClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {


        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof MultiplayerScreen) {
                addShodanButton((MultiplayerScreen) screen);
            }
        });
    }

    private void addShodanButton(MultiplayerScreen screen) {
        int buttonWidth = 100;
        int buttonHeight = 20;
        int buttonX = buttonWidth - 10;
        int buttonY = 10;

        ButtonWidget shodanButton = ButtonWidget.builder(Text.literal("Shodan Servers"),
                        button -> openShodanServerList(screen))
                .dimensions(buttonX, buttonY, buttonWidth, buttonHeight)
                .build();

        Screens.getButtons(screen).add(shodanButton);
    }

    private void openShodanServerList(MultiplayerScreen parentScreen) {
        ShodanServerListScreen shodanScreen = new ShodanServerListScreen(parentScreen);
        MinecraftClient.getInstance().setScreen(shodanScreen);
    }
}