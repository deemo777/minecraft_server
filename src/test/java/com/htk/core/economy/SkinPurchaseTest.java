package com.htk.core.economy;

import com.htk.core.skins.WeaponSkinCatalog;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;

public final class SkinPurchaseTest {
    private static int checks;
    private static void check(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        var root=Files.createTempDirectory("skin-purchase-regression-");
        var file=root.resolve("money.yml").toFile();var logger=Logger.getLogger("SkinPurchaseTest");
        var wallet=new Economy(file,logger);UUID player=UUID.randomUUID(),other=UUID.randomUUID();
        wallet.ensureAccount(player);
        String first=WeaponSkinCatalog.pool("dagger").getFirst().key();
        check(WeaponSkinCatalog.PRICE==100_000L,"draw price is 100,000G");
        check(wallet.purchaseUnlock(player,100_000,first)==Economy.UnlockPurchase.INSUFFICIENT,"insufficient funds reject draw");
        check(wallet.balance(player)==3000 && wallet.unlocks(player).isEmpty(),"rejected draw changes nothing");
        wallet.set(player,1_300_000);
        var unique=new HashSet<String>();
        for(int draw=0;draw<13;draw++){
            var pool=WeaponSkinCatalog.remaining("dagger",wallet.unlocks(player));
            check(pool.size()==13-draw,"remaining pool shrinks by one");
            String chosen=pool.get(draw%pool.size()).key();
            check(unique.add(chosen),"no duplicate skin drawn");
            check(wallet.purchaseUnlock(player,100_000,chosen)==Economy.UnlockPurchase.SUCCESS,"purchase persists");
            wallet=new Economy(file,logger);
            check(wallet.unlocks(player).contains(chosen),"skin survives restart before result GUI");
            check(wallet.balance(player)==1_300_000-(draw+1)*100_000,"cost survives restart with ownership");
        }
        check(WeaponSkinCatalog.remaining("dagger",wallet.unlocks(player)).isEmpty(),"completed category has no paid draw");
        check(wallet.balance(player)==0 && wallet.unlocks(player).size()==13,"full collection costs exactly thirteen draws");
        check(wallet.purchaseUnlock(player,100_000,first)==Economy.UnlockPurchase.OWNED,"duplicate retry does not debit");
        check(wallet.unlocks(other).isEmpty(),"collection is account-specific");
        check(WeaponSkinCatalog.remaining("tachi",wallet.unlocks(player)).size()==13,"categories do not cross unlock");
        check(WeaponSkinCatalog.pool("invalid").isEmpty(),"unknown category cannot draw");
        var all=new HashSet<String>();
        for(String kind:List.of("dagger","tachi","greatsword")){
            check(WeaponSkinCatalog.pool(kind).size()==13,"each category has thirteen paid appearances");
            for(var skin:WeaponSkinCatalog.pool(kind)){
                check(all.add(skin.key()),"unique stable skin ID");
                check(WeaponSkinCatalog.find(skin.key()).equals(skin),"stable key resolves");
                check(WeaponSkinCatalog.model(kind,skin.key(),"base").equals(skin.model()),"refresh retains correct skin");
                check(WeaponSkinCatalog.model("invalid",skin.key(),"base").equals("base"),"wrong kind cannot apply");
            }
        }
        check(all.size()==39,"thirty-nine paid appearances");
        for(String id:List.of("dagger/iron_dagger","tachi/uchigatana","greatsword/black_iron_greatsword"))
            check(WeaponSkinCatalog.find(id)==null,"default appearance excluded from draw");
        check(WeaponSkinCatalog.model("dagger",null,"base").equals("base"),"reset restores default");
        check(WeaponSkinCatalog.model("dagger","bad","base").equals("base"),"invalid stored skin safely falls back");
        // Trigger an actual atomic-save failure, rather than mocking the persistence method.
        Path blocked=root.resolve("blocked");Files.writeString(blocked,"file");
        var broken=new Economy(blocked.resolve("money.yml").toFile(),logger);broken.set(player,200_000);
        check(broken.purchaseUnlock(player,100_000,first)==Economy.UnlockPurchase.SAVE_FAILED,"save failure rejects ownership");
        check(broken.balance(player)==200_000 && broken.unlocks(player).isEmpty(),"failure rolls back cost and skin together");
        wallet.set(other,100_000);
        check(wallet.purchaseUnlock(other,100_000,first)==Economy.UnlockPurchase.SUCCESS,"another account independently acquires skin");
        wallet=new Economy(file,logger);
        check(wallet.unlocks(player).size()==13 && wallet.unlocks(other).size()==1,"ordinary saves preserve all collections");
        Path corrupt=root.resolve("corrupt.yml");Files.writeString(corrupt,"account-unlocks: broken\n");
        boolean rejected=false;try{new Economy(corrupt.toFile(),logger);}catch(IllegalStateException expected){rejected=true;}
        check(rejected,"corrupt collection data cannot silently reset accounts");
        System.out.println("Skin purchase/collection/persistence/model regression checks passed: "+checks);
    }
}
