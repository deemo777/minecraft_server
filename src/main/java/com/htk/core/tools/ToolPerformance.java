package com.htk.core.tools;

/** Every enhancement level improves numeric performance, including fractional effects. */
public final class ToolPerformance {
 private ToolPerformance() {}
 public static boolean indestructible(int level){return level>=25;}
 public static double miningSpeed(ToolType type,int level) {
  return type==ToolType.AXE?Progression.value(level,6,12,25,34,46,59):Progression.value(level,6,12,18,34,46,59);
 }
 public static double wearChance(int level){return Progression.value(level,1,.75,.5,.25,.2,0);}
 public static double fortuneStrength(int level){return Progression.value(level,0,.5,1.5,3,4,5);}
 public static double hoeFortuneStrength(int level){return Progression.value(level,0,.25,.5,1,2,3);}
 public static int rolledFortune(int level,double roll){return Progression.count(fortuneStrength(level),roll);}
 public static int rolledHoeFortune(int level,double roll){return Progression.count(hoeFortuneStrength(level),roll);}
 public static double preserveChance(int level){return Math.clamp(level,0,25)/25.0;}
 public static double expectedExtraBlocks(int level){
  if(level<=20)return Progression.value(level,0,1,2,3,5,4095);
  return Math.pow(5,(25-Math.clamp(level,20,25))/5.0)*Math.pow(4095,(Math.clamp(level,20,25)-20)/5.0);
 }
 public static int extraBlocks(int level){return Progression.count(expectedExtraBlocks(level),java.util.concurrent.ThreadLocalRandom.current().nextDouble());}
 public static int maxDurability(ToolType type,int level){
  int start=type==ToolType.FISHING_ROD?64:type==ToolType.WATERING_CAN?25:250;
  int end=type==ToolType.FISHING_ROD?128:type==ToolType.WATERING_CAN?100:type.weapon()?1561:2031;
  return (int)Math.round(start+(end-start)*Math.clamp(level,0,25)/25.0);
 }
 public static double fishingWaitFactor(int level){return 1-.02*Math.clamp(level,0,25);}
 public static double fishingExtraChance(int level){return .01*Math.clamp(level,0,25);}
}
