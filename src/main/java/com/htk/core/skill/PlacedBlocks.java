package com.htk.core.skill;

import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;

/**
 * 플레이어가 직접 놓은 블록 위치 기억 (청크 데이터 servercore:placed).
 * 놓은 원목·광석을 다시 캐서 경험치를 버는 걸 막는 데 써요.
 */
public final class PlacedBlocks {

    private final NamespacedKey key;

    public PlacedBlocks(NamespacedKey key) {
        this.key = key;
    }

    private static int pack(Block b) {
        return (b.getX() & 15) | ((b.getZ() & 15) << 4) | ((b.getY() + 4096) << 8);
    }

    private int[] all(Chunk c) {
        int[] a = c.getPersistentDataContainer().get(key, PersistentDataType.INTEGER_ARRAY);
        return a == null ? new int[0] : a;
    }

    public boolean isPlaced(Block b) {
        int k = pack(b);
        for (int v : all(b.getChunk())) if (v == k) return true;
        return false;
    }

    public void mark(Block b) {
        Chunk c = b.getChunk();
        int k = pack(b);
        int[] a = all(c);
        for (int v : a) if (v == k) return;
        int[] n = Arrays.copyOf(a, a.length + 1);
        n[a.length] = k;
        c.getPersistentDataContainer().set(key, PersistentDataType.INTEGER_ARRAY, n);
    }

    public void unmark(Block b) {
        Chunk c = b.getChunk();
        int k = pack(b);
        int[] a = all(c);
        int[] n = Arrays.stream(a).filter(v -> v != k).toArray();
        if (n.length == a.length) return;
        PersistentDataContainer pdc = c.getPersistentDataContainer();
        if (n.length == 0) pdc.remove(key);
        else pdc.set(key, PersistentDataType.INTEGER_ARRAY, n);
    }
}
