package com.htk.core.mayor;

import com.htk.core.ServerCore;
import com.htk.core.common.Dialogs;
import com.htk.core.common.Feature;
import com.htk.core.common.Text;
import com.htk.core.economy.Economy;
import com.htk.core.land.LandFeature;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * 이장 NPC (로비)
 *   - 처음: 대화창 몇 장(다음 ▶)을 넘기면 마지막에 집터를 줘요. 중간에 닫으면 다음에 그 장부터 이어서
 *   - 그 뒤: 말을 걸면 빚 갚기 창 (빚은 이장에게만 갚아요). 집터를 잃어버렸으면 다시 받기
 *   /mayornpc create      지금 서 있는 곳에 이장 세우기 (나를 바라보게)
 *   /mayornpc remove      5칸 안의 이장 없애기
 *   /mayornpc reset <이름>  그 사람의 첫 대화를 처음부터 다시 (테스트용)
 * 대사·버튼 글자: config.yml → mayor
 */
public final class MayorFeature implements Feature, Listener {

    private final ServerCore plugin;
    private final Economy economy;
    private final NamespacedKey npcKey;
    private final NamespacedKey pageKey;      // 첫 대화 몇 장째까지 봤는지
    private final NamespacedKey doneKey;      // 첫 대화를 끝내고 집터를 받았는지
    private final NamespacedKey legacyKitKey; // 예전 방식(처음 접속하면 집터)으로 이미 받은 사람

    public MayorFeature(ServerCore plugin, Economy economy) {
        this.plugin = plugin;
        this.economy = economy;
        this.npcKey = new NamespacedKey(plugin, "mayor_npc");
        this.pageKey = new NamespacedKey(plugin, "mayor_intro_page");
        this.doneKey = new NamespacedKey(plugin, "mayor_intro_done");
        this.legacyKitKey = new NamespacedKey(plugin, "land_kit_given");
    }

