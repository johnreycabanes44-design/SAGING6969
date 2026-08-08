package com.zenya.setting;

/**
 * A heading in the settings list. Carries no value of its own — the GUI draws
 * the name and skips the usual control.
 */
public class SectionSetting extends Setting<String> {
	public SectionSetting(String name) {
		super(name, name);
	}
}
