package com.zenya.utils;

import net.minecraft.client.Minecraft;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Fakes attack and use clicks by driving the vanilla key bindings, which keeps
 * swing animation, cooldowns and break progress in step with a real click.
 *
 * <p>The hold runs on a small daemon pool so it can outlive the tick that asked
 * for it. The queue is bounded and overflow is discarded on purpose: a module
 * clicking faster than the holds can drain should lose clicks, not build a
 * backlog that fires long after the target is gone.
 */
public class MouseSimulation {
	public static ExecutorService clickExecutor = new ThreadPoolExecutor(1, 2, 5L, TimeUnit.SECONDS,
			new ArrayBlockingQueue<>(64), runnable -> {
				Thread thread = new Thread(runnable, "zenya-click-sim");
				thread.setDaemon(true);
				return thread;
			}, new ThreadPoolExecutor.DiscardPolicy());
	public static Minecraft mc = Minecraft.getInstance();

	/** @param button 0 for attack, 1 for use; anything else is ignored. */
	public static void mousePress(int button) {
		if (button == 0) {
			mc.options.keyAttack.setDown(true);
		} else if (button == 1) {
			mc.options.keyUse.setDown(true);
		}
	}

	public static void mouseRelease(int button) {
		if (button == 0) {
			mc.options.keyAttack.setDown(false);
		} else if (button == 1) {
			mc.options.keyUse.setDown(false);
		}
	}

	/** Presses off-thread, waits {@code holdMillis}, and always releases. */
	public static void mouseClick(int button, int holdMillis) {
		clickExecutor.submit(() -> {
			try {
				mousePress(button);
				Thread.sleep(holdMillis);
			} catch (InterruptedException interrupted) {
				// Swallowed: only the pool shutting down interrupts us, and the
				// finally below still releases the key.
				Thread.currentThread().interrupt();
			} finally {
				mouseRelease(button);
			}
		});
	}

	public static void mouseClick(int button) {
		mouseClick(button, 35);
	}
}
