package com.mixaold.burmalda.command;

import com.mixaold.burmalda.debuff.Debuff;
import com.mixaold.burmalda.debuff.DebuffManager;
import com.mixaold.burmalda.debuff.DebuffRegistry;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.List;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class DebuffCommand {

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(
                literal("burmalda")
                    // No permission gate — the mod must work in worlds without cheats too.
                    .requires(source -> true)
                    .then(literal("list")
                        .executes(DebuffCommand::listDebuffs))
                    .then(literal("trigger")
                        .then(argument("id", StringArgumentType.word())
                            .executes(DebuffCommand::triggerDebuff)))
                    .then(literal("next")
                        .executes(DebuffCommand::nextDebuff))
                    .then(literal("repeat")
                        .executes(DebuffCommand::repeatDebuff))
                    .then(literal("reset")
                        .executes(DebuffCommand::resetMod))
                    .then(literal("menu")
                        .executes(DebuffCommand::openMenu))
            )
        );
    }

    private static int listDebuffs(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource src = ctx.getSource();
        List<Debuff> solo = DebuffRegistry.getSoloDebuffs();
        List<Debuff> group = DebuffRegistry.getGroupDebuffs();

        src.sendMessage(Text.literal("§6=== SOLO (" + solo.size() + ") ==="));
        for (Debuff d : solo) {
            src.sendMessage(Text.literal("§e" + d.getId() + " §7— " + d.getName()));
        }
        src.sendMessage(Text.literal("§d=== GROUP (" + group.size() + ") ==="));
        for (Debuff d : group) {
            src.sendMessage(Text.literal("§d" + d.getId() + " §7— " + d.getName()));
        }
        return 1;
    }

    private static int triggerDebuff(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource src = ctx.getSource();
        String id = StringArgumentType.getString(ctx, "id");

        Debuff solo = DebuffRegistry.getSoloById(id);
        if (solo != null) {
            var player = src.getPlayer();
            if (player == null) { src.sendError(Text.literal("Requires a player")); return 0; }
            DebuffManager.forceAssignSolo(src.getServer(), player, solo);
            src.sendMessage(Text.literal("§aApplied: §e" + solo.getName()));
            return 1;
        }

        Debuff group = DebuffRegistry.getGroupById(id);
        if (group != null) {
            DebuffManager.forceAssignGroup(src.getServer(), group);
            src.sendMessage(Text.literal("§aGroup started: §d" + group.getName()));
            return 1;
        }

        src.sendError(Text.literal("Debuff not found: " + id + " — use /burmalda list"));
        return 0;
    }

    private static int nextDebuff(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource src = ctx.getSource();
        ServerPlayerEntity player = src.getPlayer();
        if (player == null) { src.sendError(Text.literal("Requires a player")); return 0; }

        Debuff current = DebuffManager.getActiveDebuff(player.getUuid());
        List<Debuff> all = DebuffRegistry.getSoloDebuffs();
        if (all.isEmpty()) return 0;

        int idx = -1;
        if (current != null) {
            for (int i = 0; i < all.size(); i++) {
                if (all.get(i).getId().equals(current.getId())) { idx = i; break; }
            }
        }
        int nextIdx = (idx + 1) % all.size();
        Debuff next = all.get(nextIdx);
        DebuffManager.forceAssignSolo(src.getServer(), player, next);
        src.sendMessage(Text.literal("§aNext: §e" + next.getName()
                + " §7(" + (nextIdx + 1) + "/" + all.size() + ")"));
        return 1;
    }

    private static int repeatDebuff(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource src = ctx.getSource();
        ServerPlayerEntity player = src.getPlayer();
        if (player == null) { src.sendError(Text.literal("Requires a player")); return 0; }

        Debuff current = DebuffManager.getActiveDebuff(player.getUuid());
        if (current == null) { src.sendError(Text.literal("No active debuff")); return 0; }

        DebuffManager.forceAssignSolo(src.getServer(), player, current);
        src.sendMessage(Text.literal("§aRepeat: §e" + current.getName()));
        return 1;
    }

    private static int openMenu(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource src = ctx.getSource();
        ServerPlayerEntity player = src.getPlayer();
        if (player == null) { src.sendError(Text.literal("Requires a player")); return 0; }
        if (!com.mixaold.burmalda.chat.ActivationHandler.canUseMenu(player)) {
            src.sendError(Text.translatableWithFallback("burmalda.menu.noperm",
                    "У тебя нет доступа к меню Burmalda — оно только для того, кто запустил мод, и операторов."));
            return 0;
        }
        boolean canManage = com.mixaold.burmalda.chat.ActivationHandler.canManageWhitelist(player);
        com.mixaold.burmalda.network.BurmaldaNetwork.sendOpenMenu(player, canManage,
                com.mixaold.burmalda.menu.MenuWhitelist.csv());
        return 1;
    }

    private static int resetMod(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource src = ctx.getSource();
        DebuffManager.forceReset(src.getServer());
        src.sendMessage(Text.translatableWithFallback("burmalda.chat.reset", "§aMod reset. All debuffs cleared."));
        return 1;
    }
}
