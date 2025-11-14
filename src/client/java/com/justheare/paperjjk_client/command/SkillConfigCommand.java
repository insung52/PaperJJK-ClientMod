package com.justheare.paperjjk_client.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client-side skill configuration command
 * Usage: /jjkconfig or /jjkskill
 *
 * Opens a UI for configuring technique slots
 * TODO: Implement actual UI screen
 */
public class SkillConfigCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger("PaperJJK-Command");

    /**
     * Register the command
     */
    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        // Primary command: /jjkconfig
        dispatcher.register(ClientCommandManager.literal("jjkconfig")
            .executes(SkillConfigCommand::openSkillConfig));

        // Alias: /jjkskill
        dispatcher.register(ClientCommandManager.literal("jjkskill")
            .executes(SkillConfigCommand::openSkillConfig));

        LOGGER.info("Skill configuration commands registered: /jjkconfig, /jjkskill");
    }

    /**
     * Command execution: Open skill configuration UI
     */
    private static int openSkillConfig(CommandContext<FabricClientCommandSource> context) {
        MinecraftClient client = MinecraftClient.getInstance();

        // Execute on main thread
        client.execute(() -> {
            openSkillConfigUI(client);
        });

        return 1; // Success
    }

    /**
     * Open skill configuration UI
     * TODO: Implement actual UI screen
     */
    private static void openSkillConfigUI(MinecraftClient client) {
        if (client.player == null) {
            LOGGER.warn("Cannot open skill config UI: player is null");
            return;
        }

        // Placeholder: Send message to player
        client.player.sendMessage(
            Text.literal("§e§l[PaperJJK] Skill Configuration")
                .append(Text.literal("\n§7Configure your technique slots here."))
                .append(Text.literal("\n§cUI Coming Soon!")),
            false
        );

        LOGGER.info("Skill configuration UI opened (placeholder)");

        // TODO: Create and open actual skill configuration screen
        // Example:
        // client.setScreen(new SkillConfigScreen());
    }
}
