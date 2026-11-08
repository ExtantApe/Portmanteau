package com.extantape.portmanteau.transaction;

/**
 * Lets the live tests prove that a write which doesn't come out as planned is put back. Only a test server sets these
 * JVM system properties; on any other server they're absent and nothing here does anything.
 *
 * <ul>
 *   <li>{@code portmanteau.test.break-writes}: how many sort writes to spoil, by dropping the last item.</li>
 *   <li>{@code portmanteau.test.break-trunk-saves}: how many trunk saves to spoil, by storing different bytes.</li>
 * </ul>
 */
public final class TestFaults {
    private TestFaults() {}

    /** True, and counts one down, if the next write named by {@code property} should be spoiled. */
    public static synchronized boolean take(String property) {
        String value = System.getProperty(property);
        if (value == null) return false;
        int left;
        try {
            left = Integer.parseInt(value.strip());
        } catch (NumberFormatException e) {
            return false;
        }
        if (left <= 0) return false;
        System.setProperty(property, Integer.toString(left - 1));
        return true;
    }
}
