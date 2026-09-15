package com.mixaold.burmalda.hud;

import com.mixaold.burmalda.debuff.Debuff;
import com.mixaold.burmalda.debuff.DebuffRegistry;
import com.mixaold.burmalda.network.BurmaldaNetwork;
import com.mixaold.burmalda.util.BurmaldaRenderCompat;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Debuff-assignment menu (guide-style dark-gold card) with a bounce open/close animation.
 * Left: live player list with heads, multi-select, owner-only whitelist toggle. Right: search
 * (matches across BOTH solo & group, tagged per row) + Solo/Group tabs + a draggable scrollbar.
 */
@Environment(EnvType.CLIENT)
public class BurmaldaMenuScreen extends Screen {

    // ── Palette ───────────────────────────────────────────────────────────────
    private static final int COL_CARD    = 0xFF1C1408;
    private static final int COL_HEADER  = 0xFF120E05;
    private static final int COL_ACCENT  = 0xFFFFAA00;
    private static final int COL_DIVIDER = 0xFF3D2910;
    private static final int COL_SHADOW  = 0xBB000000;
    private static final int COL_FIELD   = 0xFF0E0A04;
    private static final int COL_TEXT_LO = 0xFFE8D5A3;
    private static final int COL_TEXT_HI = 0xFFFFCC44;
    private static final int COL_SEL     = 0x66FFAA00;
    private static final int COL_HOVER   = 0x33FFAA00;

    // ── Layout ─────────────────────────────────────────────────────────────────
    private static final int CARD_W = 440, CARD_H = 252;
    private static final int HEADER_H = 26;
    private static final int P_ROW = 20;   // player row height
    private static final int D_ROW = 15;   // debuff row height
    private static final int SB_W  = 4;    // scrollbar thickness (right, draggable)
    private static final int CLR_W = 116;  // "turn off debuffs" header button width

    // ── State ────────────────────────────────────────────────────────────────
    private static BurmaldaMenuScreen INSTANCE;
    private static String lastQuery = ""; // remembered across re-opens
    private boolean tabSolo = true;
    private net.minecraft.client.gui.widget.TextFieldWidget searchField;
    private final Set<String> selected    = new LinkedHashSet<>();
    private final Set<String> whitelisted = new LinkedHashSet<>();
    private boolean defaultSelected = false; // pre-select the opener exactly once
    private List<PlayerListEntry> playersCache = null; // avoid rebuilding+sorting every render frame
    private long playersCacheAt = 0;
    private final boolean canManage;
    private int leftScroll = 0, rightScroll = 0;
    private boolean draggingRight = false;
    private String toast = null;
    private long   toastUntil = 0;

    // Bounce animation (spring scale about screen centre)
    private float scale = 0.05f, scaleVel = 0f;
    private boolean closing = false;
    private long lastNs = 0;

    private record Entry(Debuff debuff, boolean group) {}

    public BurmaldaMenuScreen(boolean canManage, String whitelistCsv) {
        super(Text.translatableWithFallback("burmalda.menu.title", "Меню выдачи дебаффов"));
        this.canManage = canManage;
        parseCsv(whitelistCsv, whitelisted);
    }

    public static void onWhitelistSync(String csv) {
        if (INSTANCE == null) return;
        INSTANCE.whitelisted.clear();
        parseCsv(csv, INSTANCE.whitelisted);
    }

    private static void parseCsv(String csv, Set<String> into) {
        if (csv == null || csv.isEmpty()) return;
        for (String n : csv.split(",")) { String t = n.trim(); if (!t.isEmpty()) into.add(t); }
    }

    @Override public boolean shouldPause() { return false; }

    @Override
    protected void init() {
        INSTANCE = this;
        scale = 0.05f; scaleVel = 0f; closing = false; lastNs = 0;

        // Pre-select the player who opened the menu (once) so they needn't click themselves.
        if (!defaultSelected) {
            defaultSelected = true;
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player != null && mc.getNetworkHandler() != null) {
                PlayerListEntry self = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
                String selfName = self != null ? BurmaldaRenderCompat.profileName(self) : null;
                if (selfName != null && !selfName.isEmpty()) selected.add(selfName);
            }
        }

