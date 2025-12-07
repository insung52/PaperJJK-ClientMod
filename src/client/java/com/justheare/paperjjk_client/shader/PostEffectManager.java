package com.justheare.paperjjk_client.shader;

import com.justheare.paperjjk_client.mixin.client.GameRendererAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.util.Identifier;

/**
 * Manages post-processing shader effects
 */
public class PostEffectManager {
    private static boolean isActive = false;

    // Custom shaders
    private static final Identifier RED_SHADER = Identifier.of("paperjjk_client", "jjk_red");
    private static final Identifier REFRACTION_SHADER = Identifier.of("paperjjk_client", "refraction");

    // Vanilla creeper for comparison
    private static final Identifier CREEPER_SHADER = Identifier.of("minecraft", "creeper");

    // Current shader to use - REFRACTION (gravitational lens effect)
    private static final Identifier CURRENT_SHADER = REFRACTION_SHADER;

    /**
     * Toggle post-processing shader on/off
     */
    public static void toggle() {
        GameRenderer renderer = MinecraftClient.getInstance().gameRenderer;

        if (isActive) {
            // Disable
            renderer.clearPostProcessor();
            isActive = false;
            System.out.println("[PaperJJK] Post-processor disabled");
        } else {
            // Enable
            try {
                ((GameRendererAccessor) renderer).invokeSetPostProcessor(CURRENT_SHADER);
                isActive = true;
                System.out.println("[PaperJJK] Post-processor enabled: " + CURRENT_SHADER);
            } catch (Exception e) {
                System.err.println("[PaperJJK] Failed to enable post-processor:");
                e.printStackTrace();
            }
        }
    }

    public static boolean isActive() {
        return isActive;
    }
}
