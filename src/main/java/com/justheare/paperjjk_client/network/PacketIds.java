package com.justheare.paperjjk_client.network;

/**
 * Plugin Messaging packet ID definitions
 * Must match server PacketIds.java exactly
 */
public class PacketIds {
    // Client → Server: Skill Usage Packets
    public static final byte SKILL_RCT = 0x01;              // RCT healing start/end
    public static final byte SKILL_SIMPLE_DOMAIN = 0x02;    // Simple Domain charge start/end
    public static final byte SKILL_TECHNIQUE = 0x03;        // Technique slot 1-4 charge/cast
    public static final byte SKILL_REVERSE_TECHNIQUE = 0x04; // Reverse technique (Z + slot) charge/cast
    public static final byte SKILL_TERMINATE = 0x05;        // Terminate active skill
    public static final byte SKILL_CONTROL = 0x06;          // Control active skill
    public static final byte SKILL_CONFIG = 0x07;           // Configure skill settings
    public static final byte SKILL_DISTANCE = 0x09;         // Adjust skill spawn distance (scroll)
    public static final byte DOMAIN_EXPANSION = 0x08;       // Domain expansion start/end
    public static final byte DOMAIN_SETTINGS = 0x0A;        // Domain settings update/request

    // Server → Client
    public static final byte TECHNIQUE_FEEDBACK = 0x10;     // Technique success/failure feedback
    public static final byte DOMAIN_SETTINGS_RESPONSE = 0x16; // Domain settings response
    public static final byte DOMAIN_VISUAL = 0x11;          // Domain visual effects
    public static final byte CE_UPDATE = 0x12;              // Cursed energy update
    public static final byte TECHNIQUE_COOLDOWN = 0x13;     // Cooldown notification
    public static final byte PARTICLE_EFFECT = 0x14;        // Particle effects
    public static final byte SCREEN_EFFECT = 0x15;          // Screen effects
    public static final byte INFINITY_AO = 0x17;            // Infinity Ao (refraction effect sync)
    public static final byte INFINITY_AKA = 0x18;           // Infinity Aka (red expansion effect sync)
    public static final byte INFINITY_MURASAKI = 0x19;      // Infinity Murasaki (purple expansion effect sync)

    // Bidirectional
    public static final byte HANDSHAKE = 0x20;

    /**
     * Skill Action Type (action field in skill packets)
     */
    public static class SkillAction {
        public static final byte START = 0x01;      // Start charging/activation
        public static final byte END = 0x02;        // End/release/cast
    }

    /**
     * Technique Slot (slot field in SKILL_TECHNIQUE packets)
     */
    public static class TechniqueSlot {
        public static final byte SLOT_1 = 0x01;  // X key
        public static final byte SLOT_2 = 0x02;  // C key
        public static final byte SLOT_3 = 0x03;  // V key
        public static final byte SLOT_4 = 0x04;  // B key
    }

    /**
     * Domain Expansion Flags (flags field in DOMAIN_EXPANSION packet)
     */
    public static class DomainFlags {
        public static final byte NORMAL = 0x00;          // Normal domain with barrier
        public static final byte NO_BARRIER = 0x01;      // No barrier domain (Shift + R)
    }

    /**
     * Domain Settings Action (action field in DOMAIN_SETTINGS packet)
     */
    public static class DomainSettingsAction {
        public static final byte REQUEST = 0x01;   // Request current settings from server
        public static final byte UPDATE = 0x02;    // Update settings on server
    }

    /**
     * Technique Failure Reason (reason field in TECHNIQUE_FEEDBACK packet)
     */
    public static class FailureReason {
        public static final byte SUCCESS = 0x00;
        public static final byte INSUFFICIENT_CE = 0x01;  // Insufficient cursed energy
        public static final byte ON_COOLDOWN = 0x02;      // On cooldown
        public static final byte BLOCKED = 0x03;          // Blocked state
    }

    /**
     * Domain Visual Action Type (action field in DOMAIN_VISUAL packet)
     */
    public static class DomainVisualAction {
        public static final byte START = 0x01;       // Start expansion animation
        public static final byte SYNC = 0x02;        // Sync current radius (every 3 seconds)
        public static final byte END = 0x03;         // Destroy domain
    }

    /**
     * Domain Type (domainType field in DOMAIN_VISUAL packet)
     */
    public static class DomainType {
        public static final int NORMAL = 0;        // Normal domain with barrier
        public static final int NO_BARRIER = 1;    // Barrier-less domain
        public static final int MIZUSHI = 2;       // Malevolent Shrine
        public static final int INFINITY = 3;      // Unlimited Void
        public static final int OTHER = 4;         // Other innate domains
    }

    /**
     * Particle Effect Type (effectType field in PARTICLE_EFFECT packet)
     */
    public static class ParticleType {
        public static final byte INFINITY_EYE = 0x01;      // Six Eyes
        public static final byte MIZUSHI_ARROW = 0x02;     // Arrow trajectory
        public static final byte DOMAIN_AURA = 0x03;       // Domain aura
        public static final byte CE_WAVE = 0x04;           // Cursed energy wave
        public static final byte MAHORAGA_ADAPT = 0x05;    // Mahoraga adaptation
    }

    /**
     * Screen Effect Type (effectType field in SCREEN_EFFECT packet)
     */
    public static class ScreenEffectType {
        public static final byte INFINITY_DOMAIN = 0x01;   // Unlimited Void (distortion + whitening)
        public static final byte DOMAIN_ENTER = 0x02;      // Domain entry animation
        public static final byte DAMAGE_VIGNETTE = 0x03;   // Damage vignetting
        public static final byte CE_DEPLETION = 0x04;      // CE depletion (grayscale)
    }

    /**
     * Infinity Ao Action Type (action field in INFINITY_AO packet)
     */
    public static class InfinityAoAction {
        public static final byte START = 0x01;       // Start Ao (spawn refraction effect)
        public static final byte SYNC = 0x02;        // Sync position & strength (every 500ms)
        public static final byte END = 0x03;         // End Ao (remove effect)
    }

    /**
     * Infinity Aka Action Type (action field in INFINITY_AKA packet)
     */
    public static class InfinityAkaAction {
        public static final byte START = 0x01;       // Start Aka (spawn red expansion effect)
        public static final byte SYNC = 0x02;        // Sync position & strength (every 500ms)
        public static final byte END = 0x03;         // End Aka (remove effect)
    }

    /**
     * Infinity Murasaki Action Type (action field in INFINITY_MURASAKI packet)
     */
    public static class InfinityMurasakiAction {
        public static final byte START = 0x01;       // Start normal murasaki (moving purple expansion)
        public static final byte SYNC = 0x02;        // Sync position & strength (moving type)
        public static final byte START_EXPLODE = 0x03; // Start unlimit_m explosion at fixed position
        public static final byte SYNC_RADIUS = 0x04;  // Sync expanding radius (explosion type)
        public static final byte END = 0x05;         // End murasaki effect
    }
}
