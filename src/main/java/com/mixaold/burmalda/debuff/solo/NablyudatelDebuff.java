package com.mixaold.burmalda.debuff.solo;

import com.mixaold.burmalda.debuff.Debuff;
import com.mixaold.burmalda.debuff.DebuffType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.List;

public class NablyudatelDebuff extends Debuff {

    public NablyudatelDebuff() {
        super("nablyudatel", "Observer Effect",
                "While anyone — player or mob — watches you, you stand frozen",
                DebuffType.SOLO);
    }

    // Where each watched player was frozen — used to snap them back if they try to jump/drift away.
    private final java.util.Map<java.util.UUID, Vec3d> lockPos = new java.util.HashMap<>();

    @Override
    public void onPlayerTick(ServerPlayerEntity player, int ticksActive) {
        java.util.UUID id = player.getUuid();
        if (!isBeingWatched(player)) { lockPos.remove(id); return; }

        Vec3d lock = lockPos.get(id);
        if (lock == null) { lock = com.mixaold.burmalda.util.BurmaldaCompat.pos(player); lockPos.put(id, lock); }

        // TOTAL freeze: Slowness 127 kills walking, velocity is fully zeroed, and if they manage to
        // jump/drift off the spot we teleport them straight back — no escaping while watched.
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 5, 127, false, false, false));
        player.setVelocity(0, 0, 0);
        com.mixaold.burmalda.util.BurmaldaCompat.markVelocityDirty(player);

        Vec3d cur = com.mixaold.burmalda.util.BurmaldaCompat.pos(player);
        if (cur.distanceTo(lock) > 0.08) {
            // keep their look direction (so they can still glance around), just yank position back
            com.mixaold.burmalda.util.BurmaldaCompat.teleport(player,
                    com.mixaold.burmalda.util.BurmaldaCompat.world(player),
                    lock.x, lock.y, lock.z, player.getYaw(), player.getPitch());
        }

        if (ticksActive % 20 == 0) {
            player.sendMessage(Text.translatableWithFallback("burmalda.chat.nablyudatel.watched",
                    "§c[Observer Effect] §fYou're being watched!"), true);
        }
    }

    private boolean isBeingWatched(ServerPlayerEntity target) {
        ServerWorld world = com.mixaold.burmalda.util.BurmaldaCompat.world(target);
        // Other players looking at you
        List<ServerPlayerEntity> others = com.mixaold.burmalda.util.BurmaldaCompat.server(target).getPlayerManager().getPlayerList();
        for (ServerPlayerEntity observer : others) {
            if (observer.getUuid().equals(target.getUuid())) continue;
            if (watches(observer, target, world)) return true;
        }
        // Any mob looking at you
        Vec3d tp = com.mixaold.burmalda.util.BurmaldaCompat.pos(target);
        for (MobEntity mob : world.getEntitiesByClass(MobEntity.class, Box.of(tp, 64, 48, 64), m -> m.isAlive())) {
            if (watches(mob, target, world)) return true;
        }
        return false;
    }

    private boolean watches(LivingEntity observer, ServerPlayerEntity target, ServerWorld world) {
        double dist = observer.distanceTo(target);
        if (dist > 30) return false;
        Vec3d look = observer.getRotationVec(1.0f).normalize();
        Vec3d toTarget = target.getEyePos().subtract(observer.getEyePos()).normalize();
        if (look.dotProduct(toTarget) <= 0.5) return false;
        // Blocks break the gaze — require an unobstructed line of sight, eye to eye.
        return hasLineOfSight(observer, target, world);
    }

    private boolean hasLineOfSight(LivingEntity observer, ServerPlayerEntity target, ServerWorld world) {
        net.minecraft.util.hit.BlockHitResult hit = world.raycast(new net.minecraft.world.RaycastContext(
                observer.getEyePos(), target.getEyePos(),
                net.minecraft.world.RaycastContext.ShapeType.COLLIDER,
                net.minecraft.world.RaycastContext.FluidHandling.NONE,
                observer));
        return hit.getType() == net.minecraft.util.hit.HitResult.Type.MISS;
    }
}
