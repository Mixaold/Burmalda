package com.mixaold.burmalda.hud;

import com.mixaold.burmalda.debuff.ClientDebuffState;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

@Environment(EnvType.CLIENT)
public class AnnouncementOverlay {

    private static final long FADE_IN_MS  = 400L;
    private static final long HOLD_MS     = 7000L;
    private static final long FADE_OUT_MS = 800L;
    private static final long TOTAL_MS    = FADE_IN_MS + HOLD_MS + FADE_OUT_MS;

    private static final float NAME_SCALE  = 1.5f;
    private static final float DESC_SCALE  = 0.9f;
    private static final int   DESC_LINE_H = 10;
    private static final int   PAD         = 14;
    private static final int   MAX_BOX_W   = 480;

    public static void register() {
        HudRenderCallback.EVENT.register((context, tickCounter) -> render(context));
    }

    private static void render(DrawContext context) {
        if (ClientDebuffState.announcementName == null) return;

        long elapsed = System.currentTimeMillis() - ClientDebuffState.announcementStartMs;
        if (elapsed >= TOTAL_MS) {
            ClientDebuffState.announcementName  = null;
            ClientDebuffState.announcementDesc  = null;
            ClientDebuffState.announcementDarken = false;
            return;
        }

        float alpha;
        if (elapsed < FADE_IN_MS) {
            alpha = (float) elapsed / FADE_IN_MS;
        } else if (elapsed < FADE_IN_MS + HOLD_MS) {
            alpha = 1.0f;
        } else {
            alpha = 1.0f - (float)(elapsed - FADE_IN_MS - HOLD_MS) / FADE_OUT_MS;
        }
        alpha = Math.max(0f, Math.min(1f, alpha));

        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer tr = client.textRenderer;
        int sw = context.getScaledWindowWidth();
        int sh = context.getScaledWindowHeight();

        if (ClientDebuffState.announcementDarken) {
            int darkAlpha = (int)(alpha * 180) & 0xFF;
            context.fill(0, 0, sw, sh, (darkAlpha << 24));
        }

        String name = ClientDebuffState.announcementName;
        String desc = ClientDebuffState.announcementDesc != null ? ClientDebuffState.announcementDesc : "";

        int maxBoxW    = Math.min(MAX_BOX_W, sw - 32);
        int maxContent = maxBoxW - PAD * 2;

        // Name — trailing space prevents Cyrillic last-glyph render bug
        Text nameText = Text.literal(name + " ").formatted(Formatting.BOLD);
        int nameW = Math.min((int)(tr.getWidth(nameText) * NAME_SCALE), maxContent);

        // Wrap description to fit box
        int maxDescPx = (int)(maxContent / DESC_SCALE);
        List<String> descLines = wrapText(tr, desc, maxDescPx);

        // Final box width
        int boxW = nameW + PAD * 2;
        for (String line : descLines) {
            int lw = (int)(tr.getWidth(line) * DESC_SCALE) + PAD * 2;
            if (lw > boxW) boxW = lw;
        }
        boxW = Math.min(boxW, maxBoxW);

        int nameH = (int)(9 * NAME_SCALE);
        int boxH  = PAD + nameH
                + (descLines.isEmpty() ? 0 : 4 + descLines.size() * DESC_LINE_H)
                + PAD;

        int boxX = (sw - boxW) / 2;
        int boxY = sh / 2 - boxH / 2 - 50;

        int bgAlpha   = (int)(alpha * 200) & 0xFF;
        int textAlpha = (int)(alpha * 255) & 0xFF;

        context.fill(boxX - 2, boxY - 2, boxX + boxW + 2, boxY + boxH + 2, (bgAlpha << 24));
        context.fill(boxX, boxY - 2, boxX + boxW, boxY - 1, (textAlpha << 24) | 0x00FFAA00);

        int nameColor = (textAlpha << 24) | 0x00FFCC44;
        int nameX = (sw - nameW) / 2;
        int nameY = boxY + PAD;
        drawScaledText(context, tr, nameText, nameX, nameY, NAME_SCALE, nameColor, true);

        int descColor = (textAlpha << 24) | 0x00DDDDDD;
        int lineY = nameY + nameH + 4;
        for (String line : descLines) {
            Text lineText = Text.literal(line).formatted(Formatting.ITALIC);
            int lineW = (int)(tr.getWidth(lineText) * DESC_SCALE);
            int lineX = (sw - lineW) / 2;
            drawScaledText(context, tr, lineText, lineX, lineY, DESC_SCALE, descColor, false);
            lineY += DESC_LINE_H;
        }
    }

    private static List<String> wrapText(TextRenderer tr, String text, int maxPx) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) return lines;
        String[] words = text.split(" ");
        StringBuilder cur = new StringBuilder();
        for (String word : words) {
            String candidate = cur.isEmpty() ? word : cur + " " + word;
            if (tr.getWidth(candidate) <= maxPx) {
                cur = new StringBuilder(candidate);
            } else {
                if (!cur.isEmpty()) lines.add(cur.toString());
                cur = new StringBuilder(word);
            }
        }
        if (!cur.isEmpty()) lines.add(cur.toString());
        return lines;
    }

    private static void drawScaledText(DrawContext ctx, TextRenderer tr,
                                        Text text, int x, int y,
                                        float scale, int color, boolean shadow) {
        com.mixaold.burmalda.util.BurmaldaRenderCompat.push(ctx);
        com.mixaold.burmalda.util.BurmaldaRenderCompat.translate(ctx, x, y);
        com.mixaold.burmalda.util.BurmaldaRenderCompat.scale(ctx, scale, scale);
        ctx.drawText(tr, text, 0, 0, color, shadow);
        com.mixaold.burmalda.util.BurmaldaRenderCompat.pop(ctx);
    }
}
