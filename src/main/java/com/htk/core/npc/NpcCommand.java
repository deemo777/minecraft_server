package com.htk.core.npc;

import com.htk.core.ServerCore;
import com.htk.core.common.Text;
import com.htk.core.merchant.WeaponMerchantFeature;
import com.htk.core.mayor.MayorFeature;
import com.htk.core.shop.ShopFeature;
import com.htk.core.tools.BlacksmithFeature;
import com.htk.core.cookie.CookieFeature;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Common creation command for ServerCore NPCs. */
public final class NpcCommand implements TabExecutor {
    private final ServerCore plugin;

    public NpcCommand(ServerCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("servercore.admin")) {
            sender.sendMessage(Text.mm(plugin.msg("no-permission")));
            return true;
        }
        if (args.length != 2 || !args[0].equalsIgnoreCase("create")) {
            return usage(sender);
        }

        String type = args[1].toLowerCase(Locale.ROOT);
        String commandName;
        String[] forwarded;
        if (type.equals("mayor") && plugin.feature(MayorFeature.class) != null) {
            commandName = "mayornpc";
            forwarded = new String[]{"create"};
        } else if (type.equals("blacksmith") && plugin.feature(BlacksmithFeature.class) != null) {
            commandName = "blacksmithnpc";
            forwarded = new String[]{"create"};
        } else if (type.equals("cookie") && plugin.feature(CookieFeature.class) != null) {
            commandName = "cookienpc";
            forwarded = new String[]{"create"};
        } else if ((type.equals("weaponmerchant") || type.equals("merchant"))
                && plugin.feature(WeaponMerchantFeature.class) != null) {
            commandName = "weaponmerchantnpc";
            forwarded = new String[]{"create"};
        } else {
            ShopFeature shops = plugin.feature(ShopFeature.class);
            if (shops == null || !shops.shopIds().stream().anyMatch(type::equals)) {
                return usage(sender);
            }
            commandName = "shopnpc";
            forwarded = new String[]{"create", type};
        }

        PluginCommand target = plugin.getCommand(commandName);
        if (target == null) {
            sender.sendMessage(Text.mm("<red>NPC 기능 명령을 찾을 수 없어요."));
            return true;
        }
        if (target.getExecutor() == null) {
            sender.sendMessage(Text.mm("<red>해당 NPC 기능이 꺼져 있어요."));
            return true;
        }
        return target.getExecutor().onCommand(sender, target, label, forwarded);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("servercore.admin")) return List.of();
        if (args.length == 1) return StringUtil.copyPartialMatches(args[0], List.of("create"), new ArrayList<>());
        if (args.length == 2 && args[0].equalsIgnoreCase("create"))
            return StringUtil.copyPartialMatches(args[1], availableTypes(), new ArrayList<>());
        return List.of();
    }

    private List<String> availableTypes() {
        List<String> types = new ArrayList<>();
        if (plugin.feature(MayorFeature.class) != null) types.add("mayor");
        if (plugin.feature(BlacksmithFeature.class) != null) types.add("blacksmith");
        if (plugin.feature(CookieFeature.class) != null) types.add("cookie");
        if (plugin.feature(WeaponMerchantFeature.class) != null) types.add("weaponmerchant");
        ShopFeature shops = plugin.feature(ShopFeature.class);
        if (shops != null) types.addAll(shops.shopIds());
        return types;
    }

    private boolean usage(CommandSender sender) {
        sender.sendMessage(Text.mm("<gray>사용법: /npc create <type>"));
        sender.sendMessage(Text.mm("<dark_gray>종류: " + String.join(", ", availableTypes())));
        return true;
    }
}
