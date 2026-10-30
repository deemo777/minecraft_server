package com.htk.core.tools;

import java.util.UUID;

/** Per-player capstone timers in server ticks; independent of Bukkit for boundary tests. */
final class WeaponTraitState {
    private UUID target;
    private String weapon;
    private int hits;
    private long lastHit=Long.MIN_VALUE, pursuitReady, pursuitUntil, guardUntil, pressureReady;
    boolean dagger(UUID victim,String instance,long now) {
        if(now<pursuitReady){resetCombo();return false;}
        if(!victim.equals(target)||!instance.equals(weapon)||lastHit==Long.MIN_VALUE||now-lastHit>40)hits=0;
        target=victim;weapon=instance;lastHit=now;
        if(++hits<3)return false;
        resetCombo();pursuitReady=now+100;pursuitUntil=now+40;return true;
    }
    void resetCombo(){target=null;weapon=null;hits=0;lastHit=Long.MIN_VALUE;}
    void unequip(){resetCombo();pursuitUntil=0;guardUntil=0;}
    boolean pursuit(long now){return now<pursuitUntil;}
    boolean guard(long now){return now<guardUntil;}
    boolean tachi(long now){boolean fresh=!guard(now);guardUntil=now+40;return fresh;}
    boolean pressure(long now){if(now<pressureReady)return false;pressureReady=now+60;return true;}
    static double guardedDamage(double incoming){return incoming*.9;}
}
