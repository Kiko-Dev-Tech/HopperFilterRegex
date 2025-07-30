package net.earthmc.hopperfilter.listener;

import net.earthmc.hopperfilter.HopperFilter;
import net.earthmc.hopperfilter.util.ContainerUtil;
import net.earthmc.hopperfilter.util.PatternUtil;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.apache.commons.lang3.tuple.Pair;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Hopper;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.entity.minecart.HopperMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.PatternSyntaxException;

public class InventoryActionListener implements Listener {

    private final HopperFilter plugin;

    public InventoryActionListener(HopperFilter plugin) {
        super();
        this.plugin = plugin;
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryMoveItem(final InventoryMoveItemEvent event) {
        InventoryHolder sourceHolder = event.getSource().getHolder(false);
        InventoryHolder destHolder = event.getDestination().getHolder(false);

        if (!isHopperOrMinecart(sourceHolder) && !isHopperOrMinecart(destHolder)) return;

        event.setCancelled(true); // cancel default behavior
        Bukkit.getScheduler().runTaskLater(plugin, () -> moveItem(event), 1L);
    }

    private void moveItem(InventoryMoveItemEvent event) {
        final Inventory source = event.getSource();
        final Inventory destination = event.getDestination();

        final InventoryHolder sourceHolder = source.getHolder(false);
        final InventoryHolder destHolder = destination.getHolder(false);

        String filterName = null;

        boolean isDefaultHopper = false;
        // Prefer filter on source (for output)
        if (isHopperWithFilter(sourceHolder)) {
            filterName = getCustomName(sourceHolder);
        }
        // Fallback: check destination (for input filters)
        else if (isHopperWithFilter(destHolder)) {
            filterName = getCustomName(destHolder);
        } else {
            isDefaultHopper = true;
        }

        for (int i = 0; i < source.getSize(); i++) {
            ItemStack stack = source.getItem(i);
            if (stack == null || stack.getType().isAir()) continue;

            if (!canItemPassHopper(filterName, stack) && !isDefaultHopper) continue;

            int maxToMove = Math.min(plugin.getItemsPerTransfer(), stack.getAmount());
            ItemStack toMove = stack.clone();
            toMove.setAmount(maxToMove);

            Map<Integer, ItemStack> leftovers = destination.addItem(toMove);
            int moved = maxToMove - leftovers.values().stream()
                    .mapToInt(ItemStack::getAmount)
                    .sum();

            if (moved > 0) {
                stack.setAmount(stack.getAmount() - moved);
                source.setItem(i, stack.getAmount() > 0 ? stack : null);
            }

            break; // Only move one stack
        }
    }


    private boolean isHopperOrMinecart(InventoryHolder holder) {
        return holder instanceof Hopper
                || holder instanceof HopperMinecart
                || holder instanceof org.bukkit.entity.minecart.StorageMinecart;
    }

    private boolean isHopperWithFilter(InventoryHolder holder) {
        if (holder instanceof Hopper h) return h.customName() != null;
        if (holder instanceof HopperMinecart h) return h.customName() != null;
        return false;
    }

    private String getCustomName(InventoryHolder holder) {
        if (holder instanceof Hopper h) return PatternUtil.serialiseComponent(h.customName());
        if (holder instanceof HopperMinecart h) return PatternUtil.serialiseComponent(h.customName());
        return null;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryPickupItem(final InventoryPickupItemEvent event) {
        final Inventory inventory = event.getInventory();
        if (!inventory.getType().equals(InventoryType.HOPPER)) return;

        final InventoryHolder holder = inventory.getHolder(false);

        String hopperName;
        if (holder instanceof final Hopper hopper) {
            hopperName = PatternUtil.serialiseComponent(hopper.customName());
        } else if (holder instanceof final HopperMinecart hopperMinecart) {
            hopperName = PatternUtil.serialiseComponent(hopperMinecart.customName());
        } else {
            return;
        }

        if (hopperName == null) return;

        if (!canItemPassHopper(hopperName, event.getItem().getItemStack())) event.setCancelled(true);
    }

    private boolean shouldCancelDueToMoreSuitableHopper(final Inventory source, final Hopper destinationHopper, final ItemStack item) {
        // If the source is not a hopper we don't care about it
        if (!(source.getHolder(false) instanceof final Hopper sourceHopper)) return false;

        final org.bukkit.block.data.type.Hopper initiatorHopperData = (org.bukkit.block.data.type.Hopper) sourceHopper.getBlockData();

        // If the hopper is facing down that means it isn't possible for another hopper to also take items out of this hopper
        final BlockFace facing = initiatorHopperData.getFacing();
        if (facing.equals(BlockFace.DOWN)) return false;

        Block facingBlock = sourceHopper.getBlock().getRelative(facing);

        // If the relative block is not a hopper we don't care about it as that means our original destination is the only possible destination
        if (!facingBlock.getType().equals(Material.HOPPER)) return false;

        final Hopper facingHopper = (Hopper) facingBlock.getState(false);

        Hopper otherHopper;
        if (facingHopper.equals(destinationHopper)) { // We need to check the hopper below
            facingBlock = sourceHopper.getBlock().getRelative(BlockFace.DOWN);
            if (!facingBlock.getType().equals(Material.HOPPER)) return false; // We can safely say the original is the only hopper

            otherHopper = (Hopper) facingBlock.getState(false);
        } else { // We need to check this hopper
            otherHopper = facingHopper;
        }

        if (!ContainerUtil.canHopperInventoryFitItemStack(otherHopper.getInventory(), item)) return false; // This hopper cannot fit the item, return false to avoid clogging

        final String hopperName = PatternUtil.serialiseComponent(otherHopper.customName());
        if (hopperName == null) return false;

        // Before this method is called we are certain the destinationHopper does not have a name
        // If the other hopper we found here can also pass this item through but does have a name we cancel this movement to prevent
        // items moving to unnamed hoppers when there is a hopper specifically filtering for this item
        return canItemPassHopper(hopperName, item);
    }

    private boolean canItemPassHopper(final String hopperName, final ItemStack item) {
        if (hopperName == null) return true;

        if (hopperName.startsWith("r:")) {
            return filterByRegex(hopperName, item);
        }
        nextCondition: for (final String condition : hopperName.split(",")) {
            nextAnd: for (final String andString : condition.split("&")) {
                for (final String orString : andString.split("\\|")) {
                    final String pattern = orString.toLowerCase().strip();
                    if (canItemPassPattern(pattern, item)) continue nextAnd;
                }
                continue nextCondition;
            }
            return true;
        }

        return false;
    }

    /** Add Regex Support by Kiko (https://github.com/Kiko-Dev-Tech)
     *
     * @param pattern
     * @param item
     * @return
     */
    private boolean filterByRegex(final String pattern, final ItemStack item) {
        final String itemName = item.getType().getKey().getKey();
        if (pattern.isEmpty() || pattern.equals(itemName)) return true;
        final String regex = pattern.substring(2);
        try {
            return itemName.matches(regex);
        } catch (PatternSyntaxException e) {
            Bukkit.getLogger().warning("Invalid regex pattern in hopper filter: " + regex);
            return false;
        }
    }

    private boolean canItemPassPattern(final String pattern, final ItemStack item) {
        final String itemName = item.getType().getKey().getKey();
        if (pattern.isEmpty() || pattern.equals(itemName)) return true;

        final char prefix = pattern.charAt(0);
        final String string = pattern.substring(1);
        return switch (prefix) {
            case '!' -> !canItemPassPattern(string, item);
            case '*' -> itemName.contains(string);
            case '^' -> itemName.startsWith(string);
            case '$' -> itemName.endsWith(string);
            case '#' -> {
                final NamespacedKey key = NamespacedKey.fromString(string);
                if (key == null) yield false;
                Tag<Material> tag = Bukkit.getTag(Tag.REGISTRY_BLOCKS, key, Material.class);
                if (tag == null) tag = Bukkit.getTag(Tag.REGISTRY_ITEMS, key, Material.class);
                yield tag != null && tag.isTagged(item.getType());
            }
            case '~' -> doesItemHaveSpecifiedPotionEffect(item, string);
            case '+' -> doesItemHaveSpecifiedEnchantment(item, string);
            case '=' -> {
                String displayName = PlainTextComponentSerializer.plainText().serialize(item.displayName())
                        .toLowerCase()
                        .replaceAll(" ", "_");
                displayName = displayName.substring(1, displayName.length() - 1);
                yield displayName.equals(string);
            }
            default -> false;
        };
    }

    private boolean doesItemHaveSpecifiedPotionEffect(ItemStack item, String string) {
        final Material material = item.getType();
        if (!(material.equals(Material.POTION) || material.equals(Material.SPLASH_POTION) || material.equals(Material.LINGERING_POTION))) return false;

        final Pair<String, Integer> pair = PatternUtil.getNameLevelPairFromString(string);

        final PotionEffectType type = (PotionEffectType) PatternUtil.getKeyedFromString(pair.getLeft(), Registry.POTION_EFFECT_TYPE);
        if (type == null) return false;

        final Integer userLevel = pair.getRight();

        final PotionMeta meta = (PotionMeta) item.getItemMeta();
        final PotionType potionType = meta.getBasePotionType();
        if (potionType == null)
            return false;

        final List<PotionEffect> effects = potionType.getPotionEffects();
        if (userLevel == null) {
            for (PotionEffect effect : effects) {
                if (effect.getType().equals(type)) return true;
            }
            return meta.hasCustomEffect(type);
        } else {
            for (PotionEffect effect : effects) {
                if (effect.getType().equals(type) && effect.getAmplifier() + 1 == userLevel) return true;
            }
            for (PotionEffect effect : meta.getCustomEffects()) {
                if (effect.getType().equals(type) && effect.getAmplifier() + 1 == userLevel) return true;
            }
        }

        return false;
    }

    private boolean doesItemHaveSpecifiedEnchantment(ItemStack item, String string) {
        Map<Enchantment, Integer> enchantments;
        if (item.getType().equals(Material.ENCHANTED_BOOK)) {
            final EnchantmentStorageMeta meta = (EnchantmentStorageMeta) item.getItemMeta();
            enchantments = meta.getStoredEnchants();
        } else {
            enchantments = item.getEnchantments();
        }

        final Pair<String, Integer> pair = PatternUtil.getNameLevelPairFromString(string);

        final Enchantment enchantment = (Enchantment) PatternUtil.getKeyedFromString(pair.getLeft(), Registry.ENCHANTMENT);
        if (enchantment == null) return false;

        final Integer userLevel = pair.getRight();

        final Integer enchantmentLevel = enchantments.get(enchantment);
        if (userLevel == null) {
            return enchantmentLevel != null;
        } else {
            return enchantmentLevel != null && (enchantmentLevel).equals(userLevel);
        }
    }
}
