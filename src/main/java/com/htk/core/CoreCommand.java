package com.htk.core;

import com.htk.core.common.Text;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * /servercore reload            : config.yml, menu.yml, dogam.yml, points.yml 다시 불러오기
 * /servercore setpoint <name>   : 지금 서 있는 곳을 이동 지점으로 저장 (menu.yml 의 teleport: <name>)
 * /servercore delpoint <name>   : 이동 지점 지우기
 * /servercore points            : 이동 지점 목록
 */
public final class CoreCommand implements TabExecutor {

    private final ServerCore plugin;

    public CoreCommand(ServerCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("servercore.admin")) {
            sender.sendMessage(Text.mm(plugin.msg("no-permission")));
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            plugin.reloadAll();
            sender.sendMessage(Text.mm(plugin.msg("reloaded")));
            return true;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("setpoint")) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage("게임 안에서만 쓸 수 있어요.");
                return true;
            }
            plugin.points().set(args[1], p.getLocation());
            var l = p.getLocation();
            sender.sendMessage(Text.mm("<green>이동 지점 <white>" + args[1].toLowerCase() + "</white> 저장: <gray>"
                    + l.getWorld().getName() + " " + l.getBlockX() + ", " + l.getBlockY() + ", " + l.getBlockZ()));
            return true;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("delpoint")) {
            boolean ok = plugin.points().remove(args[1]);
            sender.sendMessage(Text.mm(ok ? "<green>이동 지점을 지웠어요." : "<red>그런 이동 지점이 없어요."));
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("points")) {
            var names = plugin.points().names();
            sender.sendMessage(Text.mm(names.isEmpty() ? "<gray>저장된 이동 지점이 없어요."
                    : "<gray>이동 지점: <white>" + String.join(", ", names)));
            return true;
        }
        sender.sendMessage(Text.mm("<gray>사용법: /" + label + " reload | setpoint <name> | delpoint <name> | points"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("servercore.admin")) return List.of();
        if (args.length == 1) return List.of("reload", "setpoint", "delpoint", "points");
        if (args.length == 2 && (args[0].equalsIgnoreCase("setpoint") || args[0].equalsIgnoreCase("delpoint"))) {
            return plugin.points().names();
        }
        return List.of();
    }
}
