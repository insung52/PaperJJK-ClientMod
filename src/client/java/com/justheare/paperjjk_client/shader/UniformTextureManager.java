package com.justheare.paperjjk_client.shader;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.nio.ByteBuffer;

/**
 * Manages a 1x1 texture for passing uniform data to shaders
 */
public class UniformTextureManager {
    private static int textureId = -1;

    public static void init() {
        if (textureId == -1) {
            textureId = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);

            // Initialize with default data
            ByteBuffer data = ByteBuffer.allocateDirect(16);
            data.putFloat(-10.0f); // EffectCenter.x
            data.putFloat(-10.0f); // EffectCenter.y
            data.putFloat(0.3f);   // EffectRadius
            data.putFloat(0.05f);  // EffectStrength
            data.flip();

            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, org.lwjgl.opengl.GL30.GL_RGBA32F, 1, 1, 0, GL11.GL_RGBA, GL11.GL_FLOAT, data);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);

            System.out.println("[UniformTextureManager] Created uniform texture with id=" + textureId);
        }
    }

    public static void updateUniforms(Vec3d screenPos, float radius, float strength) {
        if (textureId == -1) init();

        ByteBuffer data = ByteBuffer.allocateDirect(16);
        data.putFloat((float) screenPos.x);
        data.putFloat((float) screenPos.y);
        data.putFloat(radius);
        data.putFloat(strength);
        data.flip();

        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 1, 1, GL11.GL_RGBA, GL11.GL_FLOAT, data);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
    }

    public static int getTextureId() {
        if (textureId == -1) init();
        return textureId;
    }

    public static void cleanup() {
        if (textureId != -1) {
            GL11.glDeleteTextures(textureId);
            textureId = -1;
        }
    }
}