        // Real text field — gives click-to-focus, a blinking cursor, mouse/keyboard selection and
        // delete for free. We draw our own box; the widget only renders text + cursor + selection.
        searchField = new net.minecraft.client.gui.widget.TextFieldWidget(
                textRenderer, rightX() + 14, searchY() + 3, rightW() - 18, 10, Text.empty());
        searchField.setMaxLength(48);
        searchField.setDrawsBackground(false);
        searchField.setText(lastQuery);
        searchField.setChangedListener(t -> { lastQuery = t; rightScroll = 0; });
        addSelectableChild(searchField);

        playSound(SoundEvents.ENTITY_ITEM_PICKUP, 1.0f);
    }

    private String query() { return searchField == null ? "" : searchField.getText(); }

    @Override
    public void removed() {
        if (INSTANCE == this) INSTANCE = null;
        super.removed();
    }

    // ── Geometry (same formulas for render + input) ───────────────────────────
    private int cardX() { return (width  - CARD_W) / 2; }
    private int cardY() { return (height - CARD_H) / 2; }

    private int leftX()  { return cardX() + 8; }
    private int leftW()  { return 140; }
    private int listTop(){ return cardY() + HEADER_H + 16; }
    private int listBot(){ return cardY() + CARD_H - 22; }

    private int rightX() { return cardX() + 156; }
    private int rightW() { return CARD_W - 156 - 8; }
    private int searchY(){ return cardY() + HEADER_H + 4; }
    private int searchH(){ return 16; }
    private int tabY()   { return searchY() + searchH() + 4; }
    private int tabH()   { return 15; }
    private int rListTop(){ return tabY() + tabH() + 4; }
    private int rsbX()   { return rightX() + rightW() - SB_W; }
    private int clrX()   { return cardX() + CARD_W - 22 - CLR_W; }

    private int leftVisible()  { return Math.max(1, (listBot() - listTop()) / P_ROW); }
    private int rightVisible() { return Math.max(1, (listBot() - rListTop()) / D_ROW); }

    // ── Data ───────────────────────────────────────────────────────────────────
    private List<PlayerListEntry> players() {
        long now = System.currentTimeMillis();
        if (playersCache != null && now - playersCacheAt < 500) return playersCache;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getNetworkHandler() == null) return List.of();
        List<PlayerListEntry> list = new ArrayList<>();
        for (PlayerListEntry e : mc.getNetworkHandler().getPlayerList()) {
            if (BurmaldaRenderCompat.profileName(e) != null) list.add(e);
        }
        list.sort(Comparator.comparing(e -> BurmaldaRenderCompat.profileName(e).toLowerCase()));
        playersCache = list;
        playersCacheAt = now;
        return list;
    }

    private String dName(Debuff d) {
        return Text.translatableWithFallback("burmalda.debuff." + d.getId() + ".name", d.getName()).getString();
    }

    /** Empty query → current tab. Non-empty → matches from BOTH solo and group, each tagged. */
    private List<Entry> visibleEntries() {
        String q = query().trim().toLowerCase();
        List<Entry> out = new ArrayList<>();
        if (q.isEmpty()) {
            boolean g = !tabSolo;
            for (Debuff d : (tabSolo ? DebuffRegistry.getSoloDebuffs() : DebuffRegistry.getGroupDebuffs()))
                out.add(new Entry(d, g));
        } else {
            for (Debuff d : DebuffRegistry.getSoloDebuffs())  if (dName(d).toLowerCase().contains(q)) out.add(new Entry(d, false));
            for (Debuff d : DebuffRegistry.getGroupDebuffs()) if (dName(d).toLowerCase().contains(q)) out.add(new Entry(d, true));
        }
        return out;
    }

    private boolean searching() { return !query().trim().isEmpty(); }
    private boolean ready()     { return !closing && scale > 0.9f; }

    // ── Animation ──────────────────────────────────────────────────────────────
    private void update() {
        long now = System.nanoTime();
        double dt = lastNs == 0 ? 1.0 / 60.0 : Math.min(0.05, (now - lastNs) / 1e9);
        lastNs = now;

        float target    = closing ? 0f : 1f;
        float stiffness = closing ? 240f : 260f;
        float damping   = closing ? 26f  : 13f;   // low damping on open → overshoot = bounce
        float acc = -stiffness * (scale - target) - damping * scaleVel;
        scaleVel += acc * (float) dt;
        scale    += scaleVel * (float) dt;
        scale = Math.max(0f, Math.min(1.6f, scale));

        if (closing && scale <= 0.04f) MinecraftClient.getInstance().setScreen(null);
    }

    // ── Render ───────────────────────────────────────────────────────────────
    @Override public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {}

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        update();

        int dimA = (int) (0xCC * Math.min(1f, scale));
        ctx.fill(0, 0, width, height, dimA << 24);

        double scx = width / 2.0, scy = height / 2.0;
        BurmaldaRenderCompat.push(ctx);
        BurmaldaRenderCompat.translate(ctx, scx, scy);
        BurmaldaRenderCompat.scale(ctx, scale, scale);
        BurmaldaRenderCompat.translate(ctx, -scx, -scy);

        int x = cardX(), y = cardY();
        ctx.fill(x - 5, y - 5, x + CARD_W + 5, y + CARD_H + 5, COL_SHADOW);
        ctx.fill(x, y, x + CARD_W, y + CARD_H, COL_CARD);
        ctx.fill(x, y, x + CARD_W, y + HEADER_H, COL_HEADER);
        ctx.fill(x, y, x + CARD_W, y + 2, COL_ACCENT);
        ctx.fill(x, y + HEADER_H - 1, x + CARD_W, y + HEADER_H, COL_DIVIDER);
        ctx.fill(x, y + CARD_H - 1, x + CARD_W, y + CARD_H, COL_ACCENT);

        String title = Text.translatableWithFallback("burmalda.menu.title", "Меню выдачи дебаффов").getString();
        ctx.drawText(textRenderer, Text.literal("§l§6BURMALDA  §r§7" + title), x + 10, y + 9, 0xFFFFFFFF, true);

        int xb = x + CARD_W - 16;
        boolean xHover = inBox(mouseX, mouseY, xb, y + 7, 12, 12);
        ctx.drawText(textRenderer, Text.literal(xHover ? "§c§l✕" : "§7§l✕"), xb + 2, y + 8, 0xFFFFFFFF, false);

        // "Turn off active debuffs" header button
        int cbx = clrX(), cby = y + 5, cbh = 16;
        boolean cbHov = inBox(mouseX, mouseY, cbx, cby, CLR_W, cbh);
        ctx.fill(cbx, cby, cbx + CLR_W, cby + cbh, cbHov ? 0xFF3A1414 : 0xFF260E0E);
        BurmaldaRenderCompat.drawBorder(ctx, cbx, cby, CLR_W, cbh, cbHov ? 0xFFFF5555 : 0xFF7A2A2A);
        String cbl = "§c✖ " + (selected.isEmpty()
                ? Text.translatableWithFallback("burmalda.menu.clear.all", "Снять всё").getString()
                : Text.translatableWithFallback("burmalda.menu.clear.sel", "Снять выбр.").getString());
        int cblw = textRenderer.getWidth(cbl);
        ctx.drawText(textRenderer, Text.literal(cbl), cbx + (CLR_W - cblw) / 2, cby + 4, 0xFFFFFFFF, false);

        ctx.fill(rightX() - 6, y + HEADER_H + 2, rightX() - 5, y + CARD_H - 4, COL_DIVIDER);

        drawPlayers(ctx, mouseX, mouseY);
        drawSearch(ctx, mouseX, mouseY);
        drawTabs(ctx, mouseX, mouseY);
        drawDebuffs(ctx, mouseX, mouseY);
        drawFooter(ctx);

        BurmaldaRenderCompat.pop(ctx);
    }

    private void drawPlayers(DrawContext ctx, int mouseX, int mouseY) {
        List<PlayerListEntry> ps = players();
        leftScroll = clamp(leftScroll, 0, Math.max(0, ps.size() - leftVisible()));

        ctx.drawText(textRenderer, Text.literal("§6§l" + Text.translatableWithFallback("burmalda.menu.players", "Игроки").getString()
                + " §r§7(" + ps.size() + ")"), leftX(), cardY() + HEADER_H + 4, COL_TEXT_HI, false);

        int top = listTop(), vis = leftVisible();
        for (int i = 0; i < vis; i++) {
            int idx = leftScroll + i;
            if (idx >= ps.size()) break;
            PlayerListEntry e = ps.get(idx);
            String name = BurmaldaRenderCompat.profileName(e);
            int ry = top + i * P_ROW;
            boolean sel = selected.contains(name);
            boolean hov = inBox(mouseX, mouseY, leftX(), ry, leftW(), P_ROW - 1);
            if (sel) ctx.fill(leftX(), ry, leftX() + leftW(), ry + P_ROW - 1, COL_SEL);
            else if (hov) ctx.fill(leftX(), ry, leftX() + leftW(), ry + P_ROW - 1, COL_HOVER);
            if (sel) ctx.fill(leftX(), ry, leftX() + 2, ry + P_ROW - 1, COL_ACCENT);

            BurmaldaRenderCompat.drawHead(ctx, e, leftX() + 4, ry + 2, 16);
            String mark = sel ? "§a✔ " : "§7";
            int nameMax = leftW() - 32 - (canManage ? 26 : 0);
            ctx.drawText(textRenderer, Text.literal(mark + trim(name, nameMax)),
                    leftX() + 24, ry + 6, sel ? 0xFFFFFFFF : COL_TEXT_LO, false);

            if (canManage) {
                boolean wl = whitelisted.contains(name);
                int bx = leftX() + leftW() - 24, by = ry + 2, bw = 22, bh = P_ROW - 5;
                ctx.fill(bx, by, bx + bw, by + bh, wl ? COL_SEL : COL_FIELD);
                BurmaldaRenderCompat.drawBorder(ctx, bx, by, bw, bh, wl ? COL_ACCENT : COL_DIVIDER);
                int lw = textRenderer.getWidth("WL");
                ctx.drawText(textRenderer, Text.literal((wl ? "§6" : "§7") + "WL"),
                        bx + (bw - lw) / 2, by + 3, 0xFFFFFFFF, false);
            }
        }
        drawScrollbar(ctx, leftX() + leftW() - 2, top, listBot() - top, 2, ps.size(), vis, leftScroll);
    }

    private void drawSearch(DrawContext ctx, int mouseX, int mouseY) {
        int sx = rightX(), sy = searchY(), sw = rightW(), sh = searchH();
        boolean focused = searchField != null && searchField.isFocused();
        ctx.fill(sx, sy, sx + sw, sy + sh, COL_FIELD);
        // Border lights up gold when the field is focused — visual feedback that you clicked it.
        BurmaldaRenderCompat.drawBorder(ctx, sx, sy, sw, sh, focused ? COL_ACCENT : COL_DIVIDER);
        ctx.drawText(textRenderer, Text.literal("§e🔍"), sx + 4, sy + 4, COL_TEXT_LO, false);
        if (searchField != null) {
            if (searchField.getText().isEmpty() && !focused)
                ctx.drawText(textRenderer, Text.literal("§7" + Text.translatableWithFallback("burmalda.menu.search", "Поиск...").getString()),
                        sx + 16, sy + 4, COL_DIVIDER, false);
            searchField.render(ctx, mouseX, mouseY, 0f);
        }
    }

    private void drawTabs(DrawContext ctx, int mouseX, int mouseY) {
        int tx = rightX(), ty = tabY(), tw = rightW(), th = tabH();
        int half = tw / 2 - 1;
        // While searching, both lists are shown — dim the tab highlight to signal that.
        drawTab(ctx, tx, ty, half, th,
                Text.translatableWithFallback("burmalda.menu.tab.solo", "Соло").getString()
                        + " (" + DebuffRegistry.getSoloDebuffs().size() + ")", tabSolo && !searching(), mouseX, mouseY);
        drawTab(ctx, tx + half + 2, ty, half, th,
                Text.translatableWithFallback("burmalda.menu.tab.group", "Групповые").getString()
                        + " (" + DebuffRegistry.getGroupDebuffs().size() + ")", !tabSolo && !searching(), mouseX, mouseY);
    }

    private void drawTab(DrawContext ctx, int x, int y, int w, int h, String label, boolean active, int mx, int my) {
        boolean hov = inBox(mx, my, x, y, w, h);
        ctx.fill(x, y, x + w, y + h, active ? COL_DIVIDER : (hov ? COL_HOVER : COL_FIELD));
        if (active) ctx.fill(x, y, x + w, y + 1, COL_ACCENT);
        int lw = textRenderer.getWidth(label);
        ctx.drawText(textRenderer, Text.literal((active ? "§e§l" : "§7") + label),
                x + (w - lw) / 2, y + 4, 0xFFFFFFFF, false);
    }

    private void drawDebuffs(DrawContext ctx, int mouseX, int mouseY) {
        List<Entry> ds = visibleEntries();
        rightScroll = clamp(rightScroll, 0, Math.max(0, ds.size() - rightVisible()));
        int top = rListTop(), vis = rightVisible();
        int rowW = rightW() - SB_W - 4;
        boolean tag = searching();

        if (ds.isEmpty()) {
            ctx.drawText(textRenderer, Text.literal("§8— " + Text.translatableWithFallback("burmalda.menu.empty", "ничего не найдено").getString()),
                    rightX() + 4, top + 4, COL_DIVIDER, false);
            return;
        }
        for (int i = 0; i < vis; i++) {
            int idx = rightScroll + i;
            if (idx >= ds.size()) break;
            Entry en = ds.get(idx);
            int ry = top + i * D_ROW;
            boolean hov = inBox(mouseX, mouseY, rightX(), ry, rowW, D_ROW - 1);
            if (hov) ctx.fill(rightX(), ry, rightX() + rowW, ry + D_ROW - 1, COL_HOVER);
            String prefix = tag
                    ? (en.group() ? "§d[групп] §r" : "§6[соло] §r")
                    : (en.group() ? "§d• " : "§6• ");
            String label = prefix + (hov ? "§f" : "§7") + trim(dName(en.debuff()), rowW - textRenderer.getWidth(prefix) - 8);
            ctx.drawText(textRenderer, Text.literal(label), rightX() + 3, ry + 4, COL_TEXT_LO, false);
        }
        drawScrollbar(ctx, rsbX(), top, listBot() - top, SB_W, ds.size(), vis, rightScroll);
    }

    private void drawFooter(DrawContext ctx) {
        int fy = cardY() + CARD_H - 14;
        String line;
        if (toast != null && System.currentTimeMillis() < toastUntil) {
            line = toast;
        } else {
            String hint = Text.translatableWithFallback("burmalda.menu.hint",
                    "ЛКМ — выдать • колесо/тащи — скролл").getString();
            String wl = canManage ? "  §7| §6WL §8" + Text.translatableWithFallback("burmalda.menu.wl.hint",
                    "— доступ").getString() : "";
            line = "§8" + hint + "  §7| §eвыбрано: " + selected.size() + wl;
        }
        // Scale the footer down if it would spill past the card edge (locale-proof).
        int maxW = CARD_W - 20, w = textRenderer.getWidth(line);
        if (w > maxW) {
            float s = maxW / (float) w;
            BurmaldaRenderCompat.push(ctx);
            BurmaldaRenderCompat.translate(ctx, cardX() + 10, fy);
            BurmaldaRenderCompat.scale(ctx, s, s);
            ctx.drawText(textRenderer, Text.literal(line), 0, 0, COL_DIVIDER, false);
            BurmaldaRenderCompat.pop(ctx);
        } else {
            ctx.drawText(textRenderer, Text.literal(line), cardX() + 10, fy, COL_DIVIDER, false);
        }
    }

    private void drawScrollbar(DrawContext ctx, int x, int top, int h, int w, int total, int vis, int scroll) {
        if (total <= vis) return;
        ctx.fill(x, top, x + w, top + h, 0x40FFFFFF);
        int barH = Math.max(8, h * vis / total);
        int barY = top + (h - barH) * scroll / Math.max(1, total - vis);
        ctx.fill(x, barY, x + w, barY + barH, COL_ACCENT);
    }

    // ── Input ──────────────────────────────────────────────────────────────────
    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        if (!ready()) return false;
        int step = (int) -Math.signum(vertical);
        if (step == 0) return false;
        if (inBox((int) mx, (int) my, leftX(), listTop(), leftW(), listBot() - listTop())) {
            leftScroll += step; return true;
        }
        if (inBox((int) mx, (int) my, rightX(), rListTop(), rightW(), listBot() - rListTop())) {
            rightScroll += step; return true;
        }
        return false;
    }

    @Override
    //? if >=1.21.11 {
    /*public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean doubled) {
        double mx = click.x(), my = click.y();
        int button = click.button();
    *///? } else {
    public boolean mouseClicked(double mx, double my, int button) {
    //? }
        if (button != 0 || !ready()) return false;
        int imx = (int) mx, imy = (int) my;

        // Search field — focus it + position the cursor (let the Screen route to the child widget).
        if (inBox(imx, imy, rightX(), searchY(), rightW(), searchH())) {
            //? if >=1.21.11 {
            /*super.mouseClicked(click, doubled);
            *///? } else {
            super.mouseClicked(mx, my, button);
            //? }
            return true;
        }
        setFocused(null); // clicked elsewhere → drop search focus

        if (inBox(imx, imy, cardX() + CARD_W - 16, cardY() + 7, 12, 12)) { closeMenu(); return true; }

        // "Turn off active debuffs" button
        if (inBox(imx, imy, clrX(), cardY() + 5, CLR_W, 16)) {
            ClientPlayNetworking.send(new BurmaldaNetwork.ClearDebuffsPayload(String.join(",", selected)));
            setToast(selected.isEmpty()
                    ? "§e✖ " + Text.translatableWithFallback("burmalda.menu.cleared.all", "Сняты все активные дебаффы").getString()
                    : "§e✖ " + Text.translatableWithFallback("burmalda.menu.cleared.sel", "Сняты дебаффы у выбранных").getString() + " (" + selected.size() + ")");
            playSound(SoundEvents.ENTITY_ITEM_PICKUP, 0.8f);
            return true;
        }

        int half = rightW() / 2 - 1;
        if (inBox(imx, imy, rightX(), tabY(), half, tabH())) {
            if (!tabSolo) { tabSolo = true; rightScroll = 0; playSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f); }
            return true;
        }
        if (inBox(imx, imy, rightX() + half + 2, tabY(), half, tabH())) {
            if (tabSolo) { tabSolo = false; rightScroll = 0; playSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f); }
            return true;
        }

        // Right scrollbar — grab to drag (also jumps to the click position)
        if (inBox(imx, imy, rsbX() - 3, rListTop(), SB_W + 4, listBot() - rListTop())
                && visibleEntries().size() > rightVisible()) {
            draggingRight = true;
            setRightScrollFromMouse(my);
            return true;
        }

        // Player rows — WL pill toggles whitelist (owner only), rest toggles selection
        if (inBox(imx, imy, leftX(), listTop(), leftW(), listBot() - listTop())) {
            List<PlayerListEntry> ps = players();
            int row = (imy - listTop()) / P_ROW;
            int idx = leftScroll + row;
            if (idx >= 0 && idx < ps.size()) {
                String name = BurmaldaRenderCompat.profileName(ps.get(idx));
                int ry = listTop() + row * P_ROW;
                if (canManage && inBox(imx, imy, leftX() + leftW() - 24, ry + 2, 22, P_ROW - 5)) {
                    if (!whitelisted.remove(name)) whitelisted.add(name); // optimistic; server confirms
                    ClientPlayNetworking.send(new BurmaldaNetwork.WhitelistTogglePayload(name));
                    playSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f);
                } else {
                    if (!selected.remove(name)) selected.add(name);
                    playSound(SoundEvents.ENTITY_ITEM_PICKUP, 1.2f);
                }
            }
            return true;
        }

        // Debuff rows — assign by the entry's own category
        if (inBox(imx, imy, rightX(), rListTop(), rightW() - SB_W - 4, listBot() - rListTop())) {
            List<Entry> ds = visibleEntries();
            int idx = rightScroll + (imy - rListTop()) / D_ROW;
            if (idx >= 0 && idx < ds.size()) assign(ds.get(idx));
            return true;
        }
        return false;
    }

    @Override
    //? if >=1.21.11 {
    /*public boolean mouseDragged(net.minecraft.client.gui.Click click, double dx, double dy) {
        double my = click.y();
    *///? } else {
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
    //? }
        if (draggingRight) { setRightScrollFromMouse(my); return true; }
        //? if >=1.21.11 {
        /*return super.mouseDragged(click, dx, dy);
        *///? } else {
        return super.mouseDragged(mx, my, button, dx, dy);
        //? }
    }

    @Override
    //? if >=1.21.11 {
    /*public boolean mouseReleased(net.minecraft.client.gui.Click click) {
    *///? } else {
    public boolean mouseReleased(double mx, double my, int button) {
    //? }
        draggingRight = false;
        return false;
    }

    private void setRightScrollFromMouse(double my) {
        int total = visibleEntries().size(), vis = rightVisible();
        if (total <= vis) return;
        int top = rListTop(), h = listBot() - rListTop();
        int barH = Math.max(8, h * vis / total);
        double frac = (my - top - barH / 2.0) / Math.max(1, h - barH);
        rightScroll = clamp((int) Math.round(frac * (total - vis)), 0, total - vis);
    }

    private void assign(Entry en) {
        Debuff d = en.debuff();
        if (en.group()) {
            ClientPlayNetworking.send(new BurmaldaNetwork.AssignDebuffPayload(d.getId(), true, ""));
            setToast("§d✔ " + Text.translatableWithFallback("burmalda.menu.fired", "Запущен").getString() + ": §f" + dName(d));
            playSound(SoundEvents.ENTITY_PLAYER_LEVELUP, 1.1f);
        } else {
            if (selected.isEmpty()) {
                setToast("§c" + Text.translatableWithFallback("burmalda.menu.noselect", "Сначала выбери игрока слева").getString());
                playSound(SoundEvents.ENTITY_VILLAGER_NO, 0.8f);
                return;
            }
            String csv = String.join(",", selected);
            ClientPlayNetworking.send(new BurmaldaNetwork.AssignDebuffPayload(d.getId(), false, csv));
            setToast("§a✔ " + Text.translatableWithFallback("burmalda.menu.given", "Выдано").getString()
                    + ": §f" + dName(d) + " §7→ " + selected.size());
            playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.1f);
        }
    }

    @Override
    //? if >=1.21.11 {
    /*public boolean keyPressed(net.minecraft.client.input.KeyInput input) {
        int keyCode = input.key();
    *///? } else {
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
    //? }
        if (keyCode == 256) { closeMenu(); return true; }             // ESC
        // Everything else (typing, backspace/delete, arrows, Ctrl+A select-all) goes to the field.
        //? if >=1.21.11 {
        /*return super.keyPressed(input);
        *///? } else {
        return super.keyPressed(keyCode, scanCode, modifiers);
        //? }
    }

    @Override
    //? if >=1.21.11 {
    /*public boolean charTyped(net.minecraft.client.input.CharInput input) {
        char chr = (char) input.codepoint();
    *///? } else {
    public boolean charTyped(char chr, int modifiers) {
    //? }
        // Typing goes straight into the focused search field.
        //? if >=1.21.11 {
        /*return super.charTyped(input);
        *///? } else {
        return super.charTyped(chr, modifiers);
        //? }
    }

    private void closeMenu() {
        if (closing) return;
        closing = true;
        scaleVel += 3.5f; // little pop before it shrinks away
        playSound(SoundEvents.ENTITY_ITEM_PICKUP, 0.7f);
    }

    // ── Utilities ────────────────────────────────────────────────────────────
    private void setToast(String t) { toast = t; toastUntil = System.currentTimeMillis() + 2500; }

    private String trim(String s, int maxW) {
        if (maxW <= 0) return "";
        if (textRenderer.getWidth(s) <= maxW) return s;
        while (s.length() > 1 && textRenderer.getWidth(s + "…") > maxW) s = s.substring(0, s.length() - 1);
        return s + "…";
    }

    private static boolean inBox(int px, int py, int x, int y, int w, int h) {
        return px >= x && px < x + w && py >= y && py < y + h;
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    private void playSound(SoundEvent s, float pitch) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getSoundManager() != null)
            mc.getSoundManager().play(BurmaldaRenderCompat.uiSound(s, pitch));
    }
}
