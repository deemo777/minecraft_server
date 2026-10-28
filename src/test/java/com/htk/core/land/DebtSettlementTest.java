package com.htk.core.land;

public final class DebtSettlementTest {
    public static void main(String[] args) {
        long[] balance={99};int[] calls={0};
        java.util.function.LongPredicate withdraw=amount->{calls[0]++;if(balance[0]<amount)return false;balance[0]-=amount;return true;};
        check(DebtSettlement.settle(100,balance[0],withdraw)==DebtSettlement.Result.INSUFFICIENT&&balance[0]==99&&calls[0]==0,"insufficient balance is untouched");
        balance[0]=100;
        check(DebtSettlement.settle(100,balance[0],withdraw)==DebtSettlement.Result.PAID&&balance[0]==0,"exact balance pays full debt");
        balance[0]=1000;
        check(DebtSettlement.settle(100,balance[0],withdraw)==DebtSettlement.Result.PAID&&balance[0]==900,"surplus balance preserved");
        int prior=calls[0];
        check(DebtSettlement.settle(0,balance[0],withdraw)==DebtSettlement.Result.NO_DEBT&&calls[0]==prior,"no repeated charge after settlement");
        balance[0]=50;
        check(DebtSettlement.settle(100,100,withdraw)==DebtSettlement.Result.INSUFFICIENT&&balance[0]==50,"stale displayed balance cannot partially pay");
        System.out.println("Full debt settlement checks passed: 5");
    }
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
