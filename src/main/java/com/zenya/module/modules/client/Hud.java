package com.zenya.module.modules.client;

import com.zenya.gui.ClickGUI;
import com.zenya.module.Category;
import com.zenya.module.Module;
import com.zenya.module.ModuleManager;
import com.zenya.setting.Setting;
import com.zenya.utils.TickRateUtil;
import com.zenya.utils.ZenyaFont;
import com.zenya.utils.renderer.RenderUtil;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;

/**
 * The in-game overlay: watermark, info readouts, module list, notifications,
 * compass, totem/CPS counters, armour and staff list.
 *
 * <p>Every element is positioned independently in {@link #positions} and may be
 * dragged, but only while a Chat/Inventory/ClickGUI screen is open - that "editing"
 * flag is threaded through every draw method so the HUD stays inert during play.
 * Dragging writes straight into the array returned by {@link #getElementPos}, so
 * callers must treat that array as live client state, not a copy.
 */
public final class Hud extends Module {
	private static final List<Module> ENABLED_BUFFER = new ArrayList<>(64);
	private static final List<NotificationEntry> NOTIFICATIONS = new ArrayList<>(8);
	private static int CARD_BG = -234881024;
	private static int PILL_BG = -234881024;
	private static final int TEXT_PRIMARY = -1;
	private static final int TEXT_SECONDARY = -3158065;
	private static final float CARD_RADIUS = 10.0f;
	private static final EnumMap<HudElement, int[]> positions = new EnumMap<>(HudElement.class);
	private static final EnumMap<HudElement, Boolean> dragging = new EnumMap<>(HudElement.class);
	private static final List<Long> lmbClickTimes = new ArrayList<>();
	private static final List<Long> rmbClickTimes = new ArrayList<>();
	private static final String[] STAFF_NAMES = {
			"OGsummer", "ItsDefRealMe", "LzouZMp5", "Munkerlich", "Showered", "ArchivePedro", "Evify",
			"Noah", "Zababi", "Nathan", "PastaGamer", "Pastagamer08", "Bautiegar", "BloodSplulse",
			"GsMusie", "Frwost", "FluffyMaster07", "W1zox_", "ItsZDeath", "Chaon" };
	private static final List<String> onlineStaffBuffer = new ArrayList<>(STAFF_NAMES.length);

	private static Hud INSTANCE;
	private static int dragX;
	private static int dragY;
	private static Screen lastScreen;
	private static boolean lmbWasPressed = false;
	private static boolean rmbWasPressed = false;

	private final Setting<Boolean> watermark = new Setting<>("Watermark", true);
	private final Setting<Boolean> showPing = new Setting<>("Ping Counter", true);
	private final Setting<Boolean> showXyz = new Setting<>("XYZ Coords", true);
	private final Setting<Boolean> showTps = new Setting<>("TPS", true);
	private final Setting<Boolean> showBps = new Setting<>("BPS", true);
	private final Setting<Boolean> moduleList = new Setting<>("Module List", true);
	private final Setting<Boolean> notifications = new Setting<>("Notifications", true);
	private final Setting<Integer> notificationTime = new Setting<>("Notification Time", 2, 1, 5);
	private final Setting<Boolean> showCompass = new Setting<>("Compass", true);
	private final Setting<Boolean> compassPlayers = new Setting<>("Compass Players", true);
	private final Setting<Boolean> compassMobs = new Setting<>("Compass Mobs", true);
	private final Setting<Integer> compassSize = new Setting<>("Compass Size", 35, 15, 100);
	private final Setting<Boolean> totemCounter = new Setting<>("Totem Counter", true);
	private final Setting<Boolean> keystrokes = new Setting<>("Keystrokes", true);
	private final Setting<Boolean> armorHud = new Setting<>("Armor HUD", true);
	private final Setting<Boolean> staffList = new Setting<>("Staff List", true);
	private final Setting<Boolean> resetHud = new Setting<>("Reset HUD", false);

	/** Live array - dragging and the config loader both write through it. */
	public static int[] getElementPos(HudElement element) {
		return positions.computeIfAbsent(element, Hud::defaultPos);
	}

	public static void setElementPos(HudElement element, int x, int y) {
		positions.put(element, new int[] { x, y });
	}

	/** Falls back to an 800x480 window so positions still resolve before the game window exists. */
	private static int[] defaultPos(HudElement element) {
		Minecraft client = Minecraft.getInstance();
		int screenWidth = client != null ? client.getWindow().getGuiScaledWidth() : 800;
		int screenHeight = client != null ? client.getWindow().getGuiScaledHeight() : 480;
		return switch (element) {
			case WATERMARK -> new int[] { 8, 8 };
			case PING -> new int[] { 8, 30 };
			case XYZ -> new int[] { 8, 52 };
			case TPS -> new int[] { 8, 74 };
			case BPS -> new int[] { 8, 96 };
			case MODULE_LIST -> new int[] { screenWidth - 8, 8 };
			case NOTIFICATIONS -> new int[] { screenWidth - 176, screenHeight - 56 };
			case COMPASS -> new int[] { screenWidth / 2 - 25, 30 };
			case TOTEM_COUNTER -> new int[] { screenWidth / 2 + 30, 30 };
			case KEYSTROKES -> new int[] { screenWidth / 2 - 60, screenHeight - 80 };
			case ARMOR_HUD -> new int[] { screenWidth / 2 - 50, screenHeight - 90 };
			case STAFF_LIST -> new int[] { screenWidth - 140, 60 };
		};
	}

