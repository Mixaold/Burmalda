package com.mixaold.burmalda.hud;

import com.mixaold.burmalda.network.BurmaldaNetwork;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@Environment(EnvType.CLIENT)
public class GuideScreen extends Screen {

    // ── Page content (resolved at render time for localization) ───────────────
    private static final int[] PAGE_ACCENT = { 0xFF111008, 0xFF0F0E08, 0xFF10100A };

    private static String pageTitle(int page) {
        return switch (page) {
            case 0 -> Text.translatableWithFallback("burmalda.guide.title.1", "Добро пожаловать!").getString();
            case 1 -> Text.translatableWithFallback("burmalda.guide.title.2", "Дебаффы").getString();
            case 2 -> Text.translatableWithFallback("burmalda.guide.title.3", "Особые механики").getString();
            default -> "";
        };
    }

    private static String[] pageBody(int page) {
        return switch (page) {
            case 0 -> new String[]{
                Text.translatableWithFallback("burmalda.guide.page.1.1", "Burmalda — это мод с дебаффами.").getString(),
                Text.translatableWithFallback("burmalda.guide.page.1.2", "Каждые 3 минуты тебе выдаётся случайный дебафф.").getString(),
                Text.translatableWithFallback("burmalda.guide.page.1.3", "Нужно его выполнить или просто пережить.").getString(),
                Text.translatableWithFallback("burmalda.guide.page.1.4", "Таймер до следующего дебаффа всегда виден в HUD.").getString()
            };
            case 1 -> new String[]{
                Text.translatableWithFallback("burmalda.guide.page.2.1", "Соло-дебафф — только твой, меняется каждые 3 минуты.").getString(),
                Text.translatableWithFallback("burmalda.guide.page.2.2", "У некоторых есть задание. Не выполнишь — будут последствия.").getString(),
                Text.translatableWithFallback("burmalda.guide.page.2.3", "Групповой дебафф — срабатывает сразу на всех.").getString(),
                Text.translatableWithFallback("burmalda.guide.page.2.4", "В HUD видно сколько времени осталось до смены.").getString()
            };
            case 2 -> new String[]{
                Text.translatableWithFallback("burmalda.guide.page.3.1", "Колесо удачи — крутишь рулетку и получаешь результат.").getString(),
                Text.translatableWithFallback("burmalda.guide.page.3.2", "Может выпасть незерит с инструментами или смерть.").getString(),
                Text.translatableWithFallback("burmalda.guide.page.3.3", "Зоопарк, Молнии, Метеоритный шторм — групповые дебаффы.").getString(),
                Text.translatableWithFallback("burmalda.guide.page.3.4", "Групповые дебаффы могут выпасть с колеса удачи.").getString()
            };
            default -> new String[0];
        };
    }
    private static final int PAGE_COUNT = 3;

    // ── Textures ──────────────────────────────────────────────────────────────
    private static final Identifier TEX_PAGE_1 = Identifier.of("burmalda", "textures/gui/1.png");
    private static final Identifier TEX_PAGE_2 = Identifier.of("burmalda", "textures/gui/2.png");
    private static final Identifier TEX_PAGE_3 = Identifier.of("burmalda", "textures/gui/3.png");

    // ── Palette (золотистая) ──────────────────────────────────────────────────
    private static final int COL_CARD    = 0xFF1C1408;
    private static final int COL_HEADER  = 0xFF120E05;
    private static final int COL_ACCENT  = 0xFFFFAA00;
    private static final int COL_DIVIDER = 0xFF3D2910;
    private static final int COL_SHADOW  = 0xBB000000;
    private static final int COL_BTN     = 0xFF1C1408;
    private static final int COL_BTN_SI  = 0xFF3D2910;
    private static final int COL_TEXT_LO = 0xFFE8D5A3;
    private static final int COL_TEXT_HI = 0xFFFFCC44;
    private static final int COL_GREEN   = 0xFF55FF55;
    private static final int COL_LINK    = 0xFFFFAA55;

    // ── Static callbacks ──────────────────────────────────────────────────────
    private static GuideScreen INSTANCE;

