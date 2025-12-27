package com.justheare.paperjjk_client.render;

import com.justheare.paperjjk_client.data.ClientGameData;
import com.justheare.paperjjk_client.mixin.client.CameraAccessor;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;

import java.util.OptionalDouble;

/**
 * Simple debug renderer to test basic rendering pipeline
 * Goal: Render a simple red cube 1 meter in front of the camera
 */
public class DebugRenderer {
    private static boolean renderCube = false;
    private static boolean renderEffect1 = false;
    private static boolean renderEffect2 = false;
    private static Vec3d effect1Position = null;
    private static Vec3d effect2Position = null;
    private static boolean debuggedMethods = false;
    private static RenderLayer translucentLayer = null;

    public static void toggleCube() {
        renderCube = !renderCube;
        System.out.println("[PaperJJK Debug] Cube rendering: " + renderCube);
    }

    public static void toggleEffect(int effectId, Vec3d position) {
        if (effectId == 1) {
            renderEffect1 = !renderEffect1;
            if (renderEffect1) {
                effect1Position = position;
                System.out.println("[PaperJJK Debug] Effect 1 (blue) enabled at: " + position);
            } else {
                System.out.println("[PaperJJK Debug] Effect 1 disabled");
            }
        } else if (effectId == 2) {
            renderEffect2 = !renderEffect2;
            if (renderEffect2) {
                effect2Position = position;
                System.out.println("[PaperJJK Debug] Effect 2 (yellow) enabled at: " + position);
            } else {
                System.out.println("[PaperJJK Debug] Effect 2 disabled");
            }
        }
    }

    public static boolean isEffect1Active() { return renderEffect1; }
    public static boolean isEffect2Active() { return renderEffect2; }
    public static Vec3d getEffect1Position() { return effect1Position; }
    public static Vec3d getEffect2Position() { return effect2Position; }


