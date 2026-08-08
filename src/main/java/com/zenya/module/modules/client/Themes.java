package com.zenya.module.modules.client;

import com.zenya.module.Category;
import com.zenya.module.Module;
import com.zenya.setting.Setting;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The client's colour themes. {@link #ALL} is the list the click GUI draws, in order; the
 * selection lives in a plain {@link Setting} so it is saved with the rest of the config.
 *
 * <p>Lookup is by case-insensitive name and falls back to Ocean, so a config naming a theme
 * that no longer exists still loads. {@link Holder} defers building the module until
 * something actually asks for the selection — reading {@link #ALL} alone does not create it.
 */
public final class Themes extends Module {
	public static final List<Theme> ALL = new ArrayList<>();

	/** One full hue sweep of the animated Rainbow theme. */
	private static final long RAINBOW_CYCLE_MS = 6000L;

	private static final Theme OCEAN = theme("Ocean", "Cold clean blue", 0xFF3B82F6, 0xFF0B1220, 0xFF0E7490, 0xFF38BDF8, 0xFF60A5FA);
	private static final Theme FROST = theme("Frost", "Sharp ice red", 0xFFEF4444, 0xFF111827, 0xFF7F1D1D, 0xFFEF4444, 0xFFFCA5A5);
	private static final Theme LAVENDER = theme("Lavender", "Soft violet glow", 0xFFA78BFA, 0xFF171322, 0xFF6D28D9, 0xFFA78BFA, 0xFFE9D5FF);
	private static final Theme EMERALD = theme("Emerald", "Bright green glass", 0xFF10B981, 0xFF071711, 0xFF047857, 0xFF10B981, 0xFFA7F3D0);
	private static final Theme GOLD = theme("Gold", "Warm premium gold", 0xFFFBBF24, 0xFF181308, 0xFFB45309, 0xFFFBBF24, 0xFFFDE68A);
	private static final Theme RUBY = theme("Ruby", "Deep red contrast", 0xFFE11D48, 0xFF18080D, 0xFF9F1239, 0xFFE11D48, 0xFFFB7185);
	private static final Theme AMETHYST = theme("Amethyst", "Purple crystal", 0xFF8B5CF6, 0xFF151022, 0xFF5B21B6, 0xFF8B5CF6, 0xFFC4B5FD);
	private static final Theme MINT = theme("Mint", "Fresh cyan mint", 0xFF34D399, 0xFF071714, 0xFF0F766E, 0xFF34D399, 0xFF99F6E4);
	private static final Theme MIDNIGHT = theme("Midnight", "Quiet blue night", 0xFF64748B, 0xFF020617, 0xFF1E293B, 0xFF64748B, 0xFFCBD5E1);
	private static final Theme SAKURA = theme("Sakura", "Light pink bloom", 0xFFFB7185, 0xFF1A0B11, 0xFFBE123C, 0xFFFB7185, 0xFFFFCDD5);
	private static final Theme ROSE = theme("Rose", "Hot rose neon", 0xFFFF007F, 0xFF180611, 0xFFBE185D, 0xFFFF007F, 0xFFF9A8D4);
	private static final Theme SKY = theme("Sky", "Clear cyan blue", 0xFF00CCFF, 0xFF07131A, 0xFF0369A1, 0xFF00CCFF, 0xFFBAE6FD);
	private static final Theme FOREST = theme("Forest", "Dark green field", 0xFF228B22, 0xFF07120A, 0xFF166534, 0xFF22C55E, 0xFFBBF7D0);
	private static final Theme SUNSET = theme("Sunset", "Orange red dusk", 0xFFFF4500, 0xFF190B05, 0xFFC2410C, 0xFFFF4500, 0xFFFED7AA);
	private static final Theme RAINBOW = theme("Rainbow", "Animated spectrum", 0xFFFFFFFF, 0xFFEF4444, 0xFFF59E0B, 0xFF10B981, 0xFF3B82F6, 0xFF8B5CF6);

	private final Setting<String> themeSetting;

	private Themes() {
		super("Themes", Category.CLIENT);
		setDescription("Choose the Frost client colour theme.");
		themeSetting = new Setting<>("Theme", OCEAN.name());
		addSetting(themeSetting);
		ZenyaPlus.setAccentColor(new Color(OCEAN.accentArgb(), true));
	}

	public static Themes getInstance() {
		return Holder.INSTANCE;
	}

	public static Color getAccent() {
		return new Color(currentTheme().accentArgb(), true);
	}

	/** True while the animated theme is selected; callers then use {@link #rainbowAt}. */
	public static boolean isRainbow() {
		return "Rainbow".equalsIgnoreCase(Holder.INSTANCE.themeSetting.getValue());
	}

	/**
	 * Colour of the rainbow at position {@code index}, cycling once every
	 * {@link #RAINBOW_CYCLE_MS}. {@code hueStep} is the hue offset per index, so a list drawn
	 * with a small step shows a gradient instead of one flat colour.
	 */
	public static int rainbowAt(int index, float hueStep) {
		float hue = (float) (System.currentTimeMillis() % RAINBOW_CYCLE_MS) / RAINBOW_CYCLE_MS
				+ index * Math.max(0.01f, hueStep);
		return 0xFF000000 | (Color.HSBtoRGB(hue % 1.0f, 0.85f, 1.0f) & 0xFFFFFF);
	}

	/** Ocean is the fallback when the saved name matches no known theme. */
	public static Theme currentTheme() {
		String selected = Holder.INSTANCE.themeSetting.getValue();
		for (Theme theme : ALL) {
			if (theme.name().equalsIgnoreCase(selected)) {
				return theme;
			}
		}
		return OCEAN;
	}

	/** Stores the selection and pushes its accent into {@link ZenyaPlus} for the rest of the GUI. */
	public static void apply(Theme theme) {
		Theme selected = theme == null ? OCEAN : theme;
		Holder.INSTANCE.themeSetting.setValue(selected.name());
		ZenyaPlus.setAccentColor(new Color(selected.accentArgb(), true));
	}

	public Setting<String> selectedSetting() {
		return themeSetting;
	}

	public Theme getActive() {
		return currentTheme();
	}

	public void setActive(Theme theme) {
		apply(theme);
	}

	private static Theme theme(String name, String description, int accentArgb, int... palette) {
		return new Theme(name, description, accentArgb, palette);
	}

	static {
		Collections.addAll(ALL, OCEAN, FROST, LAVENDER, EMERALD, GOLD, RUBY, AMETHYST, MINT,
				MIDNIGHT, SAKURA, ROSE, SKY, FOREST, SUNSET, RAINBOW);
	}

	/** Lazy holder: the module is built on first access, not when {@link #ALL} is read. */
	private static final class Holder {
		private static final Themes INSTANCE = new Themes();

		private Holder() {
		}
	}

	/**
	 * One theme. All colours are ARGB; {@code palette[0]} is the GUI background and the whole
	 * array is drawn as the theme's swatch strip, so its length is free to vary per theme.
	 */
	public record Theme(String name, String description, int accentArgb, int[] palette) {
	}
}
