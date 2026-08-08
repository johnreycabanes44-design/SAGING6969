package com.zenya.utils;

import com.zenya.module.ModuleManager;
import com.zenya.module.modules.misc.NameProtect;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;

/**
 * Swaps the local player's name for the NameProtect module's alias in text the
 * client is about to draw.
 *
 * <p>Client side only, so nothing sent to the server changes. Every overload
 * returns its argument untouched when the module is off or the name is absent,
 * which keeps the mixins that call this cheap on the common path.
 */
public class NameProtectUtil {

	/** @return the NameProtect module while it is enabled, otherwise null. */
	private static NameProtect activeModule() {
		NameProtect module = (NameProtect) ModuleManager.INSTANCE.getModuleByName("NameProtect");
		return module != null && module.isEnabled() ? module : null;
	}

	public static String replace(String text) {
		if (text == null || text.isEmpty()) {
			return text;
		}

		NameProtect module = activeModule();

		if (module != null) {
			Minecraft mc = Minecraft.getInstance();
			String ownName = null;

			// The session name survives the player entity, so prefer it.
			if (mc.getUser() != null) {
				ownName = mc.getUser().getName();
			} else if (mc.player != null) {
				ownName = mc.player.getName().getString();
			}

			if (ownName != null && !ownName.isEmpty()) {
				String fakeName = module.getFakeName();

				if (fakeName != null) {
					text = text.replace(ownName, fakeName);
				}
			}
		}

		return text;
	}

	/**
	 * Flattens the sequence to plain text to run the replacement, so a rewritten
	 * line comes back unstyled. Untouched lines keep their original styling.
	 */
	public static FormattedCharSequence replace(FormattedCharSequence sequence) {
		if (sequence == null) {
			return null;
		}

		NameProtect module = activeModule();

		if (module != null) {
			StringBuilder plain = new StringBuilder();
			sequence.accept((index, style, codePoint) -> {
				plain.appendCodePoint(codePoint);
				return true;
			});
			String original = plain.toString();
			String replaced = replace(original);

			if (!original.equals(replaced)) {
				return Component.literal(replaced).getVisualOrderText();
			}
		}

		return sequence;
	}

	/** As above: a rewritten value loses its styling, an untouched one is returned as is. */
	public static FormattedText replace(FormattedText text) {
		if (text == null) {
			return null;
		}

		NameProtect module = activeModule();

		if (module != null) {
			String original = text.getString();
			String replaced = replace(original);

			if (!original.equals(replaced)) {
				return Component.literal(replaced);
			}
		}

		return text;
	}
}
