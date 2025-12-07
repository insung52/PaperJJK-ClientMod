package com.justheare.paperjjk_client.mixin.client;

import net.minecraft.client.render.GameRenderer;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Accessor for GameRenderer's private setPostProcessor method
 */
@Mixin(GameRenderer.class)
public interface GameRendererAccessor {
    @Invoker("setPostProcessor")
    void invokeSetPostProcessor(Identifier id);
}
