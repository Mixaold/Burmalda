package com.mixaold.burmalda.debuff.solo;

import com.mixaold.burmalda.debuff.Debuff;
import com.mixaold.burmalda.debuff.DebuffType;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.TntEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;

import java.util.Random;

public class FeromonDebuff extends Debuff {

    private static final Random RANDOM = new Random();
    private static final int INTERVAL = 200; // every 10 seconds
    private static final int TNT_PER_WAVE = 3;
    private static final int FUSE = 60; // 3 seconds to fall

    public FeromonDebuff() {
        super("dozhdDinamita", "Dynamite Rain",
                "Every 10 sec TNT falls from the sky straight at you",
                DebuffType.SOLO);
    }

    @Override
    public void onStart(ServerPlayerEntity player) {
        player.sendMessage(Text.translatableWithFallback("burmalda.chat.dozhd_dinamita.start", "§c[💣 Dynamite] §fFirst quiet... then boom."), false);
    }

    @Override
    public void onPlayerTick(ServerPlayerEntity player, int ticksActive) {
        if (ticksActive % INTERVAL != 0) return;

        ServerWorld world = com.mixaold.burmalda.util.BurmaldaCompat.world(player);
        int px = (int) player.getX();
        int pz = (int) player.getZ();

        // Determine if player is underground: compare player Y to world surface Y
        int surfaceY = world.getTopY(Heightmap.Type.WORLD_SURFACE, px, pz);
        boolean underground = player.getBlockY() < surfaceY - 5;

        for (int i = 0; i < TNT_PER_WAVE; i++) {
            double ox = (RANDOM.nextDouble() - 0.5) * 6;
            double oz = (RANDOM.nextDouble() - 0.5) * 6;
            double spawnX = player.getX() + ox;
            double spawnZ = player.getZ() + oz;
            double spawnY;

            if (underground) {
                // Find ceiling just above player
                int ceiling = player.getBlockY() + 2;
                BlockPos checkPos = BlockPos.ofFloored(spawnX, ceiling, spawnZ);
                while (ceiling < (world.getBottomY() + world.getHeight()) && world.isAir(checkPos)) {
                    ceiling++;
                    checkPos = BlockPos.ofFloored(spawnX, ceiling, spawnZ);
                }
                spawnY = ceiling + 1;
            } else {
                // Spawn well above surface so it has time to fall
                spawnY = Math.max(surfaceY + 20, player.getY() + 20);
            }

            TntEntity tnt = com.mixaold.burmalda.util.BurmaldaCompat.create(EntityType.TNT, world);
            if (tnt == null) continue;
            tnt.setPosition(spawnX, spawnY, spawnZ);
            tnt.setFuse(FUSE);
            world.spawnEntity(tnt);
        }

        player.sendMessage(Text.translatableWithFallback("burmalda.chat.dozhd_dinamita.incoming", "§c[💣 Dynamite] §fParcel from the sky — accept delivery"), true);
    }
}
