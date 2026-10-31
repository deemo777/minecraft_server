package com.htk.core.skill;

import com.htk.core.common.Messages;

import com.htk.core.ServerCore;
import com.htk.core.common.Feature;
import com.htk.core.common.Text;
import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
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
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerHarvestBlockEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;

/**
 * 레벨링: 농사·채광·벌목·낚시·전투
 * 경험치 표, 필요 경험치 공식, 칭호, 레벨 효과는 plugins/ServerCore/skills.yml
 * 관리자: /skill set <player> <category> <level>,  /skill reset <player>
 *
 * 레벨 효과 (아주 작게 시작, skills.yml → perks 에서 조절)
 *   농사·채광·벌목: 캔 것이 2배로 나올 확률    낚시: 두 마리 낚을 확률
 *   전투: 몹에게 주는 피해 +%                   상점: 그 분야 상점의 판매가 +%
 * 칭호: 기본 20레벨마다 (skills.yml → titles). /honor 로 고르면 채팅·탭 목록 이름 앞에 붙어요
 */
public final class SkillFeature implements Feature, Listener {

    private final ServerCore plugin;
    private final SkillStore store;
    private final PlacedBlocks placed;
    private final Map<Skill, Map<String, Long>> sources = new EnumMap<>(Skill.class);
    private final java.util.Set<String> matureOnly = new java.util.HashSet<>();   // 다 자랐을 때만 주는 작물
    private final List<BiConsumer<Player, Skill>> xpListeners = new ArrayList<>();
    private final Map<Skill, TreeMap<Integer, String>> titles = new EnumMap<>(Skill.class);
    /** 이번 틱에 경험치를 준 블록 → 떨어지는 아이템을 2배로 할지 굴려 볼 분야 */
    private final Map<Location, Skill> pendingDrops = new HashMap<>();
    private BukkitTask autosave;

