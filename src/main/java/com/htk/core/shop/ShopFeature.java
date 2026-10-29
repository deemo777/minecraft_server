package com.htk.core.shop;

import com.htk.core.common.Messages;

import com.htk.core.ServerCore;
import com.htk.core.common.Feature;
import com.htk.core.common.Text;
import com.htk.core.economy.Economy;
import com.htk.core.economy.EconomyFeature;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.io.File;
import java.io.IOException;

/**
 * NPC 상점
 *   /shopnpc create <shop>   지금 서 있는 곳에 상점 주인(주민) 세우기. 나를 바라보게 서요
 *   /shopnpc remove          가까이(5칸) 있는 상점 주인 없애기
 *   /shop <shop> [player]  상점 열기 (관리자·콘솔용. 다른 NPC 플러그인에 연결할 때도 이걸 실행)
 * 상점 내용은 plugins/ServerCore/shops.yml
 */
public final class ShopFeature implements Feature, Listener {

    private final ServerCore plugin;
    private final EconomyFeature gold;
    private final Economy economy;
    private final NamespacedKey npcKey;
    private ShopConfig config;

    public ShopFeature(ServerCore plugin, EconomyFeature gold) {
        this.plugin = plugin;
        this.gold = gold;
        this.economy = gold.economy();
        this.npcKey = new NamespacedKey(plugin, "shop_npc");
    }

    public ServerCore plugin() { return plugin; }
    public Economy economy() { return economy; }
    public EconomyFeature gold() { return gold; }
    public List<String> shopIds() { return config.ids(); }

    String msg(String key, String def) {
        return plugin.getConfig().getString("shop.messages." + key, def);
    }

    @Override
    public void enable() {
        loadConfig();
        plugin.npcModels().registerLegacy(e -> { String id=npcShop(e);return id==null?null:role(id); });
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        PluginCommand shopCmd = plugin.getCommand("shop");
        if (shopCmd != null) {
            ShopCommand c = new ShopCommand();
            shopCmd.setExecutor(c);
            shopCmd.setTabCompleter(c);
        }
        PluginCommand npcCmd = plugin.getCommand("shopnpc");
        if (npcCmd != null) {
            NpcCommand c = new NpcCommand();
            npcCmd.setExecutor(c);
            npcCmd.setTabCompleter(c);
        }
    }

    @Override
    public void reload() {
        loadConfig();
    }

    @Override
    public void disable() { }

    private void loadConfig() {
        YamlConfiguration yaml = plugin.loadYaml("shops.yml");
        YamlConfiguration defaults = plugin.loadDefaultYaml("shops.yml");
        boolean migrated = false;
        ConfigurationSection farmPlots = defaults.getConfigurationSection("shops.farmplots");
        if (farmPlots != null && !yaml.isConfigurationSection("shops.farmplots")) {
            copySection(farmPlots, yaml, "shops.farmplots");
            migrated = true;
        }
        if ("farm_plot".equalsIgnoreCase(yaml.getString("shops.crops.items.farm_plot.product", ""))) {
            yaml.set("shops.crops.items.farm_plot", null);
            migrated = true;
        }
        if (migrated) {
            try {
                yaml.save(new File(plugin.getDataFolder(), "shops.yml"));
                plugin.getLogger().info("비옥한 농지 상점을 shops.yml에 추가했어요.");
            } catch (IOException e) {
                plugin.getLogger().warning("shops.yml 농지 상점 갱신 실패: " + e.getMessage());
            }
        }
        config = ShopConfig.load(yaml, plugin.getLogger());
    }

    private void copySection(ConfigurationSection source, YamlConfiguration target, String path) {
        for (String key : source.getKeys(false)) {
            String child = path + "." + key;
            ConfigurationSection nested = source.getConfigurationSection(key);
            if (nested == null) target.set(child, source.get(key));
            else {
                target.createSection(child);
                copySection(nested, target, child);
            }
        }
    }

    // ---------------- 열기 ----------------

    public void open(Player player, ShopConfig.Shop shop, int page) {
        economy.ensureAccount(player.getUniqueId());
        new ShopGui(this, player, shop, page).open(player);
    }

