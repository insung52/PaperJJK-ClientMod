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

/**
 * Player Info Screen - Main information display
 * Shows: naturaltech, CE, RCT, domain level, skill slots
 */
public class PlayerInfoScreen extends Screen {
    private static final Logger LOGGER = LoggerFactory.getLogger("PaperJJK-PlayerInfo");

    private final Screen parent;

    // Buttons
    private ButtonWidget slot1Button;
    private ButtonWidget slot2Button;
    private ButtonWidget slot3Button;
    private ButtonWidget slot4Button;
    private ButtonWidget editSkillsButton;
    private ButtonWidget settingsButton;
    private ButtonWidget closeButton;

    public PlayerInfoScreen(Screen parent) {
        super(Text.literal("플레이어 정보"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int startY = this.height / 4;

        // Skill slot buttons (4 buttons in 2x2 grid)
        int buttonWidth = 120;
        int buttonHeight = 20;
        int gap = 10;

        // Slot 1 (X) - Top left
        this.slot1Button = ButtonWidget.builder(
            Text.literal("X: " + getShortSkillName(PlayerData.getSlot1Skill())),
            button -> openSkillDetail(PlayerData.getSlot1Skill())
        ).dimensions(centerX - buttonWidth - gap/2, startY + 100, buttonWidth, buttonHeight).build();
        this.addDrawableChild(slot1Button);

        // Slot 2 (C) - Top right
        this.slot2Button = ButtonWidget.builder(
            Text.literal("C: " + getShortSkillName(PlayerData.getSlot2Skill())),
            button -> openSkillDetail(PlayerData.getSlot2Skill())
        ).dimensions(centerX + gap/2, startY + 100, buttonWidth, buttonHeight).build();
        this.addDrawableChild(slot2Button);

        // Slot 3 (V) - Bottom left
        this.slot3Button = ButtonWidget.builder(
            Text.literal("V: " + getShortSkillName(PlayerData.getSlot3Skill())),
            button -> openSkillDetail(PlayerData.getSlot3Skill())
        ).dimensions(centerX - buttonWidth - gap/2, startY + 100 + buttonHeight + 5, buttonWidth, buttonHeight).build();
        this.addDrawableChild(slot3Button);

        // Slot 4 (B) - Bottom right
        this.slot4Button = ButtonWidget.builder(
            Text.literal("B: " + getShortSkillName(PlayerData.getSlot4Skill())),
            button -> openSkillDetail(PlayerData.getSlot4Skill())
        ).dimensions(centerX + gap/2, startY + 100 + buttonHeight + 5, buttonWidth, buttonHeight).build();
        this.addDrawableChild(slot4Button);

        // Edit skills button
        this.editSkillsButton = ButtonWidget.builder(
            Text.literal("스킬 편집"),
            button -> openSkillEditor()
        ).dimensions(centerX - 102, startY + 180, 100, 20).build();
        this.addDrawableChild(editSkillsButton);

        // Settings button
        this.settingsButton = ButtonWidget.builder(
            Text.literal("옵션 설정"),
            button -> openSettings()
        ).dimensions(centerX + 2, startY + 180, 100, 20).build();
        this.addDrawableChild(settingsButton);

        // Close button
        this.closeButton = ButtonWidget.builder(
            Text.literal("닫기"),
            button -> this.close()
        ).dimensions(centerX - 50, startY + 210, 100, 20).build();
        this.addDrawableChild(closeButton);

        LOGGER.info("[Player Info] Screen initialized");
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

        // Natural technique
        String techText = "생득술식: §e" + (PlayerData.getNaturaltech().isEmpty() ? "없음" : PlayerData.getNaturaltech());
        context.drawText(
            this.textRenderer,
            Text.literal(techText),
            centerX - this.textRenderer.getWidth(techText) / 2,
            startY + 10,
            0xFFFFFFFF,
            true
        );

        // Curse energy
        String ceText = String.format("주력량: §b%d §f/ §b%d",
            PlayerData.getCurseEnergy(),
            PlayerData.getMaxCurseEnergy()
        );
        context.drawText(
            this.textRenderer,
            Text.literal(ceText),
            centerX - this.textRenderer.getWidth(ceText) / 2,
            startY + 30,
            0xFFFFFFFF,
            true
        );

        // RCT
        String rctText = "반전술식: " + (PlayerData.hasRCT() ? "§a가능" : "§c불가능");
        context.drawText(
            this.textRenderer,
            Text.literal(rctText),
            centerX - this.textRenderer.getWidth(rctText) / 2,
            startY + 50,
            0xFFFFFFFF,
            true
        );

        // Domain level
        String domainText = "결계술 레벨: §d" + PlayerData.getDomainLevel();
        context.drawText(
            this.textRenderer,
            Text.literal(domainText),
            centerX - this.textRenderer.getWidth(domainText) / 2,
            startY + 70,
            0xFFFFFFFF,
            true
        );

        // Skill slots label
        String slotsLabel = "§6스킬 단축키";
        context.drawText(
            this.textRenderer,
            Text.literal(slotsLabel),
            centerX - this.textRenderer.getWidth(slotsLabel) / 2,
            startY + 85,
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
     * Refresh screen data (called when server sends updated info)
     */
    public void refresh() {
        // Reinitialize to update button labels
        this.clearChildren();
        this.init();
    }

    /**
     * Get short skill name (remove naturaltech prefix)
     */
    private String getShortSkillName(String fullSkillId) {
        if (fullSkillId == null || fullSkillId.isEmpty()) {
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
     * Open skill detail screen
     */
    private void openSkillDetail(String skillId) {
        if (skillId == null || skillId.isEmpty()) {
            LOGGER.info("[Player Info] No skill assigned to this slot");
            return;
        }

        LOGGER.info("[Player Info] Opening skill detail: {}", skillId);

        // Request skill info from server if not cached
        if (PlayerData.getSkillInfo(skillId) == null) {
            requestSkillInfo(skillId);
        }

        this.client.setScreen(new SkillDetailScreen(this, skillId));
    }

    /**
     * Request skill info from server
     */
    private void requestSkillInfo(String skillId) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getNetworkHandler() == null) {
            return;
        }

        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeByte(PacketIds.SKILL_INFO_REQUEST);
        buf.writeString(skillId);  // Use Minecraft's writeString for UTF
        buf.writeLong(System.currentTimeMillis());

        byte[] data = new byte[buf.readableBytes()];
        buf.getBytes(0, data);

        JJKKeyBinds.JJKPayload payload = new JJKKeyBinds.JJKPayload(data);
        client.getNetworkHandler().sendPacket(new CustomPayloadC2SPacket(payload));

        LOGGER.info("[Player Info] Requested skill info: {}", skillId);
    }

    /**
     * Open skill editor screen
     */
    private void openSkillEditor() {
        LOGGER.info("[Player Info] Opening skill editor");
        this.client.setScreen(new SkillEditorScreen(this));
    }

    /**
     * Open settings screen
     */
    private void openSettings() {
        LOGGER.info("[Player Info] Opening settings");
        this.client.setScreen(new ClientSettingsScreen(this));
    }
}
