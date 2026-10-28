package com.htk.core.hud;

import com.htk.core.ServerCore;
import com.htk.core.common.Feature;
import com.htk.core.common.Glyphs;
import com.htk.core.common.Text;
import com.htk.core.economy.Economy;
import com.htk.core.skill.Skill;
import com.htk.core.skill.SkillFeature;
import com.htk.core.skill.SkillStore;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.NamespacedKey;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 화면 왼쪽 위 HUD (얼굴·이름·골드·레벨) + 경험치를 얻으면 위에 보스바처럼 "농사 레벨 33 [962/4,375]"
 *
 * 보스바 하나를 계속 띄워 두고, 그 제목 자리에 리소스팩 글자로 그림을 그려요.
 *   - 평소: 하얀 보스바 (리소스팩이 하얀 보스바 막대를 투명하게 만들어서 막대는 안 보임) + 왼쪽 위 HUD
 *   - 경험치를 얻으면 3초 동안: 초록 보스바 + 진행도 + 가운데 "농사 레벨 33 [962/4,375]"
 * 줄마다 위아래 위치가 다른 폰트(servercore:hud1~4)를 써요 (tools/build_pack.py 와 약속).
 * /hud 로 켜고 끄기, /hud left·right·pos·reset 으로 위치 맞추기 (플레이어별로 기억)
 */
public final class HudFeature implements Feature, Listener {

    // ---- 리소스팩 약속 (build_pack.py / make_art.py) ----
    private static final Key GUI = Glyphs.FONT;
    private static final Key[] LINE = {
            Key.key("servercore", "hud1"), Key.key("servercore", "hud2"),
            Key.key("servercore", "hud3"), Key.key("servercore", "hud4")};
    private static final char PANEL = '';
    private static final int PANEL_ADV = 89;        // 88px + 1
    private static final char COIN = '';
    private static final char ICON_L3 = '';   // + 스킬 순서 (5개)
    private static final char ICON_L4 = '';
    private static final char LABEL = '';     // + 스킬 순서: "농사 레벨" 같은 글자 그림
    private static final int ICON_ADV = 9;          // 8px + 1
    private static final int LABEL_ADV = 35;        // 34px + 1
    private static final int FACE_ADV = 9;          // 얼굴(8px) 다음 칸 (대략)

    private final ServerCore plugin;
    private final SkillFeature skills;
    private final Economy economy;
    private final NamespacedKey hiddenKey;
    private final NamespacedKey offsetKey;
    private static final int MAX_OFFSET = 1000;
    private final Map<UUID, BossBar> bars = new HashMap<>();
    private final Map<UUID, Skill> xpSkill = new HashMap<>();
    private final Map<UUID, Long> xpUntil = new HashMap<>();
    private BukkitTask ticker;
    private boolean faceBroken;

    // 설정
    private int offsetX;
    private long xpShowMs;

    public HudFeature(ServerCore plugin, SkillFeature skills, Economy economy) {
        this.plugin = plugin;
        this.skills = skills;
        this.economy = economy;
        this.hiddenKey = new NamespacedKey(plugin, "hud_hidden");
        this.offsetKey = new NamespacedKey(plugin, "hud_offset");
    }

