package com.htk.core.land;

import com.htk.core.ServerCore;
import com.htk.core.common.Feature;
import com.htk.core.common.Text;
import com.htk.core.economy.Economy;
import com.htk.core.menu.MenuFeature;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.NamespacedKey;
import com.htk.core.common.Glyphs;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 내 집 땅 (동물의 숲 방식)
 *   - 집터 블록(로비의 이장님과 첫 대화를 마치면 받음)을 설치하면 그 청크가 바로 내 집 (처음 땅 값은 빚으로, 기본 0G)
 *   - 집터를 우클릭하면 집 지도 창 (넓히기·이웃). 집터는 아무도 못 부숴요
 *   - 빚은 로비의 이장님께 갚아요 (MayorFeature)
 *   - 붙어 있는 청크를 골라 한 칸씩 넓혀요 (최대 4청크). 넓히면 땅은 바로 생기고 그만큼 빚이 생겨요
 *   - 빚을 다 갚아야 다음 확장을 할 수 있어요
 *   - 이웃(허가한 사람)은 내 땅에서 전부 할 수 있고, 나머지 사람은 아무것도 못 해요
 * 설정: config.yml → land
 */
public final class LandFeature implements Feature, Listener {

    private final ServerCore plugin;
    private final Economy economy;
    private final LandStore store;
    private final Set<UUID> bypass = new HashSet<>();
    private final Map<UUID, Land> lastShown = new HashMap<>();

    // 설정
    private List<String> worlds = List.of("world");
    private int maxChunks = 4;
    private int maxRange = 2;
    private long firstCost = 0;
    private List<Long> expandCosts = List.of(1000L, 2000L, 3000L);
    private boolean protectExplosions = true;
    private org.bukkit.Material coreMaterial = org.bukkit.Material.LODESTONE;
    private boolean giveOnFirstJoin = true;
    /** 집 모형 디스플레이 표시 버전 (방향 계산을 바꾸면 올려서 예전 모형을 자동으로 새로 놓게) */
    private static final byte CORE_DISPLAY_VERSION = 2;
    private final NamespacedKey coreKey;
    private final NamespacedKey kitKey;

    public LandFeature(ServerCore plugin, Economy economy) {
        this.plugin = plugin;
        this.economy = economy;
        this.store = new LandStore(plugin.getDataFolder(), plugin.getLogger());
        this.coreKey = new NamespacedKey(plugin, "land_core");
        this.kitKey = new NamespacedKey(plugin, "land_kit_given");
    }

    @Override
    public void enable() {
        loadConfig();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getServer().getPluginManager().registerEvents(new LandProtection(this), plugin);
        PluginCommand cmd = plugin.getCommand("land");
        if (cmd != null) {
            LandCommand c = new LandCommand(this);
            cmd.setExecutor(c);
            cmd.setTabCompleter(c);
        }
    }

    @Override
    public void reload() {
        loadConfig();
    }

    @Override
    public void disable() {
        store.save();
    }

    private void loadConfig() {
        var cfg = plugin.getConfig();
        worlds = cfg.isList("land.worlds") ? cfg.getStringList("land.worlds") : List.of("world");
        maxChunks = Math.max(1, cfg.getInt("land.max-chunks", 4));
        maxRange = Math.max(1, Math.min(LandGui.RANGE, cfg.getInt("land.max-range", 2)));
        firstCost = Math.max(0, cfg.getLong("land.first-cost", 0));
        if (cfg.isList("land.expand-costs")) {
            List<Long> l = new ArrayList<>();
            for (Object o : cfg.getList("land.expand-costs")) {
                try {
                    l.add(Long.parseLong(String.valueOf(o).trim()));
                } catch (NumberFormatException ignored) { }
            }
            if (!l.isEmpty()) expandCosts = l;
        }
        protectExplosions = cfg.getBoolean("land.protect-from-explosions", true);
        giveOnFirstJoin = cfg.getBoolean("land.give-core-on-first-join", true);
        org.bukkit.Material m = org.bukkit.Material.matchMaterial(cfg.getString("land.core-block", "LODESTONE"));
        if (m != null && m.isBlock()) coreMaterial = m;
        else plugin.getLogger().warning("land.core-block 이 블록 이름이 아니에요. 자석석(LODESTONE)을 써요.");
    }

