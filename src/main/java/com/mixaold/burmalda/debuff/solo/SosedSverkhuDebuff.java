package com.mixaold.burmalda.debuff.solo;

import com.mixaold.burmalda.debuff.Debuff;
import com.mixaold.burmalda.debuff.DebuffType;
import net.minecraft.block.Blocks;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.Random;

public class SosedSverkhuDebuff extends Debuff {

    private static final int ACTIVITY_INTERVAL = 300; // 15 sec
    private static final int FOOTSTEP_INTERVAL = 160;  // 8 sec
    private static final Random RANDOM = new Random();

    // Activities: 0=drilling, 1=furniture, 2=party, 3=repair(anvil)
    private int nextActivity = 0;

    public SosedSverkhuDebuff() {
        super("sosed_sverhu", "Upstairs Neighbor",
                "Dana lives above you. He has no quiet hours.",
                DebuffType.SOLO);
    }

    @Override
    public void onStart(ServerPlayerEntity player) {
        nextActivity = RANDOM.nextInt(4);
        player.sendMessage(Text.translatableWithFallback("burmalda.chat.sosed_sverhu.start",
                "§c[Сосед сверху] §fНад вами живёт Даня."), false);
    }

    @Override
    public void onPlayerTick(ServerPlayerEntity player, int ticksActive) {
        ServerWorld world = com.mixaold.burmalda.util.BurmaldaCompat.world(player);

        // Ambient footsteps
        if (ticksActive % FOOTSTEP_INTERVAL == 0) {
            world.playSound(null, player.getX(), player.getY() + 4, player.getZ(),
                    SoundEvents.BLOCK_WOOD_STEP, SoundCategory.BLOCKS, 1.2f, 0.8f);
            world.playSound(null, player.getX() + RANDOM.nextDouble() * 2 - 1, player.getY() + 4,
                    player.getZ() + RANDOM.nextDouble() * 2 - 1,
                    SoundEvents.BLOCK_WOOD_STEP, SoundCategory.BLOCKS, 1.0f, 0.9f);
            world.playSound(null, player.getX(), player.getY() + 4, player.getZ(),
                    SoundEvents.BLOCK_WOOD_STEP, SoundCategory.BLOCKS, 0.8f, 1.0f);
        }

        // Warning 1 sec before activity
        if (ticksActive % ACTIVITY_INTERVAL == ACTIVITY_INTERVAL - 20) {
            spawnWarning(world, player);
        }

        // Main activity
        if (ticksActive % ACTIVITY_INTERVAL == 0 && ticksActive > 0) {
            doActivity(world, player, nextActivity);
            nextActivity = RANDOM.nextInt(4);
        }
    }

    private void spawnWarning(ServerWorld world, ServerPlayerEntity player) {
        for (int i = 0; i < 6; i++) {
            double ox = (RANDOM.nextDouble() - 0.5) * 2;
            double oz = (RANDOM.nextDouble() - 0.5) * 2;
            for (ServerPlayerEntity sp : world.getPlayers()) {
                com.mixaold.burmalda.util.BurmaldaCompat.spawnParticles(world, sp, ParticleTypes.CRIT, true,
                        player.getX() + ox, player.getY() + 6, player.getZ() + oz,
                        1, 0.1, 0.0, 0.1, 0.0);
            }
        }
    }

