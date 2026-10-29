package com.htk.core.economy;

import com.htk.core.common.Messages;

import com.htk.core.ServerCore;
import com.htk.core.common.Feature;
import com.htk.core.common.Placeholders;
import com.htk.core.common.Text;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;

/**
 * 골드(G)
 *   /money                       내 골드 보기
 *   /money <amount>                골드를 "골드 주머니" 아이템으로 꺼내기 (던져서 남에게 줄 수 있어요)
 *   골드 주머니 우클릭            내 잔액에 넣기 (Shift+우클릭: 손에 든 묶음 전부)
 *   /money give|take|set <player> <amount>   관리자
 */
public final class EconomyFeature implements Feature, Listener, TabExecutor {

    private final ServerCore plugin;
    private final NamespacedKey goldKey;
    private Economy economy;
    private BukkitTask autosave;

    public EconomyFeature(ServerCore plugin) {
        this.plugin = plugin;
        this.goldKey = new NamespacedKey(plugin, "gold");
    }

    public Economy economy() { return economy; }

    @Override
    public void enable() {
        economy = new Economy(plugin);
        refundLegacyStakes();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        PluginCommand cmd = plugin.getCommand("money");
        if (cmd != null) {
            cmd.setExecutor(this);
            cmd.setTabCompleter(this);
        }
        for (Player p : plugin.getServer().getOnlinePlayers()) economy.ensureAccount(p.getUniqueId());
        autosave = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            refundLegacyStakes();
            economy.save();
        }, 600L, 600L);
        Placeholders.register("money", p -> Economy.format(economy.balance(p.getUniqueId())));
    }

    @Override
    public void reload() { }

    private void refundLegacyStakes() {
        if (!economy.refundLegacyStakes()) plugin.getLogger().severe("이전 버전의 보관 골드 환불 저장 실패: 자동 재시도합니다.");
    }

    @Override
    public void disable() {
        if (autosave != null) autosave.cancel();
        if (economy != null) { refundLegacyStakes(); economy.save(); }
    }

    private String msg(String key, String def) {
        return plugin.getConfig().getString("economy.messages." + key, def);
    }

    // ---------------- 골드 주머니 아이템 ----------------

    public ItemStack goldItem(long amount) {
        Material mat = Material.matchMaterial(plugin.getConfig().getString("economy.item-material", "GOLD_NUGGET"));
        ItemStack item = new ItemStack(mat == null ? Material.GOLD_NUGGET : mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String name = plugin.getConfig().getString("economy.item-name", "<#ffd34d><bold>%amount%");
            meta.displayName(Text.mm(name.replace("%amount%", Economy.format(amount)))
                    .decoration(TextDecoration.ITALIC, false));
            List<String> loreText = plugin.getConfig().getStringList("economy.item-lore");
            if (loreText.isEmpty()) loreText = List.of("<gray>우클릭하면 내 골드에 넣어요");
            var lore = new ArrayList<net.kyori.adventure.text.Component>();
            for (String l : loreText) lore.add(Text.mm(l).decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
            meta.getPersistentDataContainer().set(goldKey, PersistentDataType.LONG, amount);
            // 리소스팩의 G 금화 그림 (리소스팩이 없으면 원래 아이템 모양)
            if (plugin.resourcePackEnabled()) meta.setItemModel(com.htk.core.common.Glyphs.model("gold_coin"));
            meta.setEnchantmentGlintOverride(true);
            item.setItemMeta(meta);
        }
        return item;
    }

    /** 골드 주머니면 금액, 아니면 0 */
    public long goldAmount(ItemStack item) {
        if (item == null || item.getType().isAir()) return 0;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return 0;
        Long v = meta.getPersistentDataContainer().get(goldKey, PersistentDataType.LONG);
        return v == null ? 0 : v;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUseGold(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack item = e.getItem();
        long each = goldAmount(item);
        if (each <= 0) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        int count = p.isSneaking() ? item.getAmount() : 1;
        long total = each * count;
        economy.deposit(p.getUniqueId(), total);
        item.setAmount(item.getAmount() - count);
        EquipmentSlot hand = e.getHand() == null ? EquipmentSlot.HAND : e.getHand();
        p.getInventory().setItem(hand, item.getAmount() <= 0 ? null : item);
        p.sendActionBar(Text.mm(msg("deposited", "<#ffd34d>+%amount%  <gray>(보유 %balance%)")
                .replace("%amount%", Economy.format(total))
                .replace("%balance%", Economy.format(economy.balance(p.getUniqueId())))));
    }

    /** 골드 주머니는 조합 재료로 못 쓰게 (금 조각 → 금괴 방지) */
    @EventHandler(priority = EventPriority.HIGH)
    public void onCraft(PrepareItemCraftEvent e) {
        for (ItemStack it : e.getInventory().getMatrix()) {
            if (goldAmount(it) > 0) {
                e.getInventory().setResult(null);
                return;
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        economy.ensureAccount(e.getPlayer().getUniqueId());
    }

    // ---------------- /money ----------------

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length >= 1 && List.of("give", "take", "set").contains(args[0].toLowerCase())) {
            return admin(sender, label, args);
        }
        if (!(sender instanceof Player p)) {
            Messages.send(sender,"사용법: /" + label + " give|take|set <player> <amount>");
            return true;
        }
        if (args.length == 0) {
            Messages.send(p,Text.mm(msg("balance", "<gray>보유 골드: <#ffd34d>%balance%")
                    .replace("%balance%", Economy.format(economy.balance(p.getUniqueId())))));
            Messages.send(p,Text.mm("<dark_gray>/" + label + " <amount> 으로 골드 주머니를 꺼낼 수 있어요."));
            return true;
        }
        long amount = parseAmount(args[0]);
        if (amount <= 0) {
            Messages.send(p,Text.mm(msg("bad-amount", "<red>금액은 1 이상의 숫자로 적어 주세요. 예: /money 500")));
            return true;
        }
        if (!economy.withdraw(p.getUniqueId(), amount)) {
            Messages.send(p,Text.mm(msg("not-enough", "<red>골드가 부족해요. <gray>(보유 %balance%)")
                    .replace("%balance%", Economy.format(economy.balance(p.getUniqueId())))));
            return true;
        }
        p.getInventory().addItem(goldItem(amount)).values()
                .forEach(left -> p.getWorld().dropItem(p.getLocation(), left));
        Messages.send(p,Text.mm(msg("withdrawn", "<#ffd34d>%amount%<gray> 골드 주머니를 꺼냈어요. <dark_gray>(남은 골드 %balance%)")
                .replace("%amount%", Economy.format(amount))
                .replace("%balance%", Economy.format(economy.balance(p.getUniqueId())))));
        return true;
    }

    private boolean admin(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission("servercore.admin")) {
            Messages.send(sender,Text.mm(plugin.msg("no-permission")));
            return true;
        }
        if (args.length < 3) {
            Messages.send(sender,Text.mm("<gray>사용법: /" + label + " " + args[0] + " <player> <amount>"));
            return true;
        }
        OfflinePlayer target = Bukkit.getPlayerExact(args[1]);
        if (target == null) target = Bukkit.getOfflinePlayerIfCached(args[1]);
        if (target == null) {
            Messages.send(sender,Text.mm("<red>그 플레이어를 찾을 수 없어요. (한 번이라도 접속한 적이 있어야 해요)"));
            return true;
        }
        long amount = parseAmount(args[2]);
        if (amount < 0 || (amount == 0 && !args[0].equalsIgnoreCase("set"))) {
            Messages.send(sender,Text.mm("<red>금액을 숫자로 적어 주세요."));
            return true;
        }
        var id = target.getUniqueId();
        economy.ensureAccount(id);
        switch (args[0].toLowerCase()) {
            case "give" -> economy.deposit(id, amount);
            case "take" -> economy.set(id, Math.max(0, economy.balance(id) - amount));
            default -> economy.set(id, amount);
        }
        Messages.send(sender,Text.mm("<green>" + target.getName() + "의 골드: <#ffd34d>" + Economy.format(economy.balance(id))));
        return true;
    }

    /** "1,000" "1000" "1000G" 모두 허용. 잘못되면 -1 */
    private static long parseAmount(String s) {
        String t = s.replace(",", "").replace("G", "").replace("g", "").trim();
        try {
            return Long.parseLong(t);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> out = new ArrayList<>(List.of("100", "1000"));
            if (sender.hasPermission("servercore.admin")) out.addAll(List.of("give", "take", "set"));
            return out;
        }
        if (args.length == 2 && sender.hasPermission("servercore.admin")) {
            List<String> names = new ArrayList<>();
            for (Player p : plugin.getServer().getOnlinePlayers()) names.add(p.getName());
            return names;
        }
        return List.of();
    }
}
