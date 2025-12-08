package com.justheare.paperjjk_client.mixin.client;

import net.minecraft.client.gl.BufferManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.ByteBuffer;

/**
 * Mixin to make BufferManager create mutable uniform buffers
 */
@Mixin(BufferManager.class)
public class GlGpuBufferMixin {

    @Inject(method = "setBufferStorage(ILjava/nio/ByteBuffer;I)V", at = @At("HEAD"), cancellable = true)
    private void onSetBufferStorage(int target, ByteBuffer data, int flags, CallbackInfo ci) {
        // Check if this is a uniform buffer (GL_UNIFORM_BUFFER = 35345)
        if (target == 35345) {
            System.out.println("[GlGpuBufferMixin] Intercepting uniform buffer creation, adding DYNAMIC_STORAGE_BIT");

            // Add GL_DYNAMIC_STORAGE_BIT (0x0100) to flags
            int newFlags = flags | 0x0100;

            // Call glBufferStorage directly with modified flags
            org.lwjgl.opengl.GL44.glBufferStorage(target, data, newFlags);

            // Cancel original method call
            ci.cancel();
        }
    }

    @Inject(method = "setBufferStorage(IJI)V", at = @At("HEAD"), cancellable = true)
    private void onSetBufferStorageSize(int target, long size, int flags, CallbackInfo ci) {
        // Check if this is a uniform buffer (GL_UNIFORM_BUFFER = 35345)
        if (target == 35345) {
            System.out.println("[GlGpuBufferMixin] Intercepting uniform buffer creation (size-only), adding DYNAMIC_STORAGE_BIT");

            // Add GL_DYNAMIC_STORAGE_BIT (0x0100) to flags
            int newFlags = flags | 0x0100;

            // Call glBufferStorage directly with modified flags
            org.lwjgl.opengl.GL44.glBufferStorage(target, size, newFlags);

            // Cancel original method call
            ci.cancel();
        }
    }
}
