package com.zenya.setting;

/**
 * A setting that is really a button: the value is the button's label and
 * {@link #trigger()} runs the action behind it.
 */
public class ActionSetting extends Setting<String> {
	private final Runnable action;

	public ActionSetting(String name, String label, Runnable action) {
		super(name, label);
		this.action = action;
	}

	public void trigger() {
		if (action != null) {
			action.run();
		}
	}
}
