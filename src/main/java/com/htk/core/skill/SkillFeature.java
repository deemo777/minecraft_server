package com.htk.core.skill;

import com.htk.core.ServerCore;
import com.htk.core.common.Feature;
import com.htk.core.common.Text;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerHarvestBlockEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * 레벨링: 농사·채광·벌목·낚시·전투
 * 경험치 표와 필요 경험치 공식은 plugins/ServerCore/skills.yml
 * 관리자: /skill set <플레이어> <분야> <레벨>,  /skill reset <플레이어>
 */
public final class SkillFeature implements Feature, Listener {

    private final ServerCore plugin;
    private final SkillStore store;
    private final PlacedBlocks placed;
    private final Map<Skill, Map<String, Long>> sources = new EnumMap<>(Skill.class);
    private final java.util.Set<String> matureOnly = new java.util.HashSet<>();   // 다 자랐을 때만 주는 작물
    private final List<BiConsumer<Player, Skill>> xpListeners = new ArrayList<>();
    private BukkitTask autosave;

    // 설정
    private int maxLevel;
    private double curveA, curveB, curveC;
    private long defaultFish, defaultMonster;
    private String levelUpTitle, levelUpSubtitle;

    public SkillFeature(ServerCore plugin) {
        this.plugin = plugin;
        this.store = new SkillStore(plugin);
        this.placed = new PlacedBlocks(new NamespacedKey(plugin, "placed"));
    }

    /** 경험치를 얻을 때마다 불러줘요 (HUD 경험치 바용) */
    public void onXp(BiConsumer<Player, Skill> listener) {
        xpListeners.add(listener);
    }

    @Override
    public void enable() {
        loadConfig();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        PluginCommand cmd = plugin.getCommand("skill");
        if (cmd != null) {
            SkillCommand c = new SkillCommand(this);
            cmd.setExecutor(c);
            cmd.setTabCompleter(c);
        }
        autosave = plugin.getServer().getScheduler().runTaskTimer(plugin, store::saveAll, 1200L, 1200L);
    }

    @Override
    public void reload() {
        loadConfig();
    }

    @Override
    public void disable() {
        if (autosave != null) autosave.cancel();
        store.saveAll();
    }

    private void loadConfig() {
        YamlConfiguration yml = plugin.loadYaml("skills.yml");
        maxLevel = Math.max(1, yml.getInt("max-level", 99));
        curveA = yml.getDouble("curve.a", 50);
        curveB = yml.getDouble("curve.b", 40);
        curveC = yml.getDouble("curve.c", 3);
        defaultFish = yml.getLong("default-fish-xp", 3);
        defaultMonster = yml.getLong("default-monster-xp", 3);
        levelUpTitle = yml.getString("level-up.title", "<#ffd34d><bold>%skill% 레벨 UP!");
        levelUpSubtitle = yml.getString("level-up.subtitle", "<white>Lv.%level%");
        matureOnly.clear();
        for (String m : yml.getStringList("mature-only")) matureOnly.add(m.toUpperCase());
        sources.clear();
        for (Skill s : Skill.values()) {
            Map<String, Long> m = new HashMap<>();
            ConfigurationSection sec = yml.getConfigurationSection("sources." + s.key());
            if (sec != null) for (String k : sec.getKeys(false)) m.put(k.toUpperCase(), sec.getLong(k, 0));
            sources.put(s, m);
        }
    }

    // ---------------- 레벨 계산 ----------------

    /** level → level+1 에 필요한 경험치 */
    public long need(int level) {
        return Math.max(1, Math.round(curveA + curveB * level + curveC * level * (double) level));
    }

    public int maxLevel() { return maxLevel; }

    public SkillStore.Progress progress(UUID id, Skill s) {
        return store.get(id).get(s);
    }

    public void addXp(Player p, Skill skill, long amount) {
        if (amount <= 0) return;
        SkillStore.Progress pr = store.get(p.getUniqueId()).get(skill);
        if (pr.level >= maxLevel) return;
        pr.xp += amount;
        int before = pr.level;
        while (pr.level < maxLevel && pr.xp >= need(pr.level)) {
            pr.xp -= need(pr.level);
            pr.level++;
        }
        if (pr.level >= maxLevel) pr.xp = 0;
        store.markDirty(p.getUniqueId());
        if (pr.level > before) levelUp(p, skill, pr.level);
        for (BiConsumer<Player, Skill> l : xpListeners) l.accept(p, skill);
    }

