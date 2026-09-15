package com.mixaold.burmalda.debuff.solo;

import com.mixaold.burmalda.debuff.Debuff;
import com.mixaold.burmalda.debuff.DebuffType;
import com.mixaold.burmalda.sound.BurmaldaSounds;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class TelefonistDebuff extends Debuff {

    private static final Random RANDOM = new Random();
    private static final int INTERVAL = 900; // 45 seconds

    public TelefonistDebuff() {
        super("telefonist", "Teleporter",
                "Every 45 sec you teleport to a random player — is he happy? Doubt it",
                DebuffType.SOLO);
    }

    @Override
    public void onPlayerTick(ServerPlayerEntity player, int ticksActive) {
        if (ticksActive % INTERVAL != 0) return;

        List<ServerPlayerEntity> others = new ArrayList<>(com.mixaold.burmalda.util.BurmaldaCompat.server(player).getPlayerManager().getPlayerList());
        others.remove(player);
        if (others.isEmpty()) return;

        ServerPlayerEntity target = others.get(RANDOM.nextInt(others.size()));
        com.mixaold.burmalda.util.BurmaldaCompat.teleport(player, com.mixaold.burmalda.util.BurmaldaCompat.server(player).getWorld(com.mixaold.burmalda.util.BurmaldaCompat.entityWorld(target).getRegistryKey()),
                target.getX(), target.getY(), target.getZ(),
                target.getYaw(), target.getPitch());
        player.sendMessage(Text.translatableWithFallback("burmalda.chat.telefonist.teleport", "§6[Teleporter] §fTeleported to §e%s", target.getName().getString()), false);
        BurmaldaSounds.playTune(player);
    }
}
