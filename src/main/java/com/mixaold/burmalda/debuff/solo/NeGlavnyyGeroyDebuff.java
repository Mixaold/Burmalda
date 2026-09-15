package com.mixaold.burmalda.debuff.solo;

import com.mixaold.burmalda.debuff.Debuff;
import com.mixaold.burmalda.debuff.DebuffType;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FireworkExplosionComponent;
import net.minecraft.component.type.FireworksComponent;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Random;

public class NeGlavnyyGeroyDebuff extends Debuff {

    private static final int CROWN_INTERVAL = 1200;
    private static final Random RANDOM = new Random();

    public NeGlavnyyGeroyDebuff() {
        super("ne_glavnyy_geroy", "Not the Main Character",
                "Did you think you were special?",
                DebuffType.SOLO);
    }

    @Override
    public void onStart(ServerPlayerEntity player) {
        crownRandom(player);
    }

    @Override
    public void onPlayerTick(ServerPlayerEntity player, int ticksActive) {
        if (ticksActive % CROWN_INTERVAL == 0) {
            crownRandom(player);
        }
    }

    private void crownRandom(ServerPlayerEntity player) {
        List<ServerPlayerEntity> others = com.mixaold.burmalda.util.BurmaldaCompat.server(player).getPlayerManager().getPlayerList()
                .stream().filter(p -> !p.getUuid().equals(player.getUuid())).toList();
        if (others.isEmpty()) return;

        ServerPlayerEntity chosen = others.get(RANDOM.nextInt(others.size()));
        ServerWorld world = com.mixaold.burmalda.util.BurmaldaCompat.world(chosen);
        double x = chosen.getX(), y = chosen.getY(), z = chosen.getZ();

        chosen.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 600, 0));
        world.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, x, y + 1, z, 30, 0.5, 0.5, 0.5, 0.1);
        world.playSound(null, x, y, z,
                SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.MASTER, 1f, 1f);

        for (int i = 0; i < 2; i++) {
            int color = RANDOM.nextInt(0x1000000);
            IntArrayList colors = new IntArrayList(new int[]{color});
            FireworkExplosionComponent explosion = new FireworkExplosionComponent(
                    FireworkExplosionComponent.Type.BURST, colors, new IntArrayList(), true, false);
            ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
            rocket.set(DataComponentTypes.FIREWORKS, new FireworksComponent(2, List.of(explosion)));
            FireworkRocketEntity firework = new FireworkRocketEntity(
                    world, x + RANDOM.nextDouble() - 0.5, y + 1, z + RANDOM.nextDouble() - 0.5, rocket);
            world.spawnEntity(firework);
        }

        com.mixaold.burmalda.util.BurmaldaCompat.server(player).getPlayerManager().broadcast(
                Text.translatableWithFallback("burmalda.chat.ne_glavnyy_geroy.chosen", "§6⭐ §e%s §6— The Chosen One!", chosen.getName().getString()), false);
        player.sendMessage(Text.translatableWithFallback("burmalda.chat.ne_glavnyy_geroy.not_you", "§8[Hero] §7That's not you."), false);
    }
}
