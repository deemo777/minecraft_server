package com.htk.core.cookie;

import com.htk.core.ServerCore;
import com.htk.core.common.Feature;
import com.htk.core.common.Glyphs;
import com.htk.core.common.Text;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import java.util.*;

/** 무료 얼굴 쿠키와 영구 저장되는 쿠키 NPC. */
public final class CookieFeature implements Feature, Listener, TabExecutor {
    private final ServerCore plugin;
    private final NamespacedKey npcKey, cookieKey;
    public CookieFeature(ServerCore plugin) {
        this.plugin = plugin;
        npcKey = new NamespacedKey(plugin, "cookie_npc");
        cookieKey = new NamespacedKey(plugin, "deemo_cookie");
    }
    @Override public void enable() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        PluginCommand cmd = plugin.getCommand("cookienpc");
        if (cmd != null) { cmd.setExecutor(this); cmd.setTabCompleter(this); }
    }
    @Override public void reload() { }
    @Override public void disable() { }
    private boolean isNpc(Entity e) {
        return e.getPersistentDataContainer().has(npcKey, PersistentDataType.BYTE);
    }
    public ItemStack cookie() {
        ItemStack item = new ItemStack(Material.COOKIE);
        var meta = item.getItemMeta();
        meta.displayName(Text.mm("<#eac18b><bold>deemo777 얼굴 쿠키"));
        meta.lore(List.of(Text.mm("<gray>바삭하게 구운 동그란 얼굴 쿠키"),
                Text.mm("<#f3d59c>허기 회복 10 · 빵의 2배")));
        meta.getPersistentDataContainer().set(cookieKey, PersistentDataType.BYTE, (byte) 1);
        if (plugin.resourcePackEnabled()) meta.setItemModel(Glyphs.model("deemo_cookie"));
        var food = meta.getFood();
        food.setNutrition(10);
        food.setSaturation(12.0f);
        food.setCanAlwaysEat(false);
        meta.setFood(food);
        item.setItemMeta(meta);
        return item;
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void interact(PlayerInteractEntityEvent e) {
        if (!isNpc(e.getRightClicked())) return;
        e.setCancelled(true);
        if (e.getHand() != EquipmentSlot.HAND) return;
        var p = e.getPlayer();
        // 한 번의 우클릭으로 중복 지급되지 않도록 짧은 아이템 쿨다운 사용.
        ItemStack item = cookie();
        if (p.hasCooldown(item)) return;
        if (!p.getInventory().addItem(item).isEmpty()) {
            p.sendMessage(Text.mm("<red>쿠키를 받을 인벤토리 공간이 없어요."));
            return;
        }
        p.setCooldown(item, 10);
        p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, .4f, 1.2f);
        p.sendActionBar(Text.mm("<#eac18b>얼굴 쿠키를 받았어요!"));
    }
    @EventHandler public void damage(EntityDamageEvent e) { if (isNpc(e.getEntity())) e.setCancelled(true); }
    @EventHandler public void transform(EntityTransformEvent e) { if (isNpc(e.getEntity())) e.setCancelled(true); }
    @Override public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("servercore.admin")) { sender.sendMessage(Text.mm("<red>관리자만 사용할 수 있어요.")); return true; }
        if (!(sender instanceof Player p)) { sender.sendMessage("플레이어가 실행해 주세요."); return true; }
        if (args.length == 1 && args[0].equalsIgnoreCase("create")) {
            Location at = p.getLocation();
            at.setX(at.getBlockX() + .5); at.setZ(at.getBlockZ() + .5);
            at.setYaw(at.getYaw() + 180); at.setPitch(0);
            Villager npc = at.getWorld().spawn(at, Villager.class, v -> {
                v.getPersistentDataContainer().set(npcKey, PersistentDataType.BYTE, (byte) 1);
                v.customName(Text.mm("<#eac18b><bold>쿠키 나눔이")); v.setCustomNameVisible(true);
                v.setAI(false); v.setInvulnerable(true); v.setSilent(true); v.setCollidable(false);
                v.setPersistent(true); v.setRemoveWhenFarAway(false); v.setAdult(); v.setAgeLock(true);
                var profession = Registry.VILLAGER_PROFESSION.get(NamespacedKey.minecraft("farmer"));
                if (profession != null) v.setProfession(profession);
            });
            p.sendMessage(Text.mm(npc.isValid() ? "<green>쿠키 NPC를 만들었어요. 우클릭하면 무료 쿠키를 받아요." :
                    "<red>NPC 생성이 차단됐어요. WorldGuard의 mob-spawning 설정을 확인해 주세요."));
        } else if (args.length == 1 && args[0].equalsIgnoreCase("remove")) {
            Entity npc = p.getWorld().getNearbyEntities(p.getLocation(), 5, 5, 5).stream()
                    .filter(this::isNpc).filter(e -> e.getLocation().distanceSquared(p.getLocation()) <= 25)
                    .min(Comparator.comparingDouble(e -> e.getLocation().distanceSquared(p.getLocation()))).orElse(null);
            if (npc == null) p.sendMessage(Text.mm("<red>5블록 안에 쿠키 NPC가 없어요."));
            else { npc.remove(); p.sendMessage(Text.mm("<green>쿠키 NPC를 제거했어요.")); }
        } else p.sendMessage(Text.mm("<gray>/cookienpc create | remove"));
        return true;
    }
    @Override public List<String> onTabComplete(CommandSender s, Command c, String a, String[] args) {
        return s.hasPermission("servercore.admin") && args.length == 1 ?
                List.of("create", "remove").stream().filter(v -> v.startsWith(args[0].toLowerCase(Locale.ROOT))).toList() : List.of();
    }
}
