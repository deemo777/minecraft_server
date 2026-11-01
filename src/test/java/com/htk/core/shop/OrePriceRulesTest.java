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
        String[] ids={"coal","raw_copper","copper_ingot","raw_iron","iron_ingot","raw_gold","gold_ingot",
                "redstone","lapis_lazuli","quartz","amethyst_shard","emerald","diamond","ancient_debris",
                "netherite_scrap","netherite_ingot","flint"};
        long[] expected={10,10,25,50,100,100,200,25,25,25,150,2000,400,5000,6000,35000,25};
        check(OrePriceRules.prices().size()==ids.length,"all requested commodities are covered");
        for(int i=0;i<ids.length;i++)
            check(defaults.getLong("shops.ores.items."+ids[i]+".sell")==expected[i],"requested price: "+ids[i]);
        var old=new YamlConfiguration();old.loadFromString(defaults.saveToString());
        old.set("ore-price-revision",null);
        old.set("shops.ores.items.netherite_ingot",null);
        old.set("shops.ores.items.ancient_debris",null);
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
        var revisionOne=new YamlConfiguration();revisionOne.loadFromString(defaults.saveToString());
        revisionOne.set("ore-price-revision",1);
        revisionOne.set("shops.ores.items.ancient_debris",null);
        for(String id:ids)if(!id.equals("ancient_debris"))revisionOne.set("shops.ores.items."+id+".sell",1);
        check(OrePriceRules.migrate(revisionOne),"revision one upgrades to revision two");
        for(int i=0;i<ids.length;i++)
            check(revisionOne.getLong("shops.ores.items."+ids[i]+".sell")==expected[i],"revision one price migration: "+ids[i]);
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
