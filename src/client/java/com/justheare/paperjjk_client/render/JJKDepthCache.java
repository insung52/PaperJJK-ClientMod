package com.justheare.paperjjk_client.render;

import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;

/**
 * Persistent depth framebuffer that captures world depth during the FrameGraph execution.
 * WorldRendererMixin registers a FramePass that calls captureFrom() while the mainFramebuffer
 * Handle is still valid (inside FrameGraphBuilder.run()), before resources are recycled.
 * MinecraftClientMixin then uses get() to copy this depth to the WindowFramebuffer for
 * depth-aware post-processing.
 */
public class JJKDepthCache {
    private static volatile SimpleFramebuffer depthBuffer;

    /**
     * Called from inside a FramePass Runnable (during FrameGraphBuilder.run()).
     * Copies the world depth from the source framebuffer to our persistent buffer.
     */
    public static void captureFrom(Framebuffer source) {
        if (source == null || source.getDepthAttachment() == null) return;

        SimpleFramebuffer buf = depthBuffer;
        if (buf == null
                || buf.textureWidth != source.textureWidth
                || buf.textureHeight != source.textureHeight) {
            if (buf != null) buf.delete();
            buf = new SimpleFramebuffer("jjk_depth_cache",
                    source.textureWidth, source.textureHeight, true);
            depthBuffer = buf;
        }
        buf.copyDepthFrom(source);
    }

    /** Returns the cached depth framebuffer, or null if not yet captured this frame. */
    public static Framebuffer get() {
        return depthBuffer;
    }
}
