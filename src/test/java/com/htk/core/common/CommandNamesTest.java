package com.htk.core.common;
import com.htk.core.menu.MenuConfig;
import com.htk.core.tools.ToolType;
import com.htk.core.skill.Skill;
import org.bukkit.configuration.file.YamlConfiguration;

public final class CommandNamesTest {
    static int checks;
    static void check(boolean b,String message){checks++;if(!b)throw new AssertionError(message);}
    public static void main(String[] args){
        check(CommandNames.inText("<gray>/칭호 로 바꾸기").equals("<gray>/honor 로 바꾸기"),"old configured hint uses English command");
        check(CommandNames.inText("/순위게시판 create b farming").equals("/rankboard create b farming"),"long aliases converted before prefixes");
        check(CommandNames.inText("https://example.com/도감").equals("https://example.com/도감"),"URL paths preserved");
        check(CommandNames.inText("/도감상점").equals("/도감상점"),"unknown command prefixes preserved");
        check(CommandNames.configured("/마을도구 give 곡괭이 10 Steve").equals("villagetool give pickaxe 10 Steve"),"legacy menu tool command and argument migrated");
        check(CommandNames.configured("강화 material 낚싯대 16").equals("enhance material fishing_rod 16"),"legacy material argument migrated");
        check(CommandNames.configured("스킬 set Steve 전투 12").equals("skill set Steve combat 12"),"legacy skill argument migrated");
        check(CommandNames.configured("칭호 농사:30").equals("honor farming:30"),"legacy title category migrated");
        check(CommandNames.configured("land trust 농사").equals("land trust 농사"),"custom player names not rewritten");
        check(CommandNames.configured("servercore setpoint 마을").equals("servercore setpoint 마을"),"custom point names not rewritten");
        String raw="tellraw @a {\"text\": \"a  b\"}";
        check(CommandNames.configured(raw).equals(raw),"unrelated command whitespace and arguments preserved");
        check(ToolType.parse("PICKAXE")==ToolType.PICKAXE && ToolType.parse("곡괭이")==null,"public tool parser English only");
        check(Skill.of("FARMING")==Skill.FARMING && Skill.of("농사")==null,"public skill parser English only");
        var y=new YamlConfiguration();y.set("head-command","스킬");y.set("buttons.rank.slots",java.util.List.of(1));y.set("buttons.rank.command","순위 낚시");
        var menu=MenuConfig.load(y);
        check(menu.headCommand().equals("skill") && menu.buttons().getFirst().command().equals("ranking fishing"),"existing menu still operates after alias removal");
        System.out.println("English command regression checks passed: "+checks);
    }
}
