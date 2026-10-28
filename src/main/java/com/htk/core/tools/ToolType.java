package com.htk.core.tools;

import org.bukkit.Material;

/**
 * 마을 도구 5종. 바탕은 진짜 다이아 도구(낚싯대는 낚싯대)라 캐기·내구도·수리는 바닐라 그대로예요.
 * model: 리소스팩 아이템 모델 (⚠ tools/build_pack.py 의 VILLAGE_TOOLS 와 약속, null 이면 바닐라 모양)
 * defaultName: tools.yml 에 이름이 없을 때 (+0 이름)
 */
public enum ToolType {
    PICKAXE("pickaxe", "곡괭이", Material.DIAMOND_PICKAXE, "village_pickaxe", "<#d9d9d9><bold>돌울림"),
    AXE("axe", "도끼", Material.DIAMOND_AXE, "village_axe", "<#d9d9d9><bold>나무꾼의 서약"),
    HOE("hoe", "괭이", Material.DIAMOND_HOE, "village_hoe", "<#d9d9d9><bold>흙내음"),
    // 낚싯대는 강화 단계별 3D 모델과 던진 상태(cast) 모델을 함께 사용
    FISHING_ROD("fishing_rod", "낚싯대", Material.FISHING_ROD, "village_fishing_rod", "<#d9d9d9><bold>잔물결"),
    // 물뿌리개: 나중에 농사를 "물을 하나하나 줘야 젖은 밭" 으로 바꿀 때 쓰는 도구. 지금은 아이템만
    //   바탕은 우클릭해도 아무 일도 안 하는 당근 낚싯대 (내구도 막대를 물 양으로 쓸 수 있게)
    WATERING_CAN("watering_can", "물뿌리개", Material.CARROT_ON_A_STICK, "village_watering_can", "<#d9d9d9><bold>이슬방울");

    public final String id;
    public final String korean;
    public final Material material;
    public final String model;
    final String defaultName;

    ToolType(String id, String korean, Material material, String model, String defaultName) {
        this.id = id;
        this.korean = korean;
        this.material = material;
        this.model = model;
        this.defaultName = defaultName;
    }

    /** 영문 도구 ID (예: pickaxe) */
    public static ToolType parse(String s) {
        if (s == null) return null;
        for (ToolType t : values()) if (t.id.equalsIgnoreCase(s)) return t;
        return null;
    }

    public static ToolType byId(String id) {
        for (ToolType t : values()) if (t.id.equals(id)) return t;
        return null;
    }
}
