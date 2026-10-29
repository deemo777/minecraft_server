package com.htk.core.tools;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.function.Function;

/** Bounded breadth-first traversal; the caller supplies only loaded, allowed neighbors. */
final class ConnectedBlocks {
    private ConnectedBlocks() {}
    static <T> List<T> collect(T root,int limit,Function<T,List<T>> neighbors) {
        if(limit<1)return List.of();
        var queue=new ArrayDeque<T>();var seen=new HashSet<T>();var result=new ArrayList<T>();
        queue.add(root);seen.add(root);
        while(!queue.isEmpty()){
            T node=queue.removeFirst();result.add(node);
            for(T next:neighbors.apply(node))if(seen.size()<limit && seen.add(next))queue.add(next);
        }
        return result;
    }
}
