package com.htk.core.tools;

import com.htk.core.ServerCore;
import com.htk.core.common.Feature;
import com.htk.core.common.Glyphs;
import com.htk.core.common.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareGrindstoneEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * 마을 도구 (대장장이에게 사는 곡괭이·도끼·괭이·낚싯대·물뿌리개) — 자세한 설계는 강화도구_설계.md
 *   - 아이템: 다이아 도구 + 단계별 판타지 이름 "드워프의 맥박 +12" + 별(강화 단계) + 3D 모양
 *   - 아이템 데이터: servercore:village_tool = 종류(pickaxe…), servercore:tool_level = 강화 단계
 *   - 모양: 아이템 모델 servercore:village_pickaxe + custom_model_data floats[0] = 강화 단계
 *     (리소스팩이 0/5/10/15/20 으로 모델을 골라요, ⚠ tools/build_pack.py 의 TOOL_TIERS 와 약속)
 *   - 인챈트 금지 (tools.yml allow-enchant): 인챈트 테이블·모루·숫돌에 못 올려요
 *   - 관리자: /villagetool give <종류> [단계] [플레이어], /villagetool level <단계> (손에 든 도구)
 * 강화·능력·수리·대장장이 NPC 는 설계가 정해지면 여기에 더해요.
 * 설정: tools.yml
 */
public final class VillageToolFeature implements Feature, Listener {

    private final ServerCore plugin;
    private final NamespacedKey typeKey;
    private final NamespacedKey levelKey;
    private YamlConfiguration cfg = new YamlConfiguration();
    private int maxLevel = 20;
    private boolean allowEnchant = false;

    public VillageToolFeature(ServerCore plugin) {
        this.plugin = plugin;
        this.typeKey = new NamespacedKey(plugin, "village_tool");
        this.levelKey = new NamespacedKey(plugin, "tool_level");
    }

    @Override
    public void enable() {
        loadConfig();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        PluginCommand cmd = plugin.getCommand("villagetool");
        if (cmd != null) {
            ToolCommand c = new ToolCommand();
            cmd.setExecutor(c);
            cmd.setTabCompleter(c);
        }
    }

    @Override
    public void reload() {
        loadConfig();
    }

    @Override
    public void disable() { }

    private void loadConfig() {
        cfg = plugin.loadYaml("tools.yml");
        cfg.setDefaults(plugin.loadDefaultYaml("tools.yml"));
        maxLevel = Math.max(0, cfg.getInt("max-level", 20));
        allowEnchant = cfg.getBoolean("allow-enchant", false);
    }

    private String msg(String key, String def) {
        return cfg.getString("messages." + key, def);
    }

    // ---------------- 아이템 ----------------

    /** 새 마을 도구 */
    public ItemStack createTool(ToolType type, int level) {
        ItemStack it = new ItemStack(type.material);
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return it;
        meta.getPersistentDataContainer().set(typeKey, PersistentDataType.STRING, type.id);
        it.setItemMeta(meta);
        setLevel(it, level);
        return it;
    }

    /** 마을 도구의 종류 (마을 도구가 아니면 null) */
    public ToolType typeOf(ItemStack it) {
        if (it == null || it.getType().isAir() || !it.hasItemMeta()) return null;
        return ToolType.byId(it.getItemMeta().getPersistentDataContainer().get(typeKey, PersistentDataType.STRING));
    }

    public boolean isTool(ItemStack it) {
        return typeOf(it) != null;
    }

    /** 강화 단계 (마을 도구가 아니면 -1) */
    public int levelOf(ItemStack it) {
        if (typeOf(it) == null) return -1;
        Integer lv = it.getItemMeta().getPersistentDataContainer().get(levelKey, PersistentDataType.INTEGER);
        return lv == null ? 0 : lv;
    }

    public int maxLevel() {
        return maxLevel;
    }