    // 설정
    private int maxLevel;
    private double curveA, curveB, curveC;
    private long defaultFish, defaultMonster;
    private String levelUpTitle, levelUpSubtitle;
    private double doubleDropPerLevel, combatDamagePerLevel, sellBonusPerLevel;
    private boolean perkMessage;
    private String titleFormat, titleUnlocked;

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
        PluginCommand honor = plugin.getCommand("honor");
        if (honor != null) {
            HonorCommand c = new HonorCommand(this);
            honor.setExecutor(c);
            honor.setTabCompleter(c);
        }
        autosave = plugin.getServer().getScheduler().runTaskTimer(plugin, store::saveAll, 1200L, 1200L);
        for (Player p : Bukkit.getOnlinePlayers()) updateListName(p);
    }

    @Override
    public void reload() {
        loadConfig();
        for (Player p : Bukkit.getOnlinePlayers()) updateListName(p);
    }

    @Override
    public void disable() {
        if (autosave != null) autosave.cancel();
        store.saveAll();
    }

    private void loadConfig() {
        YamlConfiguration yml = plugin.loadYaml("skills.yml");
        SkillScale.prepareConfig(yml);
        // 서버의 skills.yml 이 예전 것이라 칭호·효과 항목이 없으면 jar 안의 기본값을 써요
        yml.setDefaults(plugin.loadDefaultYaml("skills.yml"));
        maxLevel = Math.max(1, yml.getInt("max-level", 100));
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
        doubleDropPerLevel = Math.max(0, yml.getDouble("perks.double-drop-per-level", 0.0005));
        combatDamagePerLevel = Math.max(0, yml.getDouble("perks.combat-damage-per-level", 0.0005));
        sellBonusPerLevel = Math.max(0, yml.getDouble("perks.sell-bonus-per-level", 0.0002));
        perkMessage = yml.getBoolean("perks.show-message", true);
        titles.clear();
        for (Skill s : Skill.values()) {
            TreeMap<Integer, String> t = new TreeMap<>();
            ConfigurationSection sec = yml.getConfigurationSection("titles." + s.key());
            if (sec != null) {
                for (String k : sec.getKeys(false)) {
                    try {
                        t.put(Integer.parseInt(k.trim()), sec.getString(k, ""));
                    } catch (NumberFormatException ignored) { }
                }
            }
            titles.put(s, t);
        }
        titleFormat = yml.getString("title-format", "<dark_gray>[</dark_gray>%title%<dark_gray>]</dark_gray> ");
        titleUnlocked = yml.getString("title-unlocked", "<#ffd34d>🏅 <gray>새 칭호 <white>%title%</white> 을(를) 얻었어요! <dark_gray>(/honor 로 바꾸기)");
    }

    // ---------------- 레벨 계산 ----------------

    /** level → level+1 에 필요한 경험치 */
    public long need(Skill skill,int level) {
        return SkillExperience.required(skill,level,curveA,curveB,curveC);
    }

    public int maxLevel() { return maxLevel; }

    /** 최고 레벨이 낮아졌으면 (예: 99 → 50) 넘는 레벨은 최고 레벨로 맞춰요 */
    public SkillStore.Progress progress(UUID id, Skill s) {
        SkillStore.Progress pr = store.get(id).get(s);
        if (pr.level > maxLevel) {
            pr.level = maxLevel;
            pr.xp = 0;
            store.markDirty(id);
        }
        return pr;
    }

    public void addXp(Player p, Skill skill, long amount) {
        if (amount <= 0) return;
        SkillStore.Progress pr = progress(p.getUniqueId(), skill);
        if (pr.level >= maxLevel) return;
        pr.xp += amount;
        int before = pr.level;
        while (pr.level < maxLevel && pr.xp >= need(skill,pr.level)) {
            pr.xp -= need(skill,pr.level);
            pr.level++;
        }
        if (pr.level >= maxLevel) pr.xp = 0;
        store.markDirty(p.getUniqueId());
        if (pr.level > before) {
            levelUp(p, skill, pr.level);
            for (int lv = before + 1; lv <= pr.level; lv++) unlockTitle(p, skill, lv);
        }
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
        SkillStore.Data d = store.get(target.getUniqueId());
        if (!d.title.isEmpty() && !hasTitle(target.getUniqueId(), d.title)) d.title = "";   // 레벨을 낮추면 칭호도
        store.markDirty(target.getUniqueId());
        store.save(target.getUniqueId());
        Player online = target.getPlayer();
        if (online != null) updateListName(online);
    }

    void reset(OfflinePlayer target) {
        for (Skill s : Skill.values()) setLevel(target, s, 1);
    }

    private long xpFor(Skill s, String key) {
        return sources.getOrDefault(s, Map.of()).getOrDefault(key.toUpperCase(), 0L);
    }

    // ---------------- 칭호 ----------------

    /** 칭호 아이디 = "분야:레벨" (예: farming:30) */
    public record HonorTitle(String id, Skill skill, int level, String text) {}

    /** 그 분야에서 받을 수 있는 칭호 전부 (레벨 순) */
    List<HonorTitle> titlesOf(Skill s) {
        List<HonorTitle> out = new ArrayList<>();
        titles.getOrDefault(s, new TreeMap<>()).forEach((lv, text) -> out.add(new HonorTitle(s.key() + ":" + lv, s, lv, text)));
        return out;
    }

    HonorTitle title(String id) {
        if (id == null || !id.contains(":")) return null;
        String[] parts = id.split(":", 2);
        Skill s = Skill.of(parts[0]);
        if (s == null) return null;
        try {
            int lv = Integer.parseInt(parts[1]);
            String text = titles.getOrDefault(s, new TreeMap<>()).get(lv);
            return text == null ? null : new HonorTitle(s.key() + ":" + lv, s, lv, text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    boolean hasTitle(UUID id, String titleId) {
        HonorTitle t = title(titleId);
        return t != null && progress(id, t.skill()).level >= t.level();
    }

    /** 지금 레벨에서 받은 칭호 중 가장 높은 것 (없으면 null) */
    public String currentTitleText(UUID id, Skill s) {
        int lv = progress(id, s).level;
        Map.Entry<Integer, String> e = titles.getOrDefault(s, new TreeMap<>()).floorEntry(lv);
        return e == null ? null : e.getValue();
    }

    /** 다음 칭호까지 (없으면 null) */
    public Map.Entry<Integer, String> nextTitle(UUID id, Skill s) {
        int lv = progress(id, s).level;
        return titles.getOrDefault(s, new TreeMap<>()).higherEntry(lv);
    }

    String equippedTitle(UUID id) {
        return store.get(id).title;
    }

    void equipTitle(Player p, String titleId) {
        store.get(p.getUniqueId()).title = titleId == null ? "" : titleId;
        store.markDirty(p.getUniqueId());
        updateListName(p);
    }

    /** 채팅·탭 목록 이름 앞에 붙는 글자 (칭호가 없으면 빈 글자) */
    public Component prefix(UUID id) {
        var achievements=plugin.feature(com.htk.core.achievement.HiddenAchievementFeature.class);
        if(achievements!=null && !achievements.prefix(id).equals(Component.empty()))return achievements.prefix(id);
        HonorTitle t = title(equippedTitle(id));
        if (t == null || !hasTitle(id, t.id())) return Component.empty();
        return Text.mm(titleFormat.replace("%title%", t.text()));
    }

    private void unlockTitle(Player p, Skill skill, int level) {
        String text = titles.getOrDefault(skill, new TreeMap<>()).get(level);
        if (text == null) return;
        Messages.send(p,Text.mm(titleUnlocked.replace("%title%", text)));
        // 칭호를 아직 안 골랐거나, 같은 분야의 낮은 칭호를 달고 있으면 새 칭호로
        HonorTitle cur = title(equippedTitle(p.getUniqueId()));
        if (cur == null || (cur.skill() == skill && cur.level() < level)) equipTitle(p, skill.key() + ":" + level);
    }

    private void updateListName(Player p) {
        Component prefix = prefix(p.getUniqueId());
        p.playerListName(prefix.equals(Component.empty()) ? null : prefix.append(Component.text(p.getName())));
    }
    public void refreshListName(Player p){updateListName(p);}
    com.htk.core.achievement.HiddenAchievementFeature achievements(){return plugin.feature(com.htk.core.achievement.HiddenAchievementFeature.class);}

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncChatEvent e) {
        Component prefix = prefix(e.getPlayer().getUniqueId());
        if (prefix.equals(Component.empty())) return;
        ChatRenderer original = e.renderer();
        e.renderer((source, name, message, viewer) -> original.render(source, prefix.append(name), message, viewer));
    }

    // ---------------- 레벨 효과 ----------------

    /** 캔 것이 2배로 나올 확률 (0~1) */
    public double doubleDropChance(UUID id, Skill s) {
        return Math.min(1.0, doubleDropPerLevel * SkillScale.rewardLevel(progress(id, s).level));
    }

    /** 전투: 몹에게 주는 피해 추가 비율 */
    public double combatBonus(UUID id) {
        return combatDamagePerLevel * SkillScale.rewardLevel(progress(id, Skill.COMBAT).level);
    }

    /** 상점 판매가 추가 비율 (skillKey: shops.yml 의 skill, 비어 있으면 0) */
    public double sellBonus(Player p, String skillKey) {
        Skill s = Skill.fromConfig(skillKey);
        return s == null ? 0 : sellBonusPerLevel * SkillScale.rewardLevel(progress(p.getUniqueId(), s).level);
    }

    double sellBonusOf(UUID id, Skill s) {
        return sellBonusPerLevel * SkillScale.rewardLevel(progress(id, s).level);
    }

    private boolean roll(double chance) {
        return chance > 0 && ThreadLocalRandom.current().nextDouble() < chance;
    }

    private void perkMessage(Player p, String text) {
        if (perkMessage) p.sendActionBar(Text.mm(text));
    }

    /** 경험치를 준 블록은 떨어지는 아이템 이벤트에서 2배를 굴려 봐요 */
    private void markDrop(Block b, Skill s) {
        Location key = b.getLocation();
        pendingDrops.put(key, s);
        Bukkit.getScheduler().runTask(plugin, () -> pendingDrops.remove(key));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(BlockDropItemEvent e) {
        Skill s = pendingDrops.remove(e.getBlock().getLocation());
        if (s == null || e.getItems().isEmpty()) return;
        Player p = e.getPlayer();
        var tools=plugin.feature(com.htk.core.tools.VillageToolFeature.class);
        if(tools!=null) {
            if(tools.ownsNumericDrops(e))return;
            var type=tools.typeOf(p.getInventory().getItemInMainHand());
            if((s==Skill.WOODCUTTING&&type==com.htk.core.tools.ToolType.AXE)
                    ||(s==Skill.MINING&&type==com.htk.core.tools.ToolType.PICKAXE))return;
        }
        if (!roll(doubleDropChance(p.getUniqueId(), s))) return;
        for (Item it : e.getItems()) {
            ItemStack copy = it.getItemStack().clone();
            e.getBlock().getWorld().dropItemNaturally(it.getLocation(), copy);
        }
        perkMessage(p, "<#ffd34d>✨ " + s.korean + " 보너스! <gray>2배로 얻었어요");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (e.getEntity() instanceof Player) return;                         // 사람끼리는 그대로
        Player attacker = null;
        if (e.getDamager() instanceof Player p) attacker = p;
        else if (e.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player p) attacker = p;
        if (attacker == null) return;
        double bonus = combatBonus(attacker.getUniqueId());
        if (bonus > 0) e.setDamage(e.getDamage() * (1.0 + bonus));
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
                    markDrop(b, Skill.FARMING);
                }
            } else if (!wasPlaced) {
                addXp(p, Skill.FARMING, farm);                                           // 수박·호박·사탕수수 등
                markDrop(b, Skill.FARMING);
            }
            return;
        }
        if (wasPlaced) return;
        long mine = xpFor(Skill.MINING, key);
        if (mine > 0) {
            addXp(p, Skill.MINING, mine);
            if (mine > 1) markDrop(b, Skill.MINING);                                     // 돌(1)은 빼고 광석만
            return;
        }
        long wood = xpFor(Skill.WOODCUTTING, key);
        if (wood > 0) {
            addXp(p, Skill.WOODCUTTING, wood);
            markDrop(b, Skill.WOODCUTTING);
        }
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
        Player p = e.getPlayer();
        long xp = xpFor(Skill.FISHING, item.getItemStack().getType().name());
        addXp(p, Skill.FISHING, xp > 0 ? xp : defaultFish);
        if (roll(doubleDropChance(p.getUniqueId(), Skill.FISHING))) {
            ItemStack copy = item.getItemStack().clone();
            p.getInventory().addItem(copy).values().forEach(left -> p.getWorld().dropItem(p.getLocation(), left));
            perkMessage(p, "<#6aa8e0>✨ 낚시 보너스! <gray>한 마리 더 낚았어요");
        }
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
        updateListName(e.getPlayer());
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
