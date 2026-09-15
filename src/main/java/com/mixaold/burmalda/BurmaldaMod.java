package com.mixaold.burmalda;

import com.mixaold.burmalda.advancement.BurmaldaAdvancements;
import com.mixaold.burmalda.chat.ActivationHandler;
import com.mixaold.burmalda.command.DebuffCommand;
import com.mixaold.burmalda.debuff.Debuff;
import com.mixaold.burmalda.debuff.DebuffManager;
import com.mixaold.burmalda.debuff.DebuffRegistry;
import com.mixaold.burmalda.debuff.group.MagazinZakrytDebuff;
import com.mixaold.burmalda.debuff.solo.NetDebuff;
import com.mixaold.burmalda.network.BurmaldaNetwork;
import com.mixaold.burmalda.timer.DebuffTimer;
import com.mixaold.burmalda.util.BurmaldaLogger;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.BlockItem;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;

public class BurmaldaMod implements ModInitializer {

    public static final String MOD_ID = "burmalda";

    @Override
    public void onInitialize() {
        BurmaldaLogger.init(FabricLoader.getInstance().getGameDir());
        BurmaldaLogger.info("Initializing Burmalda mod...");

        DebuffRegistry.register();
        BurmaldaAdvancements.register();
        BurmaldaNetwork.registerServer();
        DebuffTimer.register();
        ActivationHandler.register();
        DebuffCommand.register();

        registerContainerBlock();
        registerPlayerJoin();
        registerBlockBreak();

        BurmaldaLogger.info("Burmalda mod initialized. " +
                DebuffRegistry.getSoloDebuffs().size() + " solo + " +
                DebuffRegistry.getGroupDebuffs().size() + " group debuffs loaded.");
    }

    private void registerPlayerJoin() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                server.execute(() -> {
                    if (com.mixaold.burmalda.state.BurmaldaState.getServerState(server).modActive) {
                        BurmaldaAdvancements.trigger(handler.player, BurmaldaAdvancements.WELCOME);
                    }
                    DebuffManager.tryAddToObshchaga(server, handler.player);
                }));

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                DebuffManager.onPlayerDisconnect(handler.player));
    }

    private void registerBlockBreak() {
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (world.isClient()) return;
            if (!(player instanceof ServerPlayerEntity sp)) return;
            DebuffManager.onPlayerBlockBreak(sp, pos, state);
            // Pandora's Box: smashing the treasure chest counts as cracking it open.
            com.mixaold.burmalda.debuff.group.YashchikPandoryDebuff.handleBreak(
                    pos, sp, com.mixaold.burmalda.util.BurmaldaCompat.server(sp));
        });
    }

    /**
     * Blocks players from opening container screens while MagazinZakrytDebuff is active.
     * Returning FAIL from UseBlockCallback prevents the block's onUse() from running.
     */
    private void registerContainerBlock() {
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClient()) return ActionResult.PASS;
            if (!MagazinZakrytDebuff.containersBlocked) return ActionResult.PASS;

            var blockState = world.getBlockState(hitResult.getBlockPos());
            // createScreenHandlerFactory returns non-null only for interactive containers
            if (blockState.createScreenHandlerFactory(world, hitResult.getBlockPos()) != null) {
                player.sendMessage(Text.translatableWithFallback("burmalda.chat.magazin_zakryt.blocked", "§c[Store Closed] §fClosed. Come back tomorrow."), true);
                return ActionResult.FAIL;
            }
            return ActionResult.PASS;
        });

        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClient()) return ActionResult.PASS;
            if (!(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;
            var stack = player.getStackInHand(hand);
            if (stack.getItem() instanceof BlockItem) {
                Debuff d = DebuffManager.getActiveDebuff(sp.getUuid());
                if (d instanceof NetDebuff netDebuff) netDebuff.triggerExternal();
            }
            return ActionResult.PASS;
        });

        // Pandora's Box: flipping the hidden lever-key unlocks the chest; opening the unlocked chest
        // wins. A locked chest is blocked (FAIL cancels the interaction).
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClient()) return ActionResult.PASS;
            if (player instanceof ServerPlayerEntity sp
                    && com.mixaold.burmalda.debuff.group.YashchikPandoryDebuff.handleUse(
                        hitResult.getBlockPos(), sp, com.mixaold.burmalda.util.BurmaldaCompat.server(sp))) {
                return ActionResult.FAIL;
            }
            return ActionResult.PASS;
        });

        // Interrogation: clicking the ДА / НЕТ answer pads logs an answer.
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClient()) return ActionResult.PASS;
            if (player instanceof ServerPlayerEntity sp
                    && com.mixaold.burmalda.debuff.solo.DoprosDebuff.handleUse(hitResult.getBlockPos(), sp)) {
                return ActionResult.FAIL;
            }
            return ActionResult.PASS;
        });
    }
}
