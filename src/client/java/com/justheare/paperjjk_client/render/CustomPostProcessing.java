package com.justheare.paperjjk_client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import org.lwjgl.opengl.*;

/**
 * Custom post-processing pipeline that allows dynamic uniform updates
 * Bypasses Minecraft's immutable uniform buffer system
 */
public class CustomPostProcessing {
    private static int shaderProgram = -1;
    private static int vertexShader = -1;
    private static int fragmentShader = -1;
    private static int vao = -1;

    // Uniform locations
    private static int uEffectCenter = -1;
    private static int uEffectRadius = -1;
    private static int uEffectStrength = -1;
    private static int uTexture = -1;

    private static boolean initialized = false;

    /**
     * Initialize the custom post-processing system
     */
    public static void init() {
        if (initialized) return;

        try {
            // Compile shaders
            compileShaders();

            // Create VAO for full-screen quad
            vao = GL30.glGenVertexArrays();

            initialized = true;
            System.out.println("[CustomPostProcessing] Initialized successfully");
        } catch (Exception e) {
            System.err.println("[CustomPostProcessing] Failed to initialize:");
            e.printStackTrace();
        }
    }

    /**
     * Compile vertex and fragment shaders
     */
    private static void compileShaders() {
        // Vertex shader (simple full-screen quad)
        String vertexSource = """
            #version 330 core

            out vec2 texCoord;

            void main() {
                vec2 uv = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
                gl_Position = vec4(uv * 2.0 - 1.0, 0.0, 1.0);
                texCoord = uv;
            }
            """;

        // Fragment shader (refraction effect)
        String fragmentSource = """
            #version 330 core

            uniform sampler2D uTexture;
            uniform vec2 uEffectCenter;
            uniform float uEffectRadius;
            uniform float uEffectStrength;

            in vec2 texCoord;
            out vec4 fragColor;

            void main() {
                vec2 toCenter = texCoord - uEffectCenter;
                float dist = length(toCenter);
                vec2 sampleCoord = texCoord;

                if (dist < uEffectRadius && dist > 0.0) {
                    float normalizedDist = dist / uEffectRadius;
                    float falloff = 1.0 - smoothstep(0.0, 1.0, normalizedDist);
                    float distortAmount = uEffectStrength * falloff / dist;
                    sampleCoord = texCoord + toCenter * distortAmount;
                }

                fragColor = texture(uTexture, sampleCoord);
            }
            """;

        // Compile vertex shader
        vertexShader = GL20.glCreateShader(GL20.GL_VERTEX_SHADER);
        GL20.glShaderSource(vertexShader, vertexSource);
        GL20.glCompileShader(vertexShader);

        if (GL20.glGetShaderi(vertexShader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            System.err.println("[CustomPostProcessing] Vertex shader compilation failed:");
            System.err.println(GL20.glGetShaderInfoLog(vertexShader));
            return;
        }

        // Compile fragment shader
        fragmentShader = GL20.glCreateShader(GL20.GL_FRAGMENT_SHADER);
        GL20.glShaderSource(fragmentShader, fragmentSource);
        GL20.glCompileShader(fragmentShader);

        if (GL20.glGetShaderi(fragmentShader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            System.err.println("[CustomPostProcessing] Fragment shader compilation failed:");
            System.err.println(GL20.glGetShaderInfoLog(fragmentShader));
            return;
        }

        // Link program
        shaderProgram = GL20.glCreateProgram();
        GL20.glAttachShader(shaderProgram, vertexShader);
        GL20.glAttachShader(shaderProgram, fragmentShader);
        GL20.glLinkProgram(shaderProgram);

        if (GL20.glGetProgrami(shaderProgram, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            System.err.println("[CustomPostProcessing] Shader program linking failed:");
            System.err.println(GL20.glGetProgramInfoLog(shaderProgram));
            return;
        }

        // Get uniform locations
        uTexture = GL20.glGetUniformLocation(shaderProgram, "uTexture");
        uEffectCenter = GL20.glGetUniformLocation(shaderProgram, "uEffectCenter");
        uEffectRadius = GL20.glGetUniformLocation(shaderProgram, "uEffectRadius");
        uEffectStrength = GL20.glGetUniformLocation(shaderProgram, "uEffectStrength");

        System.out.println("[CustomPostProcessing] Shaders compiled successfully");
    }

    /**
     * Render post-processing effect
     * Copies framebuffer, applies distortion, and draws back
     */
    public static void render(float centerX, float centerY, float radius, float strength) {
        if (!initialized) {
            init();
            if (!initialized) return;
        }

        try {
            RenderSystem.assertOnRenderThread();
            MinecraftClient client = MinecraftClient.getInstance();
            Framebuffer mainFramebuffer = client.getFramebuffer();

            // Get the framebuffer's color texture ID using reflection
            int textureId = getFramebufferTextureId(mainFramebuffer);
            if (textureId == -1) {
                System.err.println("[CustomPostProcessing] Failed to get framebuffer texture ID");
                return;
            }

            // Log every 5 seconds
            if (System.currentTimeMillis() % 5000 < 16) {
                System.out.println("[CustomPostProcessing] Rendering effect: center=(" +
                    String.format("%.3f", centerX) + ", " + String.format("%.3f", centerY) +
                    "), radius=" + String.format("%.3f", radius) +
                    ", strength=" + String.format("%.3f", strength));
            }

            // Save GL state
            int prevProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            int prevVAO = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
            int prevTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            boolean depthTestEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
            boolean blendEnabled = GL11.glIsEnabled(GL11.GL_BLEND);

            // Setup render state for post-processing
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_BLEND); // No blending - replace entire screen

            // Set viewport to match screen
            GL11.glViewport(0, 0, mainFramebuffer.textureWidth, mainFramebuffer.textureHeight);

            // Bind VAO and shader program
            GL30.glBindVertexArray(vao);
            GL20.glUseProgram(shaderProgram);

            // Bind framebuffer texture
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);

            // Set texture parameters for proper sampling
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);

            // Set uniforms (THIS IS THE KEY - we can update these every frame!)
            GL20.glUniform2f(uEffectCenter, centerX, centerY);
            GL20.glUniform1f(uEffectRadius, radius);
            GL20.glUniform1f(uEffectStrength, strength);
            GL20.glUniform1i(uTexture, 0);

            // Draw full-screen quad (covers entire screen with distortion effect)
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);

            // Restore previous state
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, prevTexture);
            GL20.glUseProgram(prevProgram);
            GL30.glBindVertexArray(prevVAO);
            if (depthTestEnabled) GL11.glEnable(GL11.GL_DEPTH_TEST);
            if (blendEnabled) GL11.glEnable(GL11.GL_BLEND);

        } catch (Exception e) {
            System.err.println("[CustomPostProcessing] Error during render:");
            e.printStackTrace();
        }
    }

    /**
     * Get framebuffer color texture ID via reflection
     */
    private static int getFramebufferTextureId(Framebuffer framebuffer) {
        try {
            // Get colorAttachment field
            java.lang.reflect.Field colorAttachmentField = Framebuffer.class.getDeclaredField("colorAttachment");
            colorAttachmentField.setAccessible(true);
            Object colorAttachment = colorAttachmentField.get(framebuffer);

            // Call getGlId() method
            java.lang.reflect.Method getGlIdMethod = colorAttachment.getClass().getMethod("getGlId");
            return (int) getGlIdMethod.invoke(colorAttachment);
        } catch (Exception e) {
            System.err.println("[CustomPostProcessing] Failed to get texture ID: " + e.getMessage());
            return -1;
        }
    }

    /**
     * Cleanup resources
     */
    public static void cleanup() {
        if (shaderProgram != -1) {
            GL20.glDeleteProgram(shaderProgram);
        }
        if (vertexShader != -1) {
            GL20.glDeleteShader(vertexShader);
        }
        if (fragmentShader != -1) {
            GL20.glDeleteShader(fragmentShader);
        }
        if (vao != -1) {
            GL30.glDeleteVertexArrays(vao);
        }
        initialized = false;
    }
}