    @Override
    public void enable() {
        loadConfig();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        skills.onXp((p, s) -> {
            xpSkill.put(p.getUniqueId(), s);
            xpUntil.put(p.getUniqueId(), System.currentTimeMillis() + xpShowMs);
            update(p);
        });
        PluginCommand cmd = plugin.getCommand("hud");
        if (cmd != null) {
            cmd.setExecutor((sender, command, label, args) -> {
                if (!(sender instanceof Player p)) return true;
                command(p, label, args);
                return true;
            });
            cmd.setTabCompleter((sender, command, alias, args) -> {
                if (args.length == 1) {
                    List<String> out = new ArrayList<>();
                    for (String o : List.of("on", "off", "left", "right", "pos", "reset"))
                        if (o.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(o);
                    return out;
                }
                if (args.length == 2 && (args[0].equalsIgnoreCase("left") || args[0].equalsIgnoreCase("right")))
                    return List.of("1", "5", "10", "50");
                return List.of();
            });
        }
        for (Player p : plugin.getServer().getOnlinePlayers()) update(p);
        // 골드·레벨이 바뀐 걸 반영하고, 경험치 바 시간이 지나면 숨기기 (0.5초마다)
        ticker = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : plugin.getServer().getOnlinePlayers()) update(p);
        }, 10L, 10L);
    }

    @Override
    public void reload() {
        loadConfig();
    }

    @Override
    public void disable() {
        if (ticker != null) ticker.cancel();
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            BossBar b = bars.remove(p.getUniqueId());
            if (b != null) p.hideBossBar(b);
        }
    }

    /**
     * /hud              켜기/끄기
     * /hud on | off
     * /hud left [steps]    왼쪽으로 (기본 10칸)
     * /hud right [steps]   오른쪽으로
     * /hud pos <number>   위치를 숫자로 (화면 가운데 기준, 음수 = 왼쪽)
     * /hud reset        서버 기본 위치로
     */
    private void command(Player p, String label, String[] args) {
        var pdc = p.getPersistentDataContainer();
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "" -> {
                boolean hidden = pdc.has(hiddenKey, PersistentDataType.BYTE);
                setHidden(p, !hidden);
            }
            case "on" -> setHidden(p, false);
            case "off" -> setHidden(p, true);
            case "left", "right" -> {
                int step = 10;
                if (args.length >= 2) {
                    Integer n = parse(args[1]);
                    if (n == null || n <= 0) {
                        p.sendMessage(Text.mm("<red>칸 수는 1 이상 숫자로 적어 주세요. 예) /" + label + " " + sub + " 5"));
                        return;
                    }
                    step = n;
                }
                setOffset(p, offset(p) + (sub.equals("left") ? -step : step));
            }
            case "pos" -> {
                Integer n = args.length >= 2 ? parse(args[1]) : null;
                if (n == null) {
                    p.sendMessage(Text.mm("<gray>지금 위치: <white>" + offset(p)
                            + "</white>  (바꾸려면 /" + label + " pos <number>, 서버 기본 " + offsetX + ")"));
                    return;
                }
                setOffset(p, n);
            }
            case "reset" -> {
                pdc.remove(offsetKey);
                update(p);
                p.sendMessage(Text.mm("<green>HUD 위치를 기본값(" + offsetX + ")으로 되돌렸어요."));
            }
            default -> p.sendMessage(Text.mm("<gray>사용법: /" + label
                    + " [on|off] | left [steps] | right [steps] | pos <number> | reset"));
        }
    }

    private void setHidden(Player p, boolean hide) {
        var pdc = p.getPersistentDataContainer();
        if (hide) pdc.set(hiddenKey, PersistentDataType.BYTE, (byte) 1);
        else pdc.remove(hiddenKey);
        update(p);
        p.sendMessage(Text.mm(hide ? "<gray>HUD를 껐어요. 다시 켜려면 /hud" : "<green>HUD를 켰어요."));
    }

    private void setOffset(Player p, int value) {
        int v = Math.max(-MAX_OFFSET, Math.min(MAX_OFFSET, value));
        p.getPersistentDataContainer().set(offsetKey, PersistentDataType.INTEGER, v);
        update(p);
        p.sendActionBar(Text.mm("<gray>HUD 위치 <white>" + v + "</white>  <dark_gray>(/hud left · right · reset)"));
    }

    /** 이 플레이어의 HUD 위치: 직접 정한 값이 있으면 그것, 없으면 config 의 hud.offset-x */
    private int offset(Player p) {
        Integer v = p.getPersistentDataContainer().get(offsetKey, PersistentDataType.INTEGER);
        return v != null ? v : offsetX;
    }

    private static Integer parse(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void loadConfig() {
        offsetX = plugin.getConfig().getInt("hud.offset-x", -236);
        xpShowMs = (long) (plugin.getConfig().getDouble("hud.xp-bar-seconds", 3) * 1000);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> update(e.getPlayer()), 20L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        bars.remove(id);
        xpSkill.remove(id);
        xpUntil.remove(id);
    }

    // ---------------- 그리기 ----------------

    private void update(Player p) {
        UUID id = p.getUniqueId();
        boolean show = plugin.resourcePackEnabled() && p.isOnline()
                && !p.getPersistentDataContainer().has(hiddenKey, PersistentDataType.BYTE);
        BossBar bar = bars.get(id);
        if (!show) {
            if (bar != null) {
                p.hideBossBar(bar);
                bars.remove(id);
            }
            return;
        }
        Skill active = xpSkill.get(id);
        if (active != null && xpUntil.getOrDefault(id, 0L) < System.currentTimeMillis()) {
            xpSkill.remove(id);
            active = null;
        }
        Component title = title(p, active);
        float progress = 0f;
        if (active != null) {
            SkillStore.Progress pr = skills.progress(id, active);
            progress = pr.level >= skills.maxLevel() ? 1f
                    : Math.max(0f, Math.min(1f, pr.xp / (float) skills.need(pr.level)));
        }
        BossBar.Color color = active != null ? BossBar.Color.GREEN : BossBar.Color.WHITE;
        if (bar == null) {
            bar = BossBar.bossBar(title, progress, color, BossBar.Overlay.PROGRESS);
            bars.put(id, bar);
            p.showBossBar(bar);
        } else {
            bar.name(title);
            bar.progress(progress);
            bar.color(color);
        }
    }

    /** 제목 전체 폭이 0이 되게 그려요 → 보스바 제목이 화면 가운데 기준이라 위치가 고정돼요 */
    private Component title(Player p, Skill active) {
        Pen pen = new Pen();
        UUID id = p.getUniqueId();

        // 1) 가운데: 경험치 표시 "농사 레벨 33 [962/4,375]"
        if (active != null) {
            SkillStore.Progress pr = skills.progress(id, active);
            String nums = " " + pr.level + " [" + fmt(pr.xp) + "/" + fmt(skills.need(pr.level)) + "]";
            if (pr.level >= skills.maxLevel()) nums = " " + pr.level + " [MAX]";
            int w = LABEL_ADV + width(nums);
            pen.moveTo(-w / 2);
            pen.glyph((char) (LABEL + active.ordinal()), LABEL_ADV);
            pen.text(nums, LINE[0], TextColor.color(0xc8f0a0));
        }

        // 2) 왼쪽 위 HUD
        int o = offset(p);
        pen.moveTo(o);
        pen.glyph(PANEL, PANEL_ADV);
        pen.moveTo(o + 15);
        pen.text(fit(p.getName(), 70), LINE[0], TextColor.color(0xffffff));
        pen.moveTo(o + 4);
        pen.glyph(COIN, ICON_ADV);
        pen.moveTo(o + 15);
        pen.text(economy.balance(id) > 0 ? Economy.format(economy.balance(id)) : "0G", LINE[1], TextColor.color(0xffd34d));
        int[][] pos = {{4, 2}, {32, 2}, {60, 2}, {4, 3}, {32, 3}};   // (x, 줄) — 스킬 순서대로
        for (Skill s : Skill.values()) {
            int[] xy = pos[s.ordinal()];
            pen.moveTo(o + xy[0]);
            pen.glyph((char) ((xy[1] == 2 ? ICON_L3 : ICON_L4) + s.ordinal()), ICON_ADV);
            pen.moveTo(o + xy[0] + 10);
            pen.text(String.valueOf(skills.progress(id, s).level), LINE[xy[1]], TextColor.color(0xffffff));
        }

        // 3) 얼굴 (마지막에: 폭을 정확히 몰라도 위치가 거의 안 틀어지게)
        pen.moveTo(o + 4);
        Component face = face(p);
        if (face != null) {
            pen.add(face, FACE_ADV);
        }
        pen.moveTo(0);
        return pen.build();
    }

    /** 1.21.9+ 의 "플레이어 머리" 글자. 지원 안 하면 null (얼굴 없이) */
    private Component face(Player p) {
        if (faceBroken) return null;
        try {
            return GsonComponentSerializer.gson().deserialize(
                    "{\"type\":\"object\",\"object\":\"player\",\"player\":{\"name\":\"" + p.getName() + "\"},\"hat\":true}");
        } catch (Exception e) {
            faceBroken = true;
            plugin.getLogger().warning("HUD 얼굴 표시를 이 버전에서 못 써서 얼굴 없이 보여줘요: " + e.getMessage());
            return null;
        }
    }

    private static String fmt(long v) {
        return NumberFormat.getIntegerInstance(Locale.US).format(v);
    }

    static int width(String s) {
        return Glyphs.asciiWidth(s);
    }

    /** 폭이 max 를 넘으면 뒤를 잘라 ".." */
    private static String fit(String s, int max) {
        if (width(s) <= max) return s;
        while (!s.isEmpty() && width(s + "..") > max) s = s.substring(0, s.length() - 1);
        return s + "..";
    }

    /** 글자를 붙여 가며 지금 x 위치를 기억하는 도구 */
    private static final class Pen {
        private Component out = Component.empty();
        private int x = 0;

        void moveTo(int target) {
            int d = target - x;
            if (d != 0) out = out.append(Component.text(Glyphs.space(d)).font(GUI));
            x = target;
        }

        void glyph(char c, int adv) {
            out = out.append(Component.text(String.valueOf(c)).font(GUI).color(TextColor.color(0xffffff)));
            x += adv;
        }

        void text(String s, Key font, TextColor color) {
            out = out.append(Component.text(s).font(font).color(color));
            x += width(s);
        }

        void add(Component c, int adv) {
            out = out.append(c);
            x += adv;
        }

        Component build() {
            return out;
        }
    }
}
