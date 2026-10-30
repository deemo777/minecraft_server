package com.htk.core.tools;
public final class WeaponPerformanceTest {
 public static void main(String[] args) {
  double[][] damage={{27.0/7,4.5,6.5,7.5,8.5,9.5},{8,9,11,12,13,14},{72.0/7,12,14,15,16,17}};
  ToolType[] types={ToolType.DAGGER,ToolType.TACHI,ToolType.GREATSWORD};
  for(int t=0;t<types.length;t++)for(int level=0;level<=25;level++){
   var type=types[t];
   if(level%5==0 && Math.abs(WeaponPerformance.damage(type,level)-damage[t][level/5])>1e-10)throw new AssertionError("Damage anchor");
   if(level>0 && (WeaponPerformance.damage(type,level)<=WeaponPerformance.damage(type,level-1)
    || WeaponPerformance.speed(type,level)<=WeaponPerformance.speed(type,level-1)
    || WeaponPerformance.reach(type,level)<=WeaponPerformance.reach(type,level-1)
    || WeaponPerformance.sweepRatio(type,level)<=WeaponPerformance.sweepRatio(type,level-1)))throw new AssertionError("Every stat must increase at every level: "+type+" "+level);
  }
  double[] ratios={.3,.65,1.4};
  for(int t=0;t<types.length;t++)if(Math.abs(WeaponPerformance.sweepRatio(types[t],25)-ratios[t])>1e-10)throw new AssertionError("Requested maximum sweep ratio");
  if(Math.abs(WeaponPerformance.extendedSweepDamage(18,1.4)-24.8)>1e-10)throw new AssertionError("140% sweep must exceed the native 100% cap");
  if(WeaponPerformance.extendedSweepDamage(8,.65)!=8 || WeaponPerformance.extendedSweepDamage(18,1)!=18)throw new AssertionError("Do not rescale ratios at or below 100%");
  int[] caps={2,3,5};
  for(int t=0;t<types.length;t++)if(WeaponPerformance.maxTargets(types[t])!=caps[t])throw new AssertionError("Total target cap includes primary");
  ToolType[] best={ToolType.DAGGER,ToolType.TACHI,ToolType.TACHI,ToolType.GREATSWORD,ToolType.GREATSWORD};
  for(int count=1;count<=5;count++){
   ToolType winner=null;double highest=-1;
   for(var type:types){double attack=WeaponPerformance.damage(type,25);
    int targets=Math.min(count,WeaponPerformance.maxTargets(type));
    double dps=(attack+(targets-1)*(1+attack*WeaponPerformance.sweepRatio(type,25)))*WeaponPerformance.speed(type,25);
    if(dps>highest){highest=dps;winner=type;}
   }
   if(winner!=best[count-1])throw new AssertionError("Expected weapon role at "+count+" targets");
  }
  for(var type:types) {
   var candidates=java.util.List.of(8,3,1,5,2,1,4);
   var budget=new SweepTargets.Budget<>(candidates,WeaponPerformance.maxTargets(type),java.util.Comparator.naturalOrder());
   int hits=1;
   for(int target=8;target>=1;target--) {
    boolean admitted=budget.claim(target);
    if(admitted)hits++;
    if(admitted!=(target<WeaponPerformance.maxTargets(type)))throw new AssertionError("Nearest candidates are selected, independent of native iteration order");
    if(budget.claim(target))throw new AssertionError("A target must not be hit twice in one sweep");
   }
   if(hits!=WeaponPerformance.maxTargets(type))throw new AssertionError("Direct and secondary hits share one budget");
   var next=new SweepTargets.Budget<>(candidates,WeaponPerformance.maxTargets(type),java.util.Comparator.naturalOrder());
   if(!next.claim(1))throw new AssertionError("Next attack gets an independent fresh budget");
  }
  if(new SweepTargets.Budget<>(java.util.List.of(1),1,java.util.Comparator.naturalOrder()).claim(1))throw new AssertionError("Primary-only budget has no secondary slots");
  if(new SweepTargets.Budget<Integer>(java.util.List.of(),5,java.util.Comparator.naturalOrder()).claim(1))throw new AssertionError("No targets outside the candidate set");
  System.out.println("All 26 weapon levels: damage, speed, reach, sweep strictly increase; anchors preserved");
 }
}
