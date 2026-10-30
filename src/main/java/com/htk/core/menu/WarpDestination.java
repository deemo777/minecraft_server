package com.htk.core.menu;

import java.util.List;
import org.bukkit.Location;

/** Minecraft compass: east +X, west -X, south +Z, north -Z. */
public enum WarpDestination {
    CENTER("야생 중앙", 0, 0, 0, 1),
    NORTH("야생 북쪽", 0, -1000, 3, 1),
    EAST("야생 동쪽", 1000, 0, 6, 1),
    WEST("야생 서쪽", -1000, 0, 0, 3),
    SOUTH("야생 남쪽", 0, 1000, 3, 3),
    RESERVED("준비 중", 0, 0, 6, 3);

    public final String label;
    public final int dx, dz;
    private final List<Integer> slots;

    WarpDestination(String label, int dx, int dz, int column, int row) {
        this.label=label;this.dx=dx;this.dz=dz;
        slots=List.of(row*9+column,row*9+column+1,row*9+column+2,
                (row+1)*9+column,(row+1)*9+column+1,(row+1)*9+column+2);
    }
    public List<Integer> slots() { return slots; }
    public boolean enabled() { return this!=RESERVED; }
    public Location offset(Location origin) { return origin.clone().add(dx,0,dz); }
    public static WarpDestination bySlot(int slot) {
        for (var destination:values()) if (destination.slots.contains(slot)) return destination;
        return null;
    }
}
