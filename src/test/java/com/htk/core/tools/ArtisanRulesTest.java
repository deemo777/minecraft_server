package com.htk.core.tools;

import com.htk.core.skill.Skill;

public final class ArtisanRulesTest {
    private static int checks;
    private static void check(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
    private static org.bukkit.persistence.PersistentDataContainer container(java.util.Map<org.bukkit.NamespacedKey,Object> values){
        return (org.bukkit.persistence.PersistentDataContainer)java.lang.reflect.Proxy.newProxyInstance(
            ArtisanRulesTest.class.getClassLoader(),new Class<?>[]{org.bukkit.persistence.PersistentDataContainer.class},
            (proxy,method,args)->{
                return switch(method.getName()){
                    case "set" -> {values.put((org.bukkit.NamespacedKey)args[0],args[2]);yield null;}
                    case "getOrDefault" -> values.getOrDefault(args[0],args[2]);
                    case "remove" -> {values.remove(args[0]);yield null;}
                    default -> throw new AssertionError("Unexpected persistence call: "+method.getName());
                };
            });
    }
    public static void main(String[] args){
        var empty=ArtisanRules.Progress.EMPTY;
        check(ArtisanRules.effectivePercent(3,empty)==3,"initial success rate is unchanged");
        check(Math.abs(ArtisanRules.effectivePercent(3,new ArtisanRules.Progress(1,0))-3.3)<1e-9,"each failure adds ten percent of the base rate");
        check(ArtisanRules.effectivePercent(3,new ArtisanRules.Progress(99,0))==6,"failure bonus caps at twice the base rate");
        check(ArtisanRules.effectivePercent(75,new ArtisanRules.Progress(10,0))==100,"effective success probability never exceeds 100 percent");
        var failed=ArtisanRules.attempt(24,3,empty,.99);
        check(failed.level()==24 && failed.progress().failures()==1,"failure never lowers the tool level");
        check(failed.progress().energy()==12821,"target 25 gains one seventy-eighth of full energy rounded up");
        check(ArtisanRules.energyLabel(failed.progress()).equals("1.28"),"energy display truncates instead of showing a premature guarantee");
        check(ArtisanRules.effectivePercent(10,65,new ArtisanRules.Progress(7,0))==99.99,"bonus cannot bypass the extended pity ceiling");
        check(ArtisanRules.effectivePercent(10,100,empty)==100,"custom guaranteed base rates remain guaranteed");
        check(ArtisanRules.effectivePercent(3,90,new ArtisanRules.Progress(2,0))==100,"targets below four retain their original bonus cap");
        check(ArtisanRules.effectivePercent(4,85,new ArtisanRules.Progress(7,0))==99.99,"new early pity stages retain a possible failure before full energy");
        check(ArtisanRules.attempt(3,85,empty,.99).progress().guaranteed(),"target four fills energy after one failure");
        check(ArtisanRules.attempt(4,80,empty,.99).progress().energy()==500000,"target five gains fifty percent energy per failure");
        var saved=new ArtisanRules.Progress(failed.progress().failures(),failed.progress().energy());
        check(saved.equals(failed.progress()),"integer state can be restored without floating-point loss");
        var stored=new java.util.HashMap<org.bukkit.NamespacedKey,Object>();var itemData=container(stored);
        org.bukkit.NamespacedKey toolId=new org.bukkit.NamespacedKey("servercore","tool_instance");stored.put(toolId,"tool-A");
        ArtisanProgressStore.write(itemData,24,saved);
        check(ArtisanProgressStore.read(container(new java.util.HashMap<>(stored)),24).equals(saved),"item data round-trips across a saved/reloaded container");
        check(ArtisanProgressStore.read(itemData,23).equals(empty),"energy from a different target cannot be carried into another stage");
        check(ArtisanProgressStore.read(container(new java.util.HashMap<>()),24).equals(empty),"another tool has independent energy");
        ArtisanProgressStore.write(itemData,24,empty);
        check(stored.size()==1 && stored.get(toolId).equals("tool-A"),"success clears all pity fields without deleting other tool data");
        var success=ArtisanRules.attempt(24,3,saved,0);
        check(success.level()==25 && success.progress().equals(empty),"ordinary success clears both energy and failure count");
        var full=new ArtisanRules.Progress(20,ArtisanRules.FULL);
        var guaranteed=ArtisanRules.attempt(24,0,full,1);
        check(guaranteed.level()==25 && guaranteed.guaranteed() && guaranteed.progress().equals(empty),"full energy guarantees even a custom zero-percent stage");
        check(ArtisanRules.energyLabel(new ArtisanRules.Progress(0,999999)).equals("99.99"),"below-full energy never displays 100 percent");
        check(ArtisanRules.energyLabel(full).equals("100"),"full energy is clearly displayed");
        check(new ArtisanRules.Progress(-5,-5).equals(empty),"negative saved state clamps safely");
        check(new ArtisanRules.Progress(Integer.MAX_VALUE,Integer.MAX_VALUE).guaranteed(),"overflow-sized saved energy clamps safely");
        int[] unchangedAttempts={1,2,3};
        int[] extended={1,2,4,6,8,10,12,14,16,18,19,20,40,44,48,52,56,60,64,68,72,78};
        check(ArtisanRules.failureCeiling(16,0)==200,"custom lower probability retains its longer ceiling in the middle tiers");
        check(ArtisanRules.attempt(19,10,new ArtisanRules.Progress(10,500000),.99).progress().energy()==517858,"middle-tier extension preserves earned energy");
        for(int current=0;current<25;current++){
            var progress=empty;int attempts=0;
            while(true){
                var outcome=ArtisanRules.attempt(current,UpgradeRules.success(current+1),progress,.999999999);
                attempts++;
                if(outcome.level()>current){
                    check(outcome.level()==current+1,"ceiling advances exactly one level");
                    if(current>=3)check(attempts==extended[current-3]+1 && outcome.guaranteed(),"target "+(current+1)+" reaches its configured failure ceiling exactly");
                    else check(attempts==unchangedAttempts[current],"target "+(current+1)+" retains its original attempt ceiling");
                    break;
                }
                check(outcome.level()==current,"arbitrarily unlucky streak never downgrades");
                check(outcome.progress().energy()>progress.energy(),"every failed attempt makes progress toward the ceiling");
                check(attempts<=200,"all default stages have a finite failure ceiling");progress=outcome.progress();
            }
        }
        var zero=empty;
        var earlyZero=empty;
        for(int i=0;i<20;i++)earlyZero=ArtisanRules.attempt(14,0,earlyZero,.99).progress();
        check(earlyZero.guaranteed() && ArtisanRules.attempt(14,0,earlyZero,.99).level()==15,"custom early-stage probability still uses the explicit twenty-failure ceiling");
        for(int i=0;i<199;i++)zero=ArtisanRules.attempt(24,0,zero,.99).progress();
        check(!zero.guaranteed(),"custom zero-percent extended ceiling does not fill early");
        zero=ArtisanRules.attempt(24,0,zero,.99).progress();
        check(zero.guaranteed() && zero.failures()==200,"custom zero-percent stage fills after two hundred failures");
        check(ArtisanRules.attempt(24,0,zero,.99).level()==25,"custom zero-percent ceiling completes on attempt 201");
        check(ArtisanRules.attempt(24,3,new ArtisanRules.Progress(10,500000),.99).progress().energy()==512821,"existing earned energy is retained with the new gain");
        double previous=0;
        for(String stone:new String[]{"STONE","DEEPSLATE"}) {
            check(CrystalDropRules.miningChance(stone,.02,.002,.12,100)==.02,"ordinary stone stays fixed at two percent");
        }
        for(String name:new String[]{"GRANITE","DIORITE","ANDESITE","TUFF","CALCITE","NETHERRACK","BASALT","BLACKSTONE","END_STONE","DIRT","COBBLESTONE"})
            check(CrystalDropRules.miningChance(name,.02,.002,.12,100)==0,"ineligible material never drops mining crystals: "+name);
        for(String ore:new String[]{"COAL_ORE","COPPER_ORE","IRON_ORE","GOLD_ORE","REDSTONE_ORE","LAPIS_ORE","DIAMOND_ORE"}) {
            check(CrystalDropRules.miningBase(ore)==CrystalDropRules.miningBase("DEEPSLATE_"+ore),"deepslate variant has identical ore probability");
            double initial=CrystalDropRules.miningBase(ore);
            check(Math.abs(CrystalDropRules.miningChance(ore,.02,.002,.12,10)-initial-.02)<1e-9,"ore gains two percentage points by skill level ten");
            check(Math.abs(CrystalDropRules.miningChance(ore,.02,.002,.12,100)-initial-.10)<1e-9,"ore skill bonus caps at ten percentage points");
        }
        check(CrystalDropRules.miningBase("ANCIENT_DEBRIS")==.20,"ancient debris has twenty percent base chance");
        for(int level=0;level<=100;level++) {
            double coal=CrystalDropRules.miningChance("COAL_ORE",.02,.002,.12,level);
            for(String same:new String[]{"LAPIS_ORE","DEEPSLATE_LAPIS_ORE","NETHER_GOLD_ORE","NETHER_QUARTZ_ORE"})
                check(CrystalDropRules.miningChance(same,.02,.002,.12,level)==coal,"lapis and nether ores match coal at every level");
            check(CrystalDropRules.miningChance("COPPER_ORE",.02,.002,.12,level)<coal,"copper stays below coal at every level");
            check(CrystalDropRules.miningChance("REDSTONE_ORE",.02,.002,.12,level)==CrystalDropRules.miningChance("IRON_ORE",.02,.002,.12,level),"redstone matches iron at every level");
        }
        check(CrystalDropRules.miningBase("COPPER_ORE")==.03,"copper base reduced to three percent");
        for(String rare:new String[]{"EMERALD_ORE","DEEPSLATE_EMERALD_ORE","ANCIENT_DEBRIS"}) {
            double base=CrystalDropRules.miningBase(rare), last=base;
            check(CrystalDropRules.miningChance(rare,.02,.002,.12,-1)==base,"negative rare-ore level clamps to base");
            check(Math.abs(CrystalDropRules.miningChance(rare,.02,.002,.12,1)-(rare.equals("ANCIENT_DEBRIS")?.216:.167))<1e-12,"rare-ore level-one chance");
            for(int level=0;level<=100;level++) {
                double chance=CrystalDropRules.miningChance(rare,.02,.002,.12,level);
                check(chance>=last && chance<=1,"rare-ore chance rises monotonically without exceeding one");
                check((level>=50)==(chance==1),"rare ores become guaranteed exactly at level fifty");
                check(chance==CrystalDropRules.miningChance(rare,0,0,0,level),"rare-ore curve is independent of ordinary drop caps");
                last=chance;
            }
            check(CrystalDropRules.miningChance(rare,.02,.002,.12,Integer.MAX_VALUE)==1,"oversized levels stay guaranteed");
        }
        for(int level=0;level<=100;level++){
            double chance=CrystalDropRules.chance(.02,.002,.12,level);
            check(chance>=previous && chance<=.12,"crystal probability rises monotonically to its cap");previous=chance;
        }
        check(Math.abs(CrystalDropRules.chance(.02,.002,.12,1)-.022)<1e-12,"level one crystal chance is 2.2 percent");
        check(Math.abs(CrystalDropRules.chance(.02,.002,.12,10)-.04)<1e-12,"level ten crystal chance is 4 percent");
        check(Math.abs(CrystalDropRules.chance(.02,.002,.12,20)-.06)<1e-12,"level twenty crystal chance is 6 percent");
        check(CrystalDropRules.chance(.02,.002,.12,50)==.12,"level fifty crystal chance reaches twelve percent");
        check(CrystalDropRules.chance(.02,.002,.12,-1)==.02,"missing or invalid skill level uses the base probability");
        check(CrystalDropRules.chance(Double.NaN,Double.NaN,Double.NaN,0)==.02,"non-finite configuration uses safe defaults");
        check(Math.abs(CrystalDropRules.fishingChance(.022,3)-.066)<1e-12,"fishing level one gets a threefold drop boost");
        check(Math.abs(CrystalDropRules.fishingChance(.04,3)-.12)<1e-12,"fishing level ten has twelve percent crystal chance");
        check(Math.abs(CrystalDropRules.fishingChance(.12,3)-.36)<1e-12,"fishing's capped base chance becomes thirty-six percent");
        check(CrystalDropRules.fishingChance(.9,3)==1,"fishing probability never exceeds one hundred percent");
        check(CrystalDropRules.fishingChance(.04,0)==.04,"a multiplier cannot accidentally penalize fishing");
        check(Math.abs(CrystalDropRules.fishingChance(.04,Double.NaN)-.12)<1e-12,"invalid fishing multiplier uses the threefold default");
        check(ToolType.PICKAXE.relatedSkill()==Skill.MINING && ToolType.AXE.relatedSkill()==Skill.WOODCUTTING,"mining and woodcutting map to the correct tools");
        check(ToolType.HOE.relatedSkill()==Skill.FARMING && ToolType.WATERING_CAN.relatedSkill()==Skill.FARMING
            && ToolType.FISHING_ROD.relatedSkill()==Skill.FISHING,"crop, watering and fishing rewards use the player's corresponding skill");
        for(int base:new int[]{15,20}){
            double expected=0;int ceiling=0;
            for(int current=base;current<base+5;current++){
                var progress=empty;double surviving=1;
                while(true){
                    expected+=surviving*UpgradeRules.required(current+1);ceiling++;
                    var outcome=ArtisanRules.attempt(current,UpgradeRules.success(current+1),progress,.999999999);
                    surviving*=1-outcome.percent()/100;
                    if(outcome.level()>current)break;progress=outcome.progress();
                }
            }
            System.out.printf(java.util.Locale.ROOT,"Artisan balance +%d -> +%d: mean crystals %.2f; maximum crystals %d%n",base,base+5,expected,ceiling*UpgradeRules.required(base+1));
        }
        System.out.println("Artisan and crystal-drop regression checks passed: "+checks);
    }
}
