package com.htk.core.menu;

import com.htk.core.common.Messages;

import com.htk.core.ServerCore;
import com.htk.core.common.Feature;
import com.htk.core.common.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.HeightMap;
import org.bukkit.Material;
import org.bukkit.block.Block;
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
    private volatile boolean active;
    private final Map<UUID, UUID> landings = new HashMap<>();

    public MenuFeature(ServerCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public void enable() {
        loadConfig();
        active = true;
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
    public void disable() {
        active=false;
        pending.values().forEach(BukkitTask::cancel);
        pending.clear();landings.clear();
    }

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

    public void openWarp(Player player) {
        if (!active || !player.hasPermission("servercore.menu")) return;
        new WarpGui(this).open(player);
    }

    void warp(Player player, WarpDestination destination) {
        if (!active || !destination.enabled() || !player.hasPermission("servercore.menu")) return;
        String point=config.warpPoint();
        Location origin=plugin.points().get(point);
        if(origin==null){
            String world=plugin.points().worldName(point);
            Messages.send(player,Text.mm(world==null?plugin.msg("point-not-set").replace("%point%",point)
                    :plugin.msg("point-world-missing").replace("%world%",world)));
            return;
        }
        Location target=destination.offset(origin);
        player.closeInventory();
        cancelCountdown(player);
        if(destination==WarpDestination.CENTER){
            countdownTeleport(player,target,"<#e8709a><bold>야생","");return;
        }
        if(!target.getWorld().getWorldBorder().isInside(target)){
            Messages.send(player,Text.mm("<yellow>해당 야생 지점이 월드 경계 밖에 있어요."));return;
        }
        UUID request=UUID.randomUUID();
        landings.put(player.getUniqueId(),request);
        target.getWorld().getChunkAtAsync(target).whenComplete((chunk,error)->{
            if(!active || !plugin.isEnabled())return;
            plugin.getServer().getScheduler().runTask(plugin,()->{
                if(!active || !player.isOnline() || !request.equals(landings.get(player.getUniqueId())))return;
                landings.remove(player.getUniqueId());
                Location landing=error==null?safeLanding(target):null;
                if(landing==null){
                    Messages.send(player,Text.mm("<yellow>해당 좌표에 안전하게 설 땅이 없어요. 다른 야생 지점을 선택해 주세요."));return;
                }
                countdownTeleport(player,landing,"<#7acb52><bold>"+destination.label,"");
            });
        });
    }

    /** Keep the exact X/Z offset, finding dry ground in that column without editing terrain. */
    private Location safeLanding(Location target) {
        var world=target.getWorld();
        if(!world.getWorldBorder().isInside(target))return null;
        int x=target.getBlockX(),z=target.getBlockZ();
        int top=Math.min(world.getMaxHeight()-3,world.getHighestBlockYAt(x,z,HeightMap.MOTION_BLOCKING_NO_LEAVES));
        Block floor=world.getBlockAt(x,top,z),feet=world.getBlockAt(x,top+1,z),head=world.getBlockAt(x,top+2,z);
        if(!floor.getType().isSolid() || hazardous(floor.getType()) || !clear(feet) || !clear(head))return null;
        // Fences and walls can extend into the nominally empty block above.
        if(floor.getCollisionShape().getBoundingBoxes().stream().anyMatch(box->box.getMaxY()>1))return null;
        Location landing=target.clone();landing.setY(top+1);return landing;
    }
    private static boolean clear(Block block){
        return block.isPassable() && !block.isLiquid() && !hazardous(block.getType());
    }
    static boolean hazardous(Material material){
        return switch(material){
            case LAVA,WATER,FIRE,SOUL_FIRE,MAGMA_BLOCK,CACTUS,CAMPFIRE,SOUL_CAMPFIRE,
                    POWDER_SNOW,SWEET_BERRY_BUSH,WITHER_ROSE,COBWEB -> true;
            default -> false;
        };
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
        landings.remove(player.getUniqueId());
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
