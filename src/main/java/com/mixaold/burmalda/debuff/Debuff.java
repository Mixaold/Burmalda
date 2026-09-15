package com.mixaold.burmalda.debuff;

import net.minecraft.block.BlockState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;

public abstract class Debuff {

    private final String id;
    private final String name;
    private final String description;
    private final DebuffType type;

    protected Debuff(String id, String name, String description, DebuffType type) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.type = type;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public DebuffType getType() { return type; }

    /** Called once when the debuff is assigned to a player. */
    public void onStart(ServerPlayerEntity player) {}

    /** Called every server tick while the debuff is active for a player. */
    public void onPlayerTick(ServerPlayerEntity player, int ticksActive) {}

    /** Called once when the debuff expires or is forcibly removed. */
    public void onEnd(ServerPlayerEntity player) {}

    /** Called after the player breaks a block while this debuff is active. */
    public void onBlockBreak(ServerPlayerEntity player, BlockPos pos, BlockState state) {}

    /** Called when the player deals damage to another entity while this debuff is active. */
    public void onCausedDamage(ServerPlayerEntity attacker) {}

    /**
     * Called when the player receives damage while this debuff is active.
     * Return true to consume the event (prevent default damage — use carefully).
     */
    public boolean onDamage(ServerPlayerEntity player, float amount) { return false; }

    /** Called once when a GROUP debuff starts for the entire server. */
    public void onGroupStart(MinecraftServer server) {}

    /** Called every server tick while a GROUP debuff is active. */
    public void onGroupTick(MinecraftServer server, int ticksActive) {}

    /** Called once when a GROUP debuff ends. */
    public void onGroupEnd(MinecraftServer server) {}

    /**
     * Called when a player sends a chat message while this debuff is active (their own solo debuff
     * and/or the active group debuff). Lets a debuff react to what players type. Default: no-op.
     */
    public void onChat(ServerPlayerEntity sender, String message, MinecraftServer server) {}
}
