package com.mixaold.burmalda.hud;

import com.mixaold.burmalda.network.BurmaldaNetwork;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.RotationAxis;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Environment(EnvType.CLIENT)
public class CasinoScreen extends Screen {

    private static final String[] STATIC_NAMES = {
        "Immunity", "Amnesty", "Netherite", "Apocalypse", null, null, "Death"
    };
    private static final int[] SECTOR_COLORS = {
        0xFF5FA84A,  // 0 Immunity   — green
        0xFF8DE265,  // 1 Amnesty    — light green
        0xFF3AA8C0,  // 2 Netherite  — cyan
        0xFFC33B3B,  // 3 Apocalypse — red
        0xFFB8487A,  // 4 Group 1    — pink
        0xFF8C4EB8,  // 5 Group 2    — purple
        0xFF444444   // 6 Death      — dark grey
    };
    private static final boolean[] SECTOR_GOOD = {
        true, true, true, false, false, false, false
    };
    private static final int[] FIREWORK_COLORS = {
        0xFFCC00, 0xFFFFFF, 0xFF5555, 0x55FFFF, 0x55FF55, 0xFF88FF, 0xFF8800, 0xAAFFAA
    };

    private static final int  N              = 7;
    private int               ROW_H          = 28; // shrinks in init() to fit small windows
    private static final int  ROWS           = 9;
    private static final long SPIN_DUR_MS    = 5000L;
    private static final long RESULT_SHOW_MS = 3000L;
    private static final long FADE_OUT_MS    = 800L;

    // ─── Simple 2-D particle ────────────────────────────────────────────────────
    private static class Particle {
        float x, y, vx, vy, life;
        int color;
    }

    private static CasinoScreen INSTANCE;

    public static void onResultReceived(int section) {
        if (INSTANCE != null) INSTANCE.handleResult(section);
    }
    public static void onTimeout() {
        if (INSTANCE != null) INSTANCE.handleTimeout();
    }

    private final String group1Id, group1Name, group2Id, group2Name;

    private enum Phase { INTRO_DELAY, INTRO_FADE, INTRO_CARD_IN, INTRO_SHAKE, INTRO_CARD_OUT, IDLE, SPINNING, RESULT, FADE_OUT }
    private Phase phase           = Phase.INTRO_DELAY;
    private int   resultSection   = -1;
    private long  openTimeMs      = 0;
    private long  resultShownAtMs = 0;
    private long  fadeOutStartMs  = 0;

    // Intro animation fields
    private long  introPhaseStartMs  = 0;
    private float introOverlay       = 0f;
    private float introCardScale     = 0f;
    private float introCardScaleVel  = 0f;
    private float introShakeX        = 0f;

    // Slot scroll animation
    private double scrollPos   = 0;
    private double spinFrom    = 0;
    private double spinTo      = 0;
    private long   spinStartMs = 0;

    private int   lastSoundSector = -1;
    private long  lastFrameNs     = 0L;
    private float arrowWobble     = 0f;
    private float arrowWobbleVel  = 0f;

    // Button bounce spring (rest = 1.0)
    private float buttonScale    = 1.0f;
    private float buttonScaleVel = 0f;

    // Result card pop-in spring
    private float   cardScale    = 0f;
    private float   cardScaleVel = 0f;
    private float   cardTilt     = 0f;
    private float   cardTiltVel  = 0f;
    private boolean cardAnimInit = false;

    // Screen open bounce spring
    private float screenScale    = 0.04f;
    private float screenScaleVel = 9f;

    private final List<Particle> particles = new ArrayList<>();

    private ButtonWidget playButton;

    public CasinoScreen(String g1Id, String g1Name, String g2Id, String g2Name) {
        super(Text.translatableWithFallback("burmalda.casino.title", "Wheel of Fortune"));
        this.group1Id   = g1Id;
        this.group1Name = g1Name;
        this.group2Id   = g2Id;
        this.group2Name = g2Name;
    }

    @Override public boolean shouldPause()      { return false; }
    @Override public boolean shouldCloseOnEsc() { return false; }

