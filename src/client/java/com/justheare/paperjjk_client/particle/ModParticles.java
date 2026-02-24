package com.justheare.paperjjk_client.particle;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/**
 * Registers custom particle types for PaperJJK client mod.
 * Call register() from the client entrypoint before ParticleFactoryRegistry setup.
 */
public class ModParticles {

    /**
     * Domain fragment particle — bluish-white glowing fragment.
     * Spawned at the ring edge during charging and crumble effects.
     * Texture: assets/paperjjk-client/textures/particle/domain_fragment.png
     * Sprite list: assets/paperjjk-client/particles/domain_fragment.json
     */
    public static final SimpleParticleType DOMAIN_FRAGMENT = FabricParticleTypes.simple();

    public static void register() {
        Registry.register(
            Registries.PARTICLE_TYPE,
            Identifier.of("paperjjk-client", "domain_fragment"),
            DOMAIN_FRAGMENT
        );
    }
}