    public static void onReadyUpdate(String readyNames) {
        if (INSTANCE != null) INSTANCE.handleReadyUpdate(readyNames);
    }
    public static void onStartCountdown() {
        if (INSTANCE != null) INSTANCE.handleStartCountdown();
    }
    public static void onCancelled() {
        if (INSTANCE != null) MinecraftClient.getInstance().setScreen(null);
    }

    // ── Screen state ──────────────────────────────────────────────────────────
    private enum Phase { CARD_IN, GUIDE, CONFIRM, COUNTDOWN, FADE_OUT }
    private Phase phase = Phase.CARD_IN;

    private final boolean  solo;
    private final String[] allPlayers;
    private final Set<String> readyPlayers = new HashSet<>();
    private boolean selfReady = false;

    private int currentPage = 0;

    // Content fade transition
    private boolean  transitioning   = false;
    private long     transitionStart  = 0;
    private static final long TRANS_MS = 180L;
    private Runnable onTransDone      = null;
    private float    contentAlpha     = 0f;

    // Countdown
    private long  countdownStart = 0;
    private int   lastSecShown   = 6;
    private float numScale = 1f, numVel = 0f;
    private static final long COUNTDOWN_MS = 5000L;

    // Fade-out
    private long fadeStart = 0;
    private static final long FADE_MS = 750L;

    // Delta time
    private long lastFrameNs = 0;

    // Card spring
    private float cardScale = 0f, cardScaleVel = 0f;
    private float cardTilt  = 0f, cardTiltVel  = 0f;

    // Button springs: A = Next/Начать, B = Back/Передумал, C = Пропустить
    private float btnAScale = 1f, btnAVel = 0f;
    private float btnBScale = 1f, btnBVel = 0f;
    private float btnCScale = 1f, btnCVel = 0f;

    // Card dimensions
    private int cardW = 380, cardH = 240;

    public GuideScreen(boolean solo, String playersStr) {
        super(Text.literal("Burmalda Guide"));
        this.solo       = solo;
        this.allPlayers = playersStr.isEmpty() ? new String[0] : playersStr.split(",");
    }

    @Override public boolean shouldPause()      { return false; }
    @Override public boolean shouldCloseOnEsc() { return false; }

    @Override
    protected void init() {
        INSTANCE      = this;
        phase         = Phase.CARD_IN;
        cardScale     = 0.04f;
        cardScaleVel  = 9f;
        cardTiltVel   = 22f;
        contentAlpha  = 0f;
        currentPage   = 0;
        transitioning = false;
        selfReady     = false;
        lastSecShown  = 6;
        playSound(SoundEvents.ENTITY_ITEM_PICKUP, 1.0f);
    }

    @Override
    public void removed() {
        if (INSTANCE == this) INSTANCE = null;
        super.removed();
    }

    // ── Network callbacks ─────────────────────────────────────────────────────
    private void handleReadyUpdate(String readyNames) {
        readyPlayers.clear();
        if (!readyNames.isEmpty()) readyPlayers.addAll(Arrays.asList(readyNames.split(",")));
    }

