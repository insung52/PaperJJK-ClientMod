package com.justheare.paperjjk_client.network;

import com.justheare.paperjjk_client.data.ClientGameData;
import com.justheare.paperjjk_client.network.packets.*;
import com.justheare.paperjjk_client.particle.ModParticles;
import com.justheare.paperjjk_client.shader.DomainEffectManager;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.netty.buffer.Unpooled;

/**
 * 서버로부터 패킷을 받아 처리하는 클라이언트 핸들러
 * 1.21.10 CustomPayload 시스템 사용
 */
public class ClientPacketHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("PaperJJK-Client");
    public static final Identifier CHANNEL = Identifier.of("paperjjk", "main");

    /**
     * 모든 패킷 수신 핸들러 등록
     */
    public static void register() {
        // 1.21.10에서는 CustomPayload 등록
        PayloadTypeRegistry.playS2C().register(
            JJKPayload.ID,
            JJKPayload.CODEC
        );

        // 패킷 수신 핸들러 등록
        ClientPlayNetworking.registerGlobalReceiver(
            JJKPayload.ID,
            (payload, context) -> {
                PacketByteBuf buf = new PacketByteBuf(Unpooled.wrappedBuffer(payload.data()));
                byte packetId = buf.readByte();

                LOGGER.debug("Packet received: 0x{}", String.format("%02X", packetId));

                try {
                    switch (packetId) {
                        case PacketIds.TECHNIQUE_FEEDBACK -> handleTechniqueFeedback(context.client(), buf);
                        case PacketIds.DOMAIN_VISUAL -> handleDomainVisual(context.client(), buf);
                        case PacketIds.CE_UPDATE -> handleCEUpdate(context.client(), buf);
                        case PacketIds.TECHNIQUE_COOLDOWN -> handleTechniqueCooldown(context.client(), buf);
                        case PacketIds.PARTICLE_EFFECT -> handleParticleEffect(context.client(), buf);
                        case PacketIds.SCREEN_EFFECT -> handleScreenEffect(context.client(), buf);
                        case PacketIds.DOMAIN_SETTINGS_RESPONSE -> handleDomainSettingsResponse(context.client(), buf);
                        case PacketIds.INFINITY_AO -> handleInfinityAo(context.client(), buf);
                        case PacketIds.INFINITY_AKA -> handleInfinityAka(context.client(), buf);
                        case PacketIds.INFINITY_MURASAKI -> handleInfinityMurasaki(context.client(), buf);
                        case PacketIds.PLAYER_INFO_RESPONSE -> handlePlayerInfoResponse(context.client(), buf);
                        case PacketIds.SKILL_INFO_RESPONSE -> handleSkillInfoResponse(context.client(), buf);
                        case PacketIds.HANDSHAKE -> handleHandshake(context.client(), buf);
                        case PacketIds.SIMPLE_DOMAIN_ACTIVATE -> handleSimpleDomainActivate(context.client(), buf);
                        case PacketIds.SIMPLE_DOMAIN_CHARGING_END -> handleSimpleDomainChargingEnd(context.client(), buf);
                        case PacketIds.SIMPLE_DOMAIN_POWER_SYNC -> handleSimpleDomainPowerSync(context.client(), buf);
                        case PacketIds.SIMPLE_DOMAIN_DEACTIVATE -> handleSimpleDomainDeactivate(context.client(), buf);
                        case PacketIds.SIMPLE_DOMAIN_TRANSLATE -> handleSimpleDomainTranslate(context.client(), buf);
                        case PacketIds.SLOT_GAUGE_UPDATE -> handleSlotGaugeUpdate(context.client(), buf);
                        case PacketIds.BODY_REIN_UPDATE  -> handleBodyReinUpdate(context.client(), buf);
                        case PacketIds.KAI_SLASH         -> handleKaiSlash(context.client(), buf);
                        case PacketIds.HACHI_SLASH       -> handleHachiSlash(context.client(), buf);
                        default -> LOGGER.warn("Unknown packet ID: 0x{}", String.format("%02X", packetId));
                    }
                } catch (Exception e) {
                    LOGGER.error("Error processing packet (ID: 0x{})", String.format("%02X", packetId), e);
                }
            }
        );

        LOGGER.info("Client packet handler registered: {}", CHANNEL);
    }

    /**
     * TECHNIQUE_FEEDBACK (0x10) - Technique success/failure feedback
     */
    private static void handleTechniqueFeedback(MinecraftClient client, PacketByteBuf buf) {
        TechniqueUsePacket packet = TechniqueUsePacket.read(buf);

        client.execute(() -> {
            if (packet.isSuccess()) {
                LOGGER.info("Technique success: {} - {}", packet.getTechniqueId(), packet.getMessage());
                // TODO: Success visual effects
            } else {
                LOGGER.warn("Technique failed: {} (reason: 0x{}) - {}",
                    packet.getTechniqueId(),
                    String.format("%02X", packet.getReason()),
                    packet.getMessage());
                // TODO: Failure feedback (chat, sound, etc.)
            }
        });
    }

    /**
     * DOMAIN_VISUAL (0x11) - Domain visual effects
     * Handles START/SYNC/END actions for barrier-less domain expansion
     */
    private static void handleDomainVisual(MinecraftClient client, PacketByteBuf buf) {
        byte action = buf.readByte();

        switch (action) {
            case PacketIds.DomainVisualAction.START -> {
                // v2 format: [uuidMost(8)][uuidLeast(8)][domainType(4)][cx(8)][cy(8)][cz(8)][maxRadius(4f)][isOpen(1b)]
                long uuidMost = buf.readLong();
                long uuidLeast = buf.readLong();
                java.util.UUID domainId = new java.util.UUID(uuidMost, uuidLeast);
                int domainType = buf.readInt();
                double centerX = buf.readDouble();
                double centerY = buf.readDouble();
                double centerZ = buf.readDouble();
                float maxRadius = buf.readFloat();
                boolean isOpen = buf.readBoolean();

                client.execute(() -> {
                    ClientGameData.ActiveDomain existing = ClientGameData.getDomain(domainId);
                    if (existing == null) {
                        // Fresh start
                        ClientGameData.ActiveDomain domain = new ClientGameData.ActiveDomain();
                        domain.domainId = domainId;
                        domain.center = new net.minecraft.util.math.Vec3d(centerX, centerY, centerZ);
                        domain.maxRadius = maxRadius;
                        domain.domainType = domainType;
                        domain.isOpen = isOpen;
                        domain.lastSyncTime = System.currentTimeMillis();
                        domain.color = 0;
                        domain.serverRadius = 0.0f;
                        domain.currentRadius = 0.0f;
                        domain.expansionSpeed = 0f;
                        domain.isExpanding = true;
                        ClientGameData.addDomain(domainId, domain);

                        // MIZUSHI 결없영(isOpen=true)만 ambient slash + 충전 효과 활성화
                        if (domainType == PacketIds.DomainType.MIZUSHI && isOpen) {
                            net.minecraft.util.math.Vec3d center =
                                new net.minecraft.util.math.Vec3d(centerX, centerY, centerZ);
                            // maxRadius=0 → 초기 전개, maxRadius>0 → 늦게 들어온 플레이어(주기적 재전송)
                            com.justheare.paperjjk_client.shader.AmbientKaiSlashManager
                                .setDomain(domainId, center, maxRadius);
                            // 충전 애니메이션은 초기 전개(radius=0)일 때만 재생
                            if (maxRadius == 0f) {
                                net.minecraft.util.math.Vec3d headPos =
                                    new net.minecraft.util.math.Vec3d(centerX, centerY + 1.6, centerZ);
                                com.justheare.paperjjk_client.shader.MizushiChargeEffectManager.start(headPos);
                                LOGGER.info("[Domain Visual] MIZUSHI(결없영) START → charge + ambient slash activated");
                            } else {
                                LOGGER.info("[Domain Visual] MIZUSHI(결없영) RECOVERY START → ambient slash at radius={}", maxRadius);
                            }
                        }

                        LOGGER.info("[Domain Visual] START: id={}, type={}, center=({},{},{}), maxRadius={}, isOpen={}",
                            domainId, domainType, centerX, centerY, centerZ, maxRadius, isOpen);
                    } else {
                        // Re-broadcast acts as sync
                        existing.syncFromServer(maxRadius);
                        // setDomain은 도메인이 없으면 새로 생성, 있으면 syncRadius를 호출.
                        // 타임아웃으로 AmbientKaiSlashManager에서 제거된 경우에도 복구 가능.
                        if (existing.domainType == PacketIds.DomainType.MIZUSHI && existing.isOpen) {
                            net.minecraft.util.math.Vec3d c =
                                new net.minecraft.util.math.Vec3d(centerX, centerY, centerZ);
                            com.justheare.paperjjk_client.shader.AmbientKaiSlashManager
                                .setDomain(domainId, c, maxRadius);
                        }
                        LOGGER.debug("[Domain Visual] SYNC (via START): id={}, maxRadius={}", domainId, maxRadius);
                    }
                });
            }

            case PacketIds.DomainVisualAction.SYNC -> {
                long uuidMost = buf.readLong();
                long uuidLeast = buf.readLong();
                java.util.UUID domainId = new java.util.UUID(uuidMost, uuidLeast);
                float serverRadius = buf.readFloat();

                client.execute(() -> {
                    ClientGameData.syncDomain(domainId, serverRadius);
                    // MIZUSHI 결없영(isOpen=true)만 반경 동기화
                    ClientGameData.ActiveDomain d = ClientGameData.getDomain(domainId);
                    if (d != null && d.domainType == PacketIds.DomainType.MIZUSHI && d.isOpen) {
                        com.justheare.paperjjk_client.shader.AmbientKaiSlashManager
                            .syncDomainRadius(domainId, serverRadius);
                    }
                    LOGGER.debug("[Domain Visual] SYNC: id={}, radius={}", domainId, serverRadius);
                });
            }

            case PacketIds.DomainVisualAction.END -> {
                long uuidMost = buf.readLong();
                long uuidLeast = buf.readLong();
                java.util.UUID domainId = new java.util.UUID(uuidMost, uuidLeast);

                client.execute(() -> {
                    ClientGameData.removeDomain(domainId);
                    // MIZUSHI 도메인 종료 시 1초 페이드 아웃 시작
                    com.justheare.paperjjk_client.shader.AmbientKaiSlashManager
                        .startFadeOut(domainId);
                    com.justheare.paperjjk_client.shader.MizushiChargeEffectManager.stop();
                    LOGGER.info("[Domain Visual] END: id={}", domainId);
                });
            }

            default -> LOGGER.warn("[Domain Visual] Unknown action: 0x{}", String.format("%02X", action));
        }
    }

    /**
     * CE_UPDATE (0x04) - Cursed energy update
     */
    private static void handleCEUpdate(MinecraftClient client, PacketByteBuf buf) {
        CEUpdatePacket packet = CEUpdatePacket.read(buf);

        client.execute(() -> {
            ClientGameData.setCE(packet.getCurrentCE(), packet.getMaxCE());
            ClientGameData.setRegenRate(packet.getRegenRate());
            ClientGameData.setTechnique(packet.getTechnique());
            ClientGameData.setBlocked(packet.isBlocked());

            LOGGER.debug("CE update: {}/{} (regen: {}/s, technique: {}, blocked: {})",
                packet.getCurrentCE(), packet.getMaxCE(), packet.getRegenRate(),
                packet.getTechnique(), packet.isBlocked());
        });
    }

    /**
     * TECHNIQUE_COOLDOWN (0x05) - Cooldown info
     */
    private static void handleTechniqueCooldown(MinecraftClient client, PacketByteBuf buf) {
        TechniqueCooldownPacket packet = TechniqueCooldownPacket.read(buf);

        client.execute(() -> {
            ClientGameData.setCooldown(packet.getTechniqueSlot(), packet.getCooldownTicks(), packet.getMaxCooldown());
            LOGGER.debug("Cooldown update: slot {} - {}/{} ({}%)",
                packet.getTechniqueSlot(),
                packet.getCooldownTicks(),
                packet.getMaxCooldown(),
                (int)(packet.getCooldownPercentage() * 100));
        });
    }

    /**
     * PARTICLE_EFFECT (0x06) - Custom particles
     */
    private static void handleParticleEffect(MinecraftClient client, PacketByteBuf buf) {
        byte effectType = buf.readByte();
        double x = buf.readDouble();
        double y = buf.readDouble();
        double z = buf.readDouble();
        float velocityX = buf.readFloat();
        float velocityY = buf.readFloat();
        float velocityZ = buf.readFloat();
        float scale = buf.readFloat();
        int colorRGB = buf.readInt();
        int lifetime = buf.readInt();

        client.execute(() -> {
            LOGGER.debug("Particle spawn: type={}, pos=({},{},{}), color=0x{}",
                effectType, x, y, z, String.format("%06X", colorRGB));
            // TODO: Spawn custom particles
        });
    }

    /**
     * SCREEN_EFFECT (0x07) - Screen effects
     */
    private static void handleScreenEffect(MinecraftClient client, PacketByteBuf buf) {
        byte effectType = buf.readByte();
        float intensity = buf.readFloat();
        int duration = buf.readInt();
        int dataLength = buf.readInt();
        byte[] data = new byte[dataLength];
        buf.readBytes(data);

        client.execute(() -> {
            LOGGER.info("Screen effect: type={}, intensity={}, duration={}", effectType, intensity, duration);
            // TODO: Apply shader effects
        });
    }

    /**
     * DOMAIN_SETTINGS_RESPONSE (0x16) - Domain settings response from server
     * Packet format: [packetId(1)] [normalRange(4)] [noBarrierRange(4)] [timestamp(8)]
     */
    private static void handleDomainSettingsResponse(MinecraftClient client, PacketByteBuf buf) {
        int normalRange = buf.readInt();
        int noBarrierRange = buf.readInt();
        long timestamp = buf.readLong();

        client.execute(() -> {
            LOGGER.info("Domain settings response: normal={}, noBarrier={}", normalRange, noBarrierRange);

            // Update the current screen if it's the domain settings screen
            if (client.currentScreen instanceof com.justheare.paperjjk_client.screen.DomainSettingsScreen settingsScreen) {
                settingsScreen.setDomainRanges(normalRange, noBarrierRange);
                LOGGER.info("Updated domain settings screen with server values");
            }
        });
    }

    /**
     * INFINITY_AO (0x17) - Infinity Ao refraction effect sync
     * Handles START/SYNC/END actions for server-side Infinity Ao
     */
    private static void handleInfinityAo(MinecraftClient client, PacketByteBuf buf) {
        byte action = buf.readByte();

        switch (action) {
            case PacketIds.InfinityAoAction.START -> {
                double x = buf.readDouble();
                double y = buf.readDouble();
                double z = buf.readDouble();
                float strength = buf.readFloat();
                String uniqueId = readUTF(buf);

                client.execute(() -> {
                    net.minecraft.util.math.Vec3d position = new net.minecraft.util.math.Vec3d(x, y, z);
                    com.justheare.paperjjk_client.shader.RefractionEffectManager.addEffect(
                        position,
                        0.3f,  // Fixed radius
                        strength,
                        "AO",  // Effect type
                        uniqueId
                    );
                    LOGGER.info("[Infinity Ao] START: pos=({},{},{}), strength={}, id={}",
                        x, y, z, strength, uniqueId);
                });
            }

            case PacketIds.InfinityAoAction.SYNC -> {
                double x = buf.readDouble();
                double y = buf.readDouble();
                double z = buf.readDouble();
                float strength = buf.readFloat();
                String uniqueId = readUTF(buf);

                client.execute(() -> {
                    net.minecraft.util.math.Vec3d position = new net.minecraft.util.math.Vec3d(x, y, z);
                    com.justheare.paperjjk_client.shader.RefractionEffectManager.updateEffect(
                        position,
                        0.3f,  // Fixed radius
                        strength,
                        "AO",  // Effect type
                        uniqueId
                    );
                    LOGGER.debug("[Infinity Ao] SYNC: pos=({},{},{}), strength={}, id={}",
                        x, y, z, strength, uniqueId);
                });
            }

            case PacketIds.InfinityAoAction.END -> {
                String uniqueId = readUTF(buf);

                client.execute(() -> {
                    com.justheare.paperjjk_client.shader.RefractionEffectManager.removeEffectById(uniqueId);
                    LOGGER.info("[Infinity Ao] END: id={}", uniqueId);
                });
            }

            default -> LOGGER.warn("[Infinity Ao] Unknown action: 0x{}", String.format("%02X", action));
        }
    }

    /**
     * INFINITY_AKA (0x18) - Infinity Aka red expansion effect sync
     * Handles START/SYNC/END actions for server-side Infinity Aka
     * Uses NEGATIVE strength for expansion effect
     */
    private static void handleInfinityAka(MinecraftClient client, PacketByteBuf buf) {
        byte action = buf.readByte();

        switch (action) {
            case PacketIds.InfinityAkaAction.START -> {
                double x = buf.readDouble();
                double y = buf.readDouble();
                double z = buf.readDouble();
                float strength = buf.readFloat();
                // Read UTF string (Java DataOutput format, not Minecraft format)
                String uniqueId = readUTF(buf);

                LOGGER.info("[DEBUG PACKET READ] AKA START - Read uniqueId from buffer: '{}', length: {}",
                    uniqueId, uniqueId == null ? "null" : uniqueId.length());

                client.execute(() -> {
                    net.minecraft.util.math.Vec3d position = new net.minecraft.util.math.Vec3d(x, y, z);
                    // Use NEGATIVE strength for expansion (repulsion) effect
                    com.justheare.paperjjk_client.shader.RefractionEffectManager.addEffect(
                        position,
                        0.3f,  // Fixed radius
                        -strength,  // NEGATIVE for expansion
                        "AKA",      // Effect type
                        uniqueId
                    );
                    LOGGER.info("[Infinity Aka] START: pos=({},{},{}), strength={} (expansion), id={}",
                        x, y, z, -strength, uniqueId);
                });
            }

            case PacketIds.InfinityAkaAction.SYNC -> {
                double x = buf.readDouble();
                double y = buf.readDouble();
                double z = buf.readDouble();
                float strength = buf.readFloat();
                String uniqueId = readUTF(buf);

                client.execute(() -> {
                    net.minecraft.util.math.Vec3d position = new net.minecraft.util.math.Vec3d(x, y, z);
                    // Use NEGATIVE strength for expansion (repulsion) effect
                    com.justheare.paperjjk_client.shader.RefractionEffectManager.updateEffect(
                        position,
                        0.3f,  // Fixed radius
                        -strength,  // NEGATIVE for expansion
                        "AKA",      // Effect type
                        uniqueId
                    );
                    LOGGER.debug("[Infinity Aka] SYNC: pos=({},{},{}), strength={} (expansion), id={}",
                        x, y, z, -strength, uniqueId);
                });
            }

            case PacketIds.InfinityAkaAction.END -> {
                String uniqueId = readUTF(buf);

                client.execute(() -> {
                    com.justheare.paperjjk_client.shader.RefractionEffectManager.removeEffectById(uniqueId);
                    LOGGER.info("[Infinity Aka] END: id={}", uniqueId);
                });
            }

            default -> LOGGER.warn("[Infinity Aka] Unknown action: 0x{}", String.format("%02X", action));
        }
    }

    /**
     * INFINITY_MURASAKI (0x19) - Infinity Murasaki purple expansion effect sync
     * Handles normal moving murasaki and unlimit_m explosion
     */
    private static void handleInfinityMurasaki(MinecraftClient client, PacketByteBuf buf) {
        byte action = buf.readByte();

        switch (action) {
            case PacketIds.InfinityMurasakiAction.START -> {
                // Normal murasaki - moving purple expansion (like aka)
                double x = buf.readDouble();
                double y = buf.readDouble();
                double z = buf.readDouble();
                float strength = buf.readFloat();
                String uniqueId = readUTF(buf);

                client.execute(() -> {
                    // Remove existing AKA effect with same ID (in case of collision)
                    com.justheare.paperjjk_client.shader.RefractionEffectManager.removeEffectById(uniqueId);

                    net.minecraft.util.math.Vec3d position = new net.minecraft.util.math.Vec3d(x, y, z);
                    com.justheare.paperjjk_client.shader.RefractionEffectManager.addEffect(
                        position,
                        0.3f,  // Fixed radius
                        -strength,  // NEGATIVE for expansion (like aka)
                        "MURASAKI",  // Effect type
                        uniqueId
                    );
                    LOGGER.info("[Infinity Murasaki] START (normal): pos=({},{},{}), strength={} (expansion), id={}",
                        x, y, z, -strength, uniqueId);
                });
            }

            case PacketIds.InfinityMurasakiAction.SYNC -> {
                // Normal murasaki - sync position (moving type)
                double x = buf.readDouble();
                double y = buf.readDouble();
                double z = buf.readDouble();
                float strength = buf.readFloat();
                String uniqueId = readUTF(buf);

                client.execute(() -> {
                    net.minecraft.util.math.Vec3d position = new net.minecraft.util.math.Vec3d(x, y, z);
                    com.justheare.paperjjk_client.shader.RefractionEffectManager.updateEffect(
                        position,
                        0.3f,  // Fixed radius
                        -strength,  // NEGATIVE for expansion
                        "MURASAKI",
                        uniqueId
                    );
                    LOGGER.debug("[Infinity Murasaki] SYNC (normal): pos=({},{},{}), strength={} (expansion), id={}",
                        x, y, z, -strength, uniqueId);
                });
            }

            case PacketIds.InfinityMurasakiAction.START_EXPLODE -> {
                // Unlimit_m - start explosion at fixed position
                double x = buf.readDouble();
                double y = buf.readDouble();
                double z = buf.readDouble();
                float initialRadius = buf.readFloat();
                String uniqueId = readUTF(buf);

                client.execute(() -> {
                    // CRITICAL FIX: Remove existing AKA effect with same ID
                    // When AKA collides with AO to become MURASAKI, the ID stays the same (INFINITY_AKA_...)
                    // This causes red AKA sphere to overlap and hide purple MURASAKI sphere
                    com.justheare.paperjjk_client.shader.RefractionEffectManager.removeEffectById(uniqueId);
                    LOGGER.info("[Infinity Murasaki] Removed existing AKA effect with id={}", uniqueId);

                    net.minecraft.util.math.Vec3d position = new net.minecraft.util.math.Vec3d(x, y, z);
                    // Use radius as both radius and strength for explosion
                    com.justheare.paperjjk_client.shader.RefractionEffectManager.addEffect(
                        position,
                        initialRadius,  // Expanding radius
                        -1.0f,  // NEGATIVE for expansion, fixed strength
                        "MURASAKI_EXPLODE",
                        uniqueId
                    );
                    LOGGER.info("[Infinity Murasaki] START_EXPLODE: pos=({},{},{}), radius={}, id={}",
                        x, y, z, initialRadius, uniqueId);
                });
            }

            case PacketIds.InfinityMurasakiAction.SYNC_RADIUS -> {
                // Unlimit_m - update expanding radius
                float radius = buf.readFloat();
                String uniqueId = readUTF(buf);

                client.execute(() -> {
                    // Find MURASAKI_EXPLODE effect by ID and update its radius
                    var effect = com.justheare.paperjjk_client.shader.RefractionEffectManager.getEffectById(uniqueId);
                    if (effect != null && "MURASAKI_EXPLODE".equals(effect.effectType)) {
                        effect.radius = radius;
                        LOGGER.debug("[Infinity Murasaki] SYNC_RADIUS: radius={}, id={}", radius, uniqueId);
                    }
                });
            }

            case PacketIds.InfinityMurasakiAction.END -> {
                String uniqueId = readUTF(buf);

                client.execute(() -> {
                    com.justheare.paperjjk_client.shader.RefractionEffectManager.removeEffectById(uniqueId);
                    LOGGER.info("[Infinity Murasaki] END: id={}", uniqueId);
                });
            }

            default -> LOGGER.warn("[Infinity Murasaki] Unknown action: 0x{}", String.format("%02X", action));
        }
    }

    /**
     * HANDSHAKE (0x08) - Handshake
     */
    private static void handleHandshake(MinecraftClient client, PacketByteBuf buf) {
        int version = buf.readInt();
        String modVersion = buf.readString();
        int features = buf.readInt();

        client.execute(() -> {
            LOGGER.info("Server handshake received: protocol={}, version={}, features=0x{}",
                version, modVersion, String.format("%02X", features));
            // TODO: Version compatibility check
        });
    }

    /**
     * PLAYER_INFO_RESPONSE (0x1A) - Player info response
     * Packet format: [packetId(1)] [naturaltech(UTF)] [curseenergy(int)] [maxCE(int)]
     *                [hasRCT(boolean)] [domainLevel(int)]
     *                [slot1(UTF)] [slot2(UTF)] [slot3(UTF)] [slot4(UTF)]
     */
    private static void handlePlayerInfoResponse(MinecraftClient client, PacketByteBuf buf) {
        String naturaltech = readUTF(buf);
        int curseEnergy = buf.readInt();
        int maxCurseEnergy = buf.readInt();
        boolean hasRCT = buf.readBoolean();
        int domainLevel = buf.readInt();
        int efficiencyLevel = buf.readInt();
        boolean canGraspAirSurface = buf.readBoolean();
        String slot1 = readUTF(buf);
        String slot2 = readUTF(buf);
        String slot3 = readUTF(buf);
        String slot4 = readUTF(buf);

        // Read available skills
        int skillCount = buf.readInt();
        java.util.List<String> availableSkills = new java.util.ArrayList<>();
        for (int i = 0; i < skillCount; i++) {
            availableSkills.add(readUTF(buf));
        }

        client.execute(() -> {
            com.justheare.paperjjk_client.data.PlayerData.updatePlayerInfo(
                naturaltech, curseEnergy, maxCurseEnergy, hasRCT, domainLevel,
                slot1, slot2, slot3, slot4, efficiencyLevel, canGraspAirSurface
            );

            // Update available skills
            com.justheare.paperjjk_client.data.PlayerData.setAvailableSkills(availableSkills);

            LOGGER.info("[Player Info] Updated: tech={}, CE={}/{}, RCT={}, domain={}, skills={}",
                naturaltech, curseEnergy, maxCurseEnergy, hasRCT, domainLevel, availableSkills);

            // Update current screen if it's the player info screen
            if (client.currentScreen instanceof com.justheare.paperjjk_client.screen.PlayerInfoScreen playerInfoScreen) {
                playerInfoScreen.refresh();
            }
        });
    }

    /**
     * SKILL_INFO_RESPONSE (0x1B) - Skill description response
     * Packet format: [packetId(1)] [skillId(UTF)] [displayName(UTF)] [description(UTF)] [requiredCE(UTF)]
     */
    private static void handleSkillInfoResponse(MinecraftClient client, PacketByteBuf buf) {
        String skillId = readUTF(buf);
        String displayName = readUTF(buf);
        String description = readUTF(buf);
        String requiredCE = readUTF(buf);

        client.execute(() -> {
            com.justheare.paperjjk_client.data.PlayerData.SkillInfo skillInfo =
                new com.justheare.paperjjk_client.data.PlayerData.SkillInfo(
                    skillId, displayName, description, requiredCE
                );

            com.justheare.paperjjk_client.data.PlayerData.updateSkillInfo(skillInfo);

            LOGGER.info("[Skill Info] Cached: {} ({})", skillId, displayName);

            // Update current screen if it's waiting for this skill info
            if (client.currentScreen instanceof com.justheare.paperjjk_client.screen.SkillDetailScreen detailScreen) {
                if (detailScreen.getSkillId().equals(skillId)) {
                    detailScreen.refresh();
                }
            }
        });
    }

    /**
     * SIMPLE_DOMAIN_ACTIVATE (0x21) - Domain activated (fresh start)
     * Format: [locX(8)][locY(8)][locZ(8)][power(8)][expansionDelay(4)][maxPower(4)]
     *         [chargeRate(8)][baseDecayRate(8)][maxRadius(8)][casterUUIDMost(8)][casterUUIDLeast(8)]
     */
    private static void handleSimpleDomainActivate(MinecraftClient client, PacketByteBuf buf) {
        double locX           = buf.readDouble();
        double locY           = buf.readDouble();
        double locZ           = buf.readDouble();
        double power          = buf.readDouble();
        int    expansionDelay = buf.readInt();
        int    maxPower       = buf.readInt();
        double chargeRate     = buf.readDouble();
        double baseDecayRate  = buf.readDouble();
        double maxRadius      = buf.readDouble();
        long   uuidMost       = buf.readLong();
        long   uuidLeast      = buf.readLong();
        java.util.UUID casterUuid = new java.util.UUID(uuidMost, uuidLeast);

        client.execute(() -> {
            boolean isLocalCaster = client.player != null && casterUuid.equals(client.player.getUuid());
            net.minecraft.util.math.Vec3d feetPos = new net.minecraft.util.math.Vec3d(locX, locY, locZ);
            com.justheare.paperjjk_client.shader.DomainEffectManager.onActivate(
                casterUuid, feetPos, power, expansionDelay, maxPower, chargeRate, baseDecayRate, maxRadius, isLocalCaster);
            LOGGER.info("[Simple Domain] ACTIVATE: pos=({},{},{}), power={}, expansionDelay={}, maxPower={}, chargeRate={}, decayRate={}, maxRadius={}, caster={}, local={}",
                String.format("%.2f", locX), String.format("%.2f", locY), String.format("%.2f", locZ),
                String.format("%.1f", power), expansionDelay, maxPower,
                String.format("%.1f", chargeRate), String.format("%.2f", baseDecayRate), String.format("%.1f", maxRadius),
                casterUuid, isLocalCaster);
        });
    }

    /**
     * SIMPLE_DOMAIN_CHARGING_END (0x22) - Charging stopped, power preserved
     * Format: [power(8)][locX(8)][locY(8)][locZ(8)][casterUUIDMost(8)][casterUUIDLeast(8)]
     */
    private static void handleSimpleDomainChargingEnd(MinecraftClient client, PacketByteBuf buf) {
        double power     = buf.readDouble();
        double locX      = buf.readDouble();
        double locY      = buf.readDouble();
        double locZ      = buf.readDouble();
        long   uuidMost  = buf.readLong();
        long   uuidLeast = buf.readLong();
        java.util.UUID casterUuid = new java.util.UUID(uuidMost, uuidLeast);

        client.execute(() -> {
            com.justheare.paperjjk_client.shader.DomainEffectManager.onChargingEnd(casterUuid, power, locX, locY, locZ);
            LOGGER.info("[Simple Domain] CHARGING_END: power={}, loc=({},{},{}), caster={}",
                String.format("%.1f", power),
                String.format("%.2f", locX), String.format("%.2f", locY), String.format("%.2f", locZ), casterUuid);
        });
    }

    /**
     * SIMPLE_DOMAIN_POWER_SYNC (0x23) - Authoritative power correction
     * Format: [power(8)][spawnParticles(1)][casterUUIDMost(8)][casterUUIDLeast(8)]
     */
    private static void handleSimpleDomainPowerSync(MinecraftClient client, PacketByteBuf buf) {
        double  power          = buf.readDouble();
        boolean spawnParticles = buf.readBoolean();
        long    uuidMost       = buf.readLong();
        long    uuidLeast      = buf.readLong();
        java.util.UUID casterUuid = new java.util.UUID(uuidMost, uuidLeast);

        client.execute(() -> {
            // Capture old power before sync to compute delta
            DomainEffectManager.DomainState state = DomainEffectManager.getDomain(casterUuid);
            double oldPower = (state != null) ? state.currentPower : power;

            DomainEffectManager.onPowerSync(casterUuid, power);
            LOGGER.debug("[Simple Domain] POWER_SYNC: power={}, particles={}, caster={}",
                String.format("%.1f", power), spawnParticles, casterUuid);

            // Spawn crumble particles only when flagged (not for out-of-range penalty)
            if (spawnParticles) {
                double delta = Math.max(oldPower - power,0.2);
                delta = 2 * Math.PI * oldPower * delta * 0.2;
                if (delta > 0 && state != null && client.world != null) {
                    spawnCrumbleParticles(client, state, delta);
                }
            }
        });
    }

    /**
     * Spawns crumble/shard particles at the white circle edge.
     * Called when power decreases (delta > 0).
     * Particle count scales with how fast power dropped.
     */
    private static void spawnCrumbleParticles(MinecraftClient client,
                                              DomainEffectManager.DomainState state,
                                              double delta) {
        float radius = state.getExpandRadius();
        if (radius < 0.3f) return; // White circle not visible yet

        net.minecraft.util.math.Vec3d center = state.casterFeetPos;
        net.minecraft.util.math.random.Random rng =
            net.minecraft.util.math.random.Random.create();

        // 1 particle per ~2 power lost, capped at 16
        int count = Math.max(1, Math.min(70, (int)(delta * 0.1)));

        int baseCenterY = (int) Math.floor(center.y);

        for (int i = 0; i < count; i++) {
            // Random angle around the ring
            double angle = rng.nextDouble() * Math.PI * 2.0;
            // Tiny radial jitter so particles aren't all exactly on the edge
            double r = radius + (rng.nextDouble() - 0.5) * 0.4;

            double px = center.x + r * Math.cos(angle);
            double pz = center.z + r * Math.sin(angle);

            // Find passable Y near caster floor level (search: 0, -1, -2, -3, +1, +2, +3)
            double py = findPassableY(client.world, (int) Math.floor(px), baseCenterY, (int) Math.floor(pz))
                        + rng.nextDouble() * 0.3;

            // Outward burst + upward drift
            double outward = 0.02 + rng.nextDouble() * 0.06;
            double vx = Math.cos(angle) * outward;
            double vz = Math.sin(angle) * outward;
            double vy = 0.04 + rng.nextDouble() * 0.05;

            client.particleManager.addParticle(
                ModParticles.DOMAIN_FRAGMENT,
                px, py, pz,
                vx, vy, vz
            );
        }
    }

    /**
     * Finds the nearest passable (no-collision) Y position starting from baseY.
     * Search order: 0, -1, -2, -3, +1, +2, +3
     * Returns the Y of the first passable block found, or baseY as fallback.
     */
    private static double findPassableY(net.minecraft.client.world.ClientWorld world,
                                        int bx, int baseY, int bz) {
        int[] offsets = {0, -1, -2, -3, 1, 2, 3};
        for (int offset : offsets) {
            net.minecraft.util.math.BlockPos pos =
                new net.minecraft.util.math.BlockPos(bx, baseY + offset, bz);
            net.minecraft.util.math.BlockPos below =
                new net.minecraft.util.math.BlockPos(bx, baseY + offset - 1, bz);
            boolean passable = world.getBlockState(pos).getCollisionShape(world, pos).isEmpty();
            boolean solidBelow = !world.getBlockState(below).getCollisionShape(world, below).isEmpty();
            if (passable && solidBelow) {
                return baseY + offset;
            }
        }
        return baseY;
    }

    /**
     * SIMPLE_DOMAIN_DEACTIVATE (0x24) - Domain deactivated (power reached 0)
     * Format: [casterUUIDMost(8)][casterUUIDLeast(8)]
     */
    private static void handleSimpleDomainDeactivate(MinecraftClient client, PacketByteBuf buf) {
        long   uuidMost  = buf.readLong();
        long   uuidLeast = buf.readLong();
        java.util.UUID casterUuid = new java.util.UUID(uuidMost, uuidLeast);

        client.execute(() -> {
            com.justheare.paperjjk_client.shader.DomainEffectManager.onDeactivate(casterUuid);
            LOGGER.info("[Simple Domain] DEACTIVATE: caster={}", casterUuid);
        });
    }

    /**
     * SIMPLE_DOMAIN_TRANSLATE (0x25) - Domain location changed (translated)
     * Format: [locX(8)][locY(8)][locZ(8)][casterUUIDMost(8)][casterUUIDLeast(8)]
     */
    private static void handleSimpleDomainTranslate(MinecraftClient client, PacketByteBuf buf) {
        double locX      = buf.readDouble();
        double locY      = buf.readDouble();
        double locZ      = buf.readDouble();
        long   uuidMost  = buf.readLong();
        long   uuidLeast = buf.readLong();
        java.util.UUID casterUuid = new java.util.UUID(uuidMost, uuidLeast);

        client.execute(() -> {
            DomainEffectManager.onTranslate(casterUuid, locX, locY, locZ);
            LOGGER.info("[Simple Domain] TRANSLATE: loc=({},{},{}), caster={}",
                String.format("%.2f", locX), String.format("%.2f", locY), String.format("%.2f", locZ), casterUuid);
        });
    }

    /**
     * SLOT_GAUGE_UPDATE (0x30) - 슬롯 X/C/V/B 의 상태·게이지·자물쇠·레이블
     * Format: 4회 반복 — [state(1)][gauge(1, 0~100)][locked(bool)][label(UTF)]
     */
    private static void handleSlotGaugeUpdate(MinecraftClient client, PacketByteBuf buf) {
        byte[]    states = new byte[4];
        float[]   gauges = new float[4];
        boolean[] locked = new boolean[4];
        String[]  labels = new String[4];
        for (int i = 0; i < 4; i++) {
            states[i] = buf.readByte();
            gauges[i] = (buf.readUnsignedByte()) / 100f;
            locked[i] = buf.readBoolean();
            labels[i] = readUTF(buf);
        }
        client.execute(() -> {
            for (int i = 0; i < 4; i++) {
                byte slot = (byte)(i + 1);
                ClientGameData.setSlotState(slot, states[i]);
                ClientGameData.setSlotGauge(slot, gauges[i]);
                ClientGameData.setSlotLocked(slot, locked[i]);
                ClientGameData.setSlotLabel(slot, labels[i]);
            }
        });
    }

    /**
     * BODY_REIN_UPDATE (0x31) - 신체강화 비율
     * Format: [ratio(1, 0~100)]
     */
    private static void handleBodyReinUpdate(MinecraftClient client, PacketByteBuf buf) {
        float ratio = (buf.readUnsignedByte()) / 100f;
        client.execute(() -> ClientGameData.setBodyReinforcement(ratio));
    }

    /**
     * HACHI_SLASH (0x33) — 팔(Hachi) 격자 참격 화면 효과
     * Format: [hitX(4)][hitY(4)][hitZ(4)]
     */
    private static void handleHachiSlash(MinecraftClient client, PacketByteBuf buf) {
        float hitX = buf.readFloat();
        float hitY = buf.readFloat();
        float hitZ = buf.readFloat();

        client.execute(() -> {
            com.justheare.paperjjk_client.shader.HachiSlashEffectManager.addEffect(hitX, hitY, hitZ);
            LOGGER.debug("[Hachi Slash] effect at ({},{},{})", hitX, hitY, hitZ);
        });
    }

    /**
     * KAI_SLASH (0x32) — 참격(해) 화면 post-processing 효과
     * Format: [hitX(4)][hitY(4)][hitZ(4)][axisX(4)][axisY(4)][axisZ(4)]
     */
    private static void handleKaiSlash(MinecraftClient client, PacketByteBuf buf) {
        float hitX  = buf.readFloat();
        float hitY  = buf.readFloat();
        float hitZ  = buf.readFloat();
        float axisX = buf.readFloat();
        float axisY = buf.readFloat();
        float axisZ = buf.readFloat();

        client.execute(() -> {
            com.justheare.paperjjk_client.shader.KaiSlashEffectManager.addEffect(
                hitX, hitY, hitZ, axisX, axisY, axisZ);
            LOGGER.debug("[Kai Slash] effect at ({},{},{}), axis=({},{},{})",
                hitX, hitY, hitZ, axisX, axisY, axisZ);
        });
    }

    /**
     * Read UTF string in Java DataOutput format (used by Bukkit's ByteArrayDataOutput)
     * Format: 2-byte length + Modified UTF-8 bytes
     */
    private static String readUTF(PacketByteBuf buf) {
        try {
            int length = buf.readUnsignedShort();
            byte[] bytes = new byte[length];
            buf.readBytes(bytes);
            return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            LOGGER.error("Failed to read UTF string", e);
            return "";
        }
    }

    /**
     * 1.21.10 CustomPayload 래퍼
     */
    public record JJKPayload(byte[] data) implements CustomPayload {
        public static final CustomPayload.Id<JJKPayload> ID = new CustomPayload.Id<>(CHANNEL);
        public static final PacketCodec<PacketByteBuf, JJKPayload> CODEC = PacketCodec.of(
            (value, buf) -> buf.writeBytes(value.data),
            buf -> {
                byte[] data = new byte[buf.readableBytes()];
                buf.readBytes(data);
                return new JJKPayload(data);
            }
        );

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
}