    private void doActivity(ServerWorld world, ServerPlayerEntity player, int activity) {
        double px = player.getX(), py = player.getY(), pz = player.getZ();
        switch (activity) {
            case 0 -> { // Drilling — gravel column
                world.playSound(null, px, py + 6, pz,
                        SoundEvents.BLOCK_GRAVEL_BREAK, SoundCategory.BLOCKS, 2.0f, 0.7f);
                for (int i = 0; i < 3 + RANDOM.nextInt(3); i++) {
                    double ox = (RANDOM.nextDouble() - 0.5) * 1.5;
                    double oz = (RANDOM.nextDouble() - 0.5) * 1.5;
                    FallingBlockEntity gravel = FallingBlockEntity.spawnFromBlock(world,
                            BlockPos.ofFloored(px + ox, py + 6, pz + oz),
                            Blocks.GRAVEL.getDefaultState());
                    gravel.setVelocity(0, -0.3, 0);
                }
                player.sendMessage(Text.translatableWithFallback("burmalda.chat.sosed_sverhu.drill",
                        "§8[Даня] §fПробую дырку. Буквально."), true);
            }
            case 1 -> { // Moving furniture — sand cluster
                world.playSound(null, px, py + 6, pz,
                        SoundEvents.BLOCK_STONE_BREAK, SoundCategory.BLOCKS, 2.0f, 0.4f);
                for (int i = 0; i < 6; i++) {
                    double ox = (RANDOM.nextDouble() - 0.5) * 3;
                    double oz = (RANDOM.nextDouble() - 0.5) * 3;
                    FallingBlockEntity sand = FallingBlockEntity.spawnFromBlock(world,
                            BlockPos.ofFloored(px + ox, py + 5, pz + oz),
                            Blocks.SAND.getDefaultState());
                    sand.setVelocity(ox * 0.05, -0.2, oz * 0.05);
                }
                player.sendMessage(Text.translatableWithFallback("burmalda.chat.sosed_sverhu.furniture",
                        "§8[Даня] §fПередвигаю диван. Чуть-чуть."), true);
            }
            case 2 -> { // Party — glass bottles
                world.playSound(null, px, py + 6, pz,
                        SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), SoundCategory.BLOCKS, 2.0f, 1.2f);
                for (ServerPlayerEntity sp : world.getPlayers()) {
                    com.mixaold.burmalda.util.BurmaldaCompat.spawnParticles(world, sp, ParticleTypes.SPORE_BLOSSOM_AIR, true,
                            px, py + 5, pz, 20, 1.5, 0.5, 1.5, 0.02);
                }
                // Drop several glass bottles
                for (int i = 0; i < 4 + RANDOM.nextInt(3); i++) {
                    double ox = (RANDOM.nextDouble() - 0.5) * 3;
                    double oz = (RANDOM.nextDouble() - 0.5) * 3;
                    net.minecraft.entity.ItemEntity bottle = new net.minecraft.entity.ItemEntity(
                            world, px + ox, py + 5, pz + oz,
                            new net.minecraft.item.ItemStack(net.minecraft.item.Items.GLASS_BOTTLE));
                    bottle.setVelocity(ox * 0.1, 0.1, oz * 0.1);
                    world.spawnEntity(bottle);
                }
                player.sendMessage(Text.translatableWithFallback("burmalda.chat.sosed_sverhu.party",
                        "§8[Даня] §fПятница!"), true);
            }
            case 3 -> { // Repair — anvil
                world.playSound(null, px, py + 7, pz,
                        SoundEvents.BLOCK_ANVIL_PLACE, SoundCategory.BLOCKS, 3.0f, 0.6f);
                for (ServerPlayerEntity sp : world.getPlayers()) {
                    com.mixaold.burmalda.util.BurmaldaCompat.spawnParticles(world, sp, ParticleTypes.CRIT, true,
                            px, py + 7, pz, 12, 0.3, 0.1, 0.3, 0.1);
                }
                // Spawn falling anvil
                FallingBlockEntity anvil = FallingBlockEntity.spawnFromBlock(world,
                        BlockPos.ofFloored(px, py + 7, pz),
                        Blocks.ANVIL.getDefaultState());
                anvil.setVelocity(0, -0.5, 0);
                player.sendMessage(Text.translatableWithFallback("burmalda.chat.sosed_sverhu.anvil",
                        "§c[Даня] §fОй."), false);
            }
        }

        // Every 3rd activity: water spill
        if (RANDOM.nextInt(3) == 0) {
            BlockPos waterPos = BlockPos.ofFloored(px, py + 3, pz);
            if (world.isInBuildLimit(waterPos) && world.isAir(waterPos)) {
                world.setBlockState(waterPos, Blocks.WATER.getDefaultState());
                // Schedule removal after 2 sec (40 ticks) — done manually via a delayed task flag
                // We just place it; it'll flow. In 40 ticks onPlayerTick will clean if needed.
                // For simplicity: leave water as is (it flows and then player can deal with it)
            }
            player.sendMessage(Text.translatableWithFallback("burmalda.chat.sosed_sverhu.water",
                    "§b[Даня] §fПролил чай. Щас уберу."), true);
        }
    }

    @Override
    public void onEnd(ServerPlayerEntity player) {
        player.sendMessage(Text.translatableWithFallback("burmalda.chat.sosed_sverhu.end",
                "§c[Сосед сверху] §fДаня ушёл. Тишина."), false);
    }
}
