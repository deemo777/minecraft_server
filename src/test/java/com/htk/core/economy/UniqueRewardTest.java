package com.htk.core.economy;

import java.nio.file.*;
import java.util.UUID;
import java.util.logging.Logger;

public final class UniqueRewardTest {
    private static int checks;
    private static void check(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
    public static void main(String[] args) throws Exception {
        Path directory=Files.createTempDirectory("unique-reward-regression-");
        var file=directory.resolve("money.yml").toFile();var log=Logger.getLogger("UniqueRewardTest");
        var wallet=new Economy(file,log);UUID first=UUID.randomUUID(),second=UUID.randomUUID();
        wallet.ensureAccount(first);
        check(wallet.depositOnce(first,30_000,"jump-first"),"first payout succeeds");
        check(wallet.balance(first)==33_000,"first payout adds exactly thirty thousand");
        check(wallet.depositOnce(first,30_000,"jump-first") && wallet.balance(first)==33_000,"retry cannot duplicate payment");
        wallet=new Economy(file,log);
        check(wallet.hasRewardReceipt("jump-first") && wallet.balance(first)==33_000,"receipt and balance survive restart together");
        check(wallet.depositOnce(second,30_000,"jump-first") && wallet.balance(second)==0,"receipt cannot be claimed by another player");
        Path blocked=directory.resolve("blocked");Files.writeString(blocked,"file");
        var broken=new Economy(blocked.resolve("money.yml").toFile(),log);broken.ensureAccount(first);
        check(!broken.depositOnce(first,30_000,"jump-first"),"failed save does not confirm payment");
        check(broken.balance(first)==3000 && !broken.hasRewardReceipt("jump-first"),"failed save rolls back payment and receipt for retry");
        Path corrupt=directory.resolve("corrupt.yml");Files.writeString(corrupt,"balances: [\n");
        boolean rejected=false;
        try{new Economy(corrupt.toFile(),log);}catch(IllegalStateException expected){rejected=true;}
        check(rejected,"unreadable economy data cannot silently reset first-reward receipts");
        System.out.println("Unique reward checks passed: "+checks);
    }
}
