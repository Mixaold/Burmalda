package com.mixaold.burmalda.advancement;

import net.minecraft.advancement.criterion.Criteria;
import net.minecraft.server.network.ServerPlayerEntity;

public class BurmaldaAdvancements {

    public static BurmaldaEventCriterion EVENT;

    public static final String MOD_STARTED            = "mod_started";
    public static final String BOMBA_EXPLODE          = "bomba_explode";
    public static final String HOT_POTATO             = "hot_potato";
    public static final String OBSHCHAGA_LINKED       = "obshchaga_linked";
    public static final String CYCLE_SURVIVED         = "cycle_survived";
    public static final String LIGHTNING_HIT          = "lightning_hit";
    public static final String ANTIGRAVITY_LAUNCH     = "antigravity_launch";
    public static final String KLEPTO_VICTIM          = "klepto_victim";
    public static final String SHAHED_SURVIVED        = "shahed_survived";
    public static final String SHAHED_ACE             = "shahed_ace";
    public static final String ZOOPARK_WITHER_SURVIVED = "zoopark_wither_survived";
    public static final String DEMBEL                 = "dembel";
    public static final String COMMANDER_TOUCHED      = "commander_touched";
    public static final String PANDORA_OPENED         = "pandora_opened";
    public static final String DOPROS_DOOR            = "dopros_door";
    public static final String WELCOME                = "welcome";

    public static void register() {
        EVENT = Criteria.register("burmalda:event", new BurmaldaEventCriterion());
    }

    public static void trigger(ServerPlayerEntity player, String event) {
        if (EVENT != null) EVENT.trigger(player, event);
    }
}
