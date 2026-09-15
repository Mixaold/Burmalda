package com.mixaold.burmalda.debuff.solo;

import com.mixaold.burmalda.debuff.Debuff;
import com.mixaold.burmalda.debuff.DebuffType;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class SlavikiDebuff extends Debuff {

    private static final int COUNT = 10;

    private final List<Integer> golemIds = new ArrayList<>();

    public SlavikiDebuff() {
        super("slaviki", "Slaviki",
                "Problems? They'll handle it.",
                DebuffType.SOLO);
    }

    @Override
    public void onStart(ServerPlayerEntity player) {
        golemIds.clear();
        ServerWorld world = com.mixaold.burmalda.util.BurmaldaCompat.world(player);

        for (int i = 0; i < COUNT; i++) {
            IronGolemEntity golem = com.mixaold.burmalda.util.BurmaldaCompat.create(EntityType.IRON_GOLEM, world);
            if (golem == null) continue;

            double angle = (i / (double) COUNT) * Math.PI * 2;
            double ox = Math.cos(angle) * 5;
            double oz = Math.sin(angle) * 5;
            golem.setPosition(player.getX() + ox, player.getY(), player.getZ() + oz);
            golem.setCustomName(Text.translatableWithFallback("burmalda.slaviki.golem_name", "§a[Slavik]"));
            golem.setCustomNameVisible(true);
            golem.setPersistent();

            world.spawnEntity(golem);
            golemIds.add(golem.getId());
        }

        player.sendMessage(Text.translatableWithFallback("burmalda.chat.slaviki.start",
                "§a[Slaviks] §fThey're here. And they've got your back."), false);
    }

    @Override
    public void onEnd(ServerPlayerEntity player) {
        // Славики остаются навсегда
    }

    @Override
    public void onPlayerTick(ServerPlayerEntity player, int ticksActive) {
        if (ticksActive % 5 != 0) return;

        ServerWorld world = com.mixaold.burmalda.util.BurmaldaCompat.world(player);
        List<ServerPlayerEntity> players = com.mixaold.burmalda.util.BurmaldaCompat.server(player).getPlayerManager().getPlayerList();

        for (int id : golemIds) {
            if (world.getEntityById(id) instanceof IronGolemEntity golem) {
                if (golem.getTarget() instanceof ServerPlayerEntity) {
                    golem.setTarget(null);
                }
                for (ServerPlayerEntity p : players) {
                    if (golem.getTarget() == p) {
                        golem.setTarget(null);
                    }
                }
                if (golem.getTarget() == null || !golem.getTarget().isAlive()) {
                    world.getEntitiesByClass(HostileEntity.class,
                                    golem.getBoundingBox().expand(24.0), e -> e.isAlive())
                            .stream()
                            .min(Comparator.comparingDouble(e -> e.squaredDistanceTo(golem)))
                            .ifPresent(golem::setTarget);
                }
            }
        }
    }
}
