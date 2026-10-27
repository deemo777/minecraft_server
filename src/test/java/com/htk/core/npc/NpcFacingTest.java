package com.htk.core.npc;
import org.joml.Vector3f;
public final class NpcFacingTest {
    public static void main(String[] args) {
        int checks=0;
        for(float yaw:new float[]{0,90,180,270,-90,450,720,-450,37}) {
            var face=new Vector3f(0,0,-1).rotate(NpcFacing.rotation(yaw));
            double angle=Math.toRadians(yaw);
            if(Math.abs(face.x+Math.sin(angle))>0.00001 || Math.abs(face.z-Math.cos(angle))>0.00001 || Math.abs(face.y)>0.00001)
                throw new AssertionError("NPC direction differs from Bukkit yaw: "+yaw);
            checks++;
        }
        System.out.println("NPC facing regression checks passed: "+checks);
    }
}
