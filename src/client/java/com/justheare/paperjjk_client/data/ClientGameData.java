package com.justheare.paperjjk_client.data;

import net.minecraft.util.math.Vec3d;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 서버로부터 받은 게임 데이터를 캐싱하는 클래스
 * HUD 렌더링 시 이 데이터를 사용
 */
public class ClientGameData {
    // 주술력 정보
    private static int currentCE = 0;
    private static int maxCE = 1000;
    private static float regenRate = 0.0f;
    private static String currentTechnique = "없음";
    private static boolean blocked = false;
    public static boolean hasDebugRun = false;

    // 쿨다운 정보 (슬롯 번호 → 쿨다운 데이터)
    private static final Map<Byte, CooldownData> cooldowns = new HashMap<>();

    // 영역전개 렌더링 정보 (도메인 ID → 도메인 데이터)
    private static final Map<UUID, ActiveDomain> activeDomains = new HashMap<>();

    // 신체강화 (0.0~1.0 비율, 서버 패킷 추가 전까지 기본 0)
    private static float bodyReinforcementRatio = 0f;

    // 스킬 슬롯 충전 게이지 (슬롯 번호 1~4 → 0.0~1.0 비율)
    private static final Map<Byte, Float> slotGauges = new HashMap<>();

    // 스킬 슬롯 상태 (슬롯 번호 1~4 → SlotGaugeState 바이트)
    private static final Map<Byte, Byte> slotStates = new HashMap<>();

    // 슬롯 자물쇠 (T키 고정 등)
    private static final Map<Byte, Boolean> slotLocked = new HashMap<>();

    // 슬롯 레이블 (거리: "3m", 방향: "↑"/"↓" 등)
    private static final Map<Byte, String> slotLabels = new HashMap<>();

    // 비활성화된 스킬 슬롯 (술식 타버림 등 디버프)
    private static final Set<Byte> disabledSlots = new HashSet<>();

    /**
     * 주술력 설정
     */
    public static void setCE(int current, int max) {
        currentCE = current;
        maxCE = max;
    }

    /**
     * 회복량 설정
     */
    public static void setRegenRate(float rate) {
        regenRate = rate;
    }

    /**
     * 현재 술식 설정
     */
    public static void setTechnique(String technique) {
        currentTechnique = technique;
    }

    /**
     * 차단 상태 설정
     */
    public static void setBlocked(boolean isBlocked) {
        blocked = isBlocked;
    }

    /**
     * 쿨다운 설정
     */
    public static void setCooldown(byte slot, int currentTicks, int maxTicks) {
        cooldowns.put(slot, new CooldownData(currentTicks, maxTicks));
    }

    // Getters
    public static int getCurrentCE() {
        return currentCE;
    }

    public static int getMaxCE() {
        return maxCE;
    }

    public static float getRegenRate() {
        return regenRate;
    }

    public static String getCurrentTechnique() {
        return currentTechnique;
    }

    public static boolean isBlocked() {
        return blocked;
    }

    /**
     * 주술력 퍼센트 (0.0 ~ 1.0)
     */
    public static float getCEPercentage() {
        if (maxCE == 0)
            return 0;
        return (float) currentCE / maxCE;
    }

    /**
     * 쿨다운 데이터 가져오기
     */
    public static CooldownData getCooldown(byte slot) {
        return cooldowns.getOrDefault(slot, new CooldownData(0, 0));
    }

    // 신체강화 setter/getter
    public static void setBodyReinforcement(float ratio) { bodyReinforcementRatio = ratio; }
    public static float getBodyReinforcementRatio() { return bodyReinforcementRatio; }

    // 슬롯 충전 게이지 setter/getter (slot: 1~4)
    public static void setSlotGauge(byte slot, float ratio) { slotGauges.put(slot, ratio); }
    public static float getSlotGauge(byte slot) { return slotGauges.getOrDefault(slot, 0f); }

    // 슬롯 상태 setter/getter (state: SlotGaugeState 바이트)
    public static void setSlotState(byte slot, byte state) { slotStates.put(slot, state); }
    public static byte getSlotState(byte slot) { return slotStates.getOrDefault(slot, (byte)0x00); }

    // 슬롯 자물쇠 setter/getter
    public static void setSlotLocked(byte slot, boolean locked) { slotLocked.put(slot, locked); }
    public static boolean isSlotLocked(byte slot) { return slotLocked.getOrDefault(slot, false); }

    // 슬롯 레이블 setter/getter
    public static void setSlotLabel(byte slot, String label) { slotLabels.put(slot, label != null ? label : ""); }
    public static String getSlotLabel(byte slot) { return slotLabels.getOrDefault(slot, ""); }

    // 슬롯 비활성화 setter/getter
    public static void setSlotDisabled(byte slot, boolean disabled) {
        if (disabled) disabledSlots.add(slot);
        else disabledSlots.remove(slot);
    }
    public static boolean isSlotDisabled(byte slot) { return disabledSlots.contains(slot); }

