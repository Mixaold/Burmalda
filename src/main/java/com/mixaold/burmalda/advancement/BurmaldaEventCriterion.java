package com.mixaold.burmalda.advancement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancement.criterion.AbstractCriterion;
import net.minecraft.predicate.entity.LootContextPredicate;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Optional;

public class BurmaldaEventCriterion extends AbstractCriterion<BurmaldaEventCriterion.Conditions> {

    public record Conditions(Optional<LootContextPredicate> player, String event)
            implements AbstractCriterion.Conditions {

        public static final Codec<Conditions> CODEC = RecordCodecBuilder.create(i ->
                i.group(
                        LootContextPredicate.CODEC.optionalFieldOf("player").forGetter(Conditions::player),
                        Codec.STRING.fieldOf("event").forGetter(Conditions::event)
                ).apply(i, Conditions::new));
    }

    @Override
    public Codec<Conditions> getConditionsCodec() {
        return Conditions.CODEC;
    }

    public void trigger(ServerPlayerEntity player, String event) {
        super.trigger(player, c -> c.event().equals(event));
    }
}
