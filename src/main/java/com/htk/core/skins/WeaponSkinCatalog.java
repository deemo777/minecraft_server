package com.htk.core.skins;

import java.util.*;

/** Stable collection IDs. Default appearances are deliberately outside the draw pool. */
public final class WeaponSkinCatalog {
    public static final long PRICE=100_000L;
    public record Skin(String kind,String id,String name) {
        public String key(){return kind+"/"+id;}
        public String model(){return "weapons/"+key();}
    }
    private static List<Skin> skins(String kind,String... entries){
        var result=new ArrayList<Skin>();
        for(int i=0;i<entries.length;i+=2)result.add(new Skin(kind,entries[i],entries[i+1]));
        return List.copyOf(result);
    }
    private static final Map<String,List<Skin>> POOLS=Map.of(
        "dagger",skins("dagger",
            "amethyst_greatblade_dagger","자수정 비수", "azure_dagger","창공의 비수",
            "calamity_blade_dagger","재앙의 비수", "demons_blood_blade_dagger","악마의 혈검",
            "dragon_sword_dagger","용염의 비수", "gloomsteel_knife","암철 나이프",
            "iron_sai","철제 사이", "nature_sword","자연의 송곳니",
            "nocturne_dagger","야상곡", "solstice_dagger","지점의 빛",
            "spider_sword_dagger","거미의 독니", "talonbrand_dagger","불꽃 발톱",
            "vindicator","심판의 비수"),
        "tachi",skins("tachi",
            "azure_sabre","창공의 태도", "calamity_blade_thin","재앙의 태도",
            "crystal_frostblade","수정 서리검", "demonslayers_greatsword","멸마의 태도",
            "ethereal_frostblade","영원의 서리", "gloomsteel_katana","암철 태도",
            "lycanbane","늑대사냥꾼", "moonlight","월광",
            "oculus","감시자의 눈", "valhakyra","발하키라",
            "vengeance_blade","복수의 칼날", "void_oculus","공허의 눈",
            "zenith","천정의 빛"),
        "greatsword",skins("greatsword",
            "azure_greatsword","창공의 대검", "berserkers_cleaver","광전사의 절단검",
            "claymore","클레이모어", "crimson_cleaver","진홍의 절단검",
            "demonic_sword","악마의 대검", "emerald_greatcleaver","에메랄드 대검",
            "grand_claymore","왕가의 클레이모어", "ravenous_blade","탐식의 칼날",
            "revenants_gravecleaver","망령의 묘검", "runic_piercer","룬 관통검",
            "vaelith","바엘리스", "vesper","저녁별", "winterthorn","겨울 가시"));
    public static List<Skin> pool(String kind){return POOLS.getOrDefault(kind,List.of());}
    public static Skin find(String key){
        if(key==null)return null;
        return POOLS.values().stream().flatMap(List::stream).filter(s->s.key().equals(key)).findFirst().orElse(null);
    }
    public static List<Skin> remaining(String kind,Set<String> owned){
        return pool(kind).stream().filter(s->!owned.contains(s.key())).toList();
    }
    public static String model(String kind,String key,String fallback){
        Skin skin=find(key);return skin!=null && skin.kind().equals(kind)?skin.model():fallback;
    }
}
