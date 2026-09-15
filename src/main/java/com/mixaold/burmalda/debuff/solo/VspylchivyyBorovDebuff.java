package com.mixaold.burmalda.debuff.solo;

import com.mixaold.burmalda.debuff.Debuff;
import com.mixaold.burmalda.debuff.DebuffType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;

public class VspylchivyyBorovDebuff extends Debuff {

    private boolean exploding = false;
    private int cooldown = 0;

    public VspylchivyyBorovDebuff() {
        super("vspylchivyy_borov", "Explosive Boar",
                "Took damage — explosion. Bystanders weren't asked",
                DebuffType.SOLO);
    }

    @Override
    public void onEnd(ServerPlayerEntity player) {
        exploding = false;
        cooldown = 0;
    }

    @Override
    public void onPlayerTick(ServerPlayerEntity player, int ticksActive) {
        if (cooldown > 0) cooldown--;
    }

    @Override
    public boolean onDamage(ServerPlayerEntity player, float amount) {
        if (exploding || cooldown > 0) return false;
        exploding = true;
        cooldown = 25; // 1.25s cooldown to prevent chain explosions
        ServerWorld world = com.mixaold.burmalda.util.BurmaldaCompat.world(player);
        world.createExplosion(null,
                player.getX(), player.getY(), player.getZ(),
                3.0f, true, World.ExplosionSourceType.TNT);
        exploding = false;
        return false;
    }
}
