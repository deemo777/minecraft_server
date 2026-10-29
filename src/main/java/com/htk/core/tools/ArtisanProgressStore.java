package com.htk.core.tools;

import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/** Item PDC state is scoped to the next upgrade target and survives normal tool refreshes. */
final class ArtisanProgressStore {
    private static final NamespacedKey TARGET=new NamespacedKey("servercore","artisan_target");
    private static final NamespacedKey FAILURES=new NamespacedKey("servercore","artisan_failures");
    private static final NamespacedKey ENERGY=new NamespacedKey("servercore","artisan_energy");
    private ArtisanProgressStore() {}
    static ArtisanRules.Progress read(PersistentDataContainer pdc,int level){
        if(pdc.getOrDefault(TARGET,PersistentDataType.INTEGER,-1)!=level+1)return ArtisanRules.Progress.EMPTY;
        return new ArtisanRules.Progress(pdc.getOrDefault(FAILURES,PersistentDataType.INTEGER,0),
            pdc.getOrDefault(ENERGY,PersistentDataType.INTEGER,0));
    }
    static void write(PersistentDataContainer pdc,int level,ArtisanRules.Progress progress){
        if(progress.equals(ArtisanRules.Progress.EMPTY)){clear(pdc);return;}
        pdc.set(TARGET,PersistentDataType.INTEGER,level+1);
        pdc.set(FAILURES,PersistentDataType.INTEGER,progress.failures());
        pdc.set(ENERGY,PersistentDataType.INTEGER,progress.energy());
    }
    static void clear(PersistentDataContainer pdc){pdc.remove(TARGET);pdc.remove(FAILURES);pdc.remove(ENERGY);}
}
