package com.mixaold.burmalda.debuff.solo;

import com.mixaold.burmalda.debuff.Debuff;
import com.mixaold.burmalda.debuff.DebuffType;
import com.mixaold.burmalda.network.BurmaldaNetwork;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.component.type.WrittenBookContentComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.SkeletonEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.RawFilteredPair;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class KollektoryDebuff extends Debuff {

    private static final int TASK_DURATION = 600; // 30 sec
    private static final Random RANDOM = new Random();

    private record Task(String id, TaskCheck check) {}
    @FunctionalInterface
    private interface TaskCheck { boolean test(ServerPlayerEntity p, boolean prevOnGround); }

    private static final Task[] TASKS = {
        new Task("water",  (p, pg) -> p.isTouchingWater()),
        new Task("lava",   (p, pg) -> p.isInLava()),
        new Task("sneak",  (p, pg) -> p.isSneaking()),
        new Task("jump",   (p, pg) -> pg && !p.isOnGround() && p.getVelocity().y > 0),
        new Task("sky",    (p, pg) -> com.mixaold.burmalda.util.BurmaldaCompat.world(p).isSkyVisible(p.getBlockPos())),
        new Task("high",   (p, pg) -> p.getY() > 100),
        new Task("near",   KollektoryDebuff::nearOtherPlayer),
        new Task("low",    (p, pg) -> p.getY() < 30),
    };

    private int currentTask = 0;
    private boolean taskCompleted = false;
    private boolean prevOnGround = true;
    private boolean pendingDeathAnnouncement = false;
    private final List<Integer> collectorIds = new ArrayList<>();

    public KollektoryDebuff() {
        super("kollektory", "Debt Collectors",
                "Task every 30 sec. Fail it — they come for you",
                DebuffType.SOLO);
    }

    @Override
    public void onStart(ServerPlayerEntity player) {
        currentTask = RANDOM.nextInt(TASKS.length);
        taskCompleted = false;
        prevOnGround = player.isOnGround();
        pendingDeathAnnouncement = false;
        collectorIds.clear();
        giveBook(player);
        spawnCollectors(player); // present from the start — and they stick around until the debuff ends
        player.sendMessage(Text.translatableWithFallback("burmalda.chat.kollektory.task", "§c[Collectors] §fTask: §f%s",
                taskText(currentTask)), false);
    }

    @Override
    public void onEnd(ServerPlayerEntity player) {
        removeBook(player);
        // Collectors are NOT removed — they stay in the world even after the debuff ends.
    }

    @Override
    public void onPlayerTick(ServerPlayerEntity player, int ticksActive) {
        if (pendingDeathAnnouncement && player.isAlive() && !player.isDead()) {
            BurmaldaNetwork.sendAnnouncement(player, "burmalda.kollektory.announce_title", "burmalda.kollektory.announce_body");
            updateBook(player);
            pendingDeathAnnouncement = false;
        }

        boolean onGround = player.isOnGround();

        if (!taskCompleted && TASKS[currentTask].check().test(player, prevOnGround)) {
            taskCompleted = true;
        }
        prevOnGround = onGround;

        // Actionbar countdown
        if (ticksActive % 20 == 0) {
            int elapsed = ticksActive % TASK_DURATION;
            int remaining = (TASK_DURATION - elapsed) / 20;
            player.sendMessage(Text.translatableWithFallback("burmalda.chat.kollektory.countdown",
                    "§c[Collectors] §f%s §7— %s sec", taskText(currentTask), remaining), true);
        }

        // Task cycle end
        if (ticksActive % TASK_DURATION == 0 && ticksActive > 0) {
            // Collectors NEVER vanish. Every failed task simply piles ANOTHER squad on top of the
            // last (capped only so a marathon session can't melt the server).
            if (!taskCompleted) {
                if (collectorIds.size() < 40) spawnCollectors(player);
                pendingDeathAnnouncement = true;
            }

            int next = currentTask;
            if (TASKS.length > 1) {
                while (next == currentTask) next = RANDOM.nextInt(TASKS.length);
            }
            currentTask = next;
            taskCompleted = false;
            updateBook(player);
            player.sendMessage(Text.translatableWithFallback("burmalda.chat.kollektory.new_task", "§c[Collectors] §fNew task: §f%s", taskText(currentTask)), false);
        }
    }

    private void spawnCollectors(ServerPlayerEntity player) {
        ServerWorld world = com.mixaold.burmalda.util.BurmaldaCompat.world(player);
        //? if >=1.21.2 {
        /*var enchantReg = com.mixaold.burmalda.util.BurmaldaCompat.server(player).getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT);
        *///? } else
        var enchantReg = com.mixaold.burmalda.util.BurmaldaCompat.server(player).getRegistryManager().getWrapperOrThrow(RegistryKeys.ENCHANTMENT);
        RegistryEntry<Enchantment> powerEntry = enchantReg.getOrThrow(Enchantments.POWER);

        for (int i = 0; i < 3; i++) {
            SkeletonEntity skeleton = com.mixaold.burmalda.util.BurmaldaCompat.create(EntityType.SKELETON, world);
            if (skeleton == null) continue;

            double ox = (i - 1) * 3.0;
            skeleton.setPosition(player.getX() + ox, player.getY(), player.getZ() + 3);

            skeleton.equipStack(EquipmentSlot.HEAD,  new ItemStack(Items.NETHERITE_HELMET));
            skeleton.equipStack(EquipmentSlot.CHEST, new ItemStack(Items.NETHERITE_CHESTPLATE));
            skeleton.equipStack(EquipmentSlot.LEGS,  new ItemStack(Items.NETHERITE_LEGGINGS));
            skeleton.equipStack(EquipmentSlot.FEET,  new ItemStack(Items.NETHERITE_BOOTS));

            ItemStack bow = new ItemStack(Items.BOW);
            bow.addEnchantment(powerEntry, 5);
            skeleton.equipStack(EquipmentSlot.MAINHAND, bow);

            for (EquipmentSlot slot : EquipmentSlot.values()) {
                skeleton.setEquipmentDropChance(slot, 0.0f);
            }

            skeleton.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, Integer.MAX_VALUE, 2, false, false));
            skeleton.setCustomName(Text.translatableWithFallback("burmalda.kollektory.collector_name", "§c[COLLECTOR]"));
            skeleton.setCustomNameVisible(true);
            skeleton.setGlowing(true);
            skeleton.setPersistent();
            skeleton.setTarget(player);

            world.spawnEntity(skeleton);
            collectorIds.add(skeleton.getId());
        }

        player.sendMessage(Text.translatableWithFallback("burmalda.chat.kollektory.punish", "§4THEY §c— came to punish you."), true);
    }


    private static Text taskText(int index) {
        String id = TASKS[index].id();
        return Text.translatableWithFallback("burmalda.kollektory.task." + id, id);
    }

    private static boolean nearOtherPlayer(ServerPlayerEntity player, boolean prevOnGround) {
        for (ServerPlayerEntity other : com.mixaold.burmalda.util.BurmaldaCompat.server(player).getPlayerManager().getPlayerList()) {
            if (!other.getUuid().equals(player.getUuid()) && other.distanceTo(player) < 3.0) return true;
        }
        return false;
    }

    private void giveBook(ServerPlayerEntity player) {
        player.getInventory().offerOrDrop(buildBook());
    }

    private void updateBook(ServerPlayerEntity player) {
        for (int i = 0; i < player.getInventory().size(); i++) {
            if (isCollectorBook(player.getInventory().getStack(i))) {
                player.getInventory().setStack(i, buildBook());
                player.playerScreenHandler.syncState();
                return;
            }
        }
        player.getInventory().offerOrDrop(buildBook());
    }

    private void removeBook(ServerPlayerEntity player) {
        for (int i = 0; i < player.getInventory().size(); i++) {
            if (isCollectorBook(player.getInventory().getStack(i))) {
                player.getInventory().setStack(i, ItemStack.EMPTY);
                break;
            }
        }
    }

    private ItemStack buildBook() {
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);

        NbtCompound tag = new NbtCompound();
        tag.putBoolean("burmalda_collector", true);
        book.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(tag));

        Text page = Text.empty()
                .append(Text.translatableWithFallback("burmalda.kollektory.book.req", "☠ Requirement:").formatted(Formatting.DARK_RED))
                .append(Text.literal("\n\n"))
                .append(taskText(currentTask))
                .append(Text.literal("\n\n"))
                .append(Text.translatableWithFallback("burmalda.kollektory.book.time", "Time limit: 30 seconds").formatted(Formatting.DARK_GRAY))
                .append(Text.literal("\n\n"))
                .append(Text.translatableWithFallback("burmalda.kollektory.book.fail", "Fail — and they come for you.").formatted(Formatting.DARK_RED));
        WrittenBookContentComponent content = new WrittenBookContentComponent(
                RawFilteredPair.of("Collectors"),
                "Collectors",
                0,
                List.of(RawFilteredPair.of(page)),
                true);
        book.set(DataComponentTypes.WRITTEN_BOOK_CONTENT, content);

        return book;
    }

    private static boolean isCollectorBook(ItemStack stack) {
        if (!stack.isOf(Items.WRITTEN_BOOK)) return false;
        NbtComponent data = stack.get(DataComponentTypes.CUSTOM_DATA);
        return data != null && data.copyNbt().contains("burmalda_collector");
    }
}
