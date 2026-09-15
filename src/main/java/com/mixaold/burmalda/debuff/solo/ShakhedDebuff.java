package com.mixaold.burmalda.debuff.solo;

import com.mixaold.burmalda.advancement.BurmaldaAdvancements;
import com.mixaold.burmalda.debuff.Debuff;
import com.mixaold.burmalda.debuff.DebuffType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.TntEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ShakhedDebuff extends Debuff {

    private static final Random RANDOM = new Random();
    private static final int SPAWN_INTERVAL = 600;   // 30 sec
    private static final int MAX_LIFETIME   = 500;   // 25 sec to reach player
    private static final float FAKE_CHANCE  = 0.05f; // 5% fake
    private static final double SPEED             = 0.75; // fast enough to be hard to outrun
    private static final double EXPLODE_DIST      = 1.8;
    private static final double FAKE_VANISH_DIST  = 3.0;

    private record Entry(int entityId, boolean fake, int spawnedAt) {}
    // Per-player active drones — this debuff is a shared singleton, so all live state must be keyed by player.
    private final java.util.Map<java.util.UUID, List<Entry>> activeByPlayer = new java.util.HashMap<>();
    // Consecutive real strikes survived without a direct hit, per player → «Уворот года» at 5.
    private final java.util.Map<java.util.UUID, Integer> dodgeStreak = new java.util.HashMap<>();

    private List<Entry> activeFor(ServerPlayerEntity player) {
        return activeByPlayer.computeIfAbsent(player.getUuid(), k -> new ArrayList<>());
    }

    public ShakhedDebuff() {
        super("shakhed", "Shahed",
                "Every 30 seconds — TNT flies at you like a drone",
                DebuffType.SOLO);
    }

    /** A real strike resolved without a direct hit — count it toward «Уворот года». */
    private void registerDodge(ServerPlayerEntity player) {
        int s = dodgeStreak.merge(player.getUuid(), 1, Integer::sum);
        if (s >= 5) BurmaldaAdvancements.trigger(player, BurmaldaAdvancements.SHAHED_ACE);
    }

    @Override
    public void onStart(ServerPlayerEntity player) {
        activeByPlayer.put(player.getUuid(), new ArrayList<>());
        dodgeStreak.remove(player.getUuid());
        player.sendMessage(Text.translatableWithFallback("burmalda.chat.shakhed.start", "§c[☢ SHAHED] §fPilot is airborne. Hide."), false);
    }

    @Override
    public void onEnd(ServerPlayerEntity player) {
        ServerWorld world = com.mixaold.burmalda.util.BurmaldaCompat.world(player);
        for (Entry e : activeFor(player)) {
            Entity entity = world.getEntityById(e.entityId());
            if (entity != null) entity.remove(Entity.RemovalReason.DISCARDED);
            com.mixaold.burmalda.entity.ShahedDroneVisual.remove(world, e.entityId());
        }
        activeByPlayer.remove(player.getUuid());
        if (player.isAlive()) {
            BurmaldaAdvancements.trigger(player, BurmaldaAdvancements.SHAHED_SURVIVED);
        }
    }

    @Override
    public void onPlayerTick(ServerPlayerEntity player, int ticksActive) {
        if (ticksActive % SPAWN_INTERVAL == 0) {
            spawnShahed(player, ticksActive);
        }
        updateActive(player, ticksActive);
    }

    private void spawnShahed(ServerPlayerEntity player, int tick) {
        ServerWorld world = com.mixaold.burmalda.util.BurmaldaCompat.world(player);
        boolean fake = RANDOM.nextFloat() < FAKE_CHANCE;

        double angle = RANDOM.nextDouble() * Math.PI * 2;
        double x = player.getX() + Math.cos(angle) * 55;             // farther away
        double y = player.getY() + 20 + RANDOM.nextDouble() * 12;    // higher up (+20..+32)
        double z = player.getZ() + Math.sin(angle) * 55;

        // Invisible armor-stand carrier (NOT a phantom) — it only provides position + a hitbox for
        // collision; the visible drone is the block quadcopter built by ShahedDroneVisual.
        net.minecraft.entity.decoration.ArmorStandEntity drone =
                com.mixaold.burmalda.util.BurmaldaCompat.create(net.minecraft.entity.EntityType.ARMOR_STAND, world);
        if (drone == null) return;
        drone.setPosition(x, y, z);
        drone.setNoGravity(true);
        drone.setInvulnerable(true);
        drone.setSilent(true);
        drone.setInvisible(true); // carrier is fully hidden — armor stands vanish completely when invisible
        drone.setCustomName(Text.literal(fake ? "§a[FAKE] SHAHED" : "§c☢ SHAHED"));
        drone.setCustomNameVisible(true);
        world.spawnEntity(drone);
        com.mixaold.burmalda.entity.ShahedDroneVisual.spawn(world, drone);

        activeFor(player).add(new Entry(drone.getId(), fake, tick));
        player.sendMessage(Text.translatableWithFallback("burmalda.chat.shakhed.detected", "§c[☢ SHAHED] §fTarget acquired. Incoming."), true);
    }

    private void updateActive(ServerPlayerEntity player, int ticksActive) {
        ServerWorld world = com.mixaold.burmalda.util.BurmaldaCompat.world(player);
        List<Entry> active = activeFor(player);
        List<Entry> toRemove = new ArrayList<>();

        for (Entry entry : active) {
            Entity entity = world.getEntityById(entry.entityId());
            if (entity == null) { toRemove.add(entry); continue; }

            // Max lifetime check
            if (ticksActive - entry.spawnedAt() > MAX_LIFETIME) {
                if (!entry.fake()) detonate(world, entity, player);
                else entity.remove(Entity.RemovalReason.DISCARDED);
                toRemove.add(entry);
                continue;
            }

            // Water soaks the fuse — drone is dead, no boom
            if (entity.isTouchingWater()) {
                entity.remove(Entity.RemovalReason.DISCARDED);
                toRemove.add(entry);
                registerDodge(player); // neutralized the strike = a dodge
                player.sendMessage(Text.translatableWithFallback("burmalda.chat.shakhed.drowned",
                        "§a[☢ SHAHED] §fDrone hit the water. Fuse soaked, threat neutralized."), true);
                continue;
            }

            Vec3d entityPos = com.mixaold.burmalda.util.BurmaldaCompat.pos(entity);
            Vec3d target    = player.getEyePos();
            double dist     = entityPos.distanceTo(target);

            // Fake: vanish and taunt when close
            if (entry.fake() && dist < FAKE_VANISH_DIST) {
                entity.remove(Entity.RemovalReason.DISCARDED);
                toRemove.add(entry);
                player.sendMessage(Text.translatableWithFallback("burmalda.chat.shakhed.fake", "§a[SHAHED] §fFAKE. Lucky you."), true);
                continue;
            }

            // Real: explode on player contact
            if (!entry.fake() && dist < EXPLODE_DIST) {
                detonate(world, entity, player);
                toRemove.add(entry);
                continue;
            }

            // Real: explode on block contact — expand box outward so touching a face counts
            if (!entry.fake()) {
                boolean hitBlock = BlockPos.stream(entity.getBoundingBox().expand(0.1))
                        .anyMatch(pos -> {
                            var bs = world.getBlockState(pos);
                            return !bs.isAir() && bs.getFluidState().isEmpty();
                        });
                if (hitBlock) {
                    detonate(world, entity, player);
                    toRemove.add(entry);
                    continue;
                }
                // Also explode on mob contact
                boolean hitMob = !world.getEntitiesByClass(LivingEntity.class,
                        entity.getBoundingBox().expand(0.6),
                        e -> e.isAlive() && e != player && e != entity).isEmpty();
                if (hitMob) {
                    detonate(world, entity, player);
                    toRemove.add(entry);
                    continue;
                }
            }

            // Steer toward player — constant speed. The drone is a (mob) LivingEntity, so we drive
            // it by setting position directly each tick (velocity-only gets damped by mob physics);
            // the client interpolates between the synced positions, so it still looks smooth.
            Vec3d dir   = target.subtract(entityPos).normalize();
            Vec3d step  = dir.multiply(SPEED);
            entity.setVelocity(Vec3d.ZERO);
            entity.setPosition(entityPos.add(step));
            float yaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
            entity.setYaw(yaw);

            // Drive the block quadcopter to follow + spin its rotors
            com.mixaold.burmalda.entity.ShahedDroneVisual.update(world, entity, ticksActive);

            // Drone buzz — a pitched-down aggressive bee loop reads as an angry quadcopter motor.
            // Played as rapid short one-shots (not a looping instance) so it cuts out within ~1s of
            // detonation instead of droning on. Pitch rises a touch as it closes in.
            int soundInterval = dist < 6 ? 4 : dist < 14 ? 5 : 7;
            float pitch = (float) Math.max(0.55, Math.min(1.05, 1.15 - dist * 0.035));
            if (ticksActive % soundInterval == 0) {
                // High volume → big audible radius (range ≈ 16 × volume), so you hear it coming from afar.
                world.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                        SoundEvents.ENTITY_BEE_LOOP_AGGRESSIVE,
                        SoundCategory.HOSTILE, 6.0f, pitch);
            }
        }

        // Tear down the block quadcopter for any carrier that died/detonated this tick
        for (Entry e : toRemove) com.mixaold.burmalda.entity.ShahedDroneVisual.remove(world, e.entityId());
        active.removeAll(toRemove);
    }

    private void detonate(ServerWorld world, Entity entity, ServerPlayerEntity player) {
        // Cut the buzz the instant it blows up — one-shot sounds can't be stopped, so without this
        // the aggressive-bee loop keeps droning 2-3s after the explosion. StopSound kills it now.
        net.minecraft.util.Identifier buzz = net.minecraft.util.Identifier.of("minecraft", "entity.bee.loop_aggressive");
        for (ServerPlayerEntity p : com.mixaold.burmalda.util.BurmaldaCompat.server(player).getPlayerManager().getPlayerList()) {
            if (com.mixaold.burmalda.util.BurmaldaCompat.world(p) == world
                    && p.squaredDistanceTo(entity.getX(), entity.getY(), entity.getZ()) < 130 * 130) {
                p.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.StopSoundS2CPacket(buzz, SoundCategory.HOSTILE));
            }
        }

        double dist = com.mixaold.burmalda.util.BurmaldaCompat.pos(entity).distanceTo(com.mixaold.burmalda.util.BurmaldaCompat.pos(player));
        world.createExplosion(null,
                entity.getX(), entity.getY(), entity.getZ(),
                4.5f, true, World.ExplosionSourceType.TNT);
        entity.remove(Entity.RemovalReason.DISCARDED);
        if (dist < 6.0) {
            dodgeStreak.put(player.getUuid(), 0); // direct hit breaks the streak
            player.sendMessage(Text.translatableWithFallback("burmalda.chat.shakhed.hit", "§c[☢ SHAHED] §fDIRECT HIT. Called it."), true);
        } else {
            registerDodge(player); // dodged the strike
            player.sendMessage(Text.translatableWithFallback("burmalda.chat.shakhed.miss", "§c[☢ SHAHED] §fMissed. This time."), true);
        }
    }
}
