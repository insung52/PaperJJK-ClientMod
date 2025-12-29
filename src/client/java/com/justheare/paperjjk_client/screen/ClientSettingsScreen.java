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
 * Client Settings Screen - Manages client-side visual settings
 * - Post-processing effects toggle
 * - Domain expansion geometry toggle
 */
public class ClientSettingsScreen extends Screen {
    private static final Logger LOGGER = LoggerFactory.getLogger("PaperJJK-ClientSettings");

    private final Screen parent;

    // Setting values
    private boolean postProcessingEnabled;
    private boolean domainEffectsEnabled;

    // Setting buttons
    private ButtonWidget postProcessingButton;
    private ButtonWidget domainEffectsButton;

    private ButtonWidget saveButton;
    private ButtonWidget cancelButton;

    public ClientSettingsScreen(Screen parent) {
        super(Text.literal("클라이언트 설정"));
        this.parent = parent;

        // Initialize with current settings
        this.postProcessingEnabled = PlayerData.isPostProcessingEnabled();
        this.domainEffectsEnabled = PlayerData.isDomainEffectsEnabled();
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int startY = this.height / 4;

        int buttonWidth = 200;
        int buttonHeight = 20;
        int gap = 10;

        // Post-processing toggle
        this.postProcessingButton = ButtonWidget.builder(
            Text.literal(postProcessingEnabled ? "포스트프로세싱: §a켜짐" : "포스트프로세싱: §c꺼짐"),
            button -> {
                postProcessingEnabled = !postProcessingEnabled;
                button.setMessage(Text.literal(postProcessingEnabled ? "포스트프로세싱: §a켜짐" : "포스트프로세싱: §c꺼짐"));
                LOGGER.info("[Client Settings] Post-processing toggled to: {}", postProcessingEnabled);
            }
        ).dimensions(centerX - buttonWidth / 2, startY + 20, buttonWidth, buttonHeight).build();
        this.addDrawableChild(postProcessingButton);

        // Domain effects toggle
        this.domainEffectsButton = ButtonWidget.builder(
            Text.literal(domainEffectsEnabled ? "영역 이펙트: §a켜짐" : "영역 이펙트: §c꺼짐"),
            button -> {
                domainEffectsEnabled = !domainEffectsEnabled;
                button.setMessage(Text.literal(domainEffectsEnabled ? "영역 이펙트: §a켜짐" : "영역 이펙트: §c꺼짐"));
                LOGGER.info("[Client Settings] Domain effects toggled to: {}", domainEffectsEnabled);
            }
        ).dimensions(centerX - buttonWidth / 2, startY + 60, buttonWidth, buttonHeight).build();
        this.addDrawableChild(domainEffectsButton);

        // Save button
        this.saveButton = ButtonWidget.builder(
            Text.literal("저장"),
            button -> saveSettings()
        ).dimensions(centerX - 102, startY + 120, 100, 20).build();
        this.addDrawableChild(saveButton);

        // Cancel button
        this.cancelButton = ButtonWidget.builder(
            Text.literal("취소"),
            button -> this.close()
        ).dimensions(centerX + 2, startY + 120, 100, 20).build();
        this.addDrawableChild(cancelButton);

        LOGGER.info("[Client Settings] Screen initialized");
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
        String infoText = "§7클라이언트 측 시각 효과 설정";
        context.drawText(
            this.textRenderer,
            Text.literal(infoText),
            centerX - this.textRenderer.getWidth(infoText) / 2,
            startY,
            0xFFFFFFFF,
            true
        );

        // Post-processing description
        String ppDesc = "§7화면 효과 (블러, 글로우 등)";
        context.drawText(
            this.textRenderer,
            Text.literal(ppDesc),
            centerX - this.textRenderer.getWidth(ppDesc) / 2,
            startY + 42,
            0xFFFFFFFF,
            true
        );

        // Domain effects description
        String deDesc = "§7영역전개 구 지오메트리 렌더링";
        context.drawText(
            this.textRenderer,
            Text.literal(deDesc),
            centerX - this.textRenderer.getWidth(deDesc) / 2,
            startY + 82,
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
     * Save settings to PlayerData and optionally send to server
     */
    private void saveSettings() {
        LOGGER.info("[Client Settings] Saving settings: postProcessing={}, domainEffects={}",
            postProcessingEnabled, domainEffectsEnabled);

        // Update PlayerData
        PlayerData.setPostProcessingEnabled(postProcessingEnabled);
        PlayerData.setDomainEffectsEnabled(domainEffectsEnabled);

        // Optionally send to server (for future server-side tracking)
        sendClientSettingsUpdate();

        // Close screen
        this.close();
    }

    /**
     * Send client settings update packet to server
     * (Optional - currently server doesn't need these settings)
     */
    private void sendClientSettingsUpdate() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getNetworkHandler() == null) {
            return;
        }

        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeByte(PacketIds.CLIENT_SETTINGS_UPDATE);
        buf.writeBoolean(postProcessingEnabled);
        buf.writeBoolean(domainEffectsEnabled);
        buf.writeLong(System.currentTimeMillis());

        byte[] data = new byte[buf.readableBytes()];
        buf.getBytes(0, data);

        JJKKeyBinds.JJKPayload payload = new JJKKeyBinds.JJKPayload(data);
        client.getNetworkHandler().sendPacket(new CustomPayloadC2SPacket(payload));

        LOGGER.info("[Client Settings] Sent settings update to server");
    }
}
