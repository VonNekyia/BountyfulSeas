package com.nekyia.bountyfulSeas;

import com.nekyia.bountyfulSeas.nexo.NexoItem;
import com.nekyia.bountyfulSeas.nexo.NexoItemFactory;
import de.mcterranova.terranovaLib.roseGUI.RoseGUI;
import de.mcterranova.terranovaLib.roseGUI.RoseItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.UUID;

/**
 * The bait bucket: where bait is kept, and which of it is on the hook.
 *
 * <p>Bait items are plain Nexo items that other plugins drop. They do nothing on
 * their own; they are put into a bucket, one kind is picked, and while the bucket
 * anywhere in the inventory holds some of the picked kind, the line is baited. A
 * baited line is the only one anything rare or better bites on, and every catch on
 * it uses one up.
 *
 * <p>What a bucket holds is kept on the bucket itself, so it travels with the item
 * through chests, deaths and trades with no table behind it.
 */
final class BaitBucket {

    static final String BUCKET = "bait_bucket";

    /** Every bait there is, by Nexo id, with its name. They all work the same, for now. */
    static final Map<String, String> BAITS = new LinkedHashMap<>();

    static {
        BAITS.put("earthworm", "Regenwurm");
        BAITS.put("beech_worm", "Buchenwurm");
        BAITS.put("rice_beetle", "Reiskäfer");
        BAITS.put("field_beetle", "Feldkäfer");
    }

    /** How much of each kind a new bucket takes, until something makes it bigger. */
    static final int DEFAULT_CAPACITY = 64;

    private static final String NAMESPACE = "bountyfulseas";
    private static final NamespacedKey CAPACITY = new NamespacedKey(NAMESPACE, "bait_capacity");
    private static final NamespacedKey SELECTED = new NamespacedKey(NAMESPACE, "bait_selected");
    /** Two fresh buckets would otherwise stack, and a stack of buckets shares one set of counts. */
    private static final NamespacedKey IDENTITY = new NamespacedKey(NAMESPACE, "bait_bucket_id");

    private BaitBucket() {
    }

    /** The baits and the bucket, as items Nexo can be given. */
    static List<NexoItem> nexoItems() {
        List<NexoItem> items = new ArrayList<>();
        BAITS.forEach((id, name) -> items.add(item(id, "<#c8a07a>" + name,
                List.of("<!italic><dark_gray>Köder", "<italic><gray>Gehört in den Köder-Eimer."))));
        items.add(item(BUCKET, "<gold>Köder-Eimer", List.of(
                "<!italic><dark_gray>Köder",
                "<italic><gray>Rechtsklick zum Öffnen. Seltene Fische beißen nur mit Köder.")));
        return items;
    }

    /** Whether this player's line is baited right now. */
    static boolean hasBait(Player player) {
        int slot = bucketSlot(player.getInventory());
        if (slot < 0) {
            return false;
        }
        ItemMeta meta = player.getInventory().getItem(slot).getItemMeta();
        String selected = selected(meta);
        return selected != null && count(meta, selected) > 0;
    }

    /** Uses up one of the picked bait, and says so when that was the last. */
    static void consume(Player player) {
        PlayerInventory inventory = player.getInventory();
        int slot = bucketSlot(inventory);
        if (slot < 0) {
            return;
        }
        ItemStack bucket = inventory.getItem(slot);
        ItemMeta meta = bucket.getItemMeta();
        String selected = selected(meta);
        if (selected == null) {
            return;
        }
        int left = count(meta, selected) - 1;
        setCount(meta, selected, left);
        describe(meta);
        bucket.setItemMeta(meta);
        inventory.setItem(slot, bucket);
        if (left <= 0) {
            player.sendMessage(Component.text("Your bait bucket is out of "
                    + BAITS.get(selected) + ".", NamedTextColor.YELLOW));
        }
    }

