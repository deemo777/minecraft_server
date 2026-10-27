package com.htk.core.ranking;
import org.bukkit.configuration.file.YamlConfiguration;
import java.nio.file.*;
import java.util.*;
import java.io.IOException;

final class RankingStore {
    record Board(String id,RankingLedger.Category category,UUID world,double x,double y,double z) { }
    final RankingLedger ledger=new RankingLedger();
    final Map<String,Board> boards=new LinkedHashMap<>();
    private final Path path;
    private boolean dirty;
    RankingStore(Path path) { this.path=path;load(); }
    private void load() {
        if (!Files.exists(path)) return;
        var y=new YamlConfiguration();
        try { y.load(path.toFile()); }
        catch(Exception e) { throw new IllegalStateException("순위 데이터 읽기 실패. 원본을 보존합니다: "+path,e); }
        if(y.getInt("format",1)!=1 || (y.contains("players") && !y.isConfigurationSection("players"))
                || (y.contains("boards") && !y.isConfigurationSection("boards")))
            throw new IllegalStateException("지원하지 않거나 손상된 순위 데이터 구조: "+path);
        var players=y.getConfigurationSection("players");
        if (players!=null) for (String id:players.getKeys(false)) {
            UUID uuid=UUID.fromString(id);String base="players."+id;
            var entry=new RankingLedger.Entry(uuid,y.getString(base+".name",id.substring(0,8)));
            for (var c:RankingLedger.Category.values()) {
                entry.scores.put(c,Math.max(0,y.getLong(base+"."+c+".score")));
                entry.counts.put(c,Math.max(0,y.getLong(base+"."+c+".count")));
            }
            ledger.players.put(uuid,entry);
        }
        var bs=y.getConfigurationSection("boards");
        if (bs!=null) for(String id:bs.getKeys(false)) {
            String b="boards."+id;
            boards.put(id,new Board(id,RankingLedger.Category.valueOf(y.getString(b+".category")),UUID.fromString(y.getString(b+".world")),y.getDouble(b+".x"),y.getDouble(b+".y"),y.getDouble(b+".z")));
        }
    }
    void dirty() { dirty=true; }
    void save() {
        if (!dirty) return;
        var y=new YamlConfiguration();y.set("format",1);
        for (var e:ledger.players.values()) {
            String b="players."+e.id;y.set(b+".name",e.name);
            for(var c:RankingLedger.Category.values()) { y.set(b+"."+c+".score",e.score(c));y.set(b+"."+c+".count",e.count(c)); }
        }
        for(var b:boards.values()) {
            String k="boards."+b.id;y.set(k+".category",b.category.name());y.set(k+".world",b.world.toString());
            y.set(k+".x",b.x);y.set(k+".y",b.y);y.set(k+".z",b.z);
        }
        Path temp=path.resolveSibling(path.getFileName()+".tmp");
        try {
            Files.createDirectories(path.getParent());y.save(temp.toFile());
            if (Files.exists(path)) Files.copy(path,path.resolveSibling(path.getFileName()+".bak"),StandardCopyOption.REPLACE_EXISTING);
            try { Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE); }
            catch(AtomicMoveNotSupportedException e) { Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING); }
            dirty=false;
        } catch(IOException e) { throw new IllegalStateException("순위 데이터 저장 실패: "+path,e); }
    }
}
