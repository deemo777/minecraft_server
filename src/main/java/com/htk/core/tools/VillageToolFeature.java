package com.htk.core.tools;

import com.htk.core.common.Messages;

import com.htk.core.ServerCore;
import com.htk.core.common.Feature;
import com.htk.core.common.Glyphs;
import com.htk.core.common.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareGrindstoneEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.Damageable;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.keys.tags.DamageTypeTagKeys;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * 마을 도구 (대장장이에게 사는 곡괭이·도끼·괭이·낚싯대·물뿌리개) — 자세한 설계는 강화도구_설계.md
 *   - 아이템: 철/다이아/네더라이트 도구 + 광 도구 이름 + 별(강화 단계) + 3D 모양
 *   - 아이템 데이터: servercore:village_tool = 종류(pickaxe…), servercore:tool_level = 강화 단계
 *   - 모양: 아이템 모델 servercore:village_pickaxe + custom_model_data floats[0] = 강화 단계
 *     (리소스팩이 0/5/10/15/20 으로 모델을 골라요, ⚠ tools/build_pack.py 의 TOOL_TIERS 와 약속)
 *   - 인챈트 금지 (커스텀 장비는 수치 효과만 사용): 인챈트 테이블·모루·숫돌에 못 올려요
 *   - 관리자: /villagetool give <type> [level] [player], /villagetool level <level> (손에 든 도구)
 * 대장장이·재료 드롭·확률 강화는 BlacksmithFeature가 담당해요. 곡괭이·도끼 성능은 강화 단계에 맞게 적용해요.
 * 설정: tools.yml
 */
public final class VillageToolFeature implements Feature, Listener {

    private final ServerCore plugin;
    private final NamespacedKey typeKey;
    private final NamespacedKey levelKey;
    private final NamespacedKey instanceKey;
    private final NamespacedKey waterFullKey;
    private final NamespacedKey waterUsesKey;
    private final com.htk.core.skill.PlacedBlocks placedLogs,legacyPlaced,enhancementPlaced;
    private final java.util.Set<org.bukkit.Location> placedBreaks=new java.util.HashSet<>();
    private YamlConfiguration cfg = new YamlConfiguration();
    private int maxLevel = 25;
    private boolean accepting;
    private NumericDrops numericDrops;
    private NumericFishing numericFishing;
    private WeaponSweepListener weaponSweep;
    private WeaponTraits weaponTraits;
    float weaponAttackCharge(org.bukkit.event.entity.EntityDamageByEntityEvent event) {
        return weaponSweep==null?0:weaponSweep.charge(event);
    }
    boolean secondaryWeaponHit(org.bukkit.event.entity.EntityDamageByEntityEvent event) {
        return weaponSweep!=null && weaponSweep.secondary(event);
    }
    public boolean ownsNumericDrops(org.bukkit.event.block.BlockDropItemEvent event) {
        return numericDrops!=null && numericDrops.handles(event);
    }

    public VillageToolFeature(ServerCore plugin) {
        this.plugin = plugin;
        placedLogs=new com.htk.core.skill.PlacedBlocks(new NamespacedKey(plugin,"tool_placed_logs"));
        legacyPlaced=new com.htk.core.skill.PlacedBlocks(new NamespacedKey(plugin,"placed"));
        enhancementPlaced=new com.htk.core.skill.PlacedBlocks(new NamespacedKey(plugin,"enhancement_placed"));
        this.typeKey = new NamespacedKey(plugin, "village_tool");
        this.levelKey = new NamespacedKey(plugin, "tool_level");
        this.instanceKey = new NamespacedKey(plugin,"tool_instance");
        this.waterFullKey = new NamespacedKey(plugin, "watering_can_full");
        this.waterUsesKey = new NamespacedKey(plugin, "watering_can_uses");
    }

