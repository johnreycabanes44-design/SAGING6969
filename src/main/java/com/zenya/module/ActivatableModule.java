package com.zenya.module;

/**
 * A module that also answers to its own activation key, separate from {@link Module}'s bind.
 *
 * <p>{@code wasActivationKeyPressed} is edge-detection state owned by the client tick loop:
 * it holds last tick's key state so a held key toggles the module only once.
 */
public abstract class ActivatableModule extends Module {
	public int activationKey;
	public boolean wasActivationKeyPressed;

	public ActivatableModule(String name, Category category) {
		super(name, category);
	}

	public void onActivationKeyPressed() {
		this.toggle();
	}

	public int getActivationKey() {
		return this.activationKey;
	}

	/** Rebinds and persists; use {@link #applyActivationKey(int)} while loading a config. */
	public void setActivationKey(int activationKey) {
		this.activationKey = activationKey;
		ModuleManager.INSTANCE.saveConfig();
	}

	/** Rebinds without writing the config back out. */
	public void applyActivationKey(int activationKey) {
		this.activationKey = activationKey;
	}
}
