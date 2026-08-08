package com.zenya.utils;

/**
 * Millisecond stopwatch behind every module's delay gate.
 *
 * <p>{@link #delay} only reports that the interval has elapsed, it never resets:
 * the caller decides when the next interval starts, which is what lets a module
 * roll a fresh random delay before restarting the clock.
 */
public class TimerUtils {
	public long lastMS = System.currentTimeMillis();

	public void reset() {
		this.lastMS = System.currentTimeMillis();
	}

	public boolean delay(long millis) {
		return System.currentTimeMillis() - this.lastMS >= millis;
	}

	/** Float overload for settings that store a delay as a slider value. */
	public boolean delay(float millis) {
		return System.currentTimeMillis() - this.lastMS >= (long) millis;
	}
}
