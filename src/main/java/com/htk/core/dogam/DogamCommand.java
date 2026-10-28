package com.htk.core.dogam;

import com.htk.core.common.Text;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** /dogam [page] , /dogam reset <player> */
public final class DogamCommand implements TabExecutor {

    private final DogamFeature feature;

    public DogamCommand(DogamFeature feature) {
        this.feature = feature;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || isNumber(args[0])) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("게임 안에서만 쓸 수 있어요.");
                return true;
            }
            int page = args.length == 0 ? 0 : Integer.parseInt(args[0]) - 1;
            feature.open(player, page);
            return true;
        }

        if (!sender.hasPermission("servercore.admin")) {
            sender.sendMessage(Text.mm(feature.plugin().msg("no-permission")));
            return true;
        }

        if (args[0].equalsIgnoreCase("reset") && args.length >= 2) {
            OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(args[1]);
            if (target == null) {
                sender.sendMessage(Text.mm("<red>그 플레이어를 찾을 수 없어요."));
                return true;
            }
            feature.store().get(target.getUniqueId()).clear();
            if (!target.isOnline()) feature.store().unload(target.getUniqueId()); // 바로 저장
            sender.sendMessage(Text.mm("<green>" + args[1] + "의 도감을 초기화했어요."));
            return true;
        }

        sender.sendMessage(Text.mm("<gray>사용법: /" + label + " [page] | reset <player>"));
        return true;
    }

    private static boolean isNumber(String s) {
        if (s.isEmpty() || s.length() > 4) return false;
        for (char c : s.toCharArray()) if (!Character.isDigit(c)) return false;
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("servercore.admin")) return List.of();
        if (args.length == 1) return "reset".startsWith(args[0].toLowerCase()) ? List.of("reset") : List.of();
        if (args.length == 2 && args[0].equalsIgnoreCase("reset")) {
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(args[1].toLowerCase())) names.add(p.getName());
            }
            return names;
        }
        return List.of();
    }
}
