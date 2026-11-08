package com.extantape.portmanteau.transaction;

/** Plans new contents from a snapshot. It gets copies, and may return them rearranged. */
@FunctionalInterface
public interface Planner<T> {
    T[] plan(T[] snapshot) throws PlanRefused;
}
