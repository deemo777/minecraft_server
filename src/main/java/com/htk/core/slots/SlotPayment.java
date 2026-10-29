package com.htk.core.slots;

import java.util.function.BooleanSupplier;

/** Consume exactly one physical coin, restoring it if durable settlement fails. */
public final class SlotPayment {
    private SlotPayment() { }
    public static boolean attempt(Runnable consume, BooleanSupplier settle, Runnable restore) {
        try {
            consume.run();
            if (settle.getAsBoolean()) return true;
        } catch (RuntimeException failure) {
            try { restore.run(); } catch (RuntimeException rollback) { failure.addSuppressed(rollback); }
            throw failure;
        }
        restore.run();
        return false;
    }
}
