package com.htk.core;

import com.htk.core.common.Text;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;

import java.util.List;

/** /코어 reload : config.yml, menu.yml, dogam.yml 다시 불러오기 */
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
        sender.sendMessage(Text.mm("<gray>사용법: /" + label + " reload"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission("servercore.admin")) return List.of("reload");
        return List.of();
    }
}
