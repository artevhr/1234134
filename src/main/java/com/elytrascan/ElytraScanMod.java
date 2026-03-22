package com.elytrascan;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.class_304;
import net.minecraft.class_3675;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ElytraScanMod implements ClientModInitializer {

    public static final String MOD_ID = "elytrascan";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static class_304 openGuiKey;

    @Override
    public void onInitializeClient() {
        openGuiKey = KeyBindingHelper.registerKeyBinding(new class_304(
                "key.elytrascan.open_gui",
                class_3675.class_307.field_1668,
                GLFW.GLFW_KEY_INSERT,
                "ElytraScan"
        ));

        ScanConfig.load();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (openGuiKey.method_1436()) {
                if (client.field_1755 == null) {
                    client.method_1507(new ScannerScreen());
                }
            }
            BlockScanner.tick(client);
        });

        HudRenderCallback.EVENT.register((drawContext, tickDelta) ->
                BlockScanner.renderHud(drawContext));
    }
}
