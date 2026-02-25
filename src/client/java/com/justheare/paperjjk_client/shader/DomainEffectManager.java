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

    // Fallback defaults (used only by /jjkdebug simpledomain)
    public static final float DEFAULT_MAX_RADIUS      = 10.0f;
    public static final float DEFAULT_BASE_DECAY_RATE = 0.8f;
    public static final float DEFAULT_CHARGE_RATE     = 7.0f;
    public static final int   DEFAULT_EXPANSION_DELAY = 100;
    public static final int   DEFAULT_MAX_POWER       = 230;

    /** Per-caster domain state. */
    public static class DomainState {
        public final UUID    casterUuid;
        /** True if this domain belongs to the local player. Only this domain uses tickPosition. */
        public final boolean isLocalPlayerCaster;

        public Vec3d   casterFeetPos;
        public boolean isCharging;
        public double  currentPower;
        public long    lastSyncTimeMs;
        public int     expansionDelay;
        public int     maxPower;
        /** Server-authoritative simulation constants (synced via ACTIVATE packet). */
        public double  chargeRate;
        public double  baseDecayRate;
        public double  maxRadius;

        DomainState(UUID casterUuid, boolean isLocalPlayerCaster,
                    Vec3d feetPos, double power, int expDelay, int maxPow,
                    double chargeRate, double baseDecayRate, double maxRadius) {
            this.casterUuid          = casterUuid;
            this.isLocalPlayerCaster = isLocalPlayerCaster;
            this.casterFeetPos       = feetPos;
            this.isCharging          = true;
            this.currentPower        = power;
            this.expansionDelay      = expDelay;
            this.maxPower            = maxPow;
            this.chargeRate          = chargeRate;
            this.baseDecayRate       = baseDecayRate;
            this.maxRadius           = maxRadius;
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
                return Math.min(maxPower, currentPower + chargeRate * elapsedTicks);
            } else {
                return Math.max(0.0, currentPower - baseDecayRate * elapsedTicks);
            }
        }

        public float getExpandRadius() {
            double p = getSimulatedPower();
            double circleRange = maxPower - expansionDelay;
            if (circleRange <= 0) return 0.0f;
            return (float) (Math.max(0.0, (p - expansionDelay) / circleRange) * maxRadius);
        }

        public float getDarkRadius() {
            double p = getSimulatedPower();
            if (expansionDelay <= 0) return (float)(maxRadius * 3.0);
            return (float) (Math.min(p / expansionDelay, 1.0) * maxRadius * 2.0);
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
                                  int expDelay, int maxPow,
                                  double chargeRate, double baseDecayRate, double maxRadius,
                                  boolean isLocalCaster) {
        DomainState state = domains.get(casterUuid);
        if (state != null) {
            // Re-activate: update position/power/rates, resume charging
            state.casterFeetPos  = feetPos;
            state.isCharging     = true;
            state.currentPower   = power;
            state.chargeRate     = chargeRate;
            state.baseDecayRate  = baseDecayRate;
            state.maxRadius      = maxRadius;
            state.lastSyncTimeMs = System.currentTimeMillis();
        } else {
            domains.put(casterUuid, new DomainState(casterUuid, isLocalCaster, feetPos, power, expDelay, maxPow, chargeRate, baseDecayRate, maxRadius));
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

    /** SIMPLE_DOMAIN_TRANSLATE (0x25) — location moved (translated into another domain) */
    public static void onTranslate(UUID casterUuid, double locX, double locY, double locZ) {
        DomainState state = domains.get(casterUuid);
        if (state == null) return;
        state.casterFeetPos = new Vec3d(locX, locY, locZ);
    }

    /** SIMPLE_DOMAIN_DEACTIVATE (0x24) */
    public static void onDeactivate(UUID casterUuid) {
        domains.remove(casterUuid);
    }

    // ── Debug shim (/jjkdebug simpledomain) ────────────────────────────────

    public static void activate(Vec3d feetPos, UUID localPlayerUuid) {
        onActivate(localPlayerUuid, feetPos, 0.0, DEFAULT_EXPANSION_DELAY, DEFAULT_MAX_POWER,
                   DEFAULT_CHARGE_RATE, DEFAULT_BASE_DECAY_RATE, DEFAULT_MAX_RADIUS, true);
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
