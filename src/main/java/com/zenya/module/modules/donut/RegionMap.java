package com.zenya.module.modules.donut;

import com.zenya.module.Category;
import com.zenya.module.Module;
import com.zenya.module.modules.client.ZenyaPlus;
import com.zenya.setting.Setting;
import com.zenya.utils.renderer.RenderUtil;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

import org.joml.Matrix3x2fStack;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

/**
 * Draggable HUD overlay showing Donut's 9x9 grid of world regions and which server
 * each one lives on, plus a dot for where the player currently is.
 *
 * <p>The grid is fixed: the world spans {@code MAP_SIZE * REGION_SIZE} blocks centred
 * on 0, so a world coordinate maps to a cell with {@code (coord + MAP_OFFSET) / REGION_SIZE}.
 * Dragging is polled straight from GLFW rather than driven by screen events, because the
 * HUD renders while no screen is open — hence the {@link #lastMouseDown} edge detection.
 */
public class RegionMap extends Module {
	public static final int MAP_SIZE = 9;
	public static final int CELL_SIZE = 10;
	public static final int CELL_GAP = 1;
	public static final int PANEL_SIZE = 106;
	public static final double REGION_SIZE = 50000.0;
	public static final double MAP_OFFSET = 225000.0;

	public static final String[] REGION_TYPE_NAMES = {
			"EU Central",
			"EU West",
			"NA East",
			"NA West",
			"Asia",
			"Oceania"
	};

	public static final Color[] REGION_TYPE_COLORS = {
			new Color(159, 206, 99),
			new Color(0, 166, 99),
			new Color(79, 173, 234),
			new Color(47, 110, 186),
			new Color(245, 194, 66),
			new Color(252, 136, 3)
	};

	/** Row-major {regionId, regionType} for every cell of the {@code MAP_SIZE}x{@code MAP_SIZE} grid. */
	public static final int[][] REGION_LAYOUT = {
			{ 82, 5 }, { 100, 3 }, { 101, 3 }, { 102, 3 }, { 103, 2 }, { 104, 2 }, { 105, 2 }, { 106, 2 }, { 91, 2 },
			{ 83, 5 }, { 44, 3 }, { 75, 3 }, { 42, 3 }, { 41, 2 }, { 40, 2 }, { 39, 2 }, { 38, 2 }, { 92, 2 },
			{ 84, 5 }, { 45, 3 }, { 14, 3 }, { 13, 3 }, { 12, 2 }, { 11, 2 }, { 10, 2 }, { 37, 2 }, { 93, 2 },
			{ 85, 5 }, { 46, 5 }, { 74, 5 }, { 3, 3 }, { 2, 2 }, { 1, 2 }, { 25, 2 }, { 36, 2 }, { 94, 2 },
			{ 86, 4 }, { 47, 4 }, { 72, 4 }, { 71, 4 }, { 5, 2 }, { 4, 2 }, { 24, 2 }, { 35, 2 }, { 95, 2 },
			{ 87, 4 }, { 51, 1 }, { 17, 1 }, { 9, 0 }, { 8, 0 }, { 7, 0 }, { 23, 0 }, { 34, 0 }, { 96, 2 },
			{ 88, 4 }, { 54, 1 }, { 18, 1 }, { 61, 0 }, { 62, 0 }, { 21, 0 }, { 22, 0 }, { 33, 0 }, { 97, 0 },
			{ 89, 0 }, { 26, 1 }, { 27, 0 }, { 28, 0 }, { 29, 0 }, { 30, 0 }, { 59, 0 }, { 32, 0 }, { 98, 0 },
			{ 90, 0 }, { 107, 1 }, { 108, 1 }, { 109, 1 }, { 110, 1 }, { 111, 1 }, { 112, 1 }, { 113, 1 }, { 99, 0 }
	};

	/** Cell index (row * MAP_SIZE + column) to region, built once from {@link #REGION_LAYOUT}. */
	public static final Map<Integer, RegionInfo> REGION_MAP = createRegionMap();

	/** Set by the constructor so the HUD hook can reach the live module instance. */
	public static RegionMap INSTANCE;

	public Setting<Integer> backgroundOpacity = new Setting<>("Background Opacity", 90, 0, 255);
	public int panelX = 12;
	public int panelY = 148;
	public boolean dragging;
	public int dragOffsetX;
	public int dragOffsetY;
	public boolean lastMouseDown;

	public RegionMap() {
		super("Region Map", Category.DONUT);
		INSTANCE = this;
		setDescription("Renders a draggable Donut world region map.");
		addSetting(backgroundOpacity);
	}

