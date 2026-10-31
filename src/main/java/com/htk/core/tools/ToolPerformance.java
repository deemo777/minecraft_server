package com.htk.core.tools;

/** Numeric equipment performance with explicit milestone and durability rules. */
public final class ToolPerformance {
 private ToolPerformance() {}
 public static boolean indestructible(int level){return level>=25;}
 public static double miningSpeed(ToolType type,int level) {
  return type==ToolType.AXE?Progression.value(level,6,8,25,34,46,59):Progression.value(level,6,8,18,34,46,59);
 }
 public static double conservationStrength(int level){return Progression.value(level,0,0,1,3,4,4);}
 public static double wearChance(ToolType type,int level){
  if(indestructible(level))return 0;
  return type.weapon()?1:1/(1+conservationStrength(level));
 }
 public static double fortuneStrength(int level){return level<10?0:Progression.value(level,0,0,1,3,4,4);}
 public static double hoeFortuneStrength(int level){return Progression.value(level,0,.25,.5,1,2,3);}
 public static int rolledFortune(int level,double roll){return Progression.count(fortuneStrength(level),roll);}
 public static int rolledHoeFortune(int level,double roll){return Progression.count(hoeFortuneStrength(level),roll);}
 public static double preserveChance(int level){return Math.clamp(level,0,25)/25.0;}
 public static double expectedWoodExtraBlocks(int level){
  if(level<=20)return Progression.value(level,0,1,2,3,5,4095);
  return Math.pow(5,(25-Math.clamp(level,20,25))/5.0)*Math.pow(4095,(Math.clamp(level,20,25)-20)/5.0);
 }
 public static double expectedExtraBlocks(int level){return Math.clamp(level-20,0,5);}
 public static int extraBlocks(ToolType type,int level){
  return type==ToolType.PICKAXE?(int)expectedExtraBlocks(level):Progression.count(expectedWoodExtraBlocks(level),java.util.concurrent.ThreadLocalRandom.current().nextDouble());
 }
 public static int maxDurability(ToolType type,int level){
  int bounded=Math.clamp(level,0,25);
  if(type.weapon())return (int)Math.round(250+(1561-250)*bounded/25.0);
  if(type==ToolType.FISHING_ROD)return 64;
  if(type==ToolType.WATERING_CAN)return 25;
  if(bounded>=20)return 2031;
  if(bounded>=5)return 1561;
  return (int)Math.round(250+(1561-250)*bounded/5.0);
 }
 public static double fishingWaitFactor(int level){return 1-.02*Math.clamp(level,0,25);}
 public static double fishingExtraChance(int level){return .01*Math.clamp(level,0,25);}
}
