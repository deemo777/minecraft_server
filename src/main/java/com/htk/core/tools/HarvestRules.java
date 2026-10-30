package com.htk.core.tools;
import java.util.List;
public final class HarvestRules {
 private HarvestRules() {}
 public record Offset(int x,int z) {}
 public static double expectedCells(int level){return Progression.value(level,1,3,5,8,16,25);}
 public static List<Offset> offsets(int level,float yaw){return offsets(level,yaw,java.util.concurrent.ThreadLocalRandom.current().nextDouble());}
 public static List<Offset> offsets(int level,float yaw,double roll){
  return WorkFootprint.offsets(false,Progression.count(expectedCells(level),roll),yaw).stream().map(p->new Offset(p[0],p[1])).toList();
 }
}
