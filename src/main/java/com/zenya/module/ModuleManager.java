package com.zenya.module;

import com.zenya.ZenyaClient;
import com.zenya.module.modules.StubModule;
import com.zenya.module.modules.client.ConfigManager;
import com.zenya.module.modules.client.Hud;
import com.zenya.module.modules.client.ThemeChanger;
import com.zenya.module.modules.client.Themes;
import com.zenya.module.modules.client.ZenyaPlus;
import com.zenya.module.modules.combat.AimAssist;
import com.zenya.module.modules.combat.AnchorMacro;
import com.zenya.module.modules.combat.AutoCrystal;
import com.zenya.module.modules.combat.AutoHitCrystal;
import com.zenya.module.modules.combat.AutoInvTotem;
import com.zenya.module.modules.combat.AutoMace;
import com.zenya.module.modules.combat.AutoPotRefill;
import com.zenya.module.modules.combat.AutoWTap;
import com.zenya.module.modules.combat.CrystalOptimizer;
import com.zenya.module.modules.combat.DoubleAnchor;
import com.zenya.module.modules.combat.ElytraSwap;
import com.zenya.module.modules.combat.Hitboxes;
import com.zenya.module.modules.combat.HoverTotem;
import com.zenya.module.modules.combat.MaceSwap;
import com.zenya.module.modules.combat.SafeAnchor;
import com.zenya.module.modules.combat.ShieldBreaker;
import com.zenya.module.modules.combat.Triggerbot;
import com.zenya.module.modules.donut.ActivityTracer;
import com.zenya.module.modules.donut.ChunkFinder;
import com.zenya.module.modules.donut.FakeStats;
import com.zenya.module.modules.donut.RegionMap;
import com.zenya.module.modules.misc.AutoFireworks;
import com.zenya.module.modules.misc.AutoMine;
import com.zenya.module.modules.misc.FastBridge;
import com.zenya.module.modules.misc.Freelook;
import com.zenya.module.modules.misc.NameProtect;
import com.zenya.module.modules.misc.SpotifyHUD;
import com.zenya.module.modules.misc.SwingSpeed;
import com.zenya.module.modules.misc.TridentFly;
import com.zenya.module.modules.render.BlockESP;
import com.zenya.module.modules.render.CameraTweaks;
import com.zenya.module.modules.render.CustomCrosshair;
import com.zenya.module.modules.render.Freecam;
import com.zenya.module.modules.render.JumpCircle;
import com.zenya.module.modules.render.LightESP;
import com.zenya.module.modules.render.PistonESP;
import com.zenya.module.modules.render.PlayerESP;
import com.zenya.module.modules.render.StashNotifier;
import com.zenya.module.modules.render.StorageESP;
import com.zenya.module.modules.smps.PlayerChunkFinder;
import com.zenya.module.modules.smps.SeedChunkFinder;
import com.zenya.module.modules.smps.SpawnerTags;
import com.zenya.module.modules.smps.SusChunkFinder;
import com.zenya.setting.BlocksSetting;
import com.zenya.setting.MobsSetting;
import com.zenya.setting.Setting;
import com.zenya.setting.StorageBlocksSetting;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;

