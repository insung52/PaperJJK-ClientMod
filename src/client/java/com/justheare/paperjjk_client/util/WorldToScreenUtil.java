package com.justheare.paperjjk_client.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * Utility for converting world coordinates to screen coordinates
 */
public class WorldToScreenUtil {

    /**
     * Convert world position to screen coordinates (0.0 to 1.0)
     * Returns null if position is behind camera
     */
    public static Vec3d worldToScreen(Vec3d worldPos, Camera camera, Matrix4f projectionMatrix) {
        MinecraftClient client = MinecraftClient.getInstance();

        // Get camera position
        Vec3d cameraPos = camera.getPos();

        // World position relative to camera
        float x = (float) (worldPos.x - cameraPos.x);
        float y = (float) (worldPos.y - cameraPos.y);
        float z = (float) (worldPos.z - cameraPos.z);

        // Transform to camera space
        Vector4f pos = new Vector4f(x, y, z, 1.0f);

        // Apply view matrix (camera rotation)
        Matrix4f viewMatrix = new Matrix4f();
        viewMatrix.rotation(camera.getRotation());
        viewMatrix.transform(pos);

        // Check if behind camera
        if (pos.z >= 0) {
            return null;
        }

        // Apply projection matrix
        projectionMatrix.transform(pos);

        // Perspective divide
        if (pos.w != 0) {
            pos.x /= pos.w;
            pos.y /= pos.w;
        }

        // Convert from NDC (-1 to 1) to screen space (0 to 1)
        float screenX = (pos.x + 1.0f) * 0.5f;
        float screenY = (1.0f - pos.y) * 0.5f; // Flip Y

        // Distance from camera (for radius calculation)
        float distance = (float) cameraPos.distanceTo(worldPos);

        return new Vec3d(screenX, screenY, distance);
    }
}
