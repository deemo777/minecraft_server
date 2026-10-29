package com.htk.core.achievement;

import java.nio.file.*;
import java.util.UUID;

public final class AchievementRulesTest {
    private static int checks;
    private static void check(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
    public static void main(String[] args) throws Exception {
        Path directory=Files.createTempDirectory("achievement-regression-");
        var file=directory.resolve("achievements.yml").toFile();
        var store=new AchievementStore(file);UUID first=UUID.randomUUID(),second=UUID.randomUUID();
        check(store.first()==null,"initial achievement remains hidden");
        check(store.complete(first,"FirstPlayer"),"first player wins");
        check(!store.complete(first,"FirstPlayer"),"repeated completion cannot win again");
        check(!store.complete(second,"SecondPlayer"),"later player cannot win first prize");
        check(store.owns(first,AchievementStore.FIRST) && store.owns(first,AchievementStore.NORMAL),"winner owns both titles");
        check(!store.owns(second,AchievementStore.FIRST) && store.owns(second,AchievementStore.NORMAL),"later player gets only normal title");
        store=new AchievementStore(file);
        check(first.equals(store.first()) && store.firstName().equals("FirstPlayer"),"server restart preserves first achiever");
        check(store.selected(first).equals(AchievementStore.FIRST),"winner title persists");
        store.select(first,null);store=new AchievementStore(file);
        check(store.selected(first)==null,"title removal persists");
        boolean denied=false;try{store.select(second,AchievementStore.FIRST);}catch(IllegalArgumentException expected){denied=true;}
        check(denied,"another player cannot equip the exclusive title");
        // Failed writes must not publish a winner or consume the one-time completion.
        Path blocked=directory.resolve("blocked");Files.writeString(blocked,"file");
        var broken=new AchievementStore(blocked.resolve("achievements.yml").toFile());
        try{broken.complete(first,"FirstPlayer");throw new AssertionError("write should fail");}catch(java.io.IOException expected){}
        check(broken.first()==null && !broken.completed(first),"failed first record is rolled back");
        System.out.println("Hidden achievement persistence checks passed: "+checks);
    }
}
