package com.winthier.guns;

import com.winthier.custom.CustomPlugin;
import com.winthier.custom.item.CustomItem;
import com.winthier.custom.item.ItemContext;
import com.winthier.custom.item.ItemDescription;
import com.winthier.custom.item.UncraftableItem;
import com.winthier.custom.util.Dirty;
import com.winthier.custom.util.Msg;
import com.winthier.generic_events.GenericEventsPlugin;
import com.winthier.generic_events.ItemNameEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;
import lombok.Getter;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Egg;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.material.MaterialData;
import org.bukkit.util.BlockIterator;
import org.bukkit.util.Vector;

@Getter
public final class BlunderbussItem implements CustomItem, UncraftableItem {
    private final GunsPlugin plugin;
    public static final String CUSTOM_ID = "guns:blunderbuss";
    private final String customId = CUSTOM_ID;
    private final ItemStack itemStack;
    private final ItemDescription itemDescription;
    static final String KEY_AMMO = "ammo";
    static final String KEY_TYPE = "type";
    static final String KEY_LAST_USE = "last_use";

    public BlunderbussItem(GunsPlugin plugin) {
        this.plugin = plugin;
        ItemStack item;
        item = Dirty.setSkullOwner(new ItemStack(Material.SKULL_ITEM, 1, (short)3),
                                   plugin.getConfig().getString("blunderbuss.head.Name"),
                                   UUID.fromString(plugin.getConfig().getString("blunderbuss.head.Id")),
                                   plugin.getConfig().getString("blunderbuss.head.Texture"));
        ItemDescription description = ItemDescription.of(plugin.getConfig().getConfigurationSection("blunderbuss.description"));
        description.apply(item);
        this.itemStack = item;
        this.itemDescription = description;
    }

    @Override
    public ItemStack spawnItemStack(int amount) {
        ItemStack item = itemStack.clone();
        item.setAmount(amount);
        return item;
    }

