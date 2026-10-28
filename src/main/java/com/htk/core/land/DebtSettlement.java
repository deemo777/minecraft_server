package com.htk.core.land;

import java.util.function.LongPredicate;

/** 일괄 상환: 잔액을 전부 인출하는 방식이 아니라 현재 빚의 정확한 금액만 결제한다. */
public final class DebtSettlement {
    private DebtSettlement() {}
    public enum Result { NO_DEBT, INSUFFICIENT, PAID }
    public static Result settle(long debt,long balance,LongPredicate withdraw) {
        if(debt<=0)return Result.NO_DEBT;
        if(balance<debt||!withdraw.test(debt))return Result.INSUFFICIENT;
        return Result.PAID;
    }
}
