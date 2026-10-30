package com.htk.core.tools;
public final class WeaponPerformanceTest {
 public static void main(String[] args) {
  double[][] damage={{27.0/7,4.5,6.5,7.5,8.5,9.5},{6,7,9,10,11,12},{72.0/7,12,14,15,16,17}};
  ToolType[] types={ToolType.DAGGER,ToolType.TACHI,ToolType.GREATSWORD};
  for(int t=0;t<types.length;t++)for(int level=0;level<=25;level++){
   var type=types[t];
   if(level%5==0 && Math.abs(WeaponPerformance.damage(type,level)-damage[t][level/5])>1e-10)throw new AssertionError("Damage anchor");
   if(level>0 && (WeaponPerformance.damage(type,level)<=WeaponPerformance.damage(type,level-1)
    || WeaponPerformance.speed(type,level)<=WeaponPerformance.speed(type,level-1)
    || WeaponPerformance.reach(type,level)<=WeaponPerformance.reach(type,level-1)
    || WeaponPerformance.sweepRatio(level)<=WeaponPerformance.sweepRatio(level-1)))throw new AssertionError("Every stat must increase at every level: "+type+" "+level);
  }
  System.out.println("All 26 weapon levels: damage, speed, reach, sweep strictly increase; anchors preserved");
 }
}
