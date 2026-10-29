package com.htk.core.land;

/** Placement and mutation policy, independent of Bukkit event ordering. */
final class LandAccessRules {
    private LandAccessRules() {}
    static boolean canBuild(boolean village,boolean claimed,boolean trusted,boolean unlocked,boolean operator,boolean bypass) {
        if(operator || bypass)return true;
        if(!claimed)return !village;
        return trusted && unlocked;
    }
    static boolean canPlaceCore(boolean village,boolean claimed,boolean coreItem) {
        return village && !claimed && coreItem;
    }
}