    void openLater(Player player, ShopConfig.Shop shop, int page) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) open(player, shop, page);
        });
    }

    // ---------------- 사고팔기 ----------------

    /** 꾸미지 않은 그 아이템만 (이름 붙인 것, 인챈트된 것, 골드 주머니 등은 제외) */
    private static boolean plain(ItemStack it, Material mat) {
        return it != null && it.getType() == mat && it.isSimilar(new ItemStack(mat));
    }

    int count(Player p, Material mat) {
        int n = 0;
        for (ItemStack it : p.getInventory().getStorageContents()) if (plain(it, mat)) n += it.getAmount();
        return n;
    }

    private void remove(Player p, Material mat, int amount) {
        ItemStack[] contents = p.getInventory().getStorageContents();
        for (int i = 0; i < contents.length && amount > 0; i++) {
            ItemStack it = contents[i];
            if (!plain(it, mat)) continue;
            int take = Math.min(amount, it.getAmount());
            it.setAmount(it.getAmount() - take);
            if (it.getAmount() <= 0) contents[i] = null;
            amount -= take;
        }
        p.getInventory().setStorageContents(contents);
    }

    /** 가방에 이 아이템이 몇 개 더 들어가는지 */
    private int space(Player p, ItemStack add) {
        int max = add.getMaxStackSize(), free = 0;
        for (ItemStack it : p.getInventory().getStorageContents()) {
            if (it == null || it.getType().isAir()) free += max;
            else if (it.isSimilar(add)) free += Math.max(0, max - it.getAmount());
        }
        return free;
    }

    // ---------------- 마우스 커서에 바로 쥐여 주기 (주민 교환처럼) ----------------

    /** 커서가 비었거나, 같은 아이템이 들고 있고 더 쌓을 수 있으면 true */
    private static boolean fitsCursor(Player p, ItemStack add) {
        ItemStack cur = p.getItemOnCursor();
        if (cur == null || cur.getType().isAir()) return true;
        return cur.isSimilar(add) && cur.getAmount() + add.getAmount() <= cur.getMaxStackSize();
    }

    private static void putOnCursor(Player p, ItemStack add) {
        ItemStack cur = p.getItemOnCursor();
        if (cur == null || cur.getType().isAir()) {
            p.setItemOnCursor(add);
        } else {
            cur.setAmount(cur.getAmount() + add.getAmount());
            p.setItemOnCursor(cur);
        }
    }

    private void cursorBusy(Player p) {
        p.sendActionBar(Text.mm(msg("cursor-busy", "<gray>들고 있는 아이템을 먼저 내려놓아 주세요.")));
    }

    /**
     * 팔기 (교환): 가방의 그 아이템을 내고 → 골드 주머니 아이템을 바로 받아요 (잔액에는 안 들어가요).
     * toCursor = true  : 1개 팔고 주머니를 마우스 커서에 (같은 주머니면 쌓여요)
     * toCursor = false : 가진 것 전부 팔고 주머니를 가방에
     * 하나라도 팔았으면 true
     */
    /** 레벨 보너스를 넣은 판매 금액 (n개) */
    long sellPrice(Player p, ShopConfig.Shop shop, ShopConfig.Item item, long n) {
        double bonus = 0;
        com.htk.core.skill.SkillFeature skills = plugin.feature(com.htk.core.skill.SkillFeature.class);
        if (skills != null) bonus = skills.sellBonus(p, shop.skill());
        return Math.round(item.sell() * n * (1.0 + bonus));
    }

    boolean sell(Player p, ShopConfig.Shop shop, ShopConfig.Item item, boolean toCursor) {
        if (item.sell() <= 0) return false;
        int n = Math.min(count(p, item.material()), toCursor ? 1 : Integer.MAX_VALUE);
        if (n <= 0) {
            p.sendActionBar(Text.mm(msg("nothing-to-sell", "<red>팔 물건이 없어요.")));
            return false;
        }
        long earned = sellPrice(p, shop, item, n);
        ItemStack pouch = gold.goldItem(earned);
        if (toCursor && !fitsCursor(p, pouch)) {
            cursorBusy(p);
            return false;
        }
        remove(p, item.material(), n);
        if (toCursor) putOnCursor(p, pouch);
        else give(p, pouch);
        p.sendActionBar(Text.mm(msg("sold", "<#ffd34d>%amount% <gray>골드 주머니를 받았어요 (%count%개 판매)")
                .replace("%amount%", Economy.format(earned)).replace("%count%", String.valueOf(n))));
        return true;
    }

    /**
     * 사기 (교환): 가방의 골드 주머니로 내요. 주머니가 모자라면 나머지는 잔액에서.
     * 주머니 금액이 남으면 거스름돈 주머니를 가방에 돌려줘요.
     * toCursor = true : 1개를 마우스 커서에 / false : 한 묶음을 가방에
     */
    boolean buy(Player p, ShopConfig.Item item, boolean toCursor) {
        if (item.buy() <= 0) return false;
        int amount = toCursor ? 1 : item.material().getMaxStackSize();
        long cost = item.buy() * amount;
        ItemStack bought = purchaseItem(item, amount);
        if (toCursor && !fitsCursor(p, bought)) {
            cursorBusy(p);
            return false;
        }
        if (!toCursor && space(p, bought) < amount) {
            p.sendActionBar(Text.mm(msg("inventory-full", "<red>가방에 자리가 없어요.")));
            return false;
        }
        long pouches = pouchTotal(p);
        long fromBalance = Math.max(0, cost - pouches);
        if (!economy.has(p.getUniqueId(), fromBalance)) {
            p.sendActionBar(Text.mm(msg("not-enough-gold", "<red>골드가 부족해요.")));
            return false;
        }
        long paid = takePouches(p, cost);           // 주머니를 통째로 내요 (모자랄 수도, 넘칠 수도)
        if (paid > cost) give(p, gold.goldItem(paid - cost));   // 거스름돈
        if (paid < cost) economy.withdraw(p.getUniqueId(), cost - paid);
        if (toCursor) putOnCursor(p, bought);
        else p.getInventory().addItem(bought);
        p.sendActionBar(Text.mm(msg("bought", "<gray>%count%개 구매 <dark_gray>(-%amount%)")
                .replace("%amount%", Economy.format(cost)).replace("%count%", String.valueOf(amount))));
        return true;
    }

    ItemStack purchaseItem(ShopConfig.Item item, int amount) {
        if (item.product().equalsIgnoreCase("farm_plot")) {
            com.htk.core.farm.FarmPlotFeature plots = plugin.feature(com.htk.core.farm.FarmPlotFeature.class);
            if (plots != null) return plots.createItem(amount);
        }
        return new ItemStack(item.material(), amount);
    }

    private long pouchTotal(Player p) {
        long sum = 0;
        for (ItemStack it : p.getInventory().getStorageContents()) {
            long v = gold.goldAmount(it);
            if (v > 0) sum += v * it.getAmount();
        }
        return sum;
    }

    /** 작은 주머니부터 하나씩 내서 need 이상이 되면 멈춰요. 낸 합계를 돌려줘요 */
    private long takePouches(Player p, long need) {
        ItemStack[] contents = p.getInventory().getStorageContents();
        long paid = 0;
        while (paid < need) {
            int best = -1;
            long bestValue = Long.MAX_VALUE;
            for (int i = 0; i < contents.length; i++) {
                long v = gold.goldAmount(contents[i]);
                if (v > 0 && v < bestValue) {
                    bestValue = v;
                    best = i;
                }
            }
            if (best < 0) break;
            paid += bestValue;
            ItemStack it = contents[best];
            it.setAmount(it.getAmount() - 1);
            if (it.getAmount() <= 0) contents[best] = null;
        }
        p.getInventory().setStorageContents(contents);
        return paid;
    }

    private void give(Player p, ItemStack item) {
        p.getInventory().addItem(item).values().forEach(left -> p.getWorld().dropItem(p.getLocation(), left));
    }

    // ---------------- NPC ----------------

    @EventHandler(priority=EventPriority.HIGHEST)
    public void onModelInteractAt(org.bukkit.event.player.PlayerInteractAtEntityEvent e) { onNpcClick(e); }

    private String npcShop(Entity e) {
        return e.getPersistentDataContainer().get(npcKey, PersistentDataType.STRING);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onNpcClick(PlayerInteractEntityEvent e) {
        String id = npcShop(e.getRightClicked());
        if (id == null) return;
        e.setCancelled(true);                       // 주민 거래창 대신
        if (e.getHand() != EquipmentSlot.HAND) return;   // 양손 이벤트 중 한 번만
        ShopConfig.Shop shop = config.shop(id);
        if (shop == null) {
            Messages.send(e.getPlayer(),Text.mm(msg("closed", "<gray>지금은 문을 닫았어요.")));
            return;
        }
        open(e.getPlayer(), shop, 0);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onNpcDamage(EntityDamageEvent e) {
        if (npcShop(e.getEntity()) != null) e.setCancelled(true);
    }

    /** 번개 맞아서 마녀가 되는 것 등 막기 */
    @EventHandler(priority = EventPriority.HIGH)
    public void onNpcTransform(EntityTransformEvent e) {
        if (npcShop(e.getEntity()) != null) e.setCancelled(true);
    }

    private String role(String id) {
        return switch(id) { case "fish" -> "fish";case "crops" -> "crops";case "farmplots" -> "farmplots";case "ores" -> "ores";default -> "merchant"; };
    }
    private org.bukkit.entity.Interaction spawnNpc(Location loc,ShopConfig.Shop shop) {
        return plugin.npcModels().spawn(loc,role(shop.id()),v -> {
            v.getPersistentDataContainer().set(npcKey,PersistentDataType.STRING,shop.id());
        });
    }

    // ---------------- 명령어 ----------------

    private final class ShopCommand implements TabExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!sender.hasPermission("servercore.admin")) {
                Messages.send(sender,Text.mm(plugin.msg("no-permission")));
                return true;
            }
            if (args.length == 0) {
                Messages.send(sender,Text.mm("<gray>사용법: /" + label + " <shop> [player]  <dark_gray>상점: " + String.join(", ", config.ids())));
                return true;
            }
            ShopConfig.Shop shop = config.shop(args[0]);
            if (shop == null) {
                Messages.send(sender,Text.mm("<red>그런 상점이 없어요. <gray>(" + String.join(", ", config.ids()) + ")"));
                return true;
            }
            Player target = args.length >= 2 ? Bukkit.getPlayerExact(args[1]) : (sender instanceof Player p ? p : null);
            if (target == null) {
                Messages.send(sender,Text.mm("<red>상점을 열어 줄 플레이어를 찾을 수 없어요."));
                return true;
            }
            open(target, shop, 0);
            return true;
        }

        @Override
        public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
            if (args.length == 1) return config.ids();
            return List.of();
        }
    }

    private final class NpcCommand implements TabExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!sender.hasPermission("servercore.admin")) {
                Messages.send(sender,Text.mm(plugin.msg("no-permission")));
                return true;
            }
            if (!(sender instanceof Player p)) {
                Messages.send(sender,"게임 안에서만 쓸 수 있어요.");
                return true;
            }
            if (args.length >= 2 && args[0].equalsIgnoreCase("create")) {
                ShopConfig.Shop shop = config.shop(args[1]);
                if (shop == null) {
                    Messages.send(p,Text.mm("<red>그런 상점이 없어요. <gray>(" + String.join(", ", config.ids()) + ")"));
                    return true;
                }
                // 블록 가운데에 세우고, 지금 나를 바라보게
                Location at = p.getLocation().getBlock().getLocation().add(0.5, 0, 0.5);
                at.setYaw(p.getLocation().getYaw() + 180f);
                at.setPitch(0f);
                if(spawnNpc(at,shop)==null){Messages.send(p,Text.mm("<red>NPC 생성이 차단됐어요. 월드 보호 설정을 확인하세요."));return true;}
                Messages.send(p,Text.mm("<green>상점 주인을 세웠어요: <white>" + shop.id()));
                return true;
            }
            if (args.length >= 1 && args.length <= 2
                    && (args[0].equalsIgnoreCase("remove") || args[0].equalsIgnoreCase("face"))) {
                String requestedShop = args.length == 2 ? args[1] : null;
                if (requestedShop != null && config.shop(requestedShop) == null) {
                    Messages.send(p,Text.mm("<red>그런 상점이 없어요. <gray>(" + String.join(", ", config.ids()) + ")"));
                    return true;
                }
                Entity nearest = null;
                double best = 25;   // 5칸
                for (Entity e : p.getNearbyEntities(5, 5, 5)) {
                    String shopId = npcShop(e);
                    if (shopId == null || (requestedShop != null && !requestedShop.equalsIgnoreCase(shopId))) continue;
                    double d = e.getLocation().distanceSquared(p.getLocation());
                    if (d < best) {
                        best = d;
                        nearest = e;
                    }
                }
                if (nearest == null) {
                    Messages.send(p,Text.mm("<red>5칸 안에 상점 주인이 없어요."));
                    return true;
                }
                String id = npcShop(nearest);
                if(args[0].equalsIgnoreCase("face")){plugin.npcModels().face(nearest,p.getLocation().getYaw()+180);Messages.send(p,Text.mm("<green>상인 방향을 맞췄어요."));return true;}
                plugin.npcModels().remove(nearest);
                Messages.send(p,Text.mm("<green>상점 주인을 없앴어요: <white>" + id));
                return true;
            }
            Messages.send(p,Text.mm("<gray>사용법: /" + label + " create <shop> | remove [shop] | face [shop]  <dark_gray>상점: " + String.join(", ", config.ids())));
            return true;
        }

        @Override
        public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
            if (args.length == 1) return List.of("create", "remove", "face");
            if (args.length == 2 && List.of("create", "remove", "face").contains(args[0].toLowerCase())) return config.ids();
            return new ArrayList<>();
        }
    }
}
