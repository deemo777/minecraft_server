package com.htk.core.tools;

/** Numeric equipment performance with explicit milestone and durability rules. */
public final class ToolPerformance {
 private ToolPerformance() {}
 public static boolean indestructible(int level){return level>=25;}
 public static double miningSpeed(ToolType type,int level) {
  return Progression.value(level,6,8,18,34,46,59);
 }
 public static double conservationStrength(int level){return Progression.value(level,0,0,1,3,4,4);}
 public static double wearChance(ToolType type,int level){
  if(indestructible(level))return 0;
  return 1;
 }
 public static double fortuneStrength(int level){return Progression.value(level,0,0,1,3,3.5,4);}
 /** Expected additional crop stacks, relative to the original vanilla drop. */
 public static double hoeFortuneStrength(int level){return level<5?0:level<15?cropBonusChance(level):1+cropBonusChance(level);}
 public static double cropBonusChance(int level){
  int bounded=Math.clamp(level,0,25);
  return bounded<5?0:bounded<15?(bounded-4)/10.0:.1+.9*(bounded-15)/10.0;
 }
 public static int cropMultiplier(int level,double roll){
  if(level<5)return 1;
  return level<15?(roll<cropBonusChance(level)?2:1):(roll<cropBonusChance(level)?3:2);
 }
 public static double preserveChance(int level){return level>=15?1:0;}
 public static double expectedWoodExtraBlocks(int level){
  int bounded=Math.clamp(level,0,25);
  if(bounded<10)return 0;
  if(bounded<=15)return Math.round(1+2*(bounded-10)/5.0);
  if(bounded<=20)return Math.round(3+(bounded-15)/5.0);
  if(bounded<=23)return Math.round(4+(bounded-20)/3.0);
  return Math.round(5+2*(bounded-23)/2.0);
 }
 public static double expectedExtraBlocks(int level){return Math.clamp(level-20,0,5);}
 public static int extraBlocks(ToolType type,int level){
  return type==ToolType.PICKAXE?(int)expectedExtraBlocks(level):(int)expectedWoodExtraBlocks(level);
 }
 public static int maxDurability(ToolType type,int level){
  int bounded=Math.clamp(level,0,25);
  if(type.weapon())return (int)Math.round(250+(1561-250)*bounded/25.0);
  double base=type==ToolType.FISHING_ROD?64:type==ToolType.WATERING_CAN?25
    :Progression.value(bounded,250,1561,1561,1561,2031,2031);
  return (int)Math.round(base*(1+conservationStrength(bounded)));
 }
 public static double fishingWaitFactor(int level){return 1-.02*Math.clamp(level,0,25);}
 public static double fishingExtraChance(int level){return level>=25?1:.5*Math.clamp(level,0,24)/24.0;}
}
