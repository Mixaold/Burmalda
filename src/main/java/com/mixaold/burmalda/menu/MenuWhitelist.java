package com.mixaold.burmalda.menu;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Players (by name) allowed to use the Burmalda menu in addition to the owner tier
 * (operators + whoever launched the mod). Managed from the menu itself by the owner.
 * Persisted as one name per line under the config dir so it survives restarts and is
 * shared across the host's worlds.
 */
public final class MenuWhitelist {
    private MenuWhitelist() {}

    private static final Set<String> NAMES = new LinkedHashSet<>();
    private static boolean loaded = false;

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("burmalda_menu_whitelist.txt");
    }

    private static synchronized void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        try {
            Path f = file();
            if (Files.exists(f)) {
                for (String line : Files.readAllLines(f)) {
                    String n = line.trim();
                    if (!n.isEmpty()) NAMES.add(n);
                }
            }
        } catch (IOException ignored) {}
    }

    public static synchronized boolean contains(String name) {
        ensureLoaded();
        return NAMES.contains(name);
    }

    /** Toggle a name; returns true if the player is now whitelisted. */
    public static synchronized boolean toggle(String name) {
        ensureLoaded();
        boolean now;
        if (NAMES.contains(name)) { NAMES.remove(name); now = false; }
        else { NAMES.add(name); now = true; }
        save();
        return now;
    }

    /** Comma-joined whitelist for sending to clients. */
    public static synchronized String csv() {
        ensureLoaded();
        return String.join(",", NAMES);
    }

    private static void save() {
        try { Files.write(file(), NAMES); } catch (IOException ignored) {}
    }
}
