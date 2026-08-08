package com.zenya.gui;

/**
 * Singleton mouse hooks for the HUD position editor.
 *
 * <p>Every hook is inert in this build: no element is ever grabbed, so the click
 * and scroll hooks always report "not handled" and input falls through to
 * whatever is underneath. {@link #isDragging()} is therefore always false —
 * callers must not treat a true return as reachable state.
 */
public final class HudEditor {
	public static final HudEditor INSTANCE = new HudEditor();

	private HudEditor() {
	}

	/** @return true when the editor consumed the click; never does in this build. */
	public boolean onMouseClick(double mouseX, double mouseY, int button) {
		return false;
	}

	public void onMouseRelease() {
	}

	public boolean isDragging() {
		return false;
	}

	public void onMouseDrag(double mouseX, double mouseY) {
	}

	/** @return true when the editor consumed the scroll; never does in this build. */
	public boolean onMouseScroll(double mouseX, double mouseY, double amount) {
		return false;
	}
}
