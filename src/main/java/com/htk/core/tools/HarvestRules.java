package com.htk.core.tools;
import java.util.List;
public final class HarvestRules {
 private HarvestRules() {}
 public record Offset(int x,int z) {}
 public static int cells(int level){return (int)Math.round(1+9*Math.clamp(level,0,25)/25.0);}
 public static double expectedCells(int level){return cells(level);}
 public static List<Offset> offsets(int level,float yaw){return offsets(level,yaw,0);}
 public static List<Offset> offsets(int level,float yaw,double roll){
  return WorkFootprint.offsets(false,cells(level),yaw).stream().map(p->new Offset(p[0],p[1])).toList();
 }
}
