package com.htk.core.dogam;

import com.htk.core.ServerCore;
import com.htk.core.common.Feature;
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

/** 도감 기능: 대화창 열기 → 칸 클릭 → 인벤토리에서 필요한 개수 가져가고 등록 → 보상 */
public final class DogamFeature implements Feature, Listener {

    private final ServerCore plugin;
    private YamlConfiguration config;
    private CollectionRegistry registry;
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
            player.sendMessage(Text.mm(plugin.msg("no-permission")));
            return;
        }
        player.showDialog(new DogamDialog(this, player, page).build());
    }

    // ---------- 칸 클릭 ----------

    /** 도감 칸을 눌렀을 때. 처리 후 같은 페이지를 다시 띄워요. */
    public void click(Player player, CollectionRegistry.Entry entry, int page) {
        PlayerDataStore.PlayerData data = store.get(player.getUniqueId());
        if (data.has(entry.id())) {
            player.sendMessage(Text.mm(msg("already-registered").replace("%entry%", entry.name())));
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 0.8f);
        } else {
            int have = count(player, entry.material());
            if (have >= entry.required()) {
                take(player, entry.material(), entry.required());
                register(player, entry);
            } else {
                player.sendMessage(Text.mm(msg("not-enough")
                        .replace("%entry%", entry.name())
                        .replace("%required%", String.valueOf(entry.required()))
                        .replace("%have%", String.valueOf(have))));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 1.0f);
            }
        }
        open(player, page);
    }

    private void register(Player player, CollectionRegistry.Entry entry) {
        PlayerDataStore.PlayerData data = store.get(player.getUniqueId());
        if (!data.add(entry.id())) return;

        int found = foundTotal(player), total = registry.all().size();
        player.sendMessage(Text.mm(msg("registered")
                .replace("%entry%", entry.name())
                .replace("%found%", String.valueOf(found))
                .replace("%total%", String.valueOf(total))));
        player.showTitle(Title.title(
                Text.mm("<#cdbf8e>도감 등록!"), Text.mm(entry.name()),
                Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(1200), Duration.ofMillis(300))));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.4f);
        runRewards(player, entry.rewards());

        CollectionRegistry.Category cat = registry.category(entry.categoryId());
        if (cat != null && !cat.entries().isEmpty() && foundIn(cat, data) == cat.entries().size()) {
            player.sendMessage(Text.mm(msg("category-complete").replace("%category%", cat.name())));
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            runRewards(player, cat.completeRewards());
        }
    }

    private void runRewards(Player player, List<String> commands) {
        for (String c : commands) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), c.replace("%player%", player.getName()));
        }
    }

    // ---------- 인벤토리 (가방 36칸만, 갑옷·왼손 제외) ----------

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
