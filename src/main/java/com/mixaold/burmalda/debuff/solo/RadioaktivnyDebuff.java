package com.mixaold.burmalda.debuff.solo;

import com.mixaold.burmalda.debuff.Debuff;
import com.mixaold.burmalda.debuff.DebuffType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Box;

import java.util.List;

public class RadioaktivnyDebuff extends Debuff {

    private static final int INTERVAL = 100; // 5 seconds

    public RadioaktivnyDebuff() {
        super("radioaktivnyy", "Radioactive",
                "Deals damage to everyone nearby every 5 seconds",
                DebuffType.SOLO);
    }

    @Override
    public void onStart(ServerPlayerEntity player) {
        player.setGlowing(true);
    }

    @Override
    public void onEnd(ServerPlayerEntity player) {
        player.setGlowing(false);
    }

    @Override
    public void onPlayerTick(ServerPlayerEntity player, int ticksActive) {
        if (ticksActive % 20 == 0) {
            player.setGlowing(true);
        }
        if (ticksActive % INTERVAL != 0) return;

        Box box = Box.of(com.mixaold.burmalda.util.BurmaldaCompat.pos(player), 8, 6, 8);
        List<LivingEntity> nearby = com.mixaold.burmalda.util.BurmaldaCompat.world(player).getEntitiesByClass(
                LivingEntity.class, box, e -> e.isAlive() && e != player);

        for (LivingEntity entity : nearby) {
            com.mixaold.burmalda.util.BurmaldaCompat.damage(entity, com.mixaold.burmalda.util.BurmaldaCompat.world(player).getDamageSources().generic(), 1.0f);
        }
    }
}
