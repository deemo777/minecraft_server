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
 * /land                       집 지도 창 (청크 해방·이웃) — 집터를 우클릭해도 열려요
 * /land kit                   집터 다시 받기 (집이 없을 때)
 * /land home                  집으로 이동
 * /land sethome               지금 자리를 집 스폰으로
 * (빚 갚기는 로비의 이장님께 — /land pay 는 안내만)
 * /land trust <이름>          이웃 허가 (내 땅에서 전부 할 수 있음)
 * /land untrust <이름>        허가 취소
 * /land show                  내 땅 테두리 보기
 * /land abandon confirm       집 땅 포기
 * /land confirm · cancel · preview   청크 해방 확인 · 취소 · 경계 다시 보기 (지도에서 + 를 누른 뒤, 채팅 버튼)
 * 관리자: /land bypass (남의 땅도 만지기 켜기/끄기), /land delete <이름>, /land give <이름> (집터 주기)
 *   /land zone pos1 · pos2         집 구역 네모의 두 모서리 (지금 서 있는 청크)
 *   /land zone add · remove        네모 안(없으면 지금 청크)을 집 구역에 넣기 · 빼기
 *   /land zone show · info         근처 집 구역 보기 · 정보
 *   /land admin addchunk <이름>    지금 서 있는 청크를 그 사람 땅에 붙이기 (빚 그대로)
 *   /land admin removechunk <이름> 지금 서 있는 청크를 그 사람 땅에서 떼기 (집터 청크 제외)
 *   /land admin setdebt <이름> <금액>
 */
final class LandCommand implements TabExecutor {

    private final LandFeature f;
    /** 관리자별 집 구역 네모 모서리 {청크x, 청크z} */
    private final java.util.Map<UUID, int[]> pos1 = new java.util.HashMap<>(), pos2 = new java.util.HashMap<>();

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
            case "pay" -> f.send(p, "pay-at-mayor", "<gray>빚은 로비의 <#f3c060>이장님</#f3c060>께 갚아요.");
            case "confirm" -> f.confirmExpand(p);
            case "cancel" -> f.cancelPending(p, true);
            case "preview" -> f.previewExpand(p);
            case "zone" -> {
                if (!admin) {
                    p.sendMessage(Text.mm(f.plugin().msg("no-permission")));
                    return true;
                }
                zone(p, label, args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "");
            }
            case "admin" -> {
                if (!admin) {
                    p.sendMessage(Text.mm(f.plugin().msg("no-permission")));
                    return true;
                }
                adminCmd(p, label, args);
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
                    + " [kit|home|sethome|trust <이름>|untrust <이름>|show|info|abandon confirm]"));
        }
        return true;
    }

    private void zone(Player p, String label, String sub) {
        int cx = p.getLocation().getBlockX() >> 4, cz = p.getLocation().getBlockZ() >> 4;
        boolean village = p.getWorld().getName().equalsIgnoreCase(LandFeature.HOME_WORLD);
        LandZones zones = f.zones();
        switch (sub) {
            case "pos1", "pos2" -> {
                (sub.equals("pos1") ? pos1 : pos2).put(p.getUniqueId(), new int[]{cx, cz});
                p.sendMessage(Text.mm("<green>" + sub + " = 청크 (" + cx + ", " + cz + ")"));
            }
            case "add", "remove" -> {
                if (!village) {
                    p.sendMessage(Text.mm("<red>마을(" + LandFeature.HOME_WORLD + ") 월드에서 해 주세요."));
                    return;
                }
                int[] a = pos1.get(p.getUniqueId()), b = pos2.get(p.getUniqueId());
                if (a == null || b == null) a = b = new int[]{cx, cz};
                boolean add = sub.equals("add");
                int n = zones.setRect(a[0], a[1], b[0], b[1], add);
                pos1.remove(p.getUniqueId());
                pos2.remove(p.getUniqueId());
                p.sendMessage(Text.mm("<green>집 구역에서 " + n + "청크를 " + (add ? "넣었어요" : "뺐어요")
                        + ". <gray>(전체 " + zones.size() + "청크)"));
                f.showZone(p);
            }
            case "show" -> f.showZone(p);
            case "info" -> {
                p.sendMessage(Text.mm(zones.isEmpty()
                        ? "<yellow>집 구역이 아직 없어요 → 마을 어디든 집을 지을 수 있어요."
                        : "<gray>집 구역 <white>" + zones.size() + "청크</white>. 지금 청크 (" + cx + ", " + cz + "): "
                          + (village && zones.contains(cx, cz) ? "<#7acb52>집 구역" : "<#e07a6a>공용지")));
            }
            default -> p.sendMessage(Text.mm("<gray>/" + label + " zone pos1 | pos2 | add | remove | show | info"
                    + "  <dark_gray>(pos1·pos2 를 안 정하면 지금 청크 하나)"));
        }
    }

    private void adminCmd(Player p, String label, String[] args) {
        String sub = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "";
        OfflinePlayer t = args.length >= 3 ? find(args[2]) : null;
        if (t == null && !sub.isEmpty() && args.length >= 3) {
            p.sendMessage(Text.mm("<red>그 플레이어를 찾을 수 없어요."));
            return;
        }
        switch (sub) {
            case "addchunk" -> { if (t != null) { f.adminAddChunk(p, t); return; } }
            case "removechunk" -> { if (t != null) { f.adminRemoveChunk(p, t); return; } }
            case "setdebt" -> {
                if (t != null && args.length >= 4) {
                    try {
                        f.adminSetDebt(p, t, Long.parseLong(args[3].replace(",", "")));
                    } catch (NumberFormatException e) {
                        p.sendMessage(Text.mm("<red>금액은 숫자로 적어 주세요."));
                    }
                    return;
                }
            }
            default -> { }
        }
        p.sendMessage(Text.mm("<gray>/" + label + " admin addchunk <이름> | removechunk <이름> | setdebt <이름> <금액>"
                + "  <dark_gray>(지금 서 있는 청크 기준)"));
    }

    static OfflinePlayer find(String name) {
        OfflinePlayer p = Bukkit.getPlayerExact(name);
        return p != null ? p : Bukkit.getOfflinePlayerIfCached(name);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of("kit", "home", "sethome", "trust", "untrust",
                    "show", "info", "abandon"));
            if (sender.hasPermission("servercore.admin")) subs.addAll(List.of("bypass", "delete", "give", "zone", "admin"));
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
            } else if (sub.equals("zone") && sender.hasPermission("servercore.admin")) {
                out.addAll(List.of("pos1", "pos2", "add", "remove", "show", "info"));
            } else if (sub.equals("admin") && sender.hasPermission("servercore.admin")) {
                out.addAll(List.of("addchunk", "removechunk", "setdebt"));
            }
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("admin") && sender.hasPermission("servercore.admin")) {
            for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
        }
        return out;
    }
}
