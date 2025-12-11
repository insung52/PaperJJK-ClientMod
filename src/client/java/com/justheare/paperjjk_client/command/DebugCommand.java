package com.justheare.paperjjk_client.command;

import com.justheare.paperjjk_client.render.DebugRenderer;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
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
                .then(literal("refraction")
                        .executes(DebugCommand::addRefractionEffect)  // strength 인자 없으면 기본값 1.0
                        .then(argument("strength", FloatArgumentType.floatArg(0.1f, 10.0f))
                                        .executes(DebugCommand::addRefractionEffectWithStrength)  // strength 인자 있으면 해당값 사용
                        )
                )
                .then(literal("clear")
                    .executes(DebugCommand::clearRefractionEffects)
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
        // Check current post-processor first
        net.minecraft.client.render.GameRenderer renderer = net.minecraft.client.MinecraftClient.getInstance().gameRenderer;
        net.minecraft.util.Identifier currentId = renderer.getPostProcessorId();

        context.getSource().sendFeedback(
            Text.literal("§e[Debug] Current post-processor: " + (currentId == null ? "§cNONE" : "§a" + currentId))
        );

        com.justheare.paperjjk_client.shader.PostEffectManager.toggle();

        String status = com.justheare.paperjjk_client.shader.PostEffectManager.isActive()
            ? "§aENABLED" : "§cDISABLED";

        context.getSource().sendFeedback(
            Text.literal("§d[PaperJJK Debug] §fPost-processing shader " + status)
        );
        return 1;
    }

    private static int addRefractionEffect(CommandContext<FabricClientCommandSource> context) {
        // Get player position and add effect 10 blocks ahead
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client.player == null) {
            context.getSource().sendFeedback(Text.literal("§c[Error] No player found"));
            return 0;
        }

        Vec3d playerPos = client.player.getEyePos();
        Vec3d lookVec = client.player.getRotationVec(1.0f);
        Vec3d effectPos = playerPos.add(lookVec.multiply(10));

        com.justheare.paperjjk_client.shader.RefractionEffectManager.addEffect(effectPos, 0.3f, 1.0f, "DEBUG");

        context.getSource().sendFeedback(
            Text.literal("§d[PaperJJK Debug] §fAdded refraction effect at §e" +
                String.format("(%.1f, %.1f, %.1f)", effectPos.x, effectPos.y, effectPos.z))
        );
        return 1;
    }
    private static int addRefractionEffectWithStrength(CommandContext<FabricClientCommandSource> context) {
        // Get player position and add effect 10 blocks ahead
        net.minecraft.client.MinecraftClient client =
                net.minecraft.client.MinecraftClient.getInstance();
        if (client.player == null) {
            context.getSource().sendFeedback(Text.literal("§c[Error] No player found"));
            return 0;
        }

        Vec3d playerPos = client.player.getEyePos();
        Vec3d lookVec = client.player.getRotationVec(1.0f);
        Vec3d effectPos = playerPos.add(lookVec.multiply(10));

        // Get strength from command argument
        float strength = FloatArgumentType.getFloat(context, "strength");

        com.justheare.paperjjk_client.shader.RefractionEffectManager.addEffect(effectPos, 0.3f, strength, "DEBUG");

        context.getSource().sendFeedback(
                Text.literal("§d[PaperJJK Debug] §fAdded refraction effect at §e" +
                        String.format("(%.1f, %.1f, %.1f)", effectPos.x, effectPos.y, effectPos.z) +
                        " §fwith strength §a" + String.format("%.2f", strength))
        );
        return 1;
    }
    private static int inspectMethods(CommandContext<FabricClientCommandSource> context) {
        com.justheare.paperjjk_client.debug.GameRendererDebug.inspectMethods();
        context.getSource().sendFeedback(
            Text.literal("§e[PaperJJK Debug] §fGameRenderer methods logged to console")
        );
        return 1;
    }

    private static int clearRefractionEffects(CommandContext<FabricClientCommandSource> context) {
        com.justheare.paperjjk_client.shader.RefractionEffectManager.clearEffects();
        context.getSource().sendFeedback(
            Text.literal("§d[PaperJJK Debug] §fCleared all refraction effects")
        );
        return 1;
    }
}
