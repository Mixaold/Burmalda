package com.mixaold.burmalda.util;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
//? if >=1.21.2
/*import net.minecraft.entity.SpawnReason;*/

/**
 * Cross-version shims for Minecraft API breaks between 1.21.1 and newer.
 * Call sites stay version-agnostic; the version branch lives here only.
 */
public final class BurmaldaCompat {
    private BurmaldaCompat() {}

    /**
     * Resolving a player's {@link ServerWorld} moved twice: {@code getServerWorld()} → covariant
     * {@code getWorld()} (1.21.8) → covariant {@code getEntityWorld()} (1.21.11).
     */
    public static ServerWorld world(ServerPlayerEntity player) {
        //? if >=1.21.11 {
        /*return player.getEntityWorld();
        *///? } else if >=1.21.8 {
        /*return player.getWorld();
        *///? } else {
        return player.getServerWorld();
        //? }
    }

    /** The gravity attribute field dropped its {@code GENERIC_} prefix in 1.21.2. */
    public static net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.attribute.EntityAttribute> gravityAttribute() {
        //? if >=1.21.2 {
        /*return net.minecraft.entity.attribute.EntityAttributes.GRAVITY;
        *///? } else
        return net.minecraft.entity.attribute.EntityAttributes.GENERIC_GRAVITY;
    }

    /** {@code Entity.getPos()} was renamed to {@code getEntityPos()} in 1.21.11. */
    public static net.minecraft.util.math.Vec3d pos(Entity e) {
        //? if >=1.21.11 {
        /*return e.getEntityPos();
        *///? } else
        return e.getPos();
    }

    /** {@code Entity.getWorld()} was renamed to {@code getEntityWorld()} in 1.21.11. */
    public static net.minecraft.world.World entityWorld(Entity e) {
        //? if >=1.21.11 {
        /*return e.getEntityWorld();
        *///? } else
        return e.getWorld();
    }

    /** {@code Entity.getServer()} was removed in 1.21.11; reach the server through the world instead. */
    public static net.minecraft.server.MinecraftServer server(Entity e) {
        //? if >=1.21.11 {
        /*return ((ServerWorld) e.getEntityWorld()).getServer();
        *///? } else
        return e.getServer();
    }

    /** The public {@code velocityModified} field was renamed to {@code velocityDirty} in 1.21.11. */
    public static void markVelocityDirty(Entity e) {
        //? if >=1.21.11 {
        /*e.velocityDirty = true;
        *///? } else
        e.velocityModified = true;
    }

    /**
     * Add velocity to a player AND push it to their own client. The entity tracker only sends
     * velocity packets to OTHER players watching the entity, so a server-side velocity change
     * never reaches the affected player themselves (knockback/pull did nothing for the local
     * host). Sending {@code EntityVelocityUpdateS2CPacket} to their own connection fixes that.
     */
    public static void pushVelocity(ServerPlayerEntity p, double ax, double ay, double az) {
        p.addVelocity(ax, ay, az);
        markVelocityDirty(p);
        p.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket(p));
    }

    /** {@code EntityType.create} gained a SpawnReason parameter in 1.21.2. */
    public static <T extends Entity> T create(EntityType<T> type, ServerWorld world) {
        //? if >=1.21.2 {
        /*return type.create(world, SpawnReason.EVENT);
        *///? } else
        return type.create(world);
    }

    /** {@code LivingEntity.damage} gained a ServerWorld parameter in 1.21.2; {@code getWorld} became {@code getEntityWorld} in 1.21.11. */
    public static boolean damage(LivingEntity entity, DamageSource source, float amount) {
        //? if >=1.21.11 {
        /*return entity.damage((ServerWorld) entity.getEntityWorld(), source, amount);
        *///? } else if >=1.21.2 {
        /*return entity.damage((ServerWorld) entity.getWorld(), source, amount);
        *///? } else {
        return entity.damage(source, amount);
        //? }
    }

    /** {@code LivingEntity.kill} gained a ServerWorld parameter in 1.21.2; {@code getWorld} became {@code getEntityWorld} in 1.21.11. */
    public static void kill(LivingEntity entity) {
        //? if >=1.21.11 {
        /*entity.kill((ServerWorld) entity.getEntityWorld());
        *///? } else if >=1.21.2 {
        /*entity.kill((ServerWorld) entity.getWorld());
        *///? } else {
        entity.kill();
        //? }
    }

    /** {@code ServerPlayerEntity.teleport} gained Set<PositionFlag> + resetCamera boolean in 1.21.2. */
    public static void teleport(ServerPlayerEntity p, ServerWorld world,
                                double x, double y, double z, float yaw, float pitch) {
        //? if >=1.21.2 {
        /*p.teleport(world, x, y, z, java.util.Set.of(), yaw, pitch, true);
        *///? } else
        p.teleport(world, x, y, z, yaw, pitch);
    }

    /** {@code ServerWorld.spawnParticles} (viewer overload) gained an "important" boolean in 1.21.4. */
    public static <T extends ParticleEffect> void spawnParticles(ServerWorld world, ServerPlayerEntity viewer, T particle,
            boolean force, double x, double y, double z, int count, double dx, double dy, double dz, double speed) {
        //? if >=1.21.4 {
        /*world.spawnParticles(viewer, particle, force, false, x, y, z, count, dx, dy, dz, speed);
        *///? } else {
        world.spawnParticles(viewer, particle, force, x, y, z, count, dx, dy, dz, speed);
        //? }
    }

    /** NbtCompound getters return Optional in 1.21.5; these always return a plain value. */
    public static int nbtGetInt(NbtCompound nbt, String key) {
        //? if >=1.21.5 {
        /*return nbt.getInt(key, 0);
        *///? } else {
        return nbt.getInt(key);
        //? }
    }

    public static boolean nbtGetBoolean(NbtCompound nbt, String key) {
        //? if >=1.21.5 {
        /*return nbt.getBoolean(key, false);
        *///? } else {
        return nbt.getBoolean(key);
        //? }
    }
}
