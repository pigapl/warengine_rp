package com.pigapl.warengine.kit;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;

/**
 * One kit's contents, captured from an admin's inventory and stored as JSON. {@link #armor} is
 * exactly 4 entries; {@link #inventory} omits empty stacks and does not preserve slot position.
 *
 * <p>File id and {@link #displayName} are deliberately separate: two files sharing a display name
 * while holding different gear is how a role becomes team-specific. {@link ItemStack#CODEC} carries
 * full data-component state, so TACZ/Create items round-trip.</p>
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

    public KitDefinition(List<ItemStack> armor, ItemStack offhand, List<ItemStack> inventory) {
        this(armor, offhand, inventory, Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty());
    }

    public String displayNameOr(String kitId) {
        return displayName.orElse(kitId);
    }

    /** Per-SQUAD cap ({@code <= 0} = unlimited). A budgeted kit's reservation overrides it. */
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

    public KitDefinition withLimit(int newLimit) {
        return new KitDefinition(armor, offhand, inventory, displayName, icon, description,
                newLimit <= 0 ? Optional.empty() : Optional.of(newLimit));
    }

    /** A full stack, not an item id - every TACZ gun shares one item, only {@code GunId} differs. */
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
