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
public class BurmaldaHud {

    private static final int PAD  = 6;
    private static final int RING = 24;
    private static final int GAP  = 6;
    private static final float DESC_SCALE = 0.80f;
    private static final int DESC_LINE_H  = 10; // pixels per wrapped description line
    private static final int MIN_TEXT_W   = 230; // minimum text column width

    public static void register() {
        HudRenderCallback.EVENT.register((context, tickCounter) -> render(context));
    }

    /** A timer bar centred just above the hotbar (e.g. Pandora's Box countdown). */
    private static void drawEventTimer(DrawContext ctx, MinecraftClient client) {
        if (System.currentTimeMillis() >= ClientDebuffState.eventTimerUntilMs) return;
        TextRenderer tr = client.textRenderer;
        int sw = ctx.getScaledWindowWidth();
        int sh = ctx.getScaledWindowHeight();
        int bw = 182, bh = 6;
        int x = (sw - bw) / 2;
        // Raised well clear of the vanilla action bar (≈ sh-68) so the countdown label
        // never overlaps action-bar messages like the locked-chest hint.
        int y = sh - 92;

        String label = ClientDebuffState.eventTimerLabel;
        int lw = tr.getWidth(label);
        ctx.drawText(tr, Text.literal(label), (sw - lw) / 2, y - 11, 0xFFFFFFFF, true);

        ctx.fill(x - 1, y - 1, x + bw + 1, y + bh + 1, 0xC0000000);
        ctx.fill(x, y, x + bw, y + bh, 0xFF202020);
        int fillW = Math.max(0, Math.min(bw, (int) (bw * ClientDebuffState.eventTimerFrac)));
        ctx.fill(x, y, x + fillW, y + bh, ClientDebuffState.eventTimerColor);
        com.mixaold.burmalda.util.BurmaldaRenderCompat.drawBorder(ctx, x, y, bw, bh, 0x60FFFFFF);
    }

    // ─── Cached panel layout ──────────────────────────────────────────────────
    // The panel text (translation lookups + word-wrapping + width/height measuring) only changes
    // when the active debuff or the window size changes — recomputing it every frame is what drove
    // the per-frame allocation/CPU cost. We cache it and rebuild only when an input actually changes.
    private static String lSoloId, lSoloNm, lSoloDe, lGroupId, lGroupNm, lGroupDe;
    private static int lSw = Integer.MIN_VALUE;
    private static String cSoloName = "", cGroupName = "";
    private static List<String> cSoloLines = List.of(), cGroupLines = List.of();
    private static int cTextColW, cSoloH, cGroupH, cContentW, cContentH;

    private static boolean eq(String a, String b) { return a == null ? b == null : a.equals(b); }

    private static void relayout(TextRenderer tr, int sw, boolean hasSolo, boolean hasGroup) {
        String soloId   = ClientDebuffState.activeDebuffId;
        String groupId  = ClientDebuffState.activeGroupDebuffId;
        String rSoloNm  = ClientDebuffState.activeDebuffName;
        String rSoloDe  = ClientDebuffState.activeDebuffDescription;
        String rGroupNm = ClientDebuffState.activeGroupDebuffName;
        String rGroupDe = ClientDebuffState.activeGroupDebuffDescription;

        if (sw == lSw && eq(soloId, lSoloId) && eq(rSoloNm, lSoloNm) && eq(rSoloDe, lSoloDe)
                && eq(groupId, lGroupId) && eq(rGroupNm, lGroupNm) && eq(rGroupDe, lGroupDe)) {
            return; // inputs unchanged — reuse the cached layout
        }
        lSw = sw; lSoloId = soloId; lSoloNm = rSoloNm; lSoloDe = rSoloDe;
        lGroupId = groupId; lGroupNm = rGroupNm; lGroupDe = rGroupDe;

        int maxTextColW = Math.max(MIN_TEXT_W, (sw - 16) - (PAD + RING + GAP + PAD));
        maxTextColW = Math.min(maxTextColW, 380); // cap on wide screens

        cSoloName = hasSolo ? Text.translatableWithFallback("burmalda.debuff." + soloId + ".name", rSoloNm).getString() : "";
        String soloDesc = hasSolo ? Text.translatableWithFallback("burmalda.debuff." + soloId + ".desc", rSoloDe).getString() : "";
        cGroupName = hasGroup ? "⚡ " + Text.translatableWithFallback("burmalda.debuff." + groupId + ".name", rGroupNm).getString() : "";
        String groupDesc = hasGroup ? Text.translatableWithFallback("burmalda.debuff." + groupId + ".desc", rGroupDe).getString() : "";

        cSoloLines  = hasSolo  ? wrapText(tr, soloDesc,  maxTextColW) : List.of();
        cGroupLines = hasGroup ? wrapText(tr, groupDesc, maxTextColW) : List.of();

        int soloW  = hasSolo  ? rawRowTextWidth(tr, cSoloName,  cSoloLines)  : 0;
        int groupW = hasGroup ? rawRowTextWidth(tr, cGroupName, cGroupLines) : 0;
        int textColW = Math.min(Math.max(soloW, groupW), maxTextColW);
        cTextColW = Math.max(textColW, MIN_TEXT_W);

        cContentW = PAD + RING + GAP + cTextColW + PAD + 4;
        cSoloH  = hasSolo  ? rowHeight(cSoloLines)  : 0;
        cGroupH = hasGroup ? rowHeight(cGroupLines) : 0;
        cContentH = cSoloH + cGroupH + (hasSolo && hasGroup ? 1 : 0);
    }

