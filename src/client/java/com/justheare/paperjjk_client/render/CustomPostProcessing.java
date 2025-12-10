package com.justheare.paperjjk_client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
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
    private static int uAspectRatio = -1;

    // Temporary FBO and textures for post-processing
    // We need TWO textures: one for reading (source), one for writing (destination)
    private static int tempFbo = -1;
    private static int sourceTexture = -1;  // Texture we read from (contains original frame)
    private static int destTexture = -1;    // Texture we write to (contains distorted result)
    private static int tempWidth = -1;
    private static int tempHeight = -1;

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

        // Fragment shader (Gravitational lens / refraction effect)
        String fragmentSource = """
            #version 330 core

            uniform sampler2D uTexture;
            uniform vec2 uEffectCenter;
            uniform float uEffectRadius;
            uniform float uEffectStrength;
            uniform float uAspectRatio;

            in vec2 texCoord;
            out vec4 fragColor;

            void main() {
                // Flip only the effect center Y coordinate (screen space to texture space)
                vec2 flippedCenter = vec2(uEffectCenter.x, 1.0 - uEffectCenter.y);

                // Apply aspect ratio correction to make circular effects actually circular
                vec2 aspectCorrectedTexCoord = vec2(texCoord.x * uAspectRatio, texCoord.y);
                vec2 aspectCorrectedCenter = vec2(flippedCenter.x * uAspectRatio, flippedCenter.y);

                // Calculate vector from current pixel to effect center (with aspect ratio correction)
                vec2 toCenter = aspectCorrectedTexCoord - aspectCorrectedCenter;
                float dist = length(toCenter);

                // Default: sample from current position (no distortion)
                vec2 sampleCoord = texCoord;

                // Apply gravitational lens distortion if within radius
                if (dist < uEffectRadius && dist > 0.0001) {
                    // Normalize distance (0.0 at center, 1.0 at edge)
                    float normalizedDist = dist / uEffectRadius;

                    // Smooth falloff from center to edge
                    float falloff = 1.0 - smoothstep(0.0, 1.0, normalizedDist);

                    // Calculate distortion amount (stronger at center, weaker at edge)
                    // Gravitational lensing pulls pixels TOWARD the center
                    float distortAmount = uEffectStrength * falloff / dist;

                    // Apply distortion: move sample point toward center
                    // This creates the "magnifying" effect of gravitational lensing
                    sampleCoord = texCoord + toCenter * distortAmount;

                    // Clamp to valid texture coordinates
                    sampleCoord = clamp(sampleCoord, 0.0, 1.0);
                }

                // Sample the texture at the (possibly distorted) coordinates
                // Note: sampleCoord is already in flipped OpenGL space, so we can use it directly
                vec4 color = texture(uTexture, sampleCoord);

                // Add blue bloom effect at the center of distortion
                if (dist < uEffectRadius) {
                    // Stronger bloom at the center, fading toward edges
                    float normalizedDist = dist / uEffectRadius;

                    // Blue glow color (bright blue)
                    vec3 bloomColor = vec3(0.2, 0.6, 1.0);

                    // Add bloom with exponential falloff for more concentrated glow
                    // Much stronger bloom intensity (50x stronger than distortion)
                    float bloomFactor = exp(-normalizedDist * 4.0) * 5.0;
                    color.rgb += bloomColor * bloomFactor;
                }

                fragColor = color;
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
        uAspectRatio = GL20.glGetUniformLocation(shaderProgram, "uAspectRatio");

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

            // In Minecraft 1.21, Framebuffer doesn't store FBO ID directly
            // Instead, we use the currently bound FBO (set by Minecraft's rendering pipeline)
            // During HudRenderCallback, the main framebuffer should already be bound

            // First, try to get the color texture's GL ID
            int mainTextureId = getFramebufferTextureId(mainFramebuffer);
            if (mainTextureId == -1) {
                System.err.println("[CustomPostProcessing] Failed to get main framebuffer texture ID");
                return;
            }

            // Get the currently bound FBO - this should be Minecraft's rendering FBO
            int currentFbo = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);

            // If it's FBO 0 (default framebuffer), we need a different approach
            // Try to bind the colorAttachment's parent FBO by querying it
            if (currentFbo == 0) {
                // Bind the texture and check what FBO it belongs to
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, mainTextureId);

                // Query which FBO this texture is attached to (this is hacky but might work)
                // Actually, we can't query this directly. Let's create our own FBO for the main texture
                //System.out.println("[CustomPostProcessing] Current FBO is 0, creating wrapper FBO for main texture");

                // We'll use a different strategy: don't copy FROM mainFbo, just render to it
                currentFbo = 0; // Keep as 0, we'll handle this specially
            }

            int mainFbo = currentFbo;
            /*System.out.println("[CustomPostProcessing] Current FBO binding: " + mainFbo +
                ", Main texture ID: " + mainTextureId +
                " (size: " + mainFramebuffer.textureWidth + "x" + mainFramebuffer.textureHeight + ")");*/

            // Create or resize temp framebuffer if needed
            if (tempFbo == -1 ||
                tempWidth != mainFramebuffer.textureWidth ||
                tempHeight != mainFramebuffer.textureHeight) {

                // Save current FBO binding
                int savedFbo = mainFbo;

                // Delete old resources
                if (tempFbo != -1) {
                    GL30.glDeleteFramebuffers(tempFbo);
                }
                if (sourceTexture != -1) {
                    GL11.glDeleteTextures(sourceTexture);
                }
                if (destTexture != -1) {
                    GL11.glDeleteTextures(destTexture);
                }

                // Create source texture (will hold copy of main framebuffer)
                sourceTexture = GL11.glGenTextures();
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, sourceTexture);
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8,
                    mainFramebuffer.textureWidth, mainFramebuffer.textureHeight,
                    0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (java.nio.ByteBuffer) null);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);

                // Create destination texture (will hold distorted result)
                destTexture = GL11.glGenTextures();
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, destTexture);
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8,
                    mainFramebuffer.textureWidth, mainFramebuffer.textureHeight,
                    0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (java.nio.ByteBuffer) null);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);

                // Create FBO and attach destination texture
                tempFbo = GL30.glGenFramebuffers();
                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, tempFbo);
                GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                    GL11.GL_TEXTURE_2D, destTexture, 0);

                int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
                if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
                    System.err.println("[CustomPostProcessing] Framebuffer incomplete: " + status);
                    GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, savedFbo);
                    return;
                }

                // Restore previous FBO binding
                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, savedFbo);

                tempWidth = mainFramebuffer.textureWidth;
                tempHeight = mainFramebuffer.textureHeight;
            }


            // CRITICAL: If tempFbo and mainFbo are the same, we can't proceed!
            if (tempFbo == mainFbo && mainFbo != 0) {
                System.err.println("[CustomPostProcessing] ERROR: tempFbo == mainFbo (" + tempFbo + "), cannot render to same framebuffer!");
                return;
            }

            // STEP 2: Copy main texture to our source texture
            // Strategy depends on whether mainFbo is 0 or not
            if (mainFbo == 0) {
                // FBO 0: We can't use glCopyTexSubImage2D from it
                // Instead, we'll use glBlitFramebuffer or texture-to-texture copy
                // Create a temporary FBO to hold mainTextureId so we can blit from it
                int tempReadFbo = GL30.glGenFramebuffers();
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, tempReadFbo);
                GL30.glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                    GL11.GL_TEXTURE_2D, mainTextureId, 0);

                // Attach sourceTexture to a write FBO
                int tempWriteFbo = GL30.glGenFramebuffers();
                GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, tempWriteFbo);
                GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                    GL11.GL_TEXTURE_2D, sourceTexture, 0);

                // Blit from mainTexture to sourceTexture
                GL30.glBlitFramebuffer(0, 0, tempWidth, tempHeight,
                    0, 0, tempWidth, tempHeight,
                    GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);

                // Cleanup temp FBOs and restore framebuffer state
                GL30.glDeleteFramebuffers(tempReadFbo);
                GL30.glDeleteFramebuffers(tempWriteFbo);
                // Unbind read/draw framebuffers to clean state
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, 0);
                GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, 0);
            } else {
                // Normal FBO: use glCopyTexSubImage2D
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, mainFbo);
                // CRITICAL: Ensure we're on texture unit 0 before binding
                GL13.glActiveTexture(GL13.GL_TEXTURE0);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, sourceTexture);
                GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, tempWidth, tempHeight);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            }

            // STEP 3: Render distorted version from sourceTexture to destTexture (via tempFbo)
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, tempFbo);
            GL11.glViewport(0, 0, tempWidth, tempHeight);

            // Save GL state
            boolean depthTestEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
            boolean blendEnabled = GL11.glIsEnabled(GL11.GL_BLEND);

            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_BLEND);

            GL30.glBindVertexArray(vao);
            GL20.glUseProgram(shaderProgram);

            // Bind the source texture (undistorted frame)
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, sourceTexture);

            // Calculate aspect ratio (width / height)
            float aspectRatio = (float) tempWidth / (float) tempHeight;

            // Set uniforms for distortion
            GL20.glUniform2f(uEffectCenter, centerX, centerY);
            GL20.glUniform1f(uEffectRadius, radius*2.0f);
            GL20.glUniform1f(uEffectStrength, strength * 6.0f); // 왜곡 강도 3배 증가
            GL20.glUniform1f(uAspectRatio, aspectRatio);
            GL20.glUniform1i(uTexture, 0);

            // Draw distorted quad to destTexture (attached to tempFbo)
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);

            // STEP 4: Copy distorted image back to main framebuffer texture
            if (mainFbo == 0) {
                // FBO 0: Use blit from destTexture to mainTextureId
                // Create temp FBO for mainTextureId
                int tempWriteFbo = GL30.glGenFramebuffers();
                GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, tempWriteFbo);
                GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                    GL11.GL_TEXTURE_2D, mainTextureId, 0);

                // Read from tempFbo (which has destTexture)
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, tempFbo);

                // Blit
                GL30.glBlitFramebuffer(0, 0, tempWidth, tempHeight,
                    0, 0, tempWidth, tempHeight,
                    GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);

                // Cleanup and restore framebuffer state
                GL30.glDeleteFramebuffers(tempWriteFbo);
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, 0);
                GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, 0);
            } else {
                // Normal FBO: Copy from tempFbo to mainFbo
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, tempFbo);
                GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, mainFbo);

                // CRITICAL: Ensure we're on texture unit 0 before binding
                GL13.glActiveTexture(GL13.GL_TEXTURE0);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, mainTextureId);
                GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, tempWidth, tempHeight);
            }

            // Restore state - ensure we're on texture unit 0
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            GL30.glBindVertexArray(0);
            GL20.glUseProgram(0);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, mainFbo);

            // Restore GL state
            if (depthTestEnabled) GL11.glEnable(GL11.GL_DEPTH_TEST);
            if (blendEnabled) GL11.glEnable(GL11.GL_BLEND);

        } catch (Exception e) {
            System.err.println("[CustomPostProcessing] Error during render:");
            e.printStackTrace();
        }
    }

    /**
     * Get framebuffer FBO ID via reflection
     */
    private static int getFramebufferFboId(Framebuffer framebuffer) {
        try {
            // Debug: Print all fields to find the correct one
            System.out.println("[CustomPostProcessing] Framebuffer fields:");
            for (java.lang.reflect.Field field : Framebuffer.class.getDeclaredFields()) {
                System.out.println("  - " + field.getName() + " (" + field.getType().getSimpleName() + ")");
            }

            // Try common field names
            String[] possibleNames = {"fbo", "glId", "id", "framebufferId", "framebuffer"};
            for (String fieldName : possibleNames) {
                try {
                    java.lang.reflect.Field field = Framebuffer.class.getDeclaredField(fieldName);
                    field.setAccessible(true);
                    Object value = field.get(framebuffer);

                    // If it's an int, return it directly
                    if (value instanceof Integer) {
                        int id = (int) value;
                        System.out.println("[CustomPostProcessing] Found FBO ID in field '" + fieldName + "': " + id);
                        return id;
                    }

                    // If it's an object with getGlId(), call it
                    try {
                        java.lang.reflect.Method getGlIdMethod = value.getClass().getMethod("getGlId");
                        int id = (int) getGlIdMethod.invoke(value);
                        System.out.println("[CustomPostProcessing] Found FBO ID via " + fieldName + ".getGlId(): " + id);
                        return id;
                    } catch (Exception ignored) {}
                } catch (NoSuchFieldException ignored) {}
            }

            System.err.println("[CustomPostProcessing] Could not find FBO ID in any known field");
            return -1;
        } catch (Exception e) {
            System.err.println("[CustomPostProcessing] Failed to get FBO ID: " + e.getMessage());
            e.printStackTrace();
            return -1;
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
        if (tempFbo != -1) {
            GL30.glDeleteFramebuffers(tempFbo);
            tempFbo = -1;
        }
        if (sourceTexture != -1) {
            GL11.glDeleteTextures(sourceTexture);
            sourceTexture = -1;
        }
        if (destTexture != -1) {
            GL11.glDeleteTextures(destTexture);
            destTexture = -1;
        }
        initialized = false;
    }
}
