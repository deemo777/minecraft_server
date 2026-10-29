package com.htk.core.lobby;

import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** Snapshot of a night-vision potion, including a weaker effect waiting underneath it. */
record VisionEffect(int duration,int amplifier,boolean ambient,boolean particles,boolean icon,VisionEffect hidden) {
    static VisionEffect capture(PotionEffect effect) {
        return effect==null?null:new VisionEffect(effect.getDuration(),effect.getAmplifier(),effect.isAmbient(),
            effect.hasParticles(),effect.hasIcon(),capture(effect.getHiddenPotionEffect()));
    }
    VisionEffect remaining(int elapsed) {
        if(duration==PotionEffect.INFINITE_DURATION)return this;
        int ticks=duration-Math.max(0,elapsed);
        VisionEffect next=hidden==null?null:hidden.remaining(elapsed);
        return ticks>0?new VisionEffect(ticks,amplifier,ambient,particles,icon,next):next;
    }
    PotionEffect potion() {
        return new PotionEffect(PotionEffectType.NIGHT_VISION,duration,amplifier,ambient,particles,icon,
            hidden==null?null:hidden.potion());
    }
}
