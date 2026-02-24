package com.justheare.paperjjk_client.shader;

import net.minecraft.util.math.Vec3d;

/**
 * Manages the simple domain (간이영역) visual effect state.
 *
 * Effect design:
 *   - power 0 → EXPANSION_DELAY : dark zone gradually forms (0 → 50% dark), no white circle
 *   - power > EXPANSION_DELAY   : white circle appears and grows; dark zone at 50%
 *
 * Radius formulas (power = 0-100 raw server value):
 *   White circle : max(0, power - expansionDelay) / 100.0 * MAX_RADIUS  (blocks)
 *   Dark inner   : power / 100.0 * MAX_RADIUS * 3                        (blocks)
 *   Dark gradient: dark inner  →  dark inner * 4/3                       (blocks)
 *   Darkness     : clamp(power / expansionDelay, 0, 1) * 0.5             (0.0–0.5)
 *
 * Server constants mirrored here (must stay in sync with SimpleDomainManager):
 *   CHARGE_RATE     = 10.0 % per tick
 *   BASE_DECAY_RATE =  0.5 % per tick
 *   MAX_RADIUS      =  7.0 blocks
 *   DEFAULT_EXPANSION_DELAY = 3 (raw power units; updated by server on activate)
 */
public class DomainEffectManager {

    public static final float MAX_RADIUS            = 7.0f;
    public static final float BASE_DECAY_RATE       = 0.5f;
    public static final float CHARGE_RATE           = 10.0f;
    public static final int   DEFAULT_EXPANSION_DELAY = 3;

    private static boolean active         = false;
    private static Vec3d   casterFeetPos  = Vec3d.ZERO;
    private static int     expansionDelay = DEFAULT_EXPANSION_DELAY;

    private static double  currentPower   = 0.0;
    private static boolean isCharging     = false;
    private static long    lastSyncTimeMs = 0;

    // ── Packet-driven updates ───────────────────────────────────────────────

    /** SIMPLE_DOMAIN_ACTIVATE (0x21) */
    public static void onActivate(Vec3d feetPos, double power, int expDelay) {
        active         = true;
        casterFeetPos  = feetPos;
        currentPower   = power;
        isCharging     = true;
        expansionDelay = expDelay;
        lastSyncTimeMs = System.currentTimeMillis();
    }

    /** SIMPLE_DOMAIN_CHARGING_END (0x22) */
    public static void onChargingEnd(double power) {
        currentPower   = power;
        isCharging     = false;
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
        onActivate(feetPos, 0.0, DEFAULT_EXPANSION_DELAY);
    }

    public static void deactivate() {
        onDeactivate();
    }

    // ── Queries ─────────────────────────────────────────────────────────────

    public static boolean isActive() { return active; }

    public static Vec3d getCasterFeetPos() { return casterFeetPos; }

    /** Locally-simulated power (0-100), smoothed between server packets. */
    private static double getSimulatedPower() {
        double elapsedTicks = (System.currentTimeMillis() - lastSyncTimeMs) / 1000.0 * 20.0;
        if (isCharging) {
            return Math.min(100.0, currentPower + CHARGE_RATE * elapsedTicks);
        } else {
            return Math.max(0.0, currentPower - BASE_DECAY_RATE * elapsedTicks);
        }
    }

    /**
     * White circle radius in blocks.
     * 0 until power exceeds expansionDelay, then grows from 0 to MAX_RADIUS.
     */
    public static float getExpandRadius() {
        if (!active) return 0.0f;
        double p = getSimulatedPower();
        return (float) (Math.max(0.0, p - expansionDelay) / 100.0 * MAX_RADIUS);
    }

    /**
     * Inner boundary of the dark zone in blocks (= power/100 * MAX_RADIUS * 3).
     * The gradient fades from this radius outward to darkRadius * 4/3.
     */
    public static float getDarkRadius() {
        if (!active) return 0.0f;
        double p = getSimulatedPower();
        return (float) (p / 100.0 * MAX_RADIUS * 3.0);
    }

    /**
     * How dark the inner zone is (0.0–0.5).
     * Ramps from 0 to 0.5 as power goes from 0 to expansionDelay,
     * then stays at 0.5 once the white circle appears.
     */
    public static float getDarknessLevel() {
        if (!active) return 0.0f;
        double p = getSimulatedPower();
        if (expansionDelay <= 0) return 0.5f;
        return (float) Math.min(p / expansionDelay, 1.0) * 0.5f;
    }
}
