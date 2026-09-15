package com.mixaold.burmalda.debuff.solo;

import com.mixaold.burmalda.debuff.Debuff;
import com.mixaold.burmalda.debuff.DebuffType;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public class ObratnyGolodDebuff extends Debuff {

    public ObratnyGolodDebuff() {
        super("obratnyy_golod", "Reverse Hunger",
                "Food removes HP, damage fills hunger",
                DebuffType.SOLO);
    }

    @Override
    public void onStart(ServerPlayerEntity player) {
        player.sendMessage(Text.translatableWithFallback("burmalda.chat.obratny_golod.start", "§2[Reverse Hunger] §fFood is poison. Hunger is medicine. Enjoy."), false);
    }

    @Override
    public void onPlayerTick(ServerPlayerEntity player, int ticksActive) {
        // Check if player ate something this tick (hunger changed)
        // This is approximated by checking if the item use tick is active
        // The real hook is via ServerLivingEntityEvents or by checking food stats
        // For simplicity: periodically check and adjust
        if (ticksActive % 5 != 0) return;

        // When player eats, food level goes up — we reverse it
        // We monitor via hunger level changes would require a mixin,
        // so instead we apply a persistent effect that makes food harmful
        // Alternative: add constant nausea when hunger > 15 (overfed)
        int food = player.getHungerManager().getFoodLevel();
        if (food > 15) {
            // They ate recently — penalize
            float excess = food - 15;
            com.mixaold.burmalda.util.BurmaldaCompat.damage(player, com.mixaold.burmalda.util.BurmaldaCompat.world(player).getDamageSources().generic(), excess * 0.5f);
            player.getHungerManager().setFoodLevel(15);
        }
    }

    @Override
    public boolean onDamage(ServerPlayerEntity player, float amount) {
        // Taking damage fills hunger
        int currentFood = player.getHungerManager().getFoodLevel();
        player.getHungerManager().setFoodLevel(Math.min(20, currentFood + (int)(amount)));
        return false;
    }
}
