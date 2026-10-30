package com.htk.core.tools;

import java.util.Random;
import java.nio.file.*;

public final class NumericEffectsTest {
    private static int checks;
    private static void check(boolean value,String message) {checks++;if(!value)throw new AssertionError(message);}
    private static final class Cycle extends Random {
        private int cursor;
        @Override public int nextInt(int bound) {return cursor++%bound;}
    }
    public static void main(String[] args) throws Exception {
        for(int strength=0;strength<=9;strength++) {
            check(NumericEffects.miningBonus(strength)==(strength==0?0:strength*strength+1),"Mining speed");
            check(NumericEffects.damageBonus(strength)==(strength==0?0:.5*strength+.5),"Pure damage bonus");
            check(NumericEffects.sweepRatio(strength)==(strength==0?0:(double)strength/(strength+1)),"Sweep attribute");
            check(NumericEffects.wear((strength+1)*100,strength,new Cycle())==100,"Per-unit wear probability, including multi-damage actions");
        }
        for(int strength=0;strength<=4;strength++) {
            int total=0;var random=new Cycle();
            for(int draw=0;draw<strength+2;draw++)total+=NumericEffects.oreMultiplier(strength,random);
            check(total==1+(strength+1)*(strength+2)/2,"Exhaustive ore multiplier distribution");
            check(NumericEffects.binomial(strength,0,new Random(1))==0,"Zero probability crop bonus");
            check(NumericEffects.binomial(strength,1,new Random(1))==strength,"Guaranteed crop bonus");
        }
        double[][] expected={{27.0/7,4.5,6.5,7.5,8.5,9.5},{6,7,9,10,11,12},{72.0/7,12,14,15,16,17}};
        ToolType[] types={ToolType.DAGGER,ToolType.TACHI,ToolType.GREATSWORD};int[] levels={0,5,10,15,20,25};
        for(int t=0;t<types.length;t++)for(int l=0;l<levels.length;l++)
            check(Math.abs(WeaponPerformance.damage(types[t],levels[l])-expected[t][l])<1e-10,"Every weapon damage milestone");
        var drops=org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(Path.of("src/main/resources/numeric-drops.yml").toFile());
        for(String block:drops.getKeys(false)) {
            check(org.bukkit.Material.matchMaterial(block)!=null,"Known source block: "+block);
            String preserve=drops.getString(block+".preserve");
            if(preserve!=null)check(org.bukkit.Material.matchMaterial(preserve)!=null,"Known preserved item: "+preserve);
            var bonuses=drops.getConfigurationSection(block+".bonuses");
            if(bonuses!=null)for(String item:bonuses.getKeys(false))
                check(org.bukkit.Material.matchMaterial(item)!=null,"Known bonus item: "+item);
        }
        try(var paths=Files.list(Path.of("src/main/java/com/htk/core/tools"))) {
            for(var path:paths.filter(p->p.toString().endsWith(".java")).toList()) {
                String source=Files.readString(path);
                check(!source.contains(".addEnchant(")&&!source.contains(".addUnsafeEnchantment("),"No enchanted items or proxies: "+path);
            }
        }
        System.out.println("Numeric effect and no-enchantment regression checks passed: "+checks);
    }
}
