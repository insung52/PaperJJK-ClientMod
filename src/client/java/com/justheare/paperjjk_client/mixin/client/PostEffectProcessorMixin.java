package com.justheare.paperjjk_client.mixin.client;

import com.justheare.paperjjk_client.shader.RefractionEffectManager;
import com.justheare.paperjjk_client.util.WorldToScreenUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.FrameGraphBuilder;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Mixin to intercept PostEffectProcessor rendering and update uniforms
 */
@Mixin(PostEffectProcessor.class)
public class PostEffectProcessorMixin {
    private static boolean refractionInspected = false;

    @Inject(method = "render", at = @At("HEAD"))
    private void onRender(FrameGraphBuilder frameGraphBuilder, int width, int height,
                         PostEffectProcessor.FramebufferSet framebufferSet, CallbackInfo ci) {
        PostEffectProcessor processor = (PostEffectProcessor) (Object) this;

        // Check if this is the refraction shader
        boolean isRefractionShader = isRefractionShader(processor);

        // Inspect refraction shader structure once
        if (isRefractionShader && !refractionInspected) {
            System.out.println("[PostEffectProcessorMixin] Detected refraction shader - inspecting...");
            inspectPostProcessor(processor);
            refractionInspected = true;
        }

        // Update refraction uniforms if this is refraction shader and we have an active effect
        if (isRefractionShader) {
            RefractionEffectManager.RefractionEffect effect = RefractionEffectManager.getPrimaryEffect();
            if (effect != null) {
                updateRefractionUniforms(processor, effect);
            }
        }
    }

    private boolean isRefractionShader(PostEffectProcessor processor) {
        try {
            Field passesField = processor.getClass().getDeclaredField("passes");
            passesField.setAccessible(true);
            List<?> passes = (List<?>) passesField.get(processor);

            if (!passes.isEmpty()) {
                Object firstPass = passes.get(0);
                Field idField = firstPass.getClass().getDeclaredField("id");
                idField.setAccessible(true);
                String passId = (String) idField.get(firstPass);

                // Check if any pass contains "refraction"
                return passId != null && passId.contains("refraction");
            }
        } catch (Exception e) {
            // Ignore
        }
        return false;
    }

    private void inspectPostProcessor(PostEffectProcessor processor) {
        System.out.println("[PostEffectProcessorMixin] === Inspecting PostEffectProcessor instance ===");

        try {
            // Get passes field
            Field passesField = processor.getClass().getDeclaredField("passes");
            passesField.setAccessible(true);
            List<?> passes = (List<?>) passesField.get(processor);

            System.out.println("[PostEffectProcessorMixin] Found " + passes.size() + " passes");

            // Inspect ALL passes to find RefractionConfig
            for (int i = 0; i < passes.size(); i++) {
                Object pass = passes.get(i);

                // Get pass ID
                Field idField = pass.getClass().getDeclaredField("id");
                idField.setAccessible(true);
                String passId = (String) idField.get(pass);

                System.out.println("[PostEffectProcessorMixin] Pass " + i + ": " + passId);

                // Get uniformBuffers from pass
                Field uniformBuffersField = pass.getClass().getDeclaredField("uniformBuffers");
                uniformBuffersField.setAccessible(true);
                Object uniformBuffers = uniformBuffersField.get(pass);

                if (uniformBuffers instanceof java.util.Map) {
                    java.util.Map<?, ?> uniformMap = (java.util.Map<?, ?>) uniformBuffers;

                    // Print all uniform buffer names for this pass
                    for (Object key : uniformMap.keySet()) {
                        Object value = uniformMap.get(key);
                        System.out.println("[PostEffectProcessorMixin]   Uniform: " + key + " -> " + value.getClass().getName());

                        // Inspect RefractionConfig if found
                        if (key.toString().equals("RefractionConfig")) {
                            System.out.println("[PostEffectProcessorMixin]   >>> Found RefractionConfig in pass " + i + "!");
                            System.out.println("[PostEffectProcessorMixin]     Class hierarchy:");

                            // Print class hierarchy
                            Class<?> currentClass = value.getClass();
                            while (currentClass != null) {
                                System.out.println("[PostEffectProcessorMixin]       - " + currentClass.getName());
                                currentClass = currentClass.getSuperclass();
                            }

                            // Inspect ALL methods including inherited (print everything)
                            System.out.println("[PostEffectProcessorMixin]     All methods (from GpuBuffer):");
                            currentClass = value.getClass().getSuperclass(); // GpuBuffer
                            if (currentClass != null) {
                                Method[] gpuBufferMethods = currentClass.getDeclaredMethods();
                                for (Method um : gpuBufferMethods) {
                                    System.out.println("[PostEffectProcessorMixin]       " + um.toString());
                                }
                            }

                            // Inspect BufferManager
                            System.out.println("[PostEffectProcessorMixin]     Inspecting BufferManager:");
                            try {
                                Field bufferManagerField = value.getClass().getDeclaredField("bufferManager");
                                bufferManagerField.setAccessible(true);
                                Object bufferManager = bufferManagerField.get(value);
                                System.out.println("[PostEffectProcessorMixin]       BufferManager class: " + bufferManager.getClass().getName());

                                Method[] bmMethods = bufferManager.getClass().getDeclaredMethods();
                                for (Method bm : bmMethods) {
                                    System.out.println("[PostEffectProcessorMixin]       " + bm.toString());
                                }
                            } catch (Exception e4) {
                                System.err.println("[PostEffectProcessorMixin]       Error: " + e4.getMessage());
                            }

                            // Check all fields including inherited
                            System.out.println("[PostEffectProcessorMixin]     All fields:");
                            currentClass = value.getClass();
                            while (currentClass != null) {
                                Field[] fields = currentClass.getDeclaredFields();
                                for (Field f : fields) {
                                    f.setAccessible(true);
                                    try {
                                        Object fieldValue = f.get(value);
                                        System.out.println("[PostEffectProcessorMixin]       " + f.getName() + " = " + fieldValue);
                                    } catch (Exception e3) {
                                        System.out.println("[PostEffectProcessorMixin]       " + f.getName() + " (error reading)");
                                    }
                                }
                                currentClass = currentClass.getSuperclass();
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[PostEffectProcessorMixin] Error inspecting:");
            e.printStackTrace();
        }
    }

    private void updateRefractionUniforms(PostEffectProcessor processor, RefractionEffectManager.RefractionEffect effect) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.gameRenderer == null) return;

        Camera camera = client.gameRenderer.getCamera();
        Matrix4f projectionMatrix = new Matrix4f(client.gameRenderer.getBasicProjectionMatrix(client.options.getFov().getValue().floatValue()));

        // Convert world position to screen coordinates
        Vec3d screenPos = WorldToScreenUtil.worldToScreen(effect.worldPos, camera, projectionMatrix);

        if (screenPos != null) {
            // Update via texture (works around immutable buffer issue)
            com.justheare.paperjjk_client.shader.UniformTextureManager.updateUniforms(screenPos, effect.radius, effect.strength);

            // Log every 60 frames to avoid spam
            if (System.currentTimeMillis() % 1000 < 16) {
                System.out.println("[PostEffectProcessorMixin] Updated uniforms: center=(" +
                    String.format("%.3f", screenPos.x) + ", " +
                    String.format("%.3f", screenPos.y) + "), radius=" +
                    String.format("%.2f", effect.radius) + ", strength=" +
                    String.format("%.3f", effect.strength));
            }
        }
    }
}
