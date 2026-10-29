package com.htk.core.lobby;

/** Exercise real potion-restoration rules without initializing the server's potion registry. */
public final class LobbyVisionTest {
    private static int checks;
    private static void check(boolean condition,String message) {
        checks++;if(!condition)throw new AssertionError(message);
    }
    public static void main(String[] args) {
        VisionEffect original=new VisionEffect(1200,1,false,true,true,null);
        VisionEffect restored=original.remaining(200);
        check(restored.duration()==1000 && restored.amplifier()==1,"time in the lobby is subtracted from an existing potion");
        check(restored.particles() && restored.icon() && !restored.ambient(),"original display flags are restored");
        check(original.remaining(1200)==null,"expired potion is never resurrected");
        VisionEffect infinite=new VisionEffect(-1,0,true,false,false,null);
        check(infinite.remaining(200000)==infinite,"an original infinite potion remains infinite");
        check(original.remaining(-10).duration()==1200,"tick counter wrap cannot extend the original duration");
        VisionEffect chain=new VisionEffect(100,1,false,true,true,new VisionEffect(600,0,false,true,true,null));
        restored=chain.remaining(200);
        check(restored.amplifier()==0 && restored.duration()==400,"expired stronger potion yields to the still-active hidden weaker potion");
        restored=chain.remaining(50);
        check(restored.duration()==50 && restored.hidden().duration()==550,"hidden effects age alongside the main effect");
        check(chain.remaining(600)==null,"both layers expire while in the lobby");
        System.out.println("Lobby vision regression checks passed: "+checks);
    }
}
