package com.htk.core.tools;
public final class WeaponPerformanceTest {
 public static void main(String[] args) {
  int[] levels={0,4,5,9,10,14,15,19,20,24,25};
  int[] sharp={0,0,0,0,3,3,5,5,7,7,9},sweep={0,0,0,0,1,1,3,3,3,3,4};
  for(int i=0;i<levels.length;i++){
   if(WeaponPerformance.sharpness(levels[i])!=sharp[i] || WeaponPerformance.sweeping(levels[i])!=sweep[i])throw new AssertionError("Milestone "+levels[i]);
   for(ToolType t:ToolType.values())if(t.weapon()){
    double d=WeaponPerformance.damage(t,levels[i]);if(d<=0 || d>17)throw new AssertionError("Damage");
    if((levels[i]<5)!=(t.materialAt(levels[i])==org.bukkit.Material.IRON_SWORD))throw new AssertionError("Material");
   }
  }
  if(WeaponPerformance.damage(ToolType.TACHI,0)!=6 || WeaponPerformance.damage(ToolType.TACHI,5)!=7)throw new AssertionError("Iron/diamond");
  System.out.println("Weapon milestone and material boundary checks passed");
 }
}