    private void handleStartCountdown() {
        if (phase == Phase.FADE_OUT) return;
        phase          = Phase.COUNTDOWN;
        countdownStart = System.currentTimeMillis();
        lastSecShown   = 6;
        numScale       = 1f;
        numVel         = 0f;
        playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f);
    }

    // ── Per-frame update ──────────────────────────────────────────────────────
    private void update() {
        long now = System.nanoTime();
        double dt = lastFrameNs == 0 ? 1.0 / 60.0 : Math.min(0.05, (now - lastFrameNs) / 1e9);
        lastFrameNs = now;

        if (phase != Phase.FADE_OUT) {
            float cAcc = -280f * (cardScale - 1f) - 13f * cardScaleVel;
            cardScaleVel += cAcc * (float) dt;
            cardScale    += cardScaleVel * (float) dt;
            cardScale     = Math.max(0f, Math.min(1.5f, cardScale));

            float tAcc = -200f * cardTilt - 12f * cardTiltVel;
            cardTiltVel += tAcc * (float) dt;
            cardTilt    += cardTiltVel * (float) dt;
            cardTilt     = Math.max(-25f, Math.min(25f, cardTilt));

            if (phase == Phase.CARD_IN && cardScale > 0.88f) phase = Phase.GUIDE;
        }

        if (transitioning) {
            long el = System.currentTimeMillis() - transitionStart;
            if (el < TRANS_MS) {
                contentAlpha = 1f - (float) el / TRANS_MS;
            } else {
                contentAlpha  = 0f;
                transitioning = false;
                if (onTransDone != null) { onTransDone.run(); onTransDone = null; }
                transitionStart = System.currentTimeMillis();
            }
        } else {
            long el = System.currentTimeMillis() - transitionStart;
            contentAlpha = Math.min(1f, (float) el / TRANS_MS);
        }

        btnAVel += (-320f * (btnAScale - 1f) - 20f * btnAVel) * (float) dt; btnAScale += btnAVel * (float) dt; btnAScale = Math.max(0.6f, Math.min(1.3f, btnAScale));
        btnBVel += (-320f * (btnBScale - 1f) - 20f * btnBVel) * (float) dt; btnBScale += btnBVel * (float) dt; btnBScale = Math.max(0.6f, Math.min(1.3f, btnBScale));
        btnCVel += (-320f * (btnCScale - 1f) - 20f * btnCVel) * (float) dt; btnCScale += btnCVel * (float) dt; btnCScale = Math.max(0.6f, Math.min(1.3f, btnCScale));

        if (phase == Phase.COUNTDOWN) {
            long el  = System.currentTimeMillis() - countdownStart;
            int  sec = 5 - (int)(el / 1000L);
            if (sec != lastSecShown) {
                lastSecShown = sec;
                numVel = 9f;
                if (sec > 0) playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f + sec * 0.07f);
                else         playSound(SoundEvents.ENTITY_PLAYER_LEVELUP, 1.0f);
            }
            numVel  += (-380f * (numScale - 1f) - 18f * numVel) * (float) dt;
            numScale += numVel * (float) dt;
            numScale  = Math.max(0.6f, Math.min(2.2f, numScale));
            if (el >= COUNTDOWN_MS + 200) {
                phase     = Phase.FADE_OUT;
                fadeStart = System.currentTimeMillis();
            }
        }
        // Closing is handled in render() via gFade check to avoid flash
    }

    // ── Button actions ────────────────────────────────────────────────────────
    private void onNext() {
        if (transitioning || phase != Phase.GUIDE) return;
        btnAVel -= 5f;
        playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.1f);
        if (currentPage < PAGE_COUNT - 1) {
            cardTiltVel += 8f;
            startTransition(() -> currentPage++);
        } else {
            cardScaleVel += -2f;
            cardTiltVel  += -14f;
            startTransition(() -> phase = Phase.CONFIRM);
        }
    }

    private void onBack() {
        if (transitioning || phase != Phase.GUIDE || currentPage == 0) return;
        btnBVel -= 5f;
        cardTiltVel -= 8f;
        playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 0.9f);
        startTransition(() -> currentPage--);
    }

    private void onSkip() {
        if (transitioning || (phase != Phase.GUIDE && phase != Phase.CARD_IN)) return;
        btnCVel -= 5f;
        cardScaleVel += -3f;
        cardTiltVel  += 18f;
        playSound(SoundEvents.ENTITY_PLAYER_LEVELUP, 1.0f);
        startTransition(() -> phase = Phase.CONFIRM);
    }

    private void onStart() {
        if (phase != Phase.CONFIRM || selfReady || transitioning) return;
        btnAVel  -= 5f;
        selfReady = true;
        playSound(SoundEvents.ENTITY_PLAYER_LEVELUP, 1.1f);
        if (solo) handleStartCountdown();
        ClientPlayNetworking.send(new BurmaldaNetwork.PlayerReadyPayload());
    }

    private void onCancel() {
        if (phase != Phase.CONFIRM || selfReady) return;
        btnBVel -= 5f;
        playSound(SoundEvents.ENTITY_ITEM_PICKUP, 0.5f);
        ClientPlayNetworking.send(new BurmaldaNetwork.PlayerCancelPayload());
        MinecraftClient.getInstance().setScreen(null);
    }

    private void startTransition(Runnable onDone) {
        transitioning   = true;
        transitionStart = System.currentTimeMillis();
        onTransDone     = onDone;
    }

    // ── Rendering ─────────────────────────────────────────────────────────────
    @Override public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {}

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        update();

        float gFade = 1.0f;
        if (phase == Phase.FADE_OUT) {
            double t = Math.min(1.0, (System.currentTimeMillis() - fadeStart) / (double) FADE_MS);
            gFade = (float)(1.0 - t);
            if (gFade <= 0f) {
                MinecraftClient.getInstance().setScreen(null);
                return; // don't draw anything — avoids 1-frame flash
            }
        }

        ctx.fill(0, 0, width, height, fa(0xAA000000, gFade));

        cardW = 380;
        cardH = (phase == Phase.CONFIRM || phase == Phase.COUNTDOWN)
                ? (solo ? 170 : Math.min(300, 155 + allPlayers.length * 18))
                : 240;

        com.mixaold.burmalda.util.BurmaldaRenderCompat.push(ctx);
        com.mixaold.burmalda.util.BurmaldaRenderCompat.translate(ctx, width / 2.0, height / 2.0);
        com.mixaold.burmalda.util.BurmaldaRenderCompat.rotateZ(ctx, cardTilt);
        com.mixaold.burmalda.util.BurmaldaRenderCompat.scale(ctx, cardScale, cardScale);
        com.mixaold.burmalda.util.BurmaldaRenderCompat.translate(ctx, -cardW / 2.0, -cardH / 2.0);

        drawCardBg(ctx, cardW, cardH, gFade);

        int ca = (int)(contentAlpha * 255 * gFade);
        if (ca > 0) {
            if (phase == Phase.GUIDE || phase == Phase.CARD_IN) {
                drawGuide(ctx, cardW, cardH, ca);
            } else if (phase == Phase.CONFIRM) {
                drawConfirm(ctx, cardW, cardH, ca);
            } else if (phase == Phase.COUNTDOWN) {
                drawCountdown(ctx, cardW, cardH, ca);
            }
        }

        com.mixaold.burmalda.util.BurmaldaRenderCompat.pop(ctx);
    }

    // ── Card background (dark-slate style) ───────────────────────────────────
    private void drawCardBg(DrawContext ctx, int w, int h, float gFade) {
        ctx.fill(-6, -6, w + 6, h + 6, fa(COL_SHADOW,  gFade));
        ctx.fill(0, 0, w, h,           fa(COL_CARD,    gFade));
        ctx.fill(0, 0, w, 24,          fa(COL_HEADER,  gFade));
        ctx.fill(0, 0, w, 2,           fa(COL_ACCENT,  gFade)); // orange top line
        ctx.fill(0, 24, w, 25,         fa(COL_DIVIDER, gFade)); // header separator
        ctx.fill(0, 2, 2, 24,          fa(COL_DIVIDER, gFade));
        ctx.fill(w - 2, 2, w, 24,      fa(COL_DIVIDER, gFade));
        ctx.fill(0, h - 1, w, h,       fa(COL_ACCENT,  gFade)); // thin orange bottom

        String logo = "BURMALDA";
        int lw = textRenderer.getWidth("§l" + logo);
        ctx.drawText(textRenderer, Text.literal("§l" + logo),
                w / 2 - lw / 2, 8, fa(0xFFFFFFFF, gFade), true);
    }

    // ── Guide pages ───────────────────────────────────────────────────────────
    private void drawGuide(DrawContext ctx, int w, int h, int a) {
        // Page dots
        int dotY = 32;
        for (int i = 0; i < PAGE_COUNT; i++) {
            int dx = w / 2 - (PAGE_COUNT * 10) / 2 + i * 10 + 2;
            ctx.fill(dx, dotY, dx + 6, dotY + 6,
                    alpha(i == currentPage ? COL_ACCENT : COL_DIVIDER, a));
        }

        // Title
        String title = pageTitle(currentPage);
        int tw = textRenderer.getWidth(title);
        ctx.drawText(textRenderer, Text.literal("§l" + title),
                w / 2 - tw / 2, 44, alpha(COL_TEXT_HI, a), false);

        // Separator
        ctx.fill(20, 59, w - 20, 60, alpha(COL_DIVIDER, a));

        // Image area
        int imgX = 20, imgY = 66, imgW = w - 40, imgH = 74;
        Identifier pageTex = currentPage == 0 ? TEX_PAGE_1 : (currentPage == 1 ? TEX_PAGE_2 : TEX_PAGE_3);
        if (pageTex != null) {
            com.mixaold.burmalda.util.BurmaldaRenderCompat.drawGuiTexture(ctx, pageTex, imgX, imgY, imgW, imgH, a / 255f);
        } else {
            ctx.fill(imgX, imgY, imgX + imgW, imgY + imgH, alpha(PAGE_ACCENT[currentPage], a));
            ctx.fill(imgX, imgY, imgX + imgW, imgY + 1, alpha(COL_DIVIDER, a));
            ctx.fill(imgX, imgY + imgH - 1, imgX + imgW, imgY + imgH, alpha(COL_DIVIDER, a));
            ctx.fill(imgX, imgY, imgX + 1, imgY + imgH, alpha(COL_DIVIDER, a));
            ctx.fill(imgX + imgW - 1, imgY, imgX + imgW, imgY + imgH, alpha(COL_DIVIDER, a));
            String ph = "[ Скриншот 3 ]";
            int plw = textRenderer.getWidth(ph);
            ctx.drawText(textRenderer, Text.literal(ph),
                    imgX + (imgW - plw) / 2, imgY + (imgH - 8) / 2, alpha(COL_DIVIDER, a), false);
        }

        // "by Mixaold <3" clickable credit — only on page 1
        if (currentPage == 0) {
            String credit = "by Mixaold <3";
            int cw = textRenderer.getWidth(credit);
            int cx = imgX + imgW - cw - 4;
            int cy = imgY + imgH - 11;
            ctx.drawText(textRenderer, Text.literal(credit), cx, cy, alpha(COL_LINK, a), false);
            ctx.fill(cx, cy + 9, cx + cw, cy + 10, alpha(COL_LINK, a)); // underline
        }

        // Body text
        int ty = 148;
        for (String line : pageBody(currentPage)) {
            int lw2 = textRenderer.getWidth(line);
            ctx.drawText(textRenderer, Text.literal(line),
                    w / 2 - lw2 / 2, ty, alpha(COL_TEXT_LO, a), false);
            ty += 11;
        }

        // Buttons
        int bY = h - 26, bH = 18;
        if (currentPage > 0) drawBtn(ctx, 14, bY, 72, bH, "§7" + Text.translatableWithFallback("burmalda.guide.btn.back", "← Назад").getString(), btnBScale, true, false, a);
        drawBtn(ctx, w / 2 - 46, bY, 92, bH, "§7" + Text.translatableWithFallback("burmalda.guide.btn.skip", "Пропустить").getString(), btnCScale, true, false, a);
        String nextKey = currentPage == PAGE_COUNT - 1 ? "burmalda.guide.btn.done" : "burmalda.guide.btn.next";
        String nextFb  = currentPage == PAGE_COUNT - 1 ? "Готово! →" : "Далее →";
        drawBtn(ctx, w - 86, bY, 72, bH, "§e" + Text.translatableWithFallback(nextKey, nextFb).getString(), btnAScale, true, false, a);
    }

    // ── Confirm screen ────────────────────────────────────────────────────────
    private void drawConfirm(DrawContext ctx, int w, int h, int a) {
        String title = Text.translatableWithFallback("burmalda.guide.confirm.title", "Запустить мод?").getString();
        int tw = textRenderer.getWidth(title);
        ctx.drawText(textRenderer, Text.literal("§l" + title),
                w / 2 - tw / 2, 33, alpha(COL_TEXT_HI, a), false);
        ctx.fill(20, 49, w - 20, 50, alpha(COL_DIVIDER, a));

        int cy = 60;

        if (solo) {
            String msg = Text.translatableWithFallback("burmalda.guide.confirm.solo", "Ты один — жми и стартуем!").getString();
            int mw = textRenderer.getWidth(msg);
            ctx.drawText(textRenderer, Text.literal(msg),
                    w / 2 - mw / 2, cy, alpha(COL_TEXT_LO, a), false);
            cy += 28;
        } else {
            for (String name : allPlayers) {
                boolean rdy = readyPlayers.contains(name)
                        || (selfReady && name.equals(localName()));
                String row = (rdy ? "§a✓ §f" : "§8○ §7") + name;
                ctx.drawText(textRenderer, Text.literal(row), 30, cy, alpha(0xFFFFFFFF, a), false);
                cy += 17;
            }
            cy += 8;
        }

        if (!selfReady) {
            // "Начать" and "Передумал" side by side
            int bW = 104, bH = 22, gap = 10;
            int startX = w / 2 - (bW * 2 + gap) / 2;
            int cancelX = startX + bW + gap;
            drawBtn(ctx, startX,  cy, bW, bH, "§a" + Text.translatableWithFallback("burmalda.guide.btn.start",  "✔ Начать").getString(),    btnAScale, true, false, a);
            drawBtn(ctx, cancelX, cy, bW, bH, "§c" + Text.translatableWithFallback("burmalda.guide.btn.cancel", "✘ Передумал").getString(), btnBScale, true, true,  a);
        } else {
            String wait = solo
                ? Text.translatableWithFallback("burmalda.guide.confirm.starting", "Запуск...").getString()
                : Text.translatableWithFallback("burmalda.guide.confirm.waiting",  "Ожидание игроков...").getString();
            int ww = textRenderer.getWidth(wait);
            ctx.drawText(textRenderer, Text.literal(wait),
                    w / 2 - ww / 2, cy + 7, alpha(COL_DIVIDER, a), false);
        }
    }

    // ── Countdown ─────────────────────────────────────────────────────────────
    private void drawCountdown(DrawContext ctx, int w, int h, int a) {
        int sec = Math.max(0, 5 - (int)((System.currentTimeMillis() - countdownStart) / 1000L));

        String msg = Text.translatableWithFallback("burmalda.guide.countdown.msg", "Мод запускается через...").getString();
        int mw = textRenderer.getWidth(msg);
        ctx.drawText(textRenderer, Text.literal(msg),
                w / 2 - mw / 2, 36, alpha(COL_TEXT_LO, a), false);

        String num = String.valueOf(sec);
        com.mixaold.burmalda.util.BurmaldaRenderCompat.push(ctx);
        com.mixaold.burmalda.util.BurmaldaRenderCompat.translate(ctx, w / 2.0, h / 2.0 + 14);
        com.mixaold.burmalda.util.BurmaldaRenderCompat.scale(ctx, numScale * 3.2f, numScale * 3.2f);
        int nc = sec <= 1 ? 0xFFFF4444 : (sec <= 3 ? 0xFFFF8800 : COL_TEXT_HI);
        int nw = textRenderer.getWidth(num);
        ctx.drawText(textRenderer, Text.literal(num), -nw / 2, -4, nc, true);
        com.mixaold.burmalda.util.BurmaldaRenderCompat.pop(ctx);

        String go = Text.translatableWithFallback("burmalda.guide.countdown.go", "ПОЕХАЛИ!").getString();
        int gw = textRenderer.getWidth("§l" + go);
        ctx.drawText(textRenderer, Text.literal("§l" + go),
                w / 2 - gw / 2, h - 34, alpha(COL_GREEN, a), true);
    }

    // ── Button helper — danger=true for red-tinted cancel-style buttons ───────
    private void drawBtn(DrawContext ctx, int x, int y, int w, int h,
                         String label, float scale, boolean active, boolean danger, int a) {
        com.mixaold.burmalda.util.BurmaldaRenderCompat.push(ctx);
        float cx = x + w / 2f, cy = y + h / 2f;
        com.mixaold.burmalda.util.BurmaldaRenderCompat.translate(ctx, cx, cy);
        com.mixaold.burmalda.util.BurmaldaRenderCompat.scale(ctx, scale, scale);
        com.mixaold.burmalda.util.BurmaldaRenderCompat.translate(ctx, -cx, -cy);

        int bodyCol = active ? (danger ? 0xFF2A1020 : COL_BTN) : 0xFF0E1A22;
        int topCol  = active ? (danger ? 0xFFDD4466 : COL_ACCENT) : COL_DIVIDER;
        int sideCol = active ? COL_BTN_SI : 0xFF1A1A1A;

        ctx.fill(x,         y,         x + w,     y + h,     alpha(bodyCol, a));
        ctx.fill(x,         y,         x + w,     y + 1,     alpha(topCol,  a));
        ctx.fill(x,         y + h - 1, x + w,     y + h,     alpha(sideCol, a));
        ctx.fill(x,         y + 1,     x + 1,     y + h - 1, alpha(sideCol, a));
        ctx.fill(x + w - 1, y + 1,     x + w,     y + h - 1, alpha(sideCol, a));

        int lw = textRenderer.getWidth(label);
        ctx.drawText(textRenderer, Text.literal(label),
                x + (w - lw) / 2, y + (h - 8) / 2,
                alpha(active ? 0xFFFFFFFF : 0xFF555555, a), false);

        com.mixaold.burmalda.util.BurmaldaRenderCompat.pop(ctx);
    }

    // ── Mouse input ───────────────────────────────────────────────────────────
    @Override
    //? if >=1.21.11 {
    /*public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean doubled) {
        double mx = click.x(), my = click.y();
        int button = click.button();
    *///? } else {
    public boolean mouseClicked(double mx, double my, int button) {
    //? }
        if (button != 0 || transitioning) return false;

        // Approximate card-local coords (valid when cardScale ≈ 1 and cardTilt ≈ 0)
        double lx = mx - (width  / 2.0 - cardW / 2.0);
        double ly = my - (height / 2.0 - cardH / 2.0);

        if (phase == Phase.GUIDE) {
            // "by Mixaold <3" credit link on page 1
            if (currentPage == 0) {
                String credit = "by Mixaold <3";
                int cw = textRenderer.getWidth(credit);
                int imgX = 20, imgW = cardW - 40, imgH = 74, imgY = 66;
                int cx = imgX + imgW - cw - 4;
                int cy = imgY + imgH - 11;
                if (in(lx, cx, cw + 4) && in(ly, cy, 11)) {
                    Style link = Style.EMPTY.withClickEvent(
                            //? if >=1.21.5 {
                            /*new ClickEvent.OpenUrl(java.net.URI.create("https://www.youtube.com/@MIXAOLD"))
                            *///? } else {
                            new ClickEvent(ClickEvent.Action.OPEN_URL,
                                    "https://www.youtube.com/@MIXAOLD")
                            //? }
                    );
                    //? if >=1.21.11 {
                    /*net.minecraft.client.gui.screen.Screen.handleClickEvent(link.getClickEvent(), this.client, this);
                    *///? } else {
                    this.handleTextClick(link);
                    //? }
                    return true;
                }
            }
            int bY = cardH - 26, bH = 18;
            if (in(ly, bY, bH)) {
                if (currentPage > 0 && in(lx, 14, 72))         { onBack();  return true; }
                if (in(lx, cardW / 2 - 46, 92))                 { onSkip();  return true; }
                if (in(lx, cardW - 86, 72))                     { onNext();  return true; }
            }
        } else if (phase == Phase.CONFIRM && !selfReady) {
            int cy = solo ? 88 : (60 + allPlayers.length * 17 + 8);
            int bW = 104, bH = 22, gap = 10;
            int startX  = cardW / 2 - (bW * 2 + gap) / 2;
            int cancelX = startX + bW + gap;
            if (in(ly, cy, bH)) {
                if (in(lx, startX,  bW)) { onStart();  return true; }
                if (in(lx, cancelX, bW)) { onCancel(); return true; }
            }
        } else if (phase == Phase.COUNTDOWN) {
            // Click anywhere to skip a second — reuses the exact same per-frame logic
            // (digit-pop spring + sound, and eventual fade-out) as natural time passing.
            countdownStart -= 1000L;
            return true;
        }
        return false;
    }

    private static boolean in(double v, int start, int size) {
        return v >= start && v <= start + size;
    }

    // ── Utilities ─────────────────────────────────────────────────────────────
    private static int alpha(int argb, int a) {
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    private static int fa(int argb, float fade) {
        int a = (int)(((argb >> 24) & 0xFF) * fade);
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    private String localName() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc.player != null ? mc.player.getName().getString() : "";
    }

    private void playSound(net.minecraft.sound.SoundEvent sound, float pitch) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null && mc.getSoundManager() != null)
            mc.getSoundManager().play(com.mixaold.burmalda.util.BurmaldaRenderCompat.uiSound(sound, pitch));
    }
}
