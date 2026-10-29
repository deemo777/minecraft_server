package com.htk.core.tools;

import java.util.ArrayList;
import java.util.List;

/** Mature-crop sweep geometry. Offsets are relative to the clicked crop below +25. */
public final class HarvestRules {
    private HarvestRules() {}
    public record Offset(int x,int z) {}
    public static List<Offset> offsets(int level,float yaw) {
        List<Offset> result=new ArrayList<>();
        if(level>=25){
            for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)result.add(new Offset(x,z));
        }else{
            int direction=Math.floorMod((int)Math.floor((yaw+45.0)/90.0),4);
            int fx=direction==1?-1:direction==3?1:0, fz=direction==0?1:direction==2?-1:0;
            int width=level>=10?2:level>=5?1:0;
            for(int side=-width;side<=width;side++)result.add(new Offset(-fz*side,fx*side));
            if(level>=15)for(int side=-1;side<=1;side++)result.add(new Offset(-fz*side+fx,fx*side+fz));
        }
        return List.copyOf(result);
    }
}
