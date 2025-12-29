package com.justheare.paperjjk_client.screen;

import com.justheare.paperjjk_client.data.PlayerData;
import com.justheare.paperjjk_client.keybind.JJKKeyBinds;
import com.justheare.paperjjk_client.network.PacketIds;
import io.netty.buffer.Unpooled;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.c2s.common.CustomPayloadC2SPacket;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Skill Editor Screen - Allows changing skill bindings
 * Only edits X, C, V, B slots (not R, G, Z)
 */
public class SkillEditorScreen extends Screen {
    private static final Logger LOGGER = LoggerFactory.getLogger("PaperJJK-SkillEditor");

    private final Screen parent;

    // Skill selectors for each slot
    private ButtonWidget slot1Selector;
    private ButtonWidget slot2Selector;
    private ButtonWidget slot3Selector;
    private ButtonWidget slot4Selector;

    // Available skills list (derived from naturaltech)
    private List<String> availableSkills;

    // Current selection indices
    private int slot1Index;
    private int slot2Index;
    private int slot3Index;
    private int slot4Index;

    private ButtonWidget saveButton;
    private ButtonWidget cancelButton;

    public SkillEditorScreen(Screen parent) {
        super(Text.literal("스킬 편집"));
        this.parent = parent;

        // Get available skills based on naturaltech
        this.availableSkills = getAvailableSkills();

        // Initialize indices with current slot values
        String slot1 = PlayerData.getSlot1Skill().isEmpty() ? "없음" : PlayerData.getSlot1Skill();
        String slot2 = PlayerData.getSlot2Skill().isEmpty() ? "없음" : PlayerData.getSlot2Skill();
        String slot3 = PlayerData.getSlot3Skill().isEmpty() ? "없음" : PlayerData.getSlot3Skill();
        String slot4 = PlayerData.getSlot4Skill().isEmpty() ? "없음" : PlayerData.getSlot4Skill();

        this.slot1Index = Math.max(0, availableSkills.indexOf(slot1));
        this.slot2Index = Math.max(0, availableSkills.indexOf(slot2));
        this.slot3Index = Math.max(0, availableSkills.indexOf(slot3));
        this.slot4Index = Math.max(0, availableSkills.indexOf(slot4));
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int startY = this.height / 4;

        int buttonWidth = 200;
        int buttonHeight = 20;
        int gap = 5;

        // Slot 1 (X) selector
        this.slot1Selector = ButtonWidget.builder(
            Text.literal("X: " + getShortSkillName(availableSkills.get(slot1Index))),
            button -> {
                slot1Index = (slot1Index + 1) % availableSkills.size();
                button.setMessage(Text.literal("X: " + getShortSkillName(availableSkills.get(slot1Index))));
                LOGGER.info("[Skill Editor] Slot 1 (X) changed to: {}", availableSkills.get(slot1Index));
            }
        ).dimensions(centerX - buttonWidth / 2, startY + 20, buttonWidth, buttonHeight).build();
        this.addDrawableChild(slot1Selector);

        // Slot 2 (C) selector
        this.slot2Selector = ButtonWidget.builder(
            Text.literal("C: " + getShortSkillName(availableSkills.get(slot2Index))),
            button -> {
                slot2Index = (slot2Index + 1) % availableSkills.size();
                button.setMessage(Text.literal("C: " + getShortSkillName(availableSkills.get(slot2Index))));
                LOGGER.info("[Skill Editor] Slot 2 (C) changed to: {}", availableSkills.get(slot2Index));
            }
        ).dimensions(centerX - buttonWidth / 2, startY + 20 + buttonHeight + gap, buttonWidth, buttonHeight).build();
        this.addDrawableChild(slot2Selector);

        // Slot 3 (V) selector
        this.slot3Selector = ButtonWidget.builder(
            Text.literal("V: " + getShortSkillName(availableSkills.get(slot3Index))),
            button -> {
                slot3Index = (slot3Index + 1) % availableSkills.size();
                button.setMessage(Text.literal("V: " + getShortSkillName(availableSkills.get(slot3Index))));
                LOGGER.info("[Skill Editor] Slot 3 (V) changed to: {}", availableSkills.get(slot3Index));
            }
        ).dimensions(centerX - buttonWidth / 2, startY + 20 + (buttonHeight + gap) * 2, buttonWidth, buttonHeight).build();
        this.addDrawableChild(slot3Selector);

        // Slot 4 (B) selector
        this.slot4Selector = ButtonWidget.builder(
            Text.literal("B: " + getShortSkillName(availableSkills.get(slot4Index))),
            button -> {
                slot4Index = (slot4Index + 1) % availableSkills.size();
                button.setMessage(Text.literal("B: " + getShortSkillName(availableSkills.get(slot4Index))));
                LOGGER.info("[Skill Editor] Slot 4 (B) changed to: {}", availableSkills.get(slot4Index));
            }
        ).dimensions(centerX - buttonWidth / 2, startY + 20 + (buttonHeight + gap) * 3, buttonWidth, buttonHeight).build();
        this.addDrawableChild(slot4Selector);

        // Save button
        this.saveButton = ButtonWidget.builder(
            Text.literal("저장"),
            button -> saveBindings()
        ).dimensions(centerX - 102, startY + 150, 100, 20).build();
        this.addDrawableChild(saveButton);

        // Cancel button
        this.cancelButton = ButtonWidget.builder(
            Text.literal("취소"),
            button -> this.close()
        ).dimensions(centerX + 2, startY + 150, 100, 20).build();
        this.addDrawableChild(cancelButton);

        LOGGER.info("[Skill Editor] Screen initialized");
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Draw background
        context.fillGradient(0, 0, this.width, this.height, 0xC0101010, 0xD0101010);

        // Render widgets
        super.render(context, mouseX, mouseY, delta);

        int centerX = this.width / 2;
        int startY = this.height / 4;

        // Title
        context.drawText(
            this.textRenderer,
            this.title,
            centerX - this.textRenderer.getWidth(this.title) / 2,
            startY - 20,
            0xFFFFFFFF,
            true
        );

        // Info text
        String infoText = "§7버튼을 클릭하여 스킬을 변경하세요";
        context.drawText(
            this.textRenderer,
            Text.literal(infoText),
            centerX - this.textRenderer.getWidth(infoText) / 2,
            startY,
            0xFFFFFFFF,
            true
        );
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(parent);
        }
    }

    /**
     * Get available skills based on player's naturaltech
     */
    private List<String> getAvailableSkills() {
        String naturaltech = PlayerData.getNaturaltech();
        List<String> skills = new ArrayList<>();

        // Always add "없음" option
        skills.add("없음");

        // Add skills based on naturaltech
        if (naturaltech == null || naturaltech.isEmpty()) {
            LOGGER.info("[Skill Editor] No naturaltech found, only 없음 available");
            return skills;
        }

        // Add naturaltech-specific skills
        // Format: naturaltech_skillname (e.g., "infinity_ao", "infinity_aka", etc.)
        switch (naturaltech) {
            case "infinity":
                skills.add("infinity_ao");
                skills.add("infinity_aka");
                skills.add("infinity_murasaki");
                skills.add("infinity_hollow_purple");
                break;
            case "10s":
                skills.add("10s_divine_dogs");
                skills.add("10s_nue");
                skills.add("10s_great_serpent");
                skills.add("10s_toad");
                skills.add("10s_rabbit_escape");
                skills.add("10s_max_elephant");
                skills.add("10s_piercing_ox");
                skills.add("10s_tiger_funeral");
                skills.add("10s_mahoraga");
                skills.add("10s_agito");
                break;
            case "construction":
                skills.add("construction_create");
                skills.add("construction_seal");
                break;
            case "cursed_speech":
                skills.add("cursed_speech_stop");
                skills.add("cursed_speech_crush");
                skills.add("cursed_speech_explode");
                skills.add("cursed_speech_die");
                break;
            case "projection_sorcery":
                skills.add("projection_sorcery_activate");
                break;
            case "idle_transfiguration":
                skills.add("idle_transfiguration_touch");
                skills.add("idle_transfiguration_transform");
                break;
            case "shrine":
                skills.add("shrine_dismantle");
                skills.add("shrine_cleave");
                skills.add("shrine_fire_arrow");
                break;
            case "comedian":
                skills.add("comedian_activate");
                break;
            case "star_rage":
                skills.add("star_rage_punch");
                skills.add("star_rage_mass");
                break;
            default:
                LOGGER.info("[Skill Editor] Unknown naturaltech: {}, only 없음 available", naturaltech);
                break;
        }

        LOGGER.info("[Skill Editor] Available skills for {}: {}", naturaltech, skills);
        return skills;
    }

    /**
     * Get short skill name (remove naturaltech prefix)
     */
    private String getShortSkillName(String fullSkillId) {
        if (fullSkillId == null || fullSkillId.isEmpty() || fullSkillId.equals("없음")) {
            return "없음";
        }

        // Remove prefix (e.g., "infinity_ao" -> "ao")
        int underscoreIndex = fullSkillId.indexOf('_');
        if (underscoreIndex > 0 && underscoreIndex < fullSkillId.length() - 1) {
            return fullSkillId.substring(underscoreIndex + 1);
        }

        return fullSkillId;
    }

    /**
     * Save skill bindings and send to server
     */
    private void saveBindings() {
        String slot1 = availableSkills.get(slot1Index);
        String slot2 = availableSkills.get(slot2Index);
        String slot3 = availableSkills.get(slot3Index);
        String slot4 = availableSkills.get(slot4Index);

        LOGGER.info("[Skill Editor] Saving bindings: slot1={}, slot2={}, slot3={}, slot4={}",
            slot1, slot2, slot3, slot4);

        // Convert "없음" to empty string
        slot1 = slot1.equals("없음") ? "" : slot1;
        slot2 = slot2.equals("없음") ? "" : slot2;
        slot3 = slot3.equals("없음") ? "" : slot3;
        slot4 = slot4.equals("없음") ? "" : slot4;

        // Send update packets for each changed slot
        if (!slot1.equals(PlayerData.getSlot1Skill())) {
            sendSkillBindingUpdate((byte) 1, slot1);
        }
        if (!slot2.equals(PlayerData.getSlot2Skill())) {
            sendSkillBindingUpdate((byte) 2, slot2);
        }
        if (!slot3.equals(PlayerData.getSlot3Skill())) {
            sendSkillBindingUpdate((byte) 3, slot3);
        }
        if (!slot4.equals(PlayerData.getSlot4Skill())) {
            sendSkillBindingUpdate((byte) 4, slot4);
        }

        // Update local cache
        PlayerData.updatePlayerInfo(
            PlayerData.getNaturaltech(),
            PlayerData.getCurseEnergy(),
            PlayerData.getMaxCurseEnergy(),
            PlayerData.hasRCT(),
            PlayerData.getDomainLevel(),
            slot1,
            slot2,
            slot3,
            slot4
        );

        // Close screen
        this.close();
    }

    /**
     * Send skill binding update packet to server
     */
    private void sendSkillBindingUpdate(byte slot, String skillId) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getNetworkHandler() == null) {
            return;
        }

        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeByte(PacketIds.SKILL_BINDING_UPDATE);
        buf.writeByte(slot);
        buf.writeString(skillId);  // Use Minecraft's writeString for UTF
        buf.writeLong(System.currentTimeMillis());

        byte[] data = new byte[buf.readableBytes()];
        buf.getBytes(0, data);

        JJKKeyBinds.JJKPayload payload = new JJKKeyBinds.JJKPayload(data);
        client.getNetworkHandler().sendPacket(new CustomPayloadC2SPacket(payload));

        LOGGER.info("[Skill Editor] Sent binding update: slot {} -> {}", slot, skillId);
    }
}
