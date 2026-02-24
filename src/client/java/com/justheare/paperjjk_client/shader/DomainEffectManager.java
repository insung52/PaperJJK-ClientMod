package com.justheare.paperjjk_client.shader;

import net.minecraft.util.math.Vec3d;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages simple domain (간이영역) visual effect state.
 * Supports multiple simultaneous domains (one per caster UUID).
 *
 * Two-phase design driven by server constants (EXPANSION_DELAY, MAX_POWER):
 *   Phase 1  power 0 → expansionDelay  : dark zone grows (0→50%), no white circle
 *   Phase 2  power expansionDelay → maxPower : white circle grows (0→MAX_RADIUS), dark zone stable
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

    /** Per-caster domain state. */
    public static class DomainState {
        public final UUID    casterUuid;
        /** True if this domain belongs to the local player. Only this domain uses tickPosition. */
        public final boolean isLocalPlayerCaster;

        public Vec3d  casterFeetPos;
        public boolean isCharging;
        public double  currentPower;
        public long    lastSyncTimeMs;
        public int     expansionDelay;
        public int     maxPower;

        DomainState(UUID casterUuid, boolean isLocalPlayerCaster,
                    Vec3d feetPos, double power, int expDelay, int maxPow) {
            this.casterUuid          = casterUuid;
            this.isLocalPlayerCaster = isLocalPlayerCaster;
            this.casterFeetPos       = feetPos;
            this.isCharging          = true;
            this.currentPower        = power;
            this.expansionDelay      = expDelay;
            this.maxPower            = maxPow;
            this.lastSyncTimeMs      = System.currentTimeMillis();
        }

        /**
         * Moves casterFeetPos toward playerFeetPos while charging.
         * Mirrors the server-side location interpolation.
         * Only call this for the local player's domain (isLocalPlayerCaster == true).
         */
        public void tickPosition(Vec3d playerFeetPos, float deltaTicks) {
            if (!isCharging) return;
            Vec3d direction = playerFeetPos.subtract(casterFeetPos);
            double dist = direction.length();
            if (dist < 0.001) return;
            double speed = Math.min(dist, 0.1 * deltaTicks);
            casterFeetPos = casterFeetPos.add(direction.normalize().multiply(speed));
        }

        private double getSimulatedPower() {
            double elapsedTicks = (System.currentTimeMillis() - lastSyncTimeMs) / 1000.0 * 20.0;
            if (isCharging) {
                return Math.min(maxPower, currentPower + CHARGE_RATE * elapsedTicks);
            } else {
                return Math.max(0.0, currentPower - BASE_DECAY_RATE * elapsedTicks);
            }
        }

        public float getExpandRadius() {
            double p = getSimulatedPower();
            double circleRange = maxPower - expansionDelay;
            if (circleRange <= 0) return 0.0f;
            return (float) (Math.max(0.0, (p - expansionDelay) / circleRange) * MAX_RADIUS);
        }

        public float getDarkRadius() {
            double p = getSimulatedPower();
            if (expansionDelay <= 0) return MAX_RADIUS * 3.0f;
            return (float) (Math.min(p / expansionDelay, 1.0) * MAX_RADIUS * 2.0);
        }

        public float getDarknessLevel() {
            double p = getSimulatedPower();
            if (expansionDelay <= 0) return 0.5f;
            return (float) Math.min(p / expansionDelay, 1.0) * 0.5f;
        }
    }

    // casterUUID → DomainState; LinkedHashMap preserves insertion order for render
    private static final Map<UUID, DomainState> domains = new LinkedHashMap<>();

    // ── Packet-driven updates ───────────────────────────────────────────────

    /** SIMPLE_DOMAIN_ACTIVATE (0x21) */
    public static void onActivate(UUID casterUuid, Vec3d feetPos, double power,
                                  int expDelay, int maxPow, boolean isLocalCaster) {
        DomainState state = domains.get(casterUuid);
        if (state != null) {
            // Re-activate: update position/power, resume charging
            state.casterFeetPos  = feetPos;
            state.isCharging     = true;
            state.currentPower   = power;
            state.lastSyncTimeMs = System.currentTimeMillis();
        } else {
            domains.put(casterUuid, new DomainState(casterUuid, isLocalCaster, feetPos, power, expDelay, maxPow));
        }
    }

    /** SIMPLE_DOMAIN_CHARGING_END (0x22) */
    public static void onChargingEnd(UUID casterUuid, double power, double locX, double locY, double locZ) {
        DomainState state = domains.get(casterUuid);
        if (state == null) return;
        state.currentPower   = power;
        state.isCharging     = false;
        state.casterFeetPos  = new Vec3d(locX, locY, locZ);
        state.lastSyncTimeMs = System.currentTimeMillis();
    }

    /** SIMPLE_DOMAIN_POWER_SYNC (0x23) */
    public static void onPowerSync(UUID casterUuid, double power) {
        DomainState state = domains.get(casterUuid);
        if (state == null) return;
        state.currentPower   = power;
        state.lastSyncTimeMs = System.currentTimeMillis();
    }

    /** SIMPLE_DOMAIN_DEACTIVATE (0x24) */
    public static void onDeactivate(UUID casterUuid) {
        domains.remove(casterUuid);
    }

    // ── Debug shim (/jjkdebug simpledomain) ────────────────────────────────

    public static void activate(Vec3d feetPos, UUID localPlayerUuid) {
        onActivate(localPlayerUuid, feetPos, 0.0, DEFAULT_EXPANSION_DELAY, DEFAULT_MAX_POWER, true);
    }

    public static void deactivate(UUID localPlayerUuid) {
        onDeactivate(localPlayerUuid);
    }

    // ── Queries ─────────────────────────────────────────────────────────────

    public static boolean hasActiveDomains() { return !domains.isEmpty(); }

    public static Collection<DomainState> getActiveDomains() { return domains.values(); }

    /** Returns domain state for the given caster, or null if not active. */
    public static DomainState getDomain(UUID casterUuid) { return domains.get(casterUuid); }
}
