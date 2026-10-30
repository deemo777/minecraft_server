package com.htk.core.tools;

import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;

/** Keep vanilla targeting/eligibility and protection events; extend its capped ratio numerically. */
final class WeaponSweepListener implements Listener {
    private final VillageToolFeature tools;
    WeaponSweepListener(VillageToolFeature tools){this.tools=tools;}
    @EventHandler(priority=EventPriority.LOWEST,ignoreCancelled=true)
    public void sweep(EntityDamageByEntityEvent event) {
        if(event.getCause()!=DamageCause.ENTITY_SWEEP_ATTACK || !(event.getDamager() instanceof Player player))return;
        var item=player.getInventory().getItemInMainHand();var type=tools.typeOf(item);
        if(type==null || !type.weapon())return;
        double ratio=WeaponPerformance.sweepRatio(type,tools.levelOf(item));
        if(ratio>1 && event.getDamage()>0)event.setDamage(WeaponPerformance.extendedSweepDamage(event.getDamage(),ratio));
    }
}
