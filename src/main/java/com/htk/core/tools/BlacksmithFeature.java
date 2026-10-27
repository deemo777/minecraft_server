package com.htk.core.tools;

import com.htk.core.ServerCore;
import com.htk.core.common.Feature;
import com.htk.core.common.Glyphs;
import com.htk.core.common.Text;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.type.Farmland;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public final class BlacksmithFeature implements Feature, Listener, TabExecutor {
    private final ServerCore plugin;
    private final VillageToolFeature tools;
    private final NamespacedKey npcKey, materialKey, placedKey, legacyPlacedKey;
    private final Map<UUID, EnhancementGui> sessions = new HashMap<>();
    private final Map<UUID, EquipmentSlot> fishingHands = new HashMap<>();
    private YamlConfiguration config;
    private double dropChance;
    private boolean accepting;

    public BlacksmithFeature(ServerCore plugin, VillageToolFeature tools) {
        this.plugin = plugin; this.tools = tools;
        npcKey = new NamespacedKey(plugin, "blacksmith_npc");
        materialKey = new NamespacedKey(plugin, "enhancement_material");
        placedKey = new NamespacedKey(plugin, "enhancement_placed");
        legacyPlacedKey = new NamespacedKey(plugin, "placed");
    }
    ServerCore plugin() { return plugin; }
    VillageToolFeature tools() { return tools; }
    int maxLevel() { return Math.min(UpgradeRules.MAX_LEVEL, tools.maxLevel()); }
    @Override public void enable() {
        reload(); accepting = true;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        for (String cmd : List.of("blacksmithnpc", "enhance")) {
            PluginCommand c = plugin.getCommand(cmd);
            if (c != null) { c.setExecutor(this); c.setTabCompleter(this); }
        }
    }
    @Override public void reload() {
        config = plugin.loadYaml("tools.yml");
        config.setDefaults(plugin.loadDefaultYaml("tools.yml"));
        dropChance = Math.clamp(config.getDouble("enhancement.drop-chance", .10), 0, 1);
        for (EnhancementGui gui : sessions.values()) gui.refresh();
    }
    @Override public void disable() {
        accepting = false;
        for (var entry : new ArrayList<>(sessions.entrySet())) {
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p != null) { giveBack(p, entry.getValue()); p.closeInventory(); }
        }
        sessions.clear();
    }
    int successPercent(int target) {
        List<Integer> values = config.getIntegerList("enhancement.success-rates");
        return target <= values.size() ? Math.clamp(values.get(target - 1), 0, 100) : UpgradeRules.success(target);
    }
    boolean validTool(ItemStack item) {
        ToolType type = tools.typeOf(item);
        return type != null && item.getType() == type.material && item.getAmount() == 1
                && tools.levelOf(item) >= 0 && tools.levelOf(item) <= maxLevel();
    }
    ToolType materialType(ItemStack item) {
        if (item == null || item.getType() != Material.AMETHYST_SHARD || !item.hasItemMeta()) return null;
        return ToolType.byId(item.getItemMeta().getPersistentDataContainer().get(materialKey, PersistentDataType.STRING));
    }
    public ItemStack material(ToolType type, int count) {
        ItemStack item = new ItemStack(Material.AMETHYST_SHARD, count);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(materialKey, PersistentDataType.STRING, type.id);
        meta.displayName(Text.mm("<#a9dce7><bold>" + type.korean + " 강화 결정"));
        meta.lore(List.of(Text.mm("<gray>대장간에서 같은 종류 장비를 강화해요"),
                Text.mm("<dark_gray>+1~5: 1개 · +6~10: 2개 · +11~15: 3개 · +16~20: 4개")));
        if (plugin.resourcePackEnabled()) meta.setItemModel(Glyphs.model("enhancement_" + type.id));
        item.setItemMeta(meta); return item;
    }
    private void reward(Player p, ToolType type, Location at) {
        if (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) return;
        if (ThreadLocalRandom.current().nextDouble() >= dropChance) return;
        at.getWorld().dropItemNaturally(at, material(type, 1));
    }
    private void open(Player p) {
        if (!accepting) return;
        p.closeInventory();
        EnhancementGui gui = new EnhancementGui(this, p);
        sessions.put(p.getUniqueId(), gui); p.openInventory(gui.inventory);
    }
    private void returnItem(Player p, ItemStack item) {
        if (item == null || item.getType().isAir()) return;
        p.getInventory().addItem(item).values().forEach(extra -> p.getWorld().dropItemNaturally(p.getLocation(), extra));
    }
    private void giveBack(Player p, EnhancementGui gui) {
        sessions.remove(p.getUniqueId(), gui);
        for (ItemStack item : gui.drain()) returnItem(p, item);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof EnhancementGui gui)) return;
        if (!(e.getWhoClicked() instanceof Player p)) { e.setCancelled(true); return; }
        if (!gui.owner.equals(p.getUniqueId()) || gui.returned) { e.setCancelled(true); return; }
        int raw = e.getRawSlot();
        if (raw >= 27 && !e.isShiftClick() && e.getClick() != ClickType.DOUBLE_CLICK
                && e.getClick() != ClickType.SWAP_OFFHAND && e.getClick() != ClickType.NUMBER_KEY
                && e.getClick() != ClickType.CREATIVE) return;
        e.setCancelled(true);
        if (raw >= EnhancementGui.UPGRADE - 1 && raw <= EnhancementGui.UPGRADE + 1) {
            if (e.getClick() == ClickType.LEFT || e.getClick() == ClickType.RIGHT) attempt(p, gui);
            return;
        }
        if (raw >= 27 && e.isShiftClick()) {
            ItemStack source = e.getCurrentItem();
            int target = validTool(source) ? EnhancementGui.TOOL : materialType(source) != null ? EnhancementGui.MATERIAL : -1;
            if (target < 0) return;
            ItemStack current = gui.inventory.getItem(target);
            int limit = target == EnhancementGui.TOOL ? 1 : 64;
            if (current != null && !current.isSimilar(source)) return;
            int n = current == null ? 0 : current.getAmount();
            int moved = Math.min(limit - n, source.getAmount());
            if (moved <= 0) return;
            ItemStack placed = source.clone(); placed.setAmount(n + moved);
            gui.inventory.setItem(target, placed);
            source.setAmount(source.getAmount() - moved); e.setCurrentItem(source.getAmount() == 0 ? null : source);
            gui.refresh(); return;
        }
        if ((raw != EnhancementGui.TOOL && raw != EnhancementGui.MATERIAL)
                || (e.getClick() != ClickType.LEFT && e.getClick() != ClickType.RIGHT)) return;
        ItemStack cursor = e.getCursor(), slot = gui.inventory.getItem(raw);
        boolean emptyCursor = cursor == null || cursor.getType().isAir();
        if (emptyCursor) {
            if (slot == null) return;
            int amount = e.getClick() == ClickType.RIGHT ? (slot.getAmount() + 1) / 2 : slot.getAmount();
            ItemStack taken = slot.clone(); taken.setAmount(amount);
            e.getView().setCursor(taken);
            slot.setAmount(slot.getAmount() - amount);
            gui.inventory.setItem(raw, slot.getAmount() == 0 ? null : slot);
        } else {
            boolean valid = raw == EnhancementGui.TOOL ? validTool(cursor) : materialType(cursor) != null;
            if (!valid) { p.sendActionBar(Text.mm("<red>왼쪽에는 마을 장비 1개, 오른쪽에는 강화 결정만 넣을 수 있어요.")); return; }
            if (slot == null || slot.isSimilar(cursor)) {
                int n = slot == null ? 0 : slot.getAmount();
                int limit = raw == EnhancementGui.TOOL ? 1 : 64;
                int moved = Math.min(limit - n, e.getClick() == ClickType.RIGHT ? 1 : cursor.getAmount());
                if (moved <= 0) return;
                ItemStack placed = cursor.clone(); placed.setAmount(n + moved); gui.inventory.setItem(raw, placed);
                cursor.setAmount(cursor.getAmount() - moved); e.getView().setCursor(cursor.getAmount() == 0 ? null : cursor);
            } else if (e.getClick() == ClickType.LEFT) {
                gui.inventory.setItem(raw, cursor.clone()); e.getView().setCursor(slot);
            }
        }
        gui.refresh();
    }
    private void attempt(Player p, EnhancementGui gui) {
        long now = System.nanoTime();
        if (now < gui.nextAttempt) return;
        gui.nextAttempt = now + 300_000_000L;
        ItemStack item = gui.inventory.getItem(EnhancementGui.TOOL);
        if (!validTool(item)) { p.sendMessage(Text.mm("<red>왼쪽에 마을 장비 1개를 넣어 주세요.")); return; }
        int current = tools.levelOf(item);
        if (current >= maxLevel()) { p.sendMessage(Text.mm("<gold>이미 최대 강화 단계예요.")); return; }
        ToolType type = tools.typeOf(item);
        ItemStack material = gui.inventory.getItem(EnhancementGui.MATERIAL);
        int required = UpgradeRules.required(current + 1);
        if (materialType(material) != type || material.getAmount() < required) {
            p.sendMessage(Text.mm("<red>" + type.korean + " 강화 결정 " + required + "개가 필요해요.")); return;
        }
        int result = UpgradeRules.resolve(current, ThreadLocalRandom.current().nextDouble(),
                ThreadLocalRandom.current().nextDouble(), successPercent(current + 1) / 100.0);
        ItemStack upgraded = item.clone();
        if (!tools.setLevel(upgraded, result)) return;
        material.setAmount(material.getAmount() - required);
        gui.inventory.setItem(EnhancementGui.MATERIAL, material.getAmount() == 0 ? null : material);
        gui.inventory.setItem(EnhancementGui.TOOL, upgraded);
        gui.refresh();
        if (result > current) {
            p.sendMessage(Text.mm("<#ffc773><bold>강화 성공!</bold> <white>+" + current + " → +" + result));
            p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, .7f, 1.3f);
        } else {
            p.sendMessage(Text.mm("<red>강화 실패 <gray>· " + (result < current ? "+" + current + " → +" + result : "단계 유지")
                    + " · 재료 " + required + "개 소비"));
            p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_LAND, .5f, .7f);
        }
    }
    @EventHandler public void onDrag(InventoryDragEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof EnhancementGui)) return;
        if (e.getRawSlots().stream().anyMatch(slot -> slot < 27)) e.setCancelled(true);
    }
    @EventHandler public void onClose(InventoryCloseEvent e) {
        if (e.getInventory().getHolder() instanceof EnhancementGui gui && e.getPlayer() instanceof Player p) giveBack(p, gui);
    }
    @EventHandler public void onQuit(PlayerQuitEvent e) {
        fishingHands.remove(e.getPlayer().getUniqueId());
        EnhancementGui gui = sessions.get(e.getPlayer().getUniqueId());
        if (gui != null) giveBack(e.getPlayer(), gui);
    }
    @EventHandler(priority = EventPriority.HIGHEST) public void onDeath(PlayerDeathEvent e) {
        EnhancementGui gui = sessions.remove(e.getEntity().getUniqueId());
        if (gui == null) return;
        for (ItemStack item : gui.drain()) if (item != null) {
            if (e.getKeepInventory()) returnItem(e.getEntity(), item); else e.getDrops().add(item);
        }
        e.getEntity().closeInventory();
    }

    private boolean isNpc(Entity entity) { return entity.getPersistentDataContainer().has(npcKey, PersistentDataType.BYTE); }
    @EventHandler(priority = EventPriority.HIGHEST) public void onNpc(PlayerInteractEntityEvent e) {
        if (!isNpc(e.getRightClicked())) return;
        e.setCancelled(true);
        if (e.getHand() == EquipmentSlot.HAND) open(e.getPlayer());
    }
    @EventHandler public void onNpcDamage(EntityDamageEvent e) { if (isNpc(e.getEntity())) e.setCancelled(true); }
    @EventHandler public void onNpcTransform(EntityTransformEvent e) { if (isNpc(e.getEntity())) e.setCancelled(true); }
    private void spawn(Player p) {
        Location at = p.getLocation();
        at.setX(at.getBlockX() + 0.5);
        at.setZ(at.getBlockZ() + 0.5);
        at.setYaw(at.getYaw() + 180);
        Villager v = at.getWorld().spawn(at, Villager.class, npc -> {
            npc.getPersistentDataContainer().set(npcKey, PersistentDataType.BYTE, (byte)1);
            npc.customName(Text.mm("<#ffc773><bold>대장장이")); npc.setCustomNameVisible(true);
            npc.setAI(false); npc.setInvulnerable(true); npc.setSilent(true); npc.setCollidable(false);
            npc.setPersistent(true); npc.setRemoveWhenFarAway(false);
            var profession = Registry.VILLAGER_PROFESSION.get(NamespacedKey.minecraft("weaponsmith"));
            if (profession != null) npc.setProfession(profession);
            npc.setVillagerLevel(5);
        });
        if (!v.isValid()) p.sendMessage(Text.mm("<red>NPC 생성이 차단됐어요. WorldGuard의 mob-spawning 설정을 확인해 주세요."));
        else p.sendMessage(Text.mm("<green>대장장이를 세웠어요. 우클릭하면 강화 창이 열려요."));
    }

    @Override public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("servercore.admin")) { sender.sendMessage(Text.mm("<red>관리자만 사용할 수 있어요.")); return true; }
        if (cmd.getName().equals("enhance")) {
            if (args.length >= 3 && args[0].equalsIgnoreCase("material")) {
                ToolType type = ToolType.parse(args[1]);
                int amount;
                try { amount = Integer.parseInt(args[2]); } catch (NumberFormatException ex) { amount = 0; }
                Player target = args.length >= 4 ? Bukkit.getPlayerExact(args[3]) : sender instanceof Player p ? p : null;
                if (type == null || target == null || amount < 1 || amount > 64) {
                    sender.sendMessage(Text.mm("<red>/enhance material <종류> <1~64> [플레이어]")); return true;
                }
                returnItem(target, material(type, amount));
                sender.sendMessage(Text.mm("<green>강화 결정을 지급했어요.")); return true;
            }
            Player target = args.length >= 1 ? Bukkit.getPlayerExact(args[0]) : sender instanceof Player p ? p : null;
            if (target != null) open(target); else sender.sendMessage(Text.mm("<red>/enhance [플레이어]"));
            return true;
        }
        if (!(sender instanceof Player p)) { sender.sendMessage("게임 안에서 사용해 주세요."); return true; }
        if (args.length == 1 && args[0].equalsIgnoreCase("create")) spawn(p);
        else if (args.length == 1 && args[0].equalsIgnoreCase("remove")) {
            Entity nearest = p.getNearbyEntities(5,5,5).stream().filter(this::isNpc)
                    .filter(e -> e.getLocation().distanceSquared(p.getLocation()) <= 25)
                    .min(Comparator.comparingDouble(e -> e.getLocation().distanceSquared(p.getLocation()))).orElse(null);
            if (nearest == null) p.sendMessage(Text.mm("<red>5블록 안에 대장장이가 없어요."));
            else { nearest.remove(); p.sendMessage(Text.mm("<green>대장장이를 없앴어요.")); }
        } else sender.sendMessage(Text.mm("<gray>/blacksmithnpc create | remove"));
        return true;
    }
    @Override public List<String> onTabComplete(CommandSender s, Command c, String alias, String[] args) {
        if (!s.hasPermission("servercore.admin")) return List.of();
        if (c.getName().equals("blacksmithnpc") && args.length == 1) return List.of("create","remove");
        if (c.getName().equals("enhance") && args.length == 1) return List.of("material");
        if (args.length == 2 && args[0].equalsIgnoreCase("material")) return Arrays.stream(ToolType.values()).map(t -> t.id).toList();
        return List.of();
    }

    private static boolean stone(Material type) {
        String n = type.name();
        return n.endsWith("_ORE") || Set.of(Material.STONE, Material.DEEPSLATE, Material.GRANITE,
                Material.DIORITE, Material.ANDESITE, Material.TUFF, Material.CALCITE, Material.NETHERRACK,
                Material.BASALT, Material.BLACKSTONE, Material.END_STONE, Material.ANCIENT_DEBRIS).contains(type);
    }
    private static boolean wood(Material type) { return Tag.LOGS.isTagged(type); }
    private static int position(Block b) { return (b.getX() & 15) | ((b.getZ() & 15) << 4) | ((b.getY() + 4096) << 8); }
    private boolean marked(Block b, NamespacedKey key) {
        int[] values = b.getChunk().getPersistentDataContainer().get(key, PersistentDataType.INTEGER_ARRAY);
        if (values != null) for (int value : values) if (value == position(b)) return true;
        return false;
    }
    private void mark(Block b, boolean add) {
        var data = b.getChunk().getPersistentDataContainer();
        int[] values = data.get(placedKey, PersistentDataType.INTEGER_ARRAY);
        if (values == null && !add) return;
        if (values == null) values = new int[0];
        int at = position(b);
        int[] filtered = Arrays.stream(values).filter(v -> v != at).toArray();
        int[] result = add ? Arrays.copyOf(filtered, filtered.length+1) : filtered;
        if (add) result[result.length-1] = at;
        if (result.length == 0) data.remove(placedKey); else data.set(placedKey, PersistentDataType.INTEGER_ARRAY, result);
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) public void onPlace(BlockPlaceEvent e) {
        Block b = e.getBlockPlaced(); if (stone(b.getType()) || wood(b.getType())) mark(b, true);
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        boolean placed = marked(b, placedKey) || marked(b, legacyPlacedKey);
        mark(b, false);
        if (placed || !e.isDropItems()) return;
        Player p = e.getPlayer(); ToolType type = tools.typeOf(p.getInventory().getItemInMainHand());
        if (type == ToolType.PICKAXE && stone(b.getType()) || type == ToolType.AXE && wood(b.getType())) reward(p, type, b.getLocation().add(.5,.5,.5));
        else if (type == ToolType.HOE && Tag.CROPS.isTagged(b.getType()) && b.getBlockData() instanceof Ageable age && age.getAge() == age.getMaximumAge())
            reward(p, type, b.getLocation().add(.5,.5,.5));
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) public void onFish(PlayerFishEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        if (e.getState() == PlayerFishEvent.State.FISHING) {
            EquipmentSlot hand = e.getHand() == EquipmentSlot.OFF_HAND ? EquipmentSlot.OFF_HAND : EquipmentSlot.HAND;
            ItemStack held = e.getPlayer().getInventory().getItem(hand);
            if (tools.typeOf(held) == ToolType.FISHING_ROD) fishingHands.put(id, hand); else fishingHands.remove(id);
            return;
        }
        if (e.getState() == PlayerFishEvent.State.CAUGHT_FISH) {
            EquipmentSlot hand = fishingHands.remove(id);
            if (hand != null && e.getCaught() instanceof Item
                    && tools.typeOf(e.getPlayer().getInventory().getItem(hand)) == ToolType.FISHING_ROD)
                reward(e.getPlayer(), ToolType.FISHING_ROD, e.getPlayer().getLocation());
        } else if (e.getState() == PlayerFishEvent.State.REEL_IN || e.getState() == PlayerFishEvent.State.IN_GROUND
                || e.getState() == PlayerFishEvent.State.CAUGHT_ENTITY || e.getState() == PlayerFishEvent.State.FAILED_ATTEMPT) {
            fishingHands.remove(id);
        }
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) public void onWater(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null
                || e.useInteractedBlock() == Event.Result.DENY || tools.typeOf(e.getItem()) != ToolType.WATERING_CAN) return;
        Block b = e.getClickedBlock();
        if (!(b.getBlockData() instanceof Farmland farm) || farm.getMoisture() == farm.getMaximumMoisture()) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!accepting || e.isCancelled() || !e.getPlayer().isOnline() || e.useInteractedBlock() == Event.Result.DENY
                    || tools.typeOf(e.getPlayer().getInventory().getItemInMainHand()) != ToolType.WATERING_CAN) return;
            if (!(b.getBlockData() instanceof Farmland current) || current.getMoisture() == current.getMaximumMoisture()) return;
            current.setMoisture(current.getMaximumMoisture()); b.setBlockData(current);
            b.getWorld().spawnParticle(Particle.SPLASH, b.getLocation().add(.5,1,.5), 8, .3,.1,.3);
            reward(e.getPlayer(), ToolType.WATERING_CAN, b.getLocation().add(.5,1,.5));
        });
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) public void onPiston(BlockPistonExtendEvent e) { moveMarks(e.getBlocks(), e.getDirection()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) public void onPistonPull(BlockPistonRetractEvent e) { moveMarks(e.getBlocks(), e.getDirection().getOppositeFace()); }
    private void moveMarks(List<Block> blocks, org.bukkit.block.BlockFace direction) {
        List<Block> marked = blocks.stream().filter(b -> marked(b, placedKey) || marked(b, legacyPlacedKey)).toList();
        if (marked.isEmpty()) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            marked.forEach(b -> mark(b, false));
            marked.forEach(b -> mark(b.getRelative(direction), true));
        });
    }
}
