package com.mixaold.burmalda.entity;

import com.mixaold.burmalda.mixin.BlockDisplayStateInvoker;
import com.mixaold.burmalda.mixin.DisplayTransformInvoker;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.Brightness;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.AffineTransformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A real (block-built) quadcopter rendered for an invisible Shahed carrier: a flat dark body with
 * four spinning rotor blocks at the corners. Built from vanilla BlockDisplay entities (version-stable,
 * no custom model/renderer) that ride the carrier each tick. The shape is 4-fold symmetric, so it
 * looks identical from any heading — we only need to spin the rotors and follow the carrier.
 */
public final class ShahedDroneVisual {
    private ShahedDroneVisual() {}

    // carrier entity-id → its block-display part UUIDs
    private static final Map<Integer, List<UUID>> PARTS = new HashMap<>();

    private record Part(float ox, float oy, float oz, float sx, float sy, float sz, Block block, boolean spin) {}

    private static final Part[] LAYOUT = {
        // sleek dark body
        new Part(0f, 0f, 0f, 0.45f, 0.16f, 0.45f, Blocks.POLISHED_BLACKSTONE, false),
        // glowing green status LED underneath (drone "camera")
        new Part(0f, -0.12f, 0f, 0.14f, 0.10f, 0.14f, Blocks.LIME_CONCRETE, false),
        // four motor pods at the corners (static)
        new Part( 0.38f, 0.02f,  0.38f, 0.18f, 0.14f, 0.18f, Blocks.POLISHED_BLACKSTONE, false),
        new Part(-0.38f, 0.02f,  0.38f, 0.18f, 0.14f, 0.18f, Blocks.POLISHED_BLACKSTONE, false),
        new Part( 0.38f, 0.02f, -0.38f, 0.18f, 0.14f, 0.18f, Blocks.POLISHED_BLACKSTONE, false),
        new Part(-0.38f, 0.02f, -0.38f, 0.18f, 0.14f, 0.18f, Blocks.POLISHED_BLACKSTONE, false),
        // four thin propeller blades above the pods — spinning fast
        new Part( 0.38f, 0.17f,  0.38f, 0.55f, 0.05f, 0.12f, Blocks.LIGHT_GRAY_CONCRETE, true),
        new Part(-0.38f, 0.17f,  0.38f, 0.55f, 0.05f, 0.12f, Blocks.LIGHT_GRAY_CONCRETE, true),
        new Part( 0.38f, 0.17f, -0.38f, 0.55f, 0.05f, 0.12f, Blocks.LIGHT_GRAY_CONCRETE, true),
        new Part(-0.38f, 0.17f, -0.38f, 0.55f, 0.05f, 0.12f, Blocks.LIGHT_GRAY_CONCRETE, true),
    };

    public static void spawn(ServerWorld world, Entity carrier) {
        List<UUID> ids = new ArrayList<>();
        for (Part p : LAYOUT) {
            DisplayEntity.BlockDisplayEntity d = new DisplayEntity.BlockDisplayEntity(EntityType.BLOCK_DISPLAY, world);
            d.setPosition(carrier.getX(), carrier.getY() + 0.3, carrier.getZ());
            ((BlockDisplayStateInvoker) d).burmalda$setBlockState(p.block().getDefaultState());
            DisplayTransformInvoker inv = (DisplayTransformInvoker) d;
            inv.burmalda$setTeleportDuration(3);          // smooth position following
            inv.burmalda$setBrightness(new Brightness(15, 15)); // always lit, even at night
            d.setNoGravity(true);
            d.setInvulnerable(true);
            d.setSilent(true);
            world.spawnEntity(d);
            ids.add(d.getUuid());
        }
        PARTS.put(carrier.getId(), ids);
        update(world, carrier, 0);
    }

    public static void update(ServerWorld world, Entity carrier, int tick) {
        List<UUID> ids = PARTS.get(carrier.getId());
        if (ids == null) return;
        double cx = carrier.getX(), cy = carrier.getY() + 0.3, cz = carrier.getZ();
        float spin = tick * 1.3f; // rotor spin speed (radians/tick)
        for (int i = 0; i < ids.size() && i < LAYOUT.length; i++) {
            Entity e = world.getEntity(ids.get(i));
            if (!(e instanceof DisplayEntity.BlockDisplayEntity d)) continue;
            Part p = LAYOUT[i];
            d.setPosition(cx, cy, cz);

            Quaternionf rot = p.spin() ? new Quaternionf().rotateY(spin) : new Quaternionf();
            // Display origin is the block's corner — re-centre it: T = offset − rot·(scale·0.5)
            Vector3f half = rot.transform(new Vector3f(p.sx() * 0.5f, p.sy() * 0.5f, p.sz() * 0.5f));
            Vector3f t = new Vector3f(p.ox() - half.x, p.oy() - half.y, p.oz() - half.z);
            AffineTransformation at = new AffineTransformation(
                    t, rot, new Vector3f(p.sx(), p.sy(), p.sz()), new Quaternionf());
            ((DisplayTransformInvoker) d).burmalda$setTransformation(at);
        }
    }

    public static void remove(ServerWorld world, int carrierId) {
        List<UUID> ids = PARTS.remove(carrierId);
        if (ids == null) return;
        for (UUID id : ids) {
            Entity e = world.getEntity(id);
            if (e != null) e.remove(Entity.RemovalReason.DISCARDED);
        }
    }
}
