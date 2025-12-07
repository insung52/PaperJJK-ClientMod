package com.justheare.paperjjk_client.debug;

import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

/**
 * Debug utility to inspect GameRenderer methods
 */
public class GameRendererDebug {
    private static final Logger LOGGER = LoggerFactory.getLogger("PaperJJK-Debug");
    private static boolean hasRun = false;

    public static void inspectMethods() {
        if (hasRun) return;
        hasRun = true;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.gameRenderer == null) {
            LOGGER.warn("MinecraftClient or GameRenderer not available yet");
            return;
        }

        LOGGER.info("=== GameRenderer Methods (post-processor related) ===");
        Method[] methods = client.gameRenderer.getClass().getDeclaredMethods();

        for (Method method : methods) {
            String name = method.getName().toLowerCase();
            if (name.contains("post") || name.contains("shader") || name.contains("effect")) {
                LOGGER.info("  {} ({})", method.getName(), method.toString());
            }
        }
        LOGGER.info("=== End of GameRenderer Methods ===");
    }
}
