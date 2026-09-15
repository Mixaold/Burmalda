package com.mixaold.burmalda.debuff.group;

import com.mixaold.burmalda.debuff.Debuff;
import com.mixaold.burmalda.debuff.DebuffType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;

public class KollektivnySonDebuff extends Debuff {

    public KollektivnySonDebuff() {
        super("son", "Group Levitation",
                "Everyone — players and mobs — floats in the air",
                DebuffType.GROUP);
    }

    @Override
    public void onGroupStart(MinecraftServer server) {
        applyEffects(server);
    }

    @Override
    public void onGroupTick(MinecraftServer server, int ticksActive) {
        // Re-apply every 10 seconds to keep effects going (and catch newly-spawned mobs)
        if (ticksActive % 200 == 0) applyEffects(server);
    }

    @Override
    public void onGroupEnd(MinecraftServer server) {
        forEachAffected(server, e -> {
            e.removeStatusEffect(StatusEffects.LEVITATION);
            e.removeStatusEffect(StatusEffects.SLOW_FALLING);
        });
    }

    private void applyEffects(MinecraftServer server) {
        forEachAffected(server, e -> {
            e.addStatusEffect(new StatusEffectInstance(StatusEffects.LEVITATION, 250, 1, false, true));
            e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 250, 0, false, true));
        });
    }

    // Players + every living mob around them.
    private void forEachAffected(MinecraftServer server, java.util.function.Consumer<LivingEntity> action) {
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            action.accept(p);
            ServerWorld world = com.mixaold.burmalda.util.BurmaldaCompat.world(p);
            Box box = Box.of(com.mixaold.burmalda.util.BurmaldaCompat.pos(p), 96, 64, 96);
            for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, box,
                    m -> m.isAlive() && !(m instanceof ServerPlayerEntity))) {
                action.accept(e);
            }
        }
    }
}
