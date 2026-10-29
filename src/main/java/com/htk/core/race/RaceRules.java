package com.htk.core.race;

import java.util.random.RandomGenerator;

/** Equal odds, fixed stakes, and immutable server-side trajectories shared by every spectator. */
public final class RaceRules {
    public static final int RUNNERS = 3;
    public static final java.util.List<Long> STAKES = java.util.List.of(100L, 500L, 1000L);
    private static final int STEPS = 60;
    private RaceRules() {}

    public static boolean validStake(long amount) { return amount == 100 || amount == 500 || amount == 1000; }
    public static long payout(long stake, boolean won) {
        if (!validStake(stake)) throw new IllegalArgumentException("Invalid stake");
        return won ? Math.multiplyExact(stake, 3) : 0;
    }

    public static final class Plan {
        private final int winner;
        private final double[][] positions;
        private Plan(int winner, double[][] positions) { this.winner = winner; this.positions = positions; }
        public int winner() { return winner; }
        public double progress(int runner, double time) {
            if (runner < 0 || runner >= RUNNERS || !Double.isFinite(time)) throw new IllegalArgumentException();
            double step = Math.clamp(time, 0, 1) * STEPS;
            int low = (int) step, high = Math.min(STEPS, low + 1);
            return positions[runner][low] + (positions[runner][high] - positions[runner][low]) * (step - low);
        }
    }

    public static Plan plan(RandomGenerator random) {
        int winner = random.nextInt(RUNNERS);
        double[][] positions = new double[RUNNERS][STEPS + 1];
        for (int runner = 0; runner < RUNNERS; runner++) {
            double speed = 1;
            for (int step = 1; step <= STEPS; step++) {
                if (step % 5 == 1) speed = random.nextDouble(0.3, 1.7);
                positions[runner][step] = positions[runner][step - 1] + speed;
            }
            double end = runner == winner ? 1 : random.nextDouble(0.90, 0.97);
            double total = positions[runner][STEPS];
            for (int step = 1; step <= STEPS; step++) positions[runner][step] *= end / total;
            positions[runner][STEPS] = end;
        }
        return new Plan(winner, positions);
    }
}
