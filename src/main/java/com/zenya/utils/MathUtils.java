package com.zenya.utils;

import java.util.Random;

/**
 * Numeric helpers for the aim and timing modules.
 *
 * <p>The shared {@link Random} is clock-seeded so the humanisation jitter differs
 * between sessions. It is deliberately not a SecureRandom: nothing here is
 * security relevant, and every caller wants the cheap generator.
 */
public class MathUtils {
	public static Random random = new Random(System.currentTimeMillis());

	/** Snaps {@code value} to the nearest multiple of {@code step}. */
	public static double roundToPoint(double value, double step) {
		return step * Math.round(value / step);
	}

	/** Upper bound is exclusive, matching {@link Random#nextInt(int, int)}. */
	public static int randomInt(int min, int max) {
		return random.nextInt(min, max);
	}

	/** Smoothstep-eased interpolation; {@code progress} is clamped to 0..1 first. */
	public static double smoothStepLerp(double progress, double from, double to) {
		double clamped = Math.max(0.0, Math.min(1.0, progress));
		double eased = clamped * clamped * (3.0 - 2.0 * clamped);
		return from + (to - from) * eased;
	}

	/**
	 * Steps {@code from} towards {@code to} by a whole number of units, never
	 * overshooting. Rounding the step up means it always makes progress, so a
	 * caller looping on this cannot stall short of the target.
	 */
	public static double goodLerp(float factor, double from, double to) {
		int step = (int) Math.ceil(Math.abs(to - from) * factor);

		if (from < to) {
			return Math.min(from + step, to);
		}

		return Math.max(from - step, to);
	}
}
