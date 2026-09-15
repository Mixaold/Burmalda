package com.mixaold.burmalda.debuff.solo;

import com.mixaold.burmalda.debuff.Debuff;
import com.mixaold.burmalda.debuff.DebuffType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Random;

public class TyotaValyaDebuff extends Debuff {

    private static final int INTERVAL = 600;
    private static final Random RANDOM = new Random();

    private static final String[] MSG_KEYS = {
        "burmalda.chat.tyota_valya.0", "burmalda.chat.tyota_valya.1", "burmalda.chat.tyota_valya.2",
        "burmalda.chat.tyota_valya.3", "burmalda.chat.tyota_valya.4", "burmalda.chat.tyota_valya.5",
        "burmalda.chat.tyota_valya.6", "burmalda.chat.tyota_valya.7", "burmalda.chat.tyota_valya.8",
        "burmalda.chat.tyota_valya.9", "burmalda.chat.tyota_valya.10"
    };
    private static final String[] MSG_FALLBACKS = {
        "§e[Aunt Valya]: §fDid you eat? Dress warmer. How's school?",
        "§e[Aunt Valya]: §fDon't forget to drink water. Did you sleep? I'm worried.",
        "§e[Aunt Valya]: §fWhen did you last eat a real meal? Call your mom.",
        "§e[Aunt Valya]: §fAre you wearing a hat? It's cold outside. Why don't you answer?",
        "§e[Aunt Valya]: §fYou're the careless one. Dress warmer, it's windy out there!",
        "§e[Aunt Valya]: §fI'm calling because I care. Did you eat or are you hungry again?",
        "§e[Aunt Valya]: §fWhy aren't you answering me? You know how much I worry about you.",
        "§e[Aunt Valya]: §fStudy hard, you're our only hope. How are things going at least?",
        "§e[Aunt Valya]: §fUp all night on the computer again? We'll talk when you get home.",
        "§e[Aunt Valya]: §fI baked a pie, get home quick while it's hot.",
        "§e[Aunt Valya]: §fDid you put on your hat? Don't argue with me, you'll get sick."
    };

    public TyotaValyaDebuff() {
        super("tyota_valya", "Aunt Valya",
                "Aunt Valya is always nearby",
                DebuffType.SOLO);
    }

    @Override
    public void onPlayerTick(ServerPlayerEntity player, int ticksActive) {
        if (ticksActive % INTERVAL != 0) return;

        List<ServerPlayerEntity> others = com.mixaold.burmalda.util.BurmaldaCompat.server(player).getPlayerManager().getPlayerList()
                .stream().filter(p -> !p.getUuid().equals(player.getUuid())).toList();
        if (others.isEmpty()) return;

        ServerPlayerEntity target = others.get(RANDOM.nextInt(others.size()));
        int idx = RANDOM.nextInt(MSG_KEYS.length);
        target.sendMessage(Text.translatableWithFallback(MSG_KEYS[idx], MSG_FALLBACKS[idx]), false);
    }
}
