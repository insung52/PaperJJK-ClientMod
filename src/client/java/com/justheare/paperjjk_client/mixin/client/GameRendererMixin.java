package com.justheare.paperjjk_client.mixin.client;

import com.justheare.paperjjk_client.mixin.client.CameraAccessor;
import com.justheare.paperjjk_client.render.DebugRenderer;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4fStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to GameRenderer for applying post-processing effects
 * Injects at the end of renderWorld, just like Iris does for renderLevel
 */
@Mixin(GameRenderer.class)
public class GameRendererMixin {

    /**
     * Inject right after WorldRenderer.render() returns in renderWorld.
     * At this point Iris has finished its g-buffer compositing and restored
     * the main framebuffer. The modelViewStack still has the camera rotation
     * that GameRenderer set up before calling WorldRenderer.render().
     * RenderLayer.draw() reads modelViewStack via RenderSystem.getModelViewMatrix()
     * and uploads it as ModelViewMat in the DynamicTransforms UBO automatically.
     */
    @Inject(method = "renderWorld",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/WorldRenderer;render(Lnet/minecraft/client/util/ObjectAllocator;Lnet/minecraft/client/render/RenderTickCounter;ZLnet/minecraft/client/render/Camera;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;Z)V",
            shift = At.Shift.AFTER))
    private void paperjjk$renderSpheres(RenderTickCounter tickCounter, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return;

        Camera camera = client.gameRenderer.getCamera();

        // Explicitly set up camera rotation on modelViewStack.
        // GameRenderer may have already popped it after WorldRenderer.render() returned,
        // so we set it unconditionally and restore afterwards.
        Matrix4fStack mvs = RenderSystem.getModelViewStack();
        mvs.pushMatrix();
        mvs.identity();
        mvs.rotateX((float) Math.toRadians(camera.getPitch()));
        mvs.rotateY((float) Math.toRadians(camera.getYaw() + 180.0f));

        // DebugRenderer uses a fresh identity MatrixStack and translates each sphere
        // to (worldPos - cameraPos). The GPU shader applies ModelViewMat (camera rotation
        // from modelViewStack above) and ProjMat (world projection, still valid).
        MatrixStack matrices = new MatrixStack();
        DebugRenderer.render(matrices, camera);

        mvs.popMatrix();
    }

}
