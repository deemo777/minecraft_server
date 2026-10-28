package com.htk.core.skill;

/** 레벨링 분야 (⚠ 순서 = HUD 아이콘 순서, tools/make_art.py 의 SKILL_ICONS 와 같아야 해요) */
public enum Skill {
    FARMING("농사"),
    MINING("채광"),
    WOODCUTTING("벌목"),
    FISHING("낚시"),
    COMBAT("전투");

    public final String korean;

    Skill(String korean) {
        this.korean = korean;
    }

    public String key() {
        return name().toLowerCase();
    }

    /** 기존 설정 파일의 분야 이름만 호환한다. */
    public static Skill fromConfig(String s) {
        Skill english = of(s);
        if (english != null) return english;
        for (Skill k : values()) if (k.korean.equals(s)) return k;
        return null;
    }

    public static Skill of(String s) {
        if (s == null) return null;
        for (Skill k : values()) if (k.key().equalsIgnoreCase(s)) return k;
        return null;
    }
}
