package com.zenya.utils;

import java.util.Arrays;

/**
 * Rolling estimate of the server's tick rate, sampled from the gap between
 * consecutive world-time packets.
 *
 * <p>The server sends one such packet per tick, so the inverse of the gap is the
 * tick rate. Samples are clamped to 0..20 because the burst of packets that
 * arrives after a lag spike would otherwise read as hundreds of TPS, and zeroed
 * slots are skipped so the average is not dragged down before the ring fills.
 */
public class TickRateUtil {
	public static TickRateUtil INSTANCE = new TickRateUtil();

	public float[] ticks = new float[20];
	public int nextIndex = 0;
	public long lastTime = -1L;

	public TickRateUtil() {
		Arrays.fill(this.ticks, 0.0f);
	}

	/** Call once per received tick packet. The first one only sets the baseline. */
	public void onPacket() {
		if (this.lastTime != -1L) {
			float seconds = (float) (System.currentTimeMillis() - this.lastTime) / 1000.0f;
			this.ticks[this.nextIndex % this.ticks.length] = Math.max(0.0f, Math.min(20.0f, 20.0f / seconds));
			this.nextIndex++;
		}

		this.lastTime = System.currentTimeMillis();
	}

	public float getTPS() {
		int samples = 0;
		float total = 0.0f;

		for (float tick : this.ticks) {
			if (tick > 0.0f) {
				total += tick;
				samples++;
			}
		}

		return samples == 0 ? 20.0f : total / (float) samples;
	}
}
