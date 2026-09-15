package com.mixaold.burmalda.debuff.group;

import com.mixaold.burmalda.debuff.Debuff;
import com.mixaold.burmalda.debuff.DebuffType;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

public class KorporativDebuff extends Debuff {

    public KorporativDebuff() {
        super("korporativ", "Office Party",
                "Nausea + speed + jump boost + slowness",
                DebuffType.GROUP);
    }

    @Override
    public void onGroupStart(MinecraftServer server) {
        applyEffects(server);
    }

    @Override
    public void onGroupTick(MinecraftServer server, int ticksActive) {
        if (ticksActive % 200 == 0) applyEffects(server);
    }

    @Override
    public void onGroupEnd(MinecraftServer server) {
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            p.removeStatusEffect(StatusEffects.NAUSEA);
            p.removeStatusEffect(StatusEffects.SPEED);
            p.removeStatusEffect(StatusEffects.JUMP_BOOST);
            p.removeStatusEffect(StatusEffects.SLOWNESS);
        }
    }

    private void applyEffects(MinecraftServer server) {
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 250, 0, false, true));
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 250, 1, false, true));
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.JUMP_BOOST, 250, 2, false, true));
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 250, 1, false, true));
        }
    }
}
