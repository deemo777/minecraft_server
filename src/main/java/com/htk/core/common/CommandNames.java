package com.htk.core.common;
import java.util.*;
import java.util.regex.*;

/** 등록된 명령은 영문만 사용. 기존 메뉴·안내의 명령 참조만 호환 변환한다. */
public final class CommandNames {
    private CommandNames() { }
    private static final Map<String,String> LEGACY=Map.ofEntries(
        Map.entry("무기상인","weaponmerchantnpc"),
        Map.entry("무기상점","weaponshop"),
        Map.entry("순위게시판","rankboard"),
        Map.entry("순위","ranking"),
        Map.entry("쿠키npc","cookienpc"),
        Map.entry("대장장이","blacksmithnpc"),
        Map.entry("강화","enhance"),
        Map.entry("메뉴","menu"),
        Map.entry("도감","dogam"),
        Map.entry("돈","money"),
        Map.entry("스킬","skill"),
        Map.entry("레벨","skill"),
        Map.entry("칭호","honor"),
        Map.entry("가이드","guide"),
        Map.entry("안내서","guide"),
        Map.entry("점프패드","jumppad"),
        Map.entry("마을도구","villagetool"),
        Map.entry("코어","servercore"));
    private static final Map<String,String> VALUES=Map.ofEntries(
        Map.entry("곡괭이","pickaxe"),Map.entry("도끼","axe"),Map.entry("괭이","hoe"),Map.entry("낚싯대","fishing_rod"),Map.entry("물뿌리개","watering_can"),
        Map.entry("농사","farming"),Map.entry("채광","mining"),Map.entry("벌목","woodcutting"),Map.entry("나무","woodcutting"),Map.entry("낚시","fishing"),Map.entry("전투","combat"));
    private static final Pattern DISPLAY=Pattern.compile("(?<![\\w:/])/("+
        LEGACY.keySet().stream().sorted(Comparator.comparingInt(String::length).reversed()).map(Pattern::quote).collect(java.util.stream.Collectors.joining("|"))+
        ")(?=$|[\\s<>()\\[\\]`,.:!?])");
    public static String inText(String text) {
        if(text==null)return "";
        return DISPLAY.matcher(text).replaceAll(m->Matcher.quoteReplacement("/"+LEGACY.get(m.group(1))));
    }
    /** 내부 설정용으로만 사용한다. 플레이어 입력의 파서는 영문만 허용한다. */
    public static String configured(String source) {
        if(source==null || source.isBlank())return "";
        String input=source.strip();if(input.startsWith("/"))input=input.substring(1);
        var matcher=Pattern.compile("\\S+").matcher(input);List<String> tokens=new ArrayList<>();List<int[]> spans=new ArrayList<>();
        while(matcher.find()){tokens.add(matcher.group());spans.add(new int[]{matcher.start(),matcher.end()});}
        if(tokens.isEmpty())return input;
        String root=LEGACY.getOrDefault(tokens.getFirst(),tokens.getFirst());Map<Integer,String> changes=new HashMap<>();changes.put(0,root);
        int index=switch(root.toLowerCase(Locale.ROOT)){
            case "ranking" -> 1;
            case "rankboard" -> tokens.size()>1 && tokens.get(1).equalsIgnoreCase("create")?3:-1;
            case "villagetool" -> tokens.size()>1 && tokens.get(1).equalsIgnoreCase("give")?2:-1;
            case "enhance" -> tokens.size()>1 && tokens.get(1).equalsIgnoreCase("material")?2:-1;
            case "skill" -> tokens.size()>1 && tokens.get(1).equalsIgnoreCase("set")?3:-1;
            case "honor" -> 1;
            default -> -1;
        };
        if(index>0 && index<tokens.size()) {
            String v=tokens.get(index);int colon=v.indexOf(':');String key=colon<0?v:v.substring(0,colon);
            if(VALUES.containsKey(key))changes.put(index,VALUES.get(key)+(colon<0?"":v.substring(colon)));
        }
        StringBuilder result=new StringBuilder(input);
        for(int i=tokens.size()-1;i>=0;i--)if(changes.containsKey(i))result.replace(spans.get(i)[0],spans.get(i)[1],changes.get(i));
        return result.toString();
    }
}