    /** 강화 단계를 바꾸고 이름·별·모양을 다시 그려요 (마을 도구가 아니면 false) */
    public boolean setLevel(ItemStack it, int level) {
        ToolType type = typeOf(it);
        if (type == null) return false;
        int lv = Math.max(0, Math.min(maxLevel, level));
        ItemMeta meta = it.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(levelKey, PersistentDataType.INTEGER, lv);

        String name = nameFor(type, lv);
        if (lv > 0) name += cfg.getString("level-suffix", " +%level%").replace("%level%", String.valueOf(lv));
        meta.displayName(noItalic(Text.mm(name)));
        meta.lore(lore(type, lv));

        // 모양: 아이템 모델 + 강화 단계 (리소스팩이 단계에 맞는 모델을 골라요)
        if (type.model != null && plugin.resourcePackEnabled()) meta.setItemModel(Glyphs.model(type.model));
        CustomModelDataComponent cmd = meta.getCustomModelDataComponent();
        cmd.setFloats(List.of((float) lv));
        meta.setCustomModelDataComponent(cmd);
        it.setItemMeta(meta);
        return true;
    }

    /** 단계별 이름: tools.yml 의 names 에서 lv 이하 중 가장 높은 단계 (5강마다 모양과 같이 바뀌어요) */
    private String nameFor(ToolType type, int lv) {
        var sec = cfg.getConfigurationSection("tools." + type.id + ".names");
        String best = null;
        int bestAt = -1;
        if (sec != null) {
            for (String k : sec.getKeys(false)) {
                try {
                    int at = Integer.parseInt(k.trim());
                    if (at <= lv && at > bestAt) {
                        bestAt = at;
                        best = sec.getString(k);
                    }
                } catch (NumberFormatException ignored) { }
            }
        }
        return best != null ? best : type.defaultName;
    }

    private List<Component> lore(ToolType type, int lv) {
        List<String> lines = new ArrayList<>(cfg.getStringList("tools." + type.id + ".lore"));
        // 별: 한 줄에 10칸씩 (최대 단계까지)
        String on = cfg.getString("stars.filled", "<#ffd34d>★");
        String off = cfg.getString("stars.empty", "<dark_gray>☆");
        int perRow = Math.max(1, cfg.getInt("stars.per-row", 10));
        if (maxLevel > 0) {
            if (!lines.isEmpty()) lines.add("");
            for (int start = 0; start < maxLevel; start += perRow) {
                StringBuilder row = new StringBuilder();
                for (int i = start; i < Math.min(maxLevel, start + perRow); i++) row.append(i < lv ? on : off);
                lines.add(row.toString());
            }
        }
        lines.addAll(cfg.getStringList("lore-footer"));
        List<Component> out = new ArrayList<>(lines.size());
        for (String l : lines) out.add(noItalic(Text.mm(l)));
        return out;
    }

    private static Component noItalic(Component c) {
        return c.decoration(TextDecoration.ITALIC, false);
    }