    @EventHandler
    public void onItemName(ItemNameEvent event) {
        event.setItemName(itemDescription.getDisplayName());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerInteract(PlayerInteractEvent event, ItemContext context) {
        switch (event.getAction()) {
        case PHYSICAL: return;
        case LEFT_CLICK_BLOCK: return;
        case LEFT_CLICK_AIR:
            event.setCancelled(true);
            if (context.getItemStack().getAmount() != 1) {
                Msg.sendActionBar(context.getPlayer(), "&cUnstack the blunderbuss first!");
            } else {
                shoot(context.getPlayer(), context.getItemStack());
            }
            break;
        case RIGHT_CLICK_BLOCK:
        case RIGHT_CLICK_AIR:
            event.setCancelled(true);
            if (context.getItemStack().getAmount() != 1) {
                Msg.sendActionBar(context.getPlayer(), "&cUnstack the blunderbuss first!");
            } else {
                reload(context.getPlayer(), context.getItemStack());
            }
            break;
        default: return;
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockPlace(BlockPlaceEvent event, ItemContext context) {
        event.setCancelled(true);
    }

    boolean shoot(Player player, ItemStack item) {
        if (!Dirty.TagWrapper.hasItemConfig(item)) return playOutOfAmmoEffect(player);
        Dirty.TagWrapper config = Dirty.TagWrapper.getItemConfigOf(item);
        long lastUse = config.getLong(KEY_LAST_USE);
        long now = System.currentTimeMillis();
        if (lastUse + 500 > now) return false;
        config.setLong(KEY_LAST_USE, now);
        int ammo = config.getInt(KEY_AMMO);
        if (ammo <= 0) return playOutOfAmmoEffect(player);
        AmmoType type = AmmoType.valueOf(config.getString(KEY_TYPE));
        if (player.getGameMode() != GameMode.CREATIVE) {
            config.setInt(KEY_AMMO, ammo - 1);
        }
        switch (type) {
        case ARROW:
        case SNOW_BALL:
        case EGG:
        case ENDER_PEARL:
            return shootProjectile(player, type);
        case IRON_NUGGET:
        case GOLD_NUGGET:
            return shootRay(player, type);
        default: break;
        }
        return true;
    }

    boolean shootRay(Player player, AmmoType type) {
        player.getWorld().playSound(player.getEyeLocation(), Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 0.3f, 1.7f);
        Vector velo = player.getLocation().getDirection();
        player.getWorld().spawnParticle(Particle.FLAME, player.getEyeLocation().add(velo.normalize().multiply(0.5)), 4, 0.2, 0.2, 0.2, 0.0);
        player.getWorld().spawnParticle(Particle.SMOKE_NORMAL, player.getEyeLocation().add(velo.normalize()), 8, 0.2, 0.2, 0.2, 0.0);
        final int radius = 80;
        final double radf = (double)radius;
        HashMap<Block, LivingEntity> entities = new HashMap<>();
        for (Entity e: player.getNearbyEntities(radf, radf, radf)) {
            if (!e.equals(player) && e instanceof LivingEntity) {
                double sy = e.getHeight();
                double sx = e.getWidth() * 0.5;
                Block a = e.getLocation().add(-sx, 0, -sx).getBlock();
                Block b = e.getLocation().add(sx, sy, sx).getBlock();
                for (int y = a.getY(); y <= b.getY(); y += 1) {
                    for (int z = a.getZ(); z <= b.getZ(); z += 1) {
                        for (int x = a.getX(); x <= b.getX(); x += 1) {
                            entities.put(a.getWorld().getBlockAt(x, y, z), (LivingEntity)e);
                        }
                    }
                }
            }
        }
        BlockIterator iter = new BlockIterator(player, radius);
        int count = 0;
        Block oldBlock = null;
        while (iter.hasNext() && count <= radius) {
            count += 1;
            Block newBlock = iter.next();
            if (newBlock.getY() < 0 || newBlock.getY() > 255) break;
            LivingEntity e = entities.get(newBlock);
            if (e != null && GenericEventsPlugin.getInstance().playerCanDamageEntity(player, e)) {
                double damageAmount;
                double knockbackAmount;
                if (type == AmmoType.GOLD_NUGGET) {
                    damageAmount = 8.0;
                    knockbackAmount = 4.0;
                } else {
                    damageAmount = 4.0;
                    knockbackAmount = 2.0;
                }
                e.setVelocity(e.getVelocity().add(velo.normalize().multiply(knockbackAmount)));
                e.damage(damageAmount);
                e.getWorld().playSound(e.getEyeLocation(), Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 0.3f, 1.2f);
                return true;
            } else if (oldBlock != null && !newBlock.isLiquid() && newBlock.getType() != Material.AIR && newBlock.getType().isSolid()) {
                if (GenericEventsPlugin.getInstance().playerCanGrief(player, newBlock)) {
                    boolean unbreakable;
                    switch (newBlock.getType()) {
                    case ENDER_PORTAL_FRAME:
                    case OBSIDIAN:
                        unbreakable = type != AmmoType.GOLD_NUGGET;
                        break;
                    case BEDROCK:
                    case BARRIER:
                    case MOB_SPAWNER:
                        unbreakable = true;
                        break;
                    default:
                        unbreakable = false;
                    }
                    if (!unbreakable) {
                        EntityChangeBlockEvent entityChangeBlockEvent = new EntityChangeBlockEvent(player, newBlock, Material.AIR, (byte)0);
                        plugin.getServer().getPluginManager().callEvent(entityChangeBlockEvent);
                        if (entityChangeBlockEvent.isCancelled()) unbreakable = true;
                    }
                    if (!unbreakable) {
                        ArrayList<Block> blockList = new ArrayList<>();
                        blockList.add(newBlock);
                        BlockExplodeEvent blockExplodeEvent = new BlockExplodeEvent(newBlock, blockList, 0.1f);
                        plugin.getServer().getPluginManager().callEvent(blockExplodeEvent);
                        if (blockExplodeEvent.isCancelled() || blockList.isEmpty()) unbreakable = true;
                    }
                    if (unbreakable) {
                        oldBlock.getWorld().spawnParticle(Particle.BLOCK_CRACK, oldBlock.getLocation().add(0.5, 0.5, 0.5), 8, newBlock.getState().getData());
                    } else {
                        newBlock.getWorld().spawnParticle(Particle.BLOCK_CRACK, newBlock.getLocation().add(0.5, 0.5, 0.5), 16, newBlock.getState().getData());
                        if (type == AmmoType.GOLD_NUGGET) {
                            MaterialData materialData = silkTouch(newBlock);
                            if (materialData == null) {
                                newBlock.breakNaturally();
                            } else {
                                newBlock.setType(Material.AIR);
                                newBlock.getWorld().dropItemNaturally(newBlock.getLocation().add(0.5, 0.5, 0.5), materialData.toItemStack(1));
                            }
                        } else {
                            newBlock.breakNaturally();
                        }
                    }
                    newBlock.getWorld().playSound(newBlock.getLocation().add(0.5, 0.5, 0.5), Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 0.3f, 1.2f);
                    oldBlock.getWorld().spawnParticle(Particle.SMOKE_NORMAL, oldBlock.getLocation().add(0.5, 0.5, 0.5), 8, 0.2, 0.2, 0.2, 0.0);
                }
                return true;
            } else if (oldBlock != null) {
                oldBlock.getWorld().spawnParticle(Particle.SPELL, oldBlock.getLocation().add(0.5, 0.5, 0.5), 1, 0, 0, 0, 0);
            }
            oldBlock = newBlock;
        }
        return false;
    }

    boolean shootProjectile(Player player, AmmoType type) {
        player.launchProjectile(type.projectile, player.getLocation().getDirection().normalize().multiply(type.velocity));
        Vector velo = player.getLocation().getDirection();
        player.getWorld().playSound(player.getEyeLocation().add(velo.normalize()), Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 0.3f, 2.0f);
        player.getWorld().spawnParticle(Particle.SMOKE_NORMAL, player.getEyeLocation().add(velo.normalize()), 8, 0.2, 0.2, 0.2, 0.0);
        return true;
    }

    MaterialData silkTouch(Block block) {
        Material mat = block.getType();
        switch (mat) {
        case COAL_ORE:
        case DIAMOND_ORE:
        case EMERALD_ORE:
        case REDSTONE_ORE:
        case GOLD_ORE:
        case IRON_ORE:
        case LAPIS_ORE:
        case QUARTZ_ORE:
        case GRASS:
        case MYCEL:
        case WEB:
        case GLASS:
        case THIN_GLASS:
        case ICE:
        case PACKED_ICE:
        case VINE:
        case SNOW:
        case SEA_LANTERN:
        case HUGE_MUSHROOM_1:
        case HUGE_MUSHROOM_2:
        case ENDER_CHEST:
        case BOOKSHELF:
        case ENDER_PORTAL_FRAME:
            // Copy without data
            return new MaterialData(mat);
        case STONE:
            // Copy on condition
            byte data = block.getData();
            if (data == 0) return new MaterialData(mat);
            break;
        case STAINED_GLASS:
        case STAINED_GLASS_PANE:
        case LONG_GRASS:
            // Copy with data
            data = block.getData();
            return new MaterialData(mat, data);
        case LEAVES:
        case LEAVES_2:
            // Copy with modified leaf data
            int iData = (int)block.getData() & ~12;
            return new MaterialData(mat, (byte)iData);
        case DIRT:
            // Copy on condition
            data = block.getData();
            if (data != 0) {
                return new MaterialData(mat, data);
            }
            break;
        case GLOWING_REDSTONE_ORE:
            // Copy modified material
            return new MaterialData(Material.REDSTONE_ORE);
        default: return null;
        }
        return null;
    }

    boolean reload(Player player, ItemStack item) {
        BlunderbussInventory inv = new BlunderbussInventory(plugin, player, item);
        inv.dumpItems();
        CustomPlugin.getInstance().getInventoryManager().openInventory(player, inv);
        return true;
    }

    boolean playOutOfAmmoEffect(Player player) {
        player.playSound(player.getEyeLocation(), Sound.BLOCK_DISPENSER_FAIL, SoundCategory.PLAYERS, 0.5f, 1.7f);
        return true;
    }

    enum AmmoType {
        ARROW(Material.ARROW, Arrow.class, 5),
        SNOW_BALL(Material.SNOW_BALL, Snowball.class, 5),
        EGG(Material.EGG, Egg.class, 5),
        IRON_NUGGET(Material.IRON_NUGGET, null, 0),
        GOLD_NUGGET(Material.GOLD_NUGGET, null, 0),
        ENDER_PEARL(Material.ENDER_PEARL, EnderPearl.class, 2.5);
        public final Material material;
        public final Class<? extends Projectile> projectile;
        public final double velocity;
        AmmoType(Material material, Class<? extends Projectile> projectile, double velocity) {
            this.material = material;
            this.projectile = projectile;
            this.velocity = velocity;
        }
        ItemStack getItemStack(int amount) {
            return new ItemStack(material, amount);
        }
    }
}
