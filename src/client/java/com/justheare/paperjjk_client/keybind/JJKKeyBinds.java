package com.justheare.paperjjk_client.keybind;

import com.justheare.paperjjk_client.network.PacketIds;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.network.packet.c2s.common.CustomPayloadC2SPacket;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWScrollCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.netty.buffer.Unpooled;

/**
 * JJK Keybind registration and skill-based input processing
 * Based on input.md specifications
 */
public class JJKKeyBinds {
    private static final Logger LOGGER = LoggerFactory.getLogger("PaperJJK-Keybinds");
    private static final Identifier CHANNEL = Identifier.of("paperjjk", "main");

    // Keybind definitions
    private static KeyBinding domainExpansionKey; // R - Domain Expansion
    private static KeyBinding barrierKey;         // G - Barrier Technique
    private static KeyBinding rctKey;             // Z - Reverse Cursed Technique (RCT)
    private static KeyBinding slot1Key;           // X - Technique Slot 1
    private static KeyBinding slot2Key;           // C - Technique Slot 2
    private static KeyBinding slot3Key;           // V - Technique Slot 3
    private static KeyBinding slot4Key;           // B - Technique Slot 4
    private static KeyBinding techniqueControlKey; // T - Technique Control

    // Key state tracking
    private static boolean rctPressed = false;
    private static boolean barrierPressed = false;
    private static boolean slot1Pressed = false;
    private static boolean slot2Pressed = false;
    private static boolean slot3Pressed = false;
    private static boolean slot4Pressed = false;
    private static boolean shiftPressed = false;
    private static boolean techniqueControlPressed = false;

    // R key state tracking
    private static boolean rKeyPressed = false;

