package com.htk.core.tools;

import org.bukkit.Material;
import com.htk.core.skill.Skill;

/**
 * 곡괭이·도끼·괭이: +0~4 철, +5~19 다이아, +20부터 네더라이트. 나머지 바탕은 고정.
 * model: 리소스팩 아이템 모델 (⚠ tools/build_pack.py 의 VILLAGE_TOOLS 와 약속, null 이면 바닐라 모양)
 * defaultName: tools.yml 에 이름이 없을 때 (+0 이름)
 */
public enum ToolType {
    PICKAXE("pickaxe", "곡괭이", Material.DIAMOND_PICKAXE, "village_pickaxe", "<#d9d9d9><bold>광 곡괭이"),
    AXE("axe", "도끼", Material.DIAMOND_AXE, "village_axe", "<#d9d9d9><bold>광 도끼"),
    HOE("hoe", "괭이", Material.DIAMOND_HOE, "village_hoe", "<#d9d9d9><bold>광 괭이"),
    // 낚싯대는 강화 단계별 3D 모델과 던진 상태(cast) 모델을 함께 사용
    FISHING_ROD("fishing_rod", "낚싯대", Material.FISHING_ROD, "village_fishing_rod", "<#d9d9d9><bold>광 낚싯대"),
    // 단계별 1칸/좌우 3칸/3×3/4×4 물주기. 용량은 20/25/30/35/40회, +25 물 무한.
    // PDC의 물 보유 상태를 custom_model_data flags[0]에 반영합니다.
    WATERING_CAN("watering_can", "물뿌리개", Material.CARROT_ON_A_STICK, "village_watering_can", "<#d9d9d9><bold>광 물뿌리개");

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

    public Material materialAt(int level) {
        if (level >= 20) return switch (this) {
            case PICKAXE -> Material.NETHERITE_PICKAXE;
            case AXE -> Material.NETHERITE_AXE;
            case HOE -> Material.NETHERITE_HOE;
            default -> material;
        };
        if(level>=5)return material;
        return switch(this) {
            case PICKAXE -> Material.IRON_PICKAXE;
            case AXE -> Material.IRON_AXE;
            case HOE -> Material.IRON_HOE;
            default -> material;
        };
    }
    public Skill relatedSkill() {
        return switch(this) {
            case PICKAXE -> Skill.MINING;
            case AXE -> Skill.WOODCUTTING;
            case HOE,WATERING_CAN -> Skill.FARMING;
            case FISHING_ROD -> Skill.FISHING;
        };
    }
}
