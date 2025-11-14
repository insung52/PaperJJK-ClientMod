package com.justheare.paperjjk_client.screen;

import com.justheare.paperjjk_client.keybind.JJKKeyBinds;
import com.justheare.paperjjk_client.network.PacketIds;
import io.netty.buffer.Unpooled;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.c2s.common.CustomPayloadC2SPacket;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Domain Expansion Settings Screen
 * Allows configuration of domain expansion ranges:
 * - Normal domain: 5~50
 * - No-barrier domain: 5~200
 */
public class DomainSettingsScreen extends Screen {
    private static final Logger LOGGER = LoggerFactory.getLogger("PaperJJK-DomainSettings");

    private final Screen parent;

    // Current values
    private int normalDomainRange = 30;
    private int noBarrierDomainRange = 50;

    // Sliders
    private NormalDomainSlider normalSlider;
    private NoBarrierDomainSlider noBarrierSlider;

    // Buttons
    private ButtonWidget saveButton;
    private ButtonWidget cancelButton;

    public DomainSettingsScreen(Screen parent) {
        super(Text.literal("영역전개 설정"));
        this.parent = parent;
    }

    /**
     * Set current domain ranges (called when receiving server response)
     */
    public void setDomainRanges(int normalRange, int noBarrierRange) {
        this.normalDomainRange = normalRange;
        this.noBarrierDomainRange = noBarrierRange;

        if (normalSlider != null) {
            normalSlider.setValueFromInt(normalRange);
        }
        if (noBarrierSlider != null) {
            noBarrierSlider.setValueFromInt(noBarrierRange);
        }

        LOGGER.info("[Domain Settings] Set ranges: normal={}, noBarrier={}", normalRange, noBarrierRange);
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int startY = this.height / 3;

        // Normal domain slider (5~50)
        this.normalSlider = new NormalDomainSlider(
            centerX - 150, startY, 300, 20, normalDomainRange
        );
        this.addDrawableChild(normalSlider);

        // No-barrier domain slider (5~200)
        this.noBarrierSlider = new NoBarrierDomainSlider(
            centerX - 150, startY + 60, 300, 20, noBarrierDomainRange
        );
        this.addDrawableChild(noBarrierSlider);

        // Save button
        this.saveButton = ButtonWidget.builder(
            Text.literal("저장"),
            button -> {
                int normal = normalSlider.getCurrentValue();
                int noBarrier = noBarrierSlider.getCurrentValue();
                sendUpdatePacket(normal, noBarrier);
                this.close();
            }
        ).dimensions(centerX - 102, startY + 120, 100, 20).build();
        this.addDrawableChild(saveButton);

        // Cancel button
        this.cancelButton = ButtonWidget.builder(
            Text.literal("취소"),
            button -> this.close()
        ).dimensions(centerX + 2, startY + 120, 100, 20).build();
        this.addDrawableChild(cancelButton);

        LOGGER.info("[Domain Settings] Screen initialized");
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Draw background
        context.fillGradient(0, 0, this.width, this.height, 0xC0101010, 0xD0101010);

        // Render widgets
        super.render(context, mouseX, mouseY, delta);

        // Title (ARGB format: 0xAARRGGBB - full alpha for visibility)
        context.drawText(
            this.textRenderer,
            this.title,
            this.width / 2 - this.textRenderer.getWidth(this.title) / 2,
            20,
            0xFFFFFFFF,  // White with full alpha
            true  // With shadow
        );

        // Normal domain label
        context.drawText(
            this.textRenderer,
            Text.literal("영역전개 범위 (5~50)"),
            this.width / 2 - 150,
            this.height / 3 - 15,
            0xFFFFFFFF,  // White with full alpha
            true
        );

        // No-barrier domain label
        context.drawText(
            this.textRenderer,
            Text.literal("결계가 없는 영역전개 범위 (5~200)"),
            this.width / 2 - 150,
            this.height / 3 + 45,
            0xFFFFFFFF,  // White with full alpha
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
     * Send domain settings update packet to server
     */
    private void sendUpdatePacket(int normalRange, int noBarrierRange) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getNetworkHandler() == null) {
            LOGGER.warn("[Domain Settings] Cannot send packet: not connected to server");
            return;
        }

        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeByte(PacketIds.DOMAIN_SETTINGS);
        buf.writeByte(PacketIds.DomainSettingsAction.UPDATE);
        buf.writeInt(normalRange);
        buf.writeInt(noBarrierRange);
        long timestamp = System.currentTimeMillis();
        buf.writeLong(timestamp);

        byte[] data = new byte[buf.readableBytes()];
        buf.getBytes(0, data);

        JJKKeyBinds.JJKPayload payload = new JJKKeyBinds.JJKPayload(data);
        client.getNetworkHandler().sendPacket(new CustomPayloadC2SPacket(payload));

        LOGGER.info("[Domain Settings] Sent UPDATE packet: normal={}, noBarrier={}", normalRange, noBarrierRange);
    }

    /**
     * Normal domain slider (range: 5~50)
     */
    private class NormalDomainSlider extends SliderWidget {
        public NormalDomainSlider(int x, int y, int width, int height, int initialValue) {
            super(x, y, width, height, Text.empty(), (initialValue - 5) / 45.0);  // Map 5~50 to 0~1
            this.updateMessage();
        }

        @Override
        protected void updateMessage() {
            int value = getCurrentValue();
            this.setMessage(Text.literal("§e" + value));
        }

        @Override
        protected void applyValue() {
            // Value applied when slider moves
        }

        public int getCurrentValue() {
            return 5 + (int) (this.value * 45);  // Map 0~1 to 5~50
        }

        public void setValueFromInt(int intValue) {
            this.value = (intValue - 5) / 45.0;  // Map 5~50 to 0~1
            this.updateMessage();
        }
    }

    /**
     * No-barrier domain slider (range: 5~200)
     */
    private class NoBarrierDomainSlider extends SliderWidget {
        public NoBarrierDomainSlider(int x, int y, int width, int height, int initialValue) {
            super(x, y, width, height, Text.empty(), (initialValue - 5) / 195.0);  // Map 5~200 to 0~1
            this.updateMessage();
        }

        @Override
        protected void updateMessage() {
            int value = getCurrentValue();
            this.setMessage(Text.literal("§e" + value));
        }

        @Override
        protected void applyValue() {
            // Value applied when slider moves
        }

        public int getCurrentValue() {
            return 5 + (int) (this.value * 195);  // Map 0~1 to 5~200
        }

        public void setValueFromInt(int intValue) {
            this.value = (intValue - 5) / 195.0;  // Map 5~200 to 0~1
            this.updateMessage();
        }
    }
}