    @Override
    protected void init() {
        INSTANCE          = this;
        // Shrink row height so the whole machine (title + rows + legs + button) fits small windows.
        ROW_H             = Math.max(14, Math.min(28, (height - 130) / ROWS));
        openTimeMs        = 0;
        scrollPos         = 0;
        buttonScale       = 1.0f;
        buttonScaleVel    = 0f;
        cardScale         = 0f;
        cardScaleVel      = 0f;
        cardTilt          = 0f;
        cardTiltVel       = 0f;
        cardAnimInit      = false;
        fadeOutStartMs    = 0;
        screenScale       = 0.04f;
        screenScaleVel    = 9f;
        phase             = Phase.INTRO_DELAY;
        introPhaseStartMs = System.currentTimeMillis();
        introOverlay      = 0f;
        introCardScale    = 0f;
        introCardScaleVel = 0f;
        introShakeX       = 0f;
        particles.clear();
        // Position button below slot machine legs, centered
        int slotH  = ROWS * ROW_H;
        int slotCy = height / 2 - 10;
        int slotY  = slotCy - slotH / 2;
        int frameB = slotY + slotH + 6;
        int bw = 110, bh = 20;
        int bx = (width - bw) / 2;
        int by = Math.min(frameB + 46, height - bh - 6);
        playButton = ButtonWidget.builder(Text.translatableWithFallback("burmalda.casino.btn.play", "Play!"), b -> onPlay())
                .dimensions(bx, by, bw, bh).build();
        // Not added via addDrawableChild — rendered manually with bounce matrix
    }

    @Override
    public void removed() {
        if (INSTANCE == this) INSTANCE = null;
        super.removed();
    }

    // ─── Network handlers ─────────────────────────────────────────────────────

    private void handleResult(int section) {
        if (phase != Phase.IDLE) return;
        startSpinTo(Math.max(0, Math.min(N - 1, section)));
    }

    private void handleTimeout() {
        if (phase == Phase.IDLE) startSpinTo(6);
    }

