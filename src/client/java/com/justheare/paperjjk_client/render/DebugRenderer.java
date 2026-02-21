package com.justheare.paperjjk_client.render;

import com.justheare.paperjjk_client.data.ClientGameData;
import com.justheare.paperjjk_client.mixin.client.CameraAccessor;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.Collection;

/**
 * Debug renderer for testing basic rendering pipeline.
 * Uses RenderLayer.getDebugFilledBox() which provides:
 * - POSITION_COLOR vertex format (no texture atlas, no stripes)
 * - Translucent blending (alpha works correctly)
 * - No texture required
 */
public class DebugRenderer {
    private static final RenderLayer SPHERE_LAYER = RenderLayer.of(
        "paperjjk:sphere",
        RenderSetup.builder(RenderPipelines.DEBUG_FILLED_BOX).build()
    );

    private static boolean renderCube = false;
    private static boolean renderEffect1 = false;
    private static boolean renderEffect2 = false;
    private static Vec3d effect1Position = null;
    private static Vec3d effect2Position = null;

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

    public static void render(MatrixStack matrices, Camera camera) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return;

        Vec3d cameraPos = ((CameraAccessor) camera).getPos();

        // Early exit if nothing to render (Tessellator.begin must have vertices before end())
        Collection<ClientGameData.ActiveDomain> domains = ClientGameData.getAllDomains();
        boolean hasWork = renderCube || !domains.isEmpty()
            || (renderEffect1 && effect1Position != null)
            || (renderEffect2 && effect2Position != null);
        if (!hasWork) return;

        // Use Tessellator directly — bypasses Iris's VertexConsumerProvider g-buffer routing.
        // At WorldRenderEvents.LAST, Iris has already composited its g-buffers, so vanilla
        // pipeline draws land on the final framebuffer correctly.
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        // Test cube: small red sphere 2m in front of camera
        if (renderCube) {
            Vec3d forward = new Vec3d(
                -Math.sin(Math.toRadians(camera.getYaw())) * Math.cos(Math.toRadians(camera.getPitch())),
                -Math.sin(Math.toRadians(camera.getPitch())),
                Math.cos(Math.toRadians(camera.getYaw())) * Math.cos(Math.toRadians(camera.getPitch()))
            );
            Vec3d testPos = cameraPos.add(forward.normalize().multiply(2.0));

            matrices.push();
            matrices.translate(testPos.x - cameraPos.x, testPos.y - cameraPos.y, testPos.z - cameraPos.z);
            renderSphere(matrices.peek().getPositionMatrix(), buffer, 0.5f, 1.0f, 0.0f, 0.0f, 0.35f);
            matrices.pop();
        }

        // Active domains
        for (ClientGameData.ActiveDomain domain : domains) {
            matrices.push();
            matrices.translate(
                domain.center.x - cameraPos.x,
                domain.center.y - cameraPos.y,
                domain.center.z - cameraPos.z
            );
            float r = ((domain.color >> 16) & 0xFF) / 255.0f;
            float g = ((domain.color >> 8) & 0xFF) / 255.0f;
            float b = (domain.color & 0xFF) / 255.0f;
            renderSphere(matrices.peek().getPositionMatrix(), buffer, domain.currentRadius, r, g, b, 0.2f);
            matrices.pop();
        }

        // Effect 1: blue Fresnel sphere
        if (renderEffect1 && effect1Position != null) {
            matrices.push();
            matrices.translate(
                effect1Position.x - cameraPos.x,
                effect1Position.y - cameraPos.y,
                effect1Position.z - cameraPos.z
            );
            renderFresnelSphere(matrices.peek().getPositionMatrix(), buffer, 5.0f,
                0.2f, 0.4f, 0.9f, cameraPos, effect1Position);
            matrices.pop();
        }

        // Effect 2: yellow Fresnel sphere
        if (renderEffect2 && effect2Position != null) {
            matrices.push();
            matrices.translate(
                effect2Position.x - cameraPos.x,
                effect2Position.y - cameraPos.y,
                effect2Position.z - cameraPos.z
            );
            renderFresnelSphere(matrices.peek().getPositionMatrix(), buffer, 2.5f,
                1.0f, 0.8f, 0.2f, cameraPos, effect2Position);
            matrices.pop();
        }

