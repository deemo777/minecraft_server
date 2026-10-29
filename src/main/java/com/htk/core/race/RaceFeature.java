package com.htk.core.race;

import com.htk.core.ServerCore;
import com.htk.core.common.Feature;
import com.htk.core.common.Messages;
import com.htk.core.common.Text;
import com.htk.core.economy.Economy;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.security.SecureRandom;
import java.util.*;

/** One server-wide round; GUI spectators and the world scene consume the same immutable plan. */
public final class RaceFeature implements Feature, TabExecutor {
    enum Phase { IDLE, OPEN, RUNNING, SETTLING, RESULTS, REFUNDING }
    record Bet(int runner, long stake, String key) {}
    static final String[] NAMES = {"분홍 돼지", "달빛 토끼", "황금 닭"};
    static final String[] COLORS = {"<#ef9fb5>", "<#b8c7ff>", "<#f4d36b>"};
    static final Material[] ICONS = {Material.PIG_SPAWN_EGG, Material.RABBIT_SPAWN_EGG, Material.CHICKEN_SPAWN_EGG};
    private static final String PREFIX = "race/";
    private final ServerCore plugin;
    private final Economy economy;
    private final SecureRandom random = new SecureRandom();
    private final Map<UUID, Bet> bets = new HashMap<>();
    private final BossBar bar = BossBar.bossBar(Text.mm("<gold>마을 동물 경주"), 1, BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS);
    private final Set<UUID> audience = new HashSet<>();
    private RaceScene scene;
    private BukkitTask task;
    private Phase phase = Phase.IDLE;
    private UUID round = UUID.randomUUID();
    private RaceRules.Plan plan;
    private Map<String, Long> settlement = Map.of();
    private int remaining, elapsed, duration, countdown, resultTicks, ticks;
    private boolean demo;

    public RaceFeature(ServerCore plugin, Economy economy) { this.plugin = plugin; this.economy = economy; }

