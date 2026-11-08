package com.extantape.portmanteau.transaction;

import java.util.Arrays;
import java.util.function.Predicate;

/**
 * Replaces a stored value with checks on both sides: the new bytes must decode back to what was meant before they're
 * written, the write must read back exactly, and on any failure the previous bytes go back.
 */
public final class VerifiedBytes {
    private VerifiedBytes() {}

    public interface Cell {
        /** The stored bytes, or null when nothing is stored. */
        byte[] get();

        /** Stores the bytes, or removes the value when given null. */
        void set(byte[] bytes);
    }

    public enum Outcome { WRITTEN, UNCHANGED, REFUSED, RESTORED, FAILED }

    public record Result(Outcome outcome, String reason, byte[] previous) {
        public boolean ok() {
            return outcome == Outcome.WRITTEN || outcome == Outcome.UNCHANGED;
        }
    }

    /**
     * @param decodesBack checks that the new bytes decode to exactly the contents being saved
     */
    public static Result replace(Cell cell, byte[] next, Predicate<byte[]> decodesBack) {
        byte[] previous = cell.get();
        if (Arrays.equals(previous, next)) return new Result(Outcome.UNCHANGED, null, previous);
        try {
            if (next != null && !decodesBack.test(next)) {
                return new Result(Outcome.REFUSED, "the saved form didn't decode back to the same items", previous);
            }
        } catch (RuntimeException e) {
            return new Result(Outcome.REFUSED, "the saved form couldn't be decoded: " + e, previous);
        }
        try {
            cell.set(next);
            if (Arrays.equals(cell.get(), next)) return new Result(Outcome.WRITTEN, null, previous);
        } catch (RuntimeException ignored) {
            // Fall through and put the previous bytes back.
        }
        try {
            cell.set(previous);
            if (Arrays.equals(cell.get(), previous)) {
                return new Result(Outcome.RESTORED, "the write didn't read back, so the previous value was put back", previous);
            }
        } catch (RuntimeException ignored) {
            // Reported below.
        }
        return new Result(Outcome.FAILED, "the write didn't read back and the previous value couldn't be put back", previous);
    }
}
