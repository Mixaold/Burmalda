package com.mixaold.burmalda.debuff.solo;

import com.mixaold.burmalda.debuff.Debuff;
import com.mixaold.burmalda.debuff.DebuffType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import com.mixaold.burmalda.sound.BurmaldaSounds;

import java.util.ArrayList;
import java.util.List;

public class TelekinezDebuff extends Debuff {

    private static final int INTERVAL = 400; // 20 seconds

    public TelekinezDebuff() {
        super("telekinez", "Telekinesis",
                "Hotbar spits out items every 20 seconds",
                DebuffType.SOLO);
    }

    @Override
    public void onPlayerTick(ServerPlayerEntity player, int ticksActive) {
        if (ticksActive % INTERVAL != 0) return;

        Vec3d look = player.getRotationVec(1.0f).normalize().multiply(0.8);

        // Fling all hotbar items forward
        List<Integer> toFling = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            if (!player.getInventory().getStack(i).isEmpty()) toFling.add(i);
        }
        if (toFling.isEmpty()) return;

        // Only fling 1-2 random items
        int count = Math.min(2, toFling.size());
        for (int i = 0; i < count; i++) {
            int slot = toFling.get(i);
            ItemStack stack = player.getInventory().getStack(slot).copy();
            player.getInventory().setStack(slot, ItemStack.EMPTY);

            ItemEntity entity = new ItemEntity(com.mixaold.burmalda.util.BurmaldaCompat.world(player),
                    player.getX(), player.getY() + 0.5, player.getZ(), stack);
            entity.setVelocity(look.x * 2, look.y + 0.3, look.z * 2);
            com.mixaold.burmalda.util.BurmaldaCompat.world(player).spawnEntity(entity);
        }
        player.sendMessage(Text.translatableWithFallback("burmalda.chat.telekinez.throw", "§b[Telekinesis] §fHANDS!"), true);
        BurmaldaSounds.playTune(player);
    }
}
