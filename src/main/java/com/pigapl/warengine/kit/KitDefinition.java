package com.pigapl.warengine.kit;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;

/**
 * The contents of one kit/class, captured from an admin's inventory and stored as JSON.
 * {@link #armor} is exactly 4 entries (feet, legs, chest, head - matches {@code Inventory.armor});
 * {@link #inventory} omits empty stacks and does not preserve slot position.
 *
 * <p>The file id and {@link #displayName} are deliberately separate: two files can share a display
 * name while holding different gear, which is how a role becomes team-specific -
 * {@code assault_ak.json} and {@code assault_m16.json} both show as "Assault" and {@link TeamKits}
 * decides who sees which. Nothing is ever copied to achieve that.</p>
 *
 * <p>{@link ItemStack#CODEC} carries full data-component state, so TACZ/Create items round-trip.</p>
 */
public record KitDefinition(List<ItemStack> armor, ItemStack offhand, List<ItemStack> inventory,
                            Optional<String> displayName, Optional<ItemStack> icon,
                            Optional<String> description, Optional<Integer> limit) {

    public static final Codec<KitDefinition> CODEC = RecordCodecBuilder.create(i -> i.group(
            ItemStack.OPTIONAL_CODEC.listOf().fieldOf("armor").forGetter(KitDefinition::armor),
            ItemStack.OPTIONAL_CODEC.optionalFieldOf("offhand", ItemStack.EMPTY).forGetter(KitDefinition::offhand),
            ItemStack.CODEC.listOf().fieldOf("inventory").forGetter(KitDefinition::inventory),
            Codec.STRING.optionalFieldOf("displayName").forGetter(KitDefinition::displayName),
            ItemStack.CODEC.optionalFieldOf("icon").forGetter(KitDefinition::icon),
            Codec.STRING.optionalFieldOf("description").forGetter(KitDefinition::description),
            Codec.INT.optionalFieldOf("limit").forGetter(KitDefinition::limit)
    ).apply(i, KitDefinition::new));

    /** Content-only constructor - what {@code /kit save} captures. Display fields stay unset. */
    public KitDefinition(List<ItemStack> armor, ItemStack offhand, List<ItemStack> inventory) {
        this(armor, offhand, inventory, Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty());
    }

    /** Menu label: the explicit displayName if set, otherwise the kit's file id. */
    public String displayNameOr(String kitId) {
        return displayName.orElse(kitId);
    }

    /**
     * How many players in one SQUAD may hold this kit at once ({@code <= 0} / absent = unlimited).
     * Squad-scoped since the squad module; see {@code KitService#squadKitLimit}, which overrides this
     * with the squad's reservation for kits the team has a budget for.
     */
    public int limitOrUnlimited() {
        return limit.orElse(0);
    }

    public boolean hasLimit() {
        return limitOrUnlimited() > 0;
    }

    public KitDefinition withDisplayName(String newDisplayName) {
        return new KitDefinition(armor, offhand, inventory,
                newDisplayName == null || newDisplayName.isBlank()
                        ? Optional.empty() : Optional.of(newDisplayName),
                icon, description, limit);
    }

    /** @param newLimit {@code <= 0} clears the limit (unlimited). */
    public KitDefinition withLimit(int newLimit) {
        return new KitDefinition(armor, offhand, inventory, displayName, icon, description,
                newLimit <= 0 ? Optional.empty() : Optional.of(newLimit));
    }

    /**
     * Menu icon: the explicit icon, else the first real item (inventory, then armor, then offhand).
     * A full stack, not an item id - every TACZ gun is {@code tacz:modern_kinetic_gun} and only the
     * {@code GunId} component tells them apart.
     */
    public ItemStack iconOrGuess() {
        if (icon.isPresent() && !icon.get().isEmpty()) {
            return icon.get();
        }
        for (ItemStack stack : inventory) {
            if (!stack.isEmpty()) {
                return stack;
            }
        }
        for (ItemStack piece : armor) {
            if (!piece.isEmpty()) {
                return piece;
            }
        }
        return offhand;
    }

    public int stackCount() {
        int n = inventory.size();
        for (ItemStack a : armor) {
            if (!a.isEmpty()) n++;
        }
        if (!offhand.isEmpty()) n++;
        return n;
    }
}
