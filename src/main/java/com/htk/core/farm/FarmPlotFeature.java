package com.htk.core.farm;

import com.htk.core.ServerCore;
import com.htk.core.common.Feature;
import com.htk.core.common.Glyphs;
import com.htk.core.common.Items;
import com.htk.core.common.Text;
import com.htk.core.land.Land;
import com.htk.core.land.LandFeature;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Farmland;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/** 구매 농지: 집 부지에서만 설치되며 수분 유지와 밟힘을 관리한다. */
public final class FarmPlotFeature implements Feature, Listener {
    private final ServerCore plugin;
    private final NamespacedKey itemKey;
    private final NamespacedKey plotsKey;
    private final NamespacedKey dryPlotsKey;
    private final Set<org.bukkit.Location> plots = new HashSet<>();
    private BukkitTask hydrationTask;

    public FarmPlotFeature(ServerCore plugin) {
        this.plugin = plugin;
        itemKey = new NamespacedKey(plugin, "farm_plot_item");
        plotsKey = new NamespacedKey(plugin, "farm_plot_blocks");
        dryPlotsKey = new NamespacedKey(plugin, "farm_plot_dry_blocks");
    }

    @Override public void enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        for (var world : Bukkit.getWorlds()) for (var chunk : world.getLoadedChunks()) loadPlots(chunk);
        hydrationTask = Bukkit.getScheduler().runTaskTimer(plugin, this::hydratePlots, 20L, 20L);
    }

    @Override public void reload() { }

    @Override public void disable() {
        if (hydrationTask != null) hydrationTask.cancel();
        plots.clear();
    }

    public ItemStack createItem(int amount) {
        ItemStack item = Items.of(Material.MOSS_BLOCK, amount,
                "<#91d46b><bold>비옥한 농지",
                java.util.List.of("<gray>내 집 부지에 설치", "<gray>물 공급 유지 · 일반 밭 속도 · 밟힘 방지"),
                plugin.resourcePackEnabled() ? Glyphs.model("farm_plot") : null);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(itemKey, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    public boolean isPlotItem(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(itemKey, PersistentDataType.BYTE);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void place(BlockPlaceEvent event) {
        if (!isPlotItem(event.getItemInHand())) return;
        Block block = event.getBlockPlaced();
        Player player = event.getPlayer();
        if (!mayPlace(player, block)) {
            event.setCancelled(true);
            player.sendActionBar(Text.mm("<red>비옥한 농지는 본인이 사용할 수 있는 집 부지에만 설치할 수 있어요."));
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (event.isCancelled() || block.getType() != Material.MOSS_BLOCK) return;
            block.setType(Material.FARMLAND, false);
            mark(block, true);
            player.sendActionBar(Text.mm("<#91d46b>비옥한 농지 설치 · 물을 공급하면 수분을 유지해요"));
        });
    }

    private boolean mayPlace(Player player, Block block) {
        LandFeature landFeature = plugin.feature(LandFeature.class);
        if (landFeature == null) return true;
        Land land = landFeature.landAt(block.getLocation());
        return land != null && land.world().equals(block.getWorld().getName())
                && land.trusts(player.getUniqueId())
                && land.has(block.getX() >> 4, block.getZ() >> 4);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void protectFromTrampling(EntityChangeBlockEvent event) {
        Block block = event.getBlock();
        if (event.getTo() == Material.DIRT && marked(block)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void grow(BlockGrowEvent event) {
        Block soil = event.getBlock().getRelative(BlockFace.DOWN);
        if (soil.getType() != Material.FARMLAND) return;
        if (marked(soil)) {
            if (!dryLocked(soil)) keepMoistWhileGrowing(soil);
            return;
        }
        // Ordinary hoe-tilled farmland grows at half its current rate.
        if (ThreadLocalRandom.current().nextBoolean()) event.setCancelled(true);
    }

    private void keepMoistWhileGrowing(Block soil) {
        if (!isAgeableCrop(soil.getRelative(BlockFace.UP))) return;
        BlockData data = soil.getBlockData();
        if (data instanceof Farmland farmland && farmland.getMoisture() > 0
                && farmland.getMoisture() < farmland.getMaximumMoisture()) {
            farmland.setMoisture(farmland.getMaximumMoisture());
            soil.setBlockData(farmland, false);
        }
    }

    private boolean isAgeableCrop(Block block) {
        return block.getBlockData() instanceof Ageable;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void resetMoistureAfterMatureHarvest(BlockBreakEvent event) {
        Block crop = event.getBlock();
        if (!(crop.getBlockData() instanceof Ageable ageable)
                || ageable.getAge() < ageable.getMaximumAge()) return;
        Block soil = crop.getRelative(BlockFace.DOWN);
        if (!marked(soil)) return;
        BlockData data = soil.getBlockData();
        if (data instanceof Farmland farmland) {
            farmland.setMoisture(0);
            soil.setBlockData(farmland, false);
            setDryLocked(soil, true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void waterPlaced(BlockPlaceEvent event) {
        Block water = event.getBlockPlaced();
        if (water.getType() != Material.WATER) return;
        for (org.bukkit.Location location : Set.copyOf(plots)) {
            if (location.getWorld() == null || !location.getWorld().equals(water.getWorld())) continue;
            Block soil = water.getWorld().getBlockAt(location);
            if (!dryLocked(soil) || !waterCanHydrate(water, soil)) continue;
            setDryLocked(soil, false);
            BlockData data = soil.getBlockData();
            if (data instanceof Farmland farmland) {
                farmland.setMoisture(farmland.getMaximumMoisture());
                soil.setBlockData(farmland, false);
            }
        }
    }

    private boolean waterCanHydrate(Block water, Block soil) {
        int dx = water.getX() - soil.getX();
        int dz = water.getZ() - soil.getZ();
        int dy = water.getY() - soil.getY();
        return dx * dx + dz * dz <= 16 && dy >= 0 && dy <= 1;
    }

    private void hydratePlots() {
        Iterator<org.bukkit.Location> iterator = plots.iterator();
        while (iterator.hasNext()) {
            org.bukkit.Location location = iterator.next();
            var world = location.getWorld();
            if (world == null || !world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) continue;
            Block block = world.getBlockAt(location);
            if (block.getType() != Material.FARMLAND) {
                iterator.remove();
                setDryLocked(block, false);
                mark(block, false);
                continue;
            }
            if (dryLocked(block)) {
                BlockData data = block.getBlockData();
                if (data instanceof Farmland farmland && farmland.getMoisture() != 0) {
                    farmland.setMoisture(0);
                    block.setBlockData(farmland, false);
                }
            } else keepMoistWhileGrowing(block);
        }
    }

    @EventHandler public void chunkLoad(ChunkLoadEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (event.getChunk().isLoaded()) loadPlots(event.getChunk());
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void chunkUnload(ChunkUnloadEvent event) {
        var chunk = event.getChunk();
        plots.removeIf(location -> location.getWorld() != null
                && location.getWorld().getUID().equals(chunk.getWorld().getUID())
                && (location.getBlockX() >> 4) == chunk.getX()
                && (location.getBlockZ() >> 4) == chunk.getZ());
    }

    private void loadPlots(org.bukkit.Chunk chunk) {
        int[] values = chunk.getPersistentDataContainer().get(plotsKey, PersistentDataType.INTEGER_ARRAY);
        if (values == null) return;
        for (int value : values) {
            int x = (chunk.getX() << 4) | (value & 15);
            int z = (chunk.getZ() << 4) | ((value >>> 4) & 15);
            int y = (value >> 8) - 4096;
            Block block = chunk.getWorld().getBlockAt(x, y, z);
            if (block.getType() == Material.FARMLAND) plots.add(block.getLocation());
            else mark(block, false);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void protectFromPiston(BlockPistonExtendEvent event) {
        if (event.getBlocks().stream().anyMatch(this::marked)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void protectFromPiston(BlockPistonRetractEvent event) {
        if (event.getBlocks().stream().anyMatch(this::marked)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void stopVanillaDrop(BlockBreakEvent event) {
        if (marked(event.getBlock())) event.setDropItems(false);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void refund(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!marked(block)) return;
        mark(block, false);
        block.getWorld().dropItemNaturally(block.getLocation().add(.5, .2, .5), createItem(1));
    }

    private static int packed(Block block) {
        return (block.getX() & 15) | ((block.getZ() & 15) << 4) | ((block.getY() + 4096) << 8);
    }

    private boolean marked(Block block) {
        int[] values = block.getChunk().getPersistentDataContainer().get(plotsKey, PersistentDataType.INTEGER_ARRAY);
        return values != null && Arrays.stream(values).anyMatch(value -> value == packed(block));
    }

    private boolean dryLocked(Block block) {
        int[] values = block.getChunk().getPersistentDataContainer().get(dryPlotsKey, PersistentDataType.INTEGER_ARRAY);
        return values != null && Arrays.stream(values).anyMatch(value -> value == packed(block));
    }

    private void setDryLocked(Block block, boolean lock) {
        var data = block.getChunk().getPersistentDataContainer();
        int[] values = data.get(dryPlotsKey, PersistentDataType.INTEGER_ARRAY);
        if (values == null) values = new int[0];
        int location = packed(block);
        boolean exists = Arrays.stream(values).anyMatch(value -> value == location);
        if (exists == lock) return;
        int[] updated = lock ? Arrays.copyOf(values, values.length + 1)
                : Arrays.stream(values).filter(value -> value != location).toArray();
        if (lock) updated[updated.length - 1] = location;
        if (updated.length == 0) data.remove(dryPlotsKey);
        else data.set(dryPlotsKey, PersistentDataType.INTEGER_ARRAY, updated);
    }

    private void mark(Block block, boolean add) {
        var data = block.getChunk().getPersistentDataContainer();
        int[] values = data.get(plotsKey, PersistentDataType.INTEGER_ARRAY);
        if (values == null) values = new int[0];
        int location = packed(block);
        boolean exists = Arrays.stream(values).anyMatch(value -> value == location);
        if (exists == add) return;
        int[] updated = add ? Arrays.copyOf(values, values.length + 1)
                : Arrays.stream(values).filter(value -> value != location).toArray();
        if (add) updated[updated.length - 1] = location;
        if (updated.length == 0) data.remove(plotsKey);
        else data.set(plotsKey, PersistentDataType.INTEGER_ARRAY, updated);
        if (add) plots.add(block.getLocation());
        else {
            plots.remove(block.getLocation());
            setDryLocked(block, false);
        }
    }
}
