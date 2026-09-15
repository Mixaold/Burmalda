package com.mixaold.burmalda.debuff.solo;

import com.mixaold.burmalda.debuff.Debuff;
import com.mixaold.burmalda.debuff.DebuffType;
import net.minecraft.screen.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class KorpPismoDebuff extends Debuff {

    private static final int INTERVAL = 800;
    private Text lastAction = action("standby", "was in standby mode");

    public KorpPismoDebuff() {
        super("korp_pismo", "Corporate Email",
                "Official email to all players about your actions",
                DebuffType.SOLO);
    }

    /** Localized action phrase — Russian via ru_ru.json, English fallback baked in. */
    private static Text action(String suffix, String fallback) {
        return Text.translatableWithFallback("burmalda.chat.korp_pismo.action." + suffix, fallback);
    }

    @Override
    public void onStart(ServerPlayerEntity player) {
        lastAction = action("standby", "was in standby mode");
    }

    @Override
    public void onPlayerTick(ServerPlayerEntity player, int ticksActive) {
        var sh = player.currentScreenHandler;
        if (!(sh instanceof PlayerScreenHandler)) {
            if (sh instanceof CraftingScreenHandler) lastAction = action("crafting", "was crafting");
            else if (sh instanceof FurnaceScreenHandler
                  || sh instanceof BlastFurnaceScreenHandler
                  || sh instanceof SmokerScreenHandler) lastAction = action("smelting", "was smelting ore");
            else if (sh instanceof EnchantmentScreenHandler) lastAction = action("enchanting", "was enchanting items");
            else if (sh instanceof AnvilScreenHandler) lastAction = action("anvil", "was repairing or renaming");
            else if (sh instanceof BrewingStandScreenHandler) lastAction = action("brewing", "was brewing potions");
            else lastAction = action("chest", "was examining chest contents");
        } else if (player.isUsingItem()) lastAction = action("eating", "was consuming food");
        else if (player.isInLava()) lastAction = action("lava", "was making contact with lava");
        else if (player.isTouchingWater()) lastAction = action("water", "was performing aquatic procedures");
        else if (player.isSneaking()) lastAction = action("sneaking", "was conducting reconnaissance activities");
        else if (Math.abs(player.getVelocity().x) > 0.05 || Math.abs(player.getVelocity().z) > 0.05)
            lastAction = action("moving", "was performing locomotion");
        else lastAction = action("standby", "was in standby mode");

        if (ticksActive % INTERVAL != 0) return;

        String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        com.mixaold.burmalda.util.BurmaldaCompat.server(player).getPlayerManager().broadcast(
                Text.translatableWithFallback("burmalda.chat.korp_pismo.email",
                        "§3[Corp.Mail] §fDear colleagues, I hereby inform you that at %s %s %s. Please take note.",
                        time, player.getName().getString(), lastAction),
                false);
    }
}
