package com.mixaold.burmalda.sound;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;

public class BurmaldaSounds {

    public static SoundEvent DEATH_STAR;
    public static SoundEvent THUNDER_RUMBLE;
    public static SoundEvent ACTIVATION;

    public static void register() {
        DEATH_STAR = register("death_star");
        THUNDER_RUMBLE = register("thunder_rumble");
        ACTIVATION = register("activation");
    }

    /** Plays a short "тунь" pling sound at the player's position (heard by nearby players too). */
    public static void playTune(ServerPlayerEntity player) {
        com.mixaold.burmalda.util.BurmaldaCompat.world(player).playSound(
                null,
                player.getX(), player.getY(), player.getZ(),
                SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(),
                SoundCategory.PLAYERS,
                0.8f, 2.0f
        );
    }

    /** Broadcasts the tune from a world position (for group debuff actions). */
    public static void playTuneAll(ServerWorld world, double x, double y, double z) {
        world.playSound(
                null, x, y, z,
                SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(),
                SoundCategory.PLAYERS,
                0.8f, 2.0f
        );
    }

    private static SoundEvent register(String name) {
        Identifier id = Identifier.of("burmalda", name);
        return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
    }
}
