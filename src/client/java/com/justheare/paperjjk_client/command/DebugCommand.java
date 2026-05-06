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
                .then(literal("simpledomain")
                    .executes(DebugCommand::toggleSimpleDomain)
                )
                .then(literal("particle")
                    .executes(DebugCommand::toggleParticleTest)
                )
                .then(literal("kai")
                    .executes(DebugCommand::toggleKaiSlash)
                )
                .then(literal("ambientslash")
                    .executes(DebugCommand::toggleAmbientSlash)
                    .then(argument("radius", FloatArgumentType.floatArg(1f, 300f))
                        .executes(DebugCommand::setAmbientSlashRadius)
                    )
                )
                .then(literal("charge")
                    .executes(DebugCommand::toggleCharge)
                )
                .then(literal("thermobaric")
                    .executes(DebugCommand::triggerThermobaric)
                    .then(argument("radius", FloatArgumentType.floatArg(5f, 300f))
                        .executes(DebugCommand::triggerThermobaricWithRadius)
                    )
                )
                .then(literal("barrier")
                    .executes(DebugCommand::toggleBarrier)
                    .then(argument("power", FloatArgumentType.floatArg(0.0f, 1.0f))
                        .executes(DebugCommand::toggleBarrierWithPower)
                        .then(argument("radius", FloatArgumentType.floatArg(1f, 50f))
                            .executes(DebugCommand::toggleBarrierWithPowerRadius)
                        )
                    )
                )
                .then(literal("barrierhit")
                    .executes(DebugCommand::triggerBarrierHit)
                )
        );
    }

    /** Toggle flag — PaperJJKClientClient tick handler reads this to spawn particles */
    public static volatile boolean particleTestActive = false;

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
        com.justheare.paperjjk_client.DebugConfig.log("DebugCommand", "addRefractionEffect() CALLED");

        // Get player position and add effect 10 blocks ahead
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client.player == null) {
            com.justheare.paperjjk_client.DebugConfig.log("DebugCommand", "ERROR: No player found");
            context.getSource().sendFeedback(Text.literal("§c[Error] No player found"));
            return 0;
        }

        Vec3d playerPos = client.player.getEyePos();
        Vec3d lookVec = client.player.getRotationVec(1.0f);
        Vec3d effectPos = playerPos.add(lookVec.multiply(10));

        // Generate unique ID for this effect
        String uniqueId = "DEBUG_" + System.currentTimeMillis() + "_" + System.nanoTime();

        com.justheare.paperjjk_client.DebugConfig.log("DebugCommand",
            "Adding refraction effect at: " + String.format("(%.1f, %.1f, %.1f)", effectPos.x, effectPos.y, effectPos.z) +
            " with ID: " + uniqueId);

        com.justheare.paperjjk_client.shader.RefractionEffectManager.addEffect(effectPos, 0.3f, 1.0f, "DEBUG", uniqueId);

        com.justheare.paperjjk_client.DebugConfig.log("DebugCommand", "Effect added successfully");

        context.getSource().sendFeedback(
            Text.literal("§d[PaperJJK Debug] §fAdded refraction effect #" + uniqueId + " at §e" +
                String.format("(%.1f, %.1f, %.1f)", effectPos.x, effectPos.y, effectPos.z))
        );
        return 1;
    }
    private static int addRefractionEffectWithStrength(CommandContext<FabricClientCommandSource> context) {
        com.justheare.paperjjk_client.DebugConfig.log("DebugCommand", "addRefractionEffectWithStrength() CALLED");

        // Get player position and add effect 10 blocks ahead
        net.minecraft.client.MinecraftClient client =
                net.minecraft.client.MinecraftClient.getInstance();
        if (client.player == null) {
            com.justheare.paperjjk_client.DebugConfig.log("DebugCommand", "ERROR: No player found");
            context.getSource().sendFeedback(Text.literal("§c[Error] No player found"));
            return 0;
        }

        Vec3d playerPos = client.player.getEyePos();
        Vec3d lookVec = client.player.getRotationVec(1.0f);
        Vec3d effectPos = playerPos.add(lookVec.multiply(10));

        // Get strength from command argument
        float strength = FloatArgumentType.getFloat(context, "strength");

        // Generate unique ID for this effect
        String uniqueId = "DEBUG_" + System.currentTimeMillis() + "_" + System.nanoTime();

        com.justheare.paperjjk_client.DebugConfig.log("DebugCommand",
            "Adding refraction effect at: " + String.format("(%.1f, %.1f, %.1f)", effectPos.x, effectPos.y, effectPos.z) +
            " with strength: " + strength + ", ID: " + uniqueId);

        com.justheare.paperjjk_client.shader.RefractionEffectManager.addEffect(effectPos, 0.3f, strength, "DEBUG", uniqueId);

        com.justheare.paperjjk_client.DebugConfig.log("DebugCommand", "Effect added successfully");

        context.getSource().sendFeedback(
                Text.literal("§d[PaperJJK Debug] §fAdded refraction effect #" + uniqueId + " at §e" +
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

    private static int toggleSimpleDomain(CommandContext<FabricClientCommandSource> context) {
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client.player == null) {
            context.getSource().sendFeedback(Text.literal("§c[Error] No player found"));
            return 0;
        }

        if (com.justheare.paperjjk_client.shader.DomainEffectManager.hasActiveDomains()) {
            com.justheare.paperjjk_client.shader.DomainEffectManager.deactivate(client.player.getUuid());
            context.getSource().sendFeedback(
                Text.literal("§d[PaperJJK] §f간이영역 §cDEACTIVATED")
            );
        } else {
            // Caster feet = player position (bottom of bounding box)
            Vec3d feetPos = new Vec3d(client.player.getX(), client.player.getY(), client.player.getZ());
            com.justheare.paperjjk_client.shader.DomainEffectManager.activate(feetPos, client.player.getUuid());
            context.getSource().sendFeedback(
                Text.literal("§d[PaperJJK] §f간이영역 §aACTIVATED §fat §e" +
                    String.format("(%.1f, %.1f, %.1f)", feetPos.x, feetPos.y, feetPos.z))
            );
        }
        return 1;
    }

    private static int toggleParticleTest(CommandContext<FabricClientCommandSource> context) {
        particleTestActive = !particleTestActive;
        context.getSource().sendFeedback(
            Text.literal("§d[PaperJJK] §f파티클 테스트 §" + (particleTestActive ? "aON" : "cOFF"))
        );
        return 1;
    }

    private static int toggleKaiSlash(CommandContext<FabricClientCommandSource> context) {
        com.justheare.paperjjk_client.shader.KaiSlashEffectManager.toggleDebug();
        boolean active = com.justheare.paperjjk_client.shader.KaiSlashEffectManager.isDebugActive();
        context.getSource().sendFeedback(
            Text.literal("§d[PaperJJK] §f참격(해) 효과 §" + (active ? "aON" : "cOFF"))
        );
        return 1;
    }

    private static int toggleAmbientSlash(CommandContext<FabricClientCommandSource> context) {
        boolean active = com.justheare.paperjjk_client.shader.AmbientKaiSlashManager.hasActiveDomains();
        if (active) {
            com.justheare.paperjjk_client.shader.AmbientKaiSlashManager.clearAll();
        } else {
            // 디버그용 도메인 생성
            net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
            if (mc.player != null) {
                net.minecraft.util.math.Vec3d center =
                    new net.minecraft.util.math.Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
                java.util.UUID debugId = java.util.UUID.fromString("00000000-0000-0000-0000-000000000001");
                com.justheare.paperjjk_client.shader.AmbientKaiSlashManager.setDomain(debugId, center, 30f);
            }
        }
        boolean nowActive = com.justheare.paperjjk_client.shader.AmbientKaiSlashManager.hasActiveDomains();
        context.getSource().sendFeedback(
            Text.literal("§d[PaperJJK] §f결없영 참격 효과 §" + (nowActive ? "aON" : "cOFF"))
        );
        return 1;
    }

    private static int setAmbientSlashRadius(CommandContext<FabricClientCommandSource> context) {
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client.player == null) {
            context.getSource().sendFeedback(Text.literal("§c[Error] No player found"));
            return 0;
        }
        float radius = FloatArgumentType.getFloat(context, "radius");
        Vec3d center = new Vec3d(client.player.getX(), client.player.getY(), client.player.getZ());
        java.util.UUID debugId = java.util.UUID.fromString("00000000-0000-0000-0000-000000000001");
        com.justheare.paperjjk_client.shader.AmbientKaiSlashManager.setDomain(debugId, center, radius);
        context.getSource().sendFeedback(
            Text.literal("§d[PaperJJK] §f결없영 반경 §a" + String.format("%.1f", radius) +
                "§f블록, 중심 §e" + String.format("(%.1f, %.1f, %.1f)", center.x, center.y, center.z))
        );
        return 1;
    }

    private static int toggleCharge(CommandContext<FabricClientCommandSource> context) {
        com.justheare.paperjjk_client.shader.MizushiChargeEffectManager.toggleDebug();
        boolean active = com.justheare.paperjjk_client.shader.MizushiChargeEffectManager.isDebugMode();
        context.getSource().sendFeedback(
            Text.literal("§d[PaperJJK] §f충전 효과 (0,150,0) §" + (active ? "aON (루핑)" : "cOFF"))
        );
        return 1;
    }

    private static int triggerThermobaric(CommandContext<FabricClientCommandSource> context) {
        return triggerThermobaricImpl(context, 50f);
    }

    private static int triggerThermobaricWithRadius(CommandContext<FabricClientCommandSource> context) {
        float radius = FloatArgumentType.getFloat(context, "radius");
        return triggerThermobaricImpl(context, radius);
    }

    private static int triggerThermobaricImpl(CommandContext<FabricClientCommandSource> context, float radius) {
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client.player == null) {
            context.getSource().sendFeedback(Text.literal("§c[Error] No player found"));
            return 0;
        }
        Vec3d blastPos = new Vec3d(0, 100, 0);

        if (com.justheare.paperjjk_client.shader.MizushiThermobaricManager.isActive()) {
            com.justheare.paperjjk_client.shader.MizushiThermobaricManager.stop();
            context.getSource().sendFeedback(
                Text.literal("§d[PaperJJK] §f열압력탄 §cSTOPPED")
            );
        } else {
            com.justheare.paperjjk_client.shader.MizushiThermobaricManager.trigger(blastPos, radius);
            float durationSec = (4000f + radius * 20f) / 1000f;
            context.getSource().sendFeedback(
                Text.literal("§d[PaperJJK] §f열압력탄 §aTRIGGERED §f반경 §a" +
                    String.format("%.0f", radius) + "블록 §fat §e" +
                    String.format("(%.1f, %.1f, %.1f)", blastPos.x, blastPos.y, blastPos.z) +
                    " §f(§e" + String.format("%.1f", durationSec) + "s§f)")
            );
        }
        return 1;
    }

    private static int toggleBarrier(CommandContext<FabricClientCommandSource> context) {
        return toggleBarrierImpl(context, 0.5f, 5.0f);
    }

    private static int toggleBarrierWithPower(CommandContext<FabricClientCommandSource> context) {
        float power = FloatArgumentType.getFloat(context, "power");
        return toggleBarrierImpl(context, power, 5.0f);
    }

    private static int toggleBarrierWithPowerRadius(CommandContext<FabricClientCommandSource> context) {
        float power  = FloatArgumentType.getFloat(context, "power");
        float radius = FloatArgumentType.getFloat(context, "radius");
        return toggleBarrierImpl(context, power, radius);
    }

    private static int toggleBarrierImpl(CommandContext<FabricClientCommandSource> context,
                                         float power, float radius) {
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client.player == null) {
            context.getSource().sendFeedback(Text.literal("§c[Error] No player found"));
            return 0;
        }
        if (com.justheare.paperjjk_client.shader.PassiveBarrierManager.isActive()) {
            com.justheare.paperjjk_client.shader.PassiveBarrierManager.deactivate();
            context.getSource().sendFeedback(
                Text.literal("§d[PaperJJK] §f배리어 §cDEACTIVATED")
            );
        } else {
            Vec3d pos = new Vec3d(client.player.getX(),
                client.player.getY() + client.player.getHeight() / 2.0,
                client.player.getZ());
            com.justheare.paperjjk_client.shader.PassiveBarrierManager.activate(pos, radius, power);
            context.getSource().sendFeedback(
                Text.literal("§d[PaperJJK] §f배리어 §aACTIVATED §f| power §e" +
                    String.format("%.2f", power) +
                    " §f| radius §e" + String.format("%.1f", radius) +
                    " §f| center §e" + String.format("(%.1f, %.1f, %.1f)", pos.x, pos.y, pos.z))
            );
        }
        return 1;
    }

    private static int triggerBarrierHit(CommandContext<FabricClientCommandSource> context) {
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (!com.justheare.paperjjk_client.shader.PassiveBarrierManager.isActive()) {
            context.getSource().sendFeedback(
                Text.literal("§c[PaperJJK] 배리어가 비활성 상태입니다. 먼저 /jjkdebug barrier 실행")
            );
            return 0;
        }
        if (client.player == null) {
            context.getSource().sendFeedback(Text.literal("§c[Error] No player found"));
            return 0;
        }
        // 플레이어 → 배리어 중심 방향으로 표면 충돌 지점 계산
        Vec3d center    = com.justheare.paperjjk_client.shader.PassiveBarrierManager.center;
        float radius    = com.justheare.paperjjk_client.shader.PassiveBarrierManager.radius;
        Vec3d playerPos = new Vec3d(client.player.getX(),
            client.player.getY() + client.player.getHeight() / 2.0,
            client.player.getZ());
        Vec3d dir       = center.subtract(playerPos);
        Vec3d hitDir    = dir.lengthSquared() > 0.0001 ? dir.normalize() : new Vec3d(0, 0, 1);
        Vec3d hitPos    = center.add(hitDir.negate().multiply(radius));
        com.justheare.paperjjk_client.shader.PassiveBarrierManager.addRipple(hitPos, 1.0f);
        context.getSource().sendFeedback(
            Text.literal("§d[PaperJJK] §f배리어 충돌 파동 §a+1 §fat §e" +
                String.format("(%.1f, %.1f, %.1f)", hitPos.x, hitPos.y, hitPos.z))
        );
        return 1;
    }

    private static int clearRefractionEffects(CommandContext<FabricClientCommandSource> context) {
        com.justheare.paperjjk_client.DebugConfig.log("DebugCommand", "clearRefractionEffects() CALLED");

        com.justheare.paperjjk_client.shader.RefractionEffectManager.clearEffects();

        com.justheare.paperjjk_client.DebugConfig.log("DebugCommand", "All effects cleared");

        context.getSource().sendFeedback(
            Text.literal("§d[PaperJJK Debug] §fCleared all refraction effects")
        );
        return 1;
    }
}