    @Override
    public void enable() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        PluginCommand cmd = plugin.getCommand("mayornpc");
        if (cmd != null) {
            NpcCommand c = new NpcCommand();
            cmd.setExecutor(c);
            cmd.setTabCompleter(c);
        }
    }

    @Override
    public void reload() { }

    @Override
    public void disable() { }

    private FileConfiguration cfg() {
        return plugin.getConfig();
    }

    private String s(String key, String def) {
        return cfg().getString("mayor." + key, def);
    }

    private LandFeature land() {
        return plugin.feature(LandFeature.class);
    }

    // ---------------- 첫 대화 진행 상태 ----------------

    /** 첫 대화를 끝냈는지 (예전에 집터를 이미 받았거나 집이 있는 사람도 끝낸 걸로) */
    public boolean introDone(Player p) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        if (pdc.has(doneKey, PersistentDataType.BYTE)) return true;
        if (pdc.has(legacyKitKey, PersistentDataType.BYTE)) return true;
        LandFeature land = land();
        return land != null && land.landOf(p.getUniqueId()) != null;
    }

    private int page(Player p) {
        Integer v = p.getPersistentDataContainer().get(pageKey, PersistentDataType.INTEGER);
        return v == null ? 0 : v;
    }

    private void setPage(Player p, int page) {
        p.getPersistentDataContainer().set(pageKey, PersistentDataType.INTEGER, page);
    }

    private void resetIntro(Player p) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.remove(pageKey);
        pdc.remove(doneKey);
        pdc.remove(legacyKitKey);
    }

    /** config 의 mayor.intro : 장(page)마다 줄 목록 */
    private List<List<String>> introPages() {
        List<List<String>> pages = new ArrayList<>();
        for (Object o : cfg().getList("mayor.intro", List.of())) {
            if (o instanceof List<?> lines) {
                List<String> page = new ArrayList<>();
                for (Object l : lines) page.add(String.valueOf(l));
                pages.add(page);
            } else if (o != null) {
                pages.add(List.of(String.valueOf(o)));
            }
        }
        if (pages.isEmpty()) pages.add(List.of("<white>어서 오게, 새 이웃! 이 집터를 받게나."));
        return pages;
    }

    // ---------------- 말 걸기 ----------------

    public void talk(Player p) {
        if (!introDone(p)) showIntro(p, Math.min(page(p), introPages().size() - 1));
        else showOffice(p);
    }

    private void showIntro(Player p, int page) {
        List<List<String>> pages = introPages();
        setPage(p, page);
        boolean last = page >= pages.size() - 1;

        ActionButton next = last
                ? Dialogs.button(plugin, Text.mm(s("give-label", "<#7acb52><bold>🏠 집터 받기")), null, 150, this::finishIntro)
                : Dialogs.button(plugin, Text.mm(s("next-label", "다음 ▶")), null, 150, pl -> showIntro(pl, page + 1));
        ActionButton later = Dialogs.close(Text.mm(s("later-label", "<gray>나중에")), 100);

        String counter = s("page-format", "<gray>(%page%/%pages%)")
                .replace("%page%", String.valueOf(page + 1)).replace("%pages%", String.valueOf(pages.size()));
        p.showDialog(dialog(Text.mm(s("title", "<#6b4226>이장님") + " " + counter), pages.get(page),
                List.of(next), later, 1, !last));
        if (page == 0) p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_AMBIENT, 0.8f, 1f);
    }

    private void finishIntro(Player p) {
        p.closeDialog();
        if (introDone(p)) return;                     // 두 번 누른 경우
        p.getPersistentDataContainer().set(doneKey, PersistentDataType.BYTE, (byte) 1);
        LandFeature land = land();
        if (land != null) land.giveCore(p);
        p.sendMessage(Text.mm(s("messages.core-given",
                "<#f3c060>🏠 <gray>집터를 받았어요! 마을에서 마음에 드는 곳에 설치하면 거기가 내 집이 돼요.")));
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.4f);
    }

    /** 첫 대화가 끝난 뒤: 빚 갚기 창 */
    private void showOffice(Player p) {
        LandFeature land = land();
        long debt = land == null ? 0 : land.debtOf(p.getUniqueId());
        boolean hasLand = land != null && land.landOf(p.getUniqueId()) != null;
        long balance = economy.balance(p.getUniqueId());

        List<String> body = new ArrayList<>();
        List<ActionButton> buttons = new ArrayList<>();
        if (!hasLand) {
            body.addAll(cfg().getStringList("mayor.office.no-land"));
            if (land != null && !land.hasCoreItem(p)) {
                buttons.add(Dialogs.button(plugin, Text.mm(s("office.kit-label", "<#f3c060>🏠 집터 다시 받기")), null, 200, pl -> {
                    pl.closeDialog();
                    land.kit(pl);
                }));
            }
        } else if (debt <= 0) {
            body.addAll(cfg().getStringList("mayor.office.no-debt"));
        } else {
            for (String l : cfg().getStringList("mayor.office.debt"))
                body.add(l.replace("%debt%", Economy.format(debt)).replace("%balance%", Economy.format(balance)));
            for (Object o : cfg().getList("mayor.pay-amounts", List.of(1000, 10000))) {
                long amount;
                try {
                    amount = Long.parseLong(String.valueOf(o).replace(",", "").trim());
                } catch (NumberFormatException e) {
                    continue;
                }
                if (amount <= 0 || amount >= debt) continue;
                String label = s("office.pay-label", "<#ffd34d>%amount% 갚기").replace("%amount%", Economy.format(amount));
                buttons.add(payButton(label, amount, balance >= amount));
            }
            long all = Math.min(debt, balance);
            String allLabel = all >= debt
                    ? s("office.pay-all-label", "<#ffd34d><bold>전부 갚기 (%amount%)")
                    : s("office.pay-max-label", "<#ffd34d>가진 만큼 갚기 (%amount%)");
            buttons.add(payButton(allLabel.replace("%amount%", Economy.format(all)), 0, all > 0));
        }
        if (body.isEmpty()) body.add("<white>잘 지내고 있나?");
        ActionButton close = Dialogs.close(Text.mm(s("office.close-label", "<gray>닫기")), 100);
        p.showDialog(dialog(Text.mm(s("title", "<#6b4226>이장님")), body, buttons, close, 1, true));
    }

    private ActionButton payButton(String label, long amount, boolean enough) {
        String shown = enough ? label : "<dark_gray>" + Text.plain(label);
        return Dialogs.button(plugin, Text.mm(shown), null, 200, pl -> {
            LandFeature land = land();
            if (land != null) land.payDebt(pl, amount);
            showOffice(pl);                            // 남은 빚으로 다시 그리기
        });
    }

    private Dialog dialog(net.kyori.adventure.text.Component title, List<String> lines,
                          List<ActionButton> buttons, ActionButton exit, int columns, boolean waitForResponse) {
        DialogBase base = DialogBase.builder(title)
                .canCloseWithEscape(true)
                .pause(false)
                .afterAction(waitForResponse ? DialogBase.DialogAfterAction.WAIT_FOR_RESPONSE
                                             : DialogBase.DialogAfterAction.CLOSE)
                .body(List.of(DialogBody.plainMessage(Text.lines(lines))))
                .build();
        List<ActionButton> list = buttons.isEmpty() ? List.of(exit) : buttons;
        ActionButton exitButton = buttons.isEmpty() ? null : exit;
        return Dialog.create(b -> b.empty().base(base).type(DialogType.multiAction(list, exitButton, columns)));
    }

    // ---------------- NPC ----------------

    private boolean isMayor(Entity e) {
        return e.getPersistentDataContainer().has(npcKey, PersistentDataType.BYTE);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(PlayerInteractEntityEvent e) {
        if (!isMayor(e.getRightClicked())) return;
        e.setCancelled(true);
        if (e.getHand() != EquipmentSlot.HAND) return;
        talk(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDamage(EntityDamageEvent e) {
        if (isMayor(e.getEntity())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onTransform(EntityTransformEvent e) {
        if (isMayor(e.getEntity())) e.setCancelled(true);
    }

    /** 아직 이장님을 안 만났으면 안내 */
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (introDone(p)) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline() && !introDone(p))
                p.sendMessage(Text.mm(s("messages.join-hint", "<#f3c060>🌾 <gray>로비의 <#f3c060>이장님</#f3c060>께 말을 걸어 보세요. 집터를 주실 거예요!")));
        }, 60L);
    }

    private Villager spawnNpc(Location loc) {
        return loc.getWorld().spawn(loc, Villager.class, v -> {
            v.getPersistentDataContainer().set(npcKey, PersistentDataType.BYTE, (byte) 1);
            v.customName(Text.mm(s("npc-name", "<#f3c060><bold>이장님")));
            v.setCustomNameVisible(true);
            v.setAI(false);
            v.setInvulnerable(true);
            v.setSilent(true);
            v.setCollidable(false);
            v.setPersistent(true);
            v.setRemoveWhenFarAway(false);
            var prof = Registry.VILLAGER_PROFESSION.get(NamespacedKey.minecraft(s("npc-profession", "cartographer").toLowerCase()));
            if (prof != null) v.setProfession(prof);
            v.setVillagerLevel(5);
        });
    }

    private final class NpcCommand implements TabExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!sender.hasPermission("servercore.admin")) {
                sender.sendMessage(Text.mm(plugin.msg("no-permission")));
                return true;
            }
            String sub = args.length == 0 ? "" : args[0].toLowerCase();
            if (sub.equals("reset") && args.length >= 2) {
                Player t = Bukkit.getPlayerExact(args[1]);
                if (t == null) {
                    sender.sendMessage(Text.mm("<red>접속 중인 플레이어 이름을 적어 주세요."));
                    return true;
                }
                resetIntro(t);
                sender.sendMessage(Text.mm("<green>" + t.getName() + "의 이장님 첫 대화를 처음으로 되돌렸어요."
                        + " <gray>(집이 이미 있으면 첫 대화는 건너뛰어요)"));
                return true;
            }
            if (!(sender instanceof Player p)) {
                sender.sendMessage("게임 안에서만 쓸 수 있어요.");
                return true;
            }
            if (sub.equals("create")) {
                Location at = p.getLocation().getBlock().getLocation().add(0.5, 0, 0.5);
                at.setYaw(p.getLocation().getYaw() + 180f);
                at.setPitch(0f);
                spawnNpc(at);
                p.sendMessage(Text.mm("<green>이장님을 세웠어요."));
                return true;
            }
            if (sub.equals("remove")) {
                Entity nearest = null;
                double best = 25;
                for (Entity e : p.getNearbyEntities(5, 5, 5)) {
                    if (!isMayor(e)) continue;
                    double d = e.getLocation().distanceSquared(p.getLocation());
                    if (d < best) {
                        best = d;
                        nearest = e;
                    }
                }
                if (nearest == null) {
                    p.sendMessage(Text.mm("<red>5칸 안에 이장님이 없어요."));
                    return true;
                }
                nearest.remove();
                p.sendMessage(Text.mm("<green>이장님을 없앴어요."));
                return true;
            }
            p.sendMessage(Text.mm("<gray>사용법: /" + label + " create | remove | reset <플레이어>"));
            return true;
        }

        @Override
        public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
            if (!sender.hasPermission("servercore.admin")) return List.of();
            if (args.length == 1) return List.of("create", "remove", "reset");
            if (args.length == 2 && args[0].equalsIgnoreCase("reset")) {
                List<String> names = new ArrayList<>();
                for (Player pl : Bukkit.getOnlinePlayers()) names.add(pl.getName());
                return names;
            }
            return List.of();
        }
    }
}
