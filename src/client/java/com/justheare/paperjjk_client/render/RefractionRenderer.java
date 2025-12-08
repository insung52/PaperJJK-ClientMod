package com.justheare.paperjjk_client.render;

import net.minecraft.client.gui.DrawContext;

/**
 * Renders gravitational lens / refraction effects
 */
public class RefractionRenderer {

    /**
     * Render a gravitational lens effect at the given screen position
     * Uses concentric circle outlines to simulate light bending
     */
    public static void renderGravitationalLens(DrawContext context, int centerX, int centerY, int radius, float strength) {
        // Draw concentric circle outlines (much faster than filled circles)
        int rings = 6;

        for (int ring = 1; ring <= rings; ring++) {
            float ringRatio = (float) ring / rings;
            int ringRadius = (int) (radius * ringRatio);

            // Calculate alpha - more opaque in center, transparent at edge
            int alpha = (int) ((1.0f - ringRatio) * 200 * strength);

            // Blue-ish color for gravitational lens effect
            int color = (alpha << 24) | 0x4080FF;

            // Draw circle outline (only perimeter, not filled)
            drawCircleOutline(context, centerX, centerY, ringRadius, color, 2);
        }

        // Draw bright center point
        int centerAlpha = (int) (255 * strength);
        int centerColor = (centerAlpha << 24) | 0xFFFFFF; // White center

        // Draw small filled center
        int centerSize = Math.max(3, (int)(radius * 0.05f));
        context.fill(centerX - centerSize, centerY - centerSize,
                    centerX + centerSize, centerY + centerSize, centerColor);
    }

    /**
     * Draw a circle outline using Bresenham's circle algorithm (efficient)
     */
    private static void drawCircleOutline(DrawContext context, int centerX, int centerY, int radius, int color, int thickness) {
        // Use midpoint circle algorithm for efficiency
        int x = 0;
        int y = radius;
        int d = 1 - radius;

        while (x <= y) {
            // Draw 8 octants
            drawPixelThick(context, centerX + x, centerY + y, color, thickness);
            drawPixelThick(context, centerX - x, centerY + y, color, thickness);
            drawPixelThick(context, centerX + x, centerY - y, color, thickness);
            drawPixelThick(context, centerX - x, centerY - y, color, thickness);
            drawPixelThick(context, centerX + y, centerY + x, color, thickness);
            drawPixelThick(context, centerX - y, centerY + x, color, thickness);
            drawPixelThick(context, centerX + y, centerY - x, color, thickness);
            drawPixelThick(context, centerX - y, centerY - x, color, thickness);

            if (d < 0) {
                d += 2 * x + 3;
            } else {
                d += 2 * (x - y) + 5;
                y--;
            }
            x++;
        }
    }

    /**
     * Draw a pixel with thickness
     */
    private static void drawPixelThick(DrawContext context, int x, int y, int color, int thickness) {
        if (thickness == 1) {
            context.fill(x, y, x + 1, y + 1, color);
        } else {
            int half = thickness / 2;
            context.fill(x - half, y - half, x + half + 1, y + half + 1, color);
        }
    }
}
