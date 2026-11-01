package com.htk.core.shop;

import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.configuration.file.YamlConfiguration;

/** One-time ore price revision; later administrator changes remain editable. */
final class OrePriceRules {
    static final int REVISION=2;
    record Price(String material,long sell) {}
    static Map<String,Price> prices() {
        var prices=new LinkedHashMap<String,Price>();
        prices.put("coal",new Price("COAL",10));
        prices.put("raw_copper",new Price("RAW_COPPER",10));
        prices.put("copper_ingot",new Price("COPPER_INGOT",25));
        prices.put("raw_iron",new Price("RAW_IRON",50));
        prices.put("iron_ingot",new Price("IRON_INGOT",100));
        prices.put("raw_gold",new Price("RAW_GOLD",100));
        prices.put("gold_ingot",new Price("GOLD_INGOT",200));
        prices.put("redstone",new Price("REDSTONE",25));
        prices.put("lapis_lazuli",new Price("LAPIS_LAZULI",25));
        prices.put("quartz",new Price("QUARTZ",25));
        prices.put("amethyst_shard",new Price("AMETHYST_SHARD",150));
        prices.put("diamond",new Price("DIAMOND",400));
        prices.put("emerald",new Price("EMERALD",2000));
        prices.put("ancient_debris",new Price("ANCIENT_DEBRIS",5000));
        prices.put("netherite_scrap",new Price("NETHERITE_SCRAP",6000));
        prices.put("netherite_ingot",new Price("NETHERITE_INGOT",35000));
        prices.put("flint",new Price("FLINT",25));
        return prices;
    }
    static boolean migrate(YamlConfiguration config) {
        if(config.getInt("ore-price-revision",0)>=REVISION
                || !config.isConfigurationSection("shops.ores.items"))return false;
        prices().forEach((id,price)->{
            String path="shops.ores.items."+id;
            if((id.equals("netherite_ingot") || id.equals("ancient_debris")) && !config.contains(path)) {
                config.set(path+".material",price.material);
                config.set(path+".sell",price.sell);return;
            }
            // Keep other removed commodities disabled and preserve custom material mappings.
            if(config.isConfigurationSection(path)
                    && price.material.equalsIgnoreCase(config.getString(path+".material",id)))
                config.set(path+".sell",price.sell);
        });
        config.set("ore-price-revision",REVISION);
        return true;
    }
}
