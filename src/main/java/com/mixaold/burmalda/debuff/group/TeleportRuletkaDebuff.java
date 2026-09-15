package com.mixaold.burmalda.debuff.group;

import com.mixaold.burmalda.debuff.Debuff;
import com.mixaold.burmalda.debuff.DebuffType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class TeleportRuletkaDebuff extends Debuff {

    private static final Random RANDOM = new Random();
    private static final int INTERVAL = 600; // 30 seconds

    public TeleportRuletkaDebuff() {
        super("teleport_ruletka", "Teleport Roulette",
                "Players swap positions every 30 seconds",
                DebuffType.GROUP);
    }

    @Override
    public void onGroupTick(MinecraftServer server, int ticksActive) {
        if (ticksActive % INTERVAL != 0) return;

        List<ServerPlayerEntity> players = new ArrayList<>(server.getPlayerManager().getPlayerList());
        if (players.size() < 2) return;

        // Save positions
        List<Vec3d> positions = new ArrayList<>();
        List<Float> yaws = new ArrayList<>();
        for (ServerPlayerEntity p : players) {
            positions.add(com.mixaold.burmalda.util.BurmaldaCompat.pos(p));
            yaws.add(p.getYaw());
        }

        // Rotate positions (each player goes to next player's spot)
        Collections.rotate(positions, 1);

        for (int i = 0; i < players.size(); i++) {
            Vec3d pos = positions.get(i);
            com.mixaold.burmalda.util.BurmaldaCompat.teleport(players.get(i),
                    com.mixaold.burmalda.util.BurmaldaCompat.server(players.get(i)).getWorld(com.mixaold.burmalda.util.BurmaldaCompat.entityWorld(players.get(i)).getRegistryKey()),
                    pos.x, pos.y, pos.z, yaws.get(i), players.get(i).getPitch());
        }

        server.getPlayerManager().broadcast(
                Text.translatableWithFallback("burmalda.chat.teleport_ruletka.swap", "§b[Teleport Roulette] §fEveryone swapped positions!"), false);
    }
}
