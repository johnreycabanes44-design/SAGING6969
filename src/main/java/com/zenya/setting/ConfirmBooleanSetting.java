package com.zenya.setting;

/**
 * A toggle the GUI puts behind a confirmation dialog — for the switches that
 * are annoying to undo (wiping a config, resetting every bind).
 */
public class ConfirmBooleanSetting extends Setting<Boolean> {
	private final String confirmTitle;
	private final String confirmMessage;

	public ConfirmBooleanSetting(String name, boolean value, String confirmTitle, String confirmMessage) {
		super(name, value);
		this.confirmTitle = confirmTitle;
		this.confirmMessage = confirmMessage;
	}

	public String getConfirmTitle() {
		return confirmTitle;
	}

	public String getConfirmMessage() {
		return confirmMessage;
	}
}