    /**
     * 모든 데이터 초기화 (서버 나갈 때 호출)
     */
    public static void reset() {
        currentCE = 0;
        maxCE = 1000;
        regenRate = 0.0f;
        currentTechnique = "없음";
        blocked = false;
        cooldowns.clear();
        activeDomains.clear();
        bodyReinforcementRatio = 0f;
        slotGauges.clear();
        slotStates.clear();
        slotLocked.clear();
        slotLabels.clear();
        disabledSlots.clear();
    }

    // === Domain Management ===

    /**
     * Get existing domain by ID, or null if not present.
     */
    public static ActiveDomain getDomain(UUID id) {
        return activeDomains.get(id);
    }

    /**
     * Add new domain for rendering
     */
    public static void addDomain(UUID id, ActiveDomain domain) {
        activeDomains.put(id, domain);
    }

    /**
     * Remove domain from rendering
     */
    public static void removeDomain(UUID id) {
        activeDomains.remove(id);
    }

    /**
     * Sync domain radius with server
     */
    public static void syncDomain(UUID id, float serverRadius) {
        ActiveDomain domain = activeDomains.get(id);
        if (domain != null) {
            domain.syncFromServer(serverRadius);
        }
    }

    /**
     * Complete domain expansion - snap to final radius and stop expanding
     * Called when server confirms expansion has reached max radius
     */
    public static void completeDomain(UUID id, float finalRadius) {
        ActiveDomain domain = activeDomains.get(id);
        if (domain != null) {
            domain.serverRadius = finalRadius;
            domain.currentRadius = finalRadius;
            domain.maxRadius = finalRadius;
            domain.isExpanding = false;
        }
    }

    /**
     * Update all domains (called every client tick)
     */
    public static void updateAllDomains() {
        for (ActiveDomain domain : activeDomains.values()) {
            domain.updateRadius();
        }
    }

    /**
     * Get all active domains for rendering
     */
    public static Collection<ActiveDomain> getAllDomains() {
        return activeDomains.values();
    }

    /**
     * Get active domains map (for renderer)
     */
    public static Map<UUID, ActiveDomain> getActiveDomains() {
        return activeDomains;
    }

    /**
     * 쿨다운 정보 저장 클래스
     */
    public static class CooldownData {
        private final int currentTicks;
        private final int maxTicks;

        public CooldownData(int currentTicks, int maxTicks) {
            this.currentTicks = currentTicks;
            this.maxTicks = maxTicks;
        }

        public int getCurrentTicks() {
            return currentTicks;
        }

        public int getMaxTicks() {
            return maxTicks;
        }

        public float getPercentage() {
            if (maxTicks == 0)
                return 0;
            return (float) currentTicks / maxTicks;
        }

        public boolean isOnCooldown() {
            return currentTicks > 0;
        }

        public float getSecondsRemaining() {
            return currentTicks / 20.0f; // 틱 → 초 변환
        }
    }

    /**
     * Active domain expansion data
     * Client-side rendering info for barrier-less domain expansion
     */
    public static class ActiveDomain {
        public UUID domainId;
        public Vec3d center;
        public float currentRadius;
        public float maxRadius;
        public float expansionSpeed; // blocks per tick (recalculated on sync)
        public int color;
        public int domainType;
        public long lastSyncTime; // When we last received server update
        public float serverRadius; // Last known server radius
        public boolean isExpanding;

        /**
         * Update current radius - smoothly chase server radius
         * Never exceeds server radius, gradually catches up
         */
        public void updateRadius() {
            if (!isExpanding || currentRadius >= maxRadius) {
                currentRadius = Math.min(maxRadius, serverRadius);
                if (currentRadius >= maxRadius) {
                    isExpanding = false;
                }
                return;
            }

            // Calculate how much we can grow this tick
            // We chase the server radius but never exceed it
            if (currentRadius < serverRadius) {
                // Grow towards server radius at calculated speed
                currentRadius = Math.min(serverRadius, currentRadius + expansionSpeed);
            }
            // If currentRadius >= serverRadius, don't grow (wait for next server update)
        }

        /**
         * Sync with server radius (called frequently by server)
         * Recalculates expansion speed to smoothly reach new server radius
         */
        public void syncFromServer(float newServerRadius) {
            long currentTime = System.currentTimeMillis();
            long timeSinceLastSync = currentTime - lastSyncTime;

            if (timeSinceLastSync > 0 && newServerRadius > serverRadius) {
                // Server radius increased - calculate new expansion speed
                // Speed = how much server grew / time elapsed
                float serverGrowth = newServerRadius - serverRadius;
                float timeInTicks = timeSinceLastSync / 50.0f; // ms to ticks (50ms per tick)

                if (timeInTicks > 0) {
                    // Calculate speed to catch up smoothly
                    float newSpeed = serverGrowth / timeInTicks;

                    // Apply some smoothing to avoid jerky speed changes
                    if (expansionSpeed > 0) {
                        // Weighted average: 70% old speed, 30% new speed
                        expansionSpeed = expansionSpeed * 0.7f + newSpeed * 0.3f;
                    } else {
                        expansionSpeed = newSpeed;
                    }
                }
            }

            // Update server tracking
            serverRadius = newServerRadius;
            lastSyncTime = currentTime;

            // If client somehow got ahead (shouldn't happen), snap back
            if (currentRadius > serverRadius) {
                currentRadius = serverRadius;
            }
        }
    }
}
