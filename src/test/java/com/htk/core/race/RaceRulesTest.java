package com.htk.core.race;

import java.util.Random;

public final class RaceRulesTest {
    private static int checks;
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    public static void main(String[] args) {
        var player = new org.bukkit.Location(null, 10.2, 70.9, -5.8, 32, 40);
        var candidates = RacePlacement.candidates(player);
        check(candidates.size() == 60, "automatic scene search is bounded");
        check(player.getX() == 10.2 && player.getY() == 70.9 && player.getYaw() == 32 && player.getPitch() == 40,
                "nearby search preserves player location and view");
        for (var candidate : candidates) {
            check(candidate.getPitch() == 0 && candidate.getYaw() % 90 == 0, "automatic tracks use horizontal cardinal headings");
            check(Math.abs(candidate.getY() - 70) <= 1, "search remains near the player's floor");
            var center = new org.bukkit.util.Vector(10.5, 0, -5.5);
            var finish = RaceScene.point(candidate, 20, 0).toVector().setY(0);
            check(finish.distance(center) < 32, "whole track remains near the initiating player");
        }
        int[] evaluated = {0};
        var first = RacePlacement.find(player, at -> { evaluated[0]++; return true; });
        check(first.equals(candidates.getFirst()) && evaluated[0] == 1, "solo round uses first available terrain without saved point");
        evaluated[0] = 0;
        var later = RacePlacement.find(player, at -> ++evaluated[0] == 3);
        check(later.equals(candidates.get(2)), "obstructed direction falls back to another nearby track");
        evaluated[0] = 0;
        check(RacePlacement.find(player, at -> { evaluated[0]++; return false; }) == null && evaluated[0] == 60,
                "no terrain still permits GUI-only race without admin setup");
        for (int yaw : new int[]{-180, -90, 0, 90, 180, 270}) {
            var origin = new org.bukkit.Location(null, 0.5, 70, 0.5, yaw, 0);
            var finish = RaceScene.point(origin, 20, 0);
            var left = RaceScene.point(origin, 0, -2);
            var right = RaceScene.point(origin, 0, 2);
            check(Math.abs(finish.toVector().distance(origin.toVector()) - 20) < 1e-9, "cardinal track length is twenty blocks");
            check(Math.abs(left.toVector().distance(right.toVector()) - 4) < 1e-9, "outer lanes remain four blocks apart");
            check(origin.getX() == 0.5 && origin.getY() == 70 && origin.getZ() == 0.5, "scene placement cannot mutate saved origin");
        }
        check(!RaceRules.validStake(-100) && !RaceRules.validStake(0) && !RaceRules.validStake(1001), "only fixed stakes accepted");
        for (long stake : RaceRules.STAKES) {
            check(RaceRules.payout(stake, true) == 3 * stake, "total payout includes stake");
            check(RaceRules.payout(stake, false) == 0, "losing bet pays zero");
        }
        var random = new Random(981243);
        int[] wins = new int[3]; int overtakes = 0;
        for (int round = 0; round < 3000; round++) {
            var plan = RaceRules.plan(random); wins[plan.winner()]++;
            double[] previous = new double[3]; int previousLeader = -1;
            for (int frame = 0; frame <= 100; frame++) {
                int leader = 0;
                for (int runner = 0; runner < 3; runner++) {
                    double progress = plan.progress(runner, frame / 100.0);
                    check(progress >= previous[runner] && progress <= 1, "animation never reverses or passes finish early");
                    check(progress == plan.progress(runner, frame / 100.0), "all spectators see identical trajectory");
                    if (progress > plan.progress(leader, frame / 100.0)) leader = runner;
                    previous[runner] = progress;
                }
                if (previousLeader >= 0 && leader != previousLeader) overtakes++;
                previousLeader = leader;
            }
            check(plan.progress(plan.winner(), 1) == 1, "selected winner reaches finish");
            for (int runner = 0; runner < 3; runner++) {
                check(plan.progress(runner, -1) == 0, "negative time clamped");
                check(plan.progress(runner, 2) == plan.progress(runner, 1), "late time clamped");
                if (runner != plan.winner()) check(plan.progress(runner, 1) < 1, "unique winner");
            }
        }
        for (int count : wins) check(count > 850 && count < 1150, "seeded sample keeps each lane near one third");
        check(overtakes > 3000, "race has visible position changes");
        System.out.println("Race rules checks passed: " + checks);
    }
}
