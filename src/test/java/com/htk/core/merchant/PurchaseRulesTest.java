package com.htk.core.merchant;
public final class PurchaseRulesTest {
    static int checks;
    static void check(boolean b,String m){checks++;if(!b)throw new AssertionError(m);}
    static class Buyer implements PurchaseTransaction.Buyer {
        long money=1000;int given,withdrawals;boolean space=true,delivery=true,crash;
        public boolean hasSpace(){return space;}
        public boolean withdraw(long amount){withdrawals++;if(money<amount)return false;money-=amount;return true;}
        public void refund(long amount){money+=amount;}
        public boolean deliver(){if(crash)throw new IllegalStateException("delivery");if(delivery)given++;return delivery;}
    }
    public static void main(String[] args){
        Buyer b=new Buyer();check(PurchaseTransaction.buy(b,1000)==PurchaseTransaction.Result.SUCCESS && b.money==0 && b.given==1,"exact balance buys one");
        b=new Buyer();b.space=false;check(PurchaseTransaction.buy(b,500)==PurchaseTransaction.Result.FULL && b.money==1000 && b.given==0 && b.withdrawals==0,"full inventory never charges");
        b=new Buyer();check(PurchaseTransaction.buy(b,1001)==PurchaseTransaction.Result.INSUFFICIENT && b.money==1000 && b.given==0,"insufficient balance no delivery");
        b=new Buyer();b.delivery=false;check(PurchaseTransaction.buy(b,500)==PurchaseTransaction.Result.DELIVERY_FAILED && b.money==1000 && b.given==0,"failed delivery refunded");
        b=new Buyer();check(PurchaseTransaction.buy(b,0)==PurchaseTransaction.Result.DISABLED && b.withdrawals==0,"disabled item cannot buy free");
        check(PurchaseTransaction.buy(b,-1)==PurchaseTransaction.Result.DISABLED && b.money==1000,"negative price cannot mint money");
        b=new Buyer();b.crash=true;boolean caught=false;try{PurchaseTransaction.buy(b,500);}catch(IllegalStateException expected){caught=true;}
        check(caught && b.money==1000,"delivery exception refunded");
        b=new Buyer();PurchaseTransaction.buy(b,500);PurchaseTransaction.buy(b,500);
        check(PurchaseTransaction.buy(b,500)==PurchaseTransaction.Result.INSUFFICIENT && b.money==0 && b.given==2,"repeated purchases cannot overspend");
        System.out.println("Purchase regression checks passed: "+checks);
    }
}
