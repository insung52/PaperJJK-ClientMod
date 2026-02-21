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
    private static int uDepthTexture = -1;  // Step 1: Add depth texture uniform location
    private static int uEffectDepth = -1;   // Step 5: Effect depth uniform for occlusion testing    private static int uAspectRatio = -1;
    private static int uTime = -1;          // Bloom: Time for spiral animation
    private static int uEffectType = -1;    // Effect type (0=AO, 1=AKA, 2=MURASAKI)
    private static int uAspectRatio = -1;

    // Temporary FBO and textures for post-processing
    // We need TWO textures: one for reading (source), one for writing (destination)
    private static int tempFbo = -1;
    private static int sourceTexture = -1;  // Texture we read from (contains original frame)
    private static int destTexture = -1;    // Texture we write to (contains distorted result)
    private static int copiedDepthTexture = -1;  // Step 2: Copied depth texture (GL_DEPTH_COMPONENT32)
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
            // System.err.println("[CustomPostProcessing] Failed to initialize:");
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
            uniform sampler2D uDepthTexture;  // Step 1: Add depth texture uniform
            uniform vec2 uEffectCenter;
            uniform float uEffectRadius;
            uniform float uEffectStrength;
            uniform float uEffectDepth;       // Step 5: Effect depth for occlusion testing
            uniform float uTime;
            uniform int uEffectType;          // 0=AO (blue), 1=AKA (red), 2=MURASAKI (purple)
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

                // STEP 1: Calculate effective radius using absolute value
                float absStrength = abs(uEffectStrength);
                float effectRadius = uEffectRadius * absStrength;

                // Default: sample from current position (no distortion)
                vec2 sampleCoord = texCoord;

                if (dist < effectRadius && dist > 0.0001) {
                   // Normalize distance (0.0 at center, 1.0 at edge)
                   float normalizedDist = dist / effectRadius;

                   // Smooth falloff from center to edge
                   float falloff = 1.0 - smoothstep(0.0, 1.0, normalizedDist);

                   // Calculate distortion amount (stronger at center, weaker at edge)
                   // Positive strength: pulls toward center (AO - attraction)
                   // Negative strength: pushes away from center (AKA - repulsion)
                   float distortAmount = uEffectStrength * 0.05 * falloff / dist;

                   // AO (blue) has half distortion strength (range stays same)
                   if (uEffectType == 0) {
                       distortAmount *= 0.2;
                   }

                   // Apply distortion: move sample point
                   sampleCoord = texCoord + toCenter * distortAmount;

                   // Clamp to valid texture coordinates
                   sampleCoord = clamp(sampleCoord, 0.0, 1.0);
                }

                // Step 6: Occlusion test - check if effect is behind geometry
                // Sample depth at current pixel
                float pixelDepth = texture(uDepthTexture, texCoord).r;

                // If pixel depth < effect depth, geometry is in front (occlusion)
                // Small epsilon for depth comparison to avoid precision issues
                if (pixelDepth < uEffectDepth - 0.0001) {
                    // Effect is occluded, skip distortion and bloom
                    fragColor = texture(uTexture, texCoord);
                    return;
                }

                // Sample the texture at the (possibly distorted) coordinates
                // Note: sampleCoord is already in flipped OpenGL space, so we can use it directly
                vec4 color = texture(uTexture, sampleCoord);

                // Bloom effect - color depends on strength sign
                // Enhanced bloom: Soft Edge + Spiral + Noise
                // AO (blue) uses smaller bloom radius (0.6x instead of 1.2x)
                float bloomRadiusMultiplier = (uEffectType == 0) ? 0.4 : 1.2;

                if (dist < effectRadius * bloomRadiusMultiplier) {
                    float normalizedDist = dist / effectRadius;

                    // A: Soft Edge - smooth fade at boundary
                    // Scale fade range to match bloomRadiusMultiplier
                    float fadeStart = 0.8 * bloomRadiusMultiplier / 1.2;  // AO: 0.4, AKA: 0.8
                    float edgeFade = 1.0 - smoothstep(fadeStart, bloomRadiusMultiplier, normalizedDist);

                    // Base bloom intensity scaled by absolute strength
                    float bloomFactor = exp(-normalizedDist * 4.0) * 5.0 * absStrength;

                    // E: Spiral effect - rotating vortex pattern
                    float angle = atan(toCenter.y, toCenter.x);
                    float spiral = sin(angle * 6.0 + dist * 10.0 - uTime * 3.0) * 0.5 + 0.5;
                    bloomFactor *= (0.7 + spiral * 0.3);

                    // F: Procedural noise - animated flickering
                    float noise = fract(sin(dot(texCoord * 100.0 + uTime * 0.5, vec2(12.9898, 78.233))) * 43758.5453);
                    bloomFactor *= (0.8 + noise * 0.8);

                    // Apply soft edge fade
                    bloomFactor *= edgeFade;

                    // AO (blue) bloom is half intensity
                    if (uEffectType == 0) {
                        bloomFactor *= 0.5;
                    }

                    // Bloom color depends on effect type
                    vec3 bloomColor;
                    if (uEffectType == 0) {
                        // AO: Blue attraction
                        bloomColor = vec3(0.2, 0.6, 1.0);
                    } else if (uEffectType == 1) {
                        // AKA: Red repulsion
                        bloomColor = vec3(1.0, 0.2, 0.3);
                    } else {
                        // MURASAKI: Purple expansion
                        bloomColor = vec3(0.8, 0.2, 1.0);
                    }

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
            // System.err.println("[CustomPostProcessing] Vertex shader compilation failed:");
            System.err.println(GL20.glGetShaderInfoLog(vertexShader));
            return;
        }

        // Compile fragment shader
        fragmentShader = GL20.glCreateShader(GL20.GL_FRAGMENT_SHADER);
        GL20.glShaderSource(fragmentShader, fragmentSource);
        GL20.glCompileShader(fragmentShader);

        if (GL20.glGetShaderi(fragmentShader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            // System.err.println("[CustomPostProcessing] Fragment shader compilation failed:");
            System.err.println(GL20.glGetShaderInfoLog(fragmentShader));
            return;
        }

        // Link program
        shaderProgram = GL20.glCreateProgram();
        GL20.glAttachShader(shaderProgram, vertexShader);
        GL20.glAttachShader(shaderProgram, fragmentShader);
        GL20.glLinkProgram(shaderProgram);

        if (GL20.glGetProgrami(shaderProgram, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            // System.err.println("[CustomPostProcessing] Shader program linking failed:");
            System.err.println(GL20.glGetProgramInfoLog(shaderProgram));
            return;
        }

        // Get uniform locations
        uTexture = GL20.glGetUniformLocation(shaderProgram, "uTexture");
        uDepthTexture = GL20.glGetUniformLocation(shaderProgram, "uDepthTexture");  // Step 1
        uEffectCenter = GL20.glGetUniformLocation(shaderProgram, "uEffectCenter");
        uEffectRadius = GL20.glGetUniformLocation(shaderProgram, "uEffectRadius");
        uEffectStrength = GL20.glGetUniformLocation(shaderProgram, "uEffectStrength");
        uEffectDepth = GL20.glGetUniformLocation(shaderProgram, "uEffectDepth");    // Step 5
        uTime = GL20.glGetUniformLocation(shaderProgram, "uTime");
        uEffectType = GL20.glGetUniformLocation(shaderProgram, "uEffectType");
        uAspectRatio = GL20.glGetUniformLocation(shaderProgram, "uAspectRatio");

    }

    /**
     * Render post-processing effect
     * Copies framebuffer, applies distortion, and draws back
     * @param effectType 0=AO (blue), 1=AKA (red), 2=MURASAKI (purple)
     */
    public static void render(float centerX, float centerY, float radius, float strength, float effectDepth, int effectType) {
        com.justheare.paperjjk_client.DebugConfig.log("CustomPostProcessing",
            "render() CALLED - Center: (" + String.format("%.3f", centerX) + "," + String.format("%.3f", centerY) +
            "), Radius: " + String.format("%.3f", radius) + ", Strength: " + String.format("%.3f", strength) +
            ", Depth: " + String.format("%.3f", effectDepth) + ", Type: " + effectType);

        if (!initialized) {
            com.justheare.paperjjk_client.DebugConfig.log("CustomPostProcessing", "Not initialized, calling init()...");
            init();
            if (!initialized) {
                com.justheare.paperjjk_client.DebugConfig.log("CustomPostProcessing", "ERROR: Initialization failed!");
                return;
            }
            com.justheare.paperjjk_client.DebugConfig.log("CustomPostProcessing", "Initialization complete");
        }

        try {
            RenderSystem.assertOnRenderThread();
            MinecraftClient client = MinecraftClient.getInstance();
            Framebuffer mainFramebuffer = client.getFramebuffer();

            com.justheare.paperjjk_client.DebugConfig.log("CustomPostProcessing",
                "Framebuffer size: " + mainFramebuffer.textureWidth + "x" + mainFramebuffer.textureHeight);

            // Save current FBO for state restore at end
            int savedFbo = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);

            // We are called just before blitToScreen(), so client.getFramebuffer() already
            // contains the fully composited scene (gameRenderer.render() has finished).
            // We read from it, apply distortion, and write back to it — same framebuffer.
            com.mojang.blaze3d.textures.GpuTexture colorAttachmentGpu = mainFramebuffer.getColorAttachment();
            if (colorAttachmentGpu == null) {
                com.justheare.paperjjk_client.DebugConfig.log("CustomPostProcessing", "ERROR: colorAttachment is null");
                return;
            }
            net.minecraft.client.texture.GlTexture glTexture = (net.minecraft.client.texture.GlTexture) colorAttachmentGpu;
            int mainTextureId = glTexture.getGlId();
            System.out.println("[CustomPostProcessing] mainTextureId=" + mainTextureId + " fb=" + mainFramebuffer.getClass().getSimpleName() + " size=" + mainFramebuffer.textureWidth + "x" + mainFramebuffer.textureHeight);

            if (mainTextureId <= 0) {
                System.out.println("[CustomPostProcessing] ERROR: invalid mainTextureId: " + mainTextureId);
                return;
            }

            // Read and write the same framebuffer (blitToScreen reads it next)
            int presentTextureId = mainTextureId;

            // Get depth texture ID - prioritize Iris depthtex2 (world depth without hand)
            int depthTextureId = getIrisWorldDepthTexture();
            if (depthTextureId == -1) {
                depthTextureId = getFramebufferDepthTextureId(mainFramebuffer);
            }

            // Create or resize temp framebuffer if needed
            if (tempFbo == -1 ||
                tempWidth != mainFramebuffer.textureWidth ||
                tempHeight != mainFramebuffer.textureHeight) {

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
                if (copiedDepthTexture != -1) {
                    GL11.glDeleteTextures(copiedDepthTexture);
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

                // Step 2: Create copied depth texture with GL_DEPTH_COMPONENT32F (0x81a7)
                // CRITICAL: Must ensure active texture unit is 0 before creating textures
                GL13.glActiveTexture(GL13.GL_TEXTURE0);
                copiedDepthTexture = GL11.glGenTextures();
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, copiedDepthTexture);
                // Use 0x81a7 (GL_DEPTH_COMPONENT32F) - confirmed from runtime query
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, 0x81a7,
                    mainFramebuffer.textureWidth, mainFramebuffer.textureHeight,
                    0, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, (java.nio.ByteBuffer) null);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
                // CRITICAL: Disable depth comparison mode for shader sampling
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_COMPARE_MODE, GL11.GL_NONE);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
                // System.out.println("[CustomPostProcessing] Step 2: Created copied depth texture (0x81a7)");

                // Create FBO and attach destination texture
                tempFbo = GL30.glGenFramebuffers();
                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, tempFbo);
                GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                    GL11.GL_TEXTURE_2D, destTexture, 0);

                int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
                if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
                    // System.err.println("[CustomPostProcessing] Framebuffer incomplete: " + status);
                    GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, savedFbo);
                    return;
                }

                // Restore previous FBO binding
                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, savedFbo);

                tempWidth = mainFramebuffer.textureWidth;
                tempHeight = mainFramebuffer.textureHeight;
            }


            // CRITICAL: If tempFbo and mainFbo are the same, we can't proceed!
            // STEP 2: Copy mainFramebuffer's color texture to sourceTexture via temp READ FBO.
            // We wrap mainTextureId (the actual GlTexture GL ID) in a temporary FBO so we
            // can blit from it — this is the correct way in MC 1.21 since glGetInteger
            // (FRAMEBUFFER_BINDING) may return 0 and not the actual rendering framebuffer.
            {
                int tempReadFbo = GL30.glGenFramebuffers();
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, tempReadFbo);
                GL30.glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                    GL11.GL_TEXTURE_2D, mainTextureId, 0);

                int tempWriteFbo2 = GL30.glGenFramebuffers();
                GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, tempWriteFbo2);
                GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                    GL11.GL_TEXTURE_2D, sourceTexture, 0);

                int readStatus = GL30.glCheckFramebufferStatus(GL30.GL_READ_FRAMEBUFFER);
                int writeStatus = GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER);
                com.justheare.paperjjk_client.DebugConfig.log("CustomPostProcessing",
                    "Step2 FBO status READ=" + readStatus + " WRITE=" + writeStatus);

                if (readStatus == GL30.GL_FRAMEBUFFER_COMPLETE && writeStatus == GL30.GL_FRAMEBUFFER_COMPLETE) {
                    GL30.glBlitFramebuffer(0, 0, tempWidth, tempHeight,
                        0, 0, tempWidth, tempHeight,
                        GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
                }

                GL30.glDeleteFramebuffers(tempReadFbo);
                GL30.glDeleteFramebuffers(tempWriteFbo2);
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, 0);
                GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, 0);

                // Copy depth texture data (if available)
                if (depthTextureId != -1 && copiedDepthTexture != -1) {
                    int tempDepthReadFbo = GL30.glGenFramebuffers();
                    int tempDepthWriteFbo = GL30.glGenFramebuffers();

                    GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, tempDepthReadFbo);
                    GL30.glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT,
                        GL11.GL_TEXTURE_2D, depthTextureId, 0);

                    int depthReadStatus = GL30.glCheckFramebufferStatus(GL30.GL_READ_FRAMEBUFFER);
                    if (depthReadStatus == GL30.GL_FRAMEBUFFER_COMPLETE) {
                        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, tempDepthWriteFbo);
                        GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT,
                            GL11.GL_TEXTURE_2D, copiedDepthTexture, 0);

                        int depthWriteStatus = GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER);
                        if (depthWriteStatus == GL30.GL_FRAMEBUFFER_COMPLETE) {
                            GL30.glBlitFramebuffer(0, 0, tempWidth, tempHeight,
                                0, 0, tempWidth, tempHeight,
                                GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);
                        }
                    }

                    GL30.glDeleteFramebuffers(tempDepthReadFbo);
                    GL30.glDeleteFramebuffers(tempDepthWriteFbo);
                    GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, 0);
                    GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, 0);
                }
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

            // Bind the source texture (undistorted frame) to unit 0
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, sourceTexture);

            // Step 3: Bind Iris depthtex2 (world depth, no hand) directly to GL_TEXTURE5
            if (depthTextureId != -1) {
                // Use GL_TEXTURE5 to avoid conflicts with Minecraft's texture units
                GL13.glActiveTexture(GL13.GL_TEXTURE5);
                // Bind Iris depthtex2 directly (no copy needed - Iris already provides the correct depth)
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, depthTextureId);
                // CRITICAL: Restore to unit 0 immediately
                GL13.glActiveTexture(GL13.GL_TEXTURE0);
                // System.out.println("[CustomPostProcessing] Step 3: Iris depthtex2 (world depth) bound to unit 5");
            }

            // Calculate aspect ratio (width / height)
            float aspectRatio = (float) tempWidth / (float) tempHeight;

            // Set uniforms for distortion
            GL20.glUniform2f(uEffectCenter, centerX, centerY);
            GL20.glUniform1f(uEffectRadius, radius);
            GL20.glUniform1f(uEffectStrength, strength); // 왜곡 강도 3배 증가
            GL20.glUniform1f(uEffectDepth, effectDepth);        // Step 5: Pass effect depth
            GL20.glUniform1f(uTime, (float)(System.currentTimeMillis() % 10000) / 1000.0f);
            GL20.glUniform1i(uEffectType, effectType);          // Pass effect type for color
            GL20.glUniform1f(uAspectRatio, aspectRatio);
            GL20.glUniform1i(uTexture, 0);
            // Step 3: Set depth texture uniform to unit 5 (not 1)
            if (depthTextureId != -1 && copiedDepthTexture != -1) {
                GL20.glUniform1i(uDepthTexture, 5);
            }

            // Draw distorted quad to destTexture (attached to tempFbo)
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);

            // CRITICAL: Immediately unbind depth texture from unit 5 BEFORE any other operations
            if (depthTextureId != -1) {
                GL13.glActiveTexture(GL13.GL_TEXTURE5);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
                GL13.glActiveTexture(GL13.GL_TEXTURE0);
                // System.out.println("[CustomPostProcessing] Step 3: Immediately unbound depth from unit 5 after draw");
            }

            // STEP 4: Blit distorted result (destTexture via tempFbo) back to presentTextureId.
            // presentTextureId is client.getFramebuffer()'s texture (WindowFramebuffer) —
            // what MC's blitToScreen() reads from when presenting the frame.
            {
                int tempWriteFbo = GL30.glGenFramebuffers();
                GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, tempWriteFbo);
                GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                    GL11.GL_TEXTURE_2D, presentTextureId, 0);

                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, tempFbo);

                GL30.glBlitFramebuffer(0, 0, tempWidth, tempHeight,
                    0, 0, tempWidth, tempHeight,
                    GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);

                GL30.glDeleteFramebuffers(tempWriteFbo);
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, 0);
                GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, 0);
            }

            // Restore state
            if (depthTextureId != -1) {
                GL13.glActiveTexture(GL13.GL_TEXTURE5);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            }
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);

            GL30.glBindVertexArray(0);
            GL20.glUseProgram(0);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, savedFbo);

            // Restore GL state
            if (depthTestEnabled) GL11.glEnable(GL11.GL_DEPTH_TEST);
            if (blendEnabled) GL11.glEnable(GL11.GL_BLEND);

            com.justheare.paperjjk_client.DebugConfig.log("CustomPostProcessing",
                "=== render() COMPLETED SUCCESSFULLY ===");

        } catch (Exception e) {
            com.justheare.paperjjk_client.DebugConfig.log("CustomPostProcessing",
                "ERROR during render: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Get framebuffer FBO ID via reflection
     */
    private static int getFramebufferFboId(Framebuffer framebuffer) {
        try {
            // Debug: Print all fields to find the correct one
            // System.out.println("[CustomPostProcessing] Framebuffer fields:");
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
                        // System.out.println("[CustomPostProcessing] Found FBO ID in field '" + fieldName + "': " + id);
                        return id;
                    }

                    // If it's an object with getGlId(), call it
                    try {
                        java.lang.reflect.Method getGlIdMethod = value.getClass().getMethod("getGlId");
                        int id = (int) getGlIdMethod.invoke(value);
                        // System.out.println("[CustomPostProcessing] Found FBO ID via " + fieldName + ".getGlId(): " + id);
                        return id;
                    } catch (Exception ignored) {}
                } catch (NoSuchFieldException ignored) {}
            }

            // System.err.println("[CustomPostProcessing] Could not find FBO ID in any known field");
            return -1;
        } catch (Exception e) {
            // System.err.println("[CustomPostProcessing] Failed to get FBO ID: " + e.getMessage());
            e.printStackTrace();
            return -1;
        }
    }

    /**
     * Get framebuffer depth texture ID via reflection
     */
    private static int getFramebufferDepthTextureId(Framebuffer framebuffer) {
        try {
            // Get depthAttachment field
            java.lang.reflect.Field depthAttachmentField = Framebuffer.class.getDeclaredField("depthAttachment");
            depthAttachmentField.setAccessible(true);
            Object depthAttachment = depthAttachmentField.get(framebuffer);

            if (depthAttachment == null) {
                // System.err.println("[CustomPostProcessing] Depth attachment is null");
                return -1;
            }

            // Get the GL ID from the depth attachment (GpuTexture type)
            java.lang.reflect.Method getGlIdMethod = depthAttachment.getClass().getMethod("getGlId");
            int depthTextureId = (int) getGlIdMethod.invoke(depthAttachment);

            return depthTextureId;
        } catch (Exception e) {
            // System.err.println("[CustomPostProcessing] Failed to get depth texture ID: " + e.getMessage());
            return -1;
        }
    }

    /**
     * Get framebuffer color texture ID via reflection
     */
    private static int getFramebufferTextureId(Framebuffer framebuffer) {
        try {
            Object colorAttachment = null;
            String foundFieldName = null;

            // Strategy 1: Find by type (GpuTexture) - most reliable across versions
            for (java.lang.reflect.Field field : Framebuffer.class.getDeclaredFields()) {
                String fieldTypeName = field.getType().getName();

                // Look for GpuTexture type (color attachment)
                if (fieldTypeName.contains("GpuTexture") && !fieldTypeName.contains("GpuTextureView")) {
                    field.setAccessible(true);
                    Object fieldValue = field.get(framebuffer);

                    if (fieldValue != null) {
                        colorAttachment = fieldValue;
                        foundFieldName = field.getName();

                        com.justheare.paperjjk_client.DebugConfig.log("CustomPostProcessing",
                            "Found GpuTexture field by type: " + foundFieldName + " (" + fieldTypeName + ")");
                        break;
                    }
                }
            }

            // Strategy 2: Fallback to known field names if type search failed
            if (colorAttachment == null) {
                com.justheare.paperjjk_client.DebugConfig.log("CustomPostProcessing",
                    "Type-based search failed, trying known field names...");

                String[] possibleFieldNames = {"field_1475", "colorAttachment", "field_1469", "a", "colorTexture", "mainTexture"};

                for (String fieldName : possibleFieldNames) {
                    try {
                        java.lang.reflect.Field field = Framebuffer.class.getDeclaredField(fieldName);
                        field.setAccessible(true);
                        colorAttachment = field.get(framebuffer);
                        foundFieldName = fieldName;

                        com.justheare.paperjjk_client.DebugConfig.log("CustomPostProcessing",
                            "Found color attachment via fallback field: " + fieldName);
                        break;
                    } catch (NoSuchFieldException ignored) {}
                }
            }

            if (colorAttachment == null) {
                com.justheare.paperjjk_client.DebugConfig.log("CustomPostProcessing",
                    "ERROR: Could not find color attachment field. Available fields:");

                // Print all available fields for debugging
                for (java.lang.reflect.Field field : Framebuffer.class.getDeclaredFields()) {
                    com.justheare.paperjjk_client.DebugConfig.log("CustomPostProcessing",
                        "  - " + field.getName() + " (" + field.getType().getName() + ")");
                }
                return -1;
            }

            // Now find the getGlId method
            // Strategy 1: Try known method names first for performance
            String[] knownMethodNames = {"iris$getGlId", "getGlId", "method_4624", "getId"};

            for (String methodName : knownMethodNames) {
                try {
                    java.lang.reflect.Method method = colorAttachment.getClass().getMethod(methodName);
                    if (method.getReturnType() == int.class || method.getReturnType() == Integer.class) {
                        int textureId = (int) method.invoke(colorAttachment);

                        com.justheare.paperjjk_client.DebugConfig.log("CustomPostProcessing",
                            "Got texture ID " + textureId + " via known method: " + methodName);
                        return textureId;
                    }
                } catch (NoSuchMethodException ignored) {}
            }

            // Strategy 2: Search for any method that returns int and has "id" in name
            com.justheare.paperjjk_client.DebugConfig.log("CustomPostProcessing",
                "Known methods failed, searching for int-returning methods with 'id' in name...");

            for (java.lang.reflect.Method method : colorAttachment.getClass().getMethods()) {
                String methodName = method.getName().toLowerCase();
                boolean returnsInt = method.getReturnType() == int.class || method.getReturnType() == Integer.class;
                boolean hasNoParams = method.getParameterCount() == 0;
                boolean hasIdInName = methodName.contains("id") || methodName.contains("gl");

                if (returnsInt && hasNoParams && hasIdInName) {
                    try {
                        int textureId = (int) method.invoke(colorAttachment);

                        com.justheare.paperjjk_client.DebugConfig.log("CustomPostProcessing",
                            "Got texture ID " + textureId + " via discovered method: " + method.getName());
                        return textureId;
                    } catch (Exception ignored) {}
                }
            }

            com.justheare.paperjjk_client.DebugConfig.log("CustomPostProcessing",
                "ERROR: Could not find getGlId method. Available methods:");

            // Print all available methods for debugging
            for (java.lang.reflect.Method method : colorAttachment.getClass().getMethods()) {
                if (method.getParameterCount() == 0) {
                    com.justheare.paperjjk_client.DebugConfig.log("CustomPostProcessing",
                        "  - " + method.getName() + "() -> " + method.getReturnType().getSimpleName());
                }
            }

            return -1;
        } catch (Exception e) {
            com.justheare.paperjjk_client.DebugConfig.log("CustomPostProcessing",
                "ERROR in getFramebufferTextureId: " + e.getMessage());
            e.printStackTrace();
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

    /**
     * Get Iris depthtex2 (world depth without hand) if Iris is installed
     * Returns -1 if Iris is not installed or depthtex2 is not available
     */
    private static int getIrisWorldDepthTexture() {
        try {
            // Check if Iris is loaded by attempting to access its main class
            Class<?> irisClass = Class.forName("net.irisshaders.iris.Iris");

            // Get the PipelineManager
            java.lang.reflect.Method getPipelineManagerMethod = irisClass.getMethod("getPipelineManager");
            Object pipelineManager = getPipelineManagerMethod.invoke(null);

            // Get the current pipeline (WorldRenderingPipeline)
            java.lang.reflect.Method getPipelineMethod = pipelineManager.getClass().getMethod("getPipelineNullable");
            Object pipeline = getPipelineMethod.invoke(pipelineManager);

            if (pipeline == null) {
                // System.out.println("[CustomPostProcessing] Iris: No active pipeline");
                return -1;
            }

            // Check if it's an IrisRenderingPipeline (not vanilla)
            if (!pipeline.getClass().getName().equals("net.irisshaders.iris.pipeline.IrisRenderingPipeline")) {
                // System.out.println("[CustomPostProcessing] Iris: Vanilla pipeline active, no shader depth available");
                return -1;
            }

            // Access the renderTargets field (private, need reflection)
            java.lang.reflect.Field renderTargetsField = pipeline.getClass().getDeclaredField("renderTargets");
            renderTargetsField.setAccessible(true);
            Object renderTargets = renderTargetsField.get(pipeline);

            if (renderTargets == null) {
                // System.err.println("[CustomPostProcessing] Iris: renderTargets is null");
                return -1;
            }

            // Get depthtex2 (noHand) - world depth without hand rendering
            java.lang.reflect.Method getDepthNoHandMethod = renderTargets.getClass().getMethod("getDepthTextureNoHand");
            Object depthTextureNoHand = getDepthNoHandMethod.invoke(renderTargets);

            if (depthTextureNoHand == null) {
                // System.err.println("[CustomPostProcessing] Iris: depthtex2 (noHand) is null");
                return -1;
            }

            // Get the OpenGL texture ID via iris$getGlId()
            java.lang.reflect.Method getGlIdMethod = depthTextureNoHand.getClass().getMethod("iris$getGlId");
            int textureId = (int) getGlIdMethod.invoke(depthTextureNoHand);

            // System.out.println("[CustomPostProcessing] Iris: Successfully accessed depthtex2 (world depth, no hand): " + textureId);
            return textureId;

        } catch (ClassNotFoundException e) {
            // System.out.println("[CustomPostProcessing] Iris not installed, using fallback depth");
            return -1;
        } catch (Exception e) {
            // System.err.println("[CustomPostProcessing] Failed to access Iris depth texture:");
            e.printStackTrace();
            return -1;
        }
    }

    /**
     * Get the actual RENDERING framebuffer from WorldRenderer.framebufferSet.mainFramebuffer.
     *
     * In MC 1.21, client.getFramebuffer() returns the WindowFramebuffer (presentation
     * framebuffer). The actual world rendering goes into DefaultFramebufferSet.mainFramebuffer,
     * a separate SimpleFramebuffer. Using the wrong one results in reading an empty texture.
     *
     * With Iris, Iris patches client.framebuffer AND framebufferSet.mainFramebuffer to point
     * to its composite output, so both work. Without Iris (vanilla), they differ.
     */
    private static net.minecraft.client.gl.Framebuffer getRenderingFramebuffer(
            net.minecraft.client.render.WorldRenderer worldRenderer) {
        try {
            java.lang.reflect.Field field = net.minecraft.client.render.WorldRenderer.class
                .getDeclaredField("framebufferSet");
            field.setAccessible(true);
            net.minecraft.client.render.DefaultFramebufferSet fbSet =
                (net.minecraft.client.render.DefaultFramebufferSet) field.get(worldRenderer);

            if (fbSet == null || fbSet.mainFramebuffer == null) return null;

            net.minecraft.client.gl.Framebuffer fb = fbSet.mainFramebuffer.get();
            if (fb == null || fb.getColorAttachment() == null) return null;

            return fb;
        } catch (Exception e) {
            com.justheare.paperjjk_client.DebugConfig.log("CustomPostProcessing",
                "getRenderingFramebuffer failed: " + e.getMessage());
            return null;
        }
    }
}
