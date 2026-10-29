package com.htk.core.skill;

import com.htk.core.common.Messages;

import com.htk.core.common.Text;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * /honor               칭호 선택 창
 * /honor <category:level>    그 칭호 달기 (예: /honor farming:30)
 * /honor off           칭호 떼기
 */
final class HonorCommand implements TabExecutor {

    private final SkillFeature f;

    HonorCommand(SkillFeature f) {
        this.f = f;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            Messages.send(sender,"게임 안에서만 쓸 수 있어요.");
            return true;
        }
        UUID id = p.getUniqueId();
        if (args.length >= 1) {
            String arg = args[0].toLowerCase();
            if (arg.equals("off") || arg.equals("끄기")) {
                if(f.achievements()!=null && !f.achievements().clearSelected(p))return true;
                f.equipTitle(p, "");
                Messages.send(p,Text.mm("<gray>칭호를 뗐어요."));
                return true;
            }
            SkillFeature.HonorTitle t = f.title(arg);
            if (t == null || !f.hasTitle(id, t.id())) {
                Messages.send(p,Text.mm("<red>아직 받지 않은 칭호예요."));
                return true;
            }
            if(f.achievements()!=null && !f.achievements().clearSelected(p))return true;
            f.equipTitle(p, t.id());
            Messages.send(p,Text.mm("<gray>칭호를 <white>" + t.text() + "</white><gray>(으)로 바꿨어요."));
            return true;
        }

        new HonorGui(f, p, 0).open(p);
        return true;

    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1 || !(sender instanceof Player p)) return List.of();
        List<String> out = new ArrayList<>();
        out.add("off");
        for (Skill s : Skill.values())
            for (SkillFeature.HonorTitle t : f.titlesOf(s))
                if (f.hasTitle(p.getUniqueId(), t.id())) out.add(t.id());
        return out;
    }
}