    String msg(String key, String def) {
        return plugin.getConfig().getString("land.messages." + key, def);
    }

    void send(Player p, String key, String def) {
        p.sendMessage(Text.mm(msg(key, def)));
    }

    // ---------------- 다른 기능에서 쓰는 것 ----------------

    /** 집 지도 창 열기 */
    public void openGui(Player p) {
        if (store.of(p.getUniqueId()) == null) {
            send(p, "no-land", "<gray>아직 집이 없어요. <#f3c060>집터</#f3c060>를 설치하면 거기가 내 집이 돼요. <dark_gray>(집터는 로비의 이장님께)");
            return;
        }
        new LandGui(this, p).open(p);
    }

    /** 메뉴의 "집으로" 버튼: 집이 있으면 집으로, 없으면 집 정하기 창 */
    public void homeButton(Player p) {
        if (store.of(p.getUniqueId()) != null) goHome(p);
        else {
            p.closeInventory();
            send(p, "no-land", "<gray>아직 집이 없어요. <#f3c060>집터</#f3c060>를 설치하면 거기가 내 집이 돼요. <dark_gray>(집터는 로비의 이장님께)");
        }
    }

    // ---------------- 조회 ----------------

    public ServerCore plugin() { return plugin; }
    Economy economy() { return economy; }
    LandStore store() { return store; }
    int maxChunks() { return maxChunks; }
    boolean protectExplosions() { return protectExplosions; }

    public Land landOf(UUID owner) {
        return store.of(owner);
    }

    public Land landAt(Location l) {
        return store.at(l);
    }

    boolean allowedWorld(World w) {
        return w != null && worlds.contains(w.getName());
    }

    /** 이 플레이어가 그 자리에서 블록·상자·동물 등을 건드려도 되는지 */
    boolean canBuild(Player p, Land land) {
        return land == null || land.trusts(p.getUniqueId()) || bypass.contains(p.getUniqueId());
    }

    boolean toggleBypass(Player p) {
        if (!bypass.remove(p.getUniqueId())) {
            bypass.add(p.getUniqueId());
            return true;
        }
        return false;
    }

    /** 다음 확장 때 생기는 빚 (더 못 넓히면 -1) */
    long nextExpandCost(Land land) {
        int n = land.size();                 // 지금 청크 수 = 몇 번째 확장인지 (1 → 첫 확장)
        if (n >= maxChunks) return -1;
        return expandCosts.get(Math.min(n - 1, expandCosts.size() - 1));
    }

    long firstCost() { return firstCost; }

    /** 내 땅에 붙어 있어서 넓힐 수 있는 청크인지 */
    boolean canExpandTo(Land land, int cx, int cz) {
        if (land.has(cx, cz) || store.at(land.world, cx, cz) != null) return false;
        long core = land.firstChunk();             // 집터 청크에서 상하좌우 maxRange 칸까지만
        if (Math.abs(cx - Land.cx(core)) > maxRange || Math.abs(cz - Land.cz(core)) > maxRange) return false;
        return land.has(cx + 1, cz) || land.has(cx - 1, cz) || land.has(cx, cz + 1) || land.has(cx, cz - 1);
    }

    // ---------------- 하는 일 ----------------

    /** 집터 블록을 놓은 청크를 내 집으로 (놓기 전에 불러요: false면 놓기를 막아요) */
    boolean claimWithCore(Player p, org.bukkit.block.Block core) {
        if (store.of(p.getUniqueId()) != null) {
            send(p, "already-have", "<gray>이미 집이 있어요. 집은 한 사람에 하나예요.");
            return false;
        }
        Location l = core.getLocation();
        if (!allowedWorld(l.getWorld())) {
            send(p, "wrong-world", "<red>이 월드에는 집을 지을 수 없어요.");
            return false;
        }
        Land other = store.at(l);
        if (other != null) {
            p.sendMessage(Text.mm(msg("taken", "<red>%owner%님의 땅이에요.").replace("%owner%", other.ownerName)));
            return false;
        }
        int cx = core.getX() >> 4, cz = core.getZ() >> 4;
        // 집 스폰: 집터 바로 위 (플레이어가 옆 청크에 서서 놓았을 수도 있어서)
        Location home = core.getLocation().add(0.5, 1, 0.5);
        home.setYaw(p.getLocation().getYaw());
        Land land = store.create(p.getUniqueId(), p.getName(), l.getWorld().getName(), cx, cz, home);
        land.hasCore = true;
        land.coreX = core.getX();
        land.coreY = core.getY();
        land.coreZ = core.getZ();
        land.facing = horizontal(p.getFacing());
        land.debt = firstCost;
        store.save();
        // 놓인 블록을 다음 틱에 "보이지 않는 단단한 블록 + 집 모형"으로 바꿔요
        Bukkit.getScheduler().runTask(plugin, () -> placeCoreVisual(land));
        p.sendMessage(Text.mm(msg("claimed", "<#7acb52>🏠 여기를 내 집으로 정했어요! <gray>집터를 우클릭하면 집 지도가 열려요.")));
        if (land.debt > 0) sendDebt(p, land);
        showBorder(p, land);
        return true;
    }

