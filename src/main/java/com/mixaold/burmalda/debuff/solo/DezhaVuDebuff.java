package com.mixaold.burmalda.debuff.solo;

import com.mixaold.burmalda.debuff.Debuff;
import com.mixaold.burmalda.debuff.DebuffType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayDeque;

public class DezhaVuDebuff extends Debuff {

    private static final int SNAPSHOT_INTERVAL = 20;
    private static final int MAX_HISTORY = 20;
    // Random 15–30 s between rollbacks so the player gets a real window to actually move
    private static final int MIN_INTERVAL = 300;
    private static final int MAX_INTERVAL = 600;
    private static final java.util.Random RANDOM = new java.util.Random();

    private final ArrayDeque<Vec3d> history = new ArrayDeque<>();
    private int nextRollbackTick = 0;

    public DezhaVuDebuff() {
        super("dezhavu", "Déjà Vu",
                "Every 15-30 seconds you're rolled back in time",
                DebuffType.SOLO);
    }

    @Override
    public void onStart(ServerPlayerEntity player) {
        history.clear();
        nextRollbackTick = MIN_INTERVAL + RANDOM.nextInt(MAX_INTERVAL - MIN_INTERVAL + 1);
    }

    @Override
    public void onEnd(ServerPlayerEntity player) {
        history.clear();
    }

    @Override
    public void onPlayerTick(ServerPlayerEntity player, int ticksActive) {
        if (ticksActive % SNAPSHOT_INTERVAL == 0) {
            history.addLast(com.mixaold.burmalda.util.BurmaldaCompat.pos(player));
            while (history.size() > MAX_HISTORY) {
                history.pollFirst();
            }
        }

        if (ticksActive >= nextRollbackTick && !history.isEmpty()) {
            nextRollbackTick = ticksActive + MIN_INTERVAL + RANDOM.nextInt(MAX_INTERVAL - MIN_INTERVAL + 1);
            Vec3d target = history.pollFirst();
            ServerWorld world = com.mixaold.burmalda.util.BurmaldaCompat.world(player);
            com.mixaold.burmalda.util.BurmaldaCompat.teleport(player, world, target.x, target.y, target.z,
                    player.getYaw(), player.getPitch());
            player.sendMessage(Text.translatableWithFallback("burmalda.chat.dezhavu.rollback", "§b[Déjà Vu] §fRolled back."), false);
        }
    }
}
