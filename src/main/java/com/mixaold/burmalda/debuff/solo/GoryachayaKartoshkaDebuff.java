package com.mixaold.burmalda.debuff.solo;

import com.mixaold.burmalda.advancement.BurmaldaAdvancements;
import com.mixaold.burmalda.debuff.Debuff;
import com.mixaold.burmalda.debuff.DebuffManager;
import com.mixaold.burmalda.debuff.DebuffType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.world.World;

public class GoryachayaKartoshkaDebuff extends Debuff {

    // CYCLE_TICKS = 3600, last 30 sec = ticks 3000+
    private static final int PANIC_START    = 3000; // 30 sec before end
    private static final int PANIC_INTERVAL = 100;  // broadcast every 5 sec during panic
    public static final String POTATO_TAG = "burmalda_hot_potato";

    public GoryachayaKartoshkaDebuff() {
        super("goryachaya_kartoshka", "Hot Potato",
                "Passes to others — whoever holds it last loses HP",
                DebuffType.SOLO);
    }

    @Override
    public void onStart(ServerPlayerEntity player) {
        givePotatoTo(player);
        BurmaldaAdvancements.trigger(player, BurmaldaAdvancements.HOT_POTATO);
        player.sendMessage(Text.translatableWithFallback("burmalda.chat.goryachaya_kartoshka.take", "§6[Hot Potato] §fPOTATO IS YOURS! THROW IT TO SOMEONE NOW"), false);
    }

    @Override
    public void onEnd(ServerPlayerEntity player) {
        ServerPlayerEntity holder = findHolder(player);
        if (holder != null) {
            removePotatoFrom(holder);
            com.mixaold.burmalda.util.BurmaldaCompat.world(holder).createExplosion(
                    null, holder.getX(), holder.getY(), holder.getZ(),
                    2.0f, false, World.ExplosionSourceType.TNT);
            holder.sendMessage(Text.translatableWithFallback("burmalda.chat.goryachaya_kartoshka.explode", "§c[Hot Potato] §fKABOOM! The potato blew up..."), false);
            com.mixaold.burmalda.util.BurmaldaCompat.server(holder).getPlayerManager().broadcast(
                    Text.translatableWithFallback("burmalda.chat.goryachaya_kartoshka.blame", "§6[Burmalda] §e%s§f blew up the potato!", holder.getName().getString()), false);
        }
    }

    @Override
    public void onPlayerTick(ServerPlayerEntity player, int ticksActive) {
        if (ticksActive < PANIC_START) return;
        if (ticksActive % PANIC_INTERVAL != 0) return;

        ServerPlayerEntity holder = findHolder(player);
        if (holder == null) return;

        com.mixaold.burmalda.util.BurmaldaCompat.server(player).getPlayerManager().broadcast(
                Text.translatableWithFallback("burmalda.chat.goryachaya_kartoshka.with", "§c[☢ Hot Potato] §fPOTATO IS WITH §e%s§f! LESS THAN 30 SECONDS!", holder.getName().getString()), false);
    }

    /** Called from PlayerEntityMixin when any player finishes eating the hot potato. */
    public static void onPlayerAtePotato(ServerPlayerEntity player) {
        com.mixaold.burmalda.util.BurmaldaCompat.world(player).createExplosion(
                null, player.getX(), player.getY(), player.getZ(),
                3.0f, false, World.ExplosionSourceType.TNT);
        player.sendMessage(Text.translatableWithFallback("burmalda.chat.goryachaya_kartoshka.ate", "§4[Hot Potato] §cYOU ATE IT. WHY?"), false);
        com.mixaold.burmalda.util.BurmaldaCompat.server(player).getPlayerManager().broadcast(
                Text.translatableWithFallback("burmalda.chat.goryachaya_kartoshka.ate_broadcast", "§6[Burmalda] §e%s §fate the hot potato and exploded!", player.getName().getString()), false);
        DebuffManager.markDebuffFailed(com.mixaold.burmalda.util.BurmaldaCompat.server(player), "goryachaya_kartoshka");
    }

    /**
     * Called from PlayerEntityMixin whenever a player damages another player.
     * If the attacker is holding the hot potato, it jumps to the victim ("tag by touch").
     * Works regardless of who carries the debuff — it follows the ITEM, not the debuff.
     */
    public static boolean tryTransferOnHit(ServerPlayerEntity attacker, ServerPlayerEntity victim) {
        if (attacker == null || victim == null || attacker == victim) return false;
        for (int i = 0; i < attacker.getInventory().size(); i++) {
            ItemStack stack = attacker.getInventory().getStack(i);
            if (isHotPotato(stack)) {
                attacker.getInventory().setStack(i, ItemStack.EMPTY);
                victim.getInventory().insertStack(stack);
                victim.sendMessage(Text.translatableWithFallback("burmalda.chat.goryachaya_kartoshka.take", "§6[Hot Potato] §fPOTATO IS YOURS! THROW IT TO SOMEONE NOW"), false);
                com.mixaold.burmalda.util.BurmaldaCompat.server(attacker).getPlayerManager().broadcast(
                        Text.translatableWithFallback("burmalda.chat.goryachaya_kartoshka.passed", "§6[Hot Potato] §e%s§f passed the potato to §e%s§f!", attacker.getName().getString(), victim.getName().getString()), false);
                return true;
            }
        }
        return false;
    }

    private void givePotatoTo(ServerPlayerEntity player) {
        ItemStack potato = new ItemStack(Items.BAKED_POTATO);
        potato.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§6Hot Potato §c(TICK-TOCK)"));

        NbtCompound nbt = new NbtCompound();
        nbt.putBoolean(POTATO_TAG, true);
        potato.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));

        player.getInventory().insertStack(potato);
    }

    private void removePotatoFrom(ServerPlayerEntity player) {
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            NbtComponent data;
            if (!stack.isEmpty()
                    && (data = stack.get(DataComponentTypes.CUSTOM_DATA)) != null
                    && com.mixaold.burmalda.util.BurmaldaCompat.nbtGetBoolean(data.copyNbt(), POTATO_TAG)) {
                player.getInventory().setStack(i, ItemStack.EMPTY);
                return;
            }
        }
    }

    /** Scans all online players to find who actually holds the potato item. */
    private ServerPlayerEntity findHolder(ServerPlayerEntity fallback) {
        for (ServerPlayerEntity p : com.mixaold.burmalda.util.BurmaldaCompat.server(fallback).getPlayerManager().getPlayerList()) {
            if (hasHotPotato(p)) return p;
        }
        return null;
    }

    public static boolean isHotPotato(ItemStack stack) {
        NbtComponent data;
        return !stack.isEmpty()
                && (data = stack.get(DataComponentTypes.CUSTOM_DATA)) != null
                && com.mixaold.burmalda.util.BurmaldaCompat.nbtGetBoolean(data.copyNbt(), POTATO_TAG);
    }

    private static boolean hasHotPotato(ServerPlayerEntity player) {
        for (int i = 0; i < player.getInventory().size(); i++) {
            if (isHotPotato(player.getInventory().getStack(i))) return true;
        }
        return false;
    }
}
