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
 *   - 아이템: 다이아 도구 + 단계별 판타지 이름 "드워프의 맥박 +12" + 별(강화 단계) + 3D 모양
 *   - 아이템 데이터: servercore:village_tool = 종류(pickaxe…), servercore:tool_level = 강화 단계
 *   - 모양: 아이템 모델 servercore:village_pickaxe + custom_model_data floats[0] = 강화 단계
 *     (리소스팩이 0/5/10/15/20 으로 모델을 골라요, ⚠ tools/build_pack.py 의 TOOL_TIERS 와 약속)
 *   - 인챈트 금지 (tools.yml allow-enchant): 인챈트 테이블·모루·숫돌에 못 올려요
 *   - 관리자: /villagetool give <type> [level] [player], /villagetool level <level> (손에 든 도구)
 * 대장장이·재료 드롭·확률 강화는 BlacksmithFeature가 담당해요. 곡괭이·도끼 성능은 강화 단계에 맞게 적용해요.
 * 설정: tools.yml
 */
public final class VillageToolFeature implements Feature, Listener {

    private final ServerCore plugin;
    private final NamespacedKey typeKey;
    private final NamespacedKey levelKey;
    private final NamespacedKey waterFullKey;
    private final NamespacedKey waterUsesKey;
    private final com.htk.core.skill.PlacedBlocks placedLogs,legacyPlaced,enhancementPlaced;
    private final java.util.Set<org.bukkit.Location> placedBreaks=new java.util.HashSet<>();
    private YamlConfiguration cfg = new YamlConfiguration();
    private int maxLevel = 20;
    private boolean allowEnchant = false;

    public VillageToolFeature(ServerCore plugin) {
        this.plugin = plugin;
        placedLogs=new com.htk.core.skill.PlacedBlocks(new NamespacedKey(plugin,"tool_placed_logs"));
        legacyPlaced=new com.htk.core.skill.PlacedBlocks(new NamespacedKey(plugin,"placed"));
        enhancementPlaced=new com.htk.core.skill.PlacedBlocks(new NamespacedKey(plugin,"enhancement_placed"));
        this.typeKey = new NamespacedKey(plugin, "village_tool");
        this.levelKey = new NamespacedKey(plugin, "tool_level");
        this.waterFullKey = new NamespacedKey(plugin, "watering_can_full");
        this.waterUsesKey = new NamespacedKey(plugin, "watering_can_uses");
    }

