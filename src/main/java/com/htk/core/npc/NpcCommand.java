package com.htk.core.npc;

import com.htk.core.common.Messages;

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
import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Common management command for ServerCore NPCs. */
public final class NpcCommand implements TabExecutor {
    private final ServerCore plugin;

    public NpcCommand(ServerCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("servercore.admin")) {
            Messages.send(sender,Text.mm(plugin.msg("no-permission")));
            return true;
        }
        if (args.length == 0) return usage(sender);
        String action = args[0].toLowerCase(Locale.ROOT);
        if (action.equals("reset") && args.length == 3 && args[1].equalsIgnoreCase("mayor")) {
            return dispatch(sender, label, "mayor", new String[]{"reset", args[2]});
        }
        if (!List.of("create", "remove", "face").contains(action) || args.length != 2) {
            return usage(sender);
        }
        return dispatch(sender, label, args[1], new String[]{action});
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("servercore.admin")) return List.of();
        if (args.length == 1) return StringUtil.copyPartialMatches(args[0], List.of("create", "remove", "face", "reset"), new ArrayList<>());
        if (args.length == 2 && List.of("create", "remove", "face").contains(args[0].toLowerCase(Locale.ROOT)))
            return StringUtil.copyPartialMatches(args[1], availableTypes(), new ArrayList<>());
        if (args.length == 2 && args[0].equalsIgnoreCase("reset"))
            return StringUtil.copyPartialMatches(args[1], List.of("mayor"), new ArrayList<>());
        if (args.length == 3 && args[0].equalsIgnoreCase("reset") && args[1].equalsIgnoreCase("mayor")) {
            List<String> names = Bukkit.getOnlinePlayers().stream().map(org.bukkit.entity.Player::getName).toList();
            return StringUtil.copyPartialMatches(args[2], names, new ArrayList<>());
        }
        return List.of();
    }

    private boolean dispatch(CommandSender sender, String label, String rawType, String[] forwarded) {
        String type = rawType.toLowerCase(Locale.ROOT);
        String commandName;
        if (type.equals("mayor") && plugin.feature(MayorFeature.class) != null) commandName = "mayornpc";
        else if (type.equals("blacksmith") && plugin.feature(BlacksmithFeature.class) != null) commandName = "blacksmithnpc";
        else if (type.equals("repairer") && plugin.feature(com.htk.core.tools.RepairFeature.class) != null) commandName = "repairnpc";
        else if (type.equals("cookie") && plugin.feature(CookieFeature.class) != null) commandName = "cookienpc";
        else if ((type.equals("weaponmerchant") || type.equals("merchant"))
                && plugin.feature(WeaponMerchantFeature.class) != null) commandName = "weaponmerchantnpc";
        else {
            ShopFeature shops = plugin.feature(ShopFeature.class);
            if (shops == null || shops.shopIds().stream().noneMatch(type::equals)) return usage(sender);
            commandName = "shopnpc";
            forwarded = new String[]{forwarded[0], type};
        }

        PluginCommand target = plugin.getCommand(commandName);
        if (target == null) {
            Messages.send(sender,Text.mm("<red>NPC 기능 명령을 찾을 수 없어요."));
            return true;
        }
        if (target.getExecutor() == null) {
            Messages.send(sender,Text.mm("<red>해당 NPC 기능이 꺼져 있어요."));
            return true;
        }
        return target.getExecutor().onCommand(sender, target, label, forwarded);
    }

    private List<String> availableTypes() {
        List<String> types = new ArrayList<>();
        if (plugin.feature(MayorFeature.class) != null) types.add("mayor");
        if (plugin.feature(BlacksmithFeature.class) != null) types.add("blacksmith");
        if (plugin.feature(com.htk.core.tools.RepairFeature.class) != null) types.add("repairer");
        if (plugin.feature(CookieFeature.class) != null) types.add("cookie");
        if (plugin.feature(WeaponMerchantFeature.class) != null) types.add("weaponmerchant");
        ShopFeature shops = plugin.feature(ShopFeature.class);
        if (shops != null) types.addAll(shops.shopIds());
        return types;
    }

    private boolean usage(CommandSender sender) {
        Messages.send(sender,Text.mm("<gray>사용법: /npc <create|remove|face> <type>"));
        Messages.send(sender,Text.mm("<gray>첫 대화 초기화: /npc reset mayor <player>"));
        Messages.send(sender,Text.mm("<dark_gray>종류: " + String.join(", ", availableTypes())));
        return true;
    }
}