    /** 붙어 있는 청크 하나 더 (빚이 생겨요) */
    boolean expand(Player p, int cx, int cz) {
        Land land = store.of(p.getUniqueId());
        if (land == null) return false;
        if (land.debt > 0) {
            p.sendMessage(Text.mm(msg("pay-first", "<red>빚 %debt%을 먼저 갚아야 넓힐 수 있어요.")
                    .replace("%debt%", Economy.format(land.debt))));
            return false;
        }
        long cost = nextExpandCost(land);
        if (cost < 0) {
            p.sendMessage(Text.mm(msg("max", "<gray>더 이상 넓힐 수 없어요. (최대 %max%청크)")
                    .replace("%max%", String.valueOf(maxChunks))));
            return false;
        }
        if (!canExpandTo(land, cx, cz)) {
            send(p, "not-adjacent", "<red>내 땅에 붙어 있고, 집터에서 상하좌우 2칸 안의 빈 청크만 넓힐 수 있어요.");
            return false;
        }
        store.addChunk(land, cx, cz);
        land.debt += cost;
        store.save();
        p.sendMessage(Text.mm(msg("expanded", "<#7acb52>🏠 땅을 넓혔어요! <gray>(%size%/%max%청크)")
                .replace("%size%", String.valueOf(land.size())).replace("%max%", String.valueOf(maxChunks))));
        sendDebt(p, land);
        showBorder(p, land);
        return true;
    }

    /** 남은 빚 (집이 없으면 0) */
    public long debtOf(UUID owner) {
        Land land = store.of(owner);
        return land == null ? 0 : Math.max(0, land.debt);
    }

    /** 이장님 창에서: 가진 골드로 빚 갚기 (amount <= 0 이면 갚을 수 있는 만큼) */
    public void payDebt(Player p, long amount) {
        Land land = store.of(p.getUniqueId());
        if (land == null || land.debt <= 0) {
            send(p, "no-debt", "<gray>갚을 빚이 없어요.");
            return;
        }
        long want = amount <= 0 ? land.debt : Math.min(amount, land.debt);
        long pay = Math.min(want, economy.balance(p.getUniqueId()));
        if (pay <= 0 || !economy.withdraw(p.getUniqueId(), pay)) {
            send(p, "no-gold", "<red>골드가 없어요.");
            return;
        }
        land.debt -= pay;
        store.save();
        if (land.debt <= 0) {
            p.sendMessage(Text.mm(msg("paid-off", "<#ffd34d>🎉 빚을 다 갚았어요! <gray>(%paid% 냄) 이제 땅을 더 넓힐 수 있어요.")
                    .replace("%paid%", Economy.format(pay))));
        } else {
            p.sendMessage(Text.mm(msg("paid", "<gray>%paid%을 갚았어요. 남은 빚 <#ffd34d>%debt%")
                    .replace("%paid%", Economy.format(pay)).replace("%debt%", Economy.format(land.debt))));
        }
    }

    void sendDebt(Player p, Land land) {
        p.sendMessage(Text.mm(msg("debt", "<gray>빚: <#ffd34d>%debt%</#ffd34d> <dark_gray>(로비의 이장님께 갚아요)")
                .replace("%debt%", Economy.format(land.debt))));
    }

