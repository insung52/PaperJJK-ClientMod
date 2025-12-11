package com.justheare.paperjjk_client.network;

import com.justheare.paperjjk_client.data.ClientGameData;
import com.justheare.paperjjk_client.network.packets.*;
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
                        case PacketIds.HANDSHAKE -> handleHandshake(context.client(), buf);
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
                int domainType = buf.readInt();
                double centerX = buf.readDouble();
                double centerY = buf.readDouble();
                double centerZ = buf.readDouble();
                int maxRadius = buf.readInt();
                int colorRGB = buf.readInt();
                float expansionSpeed = buf.readFloat();
                long uuidMost = buf.readLong();
                long uuidLeast = buf.readLong();
                java.util.UUID domainId = new java.util.UUID(uuidMost, uuidLeast);

                client.execute(() -> {
                    ClientGameData.ActiveDomain domain = new ClientGameData.ActiveDomain();
                    domain.domainId = domainId;
                    domain.center = new net.minecraft.util.math.Vec3d(centerX, centerY, centerZ);
                    domain.currentRadius = 0.0f;
                    domain.maxRadius = maxRadius;
                    domain.expansionSpeed = expansionSpeed;  // Initial speed from server
                    domain.color = colorRGB;
                    domain.domainType = domainType;
                    domain.lastSyncTime = System.currentTimeMillis();
                    domain.serverRadius = 0.0f;  // Server starts at 0
                    domain.isExpanding = true;

                    ClientGameData.addDomain(domainId, domain);
                    LOGGER.info("[Domain Visual] START: id={}, center=({},{},{}), maxRadius={}, speed={}/s",
                        domainId, centerX, centerY, centerZ, maxRadius, expansionSpeed);
                });
            }

            case PacketIds.DomainVisualAction.SYNC -> {
                long uuidMost = buf.readLong();
                long uuidLeast = buf.readLong();
                java.util.UUID domainId = new java.util.UUID(uuidMost, uuidLeast);
                float serverRadius = buf.readFloat();

                client.execute(() -> {
                    ClientGameData.syncDomain(domainId, serverRadius);
                    LOGGER.debug("[Domain Visual] SYNC: id={}, radius={}", domainId, serverRadius);
                });
            }

            case PacketIds.DomainVisualAction.END -> {
                long uuidMost = buf.readLong();
                long uuidLeast = buf.readLong();
                java.util.UUID domainId = new java.util.UUID(uuidMost, uuidLeast);

                client.execute(() -> {
                    ClientGameData.removeDomain(domainId);
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

                client.execute(() -> {
                    net.minecraft.util.math.Vec3d position = new net.minecraft.util.math.Vec3d(x, y, z);
                    com.justheare.paperjjk_client.shader.RefractionEffectManager.addEffect(
                        position,
                        0.3f,  // Fixed radius
                        strength,
                        "AO"   // Effect type
                    );
                    LOGGER.info("[Infinity Ao] START: pos=({},{},{}), strength={}",
                        x, y, z, strength);
                });
            }

            case PacketIds.InfinityAoAction.SYNC -> {
                double x = buf.readDouble();
                double y = buf.readDouble();
                double z = buf.readDouble();
                float strength = buf.readFloat();

                client.execute(() -> {
                    net.minecraft.util.math.Vec3d position = new net.minecraft.util.math.Vec3d(x, y, z);
                    com.justheare.paperjjk_client.shader.RefractionEffectManager.updateEffect(
                        position,
                        0.3f,  // Fixed radius
                        strength,
                        "AO"   // Effect type
                    );
                    LOGGER.debug("[Infinity Ao] SYNC: pos=({},{},{}), strength={}",
                        x, y, z, strength);
                });
            }

            case PacketIds.InfinityAoAction.END -> {
                client.execute(() -> {
                    com.justheare.paperjjk_client.shader.RefractionEffectManager.clearEffectsByType("AO");
                    LOGGER.info("[Infinity Ao] END");
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

                client.execute(() -> {
                    net.minecraft.util.math.Vec3d position = new net.minecraft.util.math.Vec3d(x, y, z);
                    // Use NEGATIVE strength for expansion (repulsion) effect
                    com.justheare.paperjjk_client.shader.RefractionEffectManager.addEffect(
                        position,
                        0.3f,  // Fixed radius
                        -strength,  // NEGATIVE for expansion
                        "AKA"       // Effect type
                    );
                    LOGGER.info("[Infinity Aka] START: pos=({},{},{}), strength={} (expansion)",
                        x, y, z, -strength);
                });
            }

            case PacketIds.InfinityAkaAction.SYNC -> {
                double x = buf.readDouble();
                double y = buf.readDouble();
                double z = buf.readDouble();
                float strength = buf.readFloat();

                client.execute(() -> {
                    net.minecraft.util.math.Vec3d position = new net.minecraft.util.math.Vec3d(x, y, z);
                    // Use NEGATIVE strength for expansion (repulsion) effect
                    com.justheare.paperjjk_client.shader.RefractionEffectManager.updateEffect(
                        position,
                        0.3f,  // Fixed radius
                        -strength,  // NEGATIVE for expansion
                        "AKA"       // Effect type
                    );
                    LOGGER.debug("[Infinity Aka] SYNC: pos=({},{},{}), strength={} (expansion)",
                        x, y, z, -strength);
                });
            }

            case PacketIds.InfinityAkaAction.END -> {
                client.execute(() -> {
                    com.justheare.paperjjk_client.shader.RefractionEffectManager.clearEffectsByType("AKA");
                    LOGGER.info("[Infinity Aka] END");
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

                client.execute(() -> {
                    net.minecraft.util.math.Vec3d position = new net.minecraft.util.math.Vec3d(x, y, z);
                    com.justheare.paperjjk_client.shader.RefractionEffectManager.addEffect(
                        position,
                        0.3f,  // Fixed radius
                        -strength,  // NEGATIVE for expansion (like aka)
                        "MURASAKI"  // Effect type
                    );
                    LOGGER.info("[Infinity Murasaki] START (normal): pos=({},{},{}), strength={} (expansion)",
                        x, y, z, -strength);
                });
            }

            case PacketIds.InfinityMurasakiAction.SYNC -> {
                // Normal murasaki - sync position (moving type)
                double x = buf.readDouble();
                double y = buf.readDouble();
                double z = buf.readDouble();
                float strength = buf.readFloat();

                client.execute(() -> {
                    net.minecraft.util.math.Vec3d position = new net.minecraft.util.math.Vec3d(x, y, z);
                    com.justheare.paperjjk_client.shader.RefractionEffectManager.updateEffect(
                        position,
                        0.3f,  // Fixed radius
                        -strength,  // NEGATIVE for expansion
                        "MURASAKI"
                    );
                    LOGGER.debug("[Infinity Murasaki] SYNC (normal): pos=({},{},{}), strength={} (expansion)",
                        x, y, z, -strength);
                });
            }

            case PacketIds.InfinityMurasakiAction.START_EXPLODE -> {
                // Unlimit_m - start explosion at fixed position
                double x = buf.readDouble();
                double y = buf.readDouble();
                double z = buf.readDouble();
                float initialRadius = buf.readFloat();

                client.execute(() -> {
                    net.minecraft.util.math.Vec3d position = new net.minecraft.util.math.Vec3d(x, y, z);
                    // Use radius as both radius and strength for explosion
                    com.justheare.paperjjk_client.shader.RefractionEffectManager.addEffect(
                        position,
                        initialRadius,  // Expanding radius
                        -1.0f,  // NEGATIVE for expansion, fixed strength
                        "MURASAKI_EXPLODE"
                    );
                    LOGGER.info("[Infinity Murasaki] START_EXPLODE: pos=({},{},{}), radius={}",
                        x, y, z, initialRadius);
                });
            }

            case PacketIds.InfinityMurasakiAction.SYNC_RADIUS -> {
                // Unlimit_m - update expanding radius
                float radius = buf.readFloat();

                client.execute(() -> {
                    // Find MURASAKI_EXPLODE effect and update its radius
                    var effects = com.justheare.paperjjk_client.shader.RefractionEffectManager.getEffects();
                    for (var effect : effects) {
                        if ("MURASAKI_EXPLODE".equals(effect.effectType)) {
                            effect.radius = radius;
                            LOGGER.debug("[Infinity Murasaki] SYNC_RADIUS: radius={}", radius);
                            break;
                        }
                    }
                });
            }

            case PacketIds.InfinityMurasakiAction.END -> {
                client.execute(() -> {
                    // Clear both types of murasaki effects
                    com.justheare.paperjjk_client.shader.RefractionEffectManager.clearEffectsByType("MURASAKI");
                    com.justheare.paperjjk_client.shader.RefractionEffectManager.clearEffectsByType("MURASAKI_EXPLODE");
                    LOGGER.info("[Infinity Murasaki] END");
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
