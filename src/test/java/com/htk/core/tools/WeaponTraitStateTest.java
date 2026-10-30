package com.htk.core.tools;

import java.util.UUID;

public final class WeaponTraitStateTest {
    private static int checks;
    private static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    public static void main(String[] args) {
        UUID a=UUID.randomUUID(),b=UUID.randomUUID();var state=new WeaponTraitState();
        check(!state.dagger(a,"one",0),"First hit");
        check(!state.dagger(a,"one",5),"Second hit");
        check(state.dagger(a,"one",10),"Third hit triggers pursuit");
        check(state.pursuit(49)&&!state.pursuit(50),"Pursuit is exactly 40 ticks");
        check(!state.dagger(a,"one",109),"Cooldown does not recharge early");
        check(!state.dagger(a,"one",110)&&!state.dagger(a,"one",111)&&state.dagger(a,"one",112),"Cooldown boundary starts a fresh combo");
        state=new WeaponTraitState();
        check(!state.dagger(a,"one",0)&&!state.dagger(a,"one",1)&&!state.dagger(b,"one",2),"Changing target resets combo");
        check(!state.dagger(b,"two",3),"Changing instance resets combo");
        check(!state.dagger(b,"two",44),"More than two seconds resets combo");
        state.unequip();check(!state.dagger(b,"two",45),"Unequip resets combo");
        check(!state.dagger(b,"two",46)&&state.dagger(b,"two",47),"Combo after unequip");
        state.unequip();check(!state.pursuit(48)&&!state.dagger(b,"two",49),"Swapping removes active movement without resetting cooldown");
        check(state.tachi(100),"Guard starts");check(state.guard(139)&&!state.guard(140),"Guard expires at 40 ticks");
        check(!state.tachi(139)&&state.guard(178)&&!state.guard(179),"Further hits refresh, not stack, guard");
        check(Math.abs(WeaponTraitState.guardedDamage(20)-18)<1e-10,"Ten percent damage reduction");
        double total=0;for(double component:new double[]{180,-40,-20,-50})total+=WeaponTraitState.guardedDamage(component);
        check(Math.abs(total-63)<1e-10,"Scaling raw, armor, magic and absorption together reduces final damage by exactly ten percent");
        state.unequip();check(!state.guard(140),"Guard ends on unequip");
        check(state.pressure(0)&&!state.pressure(59)&&state.pressure(60),"Pressure cooldown is exactly 60 ticks");
        var other=new WeaponTraitState();check(other.pressure(1),"Players have independent cooldowns");
        System.out.println("Weapon capstone state regression checks passed: "+checks);
    }
}
