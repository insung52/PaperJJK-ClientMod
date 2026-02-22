package com.justheare.paperjjk_client.mixin.client;

import com.justheare.paperjjk_client.render.JJKDepthCache;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.render.DefaultFramebufferSet;
import net.minecraft.client.render.FrameGraphBuilder;
import net.minecraft.client.render.FramePass;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.Handle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Injects into WorldRenderer.renderMain() at RETURN to register a FramePass that captures
 * world depth into JJKDepthCache before FrameGraphBuilder.run() recycles the mainFramebuffer.
 *
 * Why renderMain()? It sets up the solid-geometry rendering pass (terrain, entities, sky).
 * At its RETURN, framebufferSet.mainFramebuffer holds the Handle for the world framebuffer,
 * and FrameGraphBuilder.run() has NOT been called yet.
 *
 * Our FramePass executes DURING run() when Handle.get() is valid, copies the world depth
 * to a persistent SimpleFramebuffer (JJKDepthCache), which MinecraftClientMixin then reads.
 */
@Mixin(WorldRenderer.class)
public class WorldRendererMixin {

    @Shadow
    private DefaultFramebufferSet framebufferSet;

    @Inject(method = "renderMain", at = @At("RETURN"))
    private void paperjjk$registerDepthCapturePass(
            FrameGraphBuilder fgb,
            net.minecraft.client.render.Frustum frustum,
            org.joml.Matrix4f matrix4f,
            com.mojang.blaze3d.buffers.GpuBufferSlice gpuBufferSlice,
            boolean bl,
            net.minecraft.client.render.state.WorldRenderState worldRenderState,
            net.minecraft.client.render.RenderTickCounter renderTickCounter,
            net.minecraft.util.profiler.Profiler profiler,
            CallbackInfo ci) {

        if (framebufferSet == null || framebufferSet.mainFramebuffer == null) return;

        // Capture Handle reference — valid as a Java object even before FrameGraph.run()
        Handle<Framebuffer> mainFbHandle = framebufferSet.mainFramebuffer;

        // Register a FramePass: executes DURING run() when Handle.get() works
        FramePass depthPass = fgb.createPass("paperjjk:depth_capture");
        depthPass.dependsOn(mainFbHandle);  // run after world rendering fills mainFramebuffer
        depthPass.markToBeVisited();        // prevent FrameGraph from skipping this pass
        depthPass.setRenderer(() -> {
            try {
                JJKDepthCache.captureFrom(mainFbHandle.get());
            } catch (Exception e) {
                // depth unavailable this frame — silently skip
            }
        });
    }
}
