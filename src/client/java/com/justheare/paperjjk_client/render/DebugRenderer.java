package com.justheare.paperjjk_client.render;

import com.justheare.paperjjk_client.data.ClientGameData;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/**
 * Simple debug renderer to test basic rendering pipeline
 * Goal: Render a simple red cube 1 meter in front of the camera
 */
public class DebugRenderer {
    private static boolean renderCube = false;

    public static void toggleCube() {
        renderCube = !renderCube;
        System.out.println("[PaperJJK Debug] Cube rendering: " + renderCube);
    }

    public static void render(MatrixStack matrices, Camera camera, VertexConsumerProvider consumers) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) {
            return;
        }

        Vec3d cameraPos = camera.getPos();

        // Debug mode: render test sphere in front of camera
        if (renderCube) {
            System.out.println("[PaperJJK Debug] Render called!");

            Vec3d cameraDir = camera.getPos().add(
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

                renderSphere(matrices.peek().getPositionMatrix(), 0.5f, 1.0f, 0.0f, 0.0f, 1.0f, consumers);

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
                renderSphere(matrices.peek().getPositionMatrix(), domain.currentRadius, r, g, b, a, consumers);

            } catch (Exception e) {
                System.err.println("[PaperJJK Debug] Error rendering domain: " + e.getMessage());
                e.printStackTrace();
            } finally {
                matrices.pop();
            }
        }
    }

    /**
     * Render a solid filled sphere using triangles
     */
    private static void renderSphere(Matrix4f matrix, float radius, float r, float g, float b, float a,
                                     VertexConsumerProvider consumers) {
        try {
            // Use debug filled box render layer (supports transparency)
            VertexConsumer vertexConsumer = consumers.getBuffer(RenderLayer.getDebugFilledBox());

            int segments = 32; // Number of divisions (higher = smoother)

            // Draw sphere as quad strips (latitude by latitude)
            for (int lat = 0; lat < segments; lat++) {
                float theta1 = (float) (lat * Math.PI / segments);
                float theta2 = (float) ((lat + 1) * Math.PI / segments);

                for (int lon = 0; lon < segments; lon++) {
                    float phi1 = (float) (lon * 2 * Math.PI / segments);
                    float phi2 = (float) ((lon + 1) * 2 * Math.PI / segments);

                    // Calculate 4 vertices of the quad
                    // v1: (theta1, phi1)
                    float x1 = radius * (float) Math.sin(theta1) * (float) Math.cos(phi1);
                    float y1 = radius * (float) Math.cos(theta1);
                    float z1 = radius * (float) Math.sin(theta1) * (float) Math.sin(phi1);

                    // v2: (theta2, phi1)
                    float x2 = radius * (float) Math.sin(theta2) * (float) Math.cos(phi1);
                    float y2 = radius * (float) Math.cos(theta2);
                    float z2 = radius * (float) Math.sin(theta2) * (float) Math.sin(phi1);

                    // v3: (theta2, phi2)
                    float x3 = radius * (float) Math.sin(theta2) * (float) Math.cos(phi2);
                    float y3 = radius * (float) Math.cos(theta2);
                    float z3 = radius * (float) Math.sin(theta2) * (float) Math.sin(phi2);

                    // v4: (theta1, phi2)
                    float x4 = radius * (float) Math.sin(theta1) * (float) Math.cos(phi2);
                    float y4 = radius * (float) Math.cos(theta1);
                    float z4 = radius * (float) Math.sin(theta1) * (float) Math.sin(phi2);

                    // Calculate normals (pointing outward from center)
                    float nx1 = x1 / radius, ny1 = y1 / radius, nz1 = z1 / radius;
                    float nx2 = x2 / radius, ny2 = y2 / radius, nz2 = z2 / radius;
                    float nx3 = x3 / radius, ny3 = y3 / radius, nz3 = z3 / radius;
                    float nx4 = x4 / radius, ny4 = y4 / radius, nz4 = z4 / radius;

                    // Draw two triangles to form a quad
                    // Triangle 1: v1, v2, v3
                    vertexConsumer.vertex(matrix, x1, y1, z1).color(r, g, b, a).normal(nx1, ny1, nz1);
                    vertexConsumer.vertex(matrix, x2, y2, z2).color(r, g, b, a).normal(nx2, ny2, nz2);
                    vertexConsumer.vertex(matrix, x3, y3, z3).color(r, g, b, a).normal(nx3, ny3, nz3);

                    // Triangle 2: v1, v3, v4
                    vertexConsumer.vertex(matrix, x1, y1, z1).color(r, g, b, a).normal(nx1, ny1, nz1);
                    vertexConsumer.vertex(matrix, x3, y3, z3).color(r, g, b, a).normal(nx3, ny3, nz3);
                    vertexConsumer.vertex(matrix, x4, y4, z4).color(r, g, b, a).normal(nx4, ny4, nz4);
                }
            }

        } catch (Exception e) {
            System.err.println("[PaperJJK Debug] Error in renderSphere: " + e.getMessage());
            e.printStackTrace();
        }
    }


}
