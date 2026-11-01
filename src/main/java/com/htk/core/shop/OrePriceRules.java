package com.htk.core.shop;

import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.configuration.file.YamlConfiguration;

/** One-time ore price revision; later administrator changes remain editable. */
final class OrePriceRules {
    static final int REVISION=1;
    record Price(String material,long sell) {}
    static Map<String,Price> prices() {
        var prices=new LinkedHashMap<String,Price>();
        prices.put("coal",new Price("COAL",11));
        prices.put("raw_copper",new Price("RAW_COPPER",4));
        prices.put("copper_ingot",new Price("COPPER_INGOT",11));
        prices.put("raw_iron",new Price("RAW_IRON",8));
        prices.put("iron_ingot",new Price("IRON_INGOT",22));
        prices.put("raw_gold",new Price("RAW_GOLD",16));
        prices.put("gold_ingot",new Price("GOLD_INGOT",44));
        prices.put("redstone",new Price("REDSTONE",17));
        prices.put("lapis_lazuli",new Price("LAPIS_LAZULI",17));
        prices.put("diamond",new Price("DIAMOND",66));
        prices.put("emerald",new Price("EMERALD",132));
        prices.put("netherite_ingot",new Price("NETHERITE_INGOT",330));
        return prices;
    }
    static boolean migrate(YamlConfiguration config) {
        if(config.getInt("ore-price-revision",0)>=REVISION
                || !config.isConfigurationSection("shops.ores.items"))return false;
        prices().forEach((id,price)->{
            String path="shops.ores.items."+id;
            if(id.equals("netherite_ingot") && !config.contains(path)) {
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