    private static void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options.hudHidden) return;

        drawEventTimer(context, client);

        boolean hasSolo  = ClientDebuffState.activeDebuffId != null;
        boolean hasGroup = ClientDebuffState.activeGroupDebuffId != null;
        if (!hasSolo && !hasGroup) return;

        TextRenderer tr = client.textRenderer;
        int sw = context.getScaledWindowWidth();

        relayout(tr, sw, hasSolo, hasGroup);
        int textColW = cTextColW;
        int contentW = cContentW;
        int contentH = cContentH;
        int soloH    = cSoloH;

        int panelX = sw - contentW - 8;
        int panelY = 8;

        context.fill(panelX, panelY, panelX + contentW, panelY + contentH, 0x90000000);
        com.mixaold.burmalda.util.BurmaldaRenderCompat.drawBorder(context, panelX, panelY, contentW, contentH, 0x40FFFFFF);

        if (hasSolo) {
            boolean failed = ClientDebuffState.soloDebuffFailed;
            int ry = panelY;
            float remaining = ClientDebuffState.totalCycleTicks > 0
                    ? (float) ClientDebuffState.ticksRemaining / ClientDebuffState.totalCycleTicks : 1f;
            float elapsed = 1f - remaining;
            int arcColor = failed ? 0xFF666666
                    : elapsed < 0.5f ? 0xFF00CC44 : elapsed < 0.8f ? 0xFFCCBB00 : 0xFFCC2222;

            int rx = panelX + PAD + RING / 2;
            int ringCy = ry + PAD + RING / 2;
            drawTrack(context, rx, ringCy, RING / 2, RING / 2 - 4, 0xFF707070);
            drawArc(context, rx, ringCy, RING / 2, RING / 2 - 4, -90f, -90f + remaining * 360f, arcColor);

            int tx = panelX + PAD + RING + GAP;
            int nameColor = failed ? 0xFF888888 : 0xFFFFFFFF;
            int descColor = failed ? 0xFF555555 : 0xFFAAAAAA;
            drawRowText(context, tr, tx, ry + PAD + 1, cSoloName, cSoloLines, textColW, nameColor, descColor, failed);
        }

        if (hasGroup) {
            boolean groupDone = ClientDebuffState.groupDebuffDone;
            int ry = panelY + soloH + (hasSolo ? 1 : 0);
            int rx = panelX + PAD + RING / 2;
            int ringCy = ry + PAD + RING / 2;
            drawTrack(context, rx, ringCy, RING / 2, RING / 2 - 4, groupDone ? 0xFF4A4A4A : 0xFF707070);
            float remaining = ClientDebuffState.totalCycleTicks > 0
                    ? (float) ClientDebuffState.ticksRemaining / ClientDebuffState.totalCycleTicks : 1f;
            float elapsed = 1f - remaining;
            // Same green→yellow→red progression as the solo ring, so the reds match exactly.
            int gArc = groupDone ? 0xFF666666
                    : elapsed < 0.5f ? 0xFF00CC44 : elapsed < 0.8f ? 0xFFCCBB00 : 0xFFCC2222;
            drawArc(context, rx, ringCy, RING / 2, RING / 2 - 4, -90f, -90f + remaining * 360f, gArc);

            int tx = panelX + PAD + RING + GAP;
            int groupNameColor = groupDone ? 0xFF888888 : 0xFFFF5555;
            int groupDescColor = groupDone ? 0xFF555555 : 0xFFCC6666;
            drawRowText(context, tr, tx, ry + PAD + 1, cGroupName, cGroupLines, textColW, groupNameColor, groupDescColor, groupDone);
        }

        renderWheelSkipOverlay(context, tr, sw);
        renderObshchagaHotbarIndicator(context, tr);
    }

    // ─── Obshchaga hotbar indicator ───────────────────────────────────────────

    private static void renderObshchagaHotbarIndicator(DrawContext context, TextRenderer tr) {
        // Show for every linked player (host AND partners), not only those whose own
        // solo debuff happens to be "obshchaga" — partners get a different debuff.
        String names = ClientDebuffState.obshchagaLinkedNames;
        if (names == null || names.isEmpty()) return;

        int sw = context.getScaledWindowWidth();
        int sh = context.getScaledWindowHeight();

        String text = "⛓ Связаны: " + names;
        int textW = tr.getWidth(text);
        int panelW = textW + 10;
        int panelH = 12;
        int panelX = (sw - panelW) / 2;
        int panelY = sh - 48;

        context.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0x80000000);
        com.mixaold.burmalda.util.BurmaldaRenderCompat.drawBorder(context, panelX, panelY, panelW, panelH, 0x40FFFFFF);
        context.drawText(tr, Text.literal(text).formatted(Formatting.YELLOW), panelX + 5, panelY + 2, 0xFFFFFFFF, true);
    }

    // ─── Wheel of Fortune skip corner overlay ─────────────────────────────────

    private static void renderWheelSkipOverlay(DrawContext context, TextRenderer tr, int sw) {
        if (!ClientDebuffState.wheelSkipOverlayActive) return;
        long elapsed = System.currentTimeMillis() - ClientDebuffState.wheelSkipStartMs;
        long DURATION_MS  = 6000L;
        long FADE_START   = 4500L;
        if (elapsed >= DURATION_MS) {
            ClientDebuffState.wheelSkipOverlayActive = false;
            return;
        }
        float alpha = elapsed < FADE_START ? 1.0f
                : 1.0f - (float)(elapsed - FADE_START) / (DURATION_MS - FADE_START);

        String skipName = Text.translatableWithFallback("burmalda.debuff.koleso_udachi.name", "Wheel of Fortune").getString();
        String skipMsg  = "Relax — Luck spared you, this time";

        int nameW = tr.getWidth(Text.literal(skipName + " ").formatted(Formatting.BOLD));
        int descW = (int)(tr.getWidth(Text.literal(skipMsg).formatted(Formatting.ITALIC)) * DESC_SCALE);
        int textColW = Math.max(Math.max(nameW, descW), MIN_TEXT_W);

        int contentW = PAD + RING + GAP + textColW + PAD + 4;
        int descH    = PAD + Math.max(RING, 9 + DESC_LINE_H) + PAD;
        int panelX   = sw - contentW - 8;
        int panelY   = 8;

        context.fill(panelX, panelY, panelX + contentW, panelY + descH, withAlpha(0x90000000, alpha));
        com.mixaold.burmalda.util.BurmaldaRenderCompat.drawBorder(context, panelX, panelY, contentW, descH, withAlpha(0x40FFFFFF, alpha));

        int rx = panelX + PAD + RING / 2;
        int ringCy = panelY + PAD + RING / 2;
        drawArc(context, rx, ringCy, RING / 2, RING / 2 - 4, 0f, 360f, withAlpha(0x50666666, alpha));
        drawArc(context, rx, ringCy, RING / 2, RING / 2 - 4, 0f, 360f, withAlpha(0xFF666666, alpha));

        int tx = panelX + PAD + RING + GAP;
        Text nameText = Text.literal(skipName + " ").formatted(Formatting.BOLD, Formatting.STRIKETHROUGH);
        context.drawText(tr, nameText, tx, panelY + PAD + 1, withAlpha(0xFF888888, alpha), true);

        List<String> msgLines = wrapText(tr, skipMsg, textColW);
        int lineY = panelY + PAD + 1 + 10;
        for (String line : msgLines) {
            com.mixaold.burmalda.util.BurmaldaRenderCompat.push(context);
            com.mixaold.burmalda.util.BurmaldaRenderCompat.translate(context, tx, lineY);
            com.mixaold.burmalda.util.BurmaldaRenderCompat.scale(context, DESC_SCALE, DESC_SCALE);
            context.drawText(tr, Text.literal(line).formatted(Formatting.ITALIC), 0, 0, withAlpha(0xFF555555, alpha), false);
            com.mixaold.burmalda.util.BurmaldaRenderCompat.pop(context);
            lineY += DESC_LINE_H;
        }
    }

    private static int withAlpha(int argb, float alpha) {
        int a = (int)(((argb >> 24) & 0xFF) * alpha);
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    // ─── Text helpers ─────────────────────────────────────────────────────────

    private static List<String> wrapText(TextRenderer tr, String text, int maxW) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) return lines;
        int scaledMax = (int)(maxW / DESC_SCALE);
        String[] words = text.split(" ");
        StringBuilder current = new StringBuilder();
        for (String word : words) {
            String candidate = current.isEmpty() ? word : current + " " + word;
            if (tr.getWidth(candidate) <= scaledMax) {
                current = new StringBuilder(candidate);
            } else {
                if (!current.isEmpty()) lines.add(current.toString());
                current = new StringBuilder(word);
            }
        }
        if (!current.isEmpty()) lines.add(current.toString());
        return lines;
    }

    private static int rawRowTextWidth(TextRenderer tr, String name, List<String> descLines) {
        int nameW = tr.getWidth(Text.literal(name + " ").formatted(Formatting.BOLD)) + 1;
        int descW = 0;
        for (String line : descLines) {
            int w = (int)(tr.getWidth(Text.literal(line).formatted(Formatting.ITALIC)) * DESC_SCALE);
            if (w > descW) descW = w;
        }
        return Math.max(nameW, descW);
    }

    private static int rowHeight(List<String> descLines) {
        int textH = 9 + Math.max(0, descLines.size()) * DESC_LINE_H;
        return PAD + Math.max(RING, textH) + PAD;
    }

    private static void drawRowText(DrawContext ctx, TextRenderer tr,
                                    int x, int y,
                                    String name, List<String> descLines,
                                    int maxW,
                                    int nameColor, int descColor) {
        drawRowText(ctx, tr, x, y, name, descLines, maxW, nameColor, descColor, false);
    }

    private static void drawRowText(DrawContext ctx, TextRenderer tr,
                                    int x, int y,
                                    String name, List<String> descLines,
                                    int maxW,
                                    int nameColor, int descColor,
                                    boolean strikethrough) {
        Text nameText = strikethrough
                ? Text.literal(name + " ").formatted(Formatting.BOLD, Formatting.STRIKETHROUGH)
                : Text.literal(name + " ").formatted(Formatting.BOLD);
        ctx.drawText(tr, nameText, x, y, nameColor, true);

        int descMaxW = (int)(maxW / DESC_SCALE);
        int lineY = y + 10;
        for (String line : descLines) {
            com.mixaold.burmalda.util.BurmaldaRenderCompat.push(ctx);
            com.mixaold.burmalda.util.BurmaldaRenderCompat.translate(ctx, x, lineY);
            com.mixaold.burmalda.util.BurmaldaRenderCompat.scale(ctx, DESC_SCALE, DESC_SCALE);
            ctx.drawText(tr, Text.literal(line).formatted(Formatting.ITALIC), 0, 0, descColor, false);
            com.mixaold.burmalda.util.BurmaldaRenderCompat.pop(ctx);
            lineY += DESC_LINE_H;
        }
    }

    // ─── Arc / ring rendering ──────────────────────────────────────────────────
    // The two rings are always the same size, so the annulus geometry (which pixels, each pixel's
    // angle and its track shade band) is constant — we compute it ONCE and from then on every frame
    // just does table lookups + a handful of merged ctx.fill() runs. No per-frame sqrt/atan2.

    private static boolean ringInit = false;
    private static int ringOuterR = -1, ringInnerR = -1;
    private static int[] ringDx, ringDy;            // annulus pixels, sorted by (dy, dx) — used by the arc
    private static float[] ringAng;                 // atan2 angle of each pixel, in degrees [0,360)
    private static int[] trackY, trackX0, trackX1;  // full-annulus horizontal runs (≈2 per row) — the track

    private static void ensureRing(int outerR, int innerR) {
        if (ringInit && outerR == ringOuterR && innerR == ringInnerR) return;
        int oR2 = outerR * outerR;
        int iR2 = innerR * innerR;
        int n = 0;
        for (int dy = -outerR; dy <= outerR; dy++)
            for (int dx = -outerR; dx <= outerR; dx++) {
                int d2 = dx * dx + dy * dy;
                if (d2 < oR2 && d2 >= iR2) n++;
            }
        ringDx = new int[n]; ringDy = new int[n]; ringAng = new float[n];
        int[] ty = new int[n], tx0 = new int[n], tx1 = new int[n]; // generously sized; trimmed below
        int i = 0, runs = 0;
        for (int dy = -outerR; dy <= outerR; dy++) {
            int rs = Integer.MIN_VALUE, re = 0; // current run [rs, re) within this row
            for (int dx = -outerR; dx <= outerR; dx++) {
                int d2 = dx * dx + dy * dy;
                if (d2 < oR2 && d2 >= iR2) {
                    double deg = Math.toDegrees(Math.atan2(dy, dx));
                    ringDx[i] = dx; ringDy[i] = dy;
                    ringAng[i] = (float) ((deg % 360 + 360) % 360);
                    i++;
                    if (rs == Integer.MIN_VALUE) { rs = dx; re = dx + 1; }
                    else if (dx == re) { re = dx + 1; }
                    else { ty[runs] = dy; tx0[runs] = rs; tx1[runs] = re; runs++; rs = dx; re = dx + 1; }
                } else if (rs != Integer.MIN_VALUE) {
                    ty[runs] = dy; tx0[runs] = rs; tx1[runs] = re; runs++; rs = Integer.MIN_VALUE;
                }
            }
            if (rs != Integer.MIN_VALUE) { ty[runs] = dy; tx0[runs] = rs; tx1[runs] = re; runs++; }
        }
        trackY  = java.util.Arrays.copyOf(ty, runs);
        trackX0 = java.util.Arrays.copyOf(tx0, runs);
        trackX1 = java.util.Arrays.copyOf(tx1, runs);
        ringOuterR = outerR; ringInnerR = innerR; ringInit = true;
    }

    // Solid track ring, drawn as the precomputed horizontal runs (≈2 per row) instead of one fill per
    // pixel. This is the single biggest per-frame HUD saver — ~250 fills/ring → ~45.
    private static void drawTrack(DrawContext context, int cx, int cy,
                                  int outerR, int innerR, int argb) {
        ensureRing(outerR, innerR);
        for (int r = 0; r < trackY.length; r++) {
            context.fill(cx + trackX0[r], cy + trackY[r], cx + trackX1[r], cy + trackY[r] + 1, argb);
        }
    }

    // Solid filled ring/arc between fromDeg..toDeg: we walk the precomputed annulus pixels (sorted by
    // row) and emit merged horizontal runs (1-2 rects per row), so the edge is smooth and it stays a
    // handful of ctx.fill() calls — no shader pipeline, version-stable, cheap.
    private static void drawArc(DrawContext context, int cx, int cy,
                                int outerR, int innerR,
                                float fromDeg, float toDeg, int argb) {
        float span = toDeg - fromDeg;
        if (span <= 0f) return;
        ensureRing(outerR, innerR);
        boolean full = span >= 360f;

        int curDy = Integer.MIN_VALUE;
        int runStart = Integer.MIN_VALUE, runEnd = 0; // runEnd exclusive (in dx units)
        for (int i = 0; i < ringDx.length; i++) {
            int dx = ringDx[i], dy = ringDy[i];
            if (dy != curDy) {
                if (runStart != Integer.MIN_VALUE) {
                    context.fill(cx + runStart, cy + curDy, cx + runEnd, cy + curDy + 1, argb);
                    runStart = Integer.MIN_VALUE;
                }
                curDy = dy;
            }
            boolean in = full || ((((ringAng[i] - fromDeg) % 360f) + 360f) % 360f) <= span;
            if (in) {
                if (runStart == Integer.MIN_VALUE) { runStart = dx; runEnd = dx + 1; }
                else if (dx == runEnd) { runEnd = dx + 1; }           // contiguous — extend the run
                else { context.fill(cx + runStart, cy + curDy, cx + runEnd, cy + curDy + 1, argb); runStart = dx; runEnd = dx + 1; }
            } else if (runStart != Integer.MIN_VALUE) {
                context.fill(cx + runStart, cy + curDy, cx + runEnd, cy + curDy + 1, argb);
                runStart = Integer.MIN_VALUE;
            }
        }
        if (runStart != Integer.MIN_VALUE) {
            context.fill(cx + runStart, cy + curDy, cx + runEnd, cy + curDy + 1, argb);
        }
    }
}
