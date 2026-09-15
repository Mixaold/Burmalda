package com.mixaold.burmalda.debuff.solo;

import com.mixaold.burmalda.debuff.Debuff;
import com.mixaold.burmalda.debuff.DebuffType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.*;

public class EkhoUronaDebuff extends Debuff {

    // Pending echo damage: (tick to fire, amount)
    private final Queue<long[]> pending = new LinkedList<>();
    private boolean echoing = false; // prevents echo from scheduling another echo

    public EkhoUronaDebuff() {
        super("ekho_urona", "Damage Echo",
                "Took damage? In 3 seconds it strikes again",
                DebuffType.SOLO);
    }

    @Override
    public void onEnd(ServerPlayerEntity player) {
        pending.clear();
    }

    @Override
    public boolean onDamage(ServerPlayerEntity player, float amount) {
        if (echoing) return false; // don't echo the echo
        long echoAt = com.mixaold.burmalda.util.BurmaldaCompat.server(player).getTicks() + 60L;
        pending.add(new long[]{echoAt, (long)(amount * 10)});
        return false;
    }

    @Override
    public void onPlayerTick(ServerPlayerEntity player, int ticksActive) {
        long now = com.mixaold.burmalda.util.BurmaldaCompat.server(player).getTicks();
        while (!pending.isEmpty() && pending.peek()[0] <= now) {
            long[] entry = pending.poll();
            float dmg = entry[1] / 10f;
            if (player.isAlive()) {
                echoing = true;
                com.mixaold.burmalda.util.BurmaldaCompat.damage(player, com.mixaold.burmalda.util.BurmaldaCompat.world(player).getDamageSources().generic(), dmg);
                echoing = false;
                player.sendMessage(Text.translatableWithFallback("burmalda.chat.ekho_urona.hit", "§c[Damage Echo] §fThe past caught up: %s damage", dmg), true);
            }
        }
    }
}