    @Override
    public void enable() {
        loadConfig();accepting=true;
        plugin.getServer().getPluginManager().registerEvents(numericDrops=new NumericDrops(plugin,this), plugin);
        plugin.getServer().getPluginManager().registerEvents(numericFishing=new NumericFishing(plugin,this), plugin);
        plugin.getServer().getPluginManager().registerEvents(weaponTraits=new WeaponTraits(plugin,this),plugin);
        plugin.getServer().getPluginManager().registerEvents(weaponSweep=new WeaponSweepListener(plugin,this,weaponTraits),plugin);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        for(Player p:Bukkit.getOnlinePlayers())refreshTools(p);
        PluginCommand cmd = plugin.getCommand("villagetool");
        if (cmd != null) {
            ToolCommand c = new ToolCommand();
            cmd.setExecutor(c);
            cmd.setTabCompleter(c);
        }
    }

    @Override
    public void reload() {
        loadConfig();
        for(Player p:Bukkit.getOnlinePlayers())refreshTools(p);
    }

    @Override
    public void disable() { accepting=false;if(numericDrops!=null)numericDrops.close();if(numericFishing!=null)numericFishing.close();if(weaponSweep!=null)weaponSweep.close();if(weaponTraits!=null)weaponTraits.close();pendingRefresh.clear();for(var task:List.copyOf(work.values()))task.cancel();work.clear();felling.clear();placedBreaks.clear(); }

    private void loadConfig() {
        cfg = plugin.loadYaml("tools.yml");
        cfg.setDefaults(plugin.loadDefaultYaml("tools.yml"));
        int configured=cfg.getInt("max-level",25);
        maxLevel=configured==20?25:Math.clamp(configured,0,25);
    }

    private String msg(String key, String def) {
        return cfg.getString("messages." + key, def);
    }

    // ---------------- 아이템 ----------------

    /** 새 마을 도구 */
    public ItemStack createTool(ToolType type, int level) {
        ItemStack it = new ItemStack(type.materialAt(level));
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return it;
        meta.getPersistentDataContainer().set(typeKey, PersistentDataType.STRING, type.id);
        it.setItemMeta(meta);
        setLevel(it, level);
        return it;
    }

    /** 마을 도구의 종류 (마을 도구가 아니면 null) */
    public ToolType typeOf(ItemStack it) {
        if (it == null || it.getType().isAir() || !it.hasItemMeta()) return null;
        return ToolType.byId(it.getItemMeta().getPersistentDataContainer().get(typeKey, PersistentDataType.STRING));
    }

    public boolean isTool(ItemStack it) {
        return typeOf(it) != null;
    }

    /** 강화 단계 (마을 도구가 아니면 -1) */
    public int levelOf(ItemStack it) {
        if (typeOf(it) == null) return -1;
        Integer lv = it.getItemMeta().getPersistentDataContainer().get(levelKey, PersistentDataType.INTEGER);
        return lv == null ? 0 : lv;
    }

    public int maxLevel() {
        return maxLevel;
    }

    public ArtisanRules.Progress artisanProgress(ItemStack item) {
        if(!isTool(item))return ArtisanRules.Progress.EMPTY;
        return ArtisanProgressStore.read(item.getItemMeta().getPersistentDataContainer(),levelOf(item));
    }
    public void setArtisanProgress(ItemStack item,ArtisanRules.Progress progress) {
        if(!isTool(item))return;
        var meta=item.getItemMeta();
        ArtisanProgressStore.write(meta.getPersistentDataContainer(),levelOf(item),progress);
        item.setItemMeta(meta);
    }

    public int waterCapacity(ItemStack item) {
        return typeOf(item) == ToolType.WATERING_CAN ? WateringCanRules.capacity(levelOf(item)) : 0;
    }

    public boolean infiniteWater(ItemStack item) {
        return typeOf(item) == ToolType.WATERING_CAN && WateringCanRules.infinite(levelOf(item));
    }

    public int waterUses(ItemStack item) {
        if (typeOf(item) != ToolType.WATERING_CAN) return 0;
        var pdc = item.getItemMeta().getPersistentDataContainer();
        return WateringCanRules.remaining(pdc.get(waterUsesKey, PersistentDataType.INTEGER),
                pdc.getOrDefault(waterFullKey, PersistentDataType.BYTE, (byte) 0) == 1, levelOf(item));
    }

