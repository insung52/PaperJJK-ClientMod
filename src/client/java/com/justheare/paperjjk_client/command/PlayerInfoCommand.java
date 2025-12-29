package com.justheare.paperjjk_client.command;

import com.justheare.paperjjk_client.keybind.JJKKeyBinds;
import com.justheare.paperjjk_client.network.PacketIds;
import com.justheare.paperjjk_client.screen.PlayerInfoScreen;
import com.mojang.brigadier.CommandDispatcher;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.c2s.common.CustomPayloadC2SPacket;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/**
 * Client-side /jjkhelp command
 * Usage: /jjkhelp - Opens player info screen
 */
public class PlayerInfoCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger("PaperJJK-PlayerInfoCommand");

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(
            literal("jjkhelp")
                .executes(context -> {
                    MinecraftClient client = MinecraftClient.getInstance();

                    if (client.player == null) {
                        context.getSource().sendError(Text.literal("§c플레이어가 없습니다!"));
                        return 0;
                    }

                    if (client.getNetworkHandler() == null) {
                        context.getSource().sendError(Text.literal("§c서버에 연결되어 있지 않습니다!"));
                        return 0;
                    }

                    // Send player info request packet
                    PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
                    buf.writeByte(PacketIds.PLAYER_INFO_REQUEST);
                    buf.writeLong(System.currentTimeMillis());

                    byte[] data = new byte[buf.readableBytes()];
                    buf.getBytes(0, data);

                    JJKKeyBinds.JJKPayload payload = new JJKKeyBinds.JJKPayload(data);
                    client.getNetworkHandler().sendPacket(new CustomPayloadC2SPacket(payload));

                    // Open PlayerInfoScreen
                    client.execute(() -> {
                        client.setScreen(new PlayerInfoScreen(client.currentScreen));
                    });

                    LOGGER.info("[/jjkhelp Command] Opened player info screen");
                    return 1;
                })
        );

        LOGGER.info("[PlayerInfoCommand] Registered /jjkhelp command");
    }
}
