package com.zenya.module.modules.client;

import com.zenya.module.Category;
import com.zenya.module.Module;
import com.zenya.module.ModuleManager;
import com.zenya.setting.ActionSetting;
import com.zenya.setting.ModeSetting;
import com.zenya.setting.Setting;

/**
 * GUI front-end for config saving and loading.
 *
 * <p>It owns no config logic of its own — the buttons delegate straight to
 * {@link ModuleManager}, and the remaining settings are read back by whoever drives
 * the auto-save timer. That keeps a single copy of the save path.
 */
public final class ConfigManager extends Module {
	private final ModeSetting autoSave = new ModeSetting("Auto Save", "On Change",
			"On Change", "Every Minute", "Every 5 Minutes", "Off");
	private final Setting<Boolean> loadOnStart = new Setting<>("Load On Launch", true);
	private final Setting<Boolean> notifyOnSave = new Setting<>("Save Notification", false);
	private final ActionSetting saveNow = new ActionSetting("Save Now", "Save", () -> ModuleManager.INSTANCE.saveConfig());
	private final ActionSetting loadNow = new ActionSetting("Load Now", "Load", () -> ModuleManager.INSTANCE.loadConfig());

	public ConfigManager() {
		super("Config Manager", Category.CLIENT);
		this.setDescription("Manage and share your Frost client configs.");
		this.addSetting(this.autoSave);
		this.addSetting(this.loadOnStart);
		this.addSetting(this.notifyOnSave);
		this.addSetting(this.saveNow);
		this.addSetting(this.loadNow);
	}

	public String getAutoSaveMode() {
		return this.autoSave.getValue();
	}

	public boolean isLoadOnStart() {
		return this.loadOnStart.getValue();
	}

	public boolean isNotifyOnSave() {
		return this.notifyOnSave.getValue();
	}
}
