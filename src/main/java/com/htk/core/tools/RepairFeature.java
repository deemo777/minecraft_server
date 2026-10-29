package com.htk.core.tools;

import com.htk.core.ServerCore;
import com.htk.core.common.Feature;
import com.htk.core.common.Messages;
import com.htk.core.common.Text;
import com.htk.core.economy.Economy;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import java.util.*;

/** Repair-only artisan, preserving all custom tool state except durability damage. */
public final class RepairFeature implements Feature, Listener, TabExecutor {
    private final ServerCore plugin;
    private final VillageToolFeature tools;
    private final Economy economy;
    private final NamespacedKey npcKey;
    private final Map<UUID, RepairGui> sessions = new HashMap<>();
    private BukkitTask refreshTask;
    private boolean active;

    public RepairFeature(ServerCore plugin, VillageToolFeature tools, Economy economy) {
        this.plugin = plugin; this.tools = tools; this.economy = economy;
        npcKey = new NamespacedKey(plugin, "repairer_npc");
    }
    ServerCore plugin() { return plugin; }
    @Override public void enable() {
        reload(); active = true;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        plugin.npcModels().registerLegacy(entity -> isNpc(entity) ? "repairer" : null);
        for (String name : List.of("repair", "repairnpc")) {
            var command = plugin.getCommand(name);
            if (command != null) { command.setExecutor(this); command.setTabCompleter(this); }
        }
        refreshTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> sessions.values().forEach(RepairGui::refresh), 20, 20);
    }
    @Override public void reload() {
        sessions.values().forEach(RepairGui::refresh);
    }
    @Override public void disable() {
        active = false;
        if (refreshTask != null) refreshTask.cancel();
        for (var entry : new ArrayList<>(sessions.entrySet())) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) { giveBack(player, entry.getValue()); player.closeInventory(); }
        }
        sessions.clear();
    }
    boolean valid(ItemStack item) {
        ToolType type = tools.typeOf(item);
        return type != null && item.getAmount() == 1 && tools.levelOf(item) >= 0 && tools.levelOf(item) <= tools.maxLevel()
                && item.getType() == type.materialAt(tools.levelOf(item)) && item.getItemMeta() instanceof Damageable
                && maximum(item) > 0;
    }
    int maximum(ItemStack item) {
        var meta = item.getItemMeta();
        return meta instanceof Damageable damage && damage.hasMaxDamage() ? damage.getMaxDamage() : item.getType().getMaxDurability();
    }
    long baseCost(ItemStack item) { return RepairRules.baseCost(tools.levelOf(item)); }
    long cost(ItemStack item) { return RepairRules.cost(((Damageable) item.getItemMeta()).getDamage(), maximum(item), tools.levelOf(item)); }
    boolean canAfford(UUID player, long cost) { return economy.has(player, cost); }
    private boolean isNpc(Entity entity) { return entity.getPersistentDataContainer().has(npcKey, PersistentDataType.BYTE); }
    private void open(Player player) {
        if (!active) return;
        player.closeInventory();
        var gui = new RepairGui(this, player); sessions.put(player.getUniqueId(), gui); player.openInventory(gui.inventory);
    }
    private void returnItem(Player player, ItemStack item) {
        if (item == null || item.getType().isAir()) return;
        player.getInventory().addItem(item).values().forEach(extra -> player.getWorld().dropItemNaturally(player.getLocation(), extra));
    }
    private void giveBack(Player player, RepairGui gui) {
        sessions.remove(player.getUniqueId(), gui); returnItem(player, gui.drain());
    }
    @EventHandler(priority = EventPriority.HIGHEST) public void interact(PlayerInteractEntityEvent event) {
        if (!isNpc(event.getRightClicked())) return;
        event.setCancelled(true);
        if (event.getHand() == EquipmentSlot.HAND) open(event.getPlayer());
    }
    @EventHandler(priority = EventPriority.HIGHEST) public void interactAt(PlayerInteractAtEntityEvent event) { interact(event); }
    @EventHandler(priority = EventPriority.HIGHEST) public void damage(EntityDamageEvent event) {
        if (isNpc(event.getEntity())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST) public void click(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof RepairGui gui)) return;
        if (!(event.getWhoClicked() instanceof Player player) || !gui.owner.equals(player.getUniqueId()) || gui.returned || !active) {
            event.setCancelled(true); return;
        }
        int slot = event.getRawSlot();
        if (slot >= 27 && !event.isShiftClick() && event.getClick() != ClickType.DOUBLE_CLICK
                && event.getClick() != ClickType.SWAP_OFFHAND && event.getClick() != ClickType.NUMBER_KEY
                && event.getClick() != ClickType.CREATIVE) return;
        event.setCancelled(true);
        if (slot >= RepairGui.REPAIR - 1 && slot <= RepairGui.REPAIR + 1) {
            if (event.getClick() == ClickType.LEFT || event.getClick() == ClickType.RIGHT) repair(player, gui);
            return;
        }
        if (slot >= 27 && event.isShiftClick()) {
            ItemStack source = event.getCurrentItem();
            if (!valid(source) || gui.inventory.getItem(RepairGui.TOOL) != null) return;
            gui.inventory.setItem(RepairGui.TOOL, source.clone()); event.setCurrentItem(null); gui.refresh(); return;
        }
        if (slot != RepairGui.TOOL || (event.getClick() != ClickType.LEFT && event.getClick() != ClickType.RIGHT)) return;
        ItemStack cursor = event.getCursor(), item = gui.inventory.getItem(RepairGui.TOOL);
        if (cursor == null || cursor.getType().isAir()) {
            if (item == null) return;
            event.getView().setCursor(item); gui.inventory.setItem(RepairGui.TOOL, null);
        } else if (valid(cursor)) {
            gui.inventory.setItem(RepairGui.TOOL, cursor.clone()); event.getView().setCursor(item);
        } else player.sendActionBar(Text.mm("<gray>마을 도구 1개만 넣을 수 있어요."));
        gui.refresh();
    }
    private void repair(Player player, RepairGui gui) {
        gui.refresh();
        long now = System.nanoTime();
        if (now < gui.nextAttempt) return;
        ItemStack item = gui.inventory.getItem(RepairGui.TOOL);
        if (!valid(item)) return;
        Damageable meta = (Damageable) item.getItemMeta();
        if (meta.getDamage() <= 0 || meta.isUnbreakable()) return;
        long fee = cost(item);
        if (!canAfford(player.getUniqueId(), fee)) return;
        gui.nextAttempt = now + 300_000_000L;
        ItemStack original = item.clone(), repaired = item.clone();
        Damageable fresh = (Damageable) repaired.getItemMeta(); fresh.setDamage(0); repaired.setItemMeta(fresh);
        try {
            if (!EnhancementPayment.attempt(new EnhancementPayment.Wallet() {
                public boolean withdraw(long cost) { return economy.withdraw(player.getUniqueId(), cost); }
                public void refund(long cost) { economy.deposit(player.getUniqueId(), cost); }
            }, fee, () -> gui.inventory.setItem(RepairGui.TOOL, repaired), () -> gui.inventory.setItem(RepairGui.TOOL, original))) return;
        } catch (RuntimeException failure) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "수리 반영 실패", failure);
            Messages.send(player, Text.mm("<red>수리를 완료하지 못했어요. 관리자에게 알려 주세요."));
            gui.refresh(); return;
        }
        gui.refresh();
        Messages.send(player, Text.mm("<#a9d6cc>수리 완료 <gray>· 골드 " + Economy.format(fee) + " 소비"));
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.5f, 1.5f);
    }
    @EventHandler public void drag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof RepairGui
                && event.getRawSlots().stream().anyMatch(slot -> slot < 27)) event.setCancelled(true);
    }
    @EventHandler public void close(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof RepairGui gui && event.getPlayer() instanceof Player player) giveBack(player, gui);
    }
    @EventHandler public void quit(PlayerQuitEvent event) {
        var gui = sessions.get(event.getPlayer().getUniqueId()); if (gui != null) giveBack(event.getPlayer(), gui);
    }
    @EventHandler(priority = EventPriority.HIGHEST) public void death(PlayerDeathEvent event) {
        var gui = sessions.remove(event.getEntity().getUniqueId()); if (gui == null) return;
        ItemStack item = gui.drain();
        if (item != null) { if (event.getKeepInventory()) returnItem(event.getEntity(), item); else event.getDrops().add(item); }
        event.getEntity().closeInventory();
    }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("servercore.admin")) { Messages.send(sender, Text.mm("<red>관리자만 사용할 수 있어요.")); return true; }
        if (command.getName().equals("repair")) {
            Player player = args.length == 0 ? (sender instanceof Player p ? p : null) : args.length == 1 ? Bukkit.getPlayerExact(args[0]) : null;
            if (player == null) Messages.send(sender, "접속 중인 플레이어를 지정해 주세요."); else open(player);
            return true;
        }
        if (!(sender instanceof Player player)) { Messages.send(sender, "게임 안에서 실행해 주세요."); return true; }
        if (args.length == 1 && args[0].equalsIgnoreCase("create")) {
            var at = player.getLocation(); at.setX(at.getBlockX() + 0.5); at.setZ(at.getBlockZ() + 0.5); at.setYaw(at.getYaw() + 180); at.setPitch(0);
            var npc = plugin.npcModels().spawn(at, "repairer", entity -> entity.getPersistentDataContainer().set(npcKey, PersistentDataType.BYTE, (byte) 1));
            Messages.send(player, Text.mm(npc == null ? "<red>수리 기사 생성이 차단됐어요." : "<#a9d6cc>수리 기사를 세웠어요. 우클릭으로 수리할 수 있어요."));
        } else if (args.length == 1 && List.of("remove", "face").contains(args[0].toLowerCase(Locale.ROOT))) {
            var npc = player.getWorld().getNearbyEntities(player.getLocation(), 5, 5, 5).stream().filter(this::isNpc)
                    .filter(entity -> entity.getLocation().distanceSquared(player.getLocation()) <= 25)
                    .min(Comparator.comparingDouble(entity -> entity.getLocation().distanceSquared(player.getLocation()))).orElse(null);
            if (npc == null) Messages.send(player, Text.mm("<gray>5블록 안에 수리 기사가 없어요."));
            else if (args[0].equalsIgnoreCase("remove")) { plugin.npcModels().remove(npc); Messages.send(player, "수리 기사를 제거했어요."); }
            else { plugin.npcModels().face(npc, player.getLocation().getYaw() + 180); Messages.send(player, "수리 기사 방향을 바꿨어요."); }
        } else Messages.send(sender, "/npc create|remove|face repairer");
        return true;
    }
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("servercore.admin")) return List.of();
        if (command.getName().equals("repair")) return args.length == 1 ? null : List.of();
        return args.length == 1 ? List.of("create", "remove", "face").stream().filter(value -> value.startsWith(args[0].toLowerCase(Locale.ROOT))).toList() : List.of();
    }
}
