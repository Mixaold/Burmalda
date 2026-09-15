package com.mixaold.burmalda.debuff.solo;

import com.mixaold.burmalda.debuff.Debuff;
import com.mixaold.burmalda.debuff.DebuffType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/** Client-side effect: A/D inversion is handled by KeyboardInputMixin reading ClientDebuffState. */
public class ZerkalnyyMirDebuff extends Debuff {

    public ZerkalnyyMirDebuff() {
        super("zerkalnyy_mir", "Mirror World",
                "Left/right movement is inverted",
                DebuffType.SOLO);
    }

    @Override
    public void onStart(ServerPlayerEntity player) {
        player.sendMessage(Text.translatableWithFallback("burmalda.chat.zerkalnyy_mir.start", "§d[Mirror World] §fEverything's backwards. A is D. D is A. Good luck."), false);
    }

    @Override
    public void onEnd(ServerPlayerEntity player) {
        player.sendMessage(Text.translatableWithFallback("burmalda.chat.zerkalnyy_mir.end", "§d[Mirror World] §fBack to normal."), false);
    }
}