    private void levelUp(Player p, Skill skill, int level) {
        p.showTitle(Title.title(
                Text.mm(levelUpTitle.replace("%skill%", skill.korean).replace("%level%", String.valueOf(level))),
                Text.mm(levelUpSubtitle.replace("%skill%", skill.korean).replace("%level%", String.valueOf(level))),
                Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(1500), Duration.ofMillis(400))));
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.2f);
    }

    void setLevel(OfflinePlayer target, Skill skill, int level) {
        SkillStore.Progress pr = store.get(target.getUniqueId()).get(skill);
        pr.level = Math.max(1, Math.min(maxLevel, level));
        pr.xp = 0;
        store.markDirty(target.getUniqueId());
        store.save(target.getUniqueId());
    }

    void reset(OfflinePlayer target) {
        for (Skill s : Skill.values()) setLevel(target, s, 1);
    }

    private long xpFor(Skill s, String key) {
        return sources.getOrDefault(s, Map.of()).getOrDefault(key.toUpperCase(), 0L);
    }

    // ---------------- 경험치 얻는 곳 ----------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        Material m = e.getBlockPlaced().getType();
        // 경험치를 주는 블록을 직접 놓으면 기억 (다 자라야 주는 작물은 원래 심는 거라 제외)
        if (!matureOnly.contains(m.name())
                && (xpFor(Skill.MINING, m.name()) > 0 || xpFor(Skill.WOODCUTTING, m.name()) > 0
                || xpFor(Skill.FARMING, m.name()) > 0)) {
            placed.mark(e.getBlockPlaced());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        String key = b.getType().name();
        boolean wasPlaced = placed.isPlaced(b);
        if (wasPlaced) placed.unmark(b);
        Player p = e.getPlayer();

        long farm = xpFor(Skill.FARMING, key);
        if (farm > 0) {
            if (matureOnly.contains(key)) {
                if (b.getBlockData() instanceof Ageable age && age.getAge() >= age.getMaximumAge()) {
                    addXp(p, Skill.FARMING, farm);                                       // 다 자란 것만
                }
            } else if (!wasPlaced) {
                addXp(p, Skill.FARMING, farm);                                           // 수박·호박·사탕수수 등
            }
            return;
        }
        if (wasPlaced) return;
        long mine = xpFor(Skill.MINING, key);
        if (mine > 0) {
            addXp(p, Skill.MINING, mine);
            return;
        }
        long wood = xpFor(Skill.WOODCUTTING, key);
        if (wood > 0) addXp(p, Skill.WOODCUTTING, wood);
    }

    /** 달콤한 열매·발광 열매처럼 우클릭으로 따는 것 */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHarvest(PlayerHarvestBlockEvent e) {
        long xp = xpFor(Skill.FARMING, e.getHarvestedBlock().getType().name());
        if (xp > 0) addXp(e.getPlayer(), Skill.FARMING, xp);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent e) {
        if (e.getState() != PlayerFishEvent.State.CAUGHT_FISH || !(e.getCaught() instanceof Item item)) return;
        long xp = xpFor(Skill.FISHING, item.getItemStack().getType().name());
        addXp(e.getPlayer(), Skill.FISHING, xp > 0 ? xp : defaultFish);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(EntityDeathEvent e) {
        Player killer = e.getEntity().getKiller();
        if (killer == null) return;
        long xp = xpFor(Skill.COMBAT, e.getEntityType().name());
        if (xp <= 0 && e.getEntity() instanceof org.bukkit.entity.Enemy) xp = defaultMonster;
        addXp(killer, Skill.COMBAT, xp);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        store.get(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        store.unload(e.getPlayer().getUniqueId());
    }

    public ServerCore plugin() { return plugin; }

    static OfflinePlayer findPlayer(String name) {
        OfflinePlayer p = Bukkit.getPlayerExact(name);
        return p != null ? p : Bukkit.getOfflinePlayerIfCached(name);
    }
}
