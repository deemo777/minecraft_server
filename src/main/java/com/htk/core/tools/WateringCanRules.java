package com.htk.core.tools;

import java.util.ArrayList;
import java.util.List;

/** Water capacity, enhancement footprint and legacy item migration. */
public final class WateringCanRules {
    private WateringCanRules() {}

    public static boolean infinite(int level) { return level >= 25; }

    public static boolean canWater(int uses, int level) { return infinite(level) || uses > 0; }

    public static int afterUse(int uses, int level) {
        int bounded = Math.clamp(uses, 0, capacity(level));
        return infinite(level) ? bounded : Math.max(0, bounded - 1);
    }

    public static int capacity(int level) {
        return 20 + Math.clamp(level, 0, 25);
    }

    public static int remaining(Integer stored, boolean legacyFull, int level) {
        int capacity = capacity(level);
        return stored == null ? (legacyFull ? capacity : 0) : Math.clamp(stored, 0, capacity);
    }

    public record Offset(int x, int z) {}

    public static double expectedCells(int level){return Progression.value(level,1,3,9,16,20,25);}
    public static String areaLabel(int level){return "평균 "+Progression.label(expectedCells(level))+"칸";}
    public static List<Offset> offsets(int level,float yaw){return offsets(level,yaw,java.util.concurrent.ThreadLocalRandom.current().nextDouble());}
    public static List<Offset> offsets(int level,float yaw,double roll){
        return WorkFootprint.offsets(true,Progression.count(expectedCells(level),roll),yaw).stream().map(p->new Offset(p[0],p[1])).toList();
    }
}
