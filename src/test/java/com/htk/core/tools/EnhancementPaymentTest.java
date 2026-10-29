package com.htk.core.tools;

public final class EnhancementPaymentTest {
    private static int checks;
    private static void check(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
    private static final class Wallet implements EnhancementPayment.Wallet {
        long balance;int withdrawals,refunds;
        Wallet(long balance){this.balance=balance;}
        public boolean withdraw(long amount){withdrawals++;if(balance<amount)return false;balance-=amount;return true;}
        public void refund(long amount){refunds++;balance+=amount;}
    }
    public static void main(String[] args){
        long[] expected={500,500,500,500,500,1000,1500,2000,2500,3000,3000,3750,4500,5250,6000,
            6000,7500,9000,10500,12000,12000,16500,21000,25500,30000};
        long before=0;
        for(int target=1;target<=25;target++){
            long cost=UpgradeRules.goldCost(target);
            check(cost==expected[target-1],"exact progressive fee at +"+target);
            check(cost>=before,"fees never decrease between stages");before=cost;
            check(UpgradeRules.configuredGoldCost(target,java.util.List.of())==cost,"missing config keeps the requested fee");
        }
        check(UpgradeRules.configuredGoldCost(2,java.util.List.of(500L,1250L))==1250,"custom fee is honored");
        check(UpgradeRules.configuredGoldCost(1,java.util.List.of(-500L))==0,"negative custom fee cannot credit the wallet");
        for(int bad:new int[]{-1,0,26}){
            boolean rejected=false;try{UpgradeRules.goldCost(bad);}catch(IllegalArgumentException ex){rejected=true;}
            check(rejected,"invalid target cannot be charged");
        }
        int[] items={5};int[] rollbacks={0};
        Wallet poor=new Wallet(499);
        check(!EnhancementPayment.attempt(poor,500,()->items[0]--,()->rollbacks[0]++),"insufficient balance denies the attempt");
        check(poor.balance==499&&items[0]==5&&rollbacks[0]==0&&poor.refunds==0,"denied attempt touches neither items nor gold");
        Wallet exact=new Wallet(500);
        check(EnhancementPayment.attempt(exact,500,()->items[0]--,()->items[0]++),"exact balance pays for one attempt");
        check(exact.balance==0&&exact.withdrawals==1&&items[0]==4,"successful commit charges exactly once");
        Wallet crashing=new Wallet(3000);int snapshot=items[0];boolean thrown=false;
        try{EnhancementPayment.attempt(crashing,3000,()->{items[0]=0;throw new IllegalStateException("commit failed");},()->items[0]=snapshot);}
        catch(IllegalStateException ex){thrown=true;}
        check(thrown&&crashing.balance==3000&&crashing.refunds==1&&items[0]==snapshot,"commit failure restores items and refunds exactly once");
        Wallet brokenRollback=new Wallet(500);boolean suppressed=false;
        try{EnhancementPayment.attempt(brokenRollback,500,()->{throw new IllegalStateException("commit");},()->{throw new IllegalStateException("rollback");});}
        catch(IllegalStateException ex){suppressed=ex.getSuppressed().length==1;}
        check(suppressed&&brokenRollback.balance==500&&brokenRollback.refunds==1,"gold is refunded even if item rollback throws");
        for(boolean guaranteed:new boolean[]{false,true}){
            Wallet payer=new Wallet(30000);
            var progress=guaranteed?new ArtisanRules.Progress(30,ArtisanRules.FULL):ArtisanRules.Progress.EMPTY;
            var outcome=ArtisanRules.attempt(24,3,progress,.999999);
            int[] crystals={8};
            check(EnhancementPayment.attempt(payer,UpgradeRules.goldCost(25),()->crystals[0]-=UpgradeRules.required(25),()->crystals[0]=8),"valid final-tier attempt is payable");
            check(payer.balance==0&&crystals[0]==0,"failure and guaranteed success both consume full gold and crystals");
            check(outcome.level()==(guaranteed?25:24),"the payment does not change artisan success semantics");
        }
        System.out.println("Enhancement gold-payment regression checks passed: "+checks);
    }
}
