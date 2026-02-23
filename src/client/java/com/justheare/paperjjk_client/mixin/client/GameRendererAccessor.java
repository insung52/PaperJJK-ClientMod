package com.justheare.paperjjk_client.mixin.client;

import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Accessor for GameRenderer private methods.
 */
@Mixin(GameRenderer.class)
public interface GameRendererAccessor {
    @Invoker("setPostProcessor")
    void invokeSetPostProcessor(Identifier id);

    /**
     * Returns the effective FOV for the current frame including all dynamic modifiers:
     * sprinting, flying speed, Speed/Slowness effects, bow draw zoom.
     * Pass changingFov=true to include modifiers, false for base-only.
     */
    @Invoker("getFov")
    float invokeFov(Camera camera, float tickDelta, boolean changingFov);
}
