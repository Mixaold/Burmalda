package com.mixaold.burmalda.debuff.solo;

import com.mixaold.burmalda.debuff.Debuff;
import com.mixaold.burmalda.debuff.DebuffType;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.EnumSet;
import java.util.Random;

public class PyanyPilotDebuff extends Debuff {

    private static final Random RANDOM = new Random();
    private int nextSpin = 0;

    public PyanyPilotDebuff() {
        super("pyany_pilot", "Drunk Pilot",
                "Your head jerks randomly every 20 seconds",
                DebuffType.SOLO);
    }

    @Override
    public void onStart(ServerPlayerEntity player) {
        nextSpin = 400;
        player.sendMessage(Text.translatableWithFallback("burmalda.chat.pyany_pilot.start", "§e[Drunk Pilot] §fBuckle up — your head's leaving."), false);
    }

    @Override
    public void onEnd(ServerPlayerEntity player) {
        nextSpin = 0;
    }

    @Override
    public void onPlayerTick(ServerPlayerEntity player, int ticksActive) {
        if (ticksActive < nextSpin) return;
        nextSpin = ticksActive + 400; // 20 sec

        float deltaYaw = 80f + RANDOM.nextFloat() * 100f; // 80–180°
        if (RANDOM.nextBoolean()) deltaYaw = -deltaYaw;

        com.mixaold.burmalda.util.BurmaldaCompat.teleport(player, com.mixaold.burmalda.util.BurmaldaCompat.world(player),
                player.getX(), player.getY(), player.getZ(),
                player.getYaw() + deltaYaw, player.getPitch());
        player.sendMessage(Text.translatableWithFallback("burmalda.chat.pyany_pilot.spin", "§e[Drunk Pilot] §fWatch where you're looking!"), true);
    }
}
