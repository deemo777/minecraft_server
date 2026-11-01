package com.htk.core.farm;

import com.htk.core.ServerCore;
import com.htk.core.common.Feature;
import com.htk.core.common.Glyphs;
import com.htk.core.common.Items;
import com.htk.core.common.Text;
import com.htk.core.land.Land;
import com.htk.core.land.LandFeature;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.type.Farmland;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/** Custom soil planter: persistent farmland support plus a separate, collision-free 3D shell. */
public final class FarmPlotFeature implements Feature, Listener {
    private final ServerCore plugin;
    // Keep legacy chunk/item keys so existing purchased plots and items remain usable.
    private final NamespacedKey itemKey = new NamespacedKey("servercore","farm_plot_item");
    private final NamespacedKey soilItemKey = new NamespacedKey("servercore","soil_block_item");
    private final NamespacedKey plotsKey = new NamespacedKey("servercore","farm_plot_blocks");
    private final NamespacedKey dryPlotsKey = new NamespacedKey("servercore","farm_plot_dry_blocks");
    private final NamespacedKey displayKey = new NamespacedKey("servercore","soil_block_display");
    private final Set<Location> plots = new HashSet<>();
    private final Map<Location,ItemDisplay> displays = new HashMap<>();
    private final Map<Location,Boolean> wetModels = new HashMap<>();
    private BukkitTask hydrationTask;

