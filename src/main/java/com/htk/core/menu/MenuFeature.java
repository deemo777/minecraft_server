package com.htk.core.menu;

import com.htk.core.common.Messages;

import com.htk.core.ServerCore;
import com.htk.core.common.Feature;
import com.htk.core.common.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.command.PluginCommand;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** 마을 메뉴: Shift + F, /menu → MenuGui */
public final class MenuFeature implements Feature, Listener {

    private final ServerCore plugin;
    private MenuConfig config;

    public MenuFeature(ServerCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public void enable() {
        loadConfig();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        PluginCommand cmd = plugin.getCommand("menu");
        if (cmd != null) {
            cmd.setExecutor((sender, command, label, args) -> {
                if (!(sender instanceof Player p)) {
                    Messages.send(sender,"게임 안에서만 쓸 수 있어요.");
                    return true;
                }
                // 예전(2.0) G 키 메뉴의 "준비 중" 버튼이 /menu soon 을 실행해요
                if (args.length == 1 && args[0].equalsIgnoreCase("soon")) {
                    Messages.send(p,Text.mm(plugin.msg("coming-soon")));
                } else {
                    open(p);
                }
                return true;
            });
        }
    }

    @Override
    public void reload() {
        loadConfig();
    }

    @Override
    public void disable() { }

    private void loadConfig() {
        var yml = plugin.loadYaml("menu.yml");
        MenuConfig.migrateLayout(yml, plugin.loadDefaultYaml("menu.yml"));
        MenuConfig loaded = MenuConfig.load(yml);
        if (loaded == null) {
            plugin.getLogger().warning("menu.yml이 예전 형식이에요. plugins/ServerCore/menu.yml 을 지우고 재시작하면 새 형식으로 만들어져요. (지금은 기본 메뉴로 동작)");
            loaded = MenuConfig.load(plugin.loadDefaultYaml("menu.yml"));
        }
        config = loaded;
    }

    public void open(Player player) {
        if (!player.hasPermission("servercore.menu")) {
            Messages.send(player,Text.mm(plugin.msg("no-permission")));
            return;
        }
        new MenuGui(this, player).open(player);
    }

    // Shift + F
    @EventHandler(ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        if (!plugin.getConfig().getBoolean("menu.open-with-sneak-swap", true)) return;
        Player player = event.getPlayer();
        if (!player.isSneaking()) return; // 그냥 F는 원래대로 양손 바꾸기
        event.setCancelled(true);
        open(player);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        String hint = plugin.getConfig().getString("menu.join-hint", "");
        if (hint != null && !hint.isBlank()) Messages.send(event.getPlayer(),Text.mm(hint));
    }

    // ---------------- 카운트다운 이동 ----------------

    private final Map<UUID, BukkitTask> pending = new HashMap<>();

    /**
     * 화면 가운데에 3, 2, 1 을 띄우고 그 다음 이동, 도착하면 arriveTitle(예: HOME)을 띄워요.
     * 이미 세는 중에 또 누르면 처음부터 다시 세요.
     */
    public void countdownTeleport(Player player, Location loc, String arriveTitle, String arriveSubtitle) {
        cancelCountdown(player);
        var cfg = plugin.getConfig();
        int seconds = Math.max(0, cfg.getInt("teleport.countdown-seconds", 3));
        String numberFmt = cfg.getString("teleport.countdown-title", "<#f3c060><bold>%seconds%");
        String numberSub = cfg.getString("teleport.countdown-subtitle", "<gray>이동 중…");
        int[] left = {seconds};
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline()) {
                cancelCountdown(player);
                return;
            }
            if (left[0] > 0) {
                player.showTitle(Title.title(Text.mm(numberFmt.replace("%seconds%", String.valueOf(left[0]))),
                        Text.mm(numberSub), Title.Times.times(Duration.ZERO, Duration.ofMillis(1100), Duration.ZERO)));
                left[0]--;
                return;
            }
            cancelCountdown(player);
            player.teleportAsync(loc).thenAccept(ok -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!ok || !player.isOnline()) return;
                if (arriveTitle == null || arriveTitle.isBlank()) {
                    player.clearTitle();
                    return;
                }
                player.showTitle(Title.title(Text.mm(arriveTitle),
                        arriveSubtitle == null ? Component.empty() : Text.mm(arriveSubtitle),
                        Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(1500), Duration.ofMillis(500))));
            }));
        }, 0L, 20L);
        pending.put(player.getUniqueId(), task);
    }

    private void cancelCountdown(Player player) {
        BukkitTask t = pending.remove(player.getUniqueId());
        if (t != null) t.cancel();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cancelCountdown(event.getPlayer());
    }

    public MenuConfig config() { return config; }
    public ServerCore plugin() { return plugin; }
}
