package com.htk.core.tools;
import java.util.*;
/** Nested, unique target ordering makes fractional area gains local to the clicked block. */
final class WorkFootprint {
 private WorkFootprint() {}
 static List<int[]> offsets(boolean watering,int count,float yaw) {
  List<int[]> cells=new ArrayList<>();
  add(cells,0,0);add(cells,-1,0);add(cells,1,0);
  if(watering){for(int ahead=-1;ahead<=1;ahead++)for(int side=-1;side<=1;side++)add(cells,side,ahead);
    for(int ahead=-1;ahead<=2;ahead++)for(int side=-1;side<=2;side++)add(cells,side,ahead);
  }else{add(cells,-2,0);add(cells,2,0);for(int side=-1;side<=1;side++)add(cells,side,1);}
  for(int radius=1;radius<=2;radius++)for(int ahead=-radius;ahead<=radius;ahead++)for(int side=-radius;side<=radius;side++)add(cells,side,ahead);
  int direction=Math.floorMod((int)Math.floor((yaw+45.0)/90),4);
  int fx=direction==1?-1:direction==3?1:0,fz=direction==0?1:direction==2?-1:0;
  List<int[]> result=new ArrayList<>();
  for(int i=0;i<Math.clamp(count,1,25);i++){var p=cells.get(i);result.add(new int[]{-fz*p[0]+fx*p[1],fx*p[0]+fz*p[1]});}
  return result;
 }
 private static void add(List<int[]> cells,int x,int z){if(cells.stream().noneMatch(p->p[0]==x&&p[1]==z))cells.add(new int[]{x,z});}
}