    boolean setHome(Player p) {
        Land land = store.of(p.getUniqueId());
        if (land == null) {
            send(p, "no-land", "<gray>아직 집이 없어요. <#f3c060>집터</#f3c060>를 설치하면 거기가 내 집이 돼요. <dark_gray>(집터는 로비의 이장님께)");
            return false;
        }
        if (store.at(p.getLocation()) != land) {
            send(p, "sethome-outside", "<red>내 땅 안에서만 집 스폰을 정할 수 있어요.");
            return false;
        }
        land.setHome(p.getLocation());
        store.save();
        send(p, "sethome", "<#7acb52>여기를 집 스폰으로 정했어요.");
        return true;
    }

    /** 메뉴 "집으로" 처럼 3초 센 뒤 집으로 */
    void goHome(Player p) {
        Land land = store.of(p.getUniqueId());
        if (land == null) {
            send(p, "no-land", "<gray>아직 집이 없어요. <#f3c060>집터</#f3c060>를 설치하면 거기가 내 집이 돼요. <dark_gray>(집터는 로비의 이장님께)");
            return;
        }
        Location home = land.home();
        if (home == null) {
            send(p, "world-missing", "<red>집이 있는 월드가 지금 꺼져 있어요.");
            return;
        }
        p.closeInventory();
        String title = msg("home-title", "<#6aa8e0><bold>집");
        MenuFeature menu = plugin.feature(MenuFeature.class);
        if (menu != null) menu.countdownTeleport(p, home, title, "");
        else p.teleportAsync(home);
    }

    boolean trust(Player p, OfflinePlayer target) {
        Land land = store.of(p.getUniqueId());
        if (land == null) {
            send(p, "no-land", "<gray>아직 집이 없어요. <#f3c060>집터</#f3c060>를 설치하면 거기가 내 집이 돼요. <dark_gray>(집터는 로비의 이장님께)");
            return false;
        }
        if (target.getUniqueId().equals(p.getUniqueId())) return false;
        land.members.add(target.getUniqueId());
        store.save();
        p.sendMessage(Text.mm(msg("trusted", "<#7acb52>%name%님을 이웃으로 허가했어요. <gray>(내 땅에서 전부 할 수 있어요)")
                .replace("%name%", String.valueOf(target.getName()))));
        Player online = target.getPlayer();
        if (online != null) online.sendMessage(Text.mm(msg("trusted-you", "<#7acb52>%owner%님이 집 이웃으로 허가해 줬어요.")
                .replace("%owner%", p.getName())));
        return true;
    }

    boolean untrust(Player p, UUID target, String name) {
        Land land = store.of(p.getUniqueId());
        if (land == null || !land.members.remove(target)) return false;
        store.save();
        p.sendMessage(Text.mm(msg("untrusted", "<gray>%name%님의 허가를 없앴어요.").replace("%name%", String.valueOf(name))));
        return true;
    }

    void abandon(Player p) {
        Land land = store.of(p.getUniqueId());
        if (land == null) {
            send(p, "no-land", "<gray>아직 집이 없어요.");
            return;
        }
        removeCoreBlock(land);
        store.remove(land);
        giveCore(p);
        send(p, "abandoned", "<gray>집 땅을 포기했어요. 집터를 돌려받았어요. (남은 빚도 없어졌어요)");
    }

    void adminDelete(org.bukkit.command.CommandSender s, OfflinePlayer target) {
        Land land = store.of(target.getUniqueId());
        if (land == null) {
            s.sendMessage(Text.mm("<red>그 플레이어는 땅이 없어요."));
            return;
        }
        removeCoreBlock(land);
        store.remove(land);
        s.sendMessage(Text.mm("<green>" + land.ownerName + "의 땅을 지웠어요. (집터 블록도 없앰)"));
    }

    // ---------------- 집터 블록 ----------------

