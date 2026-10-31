package com.htk.core.skill;

import com.htk.core.common.Messages;

import com.htk.core.common.Text;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * /skill                                 스킬 창 열기
 * /skill info                            내 레벨을 채팅으로
 * /skill set <player> <category> <level>     (관리자)  분야: farming mining woodcutting fishing combat
 * /skill reset <player>                 (관리자)
 */
final class SkillCommand implements TabExecutor {

    private final SkillFeature f;

    SkillCommand(SkillFeature f) {
        this.f = f;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 && sender instanceof Player p) {
            new SkillGui(f, p).open(p);
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("info")) {
            if (!(sender instanceof Player p)) {
                Messages.send(sender,"사용법: /" + label + " set <player> <category> <level> | reset <player>");
                return true;
            }
            StringBuilder sb = new StringBuilder("<gray>");
            for (Skill s : Skill.values()) {
                var pr = f.progress(p.getUniqueId(), s);
                sb.append(s.korean).append(" <white>Lv.").append(pr.level).append("</white> <dark_gray>(")
                        .append(pr.xp).append("/").append(f.need(s,pr.level)).append(")</dark_gray>  ");
            }
            Messages.send(p,Text.mm(sb.toString().trim()));
            net.kyori.adventure.text.Component prefix = f.prefix(p.getUniqueId());
            if (!prefix.equals(net.kyori.adventure.text.Component.empty()))
                Messages.send(p,Text.mm("<gray>칭호: ").append(prefix).append(Text.mm("<dark_gray>(/honor 로 바꾸기)")));
            return true;
        }
        if (!sender.hasPermission("servercore.admin")) {
            Messages.send(sender,Text.mm(f.plugin().msg("no-permission")));
            return true;
        }
        if (args[0].equalsIgnoreCase("set") && args.length >= 4) {
            OfflinePlayer t = SkillFeature.findPlayer(args[1]);
            Skill s = Skill.of(args[2]);
            int lv;
            try {
                lv = Integer.parseInt(args[3]);
            } catch (NumberFormatException e) {
                lv = -1;
            }
            if (t == null || s == null || lv < 1) {
                Messages.send(sender,Text.mm("<red>플레이어·분야·레벨을 확인해 주세요."));
                return true;
            }
            f.setLevel(t, s, lv);
            Messages.send(sender,Text.mm("<green>" + t.getName() + " " + s.korean + " Lv." + lv));
            return true;
        }
        if (args[0].equalsIgnoreCase("reset") && args.length >= 2) {
            OfflinePlayer t = SkillFeature.findPlayer(args[1]);
            if (t == null) {
                Messages.send(sender,Text.mm("<red>그 플레이어를 찾을 수 없어요."));
                return true;
            }
            f.reset(t);
            Messages.send(sender,Text.mm("<green>" + t.getName() + "의 레벨을 모두 1로 되돌렸어요."));
            return true;
        }
        Messages.send(sender,Text.mm("<gray>사용법: /" + label + " set <player> <category> <level> | reset <player>"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("servercore.admin")) return args.length == 1 ? List.of("info") : List.of();
        if (args.length == 1) return List.of("info", "set", "reset");
        if (args.length == 2) {
            List<String> names = new ArrayList<>();
            for (Player p : f.plugin().getServer().getOnlinePlayers()) names.add(p.getName());
            return names;
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("set")) {
            List<String> out = new ArrayList<>();
            for (Skill s : Skill.values()) out.add(s.key());
            return out;
        }
        return List.of();
    }
}
