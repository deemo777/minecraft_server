package com.htk.core.skill;

import com.htk.core.common.Messages;

import com.htk.core.common.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * /honor               받은 칭호 목록 (채팅에서 눌러서 달기)
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

        String equipped = f.equippedTitle(id);
        Messages.send(p,Text.mm("<#ffd34d><bold>🏅 내 칭호</bold> <gray>(눌러서 달기)"));
        boolean any = false;
        for (Skill s : Skill.values()) {
            Component line = Text.mm("<gray>" + s.korean + " ");
            boolean first = true;
            for (SkillFeature.HonorTitle t : f.titlesOf(s)) {
                boolean have = f.hasTitle(id, t.id());
                Component part;
                if (have) {
                    any = true;
                    boolean on = t.id().equals(equipped);
                    part = Text.mm((on ? "<white>▶ " : "<white>") + t.text())
                            .hoverEvent(HoverEvent.showText(Text.mm(on ? "<gray>지금 달고 있어요" : "<gray>눌러서 달기")))
                            .clickEvent(ClickEvent.runCommand("/honor " + t.id()));
                } else {
                    part = Text.mm("<dark_gray>Lv." + t.level() + " ???")
                            .hoverEvent(HoverEvent.showText(Text.mm("<gray>" + s.korean + " Lv." + t.level() + " 에 받아요")));
                }
                line = line.append(Text.mm(first ? "" : "<dark_gray> · ")).append(part);
                first = false;
            }
            Messages.send(p,line);
        }
        if(f.achievements()!=null && f.achievements().hasTitles(id)) {
            any=true;Messages.send(p,Text.mm("<gold>[업적 칭호 보기·장착]").clickEvent(ClickEvent.runCommand("/atitle")));
        }
        if (!any) Messages.send(p,Text.mm("<gray>아직 받은 칭호가 없어요. 아무 분야나 10레벨이 되면 받아요."));
        else Messages.send(p,Text.mm("<dark_gray>[칭호 떼기]").clickEvent(ClickEvent.runCommand("/honor off")));
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