    @Override
    public void enable() {
        loadConfig();
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
    public void disable() { felling.clear();placedBreaks.clear(); }

    private void loadConfig() {
        cfg = plugin.loadYaml("tools.yml");
        cfg.setDefaults(plugin.loadDefaultYaml("tools.yml"));
        maxLevel = Math.max(0, cfg.getInt("max-level", 20));
        allowEnchant = cfg.getBoolean("allow-enchant", false);
    }

    private String msg(String key, String def) {
        return cfg.getString("messages." + key, def);
    }

    // ---------------- 아이템 ----------------

    /** 새 마을 도구 */
    public ItemStack createTool(ToolType type, int level) {
        ItemStack it = new ItemStack(type.material);
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
        // Read old capacity before changing level, so upgrades cannot refill the can.
        int waterUses = type == ToolType.WATERING_CAN
                ? Math.min(waterUses(it), WateringCanRules.capacity(lv)) : 0;
        ItemMeta meta = it.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(levelKey, PersistentDataType.INTEGER, lv);
        boolean indestructible = ToolPerformance.indestructible(lv);
        meta.setUnbreakable(indestructible);
        meta.setDamageResistantTypes(indestructible
                ? RegistryAccess.registryAccess().getRegistry(RegistryKey.DAMAGE_TYPE).getTag(DamageTypeTagKeys.IS_FIRE)
                : null);
        if (indestructible && meta instanceof Damageable damageable) damageable.setDamage(0);

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
        if(type==ToolType.PICKAXE) {
            meta.removeEnchant(org.bukkit.enchantments.Enchantment.EFFICIENCY);
            meta.removeEnchant(org.bukkit.enchantments.Enchantment.FORTUNE);
            int efficiency=ToolPerformance.efficiency(lv),fortune=ToolPerformance.fortune(lv);
            if(efficiency>0)meta.addEnchant(org.bukkit.enchantments.Enchantment.EFFICIENCY,efficiency,true);
            if(fortune>0)meta.addEnchant(org.bukkit.enchantments.Enchantment.FORTUNE,fortune,true);
        }

        // 모양: 아이템 모델 + 강화 단계 (리소스팩이 단계에 맞는 모델을 골라요)
        if (type.model != null && plugin.resourcePackEnabled()) meta.setItemModel(Glyphs.model(type.model));
        CustomModelDataComponent cmd = meta.getCustomModelDataComponent();
        cmd.setFloats(List.of((float) lv));
        if (type == ToolType.WATERING_CAN) cmd.setFlags(List.of(WateringCanRules.canWater(waterUses, lv)));
        meta.setCustomModelDataComponent(cmd);
        it.setItemMeta(meta);
        return true;
    }

    /** 단계별 이름: tools.yml 의 names 에서 lv 이하 중 가장 높은 단계 (5강마다 모양과 같이 바뀌어요) */
    private String nameFor(ToolType type, int lv) {
        String color=lv>=20?"<#ffd34d>":lv>=15?"<#b57edc>":lv>=10?"<#6aa8e0>":lv>=5?"<#7acb52>":"<#d9d9d9>";
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
        var inv=player.getInventory();
        for(int i=0;i<inv.getSize();i++) {
            ItemStack item=inv.getItem(i);
            if(typeOf(item)==null)continue;
            ItemStack updated=item.clone();setLevel(updated,levelOf(item));
            if(!updated.equals(item))inv.setItem(i,updated);
        }
    }

    private void refreshNextTick(Player player) {
        if(!plugin.isEnabled())return;
        Bukkit.getScheduler().runTask(plugin,()->{if(player.isOnline())refreshTools(player);});
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMaxToolDamage(org.bukkit.event.player.PlayerItemDamageEvent event) {
        if (typeOf(event.getItem()) != null && ToolPerformance.indestructible(levelOf(event.getItem()))) {
            event.setCancelled(true);
            refreshNextTick(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMaxToolSpawn(org.bukkit.event.entity.ItemSpawnEvent event) {
        ItemStack item = event.getEntity().getItemStack();
        if (typeOf(item) != null && ToolPerformance.indestructible(levelOf(item))) {
            ItemStack updated = item.clone();
            setLevel(updated, levelOf(item));
            event.getEntity().setItemStack(updated);
        }
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
    public void onRodPickup(org.bukkit.event.entity.EntityPickupItemEvent e){if(e.getEntity() instanceof Player p && typeOf(e.getItem().getItemStack())!=null)refreshNextTick(p);}

    private final java.util.Set<java.util.UUID> felling=new java.util.HashSet<>();

    private boolean playerPlaced(org.bukkit.block.Block b) {
        return placedLogs.isPlaced(b)||legacyPlaced.isPlaced(b)||enhancementPlaced.isPlaced(b);
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void rememberLog(org.bukkit.event.block.BlockPlaceEvent e) {
        if(org.bukkit.Tag.LOGS.isTagged(e.getBlockPlaced().getType()))placedLogs.mark(e.getBlockPlaced());
    }
    @EventHandler(priority=EventPriority.LOWEST,ignoreCancelled=true)
    public void capturePlacedLog(org.bukkit.event.block.BlockBreakEvent e) {
        var b=e.getBlock();if(!org.bukkit.Tag.LOGS.isTagged(b.getType())||!playerPlaced(b))return;
        var location=b.getLocation();placedBreaks.add(location);
        Bukkit.getScheduler().runTask(plugin,()->placedBreaks.remove(location));
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void clearPlacedLog(org.bukkit.event.block.BlockBreakEvent e) {placedLogs.unmark(e.getBlock());}

    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)
    public void axeDrops(org.bukkit.event.block.BlockDropItemEvent e) {
        var tool=e.getPlayer().getInventory().getItemInMainHand();
        if(typeOf(tool)!=ToolType.AXE || placedBreaks.contains(e.getBlock().getLocation()) || !org.bukkit.Tag.LOGS.isTagged(e.getBlockState().getType()))return;
        if(java.util.concurrent.ThreadLocalRandom.current().nextDouble()>=ToolPerformance.axeDoubleChance(levelOf(tool)))return;
        for(var item:e.getItems()){var stack=item.getItemStack().clone();stack.setAmount(stack.getAmount()*2);item.setItemStack(stack);}
    }

    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void fellTree(org.bukkit.event.block.BlockBreakEvent e) {
        Player p=e.getPlayer();var tool=p.getInventory().getItemInMainHand();
        if(placedBreaks.contains(e.getBlock().getLocation())||felling.contains(p.getUniqueId())||typeOf(tool)!=ToolType.AXE||levelOf(tool)<20
                ||p.getGameMode()!=org.bukkit.GameMode.SURVIVAL||!org.bukkit.Tag.LOGS.isTagged(e.getBlock().getType()))return;
        org.bukkit.block.Block root=e.getBlock();org.bukkit.Material kind=root.getType();
        // 탐색·작업량을 제한하고 로드된 청크만 사용한다. 각 블록은 Player.breakBlock으로 보호 이벤트를 거친다.
        var queue=new java.util.ArrayDeque<org.bukkit.block.Block>();
        var seen=new java.util.HashSet<org.bukkit.block.Block>();var logs=new java.util.ArrayList<org.bukkit.block.Block>();
        queue.add(root);seen.add(root);boolean leaves=false;
        while(!queue.isEmpty()&&seen.size()<8192&&logs.size()<4096){
            var b=queue.removeFirst();logs.add(b);
            for(int x=-1;x<=1;x++)for(int y=-1;y<=1;y++)for(int z=-1;z<=1;z++){
                int nx=b.getX()+x,ny=b.getY()+y,nz=b.getZ()+z;
                if(Math.abs(nx-root.getX())>32||Math.abs(nz-root.getZ())>32||Math.abs(ny-root.getY())>64
                        ||ny<root.getWorld().getMinHeight()||ny>=root.getWorld().getMaxHeight()
                        ||!root.getWorld().isChunkLoaded(nx>>4,nz>>4))continue;
                var n=root.getWorld().getBlockAt(nx,ny,nz);
                if((n.getBlockData() instanceof org.bukkit.block.data.type.Leaves leaf&&!leaf.isPersistent())
                        ||n.getType()==org.bukkit.Material.NETHER_WART_BLOCK||n.getType()==org.bukkit.Material.WARPED_WART_BLOCK)leaves=true;
                if(n.getType()==kind&&!playerPlaced(n)&&seen.add(n))queue.add(n);
            }
        }
        if(!leaves)return;
        logs.remove(root);if(logs.isEmpty())return;
        Bukkit.getScheduler().runTask(plugin,()->{
            if(!p.isOnline()||!root.getType().isAir())return;
            felling.add(p.getUniqueId());
            new org.bukkit.scheduler.BukkitRunnable(){int index;
                public void run(){
                    try {
                        if(!p.isOnline()||!p.getWorld().equals(root.getWorld())||p.getGameMode()!=org.bukkit.GameMode.SURVIVAL){cancel();return;}
                        for(int n=0;n<64&&index<logs.size();n++){
                            var current=p.getInventory().getItemInMainHand();
                            if(typeOf(current)!=ToolType.AXE||levelOf(current)<20){cancel();return;}
                            var b=logs.get(index++);
                            if(!b.getWorld().isChunkLoaded(b.getX()>>4,b.getZ()>>4))continue;
                            if(b.getType()==kind&&!p.breakBlock(b)){cancel();return;}
                        }
                        if(index>=logs.size())cancel();
                    } catch(RuntimeException ex){cancel();throw ex;}
                }
                @Override public void cancel(){super.cancel();felling.remove(p.getUniqueId());}
            }.runTaskTimer(plugin,0,1);
        });
    }

    // ---------------- 인챈트 금지 ----------------

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareEnchant(PrepareItemEnchantEvent e) {
        if (!allowEnchant && isTool(e.getItem())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEnchant(EnchantItemEvent e) {
        if (!allowEnchant && isTool(e.getItem())) e.setCancelled(true);
    }

    /** 모루: 인챈트 책·이름 바꾸기·합치기 전부 막아요 (수리는 대장장이에게) */
    @EventHandler(priority = EventPriority.HIGH)
    public void onAnvil(PrepareAnvilEvent e) {
        if (!allowEnchant && hasTool(e.getInventory())) e.setResult(null);
    }

    /** 숫돌: 인챈트를 지우거나 두 도구를 합치지 못하게 */
    @EventHandler(priority = EventPriority.HIGH)
    public void onGrindstone(PrepareGrindstoneEvent e) {
        if (!allowEnchant && hasTool(e.getInventory())) e.setResult(null);
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
