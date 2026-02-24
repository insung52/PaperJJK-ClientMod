package com.justheare.paperjjk_client.particle;

import net.minecraft.client.particle.*;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.util.math.random.Random;

/**
 * Bluish-white domain fragment particle.
 *
 * Yarn 1.21.11 notes:
 *  - SpriteBillboardParticle → BillboardParticle
 *  - prevPosX/Y/Z → lastX/Y/Z
 *  - getType() + ParticleTextureSheet → getRenderType() + BillboardParticle.RenderType
 *  - BillboardParticle constructor requires Sprite as last arg
 *  - ParticleFactory.create() → createParticle() with Random at end
 */
public class DomainParticle extends BillboardParticle {

    private final float spinSpeed;

    private DomainParticle(ClientWorld world,
                           double x, double y, double z,
                           double velX, double velY, double velZ,
                           Sprite sprite) {
        super(world, x, y, z, velX, velY, velZ, sprite);

        // Slightly blue-white tint
        this.red   = 0.85f;
        this.green = 0.93f;
        this.blue  = 1.0f;
        this.alpha = 1.0f;

        this.maxAge = 25 + this.random.nextInt(20); // 1.25–2.25 sec
        this.scale  = 0.03f + this.random.nextFloat() * 0.14f; // 0.2–0.6

        // Random initial z-rotation (0 ~ 2π)
        this.zRotation     = this.random.nextFloat() * (float)(Math.PI * 2.0);
        this.lastZRotation = this.zRotation;

        // Random spin speed: ±0.15 rad/tick (~±8.6°/tick)
        this.spinSpeed = (this.random.nextFloat() - 0.5f) * 0.3f;

        // Add upward drift on top of incoming velocity
        this.velocityY += 0.035 + this.random.nextDouble() * 0.025;
    }

    @Override
    public void tick() {
        // Save previous position for rendering interpolation
        this.lastX = this.x;
        this.lastY = this.y;
        this.lastZ = this.z;

        if (this.age++ >= this.maxAge) {
            this.markDead();
            return;
        }

        // Advance z-rotation
        this.lastZRotation = this.zRotation;
        this.zRotation += this.spinSpeed;

        // Fade out in the last 40% of life
        float progress = (float) this.age / this.maxAge;
        if (progress > 0.6f) {
            this.alpha = 1.0f - (progress - 0.6f) / 0.4f;
        }

        // Gentle deceleration
        this.velocityX *= 0.96;
        this.velocityY *= 0.96;
        this.velocityZ *= 0.96;

        this.move(this.velocityX, this.velocityY, this.velocityZ);
    }

    /**
     * Fullbright: render at maximum light level regardless of world lighting.
     * Iris/Optifine shader packs detect this as emissive and apply bloom.
     * Value 0xF000F0 = LightmapTextureManager.MAX_LIGHT_COORDINATE (sky=15, block=15).
     */
    @Override
    public int getBrightness(float ticks) {
        return 0xF000F0;
    }

    @Override
    public BillboardParticle.RenderType getRenderType() {
        return BillboardParticle.RenderType.PARTICLE_ATLAS_TRANSLUCENT;
    }

    // ── Factory ─────────────────────────────────────────────────────────────

    public static class Factory implements ParticleFactory<SimpleParticleType> {

        private final SpriteProvider sprites;

        public Factory(SpriteProvider sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientWorld world,
                                       double x, double y, double z,
                                       double velX, double velY, double velZ,
                                       Random random) {
            Sprite sprite = this.sprites.getSprite(random);
            return new DomainParticle(world, x, y, z, velX, velY, velZ, sprite);
        }
    }
}
