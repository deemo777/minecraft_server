package com.htk.core.dogam;

import java.nio.file.Files;
import org.bukkit.configuration.file.YamlConfiguration;

public final class CollectionProgressTest {
    private static int checks;
    private static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    public static void main(String[] args) throws Exception {
        for(int required:new int[]{1,2,3,10,64,100}) {
            for(int available:new int[]{0,1,2,9,10,64,200}) {
                int deposited=0,consumed=0;
                for(int step=0;step<=required;step++) {
                    var result=CollectionProgress.deposit(required,deposited,available);
                    check(result.taken()==Math.min(available,required-deposited),"Consume only remaining amount");
                    check(result.total()<=required && result.total()>=deposited,"Bounded progress");
                    check(result.complete()==(result.total()==required),"Completion threshold");
                    consumed+=result.taken();deposited=result.total();
                    check(consumed==deposited,"No free registration or excess consumption");
                    check(CollectionProgress.iconAmount(required,deposited,result.complete())==(result.complete()?1:required-deposited),"Completed count disappears, remaining count updates");
                }
            }
        }
        var data=new PlayerDataStore.PlayerData();
        data.setDeposited("stone",3);data.add("diamond");
        var folder=Files.createTempDirectory("collection-regression-");
        try {
            var file=folder.resolve("player.yml").toFile();
            PlayerDataStore.write(file,data);
            var restored=PlayerDataStore.decode(YamlConfiguration.loadConfiguration(file));
            check(restored.deposited("stone")==3 && !restored.has("stone"),"Partial progress survives restart");
            check(restored.has("diamond"),"Completed progress survives restart");
            restored.setDeposited("stone",10);restored.add("stone");
            check(restored.deposited("stone")==0 && restored.has("stone"),"Completion removes partial counter");
            var legacy=new YamlConfiguration();legacy.set("found",java.util.List.of("old:stone"));
            check(PlayerDataStore.decode(legacy).has("stone"),"Legacy completed records remain complete");
            restored.clear();check(restored.found().isEmpty() && restored.deposited("stone")==0,"Reset clears partial and complete data");
            boolean failed=false;
            var blocker=folder.resolve("blocker");Files.writeString(blocker,"blocked");
            try{PlayerDataStore.write(blocker.resolve("player.yml").toFile(),data);}catch(java.io.IOException expected){failed=true;}
            check(failed,"Storage failures are reported to the caller before rewards");
        } finally {
            try(var paths=Files.walk(folder)){for(var path:paths.sorted(java.util.Comparator.reverseOrder()).toList())Files.delete(path);}
        }
        System.out.println("Collection partial deposit/persistence/icon checks passed: "+checks);
    }
}
