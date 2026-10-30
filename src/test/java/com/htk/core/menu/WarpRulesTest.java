package com.htk.core.menu;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.util.*;

public final class WarpRulesTest {
    static int checks;
    static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        Location origin=new Location(null,-123.25,72.5,456.75,37,-12);
        double[][] expected={{-123.25,456.75},{-123.25,-543.25},{876.75,456.75},{-1123.25,456.75},{-123.25,1456.75}};
        int index=0;Set<Integer> slots=new HashSet<>();
        for(var destination:WarpDestination.values()){
            check(destination.slots().size()==6,"six clickable slots per card");
            for(int slot:destination.slots()){
                check(slot>=9 && slot<45 && slots.add(slot),"distinct card hitboxes");
                check(WarpDestination.bySlot(slot)==destination,"card routes every occupied slot");
            }
            if(!destination.enabled())continue;
            Location target=destination.offset(origin);
            check(target.getX()==expected[index][0] && target.getZ()==expected[index++][1],"exact compass destination");
            check(target.getY()==72.5 && target.getYaw()==37 && target.getPitch()==-12,"origin orientation preserved before ground adjustment");
            check(origin.getX()==-123.25 && origin.getZ()==456.75,"origin not mutated");
        }
        check(index==5 && !WarpDestination.RESERVED.enabled(),"five destinations and one inert card");
        check(WarpDestination.bySlot(0)==null && WarpDestination.bySlot(8)==null,"navigation separate from destinations");
        var defaults=YamlConfiguration.loadConfiguration(new File("src/main/resources/menu.yml"));
        var fresh=MenuConfig.load(defaults);
        check(fresh.warpPoint().equals("wild"),"new install uses existing wild point");
        check(fresh.bySlot().get(12).openMenu().equals("warp"),"warp opens selector");
        check(fresh.bySlot().get(27).id().equals("dungeon") && fresh.bySlot().get(27).teleport().isBlank(),"dungeon has no destination");
        var old=new YamlConfiguration();
        old.set("buttons.wild.slots",List.of(27,28,29,36,37,38));
        old.set("buttons.wild.teleport","existing-wilderness");
        old.set("buttons.warp.slots",List.of(12,13,14,21,22,23));
        old.set("buttons.warp.coming-soon",true);
        old.set("buttons.custom.command","customcommand");
        MenuConfig.migrateLayout(old,defaults);
        check(old.getString("warp-point").equals("existing-wilderness"),"legacy custom origin preserved");
        check(!old.contains("buttons.wild") && old.contains("buttons.dungeon"),"old wild card becomes dungeon");
        check(old.getString("buttons.warp.open-menu").equals("warp") && !old.getBoolean("buttons.warp.coming-soon"),"old placeholder opens selector");
        String migrated=old.saveToString();MenuConfig.migrateLayout(old,defaults);
        check(migrated.equals(old.saveToString()),"migration idempotent");
        check(old.getString("buttons.custom.command").equals("customcommand"),"unrelated custom actions preserved");
        var custom=new YamlConfiguration();
        custom.set("warp-point","configured-origin");
        custom.set("buttons.wild.slots",List.of(1));custom.set("buttons.wild.teleport","custom");
        custom.set("buttons.warp.command","customwarp");custom.set("buttons.warp.coming-soon",true);
        MenuConfig.migrateLayout(custom,defaults);
        check(custom.getString("warp-point").equals("configured-origin") && custom.contains("buttons.wild"),"custom card and explicit origin preserved");
        check(!custom.contains("buttons.warp.open-menu"),"custom warp command preserved");
        for(Material material:List.of(Material.WATER,Material.LAVA,Material.FIRE,Material.MAGMA_BLOCK,Material.CACTUS,Material.POWDER_SNOW,Material.CAMPFIRE,Material.COBWEB))
            check(MenuFeature.hazardous(material),"hazard rejected: "+material);
        check(!MenuFeature.hazardous(Material.STONE) && !MenuFeature.hazardous(Material.GRASS_BLOCK),"ordinary terrain accepted");
        System.out.println("Warp routing, coordinates, migration and hazard checks passed: "+checks);
    }
}