    @Override public void enable() {
        scene = new RaceScene(plugin, this);
        scene.enable();
        var command = plugin.getCommand("race");
        if (command != null) { command.setExecutor(this); command.setTabCompleter(this); }
        recover();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 2, 2);
    }
    @Override public void reload() { abort(); }
    @Override public void disable() {
        if (task != null) task.cancel();
        // Keep the already-decided result if shutdown happens while saving it.
        if (phase == Phase.SETTLING) {
            if (!economy.settleWagers(settlement)) plugin.getLogger().severe("경주 정산 저장 실패: 다음 시작 시 보관된 참가금을 환불합니다.");
        } else recover();
        scene.stop();
        hideBar();
        for (Player player : Bukkit.getOnlinePlayers()) if (isViewer(player)) player.closeInventory();
    }

    private void recover() {
        Map<String, Long> refunds = new HashMap<>();
        economy.pendingWagers(PREFIX).forEach((key, hold) -> refunds.put(key, hold.amount()));
        settlement = Map.copyOf(refunds);
        phase = Phase.REFUNDING;
        if (economy.settleWagers(settlement)) reset();
        else plugin.getLogger().severe("경주 환불 저장 실패: 신규 참가를 막고 환불을 재시도합니다.");
    }
    private void reset() {
        bets.clear(); plan = null; settlement = Map.of();
        round = UUID.randomUUID(); phase = Phase.IDLE; elapsed = remaining = 0; demo = false;
        if (scene != null) scene.stop();
        hideBar();
    }
    private void abort() {
        if (phase == Phase.RESULTS || phase == Phase.IDLE) { reset(); return; }
        // A completed race must finish its decided payout, even during reload/cancel.
        if (phase == Phase.SETTLING) return;
        if (!bets.isEmpty()) broadcast("<yellow>경주가 중단되어 참가금을 환불합니다.");
        scene.stop(); recover();
    }

    private void prepare(int seconds, Player initiator) {
        // A missing/blocked scene must never require an admin to unlock ordinary play.
        if (!scene.start(initiator)) scene.stop();
        countdown = seconds * 20;
        duration = 20 * Math.clamp(plugin.getConfig().getInt("race.duration-seconds", 12), 8, 30);
        resultTicks = 20 * Math.clamp(plugin.getConfig().getInt("race.result-seconds", 8), 5, 30);
    }
    private void begin() {
        remaining = countdown; phase = Phase.OPEN;
        broadcast(demo ? "<gold>무료 시연 경주! <white>/race</white><gray>로 구경하세요. 5초 뒤 출발!"
                : "<gold>동물 경주 참가 접수! <white>/race</white><gray>로 관전·참가하세요. " + (countdown / 20) + "초 뒤 출발!");
    }
    void bet(Player player, UUID expectedRound, int runner, long stake) {
        if (!player.hasPermission("servercore.race")) { tell(player, "<red>경주 참가 권한이 없어요."); return; }
        UUID id = player.getUniqueId();
        if (!round.equals(expectedRound) || !accepting()) {
            tell(player, "<yellow>접수가 마감됐어요. 다음 경기에 참가해 주세요."); return;
        }
        if (runner < 0 || runner >= 3 || !RaceRules.validStake(stake) || bets.containsKey(id)) {
            tell(player, "<yellow>한 경기에는 한 번만 참가할 수 있어요."); return;
        }
        if (!economy.has(id, stake)) { tell(player, "<red>골드가 부족해요."); return; }
        boolean first = phase == Phase.IDLE;
        if (first) prepare(Math.clamp(plugin.getConfig().getInt("race.countdown-seconds", 20), 10, 60), player);
        String key = PREFIX + round + "/" + id;
        if (!economy.reserveWager(id, stake, key)) {
            if (first) scene.stop();
            tell(player, "<red>참가금을 저장하지 못했어요. 골드는 차감되지 않았어요."); return;
        }
        bets.put(id, new Bet(runner, stake, key));
        if (first) {
            begin();
            tell(player, scene.visible() ? "<green>주변에 임시 경주장을 열었어요. 혼자서도 경기가 진행돼요!"
                    : "<yellow>현장 경주장을 표시할 수 없어 공용 관전 창에서 진행해요. /race watch");
        }
        tell(player, COLORS[runner] + NAMES[runner] + "<white>에 " + Economy.format(stake) + " 참가 완료! <gray>적중 시 총 " + Economy.format(stake * 3));
        player.playSound(player.getLocation(), "minecraft:block.note_block.pling", 0.5f, 1.4f);
    }

    private void tick() {
        ticks += 2;
        try {
            switch (phase) {
                case OPEN -> {
                    remaining -= 2;
                    updateScene();
                    if (remaining <= 0) {
                        plan = RaceRules.plan(random); elapsed = 0; phase = Phase.RUNNING;
                        broadcast("<green>출발! <gray>모두 같은 경주를 보고 있어요.");
                        sound("minecraft:entity.horse.gallop", 1);
                    } else if (remaining <= 60 && remaining % 20 == 0) sound("minecraft:block.note_block.hat", 1.3f);
                }
                case RUNNING -> {
                    elapsed = Math.min(duration, elapsed + 2);
                    updateScene();
                    if (elapsed >= duration) {
                        Map<String, Long> payouts = new HashMap<>();
                        bets.values().forEach(bet -> payouts.put(bet.key(), RaceRules.payout(bet.stake(), bet.runner() == plan.winner())));
                        settlement = Map.copyOf(payouts); phase = Phase.SETTLING;
                        finish();
                    }
                }
                case SETTLING -> { if (ticks % 20 == 0) finish(); }
                case REFUNDING -> { if (ticks % 20 == 0 && economy.settleWagers(settlement)) reset(); }
                case RESULTS -> { remaining -= 2; if (remaining <= 0) reset(); }
                case IDLE -> { }
            }
            if (ticks % 4 == 0) for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getOpenInventory().getTopInventory().getHolder() instanceof RaceGui gui) gui.render(player);
            }
            if (ticks % 20 == 0) updateBar();
        } catch (RuntimeException failure) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "경주 처리 실패", failure);
            if (phase != Phase.SETTLING) abort();
        }
    }
    private void updateScene() {
        if (scene.update()) return;
        scene.stop();
        broadcast("<yellow>현장 연출을 종료하고 공용 관전 창에서 경주를 계속합니다. <white>/race watch");
    }
    private void finish() {
        if (!economy.settleWagers(settlement)) return;
        phase = Phase.RESULTS; remaining = resultTicks;
        broadcast(COLORS[plan.winner()] + "<bold>" + NAMES[plan.winner()] + " 우승!</bold> <gray>"
                + (demo ? "무료 시연이 끝났어요." : "적중한 참가자에게 원금 포함 3배를 지급했어요."));
        for (var entry : bets.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) tell(player, entry.getValue().runner() == plan.winner()
                    ? "<gold>당첨! <white>총 " + Economy.format(entry.getValue().stake() * 3) + " 지급 완료"
                    : "<gray>이번 경기는 아쉽게 빗나갔어요. 참가금 " + Economy.format(entry.getValue().stake()));
        }
        sound("minecraft:entity.player.levelup", 1.2f);
        scene.celebrate(plan.winner());
    }

    private void updateBar() {
        if (phase == Phase.IDLE) { hideBar(); return; }
        bar.name(Text.mm("<gold>동물 경주 <white>· " + status()));
        bar.progress(phase == Phase.RUNNING ? (float) Math.clamp((double) elapsed / duration, 0, 1) : 1);
        for (Player player : Bukkit.getOnlinePlayers()) {
            boolean watching = isViewer(player) || bets.containsKey(player.getUniqueId()) || scene.near(player);
            if (watching && audience.add(player.getUniqueId())) player.showBossBar(bar);
            if (!watching && audience.remove(player.getUniqueId())) player.hideBossBar(bar);
        }
        audience.removeIf(id -> Bukkit.getPlayer(id) == null);
    }
    private void hideBar() {
        for (UUID id : audience) { var player = Bukkit.getPlayer(id); if (player != null) player.hideBossBar(bar); }
        audience.clear();
    }
    private boolean isViewer(Player player) { return player.getOpenInventory().getTopInventory().getHolder() instanceof RaceGui; }
    private void sound(String key, float pitch) {
        for (Player player : Bukkit.getOnlinePlayers()) if (isViewer(player) || scene.near(player))
            player.playSound(player.getLocation(), key, 0.5f, pitch);
    }
    private void broadcast(String text) { Bukkit.broadcast(Text.mm("<gold>[동물 경주] " + text)); }
    private void tell(CommandSender player, String text) { Messages.send(player, Text.mm(text)); }
    void open(Player player) {
        if (player.hasPermission("servercore.race")) new RaceGui(this, player).open(player);
        else tell(player, "<red>경주 관전 권한이 없어요.");
    }
    void close(Player player, RaceGui gui) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && player.getOpenInventory().getTopInventory().getHolder() == gui) player.closeInventory();
        });
    }
    UUID round() { return round; }
    Phase phase() { return phase; }
    boolean accepting() { return !demo && (phase == Phase.IDLE || phase == Phase.OPEN); }
    Bet bet(UUID id) { return bets.get(id); }
    int participants() { return bets.size(); }
    long balance(UUID id) { return economy.balance(id); }
    double progress(int runner) { return plan == null ? 0 : plan.progress(runner, (double) elapsed / duration); }
    int leader() {
        int leader = 0;
        for (int runner = 1; runner < 3; runner++) if (progress(runner) > progress(leader)) leader = runner;
        return leader;
    }
    String status() {
        return switch (phase) {
            case IDLE -> "첫 참가자를 기다리는 중";
            case OPEN -> (demo ? "무료 시연 · " : "") + "출발까지 " + Math.max(0, (remaining + 19) / 20) + "초 · " + bets.size() + "명 참가";
            case RUNNING -> "경주 중 · 선두 " + NAMES[leader()];
            case SETTLING -> "결과 저장 중 · 잠시 기다려 주세요";
            case RESULTS -> NAMES[plan.winner()] + " 우승!";
            case REFUNDING -> "중단 경기 환불 처리 중";
        };
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || (args.length == 1 && (args[0].equalsIgnoreCase("watch") || args[0].equalsIgnoreCase("start")))) {
            if (sender instanceof Player player) open(player);
            else tell(sender, "게임 안에서 /race를 사용해 주세요.");
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("help")) {
            tell(sender, "<gold>/race start <gray>동물·금액 선택 후 혼자 시작 가능 · <gold>/race watch <gray>무료 관전 / 적중 시 총 3배 / 동물별 승률 1/3");
            return true;
        }
        if (!sender.hasPermission("servercore.admin")) { tell(sender, "<gray>/race start로 시작하거나 /race watch로 구경하세요."); return true; }
        if (args.length == 1 && args[0].equalsIgnoreCase("cancel")) {
            if (phase == Phase.SETTLING) tell(sender, "<yellow>이미 끝난 경기의 결과를 저장 중이에요. 정산을 재시도합니다.");
            else { abort(); tell(sender, "<yellow>경주 취소·환불을 처리했어요. 저장 실패 시 자동 재시도합니다."); }
            return true;
        }
        if (phase != Phase.IDLE) { tell(sender, "<yellow>경기가 끝난 뒤 설정해 주세요."); return true; }
        if (args.length == 1 && args[0].equalsIgnoreCase("settrack") && sender instanceof Player player) {
            var location = player.getLocation().clone();
            location.setPitch(0); location.setYaw(Math.round(location.getYaw() / 90f) * 90f);
            location.setX(location.getBlockX() + 0.5); location.setY(location.getBlockY()); location.setZ(location.getBlockZ() + 0.5);
            if (!RaceScene.clear(location)) tell(sender, "<red>앞쪽 20블록·폭 7블록에 평평한 바닥과 높이 3블록의 빈 공간, 로드된 청크가 필요해요.");
            else {
                plugin.points().set("race-track", location);
                tell(sender, "<green>무료 시연용 출발선을 저장했어요. /race demo로 확인하세요. 일반 경주는 플레이어 주변에서 자동으로 진행돼요.");
            }
        } else if (args.length == 1 && args[0].equalsIgnoreCase("removetrack")) {
            plugin.points().remove("race-track"); tell(sender, "<green>무료 시연용 위치를 지웠어요. 일반 경주의 자동 시작·관전은 계속 사용할 수 있어요.");
        } else if (args.length == 1 && args[0].equalsIgnoreCase("demo")) {
            prepare(5, null); demo = true; begin();
        } else tell(sender, "<gray>/race [start|watch|help] · 관리자: settrack | removetrack | demo | cancel");
        return true;
    }
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) return List.of();
        return (sender.hasPermission("servercore.admin") ? List.of("start", "watch", "help", "settrack", "removetrack", "demo", "cancel") : List.of("start", "watch", "help"))
                .stream().filter(value -> value.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
    }
}
