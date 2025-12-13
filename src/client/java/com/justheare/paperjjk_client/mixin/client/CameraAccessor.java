package com.justheare.paperjjk_client.mixin.client;

import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Accessor for Camera's private pos field
 * In 1.21.11, Camera doesn't have a public getPos() method, only a private pos field
 */
@Mixin(Camera.class)
public interface CameraAccessor {
    /**
     * Get the camera position
     * @return The camera's Vec3d position
     */
    @Accessor("pos")
    Vec3d getPos();
}