        SPHERE_LAYER.draw(buffer.end());
    }

    /**
     * Renders a solid-colored translucent sphere.
     * Uses POSITION_COLOR vertex format: only vertex + color, no texture.
     */
    private static void renderSphere(Matrix4f matrix, VertexConsumer consumer,
                                     float radius, float r, float g, float b, float a) {
        int segments = 24;

        for (int lat = 0; lat < segments; lat++) {
            float theta1 = (float) (lat * Math.PI / segments);
            float theta2 = (float) ((lat + 1) * Math.PI / segments);

            for (int lon = 0; lon < segments; lon++) {
                float phi1 = (float) (lon * 2 * Math.PI / segments);
                float phi2 = (float) ((lon + 1) * 2 * Math.PI / segments);

                float x1 = radius * (float)(Math.sin(theta1) * Math.cos(phi1));
                float y1 = radius * (float) Math.cos(theta1);
                float z1 = radius * (float)(Math.sin(theta1) * Math.sin(phi1));

                float x2 = radius * (float)(Math.sin(theta2) * Math.cos(phi1));
                float y2 = radius * (float) Math.cos(theta2);
                float z2 = radius * (float)(Math.sin(theta2) * Math.sin(phi1));

                float x3 = radius * (float)(Math.sin(theta2) * Math.cos(phi2));
                float y3 = radius * (float) Math.cos(theta2);
                float z3 = radius * (float)(Math.sin(theta2) * Math.sin(phi2));

                float x4 = radius * (float)(Math.sin(theta1) * Math.cos(phi2));
                float y4 = radius * (float) Math.cos(theta1);
                float z4 = radius * (float)(Math.sin(theta1) * Math.sin(phi2));

                // Outer face (winding: CCW from outside)
                consumer.vertex(matrix, x1, y1, z1).color(r, g, b, a);
                consumer.vertex(matrix, x2, y2, z2).color(r, g, b, a);
                consumer.vertex(matrix, x3, y3, z3).color(r, g, b, a);
                consumer.vertex(matrix, x4, y4, z4).color(r, g, b, a);

                // Inner face (reversed winding for double-sided)
                consumer.vertex(matrix, x4, y4, z4).color(r, g, b, a);
                consumer.vertex(matrix, x3, y3, z3).color(r, g, b, a);
                consumer.vertex(matrix, x2, y2, z2).color(r, g, b, a);
                consumer.vertex(matrix, x1, y1, z1).color(r, g, b, a);
            }
        }
    }

    /**
     * Renders a Fresnel-effect sphere: edges glow, center transparent.
     * Uses POSITION_COLOR vertex format: only vertex + color, no texture.
     */
    private static void renderFresnelSphere(Matrix4f matrix, VertexConsumer consumer,
                                            float radius, float baseR, float baseG, float baseB,
                                            Vec3d cameraPos, Vec3d sphereCenter) {
        int segments = 24;

        for (int lat = 0; lat < segments; lat++) {
            float theta1 = (float) (lat * Math.PI / segments);
            float theta2 = (float) ((lat + 1) * Math.PI / segments);

            for (int lon = 0; lon < segments; lon++) {
                float phi1 = (float) (lon * 2 * Math.PI / segments);
                float phi2 = (float) ((lon + 1) * 2 * Math.PI / segments);

                Vec3d v1 = sphereVertex(sphereCenter, radius, theta1, phi1);
                Vec3d v2 = sphereVertex(sphereCenter, radius, theta2, phi1);
                Vec3d v3 = sphereVertex(sphereCenter, radius, theta2, phi2);
                Vec3d v4 = sphereVertex(sphereCenter, radius, theta1, phi2);

                float f1 = fresnel(v1.subtract(sphereCenter).normalize(), cameraPos.subtract(v1).normalize());
                float f2 = fresnel(v2.subtract(sphereCenter).normalize(), cameraPos.subtract(v2).normalize());
                float f3 = fresnel(v3.subtract(sphereCenter).normalize(), cameraPos.subtract(v3).normalize());
                float f4 = fresnel(v4.subtract(sphereCenter).normalize(), cameraPos.subtract(v4).normalize());

                Vec3d l1 = v1.subtract(sphereCenter);
                Vec3d l2 = v2.subtract(sphereCenter);
                Vec3d l3 = v3.subtract(sphereCenter);
                Vec3d l4 = v4.subtract(sphereCenter);

                // Outer face
                addFresnelVertex(consumer, matrix, l1, f1, baseR, baseG, baseB);
                addFresnelVertex(consumer, matrix, l2, f2, baseR, baseG, baseB);
                addFresnelVertex(consumer, matrix, l3, f3, baseR, baseG, baseB);
                addFresnelVertex(consumer, matrix, l4, f4, baseR, baseG, baseB);

                // Inner face
                addFresnelVertex(consumer, matrix, l4, f4, baseR, baseG, baseB);
                addFresnelVertex(consumer, matrix, l3, f3, baseR, baseG, baseB);
                addFresnelVertex(consumer, matrix, l2, f2, baseR, baseG, baseB);
                addFresnelVertex(consumer, matrix, l1, f1, baseR, baseG, baseB);
            }
        }
    }

    private static Vec3d sphereVertex(Vec3d center, float radius, float theta, float phi) {
        return new Vec3d(
            center.x + radius * Math.sin(theta) * Math.cos(phi),
            center.y + radius * Math.cos(theta),
            center.z + radius * Math.sin(theta) * Math.sin(phi)
        );
    }

    /** Fresnel term: 0 = facing camera, 1 = grazing angle */
    private static float fresnel(Vec3d normal, Vec3d viewDir) {
        double dot = Math.max(0.0, Math.min(1.0, normal.dotProduct(viewDir)));
        return (float) Math.pow(1.0 - dot, 3.0);
    }

    private static void addFresnelVertex(VertexConsumer consumer, Matrix4f matrix,
                                         Vec3d pos, float fresnel,
                                         float baseR, float baseG, float baseB) {
        float r = Math.min(1.0f, baseR + fresnel * 1.5f);
        float g = Math.min(1.0f, baseG + fresnel * 1.5f);
        float b = Math.min(1.0f, baseB + fresnel * 0.5f);
        float a = 0.1f + fresnel * 0.5f;

        consumer.vertex(matrix, (float) pos.x, (float) pos.y, (float) pos.z).color(r, g, b, a);
    }
}
