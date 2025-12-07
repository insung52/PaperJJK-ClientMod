package com.justheare.paperjjk_client.mixin.client;

import com.justheare.paperjjk_client.util.PostProcessorReflector;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to update post-processor uniforms dynamically
 */
@Mixin(GameRenderer.class)
public class GameRendererMixin {
    private static boolean inspected = false;

    @Inject(method = "render", at = @At("HEAD"))
    private void onRenderStart(RenderTickCounter tickCounter, boolean tick, CallbackInfo ci) {
        // Get current GameRenderer
        GameRenderer renderer = (GameRenderer) (Object) this;

        // Check if our refraction shader is active
        net.minecraft.util.Identifier currentId = renderer.getPostProcessorId();

        if (currentId != null && currentId.toString().equals("paperjjk_client:refraction")) {
            // Inspect once to discover structure
            if (!inspected) {
                System.out.println("[PaperJJK] === Starting PostEffectProcessor inspection ===");
                PostProcessorReflector.inspectGameRenderer(renderer);

                Object postProcessor = PostProcessorReflector.getPostProcessor(renderer);
                PostProcessorReflector.inspectPostProcessor(postProcessor);

                inspected = true;
            }

            // TODO: Update uniforms here after discovering structure
        }
    }
}
