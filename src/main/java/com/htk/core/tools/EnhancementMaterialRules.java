package com.htk.core.tools;

/** Weapons share one crystal family; legacy weapon IDs remain valid. */
final class EnhancementMaterialRules {
    private EnhancementMaterialRules() {}
    static ToolType family(ToolType type) { return type!=null && type.weapon()?ToolType.TACHI:type; }
    static ToolType fromId(String id) { return "weapon".equals(id)?ToolType.TACHI:family(ToolType.byId(id)); }
    static String id(ToolType type) { return type.weapon()?"weapon":type.id; }
    static String name(ToolType type) { return type.weapon()?"무기 강화 결정":type.korean+" 강화 결정"; }
    // Reuse the existing purple crystal model; no resource-pack update is needed.
    static String model(ToolType type) { return "enhancement_"+family(type).id; }
    static boolean matches(ToolType material,ToolType tool) {
        return material!=null && tool!=null && family(material)==family(tool);
    }
}
