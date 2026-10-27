package com.htk.core.ranking;
import java.nio.file.*;
import java.util.*;

public final class RankingRulesTest {
    private static int checks;
    private static void check(boolean b,String message) { checks++;if(!b)throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        var a=UUID.fromString("00000000-0000-0000-0000-000000000001");
        var b=UUID.fromString("00000000-0000-0000-0000-000000000002");
        var farm=RankingLedger.Category.FARMING;var mine=RankingLedger.Category.MINING;
        var ledger=new RankingLedger();
        check(!ledger.add(a,"A",farm,0),"zero weight ignored");check(!ledger.add(a,"A",farm,-5),"negative weight ignored");
        ledger.add(a,"old",farm,1);ledger.add(a,"renamed",farm,2);ledger.add(b,"B",farm,3);ledger.add(b,"B",mine,50);
        check(ledger.players.get(a).score(farm)==3 && ledger.players.get(a).count(farm)==2,"weighted sum independent of harvested count");
        check(ledger.players.size()==2 && ledger.players.get(a).name.equals("renamed"),"UUID survives rename");
        check(ledger.top(farm,10).getFirst().id.equals(a),"ties sorted deterministically");
        check(ledger.top(mine,10).size()==1 && ledger.top(mine,10).getFirst().id.equals(b),"categories separate");
        check(ledger.top(farm,1).size()==1,"top limit");
        check(RankingLedger.addSafe(Long.MAX_VALUE-2,20)==Long.MAX_VALUE,"score cannot wrap negative");
        check(RankingLedger.Category.parse("농사")==farm && RankingLedger.Category.parse("MINING")==mine && RankingLedger.Category.parse("oops")==null,"command categories");
        Path dir=Files.createTempDirectory("servercore-ranking-");Path file=dir.resolve("ranking-data.yml");
        var store=new RankingStore(file);store.ledger.add(a,"offline",mine,20);store.ledger.add(a,"offline",mine,50);
        UUID world=UUID.randomUUID();store.boards.put("ores",new RankingStore.Board("ores",mine,world,-16.5,75.25,32.0));store.dirty();store.save();
        var loaded=new RankingStore(file);
        check(loaded.ledger.players.get(a).score(mine)==70 && loaded.ledger.players.get(a).count(mine)==2,"score and count survive restart");
        check(loaded.boards.get("ores").world().equals(world) && loaded.boards.get("ores").x()==-16.5,"world UUID and negative board position survive restart");
        loaded.ledger.add(a,"offline",mine,1);loaded.dirty();loaded.save();check(Files.exists(dir.resolve("ranking-data.yml.bak")),"previous save backed up");
        Files.writeString(file,"players: [broken");String bad=Files.readString(file);boolean blocked=false;
        try { new RankingStore(file); }catch(IllegalStateException expected){blocked=true;}
        check(blocked && Files.readString(file).equals(bad),"malformed data cannot be silently replaced");
        Files.writeString(file,"format: 1\nplayers: [wrong-structure]\n");blocked=false;
        try { new RankingStore(file); }catch(IllegalStateException expected){blocked=true;}
        check(blocked,"wrong YAML schema also fails closed");
        Files.delete(file);Files.delete(dir.resolve("ranking-data.yml.bak"));Files.delete(dir);
        System.out.println("Ranking regression checks passed: "+checks);
    }
}