    /**
     * Register keybinds
     */
    public static void register() {
        KeyBinding.Category category = KeyBinding.Category.create(Identifier.of("paperjjk", "keybinds"));

        domainExpansionKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.paperjjk.domain_expansion",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            category
        ));

        barrierKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.paperjjk.barrier",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            category
        ));

        rctKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.paperjjk.rct",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_Z,
            category
        ));

        slot1Key = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.paperjjk.slot1",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_X,
            category
        ));

        slot2Key = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.paperjjk.slot2",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_C,
            category
        ));

        slot3Key = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.paperjjk.slot3",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            category
        ));

        slot4Key = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.paperjjk.slot4",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_B,
            category
        ));

        techniqueControlKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.paperjjk.technique_control",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_T,
            category
        ));

        // Register tick event for input processing
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player != null && client.getNetworkHandler() != null) {
                processInputs(client);

                // Register scroll callback on first tick (when window is ready)
                if (!scrollCallbackRegistered) {
                    registerScrollCallback();
                }
            }
        });

        LOGGER.info("JJK Keybinds registered successfully");
    }

    private static boolean scrollCallbackRegistered = false;

    /**
     * Register mouse scroll callback for distance adjustment
     */
    private static GLFWScrollCallback previousScrollCallback = null;

    private static void registerScrollCallback() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getWindow() == null) {
            return; // Window not ready yet
        }

        long windowHandle = client.getWindow().getHandle();
        scrollCallbackRegistered = true;

        // Save previous callback (vanilla's callback) and set new one
        previousScrollCallback = GLFW.glfwSetScrollCallback(windowHandle, (window, xOffset, yOffset) -> {
            // Only intercept scroll when charging a technique
            if (isSkillActive()) {
                // Send scroll delta to server
                if (yOffset != 0) {
                    byte activeSlot = getActiveSlot();
                    byte scrollDelta = (byte) (yOffset > 0 ? 1 : -1);
                    sendScrollPacket(activeSlot, scrollDelta);
                    String direction = scrollDelta > 0 ? "UP" : "DOWN";
                    LOGGER.info("[Scroll] {} (slot: {})", direction, activeSlot);
                }
                // Don't call previous callback - consume the event
            } else {
                // Not charging - let vanilla handle scroll by calling previous callback
                if (previousScrollCallback != null) {
                    previousScrollCallback.invoke(window, xOffset, yOffset);
                }
            }
        });

        LOGGER.info("Mouse scroll callback registered successfully");
    }

    /**
     * Check if a skill is currently active (charging or control mode)
     */
    private static boolean isSkillActive() {
        return slot1Pressed || slot2Pressed || slot3Pressed || slot4Pressed || techniqueControlPressed;
    }

    /**
     * Get the currently active slot (1-4), or 0 if none
     */
    private static byte getActiveSlot() {
        if (slot1Pressed) return PacketIds.TechniqueSlot.SLOT_1;
        if (slot2Pressed) return PacketIds.TechniqueSlot.SLOT_2;
        if (slot3Pressed) return PacketIds.TechniqueSlot.SLOT_3;
        if (slot4Pressed) return PacketIds.TechniqueSlot.SLOT_4;
        return 0;
    }

    /**
     * Process all inputs based on input.md specifications
     */
    private static void processInputs(MinecraftClient client) {
        // Check if Shift is pressed
        long windowHandle = client.getWindow().getHandle();
        boolean currentShiftPressed = GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
            || GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;

        // Update shift state for skill termination detection
        boolean shiftJustPressed = currentShiftPressed && !shiftPressed;
        shiftPressed = currentShiftPressed;

        // Update T key state
        boolean currentTPressed = techniqueControlKey.isPressed();
        techniqueControlPressed = currentTPressed;

        // Process each key according to input.md specifications
        processRCTKey(client);
        processBarrierKey(client);
        processTechniqueSlots(client);
        processDomainExpansionKey(client);
    }

    /**
     * Process RCT key (Z)
     * input.md: Z + Shift for healing (hold)
     */
    private static void processRCTKey(MinecraftClient client) {
        boolean isPressed = rctKey.isPressed();

        if (isPressed && !rctPressed) {
            // Z key pressed
            LOGGER.info("[KEY DEBUG] Z pressed (Shift: {})", shiftPressed);
            if (shiftPressed) {
                // Z + Shift: RCT healing
                sendSkillPacket(PacketIds.SKILL_RCT, PacketIds.SkillAction.START, (byte) 0);
                LOGGER.info("[RCT] Healing started (Z + Shift)");
            } else {
                LOGGER.info("[KEY DEBUG] Z pressed without Shift - no action");
            }
            rctPressed = true;
        } else if (!isPressed && rctPressed) {
            // Z key released
            LOGGER.info("[KEY DEBUG] Z released (Shift: {})", shiftPressed);
            if (shiftPressed) {
                // RCT healing end
                sendSkillPacket(PacketIds.SKILL_RCT, PacketIds.SkillAction.END, (byte) 0);
                LOGGER.info("[RCT] Healing ended");
            } else {
                LOGGER.info("[KEY DEBUG] Z released without Shift - no action");
            }
            rctPressed = false;
        }
    }

    /**
     * Process Barrier key (G)
     * input.md: G + Shift for simple domain charging (hold)
     */
    private static void processBarrierKey(MinecraftClient client) {
        boolean isPressed = barrierKey.isPressed();

        if (isPressed && !barrierPressed) {
            // G key pressed
            LOGGER.info("[KEY DEBUG] G pressed (Shift: {})", shiftPressed);
            if (shiftPressed) {
                // G + Shift: Simple domain charging
                sendSkillPacket(PacketIds.SKILL_SIMPLE_DOMAIN, PacketIds.SkillAction.START, (byte) 0);
                LOGGER.info("[Barrier] Simple domain charging started (G + Shift)");
            } else {
                LOGGER.info("[KEY DEBUG] G pressed without Shift - no action");
            }
            barrierPressed = true;
        } else if (!isPressed && barrierPressed) {
            // G key released
            LOGGER.info("[KEY DEBUG] G released (Shift: {})", shiftPressed);
            if (shiftPressed) {
                // Simple domain charging complete
                sendSkillPacket(PacketIds.SKILL_SIMPLE_DOMAIN, PacketIds.SkillAction.END, (byte) 0);
                LOGGER.info("[Barrier] Simple domain charging complete");
            } else {
                LOGGER.info("[KEY DEBUG] G released without Shift - no action");
            }
            barrierPressed = false;
        }
    }

    /**
     * Process technique slot keys (X, C, V, B)
     * input.md specifications:
     * - X/C/V/B: Normal technique (hold to charge, release to cast)
     * - Z + X/C/V/B: Reverse technique (hold to charge, release to cast)
     * - Shift + X/C/V/B release: Early termination
     */
    private static void processTechniqueSlots(MinecraftClient client) {
        processSlot(client, slot1Key, PacketIds.TechniqueSlot.SLOT_1, 1);
        processSlot(client, slot2Key, PacketIds.TechniqueSlot.SLOT_2, 2);
        processSlot(client, slot3Key, PacketIds.TechniqueSlot.SLOT_3, 3);
        processSlot(client, slot4Key, PacketIds.TechniqueSlot.SLOT_4, 4);
    }

    /**
     * Process individual technique slot
     */
    private static void processSlot(MinecraftClient client, KeyBinding key, byte slot, int slotNumber) {
        boolean isPressed = key.isPressed();
        boolean wasSlotPressed = getSlotPressed(slotNumber);

        if (isPressed && !wasSlotPressed) {
            // Slot key pressed
            LOGGER.info("[KEY DEBUG] Slot {} pressed (T: {}, Z: {}, Shift: {})",
                slotNumber, techniqueControlPressed, rctPressed, shiftPressed);

            if (techniqueControlPressed) {
                // T + Slot: Technique control
                sendSkillPacket(PacketIds.SKILL_CONTROL, (byte) 0, slot);
                LOGGER.info("[Technique Control] Opening control UI for slot {} (T + key)", slotNumber);

                // Show message to player
                if (client.player != null) {
                    //client.player.sendMessage(
                    //    net.minecraft.text.Text.literal("§6[Technique Control] Slot " + slotNumber + " - UI coming soon!"),
                    //    false
                    //);
                }
            } else if (shiftPressed) {
                // Shift + Press: Immediate termination
                sendTerminatePacket(slot);
                LOGGER.info("[Technique] Technique slot {} terminated (Shift + press)", slotNumber);
            } else if (rctPressed) {
                // Z + Slot: Reverse technique start
                sendSkillPacket(PacketIds.SKILL_REVERSE_TECHNIQUE, PacketIds.SkillAction.START, slot);
                LOGGER.info("[Technique] Reverse technique slot {} charging started (Z + key)", slotNumber);
            } else {
                // Normal technique start
                sendSkillPacket(PacketIds.SKILL_TECHNIQUE, PacketIds.SkillAction.START, slot);
                LOGGER.info("[Technique] Technique slot {} charging started", slotNumber);
            }
            setSlotPressed(slotNumber, true);
        } else if (!isPressed && wasSlotPressed) {
            // Slot key released
            LOGGER.info("[KEY DEBUG] Slot {} released (T: {}, Z: {}, Shift: {})",
                slotNumber, techniqueControlPressed, rctPressed, shiftPressed);

            // T + Slot: Don't send release packet for control
            // Shift + Slot: Already terminated on press, no action on release
            if (techniqueControlPressed || shiftPressed) {
                LOGGER.info("[Technique] Slot {} released (special key held) - no release action", slotNumber);
            } else if (rctPressed) {
                // Z + Slot release: Cast reverse technique
                sendSkillPacket(PacketIds.SKILL_REVERSE_TECHNIQUE, PacketIds.SkillAction.END, slot);
                LOGGER.info("[Technique] Reverse technique slot {} cast", slotNumber);
            } else {
                // Normal technique release: Cast
                sendSkillPacket(PacketIds.SKILL_TECHNIQUE, PacketIds.SkillAction.END, slot);
                LOGGER.info("[Technique] Technique slot {} cast", slotNumber);
            }
            setSlotPressed(slotNumber, false);
        }
    }

    /**
     * Process Domain Expansion key (R)
     * New logic:
     * - R press (누를 때): Check modifiers and send packet immediately
     *   - Shift + R: Cancel domain (영역 해제)
     *   - G + R: No-barrier domain (무변부여 영역전개)
     *   - R alone: Normal domain expansion (영역전개)
     */
    private static void processDomainExpansionKey(MinecraftClient client) {
        boolean isPressed = domainExpansionKey.isPressed();

        if (isPressed && !rKeyPressed) {
            // R key just pressed (누를 때)
            LOGGER.info("[KEY DEBUG] R pressed (T: {}, Shift: {}, G: {})", techniqueControlPressed, shiftPressed, barrierPressed);

            if (techniqueControlPressed) {
                // T + R: Open domain settings UI
                openDomainSettingsScreen(client);
                LOGGER.info("[Domain Settings] Opening settings screen (T + R)");
            } else if (shiftPressed) {
                // Shift + R: Cancel domain
                sendDomainPacket(PacketIds.SkillAction.END, PacketIds.DomainFlags.NORMAL);
                LOGGER.info("[Domain] Domain cancel packet sent (Shift + R)");
            } else if (barrierPressed) {
                // G + R: No-barrier domain expansion
                sendDomainPacket(PacketIds.SkillAction.START, PacketIds.DomainFlags.NO_BARRIER);
                LOGGER.info("[Domain] No-barrier domain expansion packet sent (G + R)");
            } else {
                // R alone: Normal domain expansion
                sendDomainPacket(PacketIds.SkillAction.START, PacketIds.DomainFlags.NORMAL);
                LOGGER.info("[Domain] Normal domain expansion packet sent (R)");
            }

            rKeyPressed = true;
        } else if (!isPressed && rKeyPressed) {
            // R key released
            LOGGER.info("[KEY DEBUG] R released");
            rKeyPressed = false;
        }
    }

    // ===== Helper Methods =====

    private static boolean getSlotPressed(int slotNumber) {
        return switch (slotNumber) {
            case 1 -> slot1Pressed;
            case 2 -> slot2Pressed;
            case 3 -> slot3Pressed;
            case 4 -> slot4Pressed;
            default -> false;
        };
    }

    private static void setSlotPressed(int slotNumber, boolean pressed) {
        switch (slotNumber) {
            case 1 -> slot1Pressed = pressed;
            case 2 -> slot2Pressed = pressed;
            case 3 -> slot3Pressed = pressed;
            case 4 -> slot4Pressed = pressed;
        }
    }

    /**
     * Send skill packet to server
     * Format: [packetId(1)] [action(1)] [slot(1)] [timestamp(8)]
     */
    private static void sendSkillPacket(byte packetId, byte action, byte slot) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getNetworkHandler() == null) {
            LOGGER.warn("[PACKET] Cannot send packet: not connected to server");
            return;
        }

        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeByte(packetId);
        buf.writeByte(action);
        buf.writeByte(slot);
        long timestamp = System.currentTimeMillis();
        buf.writeLong(timestamp);

        byte[] data = new byte[buf.readableBytes()];
        buf.getBytes(0, data);

        JJKPayload payload = new JJKPayload(data);
        client.getNetworkHandler().sendPacket(new CustomPayloadC2SPacket(payload));

        LOGGER.info("[PACKET SENT] ID: 0x{}, Action: {}, Slot: {}, Size: {} bytes",
            String.format("%02X", packetId),
            getActionName(action),
            slot,
            data.length);
    }

    /**
     * Send terminate packet to server (no action field)
     * Format: [packetId(1)] [slot(1)] [timestamp(8)]
     */
    private static void sendTerminatePacket(byte slot) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getNetworkHandler() == null) {
            LOGGER.warn("[PACKET] Cannot send packet: not connected to server");
            return;
        }

        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeByte(PacketIds.SKILL_TERMINATE);
        buf.writeByte(slot);
        long timestamp = System.currentTimeMillis();
        buf.writeLong(timestamp);

        byte[] data = new byte[buf.readableBytes()];
        buf.getBytes(0, data);

        JJKPayload payload = new JJKPayload(data);
        client.getNetworkHandler().sendPacket(new CustomPayloadC2SPacket(payload));

        LOGGER.info("[PACKET SENT] ID: 0x{}, Slot: {}, Size: {} bytes",
            String.format("%02X", PacketIds.SKILL_TERMINATE),
            slot,
            data.length);
    }

    /**
     * Send scroll adjustment packet to server
     * Format: [packetId(1)] [slot(1)] [scrollDelta(1)] [timestamp(8)]
     * scrollDelta: +1 for scroll up, -1 for scroll down
     */
    private static void sendScrollPacket(byte slot, byte scrollDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getNetworkHandler() == null) {
            LOGGER.warn("[PACKET] Cannot send scroll packet: not connected to server");
            return;
        }

        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeByte(PacketIds.SKILL_DISTANCE);
        buf.writeByte(slot);
        buf.writeByte(scrollDelta);
        long timestamp = System.currentTimeMillis();
        buf.writeLong(timestamp);

        byte[] data = new byte[buf.readableBytes()];
        buf.getBytes(0, data);

        JJKPayload payload = new JJKPayload(data);
        client.getNetworkHandler().sendPacket(new CustomPayloadC2SPacket(payload));

        String direction = scrollDelta > 0 ? "UP" : "DOWN";
        LOGGER.info("[PACKET SENT] SCROLL ID: 0x{}, Slot: {}, Direction: {}, Size: {} bytes",
            String.format("%02X", PacketIds.SKILL_DISTANCE),
            slot,
            direction,
            data.length);
    }

    /**
     * Send domain expansion packet to server
     * Format: [packetId(1)] [action(1)] [flags(1)] [timestamp(8)]
     */
    private static void sendDomainPacket(byte action, byte flags) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getNetworkHandler() == null) {
            LOGGER.warn("[PACKET] Cannot send packet: not connected to server");
            return;
        }

        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeByte(PacketIds.DOMAIN_EXPANSION);
        buf.writeByte(action);
        buf.writeByte(flags);
        long timestamp = System.currentTimeMillis();
        buf.writeLong(timestamp);

        byte[] data = new byte[buf.readableBytes()];
        buf.getBytes(0, data);

        JJKPayload payload = new JJKPayload(data);
        client.getNetworkHandler().sendPacket(new CustomPayloadC2SPacket(payload));

        LOGGER.info("[PACKET SENT] DOMAIN ID: 0x{}, Action: {}, Flags: 0x{}, Size: {} bytes",
            String.format("%02X", PacketIds.DOMAIN_EXPANSION),
            getActionName(action),
            String.format("%02X", flags),
            data.length);
    }

    /**
     * Helper: Get action name for logging
     */
    private static String getActionName(byte action) {
        return switch (action) {
            case PacketIds.SkillAction.START -> "START";
            case PacketIds.SkillAction.END -> "END";
            default -> "UNKNOWN(" + action + ")";
        };
    }

    /**
     * CustomPayload record for JJK packets
     */
    public record JJKPayload(byte[] data) implements CustomPayload {
        public static final CustomPayload.Id<JJKPayload> ID = new CustomPayload.Id<>(CHANNEL);

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /**
     * Open domain expansion settings screen (T + R)
     */
    private static void openDomainSettingsScreen(MinecraftClient client) {
        if (client.player == null) return;

        // Send REQUEST packet to get current settings from server
        sendDomainSettingsRequest();

        // Open the settings screen
        com.justheare.paperjjk_client.screen.DomainSettingsScreen screen =
            new com.justheare.paperjjk_client.screen.DomainSettingsScreen(client.currentScreen);
        client.setScreen(screen);

        LOGGER.info("[Domain Settings] Opened settings screen and sent REQUEST packet");
    }

    /**
     * Send domain settings request packet to server
     */
    private static void sendDomainSettingsRequest() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getNetworkHandler() == null) {
            LOGGER.warn("[Domain Settings] Cannot send request: not connected to server");
            return;
        }

        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeByte(PacketIds.DOMAIN_SETTINGS);
        buf.writeByte(PacketIds.DomainSettingsAction.REQUEST);
        long timestamp = System.currentTimeMillis();
        buf.writeLong(timestamp);

        byte[] data = new byte[buf.readableBytes()];
        buf.getBytes(0, data);

        JJKPayload payload = new JJKPayload(data);
        client.getNetworkHandler().sendPacket(new CustomPayloadC2SPacket(payload));

        LOGGER.info("[Domain Settings] Sent REQUEST packet");
    }

    /**
     * Reset all key states (called on disconnect)
     */
    public static void reset() {
        rctPressed = false;
        barrierPressed = false;
        slot1Pressed = false;
        slot2Pressed = false;
        slot3Pressed = false;
        slot4Pressed = false;
        shiftPressed = false;
        techniqueControlPressed = false;
        rKeyPressed = false;
        LOGGER.debug("Key states reset");
    }
}
