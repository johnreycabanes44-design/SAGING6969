package com.zenya.module.modules.misc;

import com.zenya.module.Category;
import com.zenya.module.Module;

import java.lang.reflect.Method;

/**
 * Holds the sprint key down every tick so the player never stops running.
 *
 * <p>Vanilla's "toggle sprint" option would fight this, so it is forced off while the module
 * runs and put back on disable. That option is reached by reflection because its accessor is
 * not stable across versions -- if any lookup fails the three cached handles go back to null
 * and only the key press remains, which still sprints.
 */
public class Sprint extends Module {
	public boolean hadSprintToggled;
	public Object sprintToggleOption;
	public Method sprintToggleGetValue;
	public Method sprintToggleSetValue;

	public Sprint() {
		super("Sprint", Category.MISC);
		this.setDescription("Keeps the sprint key permanently pressed and disables the vanilla sprint toggle option so you always run.");
	}

	@Override
	public void onEnable() {
		if (mc == null || mc.options == null) {
			return;
		}
		this.resolveSprintToggleOption();
		this.hadSprintToggled = this.getSprintToggledOption();
		this.setSprintToggledOption(false);
	}

	@Override
	public void onDisable() {
		if (mc == null || mc.options == null) {
			return;
		}
		this.setSprintToggledOption(this.hadSprintToggled);
		try {
			mc.options.keySprint.setDown(false);
		} catch (Throwable ignored) {
			// Releasing the key is best-effort; a key mapping another mod replaced must not break the toggle off.
		}
	}

	@Override
	public void onTick() {
		if (mc == null || mc.player == null || mc.options == null) {
			return;
		}
		this.setSprintToggledOption(false);
		try {
			mc.options.keySprint.setDown(true);
		} catch (Throwable ignored) {
			// Same as onDisable: a failed key press must not throw out of the tick loop.
		}
	}

	public boolean getSprintToggledOption() {
		try {
			this.resolveSprintToggleOption();
			if (this.sprintToggleOption == null || this.sprintToggleGetValue == null) {
				return false;
			}
			Object value = this.sprintToggleGetValue.invoke(this.sprintToggleOption);
			return value instanceof Boolean booleanValue && booleanValue;
		} catch (Throwable ignored) {
			// The option may not exist on this version; report "not toggled" rather than fail.
			return false;
		}
	}

	public void setSprintToggledOption(boolean toggled) {
		try {
			this.resolveSprintToggleOption();
			if (this.sprintToggleOption == null || this.sprintToggleSetValue == null) {
				return;
			}
			this.sprintToggleSetValue.invoke(this.sprintToggleOption, toggled);
		} catch (Throwable ignored) {
			// Nothing to fall back to -- the key press alone still sprints.
		}
	}

	/** Caches the toggle-sprint option and its accessors on first use; a miss leaves all three null so we retry later. */
	public void resolveSprintToggleOption() {
		if (this.sprintToggleOption != null || mc == null || mc.options == null) {
			return;
		}
		try {
			this.sprintToggleOption = mc.options.getClass().getMethod("getSprintToggled").invoke(mc.options);
			if (this.sprintToggleOption == null) {
				return;
			}
			Class<?> optionClass = this.sprintToggleOption.getClass();
			this.sprintToggleGetValue = optionClass.getMethod("getValue");
			this.sprintToggleSetValue = optionClass.getMethod("setValue", Object.class);
		} catch (Throwable ignored) {
			// Any of the three lookups can fail on a version that renamed them; drop back to key-press-only.
			this.sprintToggleOption = null;
			this.sprintToggleGetValue = null;
			this.sprintToggleSetValue = null;
		}
	}
}
