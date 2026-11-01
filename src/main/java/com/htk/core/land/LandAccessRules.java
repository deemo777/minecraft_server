package com.htk.core.land;

/** Placement and mutation policy, independent of Bukkit event ordering. */
final class LandAccessRules {
    private LandAccessRules() {}
    static boolean canBuild(boolean village,boolean claimed,boolean trusted,boolean unlocked,boolean operator,boolean bypass) {
        if(operator || bypass)return true;
        if(!claimed)return !village;
        return trusted && unlocked;
    }
    static boolean canBypassWorldGuard(boolean village,Land land,java.util.UUID player,int cx,int cz) {
        return village && land!=null && land.trusts(player) && land.has(cx,cz);
    }
    static boolean canPlaceCore(boolean village,boolean claimed,boolean coreItem) {
        return village && !claimed && coreItem;
    }
}
