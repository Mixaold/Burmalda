package com.mixaold.burmalda.debuff.group;

import com.mixaold.burmalda.debuff.Debuff;
import com.mixaold.burmalda.debuff.DebuffType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Box;

import java.util.List;

public class GravitatsionnyShormDebuff extends Debuff {

    private static final int INTERVAL = 60; // every 3 seconds

    public GravitatsionnyShormDebuff() {
        super("gravitatsionny_shtorm", "Gravity Storm",
                "All mobs launch into the air",
                DebuffType.GROUP);
    }

    @Override
    public void onGroupTick(MinecraftServer server, int ticksActive) {
        if (ticksActive % INTERVAL != 0) return;

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            Box box = Box.of(com.mixaold.burmalda.util.BurmaldaCompat.pos(player), 100, 30, 100);
            List<MobEntity> mobs = com.mixaold.burmalda.util.BurmaldaCompat.world(player).getEntitiesByClass(
                    MobEntity.class, box, e -> e.isAlive() && !e.isRemoved());

            for (MobEntity mob : mobs) {
                mob.setVelocity(mob.getVelocity().x, 2.0, mob.getVelocity().z);
                com.mixaold.burmalda.util.BurmaldaCompat.markVelocityDirty(mob);
            }
        }
    }
}
