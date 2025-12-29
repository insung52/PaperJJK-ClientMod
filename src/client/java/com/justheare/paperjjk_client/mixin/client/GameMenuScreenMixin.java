package com.justheare.paperjjk_client.mixin.client;

import com.justheare.paperjjk_client.keybind.JJKKeyBinds;
import com.justheare.paperjjk_client.network.PacketIds;
import com.justheare.paperjjk_client.screen.PlayerInfoScreen;
import io.netty.buffer.Unpooled;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.c2s.common.CustomPayloadC2SPacket;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mixin to add "플레이어 정보" button to the ESC pause menu
 */
@Mixin(GameMenuScreen.class)
public abstract class GameMenuScreenMixin extends Screen {
    private static final Logger LOGGER = LoggerFactory.getLogger("PaperJJK-GameMenuMixin");
    private static final Identifier PLAYER_INFO_ICON = Identifier.of("paperjjk-client", "icon_pixelated.png");

    @Unique
    private ButtonWidget paperjjk$playerInfoButton;

    protected GameMenuScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "initWidgets", at = @At("RETURN"))
    private void addPlayerInfoButton(CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();

        // Only add button if connected to a server
        if (client.player == null || client.getNetworkHandler() == null) {
            return;
        }

        // Create icon button (positioned left of Advancements button)
        // Position: left of center, at the same row as Advancements (typically second row)
        int buttonSize = 20;
        int buttonX = this.width / 2 - 130;  // Left of center button area
        int buttonY = this.height / 4 + 48;  // Second row of buttons

        paperjjk$playerInfoButton = ButtonWidget.builder(
            Text.literal("JJK"),  // Temporary text instead of icon
            button -> {
                // Send player info request packet
                PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
                buf.writeByte(PacketIds.PLAYER_INFO_REQUEST);
                buf.writeLong(System.currentTimeMillis());

                byte[] data = new byte[buf.readableBytes()];
                buf.getBytes(0, data);

                JJKKeyBinds.JJKPayload payload = new JJKKeyBinds.JJKPayload(data);
                client.getNetworkHandler().sendPacket(new CustomPayloadC2SPacket(payload));

                // Open PlayerInfoScreen
                client.setScreen(new PlayerInfoScreen((GameMenuScreen) (Object) this));
                LOGGER.info("[ESC Menu Mixin] Opened player info screen");
            }
        ).dimensions(buttonX, buttonY, buttonSize, buttonSize)
         .tooltip(Tooltip.of(Text.literal("플레이어 정보")))
         .build();

        // Add button to screen
        this.addDrawableChild(paperjjk$playerInfoButton);

        LOGGER.debug("[ESC Menu Mixin] Added player info icon button at ({}, {})", buttonX, buttonY);
    }

    // TODO: Add icon rendering once we figure out the correct DrawContext API for Yarn mappings
    // @Inject(method = "render", at = @At("RETURN"))
    // private void renderPlayerInfoIcon(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
    //     if (paperjjk$playerInfoButton != null && paperjjk$playerInfoButton.visible) {
    //         // Render icon centered in button
    //         final int iconSize = 16;
    //         int paddingX = (paperjjk$playerInfoButton.getWidth() - iconSize) / 2;
    //         int paddingY = (paperjjk$playerInfoButton.getHeight() - iconSize) / 2;
    //
    //         int iconX = paperjjk$playerInfoButton.getX() + paddingX;
    //         int iconY = paperjjk$playerInfoButton.getY() + paddingY;
    //
    //         // Draw icon texture
    //         context.drawTexture(PLAYER_INFO_ICON, iconX, iconY, 0.0f, 0.0f, iconSize, iconSize, iconSize, iconSize);
    //     }
    // }
}
