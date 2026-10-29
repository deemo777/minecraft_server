package com.htk.core.dogam;

import com.htk.core.common.Messages;

import com.htk.core.ServerCore;
import com.htk.core.common.Feature;
import com.htk.core.common.Placeholders;
import com.htk.core.common.Text;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.time.Duration;
import java.util.List;

/** 도감 기능: 책 화면 열기 → 칸 클릭 → 가방에서 필요한 개수 가져가고 등록 → 보상 */
public final class DogamFeature implements Feature, Listener {

    private final ServerCore plugin;
    private YamlConfiguration config;
    private CollectionRegistry registry;
    private java.util.Map<org.bukkit.Material, Integer> creativeOrder;   // 한 번만 계산
    private PlayerDataStore store;

    public DogamFeature(ServerCore plugin) {
        this.plugin = plugin;
    }

    // ---------- 생명주기 ----------

    @Override
    public void enable() {
        loadConfig();
        store = new PlayerDataStore(plugin);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        PluginCommand cmd = plugin.getCommand("dogam");
        if (cmd != null) {
            DogamCommand executor = new DogamCommand(this);
            cmd.setExecutor(executor);
            cmd.setTabCompleter(executor);
        }

        // 메뉴 등에서 쓰는 치환 문구
        Placeholders.register("dogam_found", p -> String.valueOf(foundTotal(p)));
        Placeholders.register("dogam_total", p -> String.valueOf(registry.all().size()));

        for (Player p : Bukkit.getOnlinePlayers()) store.get(p.getUniqueId());
        Bukkit.getScheduler().runTaskTimer(plugin, store::saveAllDirty, 20L * 300, 20L * 300); // 5분마다 저장
    }

    @Override
    public void reload() {
        loadConfig();
    }

    @Override
    public void disable() {
        if (store != null) store.saveAll();
    }

    private void loadConfig() {
        config = plugin.loadYaml("dogam.yml");
        registry = CollectionRegistry.load(config, plugin.getLogger());
        // order: creative → 크리에이티브 인벤토리 순서, file → dogam.yml 에 적힌 순서
        if (!"file".equalsIgnoreCase(config.getString("order", "creative"))) {
            if (creativeOrder == null) creativeOrder = CreativeOrder.load(plugin);
            registry.sortBy(creativeOrder);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        store.get(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        store.unload(event.getPlayer().getUniqueId());
    }

    // ---------- 열기 ----------

    public void open(Player player, int page) {
        if (!player.hasPermission("servercore.dogam")) {
            Messages.send(player,Text.mm(plugin.msg("no-permission")));
            return;
        }
        new DogamGui(this, player, page).open(player);
    }

    /** 클릭 처리 중에는 창을 바로 바꾸면 꼬일 수 있어서 다음 틱에 */
    public void openLater(Player player, int page) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) open(player, page);
        });
    }

    // ---------- 칸 클릭 ----------

    public void click(Player player, CollectionRegistry.Entry entry, int page) {
        PlayerDataStore.PlayerData data = store.get(player.getUniqueId());
        if (data.has(entry.id())) {
            Messages.send(player,Text.mm(msg("already-registered").replace("%entry%", entry.name())));
            return;
        }
        int have = count(player, entry.material());
        if (have < entry.required()) {
            Messages.send(player,Text.mm(msg("not-enough")
                    .replace("%entry%", entry.name())
                    .replace("%required%", String.valueOf(entry.required()))
                    .replace("%have%", String.valueOf(have))));
            return;
        }
        take(player, entry.material(), entry.required());
        register(player, entry);
        openLater(player, page); // 진행도·체크가 바뀌었으니 같은 페이지 새로 그리기
    }

    private void register(Player player, CollectionRegistry.Entry entry) {
        PlayerDataStore.PlayerData data = store.get(player.getUniqueId());
        if (!data.add(entry.id())) return;

        int found = foundTotal(player), total = registry.all().size();
        Messages.send(player,Text.mm(msg("registered")
                .replace("%entry%", entry.name())
                .replace("%found%", String.valueOf(found))
                .replace("%total%", String.valueOf(total))));
        player.showTitle(Title.title(
                Text.mm("<#f3c060>도감 등록!"), Text.mm(entry.name()),
                Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(1200), Duration.ofMillis(300))));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.4f);
        runRewards(player, entry.rewards());

        CollectionRegistry.Category cat = registry.category(entry.categoryId());
        if (cat != null && !cat.entries().isEmpty() && foundIn(cat, data) == cat.entries().size()) {
            Messages.send(player,Text.mm(msg("category-complete").replace("%category%", cat.name())));
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            runRewards(player, cat.completeRewards());
        }
    }

    private void runRewards(Player player, List<String> commands) {
        for (String c : commands) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), c.replace("%player%", player.getName()));
        }
    }

    // ---------- 가방 (36칸만, 갑옷·왼손 제외) ----------

    public int count(Player player, Material material) {
        int n = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item != null && item.getType() == material) n += item.getAmount();
        }
        return n;
    }

    private void take(Player player, Material material, int amount) {
        PlayerInventory inv = player.getInventory();
        ItemStack[] contents = inv.getStorageContents();
        int left = amount;
        for (int i = 0; i < contents.length && left > 0; i++) {
            ItemStack item = contents[i];
            if (item == null || item.getType() != material) continue;
            int use = Math.min(left, item.getAmount());
            left -= use;
            if (item.getAmount() == use) contents[i] = null;
            else item.setAmount(item.getAmount() - use);
        }
        inv.setStorageContents(contents);
    }

    // ---------- 계산/도우미 ----------

    public int foundIn(CollectionRegistry.Category cat, PlayerDataStore.PlayerData data) {
        int n = 0;
        for (CollectionRegistry.Entry e : cat.entries()) if (data.has(e.id())) n++;
        return n;
    }

    public int foundTotal(Player player) {
        PlayerDataStore.PlayerData data = store.get(player.getUniqueId());
        int n = 0;
        for (CollectionRegistry.Entry e : registry.all()) if (data.has(e.id())) n++;
        return n;
    }

    public String msg(String key) {
        return config.getString("messages." + key, key);
    }

    public YamlConfiguration config() { return config; }
    public CollectionRegistry registry() { return registry; }
    public PlayerDataStore store() { return store; }
    public ServerCore plugin() { return plugin; }
}
