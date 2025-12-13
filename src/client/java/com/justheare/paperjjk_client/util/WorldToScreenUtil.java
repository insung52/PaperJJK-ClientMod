package com.justheare.paperjjk_client.util;

import com.justheare.paperjjk_client.mixin.client.CameraAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;
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
        Vec3d cameraPos = ((CameraAccessor) camera).getPos();

        // World position relative to camera
        Vector3f relativePos = new Vector3f(
            (float) (worldPos.x - cameraPos.x),
            (float) (worldPos.y - cameraPos.y),
            (float) (worldPos.z - cameraPos.z)
        );

        // Create view matrix from camera rotation
        Matrix4f viewMatrix = new Matrix4f();
        viewMatrix.rotation(camera.getRotation().conjugate(new org.joml.Quaternionf())); // Use conjugate for inverse rotation

        // Transform to view space
        Vector4f viewPos = new Vector4f(relativePos, 1.0f);
        viewMatrix.transform(viewPos);

        // Check if behind camera (negative Z in view space)
        if (viewPos.z > 0) {
            return null;
        }

        // Apply projection matrix
        Vector4f clipPos = new Vector4f(viewPos);
        projectionMatrix.transform(clipPos);

        // Perspective divide
        if (Math.abs(clipPos.w) < 0.001f) {
            return null;
        }

        clipPos.x /= clipPos.w;
        clipPos.y /= clipPos.w;
        clipPos.z /= clipPos.w;

        // Convert from NDC (-1 to 1) to screen space (0 to 1)
        float screenX = (clipPos.x + 1.0f) * 0.5f;
        float screenY = (1.0f - clipPos.y) * 0.5f; // Flip Y for screen coordinates

        // Distance from camera
        float distance = (float) cameraPos.distanceTo(worldPos);

        return new Vec3d(screenX, screenY, distance);
    }
}
