package com.htk.core.shop;

import java.nio.file.Path;
import org.bukkit.configuration.file.YamlConfiguration;

public final class OrePriceRulesTest {
    private static int checks;
    private static void check(boolean condition,String message) {checks++;if(!condition)throw new AssertionError(message);}
    public static void main(String[] args) throws Exception {
        var defaults=YamlConfiguration.loadConfiguration(Path.of("src/main/resources/shops.yml").toFile());
        for(var entry:OrePriceRules.prices().entrySet()) {
            String path="shops.ores.items."+entry.getKey();
            check(defaults.getLong(path+".sell")==entry.getValue().sell(),"defaults and migration agree: "+entry.getKey());
            check(defaults.getString(path+".material").equals(entry.getValue().material()),"correct commodity: "+entry.getKey());
        }
        check(!OrePriceRules.migrate(defaults),"fresh defaults already use the new revision");
        check(defaults.getLong("shops.ores.items.raw_copper.sell")==4
                && defaults.getLong("shops.ores.items.copper_ingot.sell")==11,"both copper forms are halved from 8/22");
        for(String prefix:new String[]{"raw_",""}) {
            String suffix=prefix.isEmpty()?"_ingot":"";
            long copper=defaults.getLong("shops.ores.items."+prefix+"copper"+suffix+".sell");
            long iron=defaults.getLong("shops.ores.items."+prefix+"iron"+suffix+".sell");
            long gold=defaults.getLong("shops.ores.items."+prefix+"gold"+suffix+".sell");
            check(iron==2*copper && gold==2*iron,"raw and ingot metal ratios both follow 1:2:4");
        }
        long copper=defaults.getLong("shops.ores.items.copper_ingot.sell");
        long iron=defaults.getLong("shops.ores.items.iron_ingot.sell");
        long gold=defaults.getLong("shops.ores.items.gold_ingot.sell");
        long diamond=defaults.getLong("shops.ores.items.diamond.sell");
        check(defaults.getLong("shops.ores.items.coal.sell")==copper,"coal uses ingot copper as its reference");
        check(iron==2*copper && gold==2*iron && diamond==Math.round(gold*1.5),"ingot/diamond 1:2:4:6 ratios");
        check(defaults.getLong("shops.ores.items.redstone.sell")==17
                && defaults.getLong("shops.ores.items.lapis_lazuli.sell")==Math.round(copper*1.5),"16.5G redstone/lapis prices round to 17G");
        check(defaults.getLong("shops.ores.items.emerald.sell")==diamond*2,"emerald is twice diamond");
        check(defaults.getLong("shops.ores.items.netherite_ingot.sell")==diamond*5,"netherite ingot is five times diamond");
        var old=new YamlConfiguration();old.loadFromString(defaults.saveToString());
        old.set("ore-price-revision",null);
        old.set("shops.ores.items.netherite_ingot",null);
        old.set("shops.ores.items.raw_copper.sell",8);
        old.set("shops.ores.items.copper_ingot.sell",22);
        old.set("shops.ores.items.diamond.sell",300);
        old.set("shops.ores.items.raw_iron.buy",987);
        old.set("shops.ores.title","Custom ore shop");
        String otherShops=old.getConfigurationSection("shops.crops").getValues(true).toString();
        check(OrePriceRules.migrate(old),"existing settings receive the price revision");
        for(var entry:OrePriceRules.prices().entrySet())
            check(old.getLong("shops.ores.items."+entry.getKey()+".sell")==entry.getValue().sell(),"existing commodity revised: "+entry.getKey());
        check(old.getLong("shops.ores.items.raw_iron.buy")==987,"buy prices are preserved");
        check(old.getString("shops.ores.title").equals("Custom ore shop"),"custom shop title is preserved");
        check(old.getConfigurationSection("shops.crops").getValues(true).toString().equals(otherShops),"unrelated shop remains unchanged");
        for(String id:new String[]{"quartz","amethyst_shard","flint","netherite_scrap"})
            check(old.getLong("shops.ores.items."+id+".sell")==defaults.getLong("shops.ores.items."+id+".sell"),"unrequested commodity price preserved: "+id);
        old.set("shops.ores.items.coal.sell",777);
        var reloaded=new YamlConfiguration();reloaded.loadFromString(old.saveToString());
        check(!OrePriceRules.migrate(reloaded) && reloaded.getLong("shops.ores.items.coal.sell")==777,"reload does not reset later customization or halve prices again");
        var customized=new YamlConfiguration();customized.loadFromString(defaults.saveToString());
        customized.set("ore-price-revision",null);
        customized.set("shops.ores.items.raw_copper",null);
        customized.set("shops.ores.items.coal.material","STONE");
        customized.set("shops.ores.items.coal.sell",321);
        OrePriceRules.migrate(customized);
        check(!customized.contains("shops.ores.items.raw_copper"),"removed commodity is not re-enabled");
        check(customized.getLong("shops.ores.items.coal.sell")==321,"custom material mapping is not repriced as coal");
        var disabled=new YamlConfiguration();
        check(!OrePriceRules.migrate(disabled) && !disabled.contains("shops.ores"),"absent ore shop stays disabled");
        System.out.println("Ore price defaults/migration/reload/commodity preservation checks passed: "+checks);
    }
}
