package com.htk.core.land;

import com.htk.core.common.Text;
import com.htk.core.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * /land                       집 지도 창 (넓히기·빚 갚기·이웃) — 집터를 우클릭해도 열려요
 * /land kit                   집터 다시 받기 (집이 없을 때)
 * /land home                  집으로 이동
 * /land sethome               지금 자리를 집 스폰으로
 * /land pay [금액]            빚 갚기 (금액이 없으면 갚을 수 있는 만큼)
 * /land trust <이름>          이웃 허가 (내 땅에서 전부 할 수 있음)
 * /land untrust <이름>        허가 취소
 * /land show                  내 땅 테두리 보기
 * /land abandon confirm       집 땅 포기
 * 관리자: /land bypass (남의 땅도 만지기 켜기/끄기), /land delete <이름>, /land give <이름> (집터 주기)
 */
final class LandCommand implements TabExecutor {

    private final LandFeature f;

    LandCommand(LandFeature f) {
        this.f = f;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        boolean admin = sender.hasPermission("servercore.admin");
        if (sub.equals("delete") && admin) {
            if (args.length < 2) {
                sender.sendMessage(Text.mm("<gray>/" + label + " delete <플레이어>"));
                return true;
            }
            OfflinePlayer t = find(args[1]);
            if (t == null) sender.sendMessage(Text.mm("<red>그 플레이어를 찾을 수 없어요."));
            else f.adminDelete(sender, t);
            return true;
        }
        if (sub.equals("give") && admin) {
            Player t = args.length >= 2 ? Bukkit.getPlayerExact(args[1]) : (sender instanceof Player sp ? sp : null);
            if (t == null) {
                sender.sendMessage(Text.mm("<red>접속 중인 플레이어 이름을 적어 주세요."));
                return true;
            }
            f.giveCore(t);
            sender.sendMessage(Text.mm("<green>" + t.getName() + "에게 집터를 줬어요."));
            return true;
        }
        if (!(sender instanceof Player p)) {
            sender.sendMessage("게임 안에서만 쓸 수 있어요. (관리자: /" + label + " delete <플레이어>)");
            return true;
        }
        switch (sub) {
            case "" -> f.openGui(p);
            case "kit" -> f.kit(p);
            case "home" -> f.goHome(p);
            case "sethome" -> f.setHome(p);
            case "show" -> {
                Land land = f.landOf(p.getUniqueId());
                if (land == null) f.send(p, "no-land", "<gray>아직 집이 없어요. <white>/land</white> 에서 정할 수 있어요.");
                else f.showBorder(p, land);
            }
            case "pay" -> {
                long amount = 0;
                if (args.length >= 2) {
                    try {
                        amount = Long.parseLong(args[1].replace(",", ""));
                    } catch (NumberFormatException e) {
                        p.sendMessage(Text.mm("<red>금액은 숫자로 적어 주세요."));
                        return true;
                    }
                }
                f.pay(p, amount);
            }
            case "trust" -> {
                if (args.length < 2) {
                    p.sendMessage(Text.mm("<gray>/" + label + " trust <플레이어>"));
                    return true;
                }
                OfflinePlayer t = find(args[1]);
                if (t == null) p.sendMessage(Text.mm("<red>그 플레이어를 찾을 수 없어요. (한 번이라도 접속한 사람만)"));
                else f.trust(p, t);
            }
            case "untrust" -> {
                if (args.length < 2) {
                    p.sendMessage(Text.mm("<gray>/" + label + " untrust <플레이어>"));
                    return true;
                }
                OfflinePlayer t = find(args[1]);
                if (t == null || !f.untrust(p, t.getUniqueId(), t.getName()))
                    p.sendMessage(Text.mm("<red>허가한 이웃 중에 그 사람이 없어요."));
            }
            case "abandon" -> {
                if (args.length >= 2 && args[1].equalsIgnoreCase("confirm")) f.abandon(p);
                else p.sendMessage(Text.mm("<red>정말 집 땅을 포기하려면 <white>/" + label + " abandon confirm"));
            }
            case "bypass" -> {
                if (!admin) {
                    p.sendMessage(Text.mm(f.plugin().msg("no-permission")));
                    return true;
                }
                p.sendMessage(Text.mm(f.toggleBypass(p)
                        ? "<yellow>관리자 모드: 남의 땅도 만질 수 있어요. (다시 /" + label + " bypass 로 끄기)"
                        : "<gray>관리자 모드를 껐어요."));
            }
            case "info" -> {
                Land land = f.landOf(p.getUniqueId());
                if (land == null) {
                    f.send(p, "no-land", "<gray>아직 집이 없어요. <white>/land</white> 에서 정할 수 있어요.");
                    return true;
                }
                p.sendMessage(Text.mm("<#7acb52>🏠 내 집 <gray>땅 " + land.size() + "/" + f.maxChunks()
                        + "청크, 빚 <#ffd34d>" + Economy.format(land.debt()) + "</#ffd34d>, 이웃 " + land.members().size() + "명"));
            }
            default -> p.sendMessage(Text.mm("<gray>/" + label
                    + " [kit|home|sethome|pay [금액]|trust <이름>|untrust <이름>|show|info|abandon confirm]"));
        }
        return true;
    }

    static OfflinePlayer find(String name) {
        OfflinePlayer p = Bukkit.getPlayerExact(name);
        return p != null ? p : Bukkit.getOfflinePlayerIfCached(name);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of("kit", "home", "sethome", "pay", "trust", "untrust",
                    "show", "info", "abandon"));
            if (sender.hasPermission("servercore.admin")) subs.addAll(List.of("bypass", "delete", "give"));
            for (String s : subs) if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(s);
            return out;
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("trust") || sub.equals("delete") || sub.equals("give")) {
                for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
            } else if (sub.equals("untrust") && sender instanceof Player p) {
                Land land = f.landOf(p.getUniqueId());
                if (land != null) for (UUID id : land.members()) {
                    String n = Bukkit.getOfflinePlayer(id).getName();
                    if (n != null) out.add(n);
                }
            } else if (sub.equals("abandon")) {
                out.add("confirm");
            }
        }
        return out;
    }
}