    private void onPlay() {
        if (phase != Phase.IDLE) return;
        buttonScaleVel -= 5f;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null && mc.getSoundManager() != null) {
            mc.getSoundManager().play(com.mixaold.burmalda.util.BurmaldaRenderCompat.uiSound(
                    SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.2f));
        }
        ClientPlayNetworking.send(new BurmaldaNetwork.CasinoSpinPayload());
        if (playButton != null) {
            playButton.active = false;
            playButton.setMessage(Text.translatableWithFallback("burmalda.casino.btn.spinning", "Spinning..."));
        }
    }

    private void startSpinTo(int section) {
        resultSection = section;
        phase         = Phase.SPINNING;
        spinStartMs   = System.currentTimeMillis();
        spinFrom      = scrollPos;
        int cur   = ((int) Math.round(scrollPos) % N + N) % N;
        int delta = ((section - cur) % N + N) % N;
        if (delta == 0) delta = N;
        spinTo          = scrollPos + 6 * N + delta;
        lastSoundSector = cur;
        if (playButton != null) {
            playButton.active = false;
            playButton.setMessage(Text.translatableWithFallback("burmalda.casino.btn.spinning", "Spinning..."));
        }
    }

    private static double easeOutQuart(double t) { return 1.0 - Math.pow(1.0 - t, 4); }

    // ─── Per-frame update ─────────────────────────────────────────────────────

    private void update() {
        long now = System.nanoTime();
        double dt = lastFrameNs == 0L ? 1.0 / 60.0 : Math.min(0.05, (now - lastFrameNs) / 1e9);
        lastFrameNs = now;
        long nowMs = System.currentTimeMillis();

        // ── Intro phases ──────────────────────────────────────────────────────
        if (phase == Phase.INTRO_DELAY) {
            if (nowMs - introPhaseStartMs >= 800L) {
                phase = Phase.INTRO_FADE;
                introPhaseStartMs = nowMs;
                introOverlay = 0f;
            }
            return;
        }
        if (phase == Phase.INTRO_FADE) {
            introOverlay = Math.min(1f, (float)(nowMs - introPhaseStartMs) / 800f);
            if (introOverlay >= 1f) {
                phase = Phase.INTRO_CARD_IN;
                introPhaseStartMs = nowMs;
                introCardScale    = 0.04f;
                introCardScaleVel = 12f;
                playIntroSound();
            }
            return;
        }
        if (phase == Phase.INTRO_CARD_IN) {
            float cAcc = -300f * (introCardScale - 1.0f) - 14f * introCardScaleVel;
            introCardScaleVel += cAcc * (float) dt;
            introCardScale    += introCardScaleVel * (float) dt;
            introCardScale     = Math.max(0f, Math.min(1.5f, introCardScale));
            if (nowMs - introPhaseStartMs >= 1200L) {
                introCardScale    = 1f;
                introCardScaleVel = 0f;
                phase = Phase.INTRO_SHAKE;
                introPhaseStartMs = nowMs;
            }
            return;
        }
        if (phase == Phase.INTRO_SHAKE) {
            long elapsed = nowMs - introPhaseStartMs;
            introShakeX = (float)(10.0 * Math.sin(elapsed / 1000.0 * Math.PI * 4));
            if (elapsed >= 3000L) {
                introShakeX       = 0f;
                phase             = Phase.INTRO_CARD_OUT;
                introPhaseStartMs = nowMs;
                introCardScaleVel = -6f;
            }
            return;
        }
        if (phase == Phase.INTRO_CARD_OUT) {
            float cAcc = -300f * introCardScale - 14f * introCardScaleVel;
            introCardScaleVel += cAcc * (float) dt;
            introCardScale    += introCardScaleVel * (float) dt;
            introOverlay       = Math.max(0f, introCardScale);
            introCardScale     = Math.max(0f, introCardScale);
            if (introCardScale <= 0.01f) {
                introCardScale = 0f;
                introOverlay   = 0f;
                phase          = Phase.IDLE;
                openTimeMs     = nowMs;
                screenScale    = 0.04f;
                screenScaleVel = 9f;
            }
            return;
        }
        // ─────────────────────────────────────────────────────────────────────

        if (phase == Phase.SPINNING) {
            long   elapsed = System.currentTimeMillis() - spinStartMs;
            double t       = Math.min(1.0, elapsed / (double) SPIN_DUR_MS);
            scrollPos = spinFrom + (spinTo - spinFrom) * easeOutQuart(t);

            int cur = ((int) Math.round(scrollPos) % N + N) % N;
            if (lastSoundSector >= 0 && cur != lastSoundSector) {
                float intensity = (float)(1.0 - easeOutQuart(t));
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc != null && mc.getSoundManager() != null) {
                    mc.getSoundManager().play(com.mixaold.burmalda.util.BurmaldaRenderCompat.uiSound(
                            SoundEvents.UI_BUTTON_CLICK.value(), 0.5f + intensity * 0.9f));
                }
                arrowWobbleVel += 12f * (0.3f + 0.7f * intensity);
            }
            lastSoundSector = cur;

            if (t >= 1.0) {
                scrollPos = spinTo;
                phase     = Phase.RESULT;
                resultShownAtMs = System.currentTimeMillis();
                playFanfare(SECTOR_GOOD[resultSection]);
            }
        }

        // Screen open bounce spring
        float sAcc = -280f * (screenScale - 1f) - 13f * screenScaleVel;
        screenScaleVel += sAcc * (float) dt;
        screenScale    += screenScaleVel * (float) dt;
        screenScale     = Math.max(0f, Math.min(1.5f, screenScale));

        // Arrow spring physics
        float acc = -260f * arrowWobble - 15f * arrowWobbleVel;
        arrowWobbleVel += acc * (float) dt;
        arrowWobble    += arrowWobbleVel * (float) dt;
        arrowWobble     = Math.max(-14f, Math.min(14f, arrowWobble));

        // Button bounce spring (rest = 1.0)
        float bAcc = -320f * (buttonScale - 1.0f) - 20f * buttonScaleVel;
        buttonScaleVel += bAcc * (float) dt;
        buttonScale    += buttonScaleVel * (float) dt;
        buttonScale     = Math.max(0.5f, Math.min(1.4f, buttonScale));

        // Result card pop-in spring
        if (phase == Phase.RESULT) {
            if (!cardAnimInit) {
                cardAnimInit = true;
                cardScale    = 0.05f;
                cardScaleVel = 10f;
                cardTiltVel  = 25f;
                if (SECTOR_GOOD[resultSection]) {
                    emitFireworks();
                    playFireworkSounds();
                }
            }
            float cAcc = -300f * (cardScale - 1.0f) - 14f * cardScaleVel;
            cardScaleVel += cAcc * (float) dt;
            cardScale    += cardScaleVel * (float) dt;
            cardScale     = Math.max(0f, Math.min(1.5f, cardScale));

            float tAcc = -180f * cardTilt - 10f * cardTiltVel;
            cardTiltVel += tAcc * (float) dt;
            cardTilt    += cardTiltVel * (float) dt;
            cardTilt     = Math.max(-30f, Math.min(30f, cardTilt));
        }

        // Particle physics
        Iterator<Particle> pit = particles.iterator();
        while (pit.hasNext()) {
            Particle p = pit.next();
            p.x    += p.vx * (float) dt;
            p.y    += p.vy * (float) dt;
            p.vy   += 200f * (float) dt; // gravity
            p.life -= (float) dt / 1.5f;  // ~1.5 s lifetime
            if (p.life <= 0) pit.remove();
        }

        // RESULT → FADE_OUT after display time
        if (phase == Phase.RESULT && resultShownAtMs > 0
                && System.currentTimeMillis() - resultShownAtMs > RESULT_SHOW_MS) {
            phase          = Phase.FADE_OUT;
            fadeOutStartMs = System.currentTimeMillis();
        }

        // FADE_OUT: closing is handled in render() after gFade reaches 0 to avoid flash
    }

    private void emitFireworks() {
        int[] bx = { width / 2 - 100, width / 2, width / 2 + 100 };
        int[] by = { height / 2 - 10,  height / 2 - 50, height / 2 - 10 };
        for (int b = 0; b < bx.length; b++) {
            for (int i = 0; i < 20; i++) {
                Particle p = new Particle();
                p.x     = bx[b];
                p.y     = by[b];
                float angle = (float)(Math.random() * Math.PI * 2);
                float speed = 60f + (float)(Math.random() * 130f);
                p.vx    = (float) Math.cos(angle) * speed;
                p.vy    = (float) Math.sin(angle) * speed - 50f;
                p.life  = 0.5f + (float)(Math.random() * 1.0f);
                p.color = FIREWORK_COLORS[(int)(Math.random() * FIREWORK_COLORS.length)];
                particles.add(p);
            }
        }
    }

    private void playFireworkSounds() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getSoundManager() == null) return;
        mc.getSoundManager().play(com.mixaold.burmalda.util.BurmaldaRenderCompat.uiSound(
                SoundEvents.ENTITY_FIREWORK_ROCKET_BLAST, 1.0f));
        mc.getSoundManager().play(com.mixaold.burmalda.util.BurmaldaRenderCompat.uiSound(
                SoundEvents.ENTITY_FIREWORK_ROCKET_TWINKLE, 0.8f));
    }

    private void playFanfare(boolean good) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getSoundManager() == null) return;
        mc.getSoundManager().play(com.mixaold.burmalda.util.BurmaldaRenderCompat.uiSound(
                good ? SoundEvents.ENTITY_PLAYER_LEVELUP : SoundEvents.ENTITY_WITHER_AMBIENT,
                good ? 1.0f : 0.6f));
    }

    // ─── Rendering ────────────────────────────────────────────────────────────

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) { }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        update();

        // ── Intro phases ──────────────────────────────────────────────────────
        if (phase == Phase.INTRO_DELAY) return;
        if (phase == Phase.INTRO_FADE || phase == Phase.INTRO_CARD_IN
                || phase == Phase.INTRO_SHAKE || phase == Phase.INTRO_CARD_OUT) {
            ctx.fill(0, 0, width, height, fa(0xBB000000, introOverlay));
            drawIntroCard(ctx);
            return;
        }

        // During FADE_OUT, gFade linearly drops 1→0 so all elements dissolve into the game world
        float gFade = 1.0f;
        if (phase == Phase.FADE_OUT) {
            double t = Math.min(1.0, (System.currentTimeMillis() - fadeOutStartMs) / (double) FADE_OUT_MS);
            gFade = (float)(1.0 - t);
            if (gFade <= 0f) {
                MinecraftClient.getInstance().setScreen(null);
                return; // don't draw anything — avoids 1-frame flash
            }
        }

        ctx.fill(0, 0, width, height, fa(0xBB000000, gFade));

        // Screen open bounce — wrap slot machine + button + timer
        com.mixaold.burmalda.util.BurmaldaRenderCompat.push(ctx);
        com.mixaold.burmalda.util.BurmaldaRenderCompat.translate(ctx, width / 2.0, height / 2.0);
        com.mixaold.burmalda.util.BurmaldaRenderCompat.scale(ctx, screenScale, screenScale);
        com.mixaold.burmalda.util.BurmaldaRenderCompat.translate(ctx, -width / 2.0, -height / 2.0);

        drawSlotMachine(ctx, gFade);
        drawTimer(ctx);

        // Play button — rendered manually with bounce scale, hidden during result/fade
        if (playButton != null && phase != Phase.RESULT && phase != Phase.FADE_OUT) {
            com.mixaold.burmalda.util.BurmaldaRenderCompat.push(ctx);
            float bcx = playButton.getX() + playButton.getWidth() / 2f;
            float bcy = playButton.getY() + playButton.getHeight() / 2f;
            com.mixaold.burmalda.util.BurmaldaRenderCompat.translate(ctx, bcx, bcy);
            com.mixaold.burmalda.util.BurmaldaRenderCompat.scale(ctx, buttonScale, buttonScale);
            com.mixaold.burmalda.util.BurmaldaRenderCompat.translate(ctx, -bcx, -bcy);
            playButton.render(ctx, mouseX, mouseY, delta);
            com.mixaold.burmalda.util.BurmaldaRenderCompat.pop(ctx);
        }

        com.mixaold.burmalda.util.BurmaldaRenderCompat.pop(ctx);

        if (phase == Phase.RESULT || phase == Phase.FADE_OUT) {
            drawResultCard(ctx, gFade);
            drawParticles(ctx, gFade);
        }
    }

    @Override
    //? if >=1.21.11 {
    /*public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean doubled) {
        double mouseX = click.x(), mouseY = click.y();
        int button = click.button();
    *///? } else {
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
    //? }
        if (phase == Phase.INTRO_DELAY || phase == Phase.INTRO_FADE || phase == Phase.INTRO_CARD_IN
                || phase == Phase.INTRO_SHAKE || phase == Phase.INTRO_CARD_OUT) return false;
        if (screenScale < 0.9f) return false;
        if (playButton != null && phase == Phase.IDLE && playButton.active) {
            int bx = playButton.getX(), by = playButton.getY();
            if (mouseX >= bx && mouseX <= bx + playButton.getWidth()
                    && mouseY >= by && mouseY <= by + playButton.getHeight()) {
                onPlay();
                return true;
            }
        }
        return false;
    }

    private void drawSlotMachine(DrawContext ctx, float gFade) {
        int slotW  = Math.min(260, width - 80);
        int slotH  = ROWS * ROW_H;
        int slotX  = (width - slotW) / 2;
        int slotCy = height / 2 - 10;
        int slotY  = slotCy - slotH / 2;

        int frameX     = slotX - 6;
        int frameY     = slotY - 6;
        int frameR     = slotX + slotW + 6;
        int frameB     = slotY + slotH + 6;
        int frameColor = fa(0xFF1A0900, gFade);
        int gold       = fa(0xFFFFCC00, gFade);

        // ── Title tab ────────────────────────────────────────────────────────
        String titleStr = Text.translatableWithFallback("burmalda.casino.title", "Wheel of Fortune").getString();
        int    titleW   = textRenderer.getWidth("§l" + titleStr); // bold-aware
        int    tabW     = titleW + 28;
        int    tabH     = 20;
        int    tabX     = (width - tabW) / 2;
        int    tabY     = frameY - tabH;

        ctx.fill(tabX, tabY, tabX + tabW, frameY, frameColor);
        ctx.fill(tabX, tabY, tabX + tabW, tabY + 2, gold);
        ctx.fill(tabX, tabY + 2, tabX + 2, frameY, gold);
        ctx.fill(tabX + tabW - 2, tabY + 2, tabX + tabW, frameY, gold);
        // §l = bold without color override, color comes from the parameter
        ctx.drawText(textRenderer, Text.literal("§l" + titleStr),
                (width - titleW) / 2, tabY + 6, gold, true);

        // ── Outer frame ───────────────────────────────────────────────────────
        ctx.fill(frameX, frameY, frameR, frameB, frameColor);

        // ── Slot background ───────────────────────────────────────────────────
        ctx.fill(slotX, slotY, slotX + slotW, slotY + slotH, fa(0xFF110800, gFade));

        // ── Scrolling rows ────────────────────────────────────────────────────
        ctx.enableScissor(slotX, slotY, slotX + slotW, slotY + slotH);

        int iFloor  = (int) Math.floor(scrollPos);
        int faAlpha = (int)(0xFF * gFade);
        for (int i = iFloor - ROWS; i <= iFloor + ROWS; i++) {
            int sector = ((i % N) + N) % N;
            int rowTop = slotCy - ROW_H / 2 + (int) Math.floor((i - scrollPos) * ROW_H);

            if (rowTop + ROW_H <= slotY || rowTop >= slotY + slotH) continue;

            double dist   = Math.abs(i - scrollPos);
            float  bright = (float) Math.max(0.08, 1.0 - dist * 0.22);

            int base = SECTOR_COLORS[sector];
            int rc = (int)(((base >> 16) & 0xFF) * bright);
            int gc = (int)(((base >>  8) & 0xFF) * bright);
            int bc = (int)(( base        & 0xFF) * bright);
            ctx.fill(slotX, rowTop, slotX + slotW, rowTop + ROW_H,
                    (faAlpha << 24) | (rc << 16) | (gc << 8) | bc);

            ctx.fill(slotX, rowTop, slotX + slotW, rowTop + 1, fa(0x44FFFFFF, gFade));

            String name     = labelFor(sector);
            int    tw       = textRenderer.getWidth(name);
            int    ty       = rowTop + (ROW_H - 9) / 2;
            int    baseCol  = dist < 0.6 ? 0xFFFFFFFF : (dist < 1.6 ? 0xFFAAAAAA : 0xFF555555);
            ctx.drawText(textRenderer, name, slotX + (slotW - tw) / 2, ty, fa(baseCol, gFade), false);
        }

        ctx.disableScissor();

        // ── Edge fades ────────────────────────────────────────────────────────
        ctx.fillGradient(slotX, slotY,                      slotX + slotW, slotY + ROW_H * 2,       fa(0xDD000000, gFade), 0x00000000);
        ctx.fillGradient(slotX, slotY + slotH - ROW_H * 2,  slotX + slotW, slotY + slotH,           0x00000000, fa(0xDD000000, gFade));

        // ── Gold selection frame ──────────────────────────────────────────────
        int fY = slotCy - ROW_H / 2;
        ctx.fill(slotX - 4,         fY - 2,     slotX + slotW + 4, fY,              gold);
        ctx.fill(slotX - 4,         fY + ROW_H, slotX + slotW + 4, fY + ROW_H + 2, gold);
        ctx.fill(slotX - 4,         fY,         slotX - 2,          fY + ROW_H,     gold);
        ctx.fill(slotX + slotW + 2, fY,         slotX + slotW + 4,  fY + ROW_H,    gold);

        // ── Arrows with spring wobble ─────────────────────────────────────────
        ctx.drawText(textRenderer, "▶", (int)(slotX - 14 - arrowWobble), slotCy - 4, gold, false);
        ctx.drawText(textRenderer, "◀", (int)(slotX + slotW + 5 + arrowWobble), slotCy - 4, gold, false);

        // ── Legs ─────────────────────────────────────────────────────────────
        int legW  = 14, legH = 40;
        int legX1 = frameX + (frameR - frameX) / 4 - legW / 2;
        int legX2 = frameX + (frameR - frameX) * 3 / 4 - legW / 2;
        ctx.fillGradient(legX1, frameB, legX1 + legW, frameB + legH, frameColor, 0x001A0900);
        ctx.fillGradient(legX2, frameB, legX2 + legW, frameB + legH, frameColor, 0x001A0900);
    }

    private void drawTimer(DrawContext ctx) {
        if (phase != Phase.IDLE) return;
        int sec = Math.max(0, 60 - (int)((System.currentTimeMillis() - openTimeMs) / 1000));
        String pre = Text.translatableWithFallback("burmalda.casino.timer.prefix", "Time left:").getString();
        String suf = Text.translatableWithFallback("burmalda.casino.timer.suffix", "sec").getString();
        String s = pre + " §c" + sec + "§r " + suf;
        int tw = textRenderer.getWidth(s);
        int x  = (width - tw) / 2;
        int y  = height - 56;
        ctx.fill(x - 8, y - 3, x + tw + 8, y + 11, 0xAA000000);
        ctx.drawText(textRenderer, Text.literal(s), x, y, 0xFFFFFFFF, true);
    }

    private void drawResultCard(DrawContext ctx, float gFade) {
        int dimAlpha = (int)(Math.min(1f, cardScale / 0.5f) * 0x99 * gFade);
        ctx.fill(0, 0, width, height, dimAlpha << 24);

        String prize  = labelFor(resultSection).toUpperCase();
        String flavor = SECTOR_GOOD[resultSection]
                ? Text.translatableWithFallback("burmalda.casino.result.good", "The wheel favors you.").getString()
                : Text.translatableWithFallback("burmalda.casino.result.bad",  "Satisfied?").getString();

        int cardW = Math.max(240, textRenderer.getWidth(prize) * 2 + 80);
        int cardH = 80;

        com.mixaold.burmalda.util.BurmaldaRenderCompat.push(ctx);
        com.mixaold.burmalda.util.BurmaldaRenderCompat.translate(ctx, width / 2.0, height / 2.0);
        com.mixaold.burmalda.util.BurmaldaRenderCompat.rotateZ(ctx, cardTilt);
        com.mixaold.burmalda.util.BurmaldaRenderCompat.scale(ctx, cardScale, cardScale);
        com.mixaold.burmalda.util.BurmaldaRenderCompat.translate(ctx, -cardW / 2.0, -cardH / 2.0);

        ctx.fill(-4, -4, cardW + 4, cardH + 4, fa(0xFF1A0A02, gFade));
        ctx.fill(0,  0,  cardW,     cardH,      fa(0xFF5A3A1A, gFade));
        ctx.fill(0,  0,  cardW,     4,           fa(0xFF8A6035, gFade));

        Text lbl = Text.translatableWithFallback("burmalda.casino.result.header", "You got:");
        ctx.drawText(textRenderer, lbl, cardW / 2 - textRenderer.getWidth(lbl) / 2, 12,
                fa(0xFFFFCC44, gFade), true);

        com.mixaold.burmalda.util.BurmaldaRenderCompat.push(ctx);
        com.mixaold.burmalda.util.BurmaldaRenderCompat.translate(ctx, cardW / 2.0, 36);
        com.mixaold.burmalda.util.BurmaldaRenderCompat.scale(ctx, 1.5f, 1.5f);
        ctx.drawText(textRenderer, Text.literal(prize),
                -textRenderer.getWidth(prize) / 2, 0, fa(SECTOR_COLORS[resultSection], gFade), true);
        com.mixaold.burmalda.util.BurmaldaRenderCompat.pop(ctx);

        ctx.drawText(textRenderer, Text.literal(flavor),
                cardW / 2 - textRenderer.getWidth(flavor) / 2, cardH - 14, fa(0xFFAAAAAA, gFade), false);

        com.mixaold.burmalda.util.BurmaldaRenderCompat.pop(ctx);
    }

    private void drawParticles(DrawContext ctx, float gFade) {
        for (Particle p : particles) {
            int alpha = (int)(p.life * 255 * gFade);
            int color = (alpha << 24) | (p.color & 0x00FFFFFF);
            int px = (int) p.x, py = (int) p.y;
            ctx.fill(px - 1, py - 1, px + 3, py + 3, color);
        }
    }

    private void drawIntroCard(DrawContext ctx) {
        if (introCardScale <= 0f) return;
        String line1 = "Время Колеса";
        String line2 = "Удачи!";
        int cardW = 250;
        int cardH = 82;
        com.mixaold.burmalda.util.BurmaldaRenderCompat.push(ctx);
        com.mixaold.burmalda.util.BurmaldaRenderCompat.translate(ctx, width / 2.0, height / 2.0);
        com.mixaold.burmalda.util.BurmaldaRenderCompat.rotateZ(ctx, introShakeX);
        com.mixaold.burmalda.util.BurmaldaRenderCompat.scale(ctx, introCardScale, introCardScale);
        com.mixaold.burmalda.util.BurmaldaRenderCompat.translate(ctx, -cardW / 2.0, -cardH / 2.0);
        ctx.fill(-5, -5, cardW + 5, cardH + 5, 0xFF111111);
        ctx.fill(0,  0,  cardW,    cardH,     0xFF222222);
        ctx.fill(0, 0, cardW, 2, 0xFFFFCC00);
        ctx.fill(0, cardH - 2, cardW, cardH, 0xFFFFCC00);
        ctx.fill(0, 0, 2, cardH, 0xFFFFCC00);
        ctx.fill(cardW - 2, 0, cardW, cardH, 0xFFFFCC00);
        com.mixaold.burmalda.util.BurmaldaRenderCompat.push(ctx);
        float s1 = 2.5f;
        int tw1 = (int)(textRenderer.getWidth(line1) * s1);
        com.mixaold.burmalda.util.BurmaldaRenderCompat.translate(ctx, cardW / 2.0 - tw1 / 2.0, 10.0);
        com.mixaold.burmalda.util.BurmaldaRenderCompat.scale(ctx, s1, s1);
        ctx.drawText(textRenderer, Text.literal(line1), 0, 0, 0xFFFFCC00, true);
        com.mixaold.burmalda.util.BurmaldaRenderCompat.pop(ctx);
        com.mixaold.burmalda.util.BurmaldaRenderCompat.push(ctx);
        float s2 = 3.2f;
        int tw2 = (int)(textRenderer.getWidth(line2) * s2);
        com.mixaold.burmalda.util.BurmaldaRenderCompat.translate(ctx, cardW / 2.0 - tw2 / 2.0, 44.0);
        com.mixaold.burmalda.util.BurmaldaRenderCompat.scale(ctx, s2, s2);
        ctx.drawText(textRenderer, Text.literal(line2), 0, 0, 0xFFFF8800, true);
        com.mixaold.burmalda.util.BurmaldaRenderCompat.pop(ctx);
        com.mixaold.burmalda.util.BurmaldaRenderCompat.pop(ctx);
    }

    private void playIntroSound() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getSoundManager() == null) return;
        mc.getSoundManager().play(com.mixaold.burmalda.util.BurmaldaRenderCompat.uiSound(
                SoundEvents.ENTITY_PLAYER_LEVELUP, 1.4f));
    }

    private String labelFor(int i) {
        if (i == 4) return group1Id != null && !group1Id.isEmpty()
                ? Text.translatableWithFallback("burmalda.debuff." + group1Id + ".name", group1Name).getString()
                : group1Name;
        if (i == 5) return group2Id != null && !group2Id.isEmpty()
                ? Text.translatableWithFallback("burmalda.debuff." + group2Id + ".name", group2Name).getString()
                : group2Name;
        return switch (i) {
            case 0 -> Text.translatableWithFallback("burmalda.casino.slot.immunity",  "Immunity").getString();
            case 1 -> Text.translatableWithFallback("burmalda.casino.slot.amnesty",   "Amnesty").getString();
            case 2 -> Text.translatableWithFallback("burmalda.casino.slot.netherite", "Netherite").getString();
            case 3 -> Text.translatableWithFallback("burmalda.casino.slot.all_die", "Judgement Day").getString();
            case 6 -> Text.translatableWithFallback("burmalda.casino.slot.death",     "Death").getString();
            default -> "?";
        };
    }

    // Multiplies the alpha channel of argb by fade (0.0–1.0)
    private static int fa(int argb, float fade) {
        int a = (int)(((argb >> 24) & 0xFF) * fade);
        return (a << 24) | (argb & 0x00FFFFFF);
    }
}
