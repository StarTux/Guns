package com.winthier.guns;

import com.winthier.custom.inventory.CustomInventory;
import com.winthier.custom.util.Dirty;
import com.winthier.custom.util.Msg;
import lombok.Getter;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

@Getter
public final class BlunderbussInventory implements CustomInventory {
    private final GunsPlugin plugin;
    private final Player player;
    private final ItemStack itemStack;
    private final Inventory inventory;

    public BlunderbussInventory(GunsPlugin plugin, Player player, ItemStack itemStack) {
        this.plugin = plugin;
        this.player = player;
        this.itemStack = itemStack;
        inventory = plugin.getServer().createInventory(player, 9, "Blunderbuss");
    }

    void dumpItems() {
        if (!Dirty.TagWrapper.hasItemConfig(itemStack)) return;
        Dirty.TagWrapper config = Dirty.TagWrapper.getItemConfigOf(itemStack);
        int ammo = config.getInt(BlunderbussItem.KEY_AMMO);
        if (ammo <= 0) return;
        BlunderbussItem.AmmoType type = BlunderbussItem.AmmoType.valueOf(config.getString(BlunderbussItem.KEY_TYPE));
        Material mat = type.material;
        config.setInt(BlunderbussItem.KEY_AMMO, 0);
        while (ammo > 0) {
            int amount = Math.min(ammo, mat.getMaxStackSize());
            ammo -= amount;
            for (ItemStack drop: inventory.addItem(new ItemStack(mat, amount)).values()) {
                player.getWorld().dropItem(player.getEyeLocation(), drop).setPickupDelay(0);
            }
        }
    }

    void loadItems() {
        BlunderbussItem.AmmoType type = null;
        int amount = 0;
        for (int i = 0; i < inventory.getSize(); i += 1) {
            ItemStack item = inventory.getItem(i);
            if (item == null || item.getAmount() <= 0) continue;
            if (type == null) {
                TYPES:
                for (BlunderbussItem.AmmoType at: BlunderbussItem.AmmoType.values()) {
                    if (at.getItemStack(1).isSimilar(item)) {
                        type = at;
                        amount = item.getAmount();
                        inventory.setItem(i, null);
                        break TYPES;
                    }
                }
            } else {
                if (type.getItemStack(1).isSimilar(item)) {
                    amount += item.getAmount();
                    inventory.setItem(i, null);
                }
            }
        }
        if (type != null && amount > 0) {
            Dirty.TagWrapper config = Dirty.TagWrapper.getItemConfigOf(itemStack);
            config.setInt(BlunderbussItem.KEY_AMMO, amount);
            config.setString(BlunderbussItem.KEY_TYPE, type.name());
            Msg.sendActionBar(player, "&a%d bullets stored", amount);
            new BukkitRunnable() {
                private int i = 0;
                @Override public void run() {
                    if (!player.isValid()) {
                        cancel();
                        return;
                    }
                    switch (i) {
                    case 0:
                        player.playSound(player.getEyeLocation(), Sound.BLOCK_DISPENSER_DISPENSE, SoundCategory.MASTER, 1.0f, 0.5f);
                        break;
                    case 1:
                        player.playSound(player.getEyeLocation(), Sound.BLOCK_DISPENSER_DISPENSE, SoundCategory.MASTER, 1.0f, 0.6f);
                    default: // fallthrough
                        cancel();
                    }
                    i += 1;
                }
            }.runTaskTimer(plugin, 0, 2);
        } else {
            Msg.sendActionBar(player, "&cNo bullets stored", amount);
            player.getWorld().playSound(player.getEyeLocation(), Sound.BLOCK_DISPENSER_FAIL, SoundCategory.MASTER, 1.0f, 0.65f);
        }
        // Drop all remaining items
        for (ItemStack item: inventory) {
            if (item != null && item.getAmount() > 0) {
                player.getWorld().dropItem(player.getEyeLocation(), item).setPickupDelay(0);
            }
        }
    }

    @Override
    public void onInventoryClose(InventoryCloseEvent event) {
        loadItems();
    }

    @Override
    public void onInventoryClick(InventoryClickEvent event) {
        ItemStack item = event.getCurrentItem();
        if (item != null && item.getType() != Material.AIR) {
            boolean isAmmo = false;
            TYPES:
            for (BlunderbussItem.AmmoType at: BlunderbussItem.AmmoType.values()) {
                if (at.getItemStack(1).isSimilar(item)) {
                    isAmmo = true;
                    break TYPES;
                }
            }
            if (!isAmmo) return;
        }
        event.setCancelled(false);
    }

    @Override
    public void onInventoryDrag(InventoryDragEvent event) {
        event.setCancelled(false);
    }
}
