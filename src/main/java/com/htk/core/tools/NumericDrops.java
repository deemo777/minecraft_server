package com.htk.core.tools;

import com.htk.core.ServerCore;
import com.htk.core.skill.Skill;
import com.htk.core.skill.SkillFeature;
import org.bukkit.*;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Ageable;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Item;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/** Replace only custom equipment drops, after protection and final cancellation decisions. */
final class NumericDrops implements Listener {
    private final ServerCore plugin;
    private final VillageToolFeature tools;
    private final YamlConfiguration rules;
    private final Map<Location,Boolean> preserves=new HashMap<>();
    private final Map<Location, ItemStack> breaking = new HashMap<>();
    private final Set<Runnable> pending=new LinkedHashSet<>();
    private void defer(Runnable task) {
        pending.add(task);
        Bukkit.getScheduler().runTask(plugin,()->{if(pending.remove(task))task.run();});
    }
    void close() {
        for(Runnable task:List.copyOf(pending))if(pending.remove(task))task.run();
        breaking.clear();hives.clear();preserves.clear();
    }
    private final Map<Location,HiveHarvest> hives=new HashMap<>();
    private static final class HiveHarvest {
        final org.bukkit.block.Beehive original;
        boolean handled;
        HiveHarvest(org.bukkit.block.Beehive original){this.original=original;}
        ItemStack item() {
            ItemStack result=new ItemStack(original.getType());
            if(result.getItemMeta() instanceof BlockStateMeta meta){meta.setBlockState(original);result.setItemMeta(meta);}
            return result;
        }
    }
    NumericDrops(ServerCore plugin,VillageToolFeature tools) {
        this.plugin=plugin;this.tools=tools;
        try(var reader=new InputStreamReader(Objects.requireNonNull(plugin.getResource("numeric-drops.yml")),StandardCharsets.UTF_8)) {
            rules=YamlConfiguration.loadConfiguration(reader);
        } catch(java.io.IOException ex) {throw new IllegalStateException(ex);}
    }
    @EventHandler(priority=EventPriority.LOWEST,ignoreCancelled=true)
    public void choosePreservation(BlockBreakEvent event) {
        var item=event.getPlayer().getInventory().getItemInMainHand();
        if(tools.typeOf(item)!=ToolType.AXE)return;
        var location=event.getBlock().getLocation();
        preserves.put(location,ToolPerformance.preserveChance(tools.levelOf(item))==1);
        defer(()->preserves.remove(location));
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void preserveHive(BlockBreakEvent event) {
        var item=event.getPlayer().getInventory().getItemInMainHand();
        if(tools.typeOf(item)!=ToolType.AXE || !Boolean.TRUE.equals(preserves.get(event.getBlock().getLocation()))
                || !event.isDropItems() || event.getPlayer().getGameMode()!=GameMode.SURVIVAL
                || !(event.getBlock().getState() instanceof org.bukkit.block.Beehive hive))return;
        var harvest=new HiveHarvest(hive);var location=event.getBlock().getLocation();hives.put(location,harvest);
        // Keep the original snapshot for the item, remove occupants only from the live
        // block so ordinary destruction cannot also release a second copy of the bees.
        var live=(org.bukkit.block.Beehive)event.getBlock().getState();live.clearEntities();live.update(true,false);
        defer(()->{
            hives.remove(location,harvest);
            if(event.isCancelled() || event.getBlock().getType()==hive.getType()) {
                if(event.getBlock().getType()==hive.getType())hive.update(true,false);
            } else if(!harvest.handled && event.isDropItems()) {
                // Empty baseline loot can omit BlockDropItemEvent entirely.
                location.getWorld().dropItemNaturally(location.clone().add(.5,.5,.5),harvest.item());
            }
        });
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void capture(BlockBreakEvent event) {
        var item=event.getPlayer().getInventory().getItemInMainHand();
        if(!tools.isTool(item) || !event.isDropItems()
                || event.getBlock().getBlockData().requiresCorrectToolForDrops() && !event.getBlock().isPreferredTool(item))return;
        Location location=event.getBlock().getLocation();
        breaking.put(location,item.clone());
        defer(()->breaking.remove(location));
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void preservedExperience(BlockBreakEvent event) {
        var item=event.getPlayer().getInventory().getItemInMainHand();
        if(tools.typeOf(item)==ToolType.AXE && Boolean.TRUE.equals(preserves.get(event.getBlock().getLocation()))
                && rules.contains(event.getBlock().getType().name()+".preserve"))event.setExpToDrop(0);
    }
    boolean handles(BlockDropItemEvent event) {
        ItemStack item=breaking.get(event.getBlock().getLocation());
        ToolType type=tools.typeOf(item);if(type==null)return false;
        if(hives.containsKey(event.getBlock().getLocation()))return true;
        String path=event.getBlockState().getType().name();int level=tools.levelOf(item);
        if(type==ToolType.AXE && Boolean.TRUE.equals(preserves.get(event.getBlock().getLocation())) && rules.contains(path+".preserve"))return true;
        if(type==ToolType.PICKAXE)return rules.contains(path+".bonuses") && ToolPerformance.fortuneStrength(level)>0;
        if(type!=ToolType.HOE || level<5 || !crop(event.getBlockState().getType()))return false;
        return !(event.getBlockState().getBlockData() instanceof Ageable age)||age.getAge()==age.getMaximumAge();
    }
    @EventHandler(priority=EventPriority.LOWEST)
    public void hiveDropSeen(BlockDropItemEvent event) {
        var harvest=hives.get(event.getBlock().getLocation());
        if(harvest!=null)harvest.handled=true;
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void drops(BlockDropItemEvent event) {
        if(!handles(event)) {breaking.remove(event.getBlock().getLocation());return;}
        ItemStack item=breaking.remove(event.getBlock().getLocation());
        ToolType type=tools.typeOf(item);if(type==null)return;
        int level=tools.levelOf(item);
        boolean preserve=type==ToolType.AXE && Boolean.TRUE.equals(preserves.get(event.getBlock().getLocation()));
        int bonus=type==ToolType.PICKAXE?ToolPerformance.rolledFortune(level,ThreadLocalRandom.current().nextDouble()):0;
        BlockState state=event.getBlockState();String block=state.getType().name();
        HiveHarvest hive=hives.get(state.getLocation());
        if(hive!=null)hive.handled=true;
        var config=rules.getConfigurationSection(block);
        if(hive==null && config==null && type!=ToolType.HOE)return;
        var random=ThreadLocalRandom.current();
        List<ItemStack> output=new ArrayList<>();
        for(Item drop:event.getItems())output.add(drop.getItemStack().clone());
        String preserved=preserve && config!=null?config.getString("preserve"):null;
        if(hive!=null) {
            output.clear();output.add(hive.item());
        } else if(preserved!=null) {
            Material material=Material.matchMaterial(preserved);
            if(material==null)return;
            ItemStack result=new ItemStack(material);
            // Preserve block entity state on supported block items rather than erasing contents.
            if(result.getItemMeta() instanceof BlockStateMeta meta) {
                meta.setBlockState(state);result.setItemMeta(meta);
            }
            output.clear();output.add(result);
        } else if(type==ToolType.HOE) {
            if(state.getBlockData() instanceof Ageable age && age.getAge()<age.getMaximumAge())return;
            int multiplier=ToolPerformance.cropMultiplier(level,random.nextDouble());
            var originals=List.copyOf(output);output.clear();
            for(ItemStack stack:originals)add(output,stack.getType(),stack.getAmount()*multiplier);
        } else {
            var bonuses=config.getConfigurationSection("bonuses");if(bonuses==null)return;
            boolean crop=Set.of("WHEAT","CARROTS","POTATOES","BEETROOTS","NETHER_WART").contains(block);
            boolean mature=!crop || !(state.getBlockData() instanceof Ageable age)||age.getAge()==age.getMaximumAge();
            if(!mature)return;
            for(String key:bonuses.getKeys(false)) {
                Material material=Material.matchMaterial(key);if(material==null)continue;
                var spec=bonuses.getConfigurationSection(key);if(spec==null)continue;
                int count=output.stream().filter(i->i.getType()==material).mapToInt(ItemStack::getAmount).sum();
                String formula=spec.getString("formula","");
                if(formula.equals("chance")) {
                    var chances=spec.getDoubleList("chances");
                    double chance=chances.get(Math.min(bonus,chances.size()-1));
                    boolean success=random.nextDouble()<chance;
                    int next=success?random.nextInt(spec.getInt("min",1),spec.getInt("max",1)+1):0;
                    output.removeIf(i->i.getType()==material);
                    if(block.equals("GRAVEL")) {
                        output.removeIf(i->i.getType()==Material.GRAVEL);
                        if(!success)add(output,Material.GRAVEL,1);
                    }
                    if(block.equals("GILDED_BLACKSTONE")) {
                        output.removeIf(i->i.getType()==Material.GILDED_BLACKSTONE);
                        if(!success)add(output,Material.GILDED_BLACKSTONE,1);
                    }
                    add(output,material,next);continue;
                }
                int next=switch(formula) {
                    case "ore_drops" -> count*NumericEffects.oreMultiplier(bonus,random);
                    case "uniform_bonus_count" -> count==0?0:count+random.nextInt(bonus*spec.getInt("bonusMultiplier",1)+1);
                    case "binomial_with_bonus_count" -> count+NumericEffects.binomial(bonus,spec.getDouble("probability"),random);
                    default -> count;
                };
                next=Math.min(next,spec.getInt("cap",Integer.MAX_VALUE));
                output.removeIf(i->i.getType()==material);
                add(output,material,next);
            }
        }
        // Do not add Item entities to the event's list (Bukkit forbids that).
        // Defer replacement until all listeners have decided whether this drop is allowed.
        event.getItems().clear();
        boolean farm=type==ToolType.HOE;
        defer(()->{
            if(event.isCancelled())return;
            var skills=plugin.feature(SkillFeature.class);
            boolean doubled=farm && skills!=null && random.nextDouble()<skills.doubleDropChance(event.getPlayer().getUniqueId(),Skill.FARMING);
            var at=state.getLocation().add(.5,.5,.5);
            for(ItemStack stack:output) {
                at.getWorld().dropItemNaturally(at,stack);
                if(doubled)at.getWorld().dropItemNaturally(at,stack.clone());
            }
        });
    }
    static boolean crop(Material material) {
        return switch(material) {
            case WHEAT,CARROTS,POTATOES,BEETROOTS,NETHER_WART,MELON,PUMPKIN,SUGAR_CANE,COCOA -> true;
            default -> false;
        };
    }
    private static void add(List<ItemStack> result,Material material,int amount) {
        while(amount>0) {int n=Math.min(amount,material.getMaxStackSize());result.add(new ItemStack(material,n));amount-=n;}
    }
}
