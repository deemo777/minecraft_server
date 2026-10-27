package com.htk.core.npc;
import org.joml.Quaternionf;

/** 모델 정면 -Z를 Bukkit yaw의 바라보는 방향으로 변환한다. */
public final class NpcFacing {
    private NpcFacing() { }
    public static Quaternionf rotation(float yaw) {
        return new Quaternionf().rotateY((float)Math.toRadians(180.0-yaw));
    }
}