    public static void render(MatrixStack matrices, Camera camera, VertexConsumerProvider consumers) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) {
            return;
        }

        // Debug: Print all RenderLayer and TexturedRenderLayers methods once
        if (!debuggedMethods) {
            debuggedMethods = true;

            System.out.println("[PaperJJK Debug] ========== RenderLayer Methods (all) ==========");
            java.lang.reflect.Method[] renderLayerMethods = RenderLayer.class.getDeclaredMethods();
            for (java.lang.reflect.Method method : renderLayerMethods) {
                if (java.lang.reflect.Modifier.isStatic(method.getModifiers()) &&
                    java.lang.reflect.Modifier.isPublic(method.getModifiers()) &&
                    RenderLayer.class.isAssignableFrom(method.getReturnType())) {
                    // Try to invoke methods with 0 parameters
                    if (method.getParameterCount() == 0) {
                        try {
                            RenderLayer layer = (RenderLayer) method.invoke(null);
                            System.out.println("[PaperJJK Debug]   " + method.getName() + " -> " + layer.toString());
                        } catch (Exception e) {
                            System.out.println("[PaperJJK Debug]   " + method.getName() + " -> (0 params, invoke failed)");
                        }
                    } else {
                        System.out.println("[PaperJJK Debug]   " + method.getName() + " - params: " + method.getParameterCount());
                    }
                }
            }

            System.out.println("[PaperJJK Debug] ========== TexturedRenderLayers Methods ==========");
            java.lang.reflect.Method[] texturedMethods = TexturedRenderLayers.class.getDeclaredMethods();
            for (java.lang.reflect.Method method : texturedMethods) {
                if (java.lang.reflect.Modifier.isStatic(method.getModifiers()) &&
                    java.lang.reflect.Modifier.isPublic(method.getModifiers()) &&
                    RenderLayer.class.isAssignableFrom(method.getReturnType())) {
                    if (method.getParameterCount() == 0) {
                        try {
                            RenderLayer layer = (RenderLayer) method.invoke(null);
                            System.out.println("[PaperJJK Debug]   " + method.getName() + " -> " + layer.toString());
                        } catch (Exception e) {
                            System.out.println("[PaperJJK Debug]   " + method.getName() + " -> ERROR: " + e.getMessage());
                        }
                    } else if (method.getName().equals("method_48480")) {
                        System.out.println("[PaperJJK Debug]   method_48480 param type: " + method.getParameterTypes()[0].getName());
                    }
                }
            }
            System.out.println("[PaperJJK Debug] ========================================");

            // Get translucent layer using reflection
            try {
                java.lang.reflect.Method method = TexturedRenderLayers.class.getDeclaredMethod("method_76545");
                translucentLayer = (RenderLayer) method.invoke(null);
                System.out.println("[PaperJJK Debug] Successfully got translucent layer: " + translucentLayer);
            } catch (Exception e) {
                System.err.println("[PaperJJK Debug] Failed to get translucent layer: " + e.getMessage());
                e.printStackTrace();
            }

            // Search all RenderLayer static fields for debug layers
            System.out.println("[PaperJJK Debug] ========== RenderLayer Static Fields ==========");
            try {
                java.lang.reflect.Field[] fields = RenderLayer.class.getDeclaredFields();
                for (java.lang.reflect.Field field : fields) {
                    if (java.lang.reflect.Modifier.isStatic(field.getModifiers()) &&
                        RenderLayer.class.isAssignableFrom(field.getType())) {
                        field.setAccessible(true);
                        RenderLayer layer = (RenderLayer) field.get(null);
                        System.out.println("[PaperJJK Debug]   " + field.getName() + " = " + layer);
                    }
                }
            } catch (Exception e) {
                System.err.println("[PaperJJK Debug] Failed to list fields: " + e.getMessage());
            }
            System.out.println("[PaperJJK Debug] ==========================================");

            System.out.println("[PaperJJK Debug] Using translucent layer: " + translucentLayer);
        }

        Vec3d cameraPos = ((CameraAccessor) camera).getPos();

        // Debug mode: render test sphere in front of camera
        if (renderCube) {
            System.out.println("[PaperJJK Debug] Render called!");

            Vec3d cameraDir = ((CameraAccessor) camera).getPos().add(
                -Math.sin(Math.toRadians(camera.getYaw())) * Math.cos(Math.toRadians(camera.getPitch())),
                -Math.sin(Math.toRadians(camera.getPitch())),
                Math.cos(Math.toRadians(camera.getYaw())) * Math.cos(Math.toRadians(camera.getPitch()))
            );

            Vec3d testPos = cameraPos.add(cameraDir.normalize().multiply(2.0));

            matrices.push();
            try {
                matrices.translate(
                    testPos.x - cameraPos.x,
                    testPos.y - cameraPos.y,
                    testPos.z - cameraPos.z
                );

                renderSphere(matrices.peek().getPositionMatrix(), 0.5f, 1.0f, 0.0f, 0.0f, 1.0f, consumers, cameraPos, testPos);

            } catch (Exception e) {
                System.err.println("[PaperJJK Debug] Error rendering test sphere: " + e.getMessage());
                e.printStackTrace();
            } finally {
                matrices.pop();
            }
        }

        // Render all active domains
        for (ClientGameData.ActiveDomain domain : ClientGameData.getAllDomains()) {
            matrices.push();
            try {
                // Translate to domain center (relative to camera)
                matrices.translate(
                    domain.center.x - cameraPos.x,
                    domain.center.y - cameraPos.y,
                    domain.center.z - cameraPos.z
                );

                // Extract RGB from color int (0xRRGGBB)
                float r = ((domain.color >> 16) & 0xFF) / 255.0f;
                float g = ((domain.color >> 8) & 0xFF) / 255.0f;
                float b = (domain.color & 0xFF) / 255.0f;
                float a = 0.5f; // Semi-transparent

                // Render sphere with current radius
                renderSphere(matrices.peek().getPositionMatrix(), domain.currentRadius, r, g, b, a, consumers, cameraPos, domain.center);

            } catch (Exception e) {
                System.err.println("[PaperJJK Debug] Error rendering domain: " + e.getMessage());
                e.printStackTrace();
            } finally {
                matrices.pop();
            }
        }

        // Render debug effect 1 (blue)
        if (renderEffect1 && effect1Position != null) {
            matrices.push();
            try {
                matrices.translate(
                    effect1Position.x - cameraPos.x,
                    effect1Position.y - cameraPos.y,
                    effect1Position.z - cameraPos.z
                );

                // Blue glowing effect with Fresnel
                renderFresnelSphere(matrices.peek().getPositionMatrix(), 5.0f,
                    0.2f, 0.4f, 0.8f,  // Blue color
                    consumers, cameraPos, effect1Position);

            } catch (Exception e) {
                System.err.println("[PaperJJK Debug] Error rendering effect 1: " + e.getMessage());
                e.printStackTrace();
            } finally {
                matrices.pop();
            }
        }

        // Render debug effect 2 (yellow/red)
        if (renderEffect2 && effect2Position != null) {
            matrices.push();
            try {
                matrices.translate(
                    effect2Position.x - cameraPos.x,
                    effect2Position.y - cameraPos.y,
                    effect2Position.z - cameraPos.z
                );

                // Yellow/red glowing effect with Fresnel (smaller sphere)
                renderFresnelSphere(matrices.peek().getPositionMatrix(), 2.5f,
                    1.0f, 0.8f, 0.2f,  // Yellow/orange color
                    consumers, cameraPos, effect2Position);

            } catch (Exception e) {
                System.err.println("[PaperJJK Debug] Error rendering effect 2: " + e.getMessage());
                e.printStackTrace();
            } finally {
                matrices.pop();
            }
        }
    }

    /**
     * Render a solid filled sphere using quads (4 vertices each)
     * Uses custom debug RenderLayer that renders both front and back faces
     */
    private static void renderSphere(Matrix4f matrix, float radius, float r, float g, float b, float a,
                                     VertexConsumerProvider consumers, Vec3d cameraPos, Vec3d sphereCenter) {
        try {
            // Use translucent layer obtained via reflection
            RenderLayer layer = (translucentLayer != null) ? translucentLayer : TexturedRenderLayers.getEntitySolid();
            if (radius > 10) {
                System.out.println("[PaperJJK Debug] renderSphere with animated UV: radius=" + radius);
            }
            VertexConsumer vertexConsumer = consumers.getBuffer(layer);

            // Get game time for UV animation (creates flowing/shimmering effect)
            MinecraftClient client = MinecraftClient.getInstance();
            float time = client.world != null ? client.world.getTime() : 0;

            // Create slow scrolling UV offset - makes stripes "flow" like smoke/energy
            float uvOffsetU = (time * 0.02f) % 1.0f;
            float uvOffsetV = (time * 0.015f) % 1.0f;

            int segments = 32;
            int vertexCount = 0;

            // Draw sphere as quads (latitude by latitude)
            for (int lat = 0; lat < segments; lat++) {
                float theta1 = (float) (lat * Math.PI / segments);
                float theta2 = (float) ((lat + 1) * Math.PI / segments);

                for (int lon = 0; lon < segments; lon++) {
                    float phi1 = (float) (lon * 2 * Math.PI / segments);
                    float phi2 = (float) ((lon + 1) * 2 * Math.PI / segments);

                    // Calculate 4 vertices of the quad
                    float x1 = radius * (float) Math.sin(theta1) * (float) Math.cos(phi1);
                    float y1 = radius * (float) Math.cos(theta1);
                    float z1 = radius * (float) Math.sin(theta1) * (float) Math.sin(phi1);

                    float x2 = radius * (float) Math.sin(theta2) * (float) Math.cos(phi1);
                    float y2 = radius * (float) Math.cos(theta2);
                    float z2 = radius * (float) Math.sin(theta2) * (float) Math.sin(phi1);

                    float x3 = radius * (float) Math.sin(theta2) * (float) Math.cos(phi2);
                    float y3 = radius * (float) Math.cos(theta2);
                    float z3 = radius * (float) Math.sin(theta2) * (float) Math.sin(phi2);

                    float x4 = radius * (float) Math.sin(theta1) * (float) Math.cos(phi2);
                    float y4 = radius * (float) Math.cos(theta1);
                    float z4 = radius * (float) Math.sin(theta1) * (float) Math.sin(phi2);

                    // Calculate normals (pointing outward from center)
                    float nx1 = x1 / radius, ny1 = y1 / radius, nz1 = z1 / radius;
                    float nx2 = x2 / radius, ny2 = y2 / radius, nz2 = z2 / radius;
                    float nx3 = x3 / radius, ny3 = y3 / radius, nz3 = z3 / radius;
                    float nx4 = x4 / radius, ny4 = y4 / radius, nz4 = z4 / radius;

                    // Animated UV - scrolls slowly to create flowing smoke/energy effect
                    // Add some variation per quad to create turbulence
                    float noiseU = (float) Math.sin(theta1 + phi1) * 0.05f;
                    float noiseV = (float) Math.cos(theta1 * 2 + phi1 * 3) * 0.05f;

                    float u = (0.5f + uvOffsetU + noiseU) % 1.0f;
                    float v = (0.5f + uvOffsetV + noiseV) % 1.0f;

                    // Draw quad facing outward - animated UV creates flowing pattern
                    vertexConsumer.vertex(matrix, x1, y1, z1).color(r, g, b, a).texture(u, v).overlay(OverlayTexture.DEFAULT_UV).light(15728880).normal(nx1, ny1, nz1);
                    vertexConsumer.vertex(matrix, x2, y2, z2).color(r, g, b, a).texture(u, v).overlay(OverlayTexture.DEFAULT_UV).light(15728880).normal(nx2, ny2, nz2);
                    vertexConsumer.vertex(matrix, x3, y3, z3).color(r, g, b, a).texture(u, v).overlay(OverlayTexture.DEFAULT_UV).light(15728880).normal(nx3, ny3, nz3);
                    vertexConsumer.vertex(matrix, x4, y4, z4).color(r, g, b, a).texture(u, v).overlay(OverlayTexture.DEFAULT_UV).light(15728880).normal(nx4, ny4, nz4);

                    // Draw quad facing inward (reversed winding for double-sided rendering)
                    vertexConsumer.vertex(matrix, x4, y4, z4).color(r, g, b, a).texture(u, v).overlay(OverlayTexture.DEFAULT_UV).light(15728880).normal(-nx4, -ny4, -nz4);
                    vertexConsumer.vertex(matrix, x3, y3, z3).color(r, g, b, a).texture(u, v).overlay(OverlayTexture.DEFAULT_UV).light(15728880).normal(-nx3, -ny3, -nz3);
                    vertexConsumer.vertex(matrix, x2, y2, z2).color(r, g, b, a).texture(u, v).overlay(OverlayTexture.DEFAULT_UV).light(15728880).normal(-nx2, -ny2, -nz2);
                    vertexConsumer.vertex(matrix, x1, y1, z1).color(r, g, b, a).texture(u, v).overlay(OverlayTexture.DEFAULT_UV).light(15728880).normal(-nx1, -ny1, -nz1);

                    vertexCount += 8;
                }
            }

            if (radius > 10) {
                System.out.println("[PaperJJK Debug] renderSphere finished: " + vertexCount + " vertices with flowing UV animation");
            }

        } catch (Exception e) {
            System.err.println("[PaperJJK Debug] Error in renderSphere: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Render a Fresnel-based glowing sphere effect
     * Edges glow brightly, center is more transparent
     */
    private static void renderFresnelSphere(Matrix4f matrix, float radius,
                                           float baseR, float baseG, float baseB,
                                           VertexConsumerProvider consumers,
                                           Vec3d cameraPos, Vec3d sphereCenter) {
        try {
            // Use translucent layer obtained via reflection
            RenderLayer layer = (translucentLayer != null) ? translucentLayer : TexturedRenderLayers.getEntitySolid();
            VertexConsumer vertexConsumer = consumers.getBuffer(layer);

            int segments = 32;

            // Draw sphere as quads with Fresnel-based coloring
            for (int lat = 0; lat < segments; lat++) {
                float theta1 = (float) (lat * Math.PI / segments);
                float theta2 = (float) ((lat + 1) * Math.PI / segments);

                for (int lon = 0; lon < segments; lon++) {
                    float phi1 = (float) (lon * 2 * Math.PI / segments);
                    float phi2 = (float) ((lon + 1) * 2 * Math.PI / segments);

                    // Calculate 4 vertices
                    Vec3d v1 = getVertexOnSphere(sphereCenter, radius, theta1, phi1);
                    Vec3d v2 = getVertexOnSphere(sphereCenter, radius, theta2, phi1);
                    Vec3d v3 = getVertexOnSphere(sphereCenter, radius, theta2, phi2);
                    Vec3d v4 = getVertexOnSphere(sphereCenter, radius, theta1, phi2);

                    // Calculate normals (pointing outward)
                    Vec3d n1 = v1.subtract(sphereCenter).normalize();
                    Vec3d n2 = v2.subtract(sphereCenter).normalize();
                    Vec3d n3 = v3.subtract(sphereCenter).normalize();
                    Vec3d n4 = v4.subtract(sphereCenter).normalize();

                    // Calculate Fresnel for each vertex
                    float f1 = calculateFresnel(v1, n1, cameraPos);
                    float f2 = calculateFresnel(v2, n2, cameraPos);
                    float f3 = calculateFresnel(v3, n3, cameraPos);
                    float f4 = calculateFresnel(v4, n4, cameraPos);

                    // Convert to local coordinates (already translated by matrix)
                    Vec3d local1 = v1.subtract(sphereCenter);
                    Vec3d local2 = v2.subtract(sphereCenter);
                    Vec3d local3 = v3.subtract(sphereCenter);
                    Vec3d local4 = v4.subtract(sphereCenter);

                    // Draw quad with Fresnel-based colors (outward facing)
                    addFresnelVertex(vertexConsumer, matrix, local1, n1, f1, baseR, baseG, baseB);
                    addFresnelVertex(vertexConsumer, matrix, local2, n2, f2, baseR, baseG, baseB);
                    addFresnelVertex(vertexConsumer, matrix, local3, n3, f3, baseR, baseG, baseB);
                    addFresnelVertex(vertexConsumer, matrix, local4, n4, f4, baseR, baseG, baseB);

                    // Draw quad reversed (inward facing) for double-sided rendering
                    addFresnelVertex(vertexConsumer, matrix, local4, n4.negate(), f4, baseR, baseG, baseB);
                    addFresnelVertex(vertexConsumer, matrix, local3, n3.negate(), f3, baseR, baseG, baseB);
                    addFresnelVertex(vertexConsumer, matrix, local2, n2.negate(), f2, baseR, baseG, baseB);
                    addFresnelVertex(vertexConsumer, matrix, local1, n1.negate(), f1, baseR, baseG, baseB);
                }
            }

        } catch (Exception e) {
            System.err.println("[PaperJJK Debug] Error in renderFresnelSphere: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Get vertex position on sphere surface
     */
    private static Vec3d getVertexOnSphere(Vec3d center, float radius, float theta, float phi) {
        double x = center.x + radius * Math.sin(theta) * Math.cos(phi);
        double y = center.y + radius * Math.cos(theta);
        double z = center.z + radius * Math.sin(theta) * Math.sin(phi);
        return new Vec3d(x, y, z);
    }

    /**
     * Calculate Fresnel term
     * Returns 0 when looking straight at surface (perpendicular)
     * Returns 1 when looking at grazing angle (parallel)
     */
    private static float calculateFresnel(Vec3d vertexPos, Vec3d normal, Vec3d cameraPos) {
        Vec3d viewDir = cameraPos.subtract(vertexPos).normalize();
        double dotProduct = normal.dotProduct(viewDir);
        // Clamp to avoid negative values
        dotProduct = Math.max(0.0, Math.min(1.0, dotProduct));

        // Fresnel: 1 - dot(N, V)
        // Raised to power for sharper falloff
        float fresnel = (float) Math.pow(1.0 - dotProduct, 3.0);
        return fresnel;
    }

    /**
     * Add vertex with Fresnel-based color and alpha
     */
    private static void addFresnelVertex(VertexConsumer consumer, Matrix4f matrix,
                                        Vec3d pos, Vec3d normal, float fresnel,
                                        float baseR, float baseG, float baseB) {
        // Brighten edges based on Fresnel
        float r = baseR + fresnel * 1.5f;
        float g = baseG + fresnel * 1.5f;
        float b = baseB + fresnel * 0.5f;

        // Alpha: edges more opaque, center more transparent
        float a = 0.3f + fresnel * 0.6f;

        // Add vertex with UV at center of first tile
        consumer.vertex(matrix, (float)pos.x, (float)pos.y, (float)pos.z)
                .color(r, g, b, a)
                .texture(1.0f / 16.0f, 1.0f / 16.0f)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(15728880)
                .normal((float)normal.x, (float)normal.y, (float)normal.z);
    }


}
