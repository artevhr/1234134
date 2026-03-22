package com.elytrascan;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ElytraScanMod implements ClientModInitializer {

    public static final String MOD_ID = "elytrascan";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static KeyBinding openGuiKey;

    @Override
    public void onInitializeClient() {
        openGuiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.elytrascan.open_gui",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_INSERT,
                "ElytraScan"
        ));

        ScanConfig.load();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (openGuiKey.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(new ScannerScreen());
                }
            }
            BlockScanner.tick(client);
        });

        HudRenderCallback.EVENT.register((drawContext, tickDelta) ->
                BlockScanner.renderHud(drawContext));
    }
}
