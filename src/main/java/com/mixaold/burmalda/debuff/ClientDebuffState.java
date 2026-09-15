package com.mixaold.burmalda.debuff;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.text.Text;

@Environment(EnvType.CLIENT)
public class ClientDebuffState {

    public static String activeDebuffId = null;
    public static String activeDebuffName = "";
    public static String activeDebuffDescription = "";
    public static boolean soloDebuffFailed = false;

    public static String activeGroupDebuffId = null;
    public static String activeGroupDebuffName = "";
    public static String activeGroupDebuffDescription = "";
    public static boolean groupDebuffDone = false; // true when group debuff completed instantly (e.g. Blind Auction)

    public static int ticksRemaining = 0;
    public static int totalCycleTicks = 3600; // 3 min at 20 TPS

    // Obshchaga: comma-joined names of all linked players
    public static String obshchagaLinkedNames = null;

    // Wheel of Fortune skip overlay (corner panel)
    public static boolean wheelSkipOverlayActive = false;
    public static long wheelSkipStartMs = 0;

    // ── Event timer bar above the hotbar (e.g. Pandora's Box) ────────────────
    public static String eventTimerLabel = "";
    public static float  eventTimerFrac = 0f;
    public static int    eventTimerColor = 0xFFCC2222;
    public static long   eventTimerUntilMs = 0; // bar hides when System time passes this

    public static void setEventTimer(String label, float frac, int color) {
        eventTimerLabel = label;
        eventTimerFrac = frac;
        eventTimerColor = color;
        eventTimerUntilMs = System.currentTimeMillis() + 1500; // auto-hide if updates stop
    }

    // Pending announcement (shown in center screen)
    public static String announcementName = null;
    public static String announcementDesc = null;
    public static long announcementStartMs = 0;
    public static boolean announcementDarken = false;

    // Derived client-side effect flags
    public static boolean swapMouseButtons() {
        return "chuzhie_ruki".equals(activeDebuffId);
    }

    public static boolean invertHorizontalControls() {
        return "zerkalnyy_mir".equals(activeDebuffId);
    }

    public static boolean invertForwardBackControls() {
        return "inversiya_khoda".equals(activeGroupDebuffId);
    }

    public static boolean hideHotbar() {
        return "slepoe_doverie".equals(activeDebuffId);
    }

    public static boolean hideInventory() {
        return "slepoe_doverie".equals(activeDebuffId);
    }

    public static boolean hideHealth() {
        return "slepoe_doverie".equals(activeDebuffId)
                || "kot_shrodingera".equals(activeDebuffId);
    }

    public static boolean hideXpBar() {
        return "slepoe_doverie".equals(activeDebuffId);
    }

    public static void setDebuff(String id, String name, String desc) {
        activeDebuffId = id;
        activeDebuffName = name;
        activeDebuffDescription = desc;
        soloDebuffFailed = false;
        String localName = Text.translatableWithFallback("burmalda.debuff." + id + ".name", name).getString();
        String localDesc = Text.translatableWithFallback("burmalda.debuff." + id + ".desc", desc).getString();
        showAnnouncement(localName, localDesc);
    }

    public static void setGroupDebuff(String id, String name, String desc) {
        activeGroupDebuffId = id;
        activeGroupDebuffName = name;
        activeGroupDebuffDescription = desc;
        String localName = Text.translatableWithFallback("burmalda.debuff." + id + ".name", name).getString();
        String localDesc = Text.translatableWithFallback("burmalda.debuff." + id + ".desc", desc).getString();
        showAnnouncement("⚡ " + localName, localDesc);
    }

    public static void clearDebuff() {
        activeDebuffId = null;
        activeDebuffName = "";
        activeDebuffDescription = "";
        soloDebuffFailed = false;
        obshchagaLinkedNames = null;
    }

    public static void clearGroupDebuff() {
        activeGroupDebuffId = null;
        activeGroupDebuffName = "";
        activeGroupDebuffDescription = "";
        groupDebuffDone = false;
    }

    public static void showAnnouncement(String name, String desc) {
        showAnnouncement(name, desc, false);
    }

    public static void showAnnouncement(String name, String desc, boolean darken) {
        announcementName = name;
        announcementDesc = desc;
        announcementDarken = darken;
        announcementStartMs = System.currentTimeMillis();
    }

    public static void reset() {
        activeDebuffId = null;
        activeDebuffName = "";
        activeDebuffDescription = "";
        soloDebuffFailed = false;
        activeGroupDebuffId = null;
        activeGroupDebuffName = "";
        activeGroupDebuffDescription = "";
        ticksRemaining = 0;
        announcementName = null;
        announcementDesc = null;
        announcementStartMs = 0;
        announcementDarken = false;
        wheelSkipOverlayActive = false;
        wheelSkipStartMs = 0;
        groupDebuffDone = false;
        obshchagaLinkedNames = null;
    }
}
