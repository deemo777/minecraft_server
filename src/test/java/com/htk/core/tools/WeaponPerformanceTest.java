package com.htk.core.tools;
public final class WeaponPerformanceTest {
 public static void main(String[] args) {
  ToolType[] types={ToolType.DAGGER,ToolType.TACHI,ToolType.GREATSWORD};
  double[] initial={4,6,9},maximum={10.7,13,24},startSpeed={3,2,1.3},endSpeed={4.5,2.4,1},ratios={.1,.77,1.8};
  int[] startAnimation={7,10,15},endAnimation={4,8,20};
  for(int t=0;t<types.length;t++) {
   var type=types[t];
   var expectedAnimation=type==ToolType.DAGGER?io.papermc.paper.datacomponent.item.SwingAnimation.Animation.STAB:io.papermc.paper.datacomponent.item.SwingAnimation.Animation.WHACK;
   if(WeaponPerformance.animationType(type)!=expectedAnimation)throw new AssertionError("Dagger stabs, other weapons swing");
   if(WeaponPerformance.animationTicks(type,0)!=startAnimation[t] || WeaponPerformance.animationTicks(type,25)!=endAnimation[t])throw new AssertionError("Animation duration endpoints");
   for(int level=0;level<=25;level++) {
    int ticks=WeaponPerformance.animationTicks(type,level);
    if(ticks<1 || Math.abs(ticks-20.0/WeaponPerformance.speed(type,level))>.500000001)throw new AssertionError("Animation matches attack charge within half a tick");
    if(level>0 && (type==ToolType.GREATSWORD?ticks<WeaponPerformance.animationTicks(type,level-1):ticks>WeaponPerformance.animationTicks(type,level-1)))throw new AssertionError("Animation follows specialized speed growth");
   }
   if(WeaponPerformance.damage(type,0)!=initial[t] || Math.abs(WeaponPerformance.damage(type,25)-maximum[t])>1e-10)throw new AssertionError("Damage endpoints");
   if(WeaponPerformance.speed(type,0)!=startSpeed[t] || Math.abs(WeaponPerformance.speed(type,25)-endSpeed[t])>1e-10)throw new AssertionError("Speed endpoints");
   if(Math.abs(WeaponPerformance.sweepRatio(type,25)-ratios[t])>1e-10)throw new AssertionError("Maximum sweep ratio");
   for(int level=1;level<=25;level++) {
    if(WeaponPerformance.damage(type,level)<=WeaponPerformance.damage(type,level-1))throw new AssertionError("Every level increases damage");
    if(WeaponPerformance.reach(type,level)<=WeaponPerformance.reach(type,level-1))throw new AssertionError("Every level increases reach");
    if(type==ToolType.GREATSWORD ? WeaponPerformance.speed(type,level)>=WeaponPerformance.speed(type,level-1):WeaponPerformance.speed(type,level)<=WeaponPerformance.speed(type,level-1))throw new AssertionError("Specialized speed growth");
    if(type!=ToolType.DAGGER && WeaponPerformance.sweepRatio(type,level)<=WeaponPerformance.sweepRatio(type,level-1))throw new AssertionError("Sweep growth");
   }
  }
  if(WeaponPerformance.growth(5)>=.2 || WeaponPerformance.growth(20)>=.8)throw new AssertionError("Identity develops gradually, later in progression");
  if(Math.abs(WeaponPerformance.extendedSweepDamage(25,1.8)-44.2)>1e-10)throw new AssertionError("180% sweep exceeds native cap");
  if(WeaponPerformance.extendedSweepDamage(8,.77)!=8 || WeaponPerformance.extendedSweepDamage(25,1)!=25)throw new AssertionError("No rescaling below native cap");
  int[] caps={2,3,5};
  for(int t=0;t<types.length;t++)if(WeaponPerformance.maxTargets(types[t])!=caps[t])throw new AssertionError("Total target cap includes primary");
  ToolType[] best={ToolType.DAGGER,ToolType.GREATSWORD,ToolType.GREATSWORD,ToolType.GREATSWORD,ToolType.GREATSWORD};
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
  System.out.println("All 26 weapon levels: attack animation type/timing, specialized growth, target budgets, native-cap compensation passed");
 }
}
