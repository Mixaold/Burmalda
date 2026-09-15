package com.mixaold.burmalda.debuff.solo;

import com.mixaold.burmalda.debuff.Debuff;
import com.mixaold.burmalda.debuff.DebuffType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.List;

public class PritiyazheniDebuff extends Debuff {

    public PritiyazheniDebuff() {
        super("prityazhenie", "Attraction",
                "All ground loot flies toward you",
                DebuffType.SOLO);
    }

    @Override
    public void onPlayerTick(ServerPlayerEntity player, int ticksActive) {
        if (ticksActive % 2 != 0) return;
        Box searchBox = Box.of(com.mixaold.burmalda.util.BurmaldaCompat.pos(player), 50, 20, 50);
        List<ItemEntity> items = com.mixaold.burmalda.util.BurmaldaCompat.world(player).getEntitiesByClass(
                ItemEntity.class, searchBox, e -> !e.isRemoved());

        Vec3d playerPos = com.mixaold.burmalda.util.BurmaldaCompat.pos(player).add(0, 0.5, 0);
        for (ItemEntity item : items) {
            Vec3d toPlayer = playerPos.subtract(com.mixaold.burmalda.util.BurmaldaCompat.pos(item)).normalize().multiply(0.4);
            item.setVelocity(toPlayer);
            com.mixaold.burmalda.util.BurmaldaCompat.markVelocityDirty(item);
        }
    }
}
