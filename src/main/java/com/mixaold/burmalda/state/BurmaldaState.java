package com.mixaold.burmalda.state;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.world.World;
//? if >=1.21.5 {
/*import net.minecraft.world.PersistentStateType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
*///?}

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BurmaldaState extends PersistentState {

    private static final String STATE_KEY = "burmalda_state";

    public boolean modActive = false;
    public int cycleCount = 0;
    public int ticksInCycle = 0;

    // UUID → current solo debuff ID
    public final Map<UUID, String> playerDebuffMap = new HashMap<>();

    // Current group debuff ID (null if none active)
    public String currentGroupDebuffId = null;
    public int groupDebuffTicks = 0;

    // Obshchaga link: UUID → list of linked UUIDs
    public final Map<UUID, UUID[]> obshchagaLinks = new HashMap<>();

    public BurmaldaState() {}

    //? if >=1.21.5 {
    /*// 1.21.5+ : PersistentState is fully Codec-based; only cycleCount is persisted.
    private static final Codec<BurmaldaState> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.INT.optionalFieldOf("cycleCount", 0).forGetter(s -> s.cycleCount)
    ).apply(inst, cc -> {
        BurmaldaState s = new BurmaldaState();
        s.cycleCount = cc;
        return s;
    }));

    private static final PersistentStateType<BurmaldaState> TYPE =
            new PersistentStateType<>(STATE_KEY, BurmaldaState::new, CODEC, null);

    public static BurmaldaState getServerState(MinecraftServer server) {
        PersistentStateManager manager = server.getWorld(World.OVERWORLD).getPersistentStateManager();
        return manager.getOrCreate(TYPE);
    }
    *///?} else {
    public static BurmaldaState getServerState(MinecraftServer server) {
        PersistentStateManager manager = server.getWorld(World.OVERWORLD).getPersistentStateManager();
        return manager.getOrCreate(createType(), STATE_KEY);
    }

    private static PersistentState.Type<BurmaldaState> createType() {
        return new PersistentState.Type<>(BurmaldaState::new, BurmaldaState::fromNbt, null);
    }

    public static BurmaldaState fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        BurmaldaState state = new BurmaldaState();
        // modActive intentionally not loaded — mod always starts inactive on world load
        state.cycleCount = com.mixaold.burmalda.util.BurmaldaCompat.nbtGetInt(nbt, "cycleCount");
        // ticksInCycle, playerDebuffMap, groupDebuffId not loaded — reset on start
        return state;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        // Only persist cycle count as historical data — active state resets on load
        nbt.putInt("cycleCount", cycleCount);
        return nbt;
    }
    //?}
}
