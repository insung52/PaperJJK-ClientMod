package com.justheare.paperjjk_client.mixin.client;

import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Previously held refraction post-processing logic.
 * Moved to GameRendererMixin to inject before HUD rendering,
 * so the crosshair, health bar, etc. are not distorted.
 */
@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {
}
