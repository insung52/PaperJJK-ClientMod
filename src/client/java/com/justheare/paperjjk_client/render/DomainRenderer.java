package com.justheare.paperjjk_client.render;

import net.minecraft.client.util.math.MatrixStack;

/**
 * Domain renderer placeholder
 * Server handles all particle rendering for barrier-less domains
 */
public class DomainRenderer {
    /**
     * Empty render method - server handles particles
     */
    public static void render(MatrixStack matrices, float tickDelta) {
        // Server sends particles via DOMAIN_VISUAL packets
        // Client only needs to update domain radius in ClientGameData
        // No rendering needed here
    }
}
