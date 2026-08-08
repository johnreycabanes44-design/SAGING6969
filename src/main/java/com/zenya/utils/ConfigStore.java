package com.zenya.utils;

import net.minecraft.client.Minecraft;

import com.zenya.module.Module;
import com.zenya.module.ModuleManager;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Named config snapshots kept next to the game directory.
 *
 * <p>ModuleManager only ever reads and writes one live file ({@code zenya_config.txt});
 * a "config" here is just a copy of that file parked in {@code zenya_configs/}, so
 * saving and loading is a file copy plus a reload.
 *
 * <p>Loading has to diff the enabled set itself: {@code loadConfig} restores the flags
 * but never fires onEnable/onDisable, so every module whose state actually flipped is
 * toggled by hand afterwards.
 */
public class ConfigStore {
	public static final String EXT = ".txt";
	public static final Pattern SAFE = Pattern.compile("[^A-Za-z0-9_\\- ]");

	public static Path configsDir() {
		Path dir = Minecraft.getInstance().gameDirectory.toPath().resolve("zenya_configs");
		try {
			Files.createDirectories(dir);
		} catch (IOException exception) {
			// Best effort: the callers below all cope with the directory not being there.
		}
		return dir;
	}

	public static Path liveConfig() {
		return Minecraft.getInstance().gameDirectory.toPath().resolve("zenya_config.txt");
	}

	/** Strips anything that would be awkward in a file name; null becomes the empty name. */
	public static String sanitize(String name) {
		if (name == null) {
			return "";
		}
		return SAFE.matcher(name.trim()).replaceAll("_");
	}

	public static List<String> list() {
		List<String> names = new ArrayList<>();
		Path dir = ConfigStore.configsDir();
		if (!Files.isDirectory(dir)) {
			return names;
		}
		try {
			// ponytail: Files.list returns a stream that is never closed here, leaking a
			// directory handle on every call -- kept as-is for a 1:1 port
			Files.list(dir).forEach(path -> {
				String fileName = path.getFileName().toString();
				if (fileName.endsWith(".txt")) {
					names.add(fileName.substring(0, fileName.length() - ".txt".length()));
				}
			});
		} catch (IOException exception) {
			// An unreadable directory just means no configs to offer.
		}
		names.sort(String.CASE_INSENSITIVE_ORDER);
		return names;
	}

	public static boolean saveAs(String name) {
		String safeName = ConfigStore.sanitize(name);
		if (safeName.isEmpty()) {
			return false;
		}
		ModuleManager.INSTANCE.saveConfig();
		Path live = ConfigStore.liveConfig();
		Path target = ConfigStore.configsDir().resolve(safeName + ".txt");
		try {
			Files.copy(live, target, StandardCopyOption.REPLACE_EXISTING);
			return true;
		} catch (IOException exception) {
			return false;
		}
	}

	public static boolean load(String name) {
		Path source = ConfigStore.configsDir().resolve(ConfigStore.sanitize(name) + ".txt");
		if (!Files.isRegularFile(source)) {
			return false;
		}
		return ConfigStore.applyFromPath(source);
	}

	public static boolean delete(String name) {
		try {
			return Files.deleteIfExists(ConfigStore.configsDir().resolve(ConfigStore.sanitize(name) + ".txt"));
		} catch (IOException exception) {
			return false;
		}
	}

	/** Gzipped diff payload as a shareable {@code XCFG2-} code, or null if it could not be built. */
	public static String generateShareCode() {
		try {
			ModuleManager.INSTANCE.saveConfig();
			byte[] payload = ModuleManager.INSTANCE.buildDiffPayload();
			ByteArrayOutputStream compressed = new ByteArrayOutputStream();
			String encoded;
			// ponytail: the jar encodes before the gzip trailer is written -- kept
			// as-is for a 1:1 port, but this looks like a bug in that build
			try (GZIPOutputStream gzip = new GZIPOutputStream(compressed)) {
				gzip.write(payload);
				encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(compressed.toByteArray());
			}
			return "XCFG2-" + encoded;
		} catch (IOException exception) {
			return null;
		}
	}

	/** Accepts both the gzipped {@code XCFG2-} codes and the older plain {@code XCFG-} ones. */
	public static boolean redeemShareCode(String code) {
		if (code == null) {
			return false;
		}
		String trimmed = code.trim();
		if (trimmed.isEmpty()) {
			return false;
		}
		try {
			byte[] payload;
			if (trimmed.startsWith("XCFG2-")) {
				byte[] compressed = Base64.getUrlDecoder().decode(trimmed.substring(6));
				ByteArrayOutputStream decompressed = new ByteArrayOutputStream();
				try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
					gzip.transferTo(decompressed);
					payload = decompressed.toByteArray();
				}
			} else if (trimmed.startsWith("XCFG-")) {
				payload = Base64.getUrlDecoder().decode(trimmed.substring(5));
			} else {
				return false;
			}
			return ConfigStore.saveAndApply(payload);
		} catch (IOException | IllegalArgumentException exception) {
			return false;
		}
	}

	/** Applies raw config bytes through a scratch file, which is removed again afterwards. */
	public static boolean saveAndApply(byte[] contents) {
		try {
			Path temp = ConfigStore.configsDir().resolve(".__shared_tmp.txt");
			Files.write(temp, contents);
			boolean applied = ConfigStore.applyFromPath(temp);
			try {
				Files.deleteIfExists(temp);
			} catch (IOException exception) {
				// A leftover scratch file is harmless; it is overwritten next time.
			}
			return applied;
		} catch (IOException exception) {
			return false;
		}
	}

	/** Makes the given file the live config and toggles every module whose state changed. */
	public static boolean applyFromPath(Path source) {
		Map<String, Boolean> before = ConfigStore.snapshotEnabled();
		try {
			Files.copy(source, ConfigStore.liveConfig(), StandardCopyOption.REPLACE_EXISTING);
			ModuleManager.INSTANCE.loadConfig();
		} catch (IOException exception) {
			return false;
		}
		Map<String, Boolean> after = ConfigStore.snapshotEnabled();
		for (Module module : ModuleManager.INSTANCE.getModules()) {
			boolean wasEnabled = before.getOrDefault(module.getName(), false);
			boolean isEnabled = after.getOrDefault(module.getName(), false);
			if (wasEnabled == isEnabled) {
				continue;
			}
			try {
				if (isEnabled) {
					module.onEnable();
				} else {
					module.onDisable();
				}
			} catch (Throwable throwable) {
				// One module failing to toggle must not abandon the rest of the load.
			}
		}
		ModuleManager.INSTANCE.saveConfig();
		return true;
	}

	public static Map<String, Boolean> snapshotEnabled() {
		HashMap<String, Boolean> enabled = new HashMap<>();
		for (Module module : ModuleManager.INSTANCE.getModules()) {
			enabled.put(module.getName(), module.isEnabled());
		}
		return enabled;
	}

	public static String readClipboard() {
		try {
			return Minecraft.getInstance().keyboardHandler.getClipboard();
		} catch (Throwable throwable) {
			// Clipboard access can fail on some platforms; an empty string is fine here.
			return "";
		}
	}

	public static void writeClipboard(String text) {
		try {
			Minecraft.getInstance().keyboardHandler.setClipboard(text == null ? "" : text);
		} catch (Throwable throwable) {
			// Same as above: nothing useful to do if the platform refuses the clipboard.
		}
	}

	public static byte[] utf8(String text) {
		return text.getBytes(StandardCharsets.UTF_8);
	}
}
