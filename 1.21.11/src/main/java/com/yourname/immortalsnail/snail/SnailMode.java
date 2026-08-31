package com.yourname.immortalsnail.snail;

/**
 * The Snail's behavioral state machine.
 */
public enum SnailMode {
    /** Moving toward the closest player at the configured speed. */
    TRAVELING,
    /** Frozen in place, breaking a block in front of it. */
    BREAKING
}