    /** 집터 아이템 (설치하면 그 청크가 내 집) */
    public ItemStack coreItem() {
        ItemStack it = new ItemStack(coreMaterial);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.displayName(Text.mm(msg("core-name", "<#f3c060><bold>🏠 집터")).decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            for (String l : plugin.getConfig().isList("land.messages.core-lore")
                    ? plugin.getConfig().getStringList("land.messages.core-lore")
                    : List.of("<gray>설치한 곳의 청크가 내 집이 돼요.", "<gray>설치한 뒤 우클릭하면 집 지도가 열려요.")) {
                lore.add(Text.mm(l).decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lore);
            if (plugin.resourcePackEnabled()) meta.setItemModel(Glyphs.model("house_core"));
            else meta.setEnchantmentGlintOverride(true);
            meta.getPersistentDataContainer().set(coreKey, PersistentDataType.BYTE, (byte) 1);
            it.setItemMeta(meta);
        }
        return it;
    }

    boolean isCoreItem(ItemStack it) {
        if (it == null || it.getType().isAir() || !it.hasItemMeta()) return false;
        return it.getItemMeta().getPersistentDataContainer().has(coreKey, PersistentDataType.BYTE);
    }

    /** 가방에 집터가 있는지 */
    public boolean hasCoreItem(Player p) {
        for (ItemStack it : p.getInventory().getContents()) if (isCoreItem(it)) return true;
        return false;
    }

    /** 집이 없는 사람에게 집터 주기 (가방이 꽉 차면 발밑에) */
    public void giveCore(Player p) {
        var left = p.getInventory().addItem(coreItem());
        for (ItemStack it : left.values()) p.getWorld().dropItemNaturally(p.getLocation(), it);
    }

    /** 땅에 있는 집터 블록 */
    Land coreAt(org.bukkit.block.Block b) {
        Land land = store.at(b);
        return land != null && land.isCore(b) ? land : null;
    }

    private void removeCoreBlock(Land land) {
        World w = Bukkit.getWorld(land.world);
        if (w == null || !land.hasCore) return;
        org.bukkit.block.Block b = w.getBlockAt(land.coreX, land.coreY, land.coreZ);
        if (b.getType() == org.bukkit.Material.BARRIER || b.getType() == coreMaterial) b.setType(org.bukkit.Material.AIR);
        removeCoreDisplays(w, b);
    }

    // ---- 집터 모형: 보이지 않는 방벽(barrier) 블록 + 그 자리에 집 모형(아이템 디스플레이) ----
    //   방벽이라 서바이벌에서 못 부수고, 폭발·피스톤에도 안 움직여요. 모양은 리소스팩 servercore:house_core

    private static org.bukkit.block.BlockFace horizontal(org.bukkit.block.BlockFace f) {
        return switch (f) {
            case EAST, SOUTH, WEST -> f;
            default -> org.bukkit.block.BlockFace.NORTH;
        };
    }

    private org.bukkit.Location coreCenter(World w, Land land) {
        return new org.bukkit.Location(w, land.coreX + 0.5, land.coreY + 0.5, land.coreZ + 0.5);
    }

    private void removeCoreDisplays(World w, org.bukkit.block.Block b) {
        org.bukkit.Location c = b.getLocation().add(0.5, 0.5, 0.5);
        for (org.bukkit.entity.Entity en : w.getNearbyEntities(c, 0.7, 0.7, 0.7)) {
            if (en instanceof ItemDisplay d && d.getPersistentDataContainer().has(coreKey, PersistentDataType.BYTE)) d.remove();
        }
    }

    /** 집터 자리에 방벽 + 집 모형 (이미 있으면 새로) */
    void placeCoreVisual(Land land) {
        World w = Bukkit.getWorld(land.world);
        if (w == null || !land.hasCore) return;
        org.bukkit.block.Block b = w.getBlockAt(land.coreX, land.coreY, land.coreZ);
        b.setType(org.bukkit.Material.BARRIER);
        removeCoreDisplays(w, b);
        // 집 모형의 문(+z 면)이 놓은 사람 쪽(facing 반대)을 보게 돌려요.
        // 아이템 디스플레이는 그릴 때 모델을 스스로 180도 돌려서, 그만큼 반대로 돌려 둬요
        float angle = switch (land.facing.getOppositeFace()) {
            case EAST -> (float) Math.toRadians(90);
            case NORTH -> (float) Math.toRadians(180);
            case WEST -> (float) Math.toRadians(-90);
            default -> 0f;                                   // SOUTH
        } + (float) Math.PI;
        ItemStack model = new ItemStack(coreMaterial);
        ItemMeta meta = model.getItemMeta();
        if (meta != null) {
            meta.setItemModel(Glyphs.model("house_core"));
            model.setItemMeta(meta);
        }
        w.spawn(coreCenter(w, land), ItemDisplay.class, d -> {
            d.setItemStack(model);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setRotation(0f, 0f);
            d.setTransformation(new Transformation(new Vector3f(), new Quaternionf().rotateY(angle),
                    new Vector3f(1f, 1f, 1f), new Quaternionf()));
            d.setPersistent(true);
            d.getPersistentDataContainer().set(coreKey, PersistentDataType.BYTE, CORE_DISPLAY_VERSION);
        });
    }

    /** 집터가 방벽+모형이 아니면 고치기 (예전 자석석 집터, 모형이 지워진 경우) */
    void ensureCoreVisual(Land land) {
        World w = Bukkit.getWorld(land.world);
        if (w == null || !land.hasCore) return;
        org.bukkit.block.Block b = w.getBlockAt(land.coreX, land.coreY, land.coreZ);
        boolean hasDisplay = false;
        for (org.bukkit.entity.Entity en : w.getNearbyEntities(b.getLocation().add(0.5, 0.5, 0.5), 0.7, 0.7, 0.7)) {
            if (en instanceof ItemDisplay d && d.getPersistentDataContainer().has(coreKey, PersistentDataType.BYTE)) {
                // 예전 방향(3.8.0~3.9.1, 앞뒤가 반대)으로 놓인 모형이면 새로 놓아요
                Byte v = d.getPersistentDataContainer().get(coreKey, PersistentDataType.BYTE);
                hasDisplay = v != null && v == CORE_DISPLAY_VERSION;
                break;
            }
        }
        if (b.getType() != org.bukkit.Material.BARRIER || !hasDisplay) placeCoreVisual(land);
    }

    /** 집터 설치 = 집 정하기 */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlaceCore(BlockPlaceEvent e) {
        if (!isCoreItem(e.getItemInHand())) return;
        if (!claimWithCore(e.getPlayer(), e.getBlockPlaced())) e.setCancelled(true);
    }

    /** 집터 우클릭 = 집 지도 (주인), 다른 사람은 누구 집인지만 */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onUseCore(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null) return;
        Land land = coreAt(e.getClickedBlock());
        if (land == null) return;
        e.setCancelled(true);
        if (e.getHand() != EquipmentSlot.HAND) return;
        Player p = e.getPlayer();
        if (land.owner.equals(p.getUniqueId())) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                ensureCoreVisual(land);
                if (p.isOnline()) openGui(p);
            });
        } else {
            p.sendActionBar(Text.mm(msg("core-other", "<#f3c060>🏠 %owner%님의 집터").replace("%owner%", land.ownerName)));
        }
    }

    /** 집터는 아무도 못 부숴요 (포기는 /land abandon confirm) */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBreakCore(BlockBreakEvent e) {
        Land land = coreAt(e.getBlock());
        if (land == null) return;
        e.setCancelled(true);
        if (land.owner.equals(e.getPlayer().getUniqueId()))
            send(e.getPlayer(), "core-break", "<gray>집터는 부술 수 없어요. 집을 포기하려면 <white>/land abandon confirm");
    }

    /** 피스톤으로 집터를 밀거나 당기지 못하게 */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPistonCore(BlockPistonExtendEvent e) {
        for (org.bukkit.block.Block b : e.getBlocks()) if (coreAt(b) != null) { e.setCancelled(true); return; }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPistonCore(BlockPistonRetractEvent e) {
        for (org.bukkit.block.Block b : e.getBlocks()) if (coreAt(b) != null) { e.setCancelled(true); return; }
    }

    /** 처음 접속하면 집터 하나 (이장님 기능이 켜져 있으면 이장님이 줘요) */
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Land own = store.of(p.getUniqueId());
        if (own != null) Bukkit.getScheduler().runTaskLater(plugin, () -> ensureCoreVisual(own), 40L);
        if (!giveOnFirstJoin || store.of(p.getUniqueId()) != null) return;
        if (plugin.feature(com.htk.core.mayor.MayorFeature.class) != null) return;
        var pdc = p.getPersistentDataContainer();
        if (pdc.has(kitKey, PersistentDataType.BYTE)) return;
        pdc.set(kitKey, PersistentDataType.BYTE, (byte) 1);
        giveCore(p);
        send(p, "core-given", "<#f3c060>🏠 <gray>집터를 받았어요! 마음에 드는 곳에 설치하면 거기가 내 집이 돼요.");
    }

    /** /land kit: 집이 없으면 집터 다시 받기 (이장님과 첫 대화를 마친 사람만) */
    public void kit(Player p) {
        if (store.of(p.getUniqueId()) != null) {
            send(p, "already-have", "<gray>이미 집이 있어요. 집은 한 사람에 하나예요.");
            return;
        }
        com.htk.core.mayor.MayorFeature mayor = plugin.feature(com.htk.core.mayor.MayorFeature.class);
        if (mayor != null && !mayor.introDone(p)) {
            send(p, "meet-mayor", "<gray>로비의 <#f3c060>이장님</#f3c060>께 먼저 인사해 주세요. 집터를 주실 거예요.");
            return;
        }
        for (ItemStack it : p.getInventory().getContents()) {
            if (isCoreItem(it)) {
                send(p, "core-already", "<gray>가방에 집터가 이미 있어요.");
                return;
            }
        }
        giveCore(p);
        send(p, "core-given", "<#f3c060>🏠 <gray>집터를 받았어요! 마음에 드는 곳에 설치하면 거기가 내 집이 돼요.");
    }

    // ---------------- 경계 보여주기 / 들어가면 알림 ----------------

    /** 내 땅 테두리를 몇 초 동안 반짝이로 */
    void showBorder(Player p, Land land) {
        World w = Bukkit.getWorld(land.world);
        if (w == null || !p.getWorld().equals(w)) return;
        int[] runs = {0};
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            if (!p.isOnline() || runs[0]++ >= 10) {
                task.cancel();
                return;
            }
            double y = p.getLocation().getY() + 1;
            for (long k : land.chunks) {
                int x0 = Land.cx(k) << 4, z0 = Land.cz(k) << 4;
                int cx = Land.cx(k), cz = Land.cz(k);
                for (int i = 0; i <= 16; i += 2) {
                    if (!land.has(cx, cz - 1)) p.spawnParticle(Particle.HAPPY_VILLAGER, x0 + i, y, z0, 1, 0, 0.3, 0, 0);
                    if (!land.has(cx, cz + 1)) p.spawnParticle(Particle.HAPPY_VILLAGER, x0 + i, y, z0 + 16, 1, 0, 0.3, 0, 0);
                    if (!land.has(cx - 1, cz)) p.spawnParticle(Particle.HAPPY_VILLAGER, x0, y, z0 + i, 1, 0, 0.3, 0, 0);
                    if (!land.has(cx + 1, cz)) p.spawnParticle(Particle.HAPPY_VILLAGER, x0 + 16, y, z0 + i, 1, 0, 0.3, 0, 0);
                }
            }
        }, 0L, 10L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Location to = e.getTo();
        if ((e.getFrom().getBlockX() >> 4) == (to.getBlockX() >> 4)
                && (e.getFrom().getBlockZ() >> 4) == (to.getBlockZ() >> 4)
                && e.getFrom().getWorld() == to.getWorld()) return;
        enter(e.getPlayer(), to);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        enter(e.getPlayer(), e.getTo());
    }

    private void enter(Player p, Location to) {
        Land now = store.at(to);
        Land before = lastShown.get(p.getUniqueId());
        if (now == before) return;
        if (now == null) lastShown.remove(p.getUniqueId());
        else lastShown.put(p.getUniqueId(), now);
        if (now != null) {
            String text = now.owner.equals(p.getUniqueId())
                    ? msg("enter-own", "<#7acb52>🏠 내 집")
                    : msg("enter", "<#f3c060>🏠 %owner%님의 집").replace("%owner%", now.ownerName);
            p.sendActionBar(Text.mm(text));
        } else if (before != null) {
            p.sendActionBar(Text.mm(msg("leave", "<gray>야생")));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        lastShown.remove(e.getPlayer().getUniqueId());
        bypass.remove(e.getPlayer().getUniqueId());
        Land land = store.of(e.getPlayer().getUniqueId());
        if (land != null && !land.ownerName.equals(e.getPlayer().getName())) {   // 닉네임이 바뀌었으면
            land.ownerName = e.getPlayer().getName();
            store.save();
        }
    }
}