    public void setWaterUses(ItemStack item, int uses) {
        if (typeOf(item) != ToolType.WATERING_CAN) return;
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(waterUsesKey, PersistentDataType.INTEGER,
                Math.clamp(uses, 0, waterCapacity(item)));
        meta.getPersistentDataContainer().remove(waterFullKey);
        item.setItemMeta(meta);
        setLevel(item, levelOf(item));
    }

    /** 강화 단계를 바꾸고 이름·별·모양을 다시 그려요 (마을 도구가 아니면 false) */
    public boolean setLevel(ItemStack it, int level) {
        ToolType type = typeOf(it);
        if (type == null) return false;
        int lv = Math.max(0, Math.min(maxLevel, level));
        int previousLevel=levelOf(it);
        // Read old capacity before changing level, so upgrades cannot refill the can.
        int waterUses = type == ToolType.WATERING_CAN
                ? Math.min(waterUses(it), WateringCanRules.capacity(lv)) : 0;
        ItemMeta oldMeta=it.getItemMeta();
        int originalMax=oldMeta instanceof Damageable d && d.hasMaxDamage()?d.getMaxDamage():it.getType().getMaxDurability();
        int originalWear=oldMeta instanceof Damageable d?d.getDamage():0;
        org.bukkit.Material material=type.materialAt(lv);
        if(it.getType()!=material) {
            ItemMeta previous = it.getItemMeta();
            int oldMax=previous instanceof Damageable d && d.hasMaxDamage()?d.getMaxDamage():it.getType().getMaxDurability();
            int oldDamage=previous instanceof Damageable d?d.getDamage():0;
            it.setType(material);
            if(it.getItemMeta() instanceof Damageable d){
                int newMax=d.hasMaxDamage()?d.getMaxDamage():material.getMaxDurability();
                d.setDamage(ToolDurability.convertedDamage(oldDamage,oldMax,newMax));it.setItemMeta(d);
            }
        }
        ItemMeta meta = it.getItemMeta();
        if(meta instanceof Damageable d){
            int maximum=ToolPerformance.maxDurability(type,lv);d.setMaxDamage(maximum);
            d.setDamage(ToolDurability.convertedDamage(originalWear,originalMax,maximum));
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if(lv!=previousLevel)ArtisanProgressStore.clear(pdc);
        pdc.set(levelKey, PersistentDataType.INTEGER, lv);
        if(!pdc.has(instanceKey,PersistentDataType.STRING))pdc.set(instanceKey,PersistentDataType.STRING,java.util.UUID.randomUUID().toString());
        boolean indestructible = ToolPerformance.indestructible(lv);
        meta.setUnbreakable(indestructible);
        meta.setDamageResistantTypes(indestructible
                ? RegistryAccess.registryAccess().getRegistry(RegistryKey.DAMAGE_TYPE).getTag(DamageTypeTagKeys.IS_FIRE)
                : null);
        if (indestructible && meta instanceof Damageable damageable) damageable.setDamage(0);
        // Migrate all legacy enchanted equipment; performance is now numeric only.
        for(var enchantment:java.util.Set.copyOf(meta.getEnchants().keySet()))meta.removeEnchant(enchantment);
        meta.setEnchantmentGlintOverride(false);

        String name = nameFor(type, lv);
        if (lv > 0) name += cfg.getString("level-suffix", " +%level%").replace("%level%", String.valueOf(lv));
        meta.displayName(noItalic(Text.mm(name)));
        meta.lore(lore(lv));
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS,ItemFlag.HIDE_ATTRIBUTES,
                ItemFlag.HIDE_UNBREAKABLE,ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        if (type == ToolType.WATERING_CAN) {
            pdc.set(waterUsesKey, PersistentDataType.INTEGER, waterUses);
            pdc.remove(waterFullKey);
        }
        // Explicit defaults preserve the material's ordinary combat stats when adding a modifier.
        meta.setAttributeModifiers(type.materialAt(lv).getDefaultAttributeModifiers(org.bukkit.inventory.EquipmentSlot.HAND));
        if(type==ToolType.PICKAXE || type==ToolType.AXE) {
            double natural=material.name().startsWith("NETHERITE")?9:material.name().startsWith("DIAMOND")?8:6;
            double mining=ToolPerformance.miningSpeed(type,lv)-natural;
            meta.addAttributeModifier(org.bukkit.attribute.Attribute.MINING_EFFICIENCY,
                new org.bukkit.attribute.AttributeModifier(new NamespacedKey(plugin,"numeric_mining"),mining,
                    org.bukkit.attribute.AttributeModifier.Operation.ADD_NUMBER,org.bukkit.inventory.EquipmentSlotGroup.MAINHAND));
        }
        // 모양: 아이템 모델 + 강화 단계 (리소스팩이 단계에 맞는 모델을 골라요)
        if (type.model != null && plugin.resourcePackEnabled()) {
            String skin=pdc.get(new NamespacedKey(plugin,"weapon_skin"),PersistentDataType.STRING);
            String model=type.weapon()?com.htk.core.skins.WeaponSkinCatalog.model(type.id,skin,type.model):type.model;
            meta.setItemModel(Glyphs.model(model));
        }
        CustomModelDataComponent cmd = meta.getCustomModelDataComponent();
        cmd.setFloats(List.of((float) lv));
        if (type == ToolType.WATERING_CAN) cmd.setFlags(List.of(WateringCanRules.canWater(waterUses, lv)));
        meta.setCustomModelDataComponent(cmd);
        if(type.weapon()) WeaponPerformance.apply(plugin,type,lv,meta);
        it.setItemMeta(meta);
        if(type.weapon()) WeaponPerformance.applyAnimation(it,type,lv);
        return true;
    }

    /** 단계별 이름: tools.yml 의 names 에서 lv 이하 중 가장 높은 단계 (5강마다 모양과 같이 바뀌어요) */
    private String nameFor(ToolType type, int lv) {
        String color=lv>=25?"<#ffd34d>":lv>=20?"<#ef9c59>":lv>=15?"<#b57edc>":lv>=10?"<#6aa8e0>":lv>=5?"<#7acb52>":"<#d9d9d9>";
        return color+"<bold>광 "+type.korean;
    }

    private List<Component> lore(int lv) {
        List<String> lines = new ArrayList<>();
        // 별: 한 줄에 10칸씩 (최대 단계까지)
        String on = cfg.getString("stars.filled", "<#ffd34d>★");
        String off = cfg.getString("stars.empty", "<dark_gray>☆");
        int perRow = Math.max(1, cfg.getInt("stars.per-row", 10));
        if (maxLevel > 0) {
            for (int start = 0; start < maxLevel; start += perRow) {
                StringBuilder row = new StringBuilder();
                for (int i = start; i < Math.min(maxLevel, start + perRow); i++) row.append(i < lv ? on : off);
                lines.add(row.toString());
            }
        }
        List<Component> out = new ArrayList<>(lines.size());
        for (String l : lines) out.add(noItalic(Text.mm(l)));
        return out;
    }

    private static Component noItalic(Component c) {
        return c.decoration(TextDecoration.ITALIC, false);
    }

    /** 기존 도구도 새 이름·강화 성능으로 갱신한다. 내구도와 기타 아이템 데이터는 보존. */
    private void refreshTools(Player player) {
        refreshInventory(player.getInventory());
        ItemStack cursor = player.getItemOnCursor();
        ItemStack updated = refreshed(cursor);
        if (updated != null && !updated.equals(cursor)) player.setItemOnCursor(updated);
    }

    private ItemStack refreshed(ItemStack item) {
        if (item == null || item.getType().isAir()) return null;
        ItemStack updated = item.clone();
        if (typeOf(item) != null) setLevel(updated, levelOf(item));
        else {
            var smith = plugin.feature(BlacksmithFeature.class);
            if (smith == null || !smith.refreshMaterial(updated)) {
                var meta=updated.getItemMeta();
                if(meta==null || meta.lore()==null || meta.getPersistentDataContainer().getKeys().stream()
                        .noneMatch(key->key.getNamespace().equals("servercore")))return null;
                meta.lore(null);updated.setItemMeta(meta);
            }
        }
        return updated;
    }

    private void refreshInventory(Inventory inv) {
        for(int i=0;i<inv.getSize();i++) {
            ItemStack item=inv.getItem(i);
            ItemStack updated=refreshed(item);
            if(updated!=null && !updated.equals(item))inv.setItem(i,updated);
        }
    }

    private final java.util.Set<java.util.UUID> pendingRefresh = new java.util.HashSet<>();
    private void refreshNextTick(Player player) {
        if(!accepting || !plugin.isEnabled() || !pendingRefresh.add(player.getUniqueId()))return;
        Bukkit.getScheduler().runTask(plugin,()->{
            pendingRefresh.remove(player.getUniqueId());
            if(player.isOnline() && accepting)refreshTools(player);
        });
    }

    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void onItemInventoryOpen(org.bukkit.event.inventory.InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        Inventory opened = event.getInventory();
        refreshNextTick(player);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (accepting && player.isOnline() && player.getOpenInventory().getTopInventory() == opened)
                refreshInventory(opened);
        });
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void onItemClick(org.bukkit.event.inventory.InventoryClickEvent event) {
        if(event.getWhoClicked() instanceof Player player)refreshNextTick(player);
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void onItemDrag(org.bukkit.event.inventory.InventoryDragEvent event) {
        if(event.getWhoClicked() instanceof Player player)refreshNextTick(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMaxToolDamage(org.bukkit.event.player.PlayerItemDamageEvent event) {
        if (typeOf(event.getItem()) != null && ToolPerformance.indestructible(levelOf(event.getItem()))) {
            event.setCancelled(true);
            refreshNextTick(event.getPlayer());
        } else if(isTool(event.getItem())) {
            int wear=NumericEffects.wear(event.getDamage(),ToolPerformance.wearChance(typeOf(event.getItem()),levelOf(event.getItem())),java.util.concurrent.ThreadLocalRandom.current());
            if(wear==0)event.setCancelled(true);else event.setDamage(wear);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onToolSpawn(org.bukkit.event.entity.ItemSpawnEvent event) {
        ItemStack item = event.getEntity().getItemStack();
        ItemStack updated=refreshed(item);
        if(updated!=null && !updated.equals(item))event.getEntity().setItemStack(updated);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMaxToolFire(org.bukkit.event.entity.EntityDamageEvent event) {
        if (!(event.getEntity() instanceof org.bukkit.entity.Item item)
                || typeOf(item.getItemStack()) == null || !ToolPerformance.indestructible(levelOf(item.getItemStack()))) return;
        switch (event.getCause()) {
            case LAVA, FIRE, FIRE_TICK, HOT_FLOOR -> event.setCancelled(true);
            default -> { }
        }
    }
    @EventHandler public void onRodJoin(org.bukkit.event.player.PlayerJoinEvent e){refreshNextTick(e.getPlayer());}
    @EventHandler public void onRodHeld(org.bukkit.event.player.PlayerItemHeldEvent e){refreshNextTick(e.getPlayer());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void onRodSwap(org.bukkit.event.player.PlayerSwapHandItemsEvent e){refreshNextTick(e.getPlayer());}
    @EventHandler public void onRodInventoryClose(org.bukkit.event.inventory.InventoryCloseEvent e){if(e.getPlayer() instanceof Player p)refreshNextTick(p);}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void onRodPickup(org.bukkit.event.entity.EntityPickupItemEvent e){if(e.getEntity() instanceof Player p)refreshNextTick(p);}

    private final java.util.Set<java.util.UUID> felling=new java.util.HashSet<>();
    private final java.util.Map<java.util.UUID,org.bukkit.scheduler.BukkitTask> work=new java.util.HashMap<>();

    private boolean playerPlaced(org.bukkit.block.Block b) {
        return placedLogs.isPlaced(b)||legacyPlaced.isPlaced(b)||enhancementPlaced.isPlaced(b);
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void rememberLog(org.bukkit.event.block.BlockPlaceEvent e) {
        if(org.bukkit.Tag.LOGS.isTagged(e.getBlockPlaced().getType())||ore(e.getBlockPlaced().getType()))placedLogs.mark(e.getBlockPlaced());
    }
    @EventHandler(priority=EventPriority.LOWEST,ignoreCancelled=true)
    public void capturePlacedLog(org.bukkit.event.block.BlockBreakEvent e) {
        var b=e.getBlock();if((!org.bukkit.Tag.LOGS.isTagged(b.getType())&&!ore(b.getType()))||!playerPlaced(b))return;
        var location=b.getLocation();placedBreaks.add(location);
        Bukkit.getScheduler().runTask(plugin,()->placedBreaks.remove(location));
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void clearPlacedLog(org.bukkit.event.block.BlockBreakEvent e) {placedLogs.unmark(e.getBlock());}

    String instance(ItemStack item){return isTool(item)?item.getItemMeta().getPersistentDataContainer().get(instanceKey,PersistentDataType.STRING):null;}
    private static boolean ore(org.bukkit.Material material) {
        return material.name().endsWith("_ORE")||material==org.bukkit.Material.ANCIENT_DEBRIS;
    }
    private static String family(org.bukkit.Material material) {
        return material.name().replace("DEEPSLATE_","");
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void connectedBreak(org.bukkit.event.block.BlockBreakEvent event) {
        Player p=event.getPlayer();ItemStack tool=p.getInventory().getItemInMainHand();ToolType type=typeOf(tool);
        int level=levelOf(tool),limit=ToolPerformance.extraBlocks(type,level);
        var root=event.getBlock();boolean wood=type==ToolType.AXE && org.bukkit.Tag.LOGS.isTagged(root.getType());
        if(!accepting || limit==0 || felling.contains(p.getUniqueId()) || !event.isDropItems()
            || p.getGameMode()!=org.bukkit.GameMode.SURVIVAL || placedBreaks.contains(root.getLocation())
            || playerPlaced(root) || !(wood || type==ToolType.PICKAXE && ore(root.getType())))return;
        org.bukkit.Material kind=root.getType();String oreFamily=family(kind);
        boolean[] leaves={false};
        var blocks=ConnectedBlocks.collect(root,wood?4096:limit+1,b->{
            var neighbors=new java.util.ArrayList<org.bukkit.block.Block>();
            for(int x=-1;x<=1;x++)for(int y=-1;y<=1;y++)for(int z=-1;z<=1;z++){
                if(!wood && Math.abs(x)+Math.abs(y)+Math.abs(z)!=1)continue;
                int nx=b.getX()+x,ny=b.getY()+y,nz=b.getZ()+z;
                if(Math.abs(nx-root.getX())>32||Math.abs(nz-root.getZ())>32||Math.abs(ny-root.getY())>64
                    ||ny<root.getWorld().getMinHeight()||ny>=root.getWorld().getMaxHeight()
                    ||!root.getWorld().isChunkLoaded(nx>>4,nz>>4))continue;
                var neighbor=root.getWorld().getBlockAt(nx,ny,nz);
                if(wood && ((neighbor.getBlockData() instanceof org.bukkit.block.data.type.Leaves leaf&&!leaf.isPersistent())
                    ||neighbor.getType()==org.bukkit.Material.NETHER_WART_BLOCK||neighbor.getType()==org.bukkit.Material.WARPED_WART_BLOCK))leaves[0]=true;
                boolean same=wood?neighbor.getType()==kind:ore(neighbor.getType())&&family(neighbor.getType()).equals(oreFamily);
                if(same && !playerPlaced(neighbor))neighbors.add(neighbor);
            }
            return neighbors;
        });
        if(wood&&!leaves[0])return;
        blocks.remove(root);if(blocks.isEmpty())return;
        int count=Math.min(limit,blocks.size());String expectedId=instance(tool);if(expectedId==null)return;felling.add(p.getUniqueId());
        var task=new org.bukkit.scheduler.BukkitRunnable(){int index;
            public void run(){
                try{
                    var current=p.getInventory().getItemInMainHand();
                    if(!accepting || !p.isOnline() || !p.getWorld().equals(root.getWorld())
                        ||p.getGameMode()!=org.bukkit.GameMode.SURVIVAL || typeOf(current)!=type
                        ||levelOf(current)!=level || !expectedId.equals(instance(current))
                        || (index==0 && !root.getType().isAir())){cancel();return;}
                    for(int n=0;n<32 && index<count;n++){
                        current=p.getInventory().getItemInMainHand();
                        if(typeOf(current)!=type || levelOf(current)!=level || !expectedId.equals(instance(current))){cancel();return;}
                        var b=blocks.get(index++);
                        if(!b.getWorld().isChunkLoaded(b.getX()>>4,b.getZ()>>4)||playerPlaced(b)){cancel();return;}
                        if((wood?b.getType()!=kind:!ore(b.getType())||!family(b.getType()).equals(oreFamily))){cancel();return;}
                        if(!p.breakBlock(b)){cancel();return;}
                    }
                    if(index>=count)cancel();
                }catch(RuntimeException ex){cancel();throw ex;}
            }
            @Override public void cancel(){super.cancel();felling.remove(p.getUniqueId());work.remove(p.getUniqueId());}
        };
        work.put(p.getUniqueId(),task.runTaskTimer(plugin,1,1));
    }
    private static boolean mature(org.bukkit.block.Block block) {
        return switch(block.getType()) {
            case WHEAT,CARROTS,POTATOES,BEETROOTS,NETHER_WART -> block.getBlockData() instanceof org.bukkit.block.data.Ageable age
                && age.getAge()==age.getMaximumAge();
            default -> false;
        };
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void harvest(org.bukkit.event.block.BlockBreakEvent event) {
        Player p=event.getPlayer();ItemStack tool=p.getInventory().getItemInMainHand();int level=levelOf(tool);
        var root=event.getBlock();
        if(!accepting || typeOf(tool)!=ToolType.HOE || level<1 || felling.contains(p.getUniqueId())
            ||!event.isDropItems()||p.getGameMode()!=org.bukkit.GameMode.SURVIVAL||!mature(root))return;
        int centerX=level>=25?p.getLocation().getBlockX():root.getX(),centerZ=level>=25?p.getLocation().getBlockZ():root.getZ();
        if(level>=25 && (Math.abs(root.getX()-centerX)>2||Math.abs(root.getZ()-centerZ)>2))return;
        var targets=new java.util.ArrayList<org.bukkit.block.Block>();
        for(var offset:HarvestRules.offsets(level,p.getLocation().getYaw())){
            int x=centerX+offset.x(),z=centerZ+offset.z();
            if(!root.getWorld().isChunkLoaded(x>>4,z>>4))continue;
            var b=root.getWorld().getBlockAt(x,root.getY(),z);
            if(!b.equals(root)&&mature(b))targets.add(b);
        }
        String expectedId=instance(tool);if(expectedId==null)return;
        Bukkit.getScheduler().runTask(plugin,()->{
            if(!accepting || !p.isOnline() || !root.getType().isAir() || !p.getWorld().equals(root.getWorld())
                ||p.getGameMode()!=org.bukkit.GameMode.SURVIVAL || !expectedId.equals(instance(p.getInventory().getItemInMainHand())))return;
            felling.add(p.getUniqueId());
            try{
                for(var b:targets){
                    var current=p.getInventory().getItemInMainHand();
                    if(typeOf(current)!=ToolType.HOE||levelOf(current)!=level||!expectedId.equals(instance(current)))break;
                    if(b.getWorld().isChunkLoaded(b.getX()>>4,b.getZ()>>4)&&mature(b)&&!p.breakBlock(b))break;
                }
            }finally{felling.remove(p.getUniqueId());}
        });
    }

    // ---------------- 인챈트 금지 ----------------

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareEnchant(PrepareItemEnchantEvent e) {
        if (isTool(e.getItem())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEnchant(EnchantItemEvent e) {
        if (isTool(e.getItem())) e.setCancelled(true);
    }

    /** 모루: 인챈트 책·이름 바꾸기·합치기 전부 막아요 (수리는 수리 기사에게) */
    @EventHandler(priority = EventPriority.HIGH)
    public void onAnvil(PrepareAnvilEvent e) {
        if (hasTool(e.getInventory())) e.setResult(null);
    }

    /** 숫돌: 인챈트를 지우거나 두 도구를 합치지 못하게 */
    @EventHandler(priority = EventPriority.HIGH)
    public void onGrindstone(PrepareGrindstoneEvent e) {
        if (hasTool(e.getInventory())) e.setResult(null);
    }

    private boolean hasTool(Inventory inv) {
        for (int i = 0; i < 2; i++) if (isTool(inv.getItem(i))) return true;
        return false;
    }

    // ---------------- 관리자 명령어 ----------------

    private final class ToolCommand implements CommandExecutor, TabCompleter {

        @Override
        public boolean onCommand(CommandSender s, Command command, String label, String[] args) {
            if (!s.hasPermission("servercore.admin")) {
                Messages.send(s,Text.mm(plugin.msg("no-permission")));
                return true;
            }
            String sub = args.length == 0 ? "" : args[0].toLowerCase();
            switch (sub) {
                case "give" -> give(s, label, args);
                case "level" -> level(s, label, args);
                default -> {
                    Messages.send(s,Text.mm("<gray>/" + label + " give <type> [level] [player]  <dark_gray>— 종류: pickaxe axe hoe fishing_rod watering_can"));
                    Messages.send(s,Text.mm("<gray>/" + label + " level <level>  <dark_gray>— 손에 든 마을 도구의 강화 단계 바꾸기"));
                }
            }
            return true;
        }

        private void give(CommandSender s, String label, String[] args) {
            ToolType type = args.length >= 2 ? ToolType.parse(args[1]) : null;
            if (type == null) {
                Messages.send(s,Text.mm("<red>종류를 적어 주세요. 예: /" + label + " give 곡괭이 5"));
                return;
            }
            int lv = 0;
            if (args.length >= 3) {
                try {
                    lv = Integer.parseInt(args[2]);
                } catch (NumberFormatException ex) {
                    Messages.send(s,Text.mm("<red>단계는 숫자로 적어 주세요. (0~" + maxLevel + ")"));
                    return;
                }
            }
            Player to = args.length >= 4 ? Bukkit.getPlayerExact(args[3]) : (s instanceof Player p ? p : null);
            if (to == null) {
                Messages.send(s,Text.mm("<red>받을 플레이어를 찾을 수 없어요."));
                return;
            }
            ItemStack it = createTool(type, lv);
            to.getInventory().addItem(it).values().forEach(left -> to.getWorld().dropItem(to.getLocation(), left));
            Messages.send(s,Text.mm("<green>" + to.getName() + "에게 마을 " + type.korean + " +" + levelOf(it) + " 을(를) 줬어요."));
        }

        private void level(CommandSender s, String label, String[] args) {
            if (!(s instanceof Player p)) {
                Messages.send(s,Text.mm("<red>게임 안에서 손에 도구를 들고 써 주세요."));
                return;
            }
            ItemStack hand = p.getInventory().getItemInMainHand();
            if (!isTool(hand)) {
                Messages.send(s,Text.mm(msg("not-tool", "<red>손에 마을 도구를 들어 주세요.")));
                return;
            }
            int lv;
            try {
                lv = Integer.parseInt(args.length >= 2 ? args[1] : "");
            } catch (NumberFormatException ex) {
                Messages.send(s,Text.mm("<red>단계를 숫자로 적어 주세요. 예: /" + label + " level 10"));
                return;
            }
            setLevel(hand, lv);
            p.getInventory().setItemInMainHand(hand);
            Messages.send(s,Text.mm("<green>강화 단계를 +" + levelOf(hand) + "(으)로 바꿨어요."));
        }

        @Override
        public List<String> onTabComplete(CommandSender s, Command command, String label, String[] args) {
            List<String> out = new ArrayList<>();
            if (!s.hasPermission("servercore.admin")) return out;
            if (args.length == 1) out.addAll(List.of("give", "level"));
            else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
                for (ToolType t : ToolType.values()) out.add(t.id);
            } else if ((args.length == 3 && args[0].equalsIgnoreCase("give")) || (args.length == 2 && args[0].equalsIgnoreCase("level"))) {
                for (int i = 0; i <= maxLevel; i += 5) out.add(String.valueOf(i));
            } else if (args.length == 4 && args[0].equalsIgnoreCase("give")) {
                for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
            }
            String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase();
            out.removeIf(o -> !o.toLowerCase().startsWith(last));
            return out;
        }
    }
}
