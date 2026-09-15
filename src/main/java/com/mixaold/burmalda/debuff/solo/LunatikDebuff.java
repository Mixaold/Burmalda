package com.mixaold.burmalda.debuff.solo;

import com.mixaold.burmalda.debuff.Debuff;
import com.mixaold.burmalda.debuff.DebuffType;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import java.util.Random;

public class LunatikDebuff extends Debuff {

    private static final Random RANDOM = new Random();
    private static final double LAUNCH_FORCE = 2.0;
    private int nextLaunch = 0;

    public LunatikDebuff() {
        super("lunatik", "Sleepwalker",
                "Every 5-6 seconds you get launched forward",
                DebuffType.SOLO);
    }

    @Override
    public void onStart(ServerPlayerEntity player) {
        nextLaunch = 80 + RANDOM.nextInt(40);
        player.sendMessage(Text.translatableWithFallback("burmalda.chat.lunatik.start", "§b[Sleepwalker] §fHere we go. Literally."), false);
    }

    @Override
    public void onEnd(ServerPlayerEntity player) {
        nextLaunch = 0;
    }

    @Override
    public void onPlayerTick(ServerPlayerEntity player, int ticksActive) {
        if (ticksActive < nextLaunch) return;
        nextLaunch = ticksActive + 100 + RANDOM.nextInt(40); // 5-7 sec

        Vec3d look = player.getRotationVector();
        double hLen = Math.sqrt(look.x * look.x + look.z * look.z);
        Vec3d horiz;
        if (hLen > 0.001) {
            horiz = new Vec3d(look.x / hLen, 0, look.z / hLen);
        } else {
            double yaw = Math.toRadians(player.getBodyYaw());
            horiz = new Vec3d(-Math.sin(yaw), 0, Math.cos(yaw));
        }

        player.setVelocity(horiz.multiply(LAUNCH_FORCE).add(0, 0.25, 0));
        com.mixaold.burmalda.util.BurmaldaCompat.markVelocityDirty(player);
        player.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(player));
        player.sendMessage(Text.translatableWithFallback("burmalda.chat.lunatik.launch", "§b[Sleepwalker] §fOff they go."), true);
    }
}