import java.awt.Color;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The module registry and the single dispatch point for ticks, rendering and packets.
 *
 * <p>Every dispatch is wrapped per module: a module that throws is disabled instead of
 * taking the whole client down with it.
 *
 * <p>The config file is a flat tab-separated text file whose names are Base64 encoded,
 * so a renamed module or setting simply fails to match rather than corrupting the load.
 * {@link #loadingConfig} suppresses the save-on-change hook while a load is in flight,
 * otherwise applying each stored value would rewrite the file being read.
 */
public class ModuleManager {
	public static final String CONFIG_HEADER = "ZENYA_CONFIG_V2";
	public static final String MODULE_PREFIX = "MODULE";
	public static final String SETTING_PREFIX = "SETTING";
	public static final String HUDPOS_PREFIX = "HUDPOS";
	public static final ModuleManager INSTANCE = new ModuleManager();

	public List<Module> modules = new ArrayList<>();
	public EnumMap<Category, List<Module>> modulesByCategory = new EnumMap<>(Category.class);
	public boolean initialized = false;
	public boolean loadingConfig = false;

	/**
	 * Instantiates an optional module by class name, falling back to an inert stub so a
	 * payload class that is missing from this build still occupies its slot in the GUI.
	 */
	public static Module loadPayloadModule(String className, String fallbackName, Category category) {
		try {
			Class<?> type = Class.forName(className);
			return (Module) type.getDeclaredConstructor().newInstance();
		} catch (Throwable error) {
			ZenyaClient.LOGGER.warn("Direct load failed for {}: {} - using stub", className, error.getMessage());
			return new StubModule(fallbackName, category);
		}
	}

	public void init() {
		if (this.initialized) {
			return;
		}
		this.modules.add(new ZenyaPlus());
		this.modules.add(new Hud());
		this.modules.add(Themes.getInstance());
		this.modules.add(new ThemeChanger());
		this.modules.add(new ConfigManager());
		this.modules.add(new PlayerESP());
		this.modules.add(new BlockESP());
		this.modules.add(new StorageESP());
		this.modules.add(new PistonESP());
		this.modules.add(new Freecam());
		this.modules.add(loadPayloadModule("com.zenya.module.modules.render.FullBright", "FullBright", Category.RENDER));
		this.modules.add(new StashNotifier());
		this.modules.add(new LightESP());
		this.modules.add(new CameraTweaks());
		this.modules.add(new HoverTotem());
		this.modules.add(new AutoInvTotem());
		this.modules.add(new AnchorMacro());
		this.modules.add(new AutoCrystal());
		this.modules.add(new Triggerbot());
		this.modules.add(new AimAssist());
		this.modules.add(new AutoMace());
		this.modules.add(new MaceSwap());
		this.modules.add(new SafeAnchor());
		this.modules.add(new AutoHitCrystal());
		this.modules.add(new DoubleAnchor());
		this.modules.add(new ShieldBreaker());
		this.modules.add(new AutoWTap());
		this.modules.add(new Hitboxes());
		this.modules.add(new ElytraSwap());
		this.modules.add(new CrystalOptimizer());
		this.modules.add(new AutoPotRefill());
		this.modules.add(new SwingSpeed());
		this.modules.add(new Freelook());
		this.modules.add(new AutoMine());
		this.modules.add(new FastBridge());
		this.modules.add(new TridentFly());
		this.modules.add(new AutoFireworks());
		this.modules.add(loadPayloadModule("com.zenya.module.modules.misc.FastPlace", "Fast Place", Category.MISC));
		this.modules.add(loadPayloadModule("com.zenya.module.modules.misc.AutoTool", "Auto Tool", Category.MISC));
		this.modules.add(loadPayloadModule("com.zenya.module.modules.misc.AutoLog", "AutoLog", Category.MISC));
		this.modules.add(loadPayloadModule("com.zenya.module.modules.misc.Sprint", "Sprint", Category.MISC));
		this.modules.add(loadPayloadModule("com.zenya.module.modules.misc.WeatherNotifier", "WeatherNotifier", Category.MISC));
		this.modules.add(new SpotifyHUD());
		this.modules.add(new NameProtect());
		this.modules.add(loadPayloadModule("com.zenya.module.modules.misc.SkinChanger", "SkinChanger", Category.MISC));
		this.modules.add(new SpawnerTags());
		this.modules.add(new PlayerChunkFinder());
		this.modules.add(new SeedChunkFinder());
		this.modules.add(new SusChunkFinder());
		this.modules.add(new FakeStats());
		this.modules.add(loadPayloadModule("com.zenya.module.modules.donut.FakePay", "FakePay", Category.DONUT));
		this.modules.add(new RegionMap());
		this.modules.add(new ChunkFinder());
		this.modules.add(new ActivityTracer());
		this.modules.add(new CustomCrosshair());
		this.modules.add(new JumpCircle());
		this.rebuildCategoryCache();
		this.initialized = true;
		this.loadConfig();
	}

	public void onSettingChanged() {
		if (!this.initialized || this.loadingConfig) {
			return;
		}
		this.saveConfig();
	}

	/** Same format as the config file, but only the modules that differ from their defaults. */
	public byte[] buildDiffPayload() {
		StringBuilder payload = new StringBuilder();
		payload.append(CONFIG_HEADER).append('\n');
		for (Module module : this.modules) {
			boolean enabled = module.isEnabled();
			boolean hasBind = module.getBind() != 0;
			int activationKey = module instanceof ActivatableModule activatable ? activatable.getActivationKey() : 0;
			boolean hasActivationKey = activationKey != 0;
			ArrayList<Setting> changed = new ArrayList<>();
			for (Setting setting : module.getSettings()) {
				if (Objects.equals(setting.getValue(), setting.getDefaultValue())) {
					continue;
				}
				changed.add(setting);
			}
			if (!enabled && !hasBind && !hasActivationKey && changed.isEmpty()) {
				continue;
			}
			payload.append(MODULE_PREFIX).append('\t')
					.append(this.encode(module.getName())).append('\t')
					.append(module.getBind()).append('\t')
					.append(activationKey).append('\t')
					.append(module.isEnabled()).append('\n');
			for (Setting setting : changed) {
				String serialized = this.serializeSettingValue(setting);
				if (serialized == null) {
					continue;
				}
				payload.append(SETTING_PREFIX).append('\t')
						.append(this.encode(module.getName())).append('\t')
						.append(this.encode(setting.getName())).append('\t')
						.append(this.encode(serialized)).append('\n');
			}
		}
		return payload.toString().getBytes(StandardCharsets.UTF_8);
	}

	public void saveConfig() {
		if (!this.initialized || this.loadingConfig) {
			return;
		}
		Path configPath = this.getConfigPath();
		try {
			Files.createDirectories(configPath.getParent());
			try (BufferedWriter writer = Files.newBufferedWriter(configPath, StandardCharsets.UTF_8)) {
				writer.write(CONFIG_HEADER);
				writer.newLine();
				for (Module module : this.modules) {
					writer.write(MODULE_PREFIX);
					writer.write('\t');
					writer.write(this.encode(module.getName()));
					writer.write('\t');
					writer.write(Integer.toString(module.getBind()));
					writer.write('\t');
					int activationKey = module instanceof ActivatableModule activatable ? activatable.getActivationKey() : 0;
					writer.write(Integer.toString(activationKey));
					writer.write('\t');
					writer.write(Boolean.toString(module.isEnabled()));
					writer.newLine();
					for (Setting setting : module.getSettings()) {
						String serialized = this.serializeSettingValue(setting);
						if (serialized == null) {
							continue;
						}
						writer.write(SETTING_PREFIX);
						writer.write('\t');
						writer.write(this.encode(module.getName()));
						writer.write('\t');
						writer.write(this.encode(setting.getName()));
						writer.write('\t');
						writer.write(this.encode(serialized));
						writer.newLine();
					}
				}
				for (Hud.HudElement element : Hud.HudElement.values()) {
					int[] position = Hud.getElementPos(element);
					writer.write(HUDPOS_PREFIX);
					writer.write('\t');
					writer.write(element.name());
					writer.write('\t');
					writer.write(Integer.toString(position[0]));
					writer.write('\t');
					writer.write(Integer.toString(position[1]));
					writer.newLine();
				}
			}
		} catch (IOException swallowed) {
			// Config saving is best-effort; a read-only game directory must not break the client.
		}
	}

	public void loadConfig() {
		Path configPath = this.getConfigPath();
		if (!Files.exists(configPath)) {
			return;
		}
		this.loadingConfig = true;
		try {
			List<String> lines = Files.readAllLines(configPath, StandardCharsets.UTF_8);
			for (String line : lines) {
				if (line == null || line.isBlank()) {
					continue;
				}
				if (CONFIG_HEADER.equals(line)) {
					continue;
				}
				if (line.startsWith(MODULE_PREFIX + "\t")) {
					this.loadModuleLine(line);
					continue;
				}
				if (line.startsWith(SETTING_PREFIX + "\t")) {
					this.loadSettingLine(line);
					continue;
				}
				if (line.startsWith(HUDPOS_PREFIX + "\t")) {
					this.loadHudPosLine(line);
					continue;
				}
				this.loadLegacyModuleLine(line);
			}
		} catch (IOException swallowed) {
			// An unreadable config just means defaults; nothing useful to recover here.
		} finally {
			this.loadingConfig = false;
		}
	}

	public void loadHudPosLine(String line) {
		try {
			String[] parts = line.split("\t");
			if (parts.length < 4) {
				return;
			}
			Hud.HudElement element = Hud.HudElement.valueOf(parts[1]);
			int x = Integer.parseInt(parts[2]);
			int y = Integer.parseInt(parts[3]);
			Hud.setElementPos(element, x, y);
		} catch (Exception swallowed) {
			// Unknown element name or malformed coordinates: keep the default position.
		}
	}

	public boolean isLoadingConfig() {
		return this.loadingConfig;
	}

	public List<Module> getModules() {
		return this.modules;
	}

	public List<Module> getModulesInCategory(Category category) {
		List<Module> inCategory = this.modulesByCategory.get(category);
		return inCategory == null ? List.of() : inCategory;
	}

	/** Resolves a module by display name, mapping the client's former names onto the current one. */
	public Module getModuleByName(String name) {
		String wanted = name;
		if (name.equalsIgnoreCase("Zenya+") || name.equalsIgnoreCase("Xenon")) {
			wanted = "Frost+";
		}
		for (Module module : this.modules) {
			if (module.getName().equalsIgnoreCase(wanted)) {
				return module;
			}
		}
		return null;
	}

	public void onTick() {
		List<Module> snapshot = this.modules;
		int count = snapshot.size();
		for (int index = 0; index < count; ++index) {
			Module module = snapshot.get(index);
			if (!module.isEnabled()) {
				continue;
			}
			try {
				module.onTick();
			} catch (Throwable error) {
				ZenyaClient.LOGGER.warn("Disabling module {} after tick failure", module.getName(), error);
				module.setEnabled(false);
			}
		}
	}

	public void onRender(PoseStack poseStack, float tickDelta) {
		List<Module> snapshot = this.modules;
		int count = snapshot.size();
		for (int index = 0; index < count; ++index) {
			Module module = snapshot.get(index);
			if (!module.isEnabled()) {
				continue;
			}
			try {
				module.onRender(poseStack, tickDelta);
			} catch (Throwable error) {
				ZenyaClient.LOGGER.warn("Disabling module {} after render failure", module.getName(), error);
				module.setEnabled(false);
			}
		}
	}

	public void onWorldChange() {
		List<Module> snapshot = this.modules;
		int count = snapshot.size();
		for (int index = 0; index < count; ++index) {
			Module module = snapshot.get(index);
			if (!module.isEnabled()) {
				continue;
			}
			try {
				module.onWorldChange();
			} catch (Throwable error) {
				ZenyaClient.LOGGER.warn("Disabling module {} after world-change failure", module.getName(), error);
				module.setEnabled(false);
			}
		}
	}

	public void onPacketReceive(Packet packet) {
		List<Module> snapshot = this.modules;
		int count = snapshot.size();
		for (int index = 0; index < count; ++index) {
			Module module = snapshot.get(index);
			if (!module.isEnabled()) {
				continue;
			}
			try {
				module.onPacketReceive(packet);
			} catch (Throwable error) {
				ZenyaClient.LOGGER.warn("Disabling module {} after packet receive failure", module.getName(), error);
				module.setEnabled(false);
			}
		}
	}

	/** @return true if any module wants the packet cancelled. Every module still gets to see it. */
	public boolean onPacketSend(Packet packet) {
		boolean cancelled = false;
		List<Module> snapshot = this.modules;
		int count = snapshot.size();
		for (int index = 0; index < count; ++index) {
			Module module = snapshot.get(index);
			if (!module.isEnabled()) {
				continue;
			}
			try {
				cancelled |= module.onPacketSend(packet);
			} catch (Exception swallowed) {
				// A throwing module must not stall the network thread; treat it as "no opinion".
			}
		}
		return cancelled;
	}

	public Path getConfigPath() {
		return Minecraft.getInstance().gameDirectory.toPath().resolve("zenya_config.txt");
	}

	public void loadModuleLine(String line) {
		String[] parts = line.split("\t", 5);
		if (parts.length < 5) {
			return;
		}
		Module module = this.getModuleByName(this.decode(parts[1]));
		if (module == null) {
			return;
		}
		try {
			module.applyBind(Integer.parseInt(parts[2]));
			if (module instanceof ActivatableModule activatable) {
				activatable.applyActivationKey(Integer.parseInt(parts[3]));
			}
			module.applyEnabled(Boolean.parseBoolean(parts[4]));
		} catch (Exception swallowed) {
			// Malformed line: keep whatever was already applied and move on.
		}
	}

	public void loadSettingLine(String line) {
		String[] parts = line.split("\t", 4);
		if (parts.length < 4) {
			return;
		}
		Module module = this.getModuleByName(this.decode(parts[1]));
		if (module == null) {
			return;
		}
		Setting setting = this.getSettingByName(module, this.decode(parts[2]));
		if (setting == null) {
			return;
		}
		this.applySettingValue(setting, this.decode(parts[3]));
	}

	/** Reads the pre-V2 format: plain {@code name:bind:activationKey:enabled}, no Base64. */
	public void loadLegacyModuleLine(String line) {
		String[] parts = line.split(":", 4);
		if (parts.length < 2) {
			return;
		}
		Module module = this.getModuleByName(parts[0]);
		if (module == null) {
			return;
		}
		try {
			if (parts.length >= 2) {
				module.applyBind(Integer.parseInt(parts[1]));
			}
			if (parts.length >= 3 && module instanceof ActivatableModule activatable) {
				activatable.applyActivationKey(Integer.parseInt(parts[2]));
			}
			if (parts.length >= 4) {
				module.applyEnabled(Boolean.parseBoolean(parts[3]));
			}
		} catch (Exception swallowed) {
			// Malformed legacy line: keep whatever was already applied and move on.
		}
	}

	public Setting getSettingByName(Module module, String name) {
		for (Setting setting : module.getSettings()) {
			if (!setting.matchesName(name)) {
				continue;
			}
			return setting;
		}
		return null;
	}

	/** @return the config representation of a setting, or null if its type cannot be stored. */
	public String serializeSettingValue(Setting setting) {
		Object value = setting.getValue();
		if (setting instanceof BlocksSetting blocks) {
			return this.serializeBlocks(blocks);
		}
		if (setting instanceof MobsSetting mobs) {
			return this.serializeMobs(mobs);
		}
		if (setting instanceof StorageBlocksSetting storageBlocks) {
			return this.serializeStorageBlocks(storageBlocks);
		}
		if (value instanceof Boolean flag) {
			return Boolean.toString(flag);
		}
		if (value instanceof Float number) {
			return Float.toString(number.floatValue());
		}
		if (value instanceof Integer number) {
			return Integer.toString(number);
		}
		if (value instanceof Double number) {
			return Double.toString(number);
		}
		if (value instanceof String text) {
			return text;
		}
		if (value instanceof Color color) {
			return color.getRed() + "," + color.getGreen() + "," + color.getBlue() + "," + color.getAlpha();
		}
		return null;
	}

	/** Applies a stored value, dispatching on the setting's current value to pick the type. */
	public void applySettingValue(Setting setting, String stored) {
		Object current = setting.getValue();
		try {
			if (setting instanceof BlocksSetting blocks) {
				this.deserializeBlocks(blocks, stored);
				return;
			}
			if (setting instanceof MobsSetting mobs) {
				mobs.setValue(this.deserializeMobs(stored));
				return;
			}
			if (setting instanceof StorageBlocksSetting storageBlocks) {
				this.deserializeStorageBlocks(storageBlocks, stored);
				return;
			}
			if (current instanceof Boolean) {
				setting.setValue(Boolean.parseBoolean(stored));
				return;
			}
			if (current instanceof Float) {
				setting.setValue(Float.valueOf(Float.parseFloat(stored)));
				return;
			}
			if (current instanceof Integer) {
				// Sliders were written as floats by older builds, so parse wide and round.
				setting.setValue(Math.round(Float.parseFloat(stored)));
				return;
			}
			if (current instanceof Double) {
				setting.setValue(Double.parseDouble(stored));
				return;
			}
			if (current instanceof String) {
				setting.setValue(stored);
				return;
			}
			if (current instanceof Color) {
				String[] channels = stored.split(",", 4);
				if (channels.length == 4) {
					setting.setValue(new Color(Integer.parseInt(channels[0]), Integer.parseInt(channels[1]),
							Integer.parseInt(channels[2]), Integer.parseInt(channels[3])));
				}
			}
		} catch (Exception swallowed) {
			// A value that no longer parses simply leaves the setting at its current value.
		}
	}

	/** Used by lazily loaded modules whose settings did not exist when the config was read. */
	public void applyDeferredSetting(Module module, String settingName, String stored) {
		Setting setting = this.getSettingByName(module, settingName);
		if (setting != null) {
			this.applySettingValue(setting, stored);
		}
	}

	/** Format: {@code id,id,...|id:r,g,b,a;id:r,g,b,a}. */
	public String serializeBlocks(BlocksSetting setting) {
		StringBuilder serialized = new StringBuilder();
		for (Block block : setting.getSelectedBlocks()) {
			Identifier id = BuiltInRegistries.BLOCK.getKey(block);
			if (id == null) {
				continue;
			}
			if (!serialized.isEmpty()) {
				serialized.append(',');
			}
			serialized.append(id);
		}
		serialized.append('|');
		boolean first = true;
		for (Map.Entry<Block, Color> entry : setting.getColors().entrySet()) {
			Identifier id = BuiltInRegistries.BLOCK.getKey(entry.getKey());
			if (id == null) {
				continue;
			}
			if (!first) {
				serialized.append(';');
			}
			Color color = entry.getValue();
			serialized.append(id).append(':')
					.append(color.getRed()).append(',')
					.append(color.getGreen()).append(',')
					.append(color.getBlue()).append(',')
					.append(color.getAlpha());
			first = false;
		}
		return serialized.toString();
	}

	public void deserializeBlocks(BlocksSetting setting, String stored) {
		if (stored == null || stored.isBlank()) {
			return;
		}
		int separator = stored.indexOf('|');
		String selectedPart = separator < 0 ? stored : stored.substring(0, separator);
		String colorPart = separator < 0 ? "" : stored.substring(separator + 1);
		if (!colorPart.isBlank()) {
			for (String colorEntry : colorPart.split(";")) {
				int colon = colorEntry.indexOf(':');
				if (colon <= 0) {
					continue;
				}
				String id = colorEntry.substring(0, colon).trim();
				String[] channels = colorEntry.substring(colon + 1).split(",");
				if (channels.length < 4) {
					continue;
				}
				try {
					Block block = BuiltInRegistries.BLOCK.getValue(Identifier.tryParse(id));
					if (block == null) {
						continue;
					}
					setting.setColor(block, new Color(Integer.parseInt(channels[0].trim()), Integer.parseInt(channels[1].trim()),
							Integer.parseInt(channels[2].trim()), Integer.parseInt(channels[3].trim())));
				} catch (Exception swallowed) {
					// Unknown block id or unparseable channel: skip this colour entry.
				}
			}
		}
		LinkedHashSet<Block> selected = new LinkedHashSet<>();
		if (!selectedPart.isBlank()) {
			for (String id : selectedPart.split(",")) {
				Block block = BuiltInRegistries.BLOCK.getValue(Identifier.tryParse(id.trim()));
				if (block == null) {
					continue;
				}
				selected.add(block);
			}
		}
		setting.setValue(selected);
	}

	public String serializeMobs(MobsSetting setting) {
		StringBuilder serialized = new StringBuilder();
		for (EntityType<?> type : setting.getSelectedMobs()) {
			Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
			if (id == null) {
				continue;
			}
			if (!serialized.isEmpty()) {
				serialized.append(',');
			}
			serialized.append(id);
		}
		return serialized.toString();
	}

	public Set<EntityType<?>> deserializeMobs(String stored) {
		LinkedHashSet<EntityType<?>> mobs = new LinkedHashSet<>();
		if (stored == null || stored.isBlank()) {
			return mobs;
		}
		for (String entry : stored.split(",")) {
			String id = entry.trim();
			if (id.isEmpty()) {
				continue;
			}
			Identifier parsed = Identifier.tryParse(id);
			if (parsed == null) {
				continue;
			}
			EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(parsed);
			if (type == null) {
				continue;
			}
			mobs.add(type);
		}
		return mobs;
	}

	/** Format: {@code id,id,...|id:r,g,b,a;id:r,g,b,a}, ids kept as text rather than blocks. */
	public String serializeStorageBlocks(StorageBlocksSetting setting) {
		StringBuilder serialized = new StringBuilder();
		boolean first = true;
		for (String id : setting.getSelected()) {
			if (!first) {
				serialized.append(',');
			}
			serialized.append(id);
			first = false;
		}
		serialized.append('|');
		first = true;
		for (Map.Entry<String, Color> entry : setting.getColorsSnapshot().entrySet()) {
			if (!first) {
				serialized.append(';');
			}
			Color color = entry.getValue();
			serialized.append(entry.getKey()).append(':')
					.append(color.getRed()).append(',')
					.append(color.getGreen()).append(',')
					.append(color.getBlue()).append(',')
					.append(color.getAlpha());
			first = false;
		}
		return serialized.toString();
	}

	public void deserializeStorageBlocks(StorageBlocksSetting setting, String stored) {
		if (stored == null) {
			return;
		}
		int separator = stored.indexOf('|');
		String selectedPart = separator < 0 ? stored : stored.substring(0, separator);
		String colorPart = separator < 0 ? "" : stored.substring(separator + 1);
		if (!colorPart.isBlank()) {
			LinkedHashMap<String, Color> colors = new LinkedHashMap<>();
			for (String colorEntry : colorPart.split(";")) {
				int colon = colorEntry.indexOf(':');
				if (colon <= 0) {
					continue;
				}
				String id = colorEntry.substring(0, colon).trim();
				String[] channels = colorEntry.substring(colon + 1).split(",", 4);
				if (channels.length < 4) {
					continue;
				}
				try {
					colors.put(id, new Color(Integer.parseInt(channels[0].trim()), Integer.parseInt(channels[1].trim()),
							Integer.parseInt(channels[2].trim()), Integer.parseInt(channels[3].trim())));
				} catch (NumberFormatException swallowed) {
					// Unparseable channel: skip this colour entry and keep the default.
				}
			}
			setting.restoreColors(colors);
		}
		LinkedHashSet<String> selected = new LinkedHashSet<>();
		if (!selectedPart.isBlank()) {
			for (String entry : selectedPart.split(",")) {
				String id = entry.trim();
				if (id.isEmpty() || setting.findEntry(id) == null) {
					continue;
				}
				selected.add(id);
			}
		}
		setting.setValue(selected);
	}

	/**
	 * Rebuilds the per-category lists as unmodifiable snapshots. Every category is seeded
	 * with an empty list first so the GUI can render a category that owns no modules.
	 */
	public void rebuildCategoryCache() {
		this.modulesByCategory.clear();
		for (Category category : Category.values()) {
			this.modulesByCategory.put(category, new ArrayList<>());
		}
		for (Module module : this.modules) {
			this.modulesByCategory.computeIfAbsent(module.getCategory(), key -> new ArrayList<>()).add(module);
		}
		for (Category category : Category.values()) {
			this.modulesByCategory.put(category, Collections.unmodifiableList(new ArrayList<>(this.modulesByCategory.get(category))));
		}
	}

	public String encode(String value) {
		return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
	}

	/** Falls back to the raw text so a hand-edited, un-encoded config still loads. */
	public String decode(String value) {
		if (value == null || value.isEmpty()) {
			return "";
		}
		try {
			return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
		} catch (IllegalArgumentException notBase64) {
			return value;
		}
	}
}
