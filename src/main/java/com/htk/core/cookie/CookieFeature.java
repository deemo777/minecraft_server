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
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.util.Transformation;
import org.joml.Vector3f;
import org.joml.Quaternionf;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import java.util.*;

/** 무료 얼굴 쿠키와 영구 저장되는 쿠키 NPC. */
public final class CookieFeature implements Feature, Listener, TabExecutor {
    private final ServerCore plugin;
    private final NamespacedKey npcKey, cookieKey, visualKey, yawKey;
    public CookieFeature(ServerCore plugin) {
        this.plugin = plugin;
        npcKey = new NamespacedKey(plugin, "cookie_npc");
        cookieKey = new NamespacedKey(plugin, "deemo_cookie");
        visualKey = new NamespacedKey(plugin, "cookie_chef_visual");
        yawKey = new NamespacedKey(plugin, "cookie_chef_yaw");
    }
    @Override public void enable() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        PluginCommand cmd = plugin.getCommand("cookienpc");
        if (cmd != null) { cmd.setExecutor(this); cmd.setTabCompleter(this); }
        for (World w : Bukkit.getWorlds())
            for (Chunk c : w.getLoadedChunks()) repair(c);
    }
    @Override public void reload() { }
    @Override public void disable() { }
    private boolean isNpc(Entity e) {
        return e.getPersistentDataContainer().has(npcKey, PersistentDataType.BYTE);
    }
    public ItemStack cookie() {
        ItemStack item = new ItemStack(Material.COOKIE);
        var meta = item.getItemMeta();
        meta.displayName(Text.mm("<#eac18b><bold>광쿠키"));
        meta.lore(List.of(Text.mm("<gray>바삭하게 구운 광태의 얼굴")));
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
        item.setAmount(16);
        int space = 0;
        for (ItemStack slot : p.getInventory().getStorageContents()) {
            if (slot == null || slot.getType().isAir()) space += item.getMaxStackSize();
            else if (slot.isSimilar(item)) space += Math.max(0, Math.min(slot.getMaxStackSize(), p.getInventory().getMaxStackSize()) - slot.getAmount());
        }
        if (space < item.getAmount()) {
            p.sendMessage(Text.mm("<red>쿠키 16개를 받을 인벤토리 공간이 필요해요."));
            return;
        }
        p.getInventory().addItem(item);
        p.setCooldown(item, 10);
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HARP, SoundCategory.PLAYERS, .35f, 1.0f);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HARP, SoundCategory.PLAYERS, .3f, 1.259921f);
        }, 4L);
        p.sendActionBar(Text.mm("<#eac18b>광쿠키 16개를 받았어요!"));
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
            Interaction npc = spawnChef(at);
            p.sendMessage(Text.mm(npc != null ? "<green>deemo777 요리사를 만들었어요. 우클릭하면 쿠키 16개를 받아요." :
                    "<red>NPC 생성이 차단됐어요. 월드 보호 설정을 확인해 주세요."));
        } else if (args.length == 1 && (args[0].equalsIgnoreCase("remove") || args[0].equalsIgnoreCase("face"))) {
            Entity npc = p.getWorld().getNearbyEntities(p.getLocation(), 5, 5, 5).stream()
                    .filter(this::isNpc).filter(e -> e.getLocation().distanceSquared(p.getLocation()) <= 25)
                    .min(Comparator.comparingDouble(e -> e.getLocation().distanceSquared(p.getLocation()))).orElse(null);
            if (npc == null) p.sendMessage(Text.mm("<red>5블록 안에 쿠키 NPC가 없어요."));
            else if (args[0].equalsIgnoreCase("face")) {
                npc.getPersistentDataContainer().set(yawKey, PersistentDataType.FLOAT, p.getLocation().getYaw() + 180f);
                ensureVisual(npc);
                p.sendMessage(Text.mm("<green>요리사의 방향을 현재 시선의 반대로 맞췄어요."));
            }
            else { removeVisual(npc); npc.remove(); p.sendMessage(Text.mm("<green>쿠키 NPC를 제거했어요.")); }
        } else p.sendMessage(Text.mm("<gray>/cookienpc create | remove | face"));
        return true;
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void interactAt(PlayerInteractAtEntityEvent e) { interact(e); }

    @EventHandler public void chunkLoad(ChunkLoadEvent e) {
        // 청크 로드 완료 후 기존 주민 전환 및 누락된 표시 엔티티 복원.
        Bukkit.getScheduler().runTask(plugin, () -> { if (e.getChunk().isLoaded()) repair(e.getChunk()); });
    }
    private Interaction spawnChef(Location at) {
        Interaction root = at.getWorld().spawn(at, Interaction.class, e -> {
            e.getPersistentDataContainer().set(npcKey, PersistentDataType.BYTE, (byte)1);
            e.getPersistentDataContainer().set(yawKey, PersistentDataType.FLOAT, at.getYaw());
            e.setInteractionWidth(.9f); e.setInteractionHeight(2.25f); e.setResponsive(true);
            e.setPersistent(true); e.setInvulnerable(true); e.setSilent(true);
        });
        if (!root.isValid()) return null;
        if (!ensureVisual(root)) { root.remove(); return null; }
        return root;
    }
    private boolean ensureVisual(Entity root) {
        String id = root.getUniqueId().toString();
        List<ItemDisplay> found = root.getWorld().getNearbyEntities(root.getLocation(), 1, 1, 1).stream()
                .filter(e -> e instanceof ItemDisplay && id.equals(e.getPersistentDataContainer().get(visualKey, PersistentDataType.STRING)))
                .map(e -> (ItemDisplay)e).toList();
        if (!found.isEmpty()) {
            applyFacing(root, found.getFirst());
            for (int i=1;i<found.size();i++) found.get(i).remove();
            return true;
        }
        Location at = root.getLocation(); at.setYaw(0); at.setPitch(0);
        ItemDisplay visual = at.getWorld().spawn(at, ItemDisplay.class, d -> {
            d.getPersistentDataContainer().set(visualKey, PersistentDataType.STRING, id);
            ItemStack model = new ItemStack(Material.PAPER);
            var meta = model.getItemMeta(); meta.setItemModel(Glyphs.model("deemo_chef")); model.setItemMeta(meta);
            d.setItemStack(model); d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setTransformation(new Transformation(new Vector3f(0,.5625f,0), new Quaternionf(), new Vector3f(1.125f), new Quaternionf()));
            d.setPersistent(true); d.setInvulnerable(true); d.setSilent(true); d.setGravity(false);
            d.setViewRange(1.5f);
        });
        if (visual.isValid()) applyFacing(root, visual);
        return visual.isValid();
    }
    private void applyFacing(Entity root, ItemDisplay visual) {
        // Interaction의 yaw 대신 PDC 방향과 Display 변환을 사용해 재시작 후에도 유지.
        float yaw = root.getPersistentDataContainer().getOrDefault(yawKey, PersistentDataType.FLOAT, 0f);
        Location at = root.getLocation(); at.setYaw(0); at.setPitch(0);
        visual.teleport(at);
        visual.setBillboard(Display.Billboard.FIXED);
        // 모델 정면은 -Z, Bukkit yaw 0은 +Z이며 양의 yaw는 -X를 향한다.
        visual.setTransformation(new Transformation(new Vector3f(0,.5625f,0),
                com.htk.core.npc.NpcFacing.rotation(yaw),
                new Vector3f(1.125f), new Quaternionf()));
    }
    private void removeVisual(Entity root) {
        String id = root.getUniqueId().toString();
        for (Entity e : root.getWorld().getNearbyEntities(root.getLocation(),1,1,1))
            if (id.equals(e.getPersistentDataContainer().get(visualKey,PersistentDataType.STRING))) e.remove();
    }
    private void repair(Chunk chunk) {
        for (Entity e : chunk.getEntities()) {
            if (isNpc(e)) {
                if (e instanceof Villager) {
                    if (spawnChef(e.getLocation()) != null) e.remove();
                    else { e.setSilent(true); plugin.getLogger().warning("쿠키 요리사 전환이 차단됐습니다: " + e.getUniqueId()); }
                } else if (e instanceof Interaction) ensureVisual(e);
            } else if (e instanceof ItemDisplay) {
                String id=e.getPersistentDataContainer().get(visualKey,PersistentDataType.STRING);
                if (id != null) {
                    try { Entity root=Bukkit.getEntity(UUID.fromString(id)); if (root==null || !isNpc(root)) e.remove(); }
                    catch (IllegalArgumentException ignored) { e.remove(); }
                }
            }
        }
    }
    @Override public List<String> onTabComplete(CommandSender s, Command c, String a, String[] args) {
        return s.hasPermission("servercore.admin") && args.length == 1 ?
                List.of("create", "remove", "face").stream().filter(v -> v.startsWith(args[0].toLowerCase(Locale.ROOT))).toList() : List.of();
    }
}
