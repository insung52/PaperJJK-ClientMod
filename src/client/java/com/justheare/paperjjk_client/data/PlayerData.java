package com.justheare.paperjjk_client.data;

import java.util.HashMap;
import java.util.Map;

/**
 * Client-side player data cache
 * Stores player info and skill descriptions received from server
 */
public class PlayerData {
    // Player info
    private static String naturaltech = "";
    private static int curseEnergy = 0;
    private static int maxCurseEnergy = 0;
    private static boolean hasRCT = false;
    private static int domainLevel = 0;
    private static int efficiencyLevel = 0;
    private static boolean canGraspAirSurface = false;
    private static String slot1Skill = "";
    private static String slot2Skill = "";
    private static String slot3Skill = "";
    private static String slot4Skill = "";

    // Skill descriptions cache
    private static final Map<String, SkillInfo> skillCache = new HashMap<>();

    // Available skills for player's naturaltech
    private static java.util.List<String> availableSkills = new java.util.ArrayList<>();

    // Client settings (stored client-side)
    private static boolean postProcessingEnabled = true;
    private static boolean domainEffectsEnabled = true;
    private static boolean hudEnabled = true;

    /**
     * Update player info from server response
     */
    public static void updatePlayerInfo(String naturaltech, int ce, int maxCE, boolean rct, int domainLvl,
                                       String slot1, String slot2, String slot3, String slot4,
                                       int efficiencyLvl, boolean airGrasp) {
        PlayerData.naturaltech = naturaltech;
        PlayerData.curseEnergy = ce;
        PlayerData.maxCurseEnergy = maxCE;
        PlayerData.hasRCT = rct;
        PlayerData.domainLevel = domainLvl;
        PlayerData.efficiencyLevel = efficiencyLvl;
        PlayerData.canGraspAirSurface = airGrasp;
        PlayerData.slot1Skill = slot1;
        PlayerData.slot2Skill = slot2;
        PlayerData.slot3Skill = slot3;
        PlayerData.slot4Skill = slot4;
    }

    /**
     * Update skill info from server response
     */
    public static void updateSkillInfo(SkillInfo info) {
        skillCache.put(info.skillId, info);
    }

    // Getters
    public static String getNaturaltech() { return naturaltech; }
    public static int getCurseEnergy() { return curseEnergy; }
    public static int getMaxCurseEnergy() { return maxCurseEnergy; }
    public static boolean hasRCT() { return hasRCT; }
    public static int getDomainLevel() { return domainLevel; }
    public static int getEfficiencyLevel() { return efficiencyLevel; }
    public static boolean canGraspAirSurface() { return canGraspAirSurface; }
    public static String getSlot1Skill() { return slot1Skill; }
    public static String getSlot2Skill() { return slot2Skill; }
    public static String getSlot3Skill() { return slot3Skill; }
    public static String getSlot4Skill() { return slot4Skill; }

    public static SkillInfo getSkillInfo(String skillId) {
        return skillCache.get(skillId);
    }

    // Available skills
    public static java.util.List<String> getAvailableSkills() {
        return new java.util.ArrayList<>(availableSkills);
    }

    public static void setAvailableSkills(java.util.List<String> skills) {
        availableSkills = new java.util.ArrayList<>(skills);
    }

    // Client settings
    public static boolean isPostProcessingEnabled() { return postProcessingEnabled; }
    public static void setPostProcessingEnabled(boolean enabled) { postProcessingEnabled = enabled; }

    public static boolean isDomainEffectsEnabled() { return domainEffectsEnabled; }
    public static void setDomainEffectsEnabled(boolean enabled) { domainEffectsEnabled = enabled; }

    public static boolean isHudEnabled() { return hudEnabled; }
    public static void setHudEnabled(boolean enabled) { hudEnabled = enabled; }

    /**
     * Skill information data class
     */
    public static class SkillInfo {
        public final String skillId;
        public final String displayName;
        public final String description;
        public final String requiredCE;

        public SkillInfo(String skillId, String displayName, String description, String requiredCE) {
            this.skillId = skillId;
            this.displayName = displayName;
            this.description = description;
            this.requiredCE = requiredCE;
        }
    }
}
