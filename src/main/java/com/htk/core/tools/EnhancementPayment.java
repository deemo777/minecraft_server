package com.htk.core.tools;

/** Charge one valid attempt, restoring both inventory state and gold if committing it fails. */
final class EnhancementPayment {
    interface Wallet {
        boolean withdraw(long amount);
        void refund(long amount);
    }
    private EnhancementPayment() {}
    static boolean attempt(Wallet wallet,long cost,Runnable commit,Runnable rollback) {
        if(cost<0)throw new IllegalArgumentException("Negative enhancement fee");
        if(!wallet.withdraw(cost))return false;
        try {commit.run();return true;}
        catch(RuntimeException failure){
            try {rollback.run();}catch(RuntimeException ex){failure.addSuppressed(ex);}
            try {wallet.refund(cost);}catch(RuntimeException ex){failure.addSuppressed(ex);}
            throw failure;
        }
    }
}
