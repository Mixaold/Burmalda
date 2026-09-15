package com.mixaold.burmalda.debuff.group;

import com.mixaold.burmalda.debuff.Debuff;
import com.mixaold.burmalda.debuff.DebuffType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.function.Consumer;

public class NulevayaGravitatsiyaDebuff extends Debuff {

    // Reduced gravity via the gravity attribute — syncs to every client (incl. the SP host)
    // and works on all versions, unlike server-side velocity pushes which never reached the host.
    private static final Identifier MOD_ID = Identifier.of("burmalda", "low_gravity");
    private static final double FACTOR = -0.65; // gravity reduced to ~35% → floaty, slight bounce

    public NulevayaGravitatsiyaDebuff() {
        super("nulevaya_gravit", "Low Gravity",
                "Reduced gravity — everyone floats and bounces",
                DebuffType.GROUP);
    }

    private void applyTo(LivingEntity e) {
        EntityAttributeInstance inst = e.getAttributeInstance(com.mixaold.burmalda.util.BurmaldaCompat.gravityAttribute());
        if (inst == null) return;
        if (inst.getModifier(MOD_ID) == null) {
            inst.addTemporaryModifier(new EntityAttributeModifier(
                    MOD_ID, FACTOR, EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }
        syncToSelf(e, inst);
    }

    private void removeFrom(LivingEntity e) {
        EntityAttributeInstance inst = e.getAttributeInstance(com.mixaold.burmalda.util.BurmaldaCompat.gravityAttribute());
        if (inst == null) return;
        inst.removeModifier(MOD_ID);
        syncToSelf(e, inst);
    }

    // A player's OWN attribute changes aren't always auto-synced reliably — the apply path survives
    // because onGroupTick re-runs it, but the single removal on end can be missed (so normal gravity
    // never came back until death/respawn). Push the attribute explicitly to guarantee the client updates.
    private void syncToSelf(LivingEntity e, EntityAttributeInstance inst) {
        if (e instanceof ServerPlayerEntity sp) {
            sp.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.EntityAttributesS2CPacket(
                    sp.getId(), java.util.List.of(inst)));
        }
    }

    /** Apply an action to every player AND every nearby mob — gravity hits mobs too, not just players. */
    private void forEachAffected(MinecraftServer server, Consumer<LivingEntity> action) {
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            action.accept(p);
            ServerWorld w = com.mixaold.burmalda.util.BurmaldaCompat.world(p);
            Vec3d pos = com.mixaold.burmalda.util.BurmaldaCompat.pos(p);
            for (MobEntity m : w.getEntitiesByClass(MobEntity.class, Box.of(pos, 96, 64, 96), LivingEntity::isAlive)) {
                action.accept(m);
            }
        }
    }

    @Override
    public void onGroupStart(MinecraftServer server) {
        forEachAffected(server, this::applyTo);
        server.getPlayerManager().broadcast(
                Text.translatableWithFallback("burmalda.chat.nulevaya_gravit.start",
                        "§b[Low Gravity] §fGravity drops — everyone floats!"), false);
    }

    @Override
    public void onGroupTick(MinecraftServer server, int ticksActive) {
        // Re-apply for players/mobs that joined or wandered in mid-debuff (cheap: only adds when missing).
        if (ticksActive % 20 == 0) {
            forEachAffected(server, this::applyTo);
        }
    }

    @Override
    public void onGroupEnd(MinecraftServer server) {
        forEachAffected(server, this::removeFrom);
        server.getPlayerManager().broadcast(
                Text.translatableWithFallback("burmalda.chat.nulevaya_gravit.end",
                        "§c[Low Gravity] §fGRAVITY RETURNS!"), false);
    }
}
