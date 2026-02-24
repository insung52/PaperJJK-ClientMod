package com.justheare.paperjjk_client.shader;

import net.minecraft.util.math.Vec3d;

/**
 * Manages the simple domain (간이영역) visual effect state.
 *
 * Two-phase design driven by server constants (EXPANSION_DELAY, MAX_POWER):
 *   Phase 1  power 0 → expansionDelay  : dark zone grows (0→50%), no white circle
 *   Phase 2  power expansionDelay → maxPower : white circle grows (0→MAX_RADIUS), dark zone stable
 *
 * Radius formulas:
 *   DarknessLevel : clamp(p / expansionDelay, 0, 1) * 0.5
 *   DarkRadius    : clamp(p / expansionDelay, 0, 1) * MAX_RADIUS * 3   (blocks)
 *   ExpandRadius  : max(0, (p - expansionDelay) / (maxPower - expansionDelay)) * MAX_RADIUS
 *
 * Server constants mirrored here (must stay in sync with SimpleDomainManager):
 *   CHARGE_RATE     = 10.0 % per tick
 *   BASE_DECAY_RATE =  0.8 % per tick
 *   MAX_RADIUS      = 10.0 blocks
 *   DEFAULT_EXPANSION_DELAY = 100
 *   DEFAULT_MAX_POWER       = 230
 */
public class DomainEffectManager {

    public static final float MAX_RADIUS              = 10.0f;
    public static final float BASE_DECAY_RATE         = 0.8f;
    public static final float CHARGE_RATE             = 10.0f;
    public static final int   DEFAULT_EXPANSION_DELAY = 100;
    public static final int   DEFAULT_MAX_POWER       = 230;

    private static boolean active         = false;
    private static Vec3d   casterFeetPos  = Vec3d.ZERO;
    private static int     expansionDelay = DEFAULT_EXPANSION_DELAY;
    private static int     maxPower       = DEFAULT_MAX_POWER;

    private static double  currentPower   = 0.0;
    private static boolean isCharging     = false;
    private static long    lastSyncTimeMs = 0;

    // ── Packet-driven updates ───────────────────────────────────────────────

    /** SIMPLE_DOMAIN_ACTIVATE (0x21) */
    public static void onActivate(Vec3d feetPos, double power, int expDelay, int maxPow) {
        active         = true;
        casterFeetPos  = feetPos;
        currentPower   = power;
        isCharging     = true;
        expansionDelay = expDelay;
        maxPower       = maxPow;
        lastSyncTimeMs = System.currentTimeMillis();
    }

    /** SIMPLE_DOMAIN_CHARGING_END (0x22) */
    public static void onChargingEnd(double power, double locX, double locY, double locZ) {
        currentPower   = power;
        isCharging     = false;
        casterFeetPos  = new Vec3d(locX, locY, locZ);
        lastSyncTimeMs = System.currentTimeMillis();
    }

    /** SIMPLE_DOMAIN_POWER_SYNC (0x23) */
    public static void onPowerSync(double power) {
        currentPower   = power;
        lastSyncTimeMs = System.currentTimeMillis();
    }

    /** SIMPLE_DOMAIN_DEACTIVATE (0x24) */
    public static void onDeactivate() {
        active       = false;
        currentPower = 0.0;
        isCharging   = false;
    }

    // ── Debug shim (/jjkdebug simpledomain) ────────────────────────────────

    public static void activate(Vec3d feetPos) {
        onActivate(feetPos, 0.0, DEFAULT_EXPANSION_DELAY, DEFAULT_MAX_POWER);
    }

    public static void deactivate() {
        onDeactivate();
    }

    // ── Queries ─────────────────────────────────────────────────────────────

    public static boolean isActive() { return active; }

    public static Vec3d getCasterFeetPos() { return casterFeetPos; }

    /**
     * Smoothly moves casterFeetPos toward playerFeetPos while charging,
     * mirroring the server-side movement formula applied in tick().
     * Call once per frame before rendering, passing the render delta ticks.
     * Speed: min(distance, 0.1) blocks per tick × deltaTicks.
     */
    public static void tickPosition(Vec3d playerFeetPos, float deltaTicks) {
        if (!active || !isCharging) return;
        Vec3d direction = playerFeetPos.subtract(casterFeetPos);
        double dist = direction.length();
        if (dist < 0.001) return;
        double speed = Math.min(dist, 0.1 * deltaTicks);
        casterFeetPos = casterFeetPos.add(direction.normalize().multiply(speed));
    }

    /** Locally-simulated power (0 to maxPower), smoothed between server packets. */
    private static double getSimulatedPower() {
        double elapsedTicks = (System.currentTimeMillis() - lastSyncTimeMs) / 1000.0 * 20.0;
        if (isCharging) {
            return Math.min(maxPower, currentPower + CHARGE_RATE * elapsedTicks);
        } else {
            return Math.max(0.0, currentPower - BASE_DECAY_RATE * elapsedTicks);
        }
    }

    /**
     * White circle radius in blocks.
     * 0 during phase 1 (p <= expansionDelay).
     * Grows 0 → MAX_RADIUS during phase 2 (p = expansionDelay → maxPower).
     */
    public static float getExpandRadius() {
        if (!active) return 0.0f;
        double p = getSimulatedPower();
        double circleRange = maxPower - expansionDelay;
        if (circleRange <= 0) return 0.0f;
        return (float) (Math.max(0.0, (p - expansionDelay) / circleRange) * MAX_RADIUS);
    }

    /**
     * Inner boundary of the dark zone in blocks.
     * Grows 0 → MAX_RADIUS*3 during phase 1, stays fixed in phase 2.
     */
    public static float getDarkRadius() {
        if (!active) return 0.0f;
        double p = getSimulatedPower();
        if (expansionDelay <= 0) return MAX_RADIUS * 3.0f;
        return (float) (Math.min(p / expansionDelay, 1.0) * MAX_RADIUS * 2.0);
    }

    /**
     * How dark the inner zone is (0.0–0.5).
     * Ramps 0→0.5 during phase 1, stays 0.5 in phase 2.
     */
    public static float getDarknessLevel() {
        if (!active) return 0.0f;
        double p = getSimulatedPower();
        if (expansionDelay <= 0) return 0.5f;
        return (float) Math.min(p / expansionDelay, 1.0) * 0.5f;
    }
}
