package com.justheare.paperjjk_client.command;

import com.justheare.paperjjk_client.render.DebugRenderer;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
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
                .then(literal("effect")
                    .then(argument("id", IntegerArgumentType.integer(1, 10))
                        .executes(DebugCommand::toggleEffect)
                    )
                )
                .then(literal("shader")
                    .executes(DebugCommand::toggleShader)
                )
                .then(literal("methods")
                    .executes(DebugCommand::inspectMethods)
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

    private static int toggleEffect(CommandContext<FabricClientCommandSource> context) {
        int effectId = IntegerArgumentType.getInteger(context, "id");

        if (effectId == 1) {
            // Effect 1: Blue Fresnel sphere
            Vec3d effectPos = new Vec3d(0, 150, 0);
            DebugRenderer.toggleEffect(1, effectPos);
            context.getSource().sendFeedback(
                Text.literal("§6[PaperJJK Debug] §fToggled effect 1 (blue Fresnel) at " + effectPos)
            );
        } else if (effectId == 2) {
            // Effect 2: Yellow Fresnel sphere
            Vec3d effectPos = new Vec3d(10, 150, 10);
            DebugRenderer.toggleEffect(2, effectPos);
            context.getSource().sendFeedback(
                Text.literal("§6[PaperJJK Debug] §fToggled effect 2 (yellow Fresnel) at " + effectPos)
            );
        } else {
            context.getSource().sendFeedback(
                Text.literal("§c[PaperJJK Debug] §fEffect " + effectId + " not implemented yet")
            );
        }

        return 1;
    }

    private static int toggleShader(CommandContext<FabricClientCommandSource> context) {
        context.getSource().sendFeedback(
            Text.literal("§d[PaperJJK Debug] §fShader command - To be implemented")
        );
        // TODO: Implement shader toggle
        return 1;
    }

    private static int inspectMethods(CommandContext<FabricClientCommandSource> context) {
        com.justheare.paperjjk_client.debug.GameRendererDebug.inspectMethods();
        context.getSource().sendFeedback(
            Text.literal("§e[PaperJJK Debug] §fGameRenderer methods logged to console")
        );
        return 1;
    }
}
