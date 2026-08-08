package com.zenya.module.modules.misc;

import com.zenya.module.Category;
import com.zenya.module.Module;
import com.zenya.setting.Setting;

/**
 * Replaces the local player's name with an alias in chat and nametags.
 *
 * <p>Holds no logic of its own: the swap happens in the render/chat mixins, which
 * reach this module through {@code NameProtectUtil} and read {@link #getFakeName()}.
 * Purely cosmetic — the server is still told the real name.
 */
public class NameProtect extends Module {
	/** Set by the constructor and kept for callers outside the source tree; the mixins resolve the module by name instead. */
	public static NameProtect instance;
	public Setting<String> fakeName;

	public NameProtect() {
		super("NameProtect", Category.MISC);
		this.fakeName = new Setting<>("FakeName", "§5📹§3+§7NPedro");
		instance = this;
		this.addSetting(this.fakeName);
	}

	public String getFakeName() {
		return this.fakeName.getValue();
	}
}
