package com.mixaold.burmalda;

import com.mixaold.burmalda.debuff.ClientDebuffState;
import com.mixaold.burmalda.hud.AnnouncementOverlay;
import com.mixaold.burmalda.hud.BurmaldaHud;
import com.mixaold.burmalda.hud.CasinoScreen;
import com.mixaold.burmalda.hud.GuideScreen;
import com.mixaold.burmalda.network.BurmaldaNetwork;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screen.ingame.HandledScreen;

@Environment(EnvType.CLIENT)
public class BurmaldaClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        registerPacketReceivers();
        BurmaldaHud.register();
        AnnouncementOverlay.register();
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
                client.execute(ClientDebuffState::reset));

        // Blind Trust: close any inventory screen while slepoe_doverie is active
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (ClientDebuffState.hideInventory() && client.currentScreen instanceof HandledScreen) {
                client.setScreen(null);
            }
        });

    }

    private void registerPacketReceivers() {

        // Solo debuff assigned to this player
        ClientPlayNetworking.registerGlobalReceiver(BurmaldaNetwork.DebuffAssignedPayload.ID,
                (payload, context) -> context.client().execute(() ->
                        ClientDebuffState.setDebuff(payload.debuffId(), payload.name(), payload.description())));

        // Solo debuff cleared (new cycle starting)
        ClientPlayNetworking.registerGlobalReceiver(BurmaldaNetwork.DebuffClearedPayload.ID,
                (payload, context) -> context.client().execute(ClientDebuffState::clearDebuff));

        // Group debuff activated for everyone
        ClientPlayNetworking.registerGlobalReceiver(BurmaldaNetwork.GroupDebuffStartPayload.ID,
                (payload, context) -> context.client().execute(() ->
                        ClientDebuffState.setGroupDebuff(payload.debuffId(), payload.name(), payload.description())));

        // Group debuff ended
        ClientPlayNetworking.registerGlobalReceiver(BurmaldaNetwork.GroupDebuffEndedPayload.ID,
                (payload, context) -> context.client().execute(ClientDebuffState::clearGroupDebuff));

        // Group debuff completed instantly (e.g. Blind Auction) — gray out HUD panel
        ClientPlayNetworking.registerGlobalReceiver(BurmaldaNetwork.GroupDebuffDonePayload.ID,
                (payload, context) -> context.client().execute(() ->
                        ClientDebuffState.groupDebuffDone = true));

        // Timer sync — update HUD countdown
        ClientPlayNetworking.registerGlobalReceiver(BurmaldaNetwork.TimerSyncPayload.ID,
                (payload, context) -> context.client().execute(() -> {
                    ClientDebuffState.ticksRemaining = payload.ticksRemaining();
                }));

        // Mod activated by a player — show announcement
        ClientPlayNetworking.registerGlobalReceiver(BurmaldaNetwork.ModActivatedPayload.ID,
                (payload, context) -> context.client().execute(() ->
                        ClientDebuffState.showAnnouncement("Burmalda", "Activated by player " + payload.activatorName())));

        // Obshchaga link notification — store linked names and show announcement
        ClientPlayNetworking.registerGlobalReceiver(BurmaldaNetwork.ObshchagaLinkPayload.ID,
                (payload, context) -> context.client().execute(() -> {
                    ClientDebuffState.obshchagaLinkedNames = payload.linkedNames();
                    ClientDebuffState.showAnnouncement("Общага", payload.linkedNames());
                }));

        // Generic announcement overlay (Filosof, Kollektory, etc.) — resolve translation keys client-side
        ClientPlayNetworking.registerGlobalReceiver(BurmaldaNetwork.AnnouncementPayload.ID,
                (payload, context) -> context.client().execute(() -> {
                    String title = net.minecraft.text.Text.translatableWithFallback(payload.title(), payload.title()).getString();
                    String body  = net.minecraft.text.Text.translatableWithFallback(payload.body(),  payload.body()).getString();
                    ClientDebuffState.showAnnouncement(title, body, payload.darken());
                }));

        // Solo debuff failed (e.g. ate hot potato)
        ClientPlayNetworking.registerGlobalReceiver(BurmaldaNetwork.DebuffFailedPayload.ID,
                (payload, context) -> context.client().execute(() ->
                        ClientDebuffState.soloDebuffFailed = true));

        // Wheel of Fortune skipped this player's debuff — show corner overlay
        ClientPlayNetworking.registerGlobalReceiver(BurmaldaNetwork.WheelSkipPayload.ID,
                (payload, context) -> context.client().execute(() -> {
                    ClientDebuffState.wheelSkipOverlayActive = true;
                    ClientDebuffState.wheelSkipStartMs = System.currentTimeMillis();
                }));

        // Casino: open wheel screen
        ClientPlayNetworking.registerGlobalReceiver(BurmaldaNetwork.CasinoOpenPayload.ID,
                (payload, context) -> context.client().execute(() ->
                        context.client().setScreen(
                                new CasinoScreen(payload.g1Id(), payload.g1Name(), payload.g2Id(), payload.g2Name()))));

        // Casino: wheel result — trigger deceleration to landed section
        ClientPlayNetworking.registerGlobalReceiver(BurmaldaNetwork.CasinoResultPayload.ID,
                (payload, context) -> context.client().execute(() ->
                        CasinoScreen.onResultReceived(payload.section())));

        // Casino: timeout — show fate overlay before death
        ClientPlayNetworking.registerGlobalReceiver(BurmaldaNetwork.CasinoTimeoutPayload.ID,
                (payload, context) -> context.client().execute(CasinoScreen::onTimeout));

        // Guide: open guide+confirm screen for all players
        ClientPlayNetworking.registerGlobalReceiver(BurmaldaNetwork.GuideOpenPayload.ID,
                (payload, context) -> context.client().execute(() ->
                        context.client().setScreen(new GuideScreen(payload.solo(), payload.players()))));

        // Guide: ready-state update (who clicked "Начать")
        ClientPlayNetworking.registerGlobalReceiver(BurmaldaNetwork.ReadyUpdatePayload.ID,
                (payload, context) -> context.client().execute(() ->
                        GuideScreen.onReadyUpdate(payload.readyNames())));

        // Guide: all confirmed — start 5-second countdown
        ClientPlayNetworking.registerGlobalReceiver(BurmaldaNetwork.StartCountdownPayload.ID,
                (payload, context) -> context.client().execute(GuideScreen::onStartCountdown));

        // Guide: cancelled by a player — close guide for everyone
        ClientPlayNetworking.registerGlobalReceiver(BurmaldaNetwork.GuideCancelledPayload.ID,
                (payload, context) -> context.client().execute(GuideScreen::onCancelled));

        // Menu: open the debuff-assignment screen
        ClientPlayNetworking.registerGlobalReceiver(BurmaldaNetwork.OpenMenuPayload.ID,
                (payload, context) -> context.client().execute(() ->
                        context.client().setScreen(new com.mixaold.burmalda.hud.BurmaldaMenuScreen(
                                payload.canManage(), payload.whitelistCsv()))));

        // Menu: whitelist changed — refresh the open screen
        ClientPlayNetworking.registerGlobalReceiver(BurmaldaNetwork.WhitelistSyncPayload.ID,
                (payload, context) -> context.client().execute(() ->
                        com.mixaold.burmalda.hud.BurmaldaMenuScreen.onWhitelistSync(payload.whitelistCsv())));

        // Event timer bar above the hotbar
        ClientPlayNetworking.registerGlobalReceiver(BurmaldaNetwork.EventTimerPayload.ID,
                (payload, context) -> context.client().execute(() -> {
                    String[] parts = payload.data().split("\\|");
                    if (parts.length == 4) {
                        try {
                            // parts[0] = translation key (resolved in the client's locale), parts[1] = literal time.
                            String label = net.minecraft.text.Text.translatableWithFallback(parts[0], parts[0]).getString() + " " + parts[1];
                            ClientDebuffState.setEventTimer(label, Float.parseFloat(parts[2]), Integer.parseInt(parts[3]));
                        } catch (NumberFormatException ignored) {}
                    }
                }));
    }
}