	public Hud() {
		super("Hud", Category.CLIENT);
		this.setDescription("Draws the on-screen overlay. Move elements by opening Chat.");
		this.addSetting(this.watermark);
		this.addSetting(this.showPing);
		this.addSetting(this.showXyz);
		this.addSetting(this.showTps);
		this.addSetting(this.showBps);
		this.addSetting(this.moduleList);
		this.addSetting(this.notifications);
		this.addSetting(this.notificationTime);
		this.addSetting(this.showCompass);
		this.addSetting(this.compassPlayers);
		this.addSetting(this.compassMobs);
		this.addSetting(this.compassSize);
		this.addSetting(this.totemCounter);
		this.addSetting(this.keystrokes);
		this.addSetting(this.armorHud);
		this.addSetting(this.staffList);
		this.addSetting(this.resetHud);
		INSTANCE = this;
	}

	@Override
	public void onTick() {
		if (this.resetHud.getValue()) {
			this.resetPositions();
			this.resetHud.setValue(false);
		}
	}

	private void resetPositions() {
		positions.clear();
		for (HudElement element : HudElement.values()) {
			int[] pos = defaultPos(element);
			setElementPos(element, pos[0], pos[1]);
		}
	}

	public static String moduleListLayout() {
		return "Top";
	}

	public static boolean showModuleList() {
		return INSTANCE != null && INSTANCE.isEnabled() && INSTANCE.moduleList.getValue();
	}

	public static boolean hideVanillaPotionEffects() {
		return INSTANCE != null && INSTANCE.isEnabled();
	}