    // ---------------- 인챈트 금지 ----------------

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareEnchant(PrepareItemEnchantEvent e) {
        if (!allowEnchant && isTool(e.getItem())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEnchant(EnchantItemEvent e) {
        if (!allowEnchant && isTool(e.getItem())) e.setCancelled(true);
    }

    /** 모루: 인챈트 책·이름 바꾸기·합치기 전부 막아요 (수리는 대장장이에게) */
    @EventHandler(priority = EventPriority.HIGH)
    public void onAnvil(PrepareAnvilEvent e) {
        if (!allowEnchant && hasTool(e.getInventory())) e.setResult(null);
    }

    /** 숫돌: 인챈트를 지우거나 두 도구를 합치지 못하게 */
    @EventHandler(priority = EventPriority.HIGH)
    public void onGrindstone(PrepareGrindstoneEvent e) {
        if (!allowEnchant && hasTool(e.getInventory())) e.setResult(null);
    }

    private boolean hasTool(Inventory inv) {
        for (int i = 0; i < 2; i++) if (isTool(inv.getItem(i))) return true;
        return false;
    }

    // ---------------- 관리자 명령어 ----------------

    private final class ToolCommand implements CommandExecutor, TabCompleter {

        @Override
        public boolean onCommand(CommandSender s, Command command, String label, String[] args) {
            if (!s.hasPermission("servercore.admin")) {
                s.sendMessage(Text.mm(plugin.msg("no-permission")));
                return true;
            }
            String sub = args.length == 0 ? "" : args[0].toLowerCase();
            switch (sub) {
                case "give" -> give(s, label, args);
                case "level" -> level(s, label, args);
                default -> {
                    s.sendMessage(Text.mm("<gray>/" + label + " give <종류> [단계] [플레이어]  <dark_gray>— 종류: pickaxe axe hoe fishing_rod watering_can (또는 곡괭이 도끼 괭이 낚싯대 물뿌리개)"));
                    s.sendMessage(Text.mm("<gray>/" + label + " level <단계>  <dark_gray>— 손에 든 마을 도구의 강화 단계 바꾸기"));
                }
            }
            return true;
        }

        private void give(CommandSender s, String label, String[] args) {
            ToolType type = args.length >= 2 ? ToolType.parse(args[1]) : null;
            if (type == null) {
                s.sendMessage(Text.mm("<red>종류를 적어 주세요. 예: /" + label + " give 곡괭이 5"));
                return;
            }
            int lv = 0;
            if (args.length >= 3) {
                try {
                    lv = Integer.parseInt(args[2]);
                } catch (NumberFormatException ex) {
                    s.sendMessage(Text.mm("<red>단계는 숫자로 적어 주세요. (0~" + maxLevel + ")"));
                    return;
                }
            }
            Player to = args.length >= 4 ? Bukkit.getPlayerExact(args[3]) : (s instanceof Player p ? p : null);
            if (to == null) {
                s.sendMessage(Text.mm("<red>받을 플레이어를 찾을 수 없어요."));
                return;
            }
            ItemStack it = createTool(type, lv);
            to.getInventory().addItem(it).values().forEach(left -> to.getWorld().dropItem(to.getLocation(), left));
            s.sendMessage(Text.mm("<green>" + to.getName() + "에게 마을 " + type.korean + " +" + levelOf(it) + " 을(를) 줬어요."));
        }

        private void level(CommandSender s, String label, String[] args) {
            if (!(s instanceof Player p)) {
                s.sendMessage(Text.mm("<red>게임 안에서 손에 도구를 들고 써 주세요."));
                return;
            }
            ItemStack hand = p.getInventory().getItemInMainHand();
            if (!isTool(hand)) {
                s.sendMessage(Text.mm(msg("not-tool", "<red>손에 마을 도구를 들어 주세요.")));
                return;
            }
            int lv;
            try {
                lv = Integer.parseInt(args.length >= 2 ? args[1] : "");
            } catch (NumberFormatException ex) {
                s.sendMessage(Text.mm("<red>단계를 숫자로 적어 주세요. 예: /" + label + " level 10"));
                return;
            }
            setLevel(hand, lv);
            p.getInventory().setItemInMainHand(hand);
            s.sendMessage(Text.mm("<green>강화 단계를 +" + levelOf(hand) + "(으)로 바꿨어요."));
        }

        @Override
        public List<String> onTabComplete(CommandSender s, Command command, String label, String[] args) {
            List<String> out = new ArrayList<>();
            if (!s.hasPermission("servercore.admin")) return out;
            if (args.length == 1) out.addAll(List.of("give", "level"));
            else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
                for (ToolType t : ToolType.values()) out.add(t.id);
            } else if ((args.length == 3 && args[0].equalsIgnoreCase("give")) || (args.length == 2 && args[0].equalsIgnoreCase("level"))) {
                for (int i = 0; i <= maxLevel; i += 5) out.add(String.valueOf(i));
            } else if (args.length == 4 && args[0].equalsIgnoreCase("give")) {
                for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
            }
            String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase();
            out.removeIf(o -> !o.toLowerCase().startsWith(last));
            return out;
        }
    }
}