	@Override
	public void onDisable() {
		dragging = false;
		lastMouseDown = false;
	}

	public static void renderHud(GuiGraphics graphics) {
		RegionMap instance = INSTANCE;
		if (instance == null || !instance.isEnabled()) {
			return;
		}
		instance.render(graphics);
	}

	public void render(GuiGraphics graphics) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) {
			return;
		}
		int panelWidth = getPanelWidth();
		int panelHeight = getPanelHeight();
		handleDrag(mc, panelWidth, panelHeight);
		panelX = Mth.clamp(panelX, 0, Math.max(0, mc.getWindow().getGuiScaledWidth() - panelWidth));
		panelY = Mth.clamp(panelY, 0, Math.max(0, mc.getWindow().getGuiScaledHeight() - panelHeight));
		int left = panelX;
		int top = panelY;
		int gridLeft = left + 4;
		int gridTop = top + 4;
		int legendLeft = left + PANEL_SIZE + 4;
		int accent = ZenyaPlus.getAccentARGB();
		int background = backgroundOpacity.getValue() << 24;
		RenderUtil.drawRoundedRect(graphics, left, top, panelWidth, panelHeight, 8.0f, background, false);
		RenderUtil.drawOutline(graphics, left, top, panelWidth, panelHeight, 8.0f, 1.5f, accent, false);
		for (int row = 0; row < MAP_SIZE; ++row) {
			for (int column = 0; column < MAP_SIZE; ++column) {
				RegionInfo region = REGION_MAP.get(row * MAP_SIZE + column);
				if (region == null) continue;
				int cellX = gridLeft + column * (CELL_SIZE + CELL_GAP);
				int cellY = gridTop + row * (CELL_SIZE + CELL_GAP);
				Color color = REGION_TYPE_COLORS[Mth.clamp(region.regionType(), 0, REGION_TYPE_COLORS.length - 1)];
				RenderUtil.drawRoundedRect(graphics, cellX, cellY, CELL_SIZE, CELL_SIZE, 2.0f,
						new Color(color.getRed(), color.getGreen(), color.getBlue(), 214).getRGB(), false);
				drawScaledText(graphics, String.valueOf(region.regionId()), cellX + 1.5f, cellY + 2.5f, 0.5f, -15724528);
			}
		}
		drawPlayerDot(graphics, gridLeft, gridTop);
		drawLegend(graphics, legendLeft, top + 4);
	}

	/** Colour key plus the region and block position the player is standing in. */
	public void drawLegend(GuiGraphics graphics, int x, int y) {
		Font font = Minecraft.getInstance().font;
		graphics.drawString(font, "Regions", x + 4, y, -1643275, false);
		y += 12;
		for (int type = 0; type < REGION_TYPE_NAMES.length; ++type) {
			RenderUtil.drawRoundedRect(graphics, x + 4, y + 2, 6.0f, 6.0f, 2.0f, REGION_TYPE_COLORS[type].getRGB(), false);
			drawScaledText(graphics, REGION_TYPE_NAMES[type], x + 14, y + 1, 0.65f, -1643275);
			y += 10;
		}
		Minecraft mc = Minecraft.getInstance();
		double playerX = mc.player.getX();
		double playerZ = mc.player.getZ();
		int regionId = getRegionIdAt(playerX, playerZ);
		String regionTypeName = getRegionTypeName(playerX, playerZ);
		y += 4;
		String regionIdText = String.valueOf(regionId >= 0 ? Integer.valueOf(regionId) : "?");
		drawScaledText(graphics, "R:" + regionIdText + " " + regionTypeName, x + 4, y, 0.65f, -1643275);
		// ponytail: the decompiler dropped the X value here (it emitted a '(int)' cast on the
		// region name); restored from the discarded 'cfr_ignored_0 = (int) Math.floor(playerX)'.
		int blockX = (int) Math.floor(playerX);
		int blockZ = (int) Math.floor(playerZ);
		drawScaledText(graphics, "X:" + blockX + " Z:" + blockZ, x + 4, y + 10, 0.65f, -1643275);
	}

	/** Draws the player marker at its fractional cell position, clamped to the last cell. */
	public void drawPlayerDot(GuiGraphics graphics, int gridLeft, int gridTop) {
		Minecraft mc = Minecraft.getInstance();
		double cellX = Mth.clamp((mc.player.getX() + MAP_OFFSET) / REGION_SIZE, 0.0, 8.99);
		double cellY = Mth.clamp((mc.player.getZ() + MAP_OFFSET) / REGION_SIZE, 0.0, 8.99);
		int dotX = (int) Math.round(gridLeft + cellX * (CELL_SIZE + CELL_GAP));
		int dotY = (int) Math.round(gridTop + cellY * (CELL_SIZE + CELL_GAP));
		RenderUtil.drawRoundedRect(graphics, dotX - 2, dotY - 2, 5.0f, 5.0f, 2.5f, -1, false);
		RenderUtil.drawRoundedRect(graphics, dotX - 1, dotY - 1, 3.0f, 3.0f, 1.5f, ZenyaPlus.getAccentARGB(), false);
	}

	/**
	 * Polls the raw mouse each frame: a press that starts inside the panel begins a drag,
	 * and the grab offset keeps the panel from jumping to the cursor.
	 */
	public void handleDrag(Minecraft mc, int panelWidth, int panelHeight) {
		long window = mc.getWindow().handle();
		double mouseX = mc.mouseHandler.xpos() * (double) mc.getWindow().getGuiScaledWidth() / (double) mc.getWindow().getScreenWidth();
		double mouseY = mc.mouseHandler.ypos() * (double) mc.getWindow().getGuiScaledHeight() / (double) mc.getWindow().getScreenHeight();
		boolean mouseDown = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
		boolean overPanel = mouseX >= panelX && mouseX <= panelX + panelWidth
				&& mouseY >= panelY && mouseY <= panelY + panelHeight;
		if (mouseDown && !lastMouseDown && overPanel) {
			dragging = true;
			dragOffsetX = (int) mouseX - panelX;
			dragOffsetY = (int) mouseY - panelY;
		}
		if (!mouseDown) {
			dragging = false;
		}
		if (dragging) {
			panelX = (int) mouseX - dragOffsetX;
			panelY = (int) mouseY - dragOffsetY;
		}
		lastMouseDown = mouseDown;
	}

	/** Font has no size, so small labels are drawn at the origin of a scaled matrix. */
	public void drawScaledText(GuiGraphics graphics, String text, float x, float y, float scale, int color) {
		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(x, y);
		pose.scale(scale, scale);
		graphics.drawString(Minecraft.getInstance().font, text, 0, 0, color, false);
		pose.popMatrix();
	}

	public int getPanelWidth() {
		return 188;
	}

	public int getPanelHeight() {
		return Math.max(PANEL_SIZE, 80);
	}

	/** World position to grid column/row; the result may be outside the grid. */
	public static int[] worldToGrid(double x, double z) {
		int[] grid = new int[2];
		grid[0] = (int) Math.floor((x + MAP_OFFSET) / REGION_SIZE);
		grid[1] = (int) Math.floor((z + MAP_OFFSET) / REGION_SIZE);
		return grid;
	}

	/** Region id at a world position, or -1 when it is off the map. */
	public static int getRegionIdAt(double x, double z) {
		int[] grid = worldToGrid(x, z);
		if (grid[0] < 0 || grid[0] >= MAP_SIZE || grid[1] < 0 || grid[1] >= MAP_SIZE) {
			return -1;
		}
		RegionInfo region = REGION_MAP.get(grid[1] * MAP_SIZE + grid[0]);
		return region == null ? -1 : region.regionId();
	}

	/** Server name at a world position, or "Unknown" when it is off the map. */
	public static String getRegionTypeName(double x, double z) {
		int[] grid = worldToGrid(x, z);
		if (grid[0] < 0 || grid[0] >= MAP_SIZE || grid[1] < 0 || grid[1] >= MAP_SIZE) {
			return "Unknown";
		}
		RegionInfo region = REGION_MAP.get(grid[1] * MAP_SIZE + grid[0]);
		if (region == null) {
			return "Unknown";
		}
		return REGION_TYPE_NAMES[Mth.clamp(region.regionType(), 0, REGION_TYPE_NAMES.length - 1)];
	}

	public static Map<Integer, RegionInfo> createRegionMap() {
		HashMap<Integer, RegionInfo> regions = new HashMap<>();
		for (int cell = 0; cell < REGION_LAYOUT.length; ++cell) {
			regions.put(cell, new RegionInfo(REGION_LAYOUT[cell][0], REGION_LAYOUT[cell][1]));
		}
		return regions;
	}

	/** One grid cell: the in-game region number and its index into {@link #REGION_TYPE_NAMES}. */
	public record RegionInfo(int regionId, int regionType) {}
}
