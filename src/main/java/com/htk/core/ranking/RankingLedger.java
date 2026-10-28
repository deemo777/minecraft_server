package com.htk.core.ranking;
import java.util.*;

/** 스킬 경험치와 독립된 전체 누적 점수. 동점은 UUID로 안정 정렬한다. */
final class RankingLedger {
    enum Category { FARMING, MINING, WOODCUTTING, FISHING;
        static Category parse(String v) {
            if (v.equalsIgnoreCase("farming")) return FARMING;
            if (v.equalsIgnoreCase("mining")) return MINING;
            if (v.equalsIgnoreCase("woodcutting")) return WOODCUTTING;
            if (v.equalsIgnoreCase("fishing")) return FISHING;
            return null;
        }
        String title() { return switch(this) { case FARMING -> "농사"; case MINING -> "채광"; case WOODCUTTING -> "벌목"; case FISHING -> "낚시"; }; }
    }
    static final class Entry {
        final UUID id; String name;
        final EnumMap<Category,Long> scores=new EnumMap<>(Category.class);
        final EnumMap<Category,Long> counts=new EnumMap<>(Category.class);
        Entry(UUID id,String name) { this.id=id;this.name=name; }
        long score(Category c) { return scores.getOrDefault(c,0L); }
        long count(Category c) { return counts.getOrDefault(c,0L); }
    }
    final Map<UUID,Entry> players=new HashMap<>();
    boolean add(UUID id,String name,Category c,long points) {
        if (points<=0) return false;
        Entry e=players.computeIfAbsent(id,k->new Entry(k,name));e.name=name;
        e.scores.put(c,addSafe(e.score(c),points));e.counts.put(c,addSafe(e.count(c),1));return true;
    }
    static long addSafe(long a,long b) { return a>Long.MAX_VALUE-b?Long.MAX_VALUE:a+b; }
    List<Entry> top(Category c,int size) {
        return players.values().stream().filter(e->e.score(c)>0)
                .sorted(Comparator.<Entry>comparingLong(e->e.score(c)).reversed().thenComparing(e->e.id.toString())).limit(size).toList();
    }
}