    public FarmPlotFeature(ServerCore plugin) { this.plugin=plugin; }
    @Override public void enable() {
        Bukkit.getPluginManager().registerEvents(this,plugin);
        for(var world:Bukkit.getWorlds())for(var chunk:world.getLoadedChunks())loadPlots(chunk);
        hydrationTask=Bukkit.getScheduler().runTaskTimer(plugin,this::hydratePlots,20L,20L);
    }
    @Override public void reload() { hydratePlots(); }
    @Override public void disable() {
        if(hydrationTask!=null)hydrationTask.cancel();
        displays.values().forEach(ItemDisplay::remove);
        displays.clear();wetModels.clear();plots.clear();
    }
    public ItemStack createItem(int amount) {
        ItemStack item=Items.of(Material.PAPER,amount,"<#cfaa75><bold>토양 블록",List.of(),
                plugin.resourcePackEnabled()?Glyphs.model("soil_block"):null);
        ItemMeta meta=item.getItemMeta();
        meta.getPersistentDataContainer().set(soilItemKey,PersistentDataType.BYTE,(byte)1);
        item.setItemMeta(meta);return item;
    }
    public boolean isPlotItem(ItemStack item) {
        if(item==null || !item.hasItemMeta())return false;
        var data=item.getItemMeta().getPersistentDataContainer();
        return data.has(soilItemKey,PersistentDataType.BYTE) || data.has(itemKey,PersistentDataType.BYTE);
    }
    public boolean refreshItem(ItemStack item) {
        if(!isPlotItem(item))return false;
        ItemMeta meta=item.getItemMeta();
        item.setType(Material.PAPER);
        meta.getPersistentDataContainer().remove(itemKey);
        meta.getPersistentDataContainer().set(soilItemKey,PersistentDataType.BYTE,(byte)1);
        meta.displayName(Text.mm("<#cfaa75><bold>토양 블록").decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC,false));
        meta.lore(null);
        meta.setItemModel(plugin.resourcePackEnabled()?Glyphs.model("soil_block"):null);
        item.setItemMeta(meta);return true;
    }
    private boolean mayPlace(Player player,Block block) {
        LandFeature feature=plugin.feature(LandFeature.class);
        if(feature==null)return true;
        Land land=feature.landAt(block.getLocation());
        return land!=null && feature.canBuildAt(player,land,block.getLocation());
    }
    /** Paper-backed custom item: fire the normal placement event before consuming anything. */
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void placeSoil(PlayerInteractEvent event) {
        if(event.getAction()!=Action.RIGHT_CLICK_BLOCK || event.getClickedBlock()==null || !isPlotItem(event.getItem()))return;
        if(event.useItemInHand()==Event.Result.DENY)return;
        event.setCancelled(true);
        Player player=event.getPlayer();
        if(player.getGameMode()==GameMode.ADVENTURE || player.getGameMode()==GameMode.SPECTATOR)return;
        Block clicked=event.getClickedBlock();
        Block target=clicked.isReplaceable()?clicked:clicked.getRelative(event.getBlockFace());
        if(!target.isReplaceable() || target.isLiquid() || marked(target)
                || target.getY()<target.getWorld().getMinHeight() || target.getY()>=target.getWorld().getMaxHeight())return;
        if(!mayPlace(player,target)) {
            player.sendActionBar(Text.mm("<red>토양 블록은 본인이 사용할 수 있는 집 부지에만 설치할 수 있어요."));return;
        }
        var previous=target.getState();
        target.setType(Material.FARMLAND,false);
        var placed=new BlockPlaceEvent(target,previous,clicked,event.getItem().clone(),player,true,event.getHand());
        Bukkit.getPluginManager().callEvent(placed);
        if(placed.isCancelled() || !placed.canBuild()) {previous.update(true,false);return;}
        if(target.getType()!=Material.FARMLAND) {previous.update(true,false);return;}
        mark(target,true);render(target);
        if(player.getGameMode()!=GameMode.CREATIVE) {
            ItemStack held=event.getHand()==EquipmentSlot.OFF_HAND?player.getInventory().getItemInOffHand():player.getInventory().getItemInMainHand();
            held.setAmount(held.getAmount()-1);
            if(event.getHand()==EquipmentSlot.OFF_HAND)player.getInventory().setItemInOffHand(held);
            else player.getInventory().setItemInMainHand(held);
        }
        player.playSound(target.getLocation(),Sound.BLOCK_WOOD_PLACE,1,1);
    }
    /** Legacy block-backed items still work if another placement path emits a native event. */
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void legacyPlace(BlockPlaceEvent event) {
        if(!isPlotItem(event.getItemInHand()) || event.getBlock().getType()!=Material.MOSS_BLOCK)return;
        if(!mayPlace(event.getPlayer(),event.getBlock())) {event.setCancelled(true);return;}
        Bukkit.getScheduler().runTask(plugin,()->{
            if(event.isCancelled() || event.getBlock().getType()!=Material.MOSS_BLOCK)return;
            event.getBlock().setType(Material.FARMLAND,false);mark(event.getBlock(),true);render(event.getBlock());
        });
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void preventDryDecay(BlockFadeEvent event) {
        if(marked(event.getBlock()) && event.getNewState().getType()==Material.DIRT)event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void protectFromTrampling(EntityChangeBlockEvent event) {
        if(event.getTo()==Material.DIRT && marked(event.getBlock()))event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void moisture(MoistureChangeEvent event) {
        if(marked(event.getBlock()) && dryLocked(event.getBlock())
                && event.getNewState().getBlockData() instanceof Farmland farm && farm.getMoisture()>0)event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)
    public void grow(BlockGrowEvent event) {
        Block soil=event.getBlock().getRelative(BlockFace.DOWN);
        if(soil.getType()!=Material.FARMLAND)return;
        if(marked(soil)) {if(!dryLocked(soil))keepMoistWhileGrowing(soil);return;}
        if(ThreadLocalRandom.current().nextBoolean())event.setCancelled(true);
    }
    private void keepMoistWhileGrowing(Block soil) {
        if(!(soil.getRelative(BlockFace.UP).getBlockData() instanceof Ageable))return;
        if(soil.getBlockData() instanceof Farmland farm && farm.getMoisture()>0 && farm.getMoisture()<farm.getMaximumMoisture()) {
            farm.setMoisture(farm.getMaximumMoisture());soil.setBlockData(farm,false);
        }
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void resetMoistureAfterMatureHarvest(BlockBreakEvent event) {
        Block crop=event.getBlock();
        if(!(crop.getBlockData() instanceof Ageable age) || age.getAge()<age.getMaximumAge())return;
        Block soil=crop.getRelative(BlockFace.DOWN);
        if(marked(soil) && soil.getBlockData() instanceof Farmland farm) {
            farm.setMoisture(0);soil.setBlockData(farm,false);setDryLocked(soil,true);render(soil);
        }
    }
    /** Called only after the watering can's per-cell permission checks succeed. */
    public void watered(Block soil) {
        if(!marked(soil))return;
        setDryLocked(soil,false);
        if(soil.getBlockData() instanceof Farmland farm) {
            farm.setMoisture(farm.getMaximumMoisture());soil.setBlockData(farm,false);
        }
        render(soil);
    }
    private void waterAround(Block water) {
        if(water.getType()!=Material.WATER)return;
        for(Location location:Set.copyOf(plots)) {
            if(!water.getWorld().equals(location.getWorld()))continue;
            int dx=water.getX()-location.getBlockX(),dz=water.getZ()-location.getBlockZ(),dy=water.getY()-location.getBlockY();
            if(Math.abs(dx)<=4 && Math.abs(dz)<=4 && dy>=0 && dy<=1)watered(water.getWorld().getBlockAt(location));
        }
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void waterPlaced(BlockPlaceEvent event) {
        if(event.getBlock().getType()==Material.WATER)Bukkit.getScheduler().runTask(plugin,()->{if(!event.isCancelled())waterAround(event.getBlock());});
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void waterBucket(PlayerBucketEmptyEvent event) {
        Block target=event.getBlock();
        Bukkit.getScheduler().runTask(plugin,()->{if(!event.isCancelled())waterAround(target);});
    }
    private void hydratePlots() {
        for(Location location:Set.copyOf(plots)) {
            World world=location.getWorld();
            if(world==null || !world.isChunkLoaded(location.getBlockX()>>4,location.getBlockZ()>>4))continue;
            Block block=world.getBlockAt(location);
            // Recover registered legacy plots that already decayed; never replace unrelated construction.
            if(block.getType()==Material.DIRT)block.setType(Material.FARMLAND,false);
            if(block.getType()!=Material.FARMLAND) {mark(block,false);removeDisplay(location);continue;}
            if(dryLocked(block) && block.getBlockData() instanceof Farmland farm && farm.getMoisture()!=0) {
                farm.setMoisture(0);block.setBlockData(farm,false);
            } else if(!dryLocked(block))keepMoistWhileGrowing(block);
            render(block);
        }
    }
    private void render(Block block) {
        if(plugin==null)return;
        if(!plugin.resourcePackEnabled()) {removeDisplay(block.getLocation());return;}
        Location location=block.getLocation();
        boolean wet=block.getBlockData() instanceof Farmland farm && farm.getMoisture()>0;
        ItemDisplay display=displays.get(location);
        if(display!=null && display.isValid() && Objects.equals(wetModels.get(location),wet))return;
        ItemStack model=new ItemStack(Material.PAPER);
        ItemMeta meta=model.getItemMeta();meta.setItemModel(Glyphs.model(wet?"soil_block_wet":"soil_block"));model.setItemMeta(meta);
        if(display==null || !display.isValid()) {
            display=block.getWorld().spawn(location.clone().add(.5,.5,.5),ItemDisplay.class,d->{
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                d.setPersistent(false);d.setInvulnerable(true);d.setDisplayWidth(1);d.setDisplayHeight(1.25f);
                d.getPersistentDataContainer().set(displayKey,PersistentDataType.BYTE,(byte)1);
            });
            displays.put(location,display);
        }
        display.setItemStack(model);wetModels.put(location,wet);
    }
    private void removeDisplay(Location location) {
        ItemDisplay display=displays.remove(location);wetModels.remove(location);
        if(display!=null)display.remove();
    }
    @EventHandler public void chunkLoad(ChunkLoadEvent event) {
        Bukkit.getScheduler().runTask(plugin,()->{if(event.getChunk().isLoaded())loadPlots(event.getChunk());});
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void chunkUnload(ChunkUnloadEvent event) {
        var chunk=event.getChunk();
        for(Location location:Set.copyOf(plots))if(location.getWorld().equals(chunk.getWorld())
                && (location.getBlockX()>>4)==chunk.getX() && (location.getBlockZ()>>4)==chunk.getZ()) {
            plots.remove(location);removeDisplay(location);
        }
    }
    private void loadPlots(org.bukkit.Chunk chunk) {
        for(var entity:chunk.getEntities())if(entity instanceof ItemDisplay && entity.getPersistentDataContainer().has(displayKey,PersistentDataType.BYTE))entity.remove();
        int[] values=chunk.getPersistentDataContainer().get(plotsKey,PersistentDataType.INTEGER_ARRAY);
        if(values==null)return;
        for(int value:values) {
            Block block=chunk.getWorld().getBlockAt((chunk.getX()<<4)|(value&15),(value>>8)-4096,(chunk.getZ()<<4)|((value>>>4)&15));
            if(block.getType()==Material.DIRT)block.setType(Material.FARMLAND,false);
            if(block.getType()==Material.FARMLAND) {plots.add(block.getLocation());removeDisplay(block.getLocation());render(block);}
            else mark(block,false);
        }
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void protectFromPiston(BlockPistonExtendEvent event) {
        // Farmland can be destroyed at the destination without appearing in the moved-block list.
        if(marked(event.getBlock().getRelative(event.getDirection())) || event.getBlocks().stream()
                .anyMatch(block->marked(block) || marked(block.getRelative(event.getDirection()))))event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void protectFromPiston(BlockPistonRetractEvent event) {if(event.getBlocks().stream().anyMatch(this::marked))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void protectExplosion(EntityExplodeEvent event) {event.blockList().removeIf(this::marked);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void protectExplosion(BlockExplodeEvent event) {event.blockList().removeIf(this::marked);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void stopVanillaDrop(BlockBreakEvent event) {if(marked(event.getBlock()))event.setDropItems(false);}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void refund(BlockBreakEvent event) {
        Block block=event.getBlock();if(!marked(block))return;
        mark(block,false);removeDisplay(block.getLocation());
        if(event.getPlayer().getGameMode()!=GameMode.CREATIVE)
            block.getWorld().dropItemNaturally(block.getLocation().add(.5,.2,.5),createItem(1));
    }

    private static int packed(Block block) {
        return (block.getX() & 15) | ((block.getZ() & 15) << 4) | ((block.getY() + 4096) << 8);
    }

    boolean marked(Block block) {
        int[] values = block.getChunk().getPersistentDataContainer().get(plotsKey, PersistentDataType.INTEGER_ARRAY);
        return values != null && Arrays.stream(values).anyMatch(value -> value == packed(block));
    }

    boolean dryLocked(Block block) {
        int[] values = block.getChunk().getPersistentDataContainer().get(dryPlotsKey, PersistentDataType.INTEGER_ARRAY);
        return values != null && Arrays.stream(values).anyMatch(value -> value == packed(block));
    }

    void setDryLocked(Block block, boolean lock) {
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

    void mark(Block block, boolean add) {
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
