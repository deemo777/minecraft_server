package com.htk.core.merchant;

/** 한 개 구매: 공간 확인 → 출금 → 지급, 지급 실패 시 환불. */
final class PurchaseTransaction {
    enum Result { SUCCESS, FULL, INSUFFICIENT, DISABLED, DELIVERY_FAILED }
    interface Buyer {
        boolean hasSpace();
        boolean withdraw(long amount);
        void refund(long amount);
        boolean deliver();
    }
    static Result buy(Buyer buyer,long price) {
        if(price<=0)return Result.DISABLED;
        if(!buyer.hasSpace())return Result.FULL;
        if(!buyer.withdraw(price))return Result.INSUFFICIENT;
        boolean delivered;
        try { delivered=buyer.deliver(); }
        catch(RuntimeException e) { buyer.refund(price);throw e; }
        if(!delivered){buyer.refund(price);return Result.DELIVERY_FAILED;}
        return Result.SUCCESS;
    }
}
