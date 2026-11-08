package com.extantape.portmanteau.sorting.api;

/** Why a sort is happening. */
public enum SortReason {
    /** The player closed the container or their inventory. */
    CLOSE,
    /** A hotkey click. */
    HOTKEY,
    /** {@code /isort} or another command. */
    COMMAND,
    /** Quick transfer, by clicking outside the window. */
    QUICK_TRANSFER,
    /** Another plugin asked through the API. */
    API
}