    /** Sets how much of each kind the bucket a player carries takes. False when they carry none. */
    static boolean setCapacity(Player player, int capacity) {
        PlayerInventory inventory = player.getInventory();
        int slot = bucketSlot(inventory);
        if (slot < 0) {
            return false;
        }
        ItemStack bucket = inventory.getItem(slot);
        ItemMeta meta = bucket.getItemMeta();
        meta.getPersistentDataContainer().set(CAPACITY, PersistentDataType.INTEGER, Math.max(1, capacity));
        describe(meta);
        bucket.setItemMeta(meta);
        inventory.setItem(slot, bucket);
        return true;
    }

    private static int bucketSlot(PlayerInventory inventory) {
        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            if (isBucket(contents[slot])) {
                return slot;
            }
        }
        return -1;
    }

    private static boolean isBucket(ItemStack stack) {
        return BUCKET.equals(NexoItemFactory.idOf(stack));
    }

    private static NamespacedKey countKey(String bait) {
        return new NamespacedKey(NAMESPACE, "bait_" + bait);
    }

    static int count(ItemMeta meta, String bait) {
        return meta.getPersistentDataContainer().getOrDefault(countKey(bait), PersistentDataType.INTEGER, 0);
    }

    private static void setCount(ItemMeta meta, String bait, int count) {
        meta.getPersistentDataContainer().set(countKey(bait), PersistentDataType.INTEGER, Math.max(0, count));
    }

    static int capacity(ItemMeta meta) {
        return meta.getPersistentDataContainer().getOrDefault(CAPACITY, PersistentDataType.INTEGER, DEFAULT_CAPACITY);
    }

    static String selected(ItemMeta meta) {
        return meta.getPersistentDataContainer().get(SELECTED, PersistentDataType.STRING);
    }

    /** The lore that shows what is inside, rewritten after every change. */
    private static void describe(ItemMeta meta) {
        PersistentDataContainer data = meta.getPersistentDataContainer();
        if (!data.has(IDENTITY)) {
            data.set(IDENTITY, PersistentDataType.STRING, UUID.randomUUID().toString());
        }
        String selected = selected(meta);
        List<Component> lore = new ArrayList<>();
        lore.add(line("Right-click to open", NamedTextColor.DARK_GRAY));
        lore.add(Component.empty());
        BAITS.forEach((id, name) -> lore.add(line((id.equals(selected) ? "▶ " : "  ") + name + "  "
                + count(meta, id) + " / " + capacity(meta),
                id.equals(selected) ? NamedTextColor.GREEN : NamedTextColor.GRAY)));
        meta.lore(lore);
    }

    private static Component line(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    private static NexoItem item(String id, String displayName, List<String> lore) {
        return new NexoItem() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public String file() {
                return "misc";
            }

            @Override
            public String displayName() {
                return displayName;
            }

            @Override
            public List<String> lore() {
                return lore;
            }

            @Override
            public OptionalDouble foodSaturation() {
                return OptionalDouble.empty();
            }
        };
    }

    /** Opens the bucket on a right-click with it in hand. */
    static final class Opener implements Listener {

        @EventHandler
        public void onUse(PlayerInteractEvent event) {
            if (event.getHand() != EquipmentSlot.HAND
                    || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)
                    || !isBucket(event.getItem())) {
                return;
            }
            event.setCancelled(true);
            new Menu(event.getPlayer()).open();
        }
    }

    /**
     * The inside of the bucket. Left-click picks a bait, right-click tips every one
     * of that kind from the inventory in, shift-click takes a stack back out.
     */
    static final class Menu extends RoseGUI {

        private static final int[] SLOTS = {10, 12, 14, 16};

        Menu(Player player) {
            super(player, "bountyfulseas-bait-bucket",
                    Component.text("Köder-Eimer", NamedTextColor.GOLD, TextDecoration.BOLD), 3);
        }

        @Override
        public void onOpen(InventoryOpenEvent event) {
            fillGui(new RoseItem.Builder()
                    .material(Material.BLACK_STAINED_GLASS_PANE)
                    .displayName(Component.empty())
                    .build());
            ItemStack bucket = player.getInventory().getItemInMainHand();
            if (!isBucket(bucket)) {
                return;
            }
            ItemMeta meta = bucket.getItemMeta();
            String selected = selected(meta);
            int index = 0;
            for (Map.Entry<String, String> bait : BAITS.entrySet()) {
                addItem(SLOTS[index++], icon(bait.getKey(), bait.getValue(), meta, bait.getKey().equals(selected)));
            }
            addItem(22, new RoseItem.Builder()
                    .material(Material.BOOK)
                    .displayName(line("How bait works", NamedTextColor.YELLOW))
                    .addLore(line("Rare fish and better only bite on a baited line.", NamedTextColor.GRAY),
                            line("Keep this bucket anywhere in your inventory:", NamedTextColor.GRAY),
                            line("every catch uses one of the picked bait.", NamedTextColor.GRAY))
                    .build());
        }

        private RoseItem icon(String id, String name, ItemMeta meta, boolean picked) {
            ItemStack stack = NexoItemFactory.create(id);
            RoseItem.Builder builder = new RoseItem.Builder();
            if (stack != null) {
                builder.copyStack(stack);
            } else {
                builder.material(Material.WHEAT_SEEDS);
            }
            return builder
                    .displayName(line((picked ? "▶ " : "") + name, picked ? NamedTextColor.GREEN : NamedTextColor.WHITE))
                    .addLore(line(count(meta, id) + " / " + capacity(meta), NamedTextColor.AQUA),
                            Component.empty(),
                            line(picked ? "on the hook" : "Left-click to put it on the hook",
                                    picked ? NamedTextColor.GREEN : NamedTextColor.YELLOW),
                            line("Right-click to put all of yours in", NamedTextColor.YELLOW),
                            line("Shift-click to take a stack out", NamedTextColor.YELLOW))
                    .build()
                    .onClick(click -> act(id, click));
        }

        private void act(String bait, InventoryClickEvent click) {
            PlayerInventory inventory = player.getInventory();
            ItemStack bucket = inventory.getItemInMainHand();
            if (!isBucket(bucket)) {
                player.closeInventory();
                return;
            }
            ItemMeta meta = bucket.getItemMeta();
            if (click.isShiftClick()) {
                takeOut(inventory, meta, bait);
            } else if (click.isRightClick()) {
                putIn(inventory, meta, bait);
            } else {
                meta.getPersistentDataContainer().set(SELECTED, PersistentDataType.STRING, bait);
            }
            describe(meta);
            bucket.setItemMeta(meta);
            inventory.setItemInMainHand(bucket);
            new Menu(player).open();
        }

        private static void putIn(PlayerInventory inventory, ItemMeta meta, String bait) {
            int room = capacity(meta) - count(meta, bait);
            for (int slot = 0; slot < inventory.getSize() && room > 0; slot++) {
                ItemStack stack = inventory.getItem(slot);
                if (!bait.equals(NexoItemFactory.idOf(stack))) {
                    continue;
                }
                int moved = Math.min(room, stack.getAmount());
                stack.setAmount(stack.getAmount() - moved);
                inventory.setItem(slot, stack.getAmount() > 0 ? stack : null);
                room -= moved;
                setCount(meta, bait, count(meta, bait) + moved);
            }
        }

        private void takeOut(PlayerInventory inventory, ItemMeta meta, String bait) {
            ItemStack stack = NexoItemFactory.create(bait);
            int taken = Math.min(count(meta, bait), stack == null ? 0 : stack.getMaxStackSize());
            if (taken <= 0) {
                return;
            }
            stack.setAmount(taken);
            setCount(meta, bait, count(meta, bait) - taken);
            inventory.addItem(stack).values()
                    .forEach(left -> player.getWorld().dropItem(player.getLocation(), left));
        }
    }
}
