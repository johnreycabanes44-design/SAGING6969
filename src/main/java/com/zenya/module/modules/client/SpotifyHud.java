package com.zenya.module.modules.client;

/**
 * Fixed dimensions of the Spotify now-playing card.
 *
 * <p>Only the geometry survives in this build — there is no track state and no draw
 * call. The HUD editor still needs the card's footprint to lay out and snap the slot,
 * so the two sizes stay behind this class instead of being duplicated at the call site.
 */
public final class SpotifyHud {
	private SpotifyHud() {
	}

	public static int getCardW() {
		return 220;
	}

	public static int getCardH() {
		return 56;
	}
}
