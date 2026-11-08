package com.extantape.portmanteau.transaction;

/** Thrown by a planner that can't plan safely, such as for an over-stacked item. Nothing is written. */
public final class PlanRefused extends Exception {
    public PlanRefused(String reason) {
        super(reason, null, false, false);
    }
}
