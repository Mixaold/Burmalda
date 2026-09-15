package com.mixaold.burmalda.debuff.solo;

import com.mixaold.burmalda.debuff.Debuff;
import com.mixaold.burmalda.debuff.DebuffType;
import net.minecraft.server.network.ServerPlayerEntity;

public class ObratnayaZhiznDebuff extends Debuff {

    private double prevX = Double.NaN;
    private double prevZ = Double.NaN;

    public ObratnayaZhiznDebuff() {
        super("obratnaya_zhizn", "Reverse Life",
                "Standing still — lose HP, walking — regenerate",
                DebuffType.SOLO);
    }

    @Override
    public void onStart(ServerPlayerEntity player) {
        prevX = player.getX();
        prevZ = player.getZ();
    }

    @Override
    public void onPlayerTick(ServerPlayerEntity player, int ticksActive) {
        if (ticksActive % 10 != 0) return;

        double curX = player.getX();
        double curZ = player.getZ();

        double dx = curX - prevX;
        double dz = curZ - prevZ;
        // порог 0.05 блока за 10 тиков — улавливает даже кроходьбу
        boolean moving = (dx * dx + dz * dz) > 0.0025;

        prevX = curX;
        prevZ = curZ;

        if (moving) {
            if (player.getHealth() < player.getMaxHealth()) {
                player.setHealth(Math.min(player.getMaxHealth(), player.getHealth() + 0.25f));
            }
        } else {
            if (player.getHealth() > 1f) {
                com.mixaold.burmalda.util.BurmaldaCompat.damage(player, com.mixaold.burmalda.util.BurmaldaCompat.world(player).getDamageSources().generic(), 0.25f);
            }
        }
    }
}
