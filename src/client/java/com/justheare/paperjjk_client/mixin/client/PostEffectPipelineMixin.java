package com.justheare.paperjjk_client.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.List;
import java.util.Map;

/**
 * Mixin to modify uniform values when PostEffectProcessor is created
 */
@Mixin(targets = "net.minecraft.client.gl.PostEffectPipeline$Pass")
public class PostEffectPipelineMixin {

    @ModifyArg(
        method = "<init>",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gl/PostEffectPass;<init>(Ljava/lang/String;Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/util/Identifier;Ljava/util/List;Ljava/util/Map;)V"),
        index = 4
    )
    private static Map<String, List<Object>> modifyUniforms(Map<String, List<Object>> uniforms) {
        // Check if this is the refraction shader
        if (uniforms.containsKey("RefractionConfig")) {
            System.out.println("[PostEffectPipelineMixin] Intercepting RefractionConfig creation");

            // Get dynamic values from manager
            com.justheare.paperjjk_client.shader.RefractionEffectManager.RefractionEffect effect =
                com.justheare.paperjjk_client.shader.RefractionEffectManager.getPrimaryEffect();

            if (effect != null) {
                // Calculate screen position
                net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
                if (client.world != null && client.gameRenderer != null) {
                    net.minecraft.client.render.Camera camera = client.gameRenderer.getCamera();
                    org.joml.Matrix4f projectionMatrix = new org.joml.Matrix4f(
                        client.gameRenderer.getBasicProjectionMatrix(
                            client.options.getFov().getValue().floatValue()));

                    net.minecraft.util.math.Vec3d screenPos =
                        com.justheare.paperjjk_client.util.WorldToScreenUtil.worldToScreen(
                            effect.worldPos, camera, projectionMatrix);

                    if (screenPos != null) {
                        // Modify the uniform values
                        List<Object> config = uniforms.get("RefractionConfig");
                        if (config != null && config.size() >= 3) {
                            // Update EffectCenter
                            Object centerObj = config.get(0);
                            if (centerObj instanceof Map) {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> centerMap = (Map<String, Object>) centerObj;
                                if (centerMap.get("value") instanceof List) {
                                    @SuppressWarnings("unchecked")
                                    List<Double> values = (List<Double>) centerMap.get("value");
                                    if (values.size() >= 2) {
                                        values.set(0, screenPos.x);
                                        values.set(1, screenPos.y);
                                    }
                                }
                            }

                            System.out.println("[PostEffectPipelineMixin] Modified uniforms: center=(" +
                                String.format("%.3f", screenPos.x) + ", " +
                                String.format("%.3f", screenPos.y) + ")");
                        }
                    }
                }
            }
        }

        return uniforms;
    }
}
