package com.htk.core.tools;

import java.util.*;

/** A per-attack budget; the direct target reserves one slot before secondary selection. */
final class SweepTargets {
    private SweepTargets() {}
    static boolean ready(float charge,boolean onGround,boolean sprinting,float fallDistance) {
        return charge>=.9f && onGround && !sprinting && fallDistance<=0;
    }
    static boolean inRange(double distanceSquared,double reach) {
        return distanceSquared>=0 && distanceSquared<=reach*reach;
    }
    static final class Budget<T> {
        private final Set<T> remaining;
        Budget(Collection<T> candidates,int totalTargets,Comparator<? super T> nearestFirst) {
            remaining=new HashSet<>(candidates.stream().distinct().sorted(nearestFirst)
                    .limit(Math.max(0,totalTargets-1)).toList());
        }
        boolean claim(T target){return remaining.remove(target);}
    }
}