	public static void renderHud(GuiGraphics graphics) {
		if (INSTANCE == null || !INSTANCE.isEnabled()) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (client == null || client.player == null) {
			return;
		}
		if (client.getDebugOverlay().showDebugScreen()) {
			return;
		}
		CARD_BG = ZenyaPlus.getBackgroundARGB();
		PILL_BG = ZenyaPlus.getBackgroundARGB();
		Font font = client.font;
		int accent = ZenyaPlus.getAccentARGB();
		boolean editing = client.screen instanceof ChatScreen
				|| client.screen instanceof InventoryScreen
				|| client.screen instanceof ClickGUI;
		if (INSTANCE.watermark.getValue()) {
			renderComponent(graphics, font, HudElement.WATERMARK, "Frost", " +", accent, editing);
		}
		if (INSTANCE.showPing.getValue()) {
			renderComponent(graphics, font, HudElement.PING, "Ping: ", getPing(client) + "ms", accent, editing);
		}
		if (INSTANCE.showXyz.getValue()) {
			String coords = String.format("%.1f %.1f %.1f", client.player.getX(), client.player.getY(), client.player.getZ());
			renderComponent(graphics, font, HudElement.XYZ, "XYZ: ", coords, accent, editing);
		}
		if (INSTANCE.showTps.getValue()) {
			renderComponent(graphics, font, HudElement.TPS, "TPS: ",
					String.format("%.1f", TickRateUtil.INSTANCE.getTPS()), accent, editing);
		}
		if (INSTANCE.showBps.getValue()) {
			double deltaX = client.player.getX() - client.player.xOld;
			double deltaZ = client.player.getZ() - client.player.zOld;
			double blocksPerSecond = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ) * 20.0;
			renderComponent(graphics, font, HudElement.BPS, "BPS: ",
					String.format("%.1f", blocksPerSecond), accent, editing);
		}
		if (INSTANCE.moduleList.getValue()) {
			drawModuleList(graphics, font, editing);
		}
		if (INSTANCE.notifications.getValue()) {
			drawNotifications(graphics, font, editing);
		}
		if (INSTANCE.showCompass.getValue()) {
			drawCompass(graphics, font, editing);
		}
		if (INSTANCE.totemCounter.getValue()) {
			drawTotemCounter(graphics, font, editing);
		}
		if (INSTANCE.keystrokes.getValue()) {
			drawKeystrokes(graphics, font, editing);
		}
		if (INSTANCE.armorHud.getValue()) {
			drawArmorHud(graphics, font, editing);
		}
		if (INSTANCE.staffList.getValue()) {
			drawStaffList(graphics, font, editing);
		}
		// Closing the drag-capable screen is what commits moved positions to disk.
		if (lastScreen != null && client.screen == null
				&& (lastScreen instanceof ChatScreen || lastScreen instanceof ClickGUI)) {
			ModuleManager.INSTANCE.saveConfig();
		}
		lastScreen = client.screen;
	}

	/** Newest first; a re-toggle of the same module replaces its pending entry rather than stacking. */
	public static void pushModuleNotification(Module module, boolean enabled) {
		if (module == null) {
			return;
		}
		long now = System.currentTimeMillis();
		long lifetime = notificationLifetimeMs();
		String title = module.getDisplayName();
		NOTIFICATIONS.removeIf(entry -> entry.title().equals(title) || now - entry.createdAt() > lifetime);
		NOTIFICATIONS.add(0, new NotificationEntry(title, enabled ? "has been enabled" : "has been disabled",
				moduleIcon(module), now));
		while (NOTIFICATIONS.size() > 4) {
			NOTIFICATIONS.remove(NOTIFICATIONS.size() - 1);
		}
	}

	/** "Notification Time" in seconds, clamped to the setting's own 1-5 range. */
	private static long notificationLifetimeMs() {
		int seconds = INSTANCE == null ? 2 : INSTANCE.notificationTime.getValue();
		return (long) Math.max(1, Math.min(5, seconds)) * 1000L;
	}

	private static int getPing(Minecraft client) {
		try {
			if (client.getConnection() != null) {
				PlayerInfo self = client.getConnection().getPlayerInfo(client.player.getUUID());
				if (self != null) {
					return self.getLatency();
				}
			}
		} catch (Exception ignored) {
			// Connection/player can drop mid-frame; showing 0ms beats crashing the render loop.
		}
		return 0;
	}

	/** One label/value pill, e.g. "XYZ: 12.0 64.0 -8.0". */
	private static void renderComponent(GuiGraphics graphics, Font font, HudElement element,
			String label, String value, int accent, boolean editing) {
		int[] pos = getElementPos(element);
		int labelWidth = ZenyaFont.width(font, label);
		int valueWidth = ZenyaFont.width(font, value);
		int width = labelWidth + valueWidth + 20;
		if (editing) {
			handleDrag(element, width, 18);
			RenderUtil.drawOutline(graphics, pos[0], pos[1], width, 18.0f, 6.0f, 1.0f, -2008376065, false);
		}
		int valueColor = Themes.isRainbow() ? Themes.rainbowAt(element.ordinal(), 0.05f) : accent;
		card(graphics, pos[0], pos[1], width, 18, valueColor);
		ZenyaFont.draw(graphics, font, label, pos[0] + 10, pos[1] + 5, TEXT_PRIMARY, false);
		ZenyaFont.draw(graphics, font, value, pos[0] + 10 + labelWidth, pos[1] + 5, valueColor, false);
	}

	private static void handleDrag(HudElement element, int width, int height) {
		int[] pos = getElementPos(element);
		handleDrag(element, pos[0], pos[1], width, height, 0, 0);
	}

	/**
	 * Grabs the element under the cursor while LMB is held and writes the new position
	 * straight into its live array. anchorX/anchorY re-add the offset for right-aligned
	 * elements, whose hit box starts left of the stored origin.
	 */
	private static void handleDrag(HudElement element, int x, int y, int width, int height, int anchorX, int anchorY) {
		Minecraft client = Minecraft.getInstance();
		double mouseX = client.mouseHandler.xpos() * (double) client.getWindow().getGuiScaledWidth()
				/ (double) client.getWindow().getScreenWidth();
		double mouseY = client.mouseHandler.ypos() * (double) client.getWindow().getGuiScaledHeight()
				/ (double) client.getWindow().getScreenHeight();
		int[] pos = getElementPos(element);
		boolean hovered = mouseX >= (double) x && mouseX <= (double) (x + width)
				&& mouseY >= (double) y && mouseY <= (double) (y + height);
		if (GLFW.glfwGetMouseButton(client.getWindow().handle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS) {
			if (hovered && !isAnyDragging()) {
				dragging.put(element, true);
				dragX = (int) mouseX - x;
				dragY = (int) mouseY - y;
			}
			if (dragging.getOrDefault(element, false)) {
				pos[0] = (int) mouseX - dragX + anchorX;
				pos[1] = (int) mouseY - dragY + anchorY;
			}
		} else {
			if (dragging.getOrDefault(element, false)) {
				ModuleManager.INSTANCE.saveConfig();
			}
			dragging.put(element, false);
		}
	}

	/** Only one element may be grabbed at a time, otherwise overlapping hit boxes both move. */
	private static boolean isAnyDragging() {
		return dragging.containsValue(true);
	}

	private static void drawModuleList(GuiGraphics graphics, Font font, boolean editing) {
		int[] pos = getElementPos(HudElement.MODULE_LIST);
		ENABLED_BUFFER.clear();
		for (Module module : ModuleManager.INSTANCE.getModules()) {
			if (!module.isEnabled() || module.getCategory() == Category.CLIENT) {
				continue;
			}
			ENABLED_BUFFER.add(module);
		}
		int accent = ZenyaPlus.getAccentARGB();
		boolean rainbow = Themes.isRainbow();
		Minecraft client = Minecraft.getInstance();
		int screenWidth = client != null ? client.getWindow().getGuiScaledWidth() : 800;
		// Past the screen midpoint the list grows leftwards, so the stored origin is its right edge.
		boolean rightAligned = pos[0] > screenWidth / 2;
		if (ENABLED_BUFFER.isEmpty()) {
			if (editing) {
				int placeholderX = pos[0] - (rightAligned ? 80 : 0);
				handleDrag(HudElement.MODULE_LIST, placeholderX, pos[1], 80, 18, rightAligned ? 80 : 0, 0);
				RenderUtil.drawOutline(graphics, placeholderX, pos[1], 80.0f, 18.0f, 6.0f, 1.0f, -2008376065, false);
				ZenyaFont.draw(graphics, font, "[Module List]", placeholderX + 4, pos[1] + 5, -1996488705, false);
			}
			return;
		}
		ENABLED_BUFFER.sort(Comparator.comparingInt((Module module) -> ZenyaFont.width(font, module.getName())).reversed());
		int listWidth = ZenyaFont.width(font, ENABLED_BUFFER.get(0).getName()) + 12;
		int listHeight = ENABLED_BUFFER.size() * 14 + (ENABLED_BUFFER.size() - 1) * 2;
		if (editing) {
			int outlineX = pos[0] - (rightAligned ? listWidth : 0);
			handleDrag(HudElement.MODULE_LIST, outlineX, pos[1], listWidth, listHeight, rightAligned ? listWidth : 0, 0);
			RenderUtil.drawOutline(graphics, outlineX, pos[1], listWidth, listHeight, 6.0f, 1.0f, -2008376065, false);
		}
		int rowY = pos[1];
		int index = 0;
		for (Module module : ENABLED_BUFFER) {
			String name = module.getName();
			int rowWidth = ZenyaFont.width(font, name) + 12;
			int rowX = rightAligned ? pos[0] - rowWidth : pos[0];
			int rowColor = rainbow ? Themes.rainbowAt(index, 0.05f) : accent;
			if (ZenyaPlus.blurBackgroundEnabled()) {
				RenderUtil.drawBlur(graphics, rowX, rowY, rowWidth, 14.0f, 4.0f, 1.5f, false);
			}
			RenderUtil.drawRoundedRect(graphics, rowX, rowY, rowWidth, 14.0f, 4.0f, -821359084, false);
			if (rightAligned) {
				RenderUtil.drawRoundedRect(graphics, pos[0] - 2, rowY, 2.0f, 14.0f, 1.0f, 0.0f, 1.0f, 0.0f, false, rowColor);
				ZenyaFont.draw(graphics, font, name, rowX + 4, rowY + (14 - font.lineHeight) / 2 + 1, rowColor, false);
			} else {
				RenderUtil.drawRoundedRect(graphics, pos[0], rowY, 2.0f, 14.0f, 1.0f, 0.0f, 1.0f, 0.0f, false, rowColor);
				ZenyaFont.draw(graphics, font, name, rowX + 6, rowY + (14 - font.lineHeight) / 2 + 1, rowColor, false);
			}
			rowY += 16;
			++index;
		}
	}

	private static void drawNotifications(GuiGraphics graphics, Font font, boolean editing) {
		int[] pos = getElementPos(HudElement.NOTIFICATIONS);
		long now = System.currentTimeMillis();
		long lifetime = notificationLifetimeMs();
		NOTIFICATIONS.removeIf(entry -> now - entry.createdAt() > lifetime);
		int stackHeight = Math.max(36, NOTIFICATIONS.size() * 36 + Math.max(0, NOTIFICATIONS.size() - 1) * 5);
		if (editing) {
			handleDrag(HudElement.NOTIFICATIONS, 164, stackHeight);
			RenderUtil.drawOutline(graphics, pos[0], pos[1], 164.0f, stackHeight, 6.0f, 1.0f, -2008376065, false);
			if (NOTIFICATIONS.isEmpty()) {
				card(graphics, pos[0], pos[1], 164, 36);
				ZenyaFont.draw(graphics, font, "[Notifications]", pos[0] + 12, pos[1] + 15, -1996488705, false);
				return;
			}
		}
		if (NOTIFICATIONS.isEmpty()) {
			return;
		}
		boolean rainbow = Themes.isRainbow();
		int cardY = pos[1];
		int index = 0;
		for (NotificationEntry entry : NOTIFICATIONS) {
			long age = now - entry.createdAt();
			// Slide/fade in over 180ms, back out over the last 220ms of the lifetime.
			float fadeIn = clamp01((float) age / 180.0f);
			float fadeOut = clamp01((float) (lifetime - age) / 220.0f);
			float progress = easeOutCubic(Math.min(fadeIn, fadeOut));
			int cardX = pos[0] + Math.round((1.0f - progress) * 14.0f);
			int backgroundColor = multiplyAlpha(CARD_BG, progress);
			int accent = rainbow ? Themes.rainbowAt(index, 0.05f) : ZenyaPlus.getAccentARGB();
			int titleColor = multiplyAlpha(TEXT_PRIMARY, progress);
			int messageColor = multiplyAlpha(TEXT_SECONDARY, progress);
			int enabledColor = multiplyAlpha(accent, progress);
			RenderUtil.drawRoundedRect(graphics, cardX, cardY, 164.0f, 36.0f, CARD_RADIUS, backgroundColor, false);
			RenderUtil.drawRoundedRect(graphics, cardX + 8, cardY + 8, 20.0f, 20.0f, 6.0f,
					multiplyAlpha(0x1FFFFFFF, progress), false);
			graphics.renderItem(new ItemStack(entry.icon()), cardX + 10, cardY + 10);
			ZenyaFont.draw(graphics, font, entry.title(), cardX + 36, cardY + 7, titleColor, false);
			ZenyaFont.draw(graphics, font, entry.message(), cardX + 36, cardY + 20,
					entry.message().endsWith("enabled") ? enabledColor : messageColor, false);
			cardY += 41;
			++index;
		}
	}

	/** Per-module icon; falls back to a category icon for anything unlisted. */
	private static Item moduleIcon(Module module) {
		return switch (module.getName().toLowerCase(Locale.ROOT).replace(" ", "")) {
			case "freelook" -> Items.ENDER_EYE;
			case "freecam", "cameratweaks" -> Items.SPYGLASS;
			case "storageesp", "stashnotifier" -> Items.CHEST;
			case "blockesp", "lightesp" -> Items.GLOWSTONE_DUST;
			case "playeresp", "nametags" -> Items.PLAYER_HEAD;
			case "voidesp" -> Items.OBSIDIAN;
			case "fullbright" -> Items.TORCH;
			case "hud" -> Items.MAP;
			case "configmanager" -> Items.WRITABLE_BOOK;
			case "themes", "themechanger", "frost+", "zenya+" -> Items.NETHER_STAR;
			case "autototem", "hovertotem", "autoinvtotem" -> Items.TOTEM_OF_UNDYING;
			case "triggerbot", "aimassist" -> Items.CROSSBOW;
			case "autocrystal", "autohitcrystal", "crystaloptimizer" -> Items.END_CRYSTAL;
			case "anchormacro", "safeanchor", "doubleanchor" -> Items.RESPAWN_ANCHOR;
			case "elytraswap" -> Items.ELYTRA;
			case "shieldbreaker" -> Items.SHIELD;
			case "automace", "maceswap" -> Items.MACE;
			case "swingspeed" -> Items.DIAMOND_SWORD;
			case "automine" -> Items.DIAMOND_PICKAXE;
			case "fastbridge" -> Items.BRICKS;
			case "tridentfly" -> Items.TRIDENT;
			case "autofireworks" -> Items.FIREWORK_ROCKET;
			case "autotool" -> Items.IRON_PICKAXE;
			case "autolog" -> Items.OAK_DOOR;
			case "sprint" -> Items.LEATHER_BOOTS;
			case "weathernotifier" -> Items.WATER_BUCKET;
			case "spotifyhud" -> Items.MUSIC_DISC_13;
			case "spawnertags" -> Items.IRON_BARS;
			// ponytail: "baseBlocksdetection" has a capital B, so it never matches the lower-cased name.
			case "playerchunkfinder", "baseBlocksdetection", "chunkfinder", "chunkreload", "deltasensor",
					"grasmuster" -> Items.COMPASS;
			case "fakestats" -> Items.NAME_TAG;
			case "fakepay" -> Items.EMERALD;
			case "regionmap" -> Items.FILLED_MAP;
			case "autorelog" -> Items.CLOCK;
			case "antitrap" -> Items.IRON_BARS;
			case "spawnerfinder" -> Items.ECHO_SHARD;
			case "amethystesp" -> Items.AMETHYST_SHARD;
			default -> switch (module.getCategory()) {
				case COMBAT -> Items.DIAMOND_SWORD;
				case RENDER -> Items.SPYGLASS;
				case MISC -> Items.COMPASS;
				case DONUT -> Items.AMETHYST_SHARD;
				case SMPS -> Items.CHEST;
				case CLIENT -> Items.NETHER_STAR;
			};
		};
	}

	private static void card(GuiGraphics graphics, int x, int y, int width, int height) {
		card(graphics, x, y, width, height, ZenyaPlus.getAccentARGB());
	}

	// ponytail: the accent parameter is ignored - every card draws with CARD_BG.
	private static void card(GuiGraphics graphics, int x, int y, int width, int height, int accent) {
		float radius = Math.min(CARD_RADIUS, (float) height * 0.5f);
		if (ZenyaPlus.blurBackgroundEnabled()) {
			RenderUtil.drawBlur(graphics, x, y, width, height, radius, 3.0f, false);
		}
		RenderUtil.drawRoundedRect(graphics, x, y, width, height, radius, CARD_BG, false);
	}

	/** Radar of nearby players/mobs plus cardinal letters, all rotated against the player yaw. */
	private static void drawCompass(GuiGraphics graphics, Font font, boolean editing) {
		int[] pos = getElementPos(HudElement.COMPASS);
		int radius = INSTANCE.compassSize.getValue();
		int diameter = radius * 2;
		int centerX = pos[0] + radius;
		int centerY = pos[1] + radius;
		if (editing) {
			handleDrag(HudElement.COMPASS, diameter, diameter);
			RenderUtil.drawOutline(graphics, pos[0], pos[1], diameter, diameter, 6.0f, 1.0f, -2008376065, false);
		}
		boolean rainbow = Themes.isRainbow();
		int accent = rainbow ? Themes.rainbowAt(0, 0.05f) : ZenyaPlus.getAccentARGB();
		if (ZenyaPlus.blurBackgroundEnabled()) {
			RenderUtil.drawBlur(graphics, pos[0], pos[1], diameter, diameter, radius, 3.0f, false);
		}
		RenderUtil.drawRoundedRect(graphics, pos[0], pos[1], diameter, diameter, radius, CARD_BG, false);
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			return;
		}
		float yaw = client.player.getYRot();
		if ((INSTANCE.compassPlayers.getValue() || INSTANCE.compassMobs.getValue()) && client.level != null) {
			for (Entity entity : client.level.entitiesForRendering()) {
				if (entity == client.player) {
					continue;
				}
				boolean isPlayer = entity instanceof Player;
				boolean isMob = entity instanceof Mob;
				if ((!isPlayer || !INSTANCE.compassPlayers.getValue())
						&& (!isMob || !INSTANCE.compassMobs.getValue())) {
					continue;
				}
				double deltaX = entity.getX() - client.player.getX();
				double deltaZ = entity.getZ() - client.player.getZ();
				double distance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
				if (distance > 80.0) {
					continue;
				}
				double bearing = Math.toDegrees(Math.atan2(deltaZ, deltaX)) - 90.0;
				double radians = Math.toRadians(bearing - (double) yaw);
				double dotRadius = distance / 80.0 * (double) (radius - 6);
				float dotX = (float) ((double) centerX + Math.sin(radians) * dotRadius);
				float dotY = (float) ((double) centerY - Math.cos(radians) * dotRadius);
				int dotColor = isPlayer ? -48060 : -12255420;
				RenderUtil.drawRoundedRect(graphics, dotX - 2.0f, dotY - 2.0f, 4.0f, 4.0f, 2.0f, dotColor, false);
			}
		}
		float[] cardinalAngles = { 0.0f, 90.0f, 180.0f, -90.0f };
		String[] cardinalLabels = { "S", "W", "N", "E" };
		for (int i = 0; i < 4; ++i) {
			double radians = Math.toRadians(cardinalAngles[i] - yaw);
			int ringRadius = radius - 8;
			String label = cardinalLabels[i];
			int labelWidth = ZenyaFont.width(font, label);
			// North keeps the accent colour so the player can find it at a glance.
			int labelColor = i == 2 ? accent : TEXT_PRIMARY;
			if (rainbow && i != 2) {
				labelColor = Themes.rainbowAt(i + 1, 0.05f);
			}
			float labelX = (float) ((double) centerX + Math.sin(radians) * (double) ringRadius) - (float) labelWidth / 2.0f;
			float labelY = (float) ((double) centerY - Math.cos(radians) * (double) ringRadius) - font.lineHeight / 2.0f;
			ZenyaFont.draw(graphics, font, label, (int) labelX, (int) labelY, labelColor, false);
		}
		RenderUtil.drawRoundedRect(graphics, (float) centerX - 2.0f, (float) centerY - 2.0f, 4.0f, 4.0f, 2.0f, accent, false);
	}

	private static void drawTotemCounter(GuiGraphics graphics, Font font, boolean editing) {
		int[] pos = getElementPos(HudElement.TOTEM_COUNTER);
		Minecraft client = Minecraft.getInstance();
		int totems = 0;
		if (client.player != null) {
			for (int slot = 0; slot < client.player.getInventory().getContainerSize(); ++slot) {
				if (client.player.getInventory().getItem(slot).getItem() != Items.TOTEM_OF_UNDYING) {
					continue;
				}
				totems += client.player.getInventory().getItem(slot).getCount();
			}
			if (client.player.getOffhandItem().getItem() == Items.TOTEM_OF_UNDYING) {
				totems += client.player.getOffhandItem().getCount();
			}
		}
		String text = "Totems: " + totems;
		int width = 32 + ZenyaFont.width(font, text) + 10;
		if (editing) {
			handleDrag(HudElement.TOTEM_COUNTER, width, 24);
			RenderUtil.drawOutline(graphics, pos[0], pos[1], width, 24.0f, 6.0f, 1.0f, -2008376065, false);
		}
		boolean rainbow = Themes.isRainbow();
		int accent = rainbow ? Themes.rainbowAt(0, 0.05f) : ZenyaPlus.getAccentARGB();
		card(graphics, pos[0], pos[1], width, 24, accent);
		graphics.renderItem(new ItemStack(Items.TOTEM_OF_UNDYING), pos[0] + 10, pos[1] + 4);
		ZenyaFont.draw(graphics, font, text, pos[0] + 32, pos[1] + (24 - font.lineHeight) / 2 + 1,
				rainbow ? accent : TEXT_PRIMARY, false);
	}

	/** WASD + space block, with LMB/RMB CPS counters underneath. 24px keys, 4px gaps. */
	private static void drawKeystrokes(GuiGraphics graphics, Font font, boolean editing) {
		int[] pos = getElementPos(HudElement.KEYSTROKES);
		Minecraft client = Minecraft.getInstance();
		if (editing) {
			handleDrag(HudElement.KEYSTROKES, 80, 100);
			RenderUtil.drawOutline(graphics, pos[0], pos[1], 80.0f, 100.0f, 6.0f, 1.0f, -2008376065, false);
		}
		int accent = Themes.isRainbow() ? Themes.rainbowAt(0, 0.05f) : ZenyaPlus.getAccentARGB();
		boolean forward = client.options.keyUp.isDown();
		boolean left = client.options.keyLeft.isDown();
		boolean back = client.options.keyDown.isDown();
		boolean right = client.options.keyRight.isDown();
		boolean jump = client.options.keyJump.isDown();
		boolean attack = client.options.keyAttack.isDown();
		boolean use = client.options.keyUse.isDown();
		int top = pos[1];
		drawKey(graphics, font, "W", pos[0] + 28, top, 24, 24, forward, accent);
		drawKey(graphics, font, "A", pos[0], top + 28, 24, 24, left, accent);
		drawKey(graphics, font, "S", pos[0] + 28, top + 28, 24, 24, back, accent);
		drawKey(graphics, font, "D", pos[0] + 56, top + 28, 24, 24, right, accent);
		int spaceY = top + 56;
		drawKey(graphics, font, "SPACE", pos[0], spaceY, 80, 24, jump, accent);
		int counterY = spaceY + 28;
		drawClickCounter(graphics, font, "LMB: " + getLmbCPS(), pos[0], counterY, 38, 16, attack, accent);
		drawClickCounter(graphics, font, "RMB: " + getRmbCPS(), pos[0] + 42, counterY, 38, 16, use, accent);
	}

	private static void drawKey(GuiGraphics graphics, Font font, String key, int x, int y,
			int width, int height, boolean pressed, int accent) {
		RenderUtil.drawRoundedRect(graphics, x, y, width, height, 6.0f, pressed ? accent : CARD_BG, false);
		int keyWidth = ZenyaFont.width(font, key);
		ZenyaFont.draw(graphics, font, key, x + (width - keyWidth) / 2,
				y + (height - font.lineHeight) / 2 + 1, TEXT_PRIMARY, false);
	}

	private static void drawClickCounter(GuiGraphics graphics, Font font, String text, int x, int y,
			int width, int height, boolean pressed, int accent) {
		drawKey(graphics, font, text, x, y, width, height, pressed, accent);
	}

	/** Rising-edge counted clicks in the last second, capped at 20 so a macro cannot stretch the pill. */
	private static int getLmbCPS() {
		Minecraft client = Minecraft.getInstance();
		boolean pressed = client.options.keyAttack.isDown();
		long now = System.currentTimeMillis();
		if (pressed && !lmbWasPressed) {
			lmbClickTimes.add(now);
		}
		lmbWasPressed = pressed;
		lmbClickTimes.removeIf(time -> now - time > 1000L);
		return Math.min(lmbClickTimes.size(), 20);
	}

	private static int getRmbCPS() {
		Minecraft client = Minecraft.getInstance();
		boolean pressed = client.options.keyUse.isDown();
		long now = System.currentTimeMillis();
		if (pressed && !rmbWasPressed) {
			rmbClickTimes.add(now);
		}
		rmbWasPressed = pressed;
		rmbClickTimes.removeIf(time -> now - time > 1000L);
		return Math.min(rmbClickTimes.size(), 20);
	}

	private static void drawArmorHud(GuiGraphics graphics, Font font, boolean editing) {
		Minecraft client = Minecraft.getInstance();
		if (client == null || client.player == null) {
			return;
		}
		int[] pos = getElementPos(HudElement.ARMOR_HUD);
		ItemStack helmet = client.player.getItemBySlot(EquipmentSlot.HEAD);
		ItemStack chestplate = client.player.getItemBySlot(EquipmentSlot.CHEST);
		ItemStack leggings = client.player.getItemBySlot(EquipmentSlot.LEGS);
		ItemStack boots = client.player.getItemBySlot(EquipmentSlot.FEET);
		boolean anyArmor = !helmet.isEmpty() || !chestplate.isEmpty() || !leggings.isEmpty() || !boots.isEmpty();
		if (editing) {
			handleDrag(HudElement.ARMOR_HUD, 92, 37);
			RenderUtil.drawOutline(graphics, pos[0], pos[1], 92.0f, 37.0f, 6.0f, 1.0f, -2008376065, false);
			if (!anyArmor) {
				card(graphics, pos[0], pos[1], 92, 37);
				ZenyaFont.draw(graphics, font, "[Armor HUD]", pos[0] + 18, pos[1] + 15, -1996488705, false);
				return;
			}
		}
		if (!anyArmor) {
			return;
		}
		int accent = Themes.isRainbow() ? Themes.rainbowAt(0, 0.05f) : ZenyaPlus.getAccentARGB();
		card(graphics, pos[0], pos[1], 92, 37, accent);
		// Boots first so the row reads bottom-up, matching the vanilla armour bar.
		ItemStack[] pieces = { boots, leggings, chestplate, helmet };
		for (int i = 0; i < pieces.length; ++i) {
			ItemStack piece = pieces[i];
			int pieceX = pos[0] + 8 + i * 20;
			int pieceY = pos[1] + 8;
			if (piece.isEmpty()) {
				continue;
			}
			graphics.renderItem(piece, pieceX, pieceY);
			if (!piece.isDamageableItem()) {
				continue;
			}
			int maxDamage = piece.getMaxDamage();
			float remaining = (float) (maxDamage - piece.getDamageValue()) / (float) maxDamage;
			int barY = pieceY + 18;
			int barWidth = Math.round(16.0f * remaining);
			RenderUtil.drawRoundedRect(graphics, pieceX, barY, 16.0f, 3.0f, 1.5f, -13421773, false);
			if (barWidth <= 0) {
				continue;
			}
			RenderUtil.drawRoundedRect(graphics, pieceX, barY, barWidth, 3.0f, 1.5f, getDurabilityColor(remaining), false);
		}
	}

	/** Green at full durability, fading through yellow to red as the item wears out. */
	private static int getDurabilityColor(float remaining) {
		if (remaining > 0.5f) {
			float t = (remaining - 0.5f) * 2.0f;
			int red = (int) (255.0f * (1.0f - t));
			return 0xFF000000 | red << 16 | 0xFF00;
		}
		float t = remaining * 2.0f;
		int green = (int) (255.0f * t);
		return 0xFFFF0000 | green << 8;
	}

	/** Scales an ARGB colour's alpha by {@code factor}, leaving RGB untouched. */
	private static int multiplyAlpha(int argb, float factor) {
		int alpha = Math.round((float) (argb >>> 24 & 0xFF) * clamp01(factor));
		return alpha << 24 | argb & 0xFFFFFF;
	}

	private static float clamp01(float value) {
		return Math.max(0.0f, Math.min(1.0f, value));
	}

	private static float easeOutCubic(float value) {
		float t = clamp01(value);
		float inverse = 1.0f - t;
		return 1.0f - inverse * inverse * inverse;
	}

	/** Lists which of the hard-coded {@link #STAFF_NAMES} are currently in the tab list. */
	private static void drawStaffList(GuiGraphics graphics, Font font, boolean editing) {
		int[] pos = getElementPos(HudElement.STAFF_LIST);
		Minecraft client = Minecraft.getInstance();
		onlineStaffBuffer.clear();
		if (client.getConnection() != null) {
			Collection<PlayerInfo> online = client.getConnection().getOnlinePlayers();
			for (String staff : STAFF_NAMES) {
				for (PlayerInfo info : online) {
					if (info.getProfile().name().equalsIgnoreCase(staff)) {
						onlineStaffBuffer.add(staff);
						break;
					}
				}
			}
		}
		int widestName = 0;
		for (String staff : onlineStaffBuffer) {
			widestName = Math.max(widestName, ZenyaFont.width(font, staff));
		}
		int width = Math.max(130, 42 + widestName);
		int rowsHeight = onlineStaffBuffer.isEmpty() ? 0 : onlineStaffBuffer.size() * 18 - 2;
		int height = onlineStaffBuffer.isEmpty() ? 48 : 36 + rowsHeight;
		if (editing) {
			handleDrag(HudElement.STAFF_LIST, width, height);
			RenderUtil.drawOutline(graphics, pos[0], pos[1], width, height, 6.0f, 1.0f, -2008376065, false);
		}
		boolean rainbow = Themes.isRainbow();
		int accent = rainbow ? Themes.rainbowAt(0, 0.05f) : ZenyaPlus.getAccentARGB();
		if (ZenyaPlus.blurBackgroundEnabled()) {
			RenderUtil.drawBlur(graphics, pos[0], pos[1], width, height, CARD_RADIUS, 3.0f, false);
		}
		RenderUtil.drawRoundedRect(graphics, pos[0], pos[1], width, height, CARD_RADIUS, CARD_BG, false);
		ZenyaFont.draw(graphics, font, "Staff List", pos[0] + 8, pos[1] + 7, TEXT_PRIMARY, false);
		String count = String.valueOf(onlineStaffBuffer.size());
		int badgeWidth = ZenyaFont.width(font, count) + 8;
		int badgeX = pos[0] + width - badgeWidth - 6;
		int badgeY = pos[1] + 6;
		RenderUtil.drawRoundedRect(graphics, badgeX, badgeY, badgeWidth, 12.0f, 6.0f, accent, false);
		ZenyaFont.draw(graphics, font, count, badgeX + (badgeWidth - ZenyaFont.width(font, count)) / 2,
				badgeY + 2, TEXT_PRIMARY, false);
		if (onlineStaffBuffer.isEmpty()) {
			ZenyaFont.draw(graphics, font, "No staff online", pos[0] + 10, pos[1] + 30, -7829368, false);
			return;
		}
		int rowY = pos[1] + 28;
		int index = 0;
		for (String staff : onlineStaffBuffer) {
			int rowColor = index % 2 == 0 ? 0x18FFFFFF : 0xCFFFFFF;
			RenderUtil.drawRoundedRect(graphics, pos[0] + 4, rowY, width - 8, 16.0f, 4.0f, rowColor, false);
			int dotX = pos[0] + 10;
			int dotY = rowY + 5;
			int dotColor = rainbow ? Themes.rainbowAt(index, 0.05f) : -12656529;
			RenderUtil.drawRoundedRect(graphics, dotX, dotY, 6.0f, 6.0f, 3.0f, dotColor, false);
			int nameColor = rainbow ? Themes.rainbowAt(index, 0.05f) : TEXT_PRIMARY;
			ZenyaFont.draw(graphics, font, staff, dotX + 12, rowY + (16 - font.lineHeight) / 2 + 1, nameColor, false);
			rowY += 18;
			++index;
		}
	}

	public enum HudElement {
		WATERMARK("Frost+"),
		PING("Ping"),
		XYZ("XYZ"),
		TPS("TPS"),
		BPS("BPS"),
		MODULE_LIST("Module List"),
		NOTIFICATIONS("Notifications"),
		COMPASS("Compass"),
		TOTEM_COUNTER("Totem Counter"),
		KEYSTROKES("Keystrokes"),
		ARMOR_HUD("Armor HUD"),
		STAFF_LIST("Staff List");

		public final String label;

		HudElement(String label) {
			this.label = label;
		}
	}

	private record NotificationEntry(String title, String message, Item icon, long createdAt) {
	}
}
