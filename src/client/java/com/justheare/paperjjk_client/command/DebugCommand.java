package com.justheare.paperjjk_client.command;

import com.justheare.paperjjk_client.render.DebugRenderer;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/**
 * Simple debug command for testing rendering
 */
public class DebugCommand {
    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(
            literal("jjkdebug")
                .then(literal("render")
                    .executes(DebugCommand::toggleRender)
                )
        );
    }

    private static int toggleRender(CommandContext<FabricClientCommandSource> context) {
        DebugRenderer.toggleCube();
        context.getSource().sendFeedback(
            Text.literal("§6[PaperJJK Debug] §fToggled debug cube rendering")
        );
        return 1;
    }
}
