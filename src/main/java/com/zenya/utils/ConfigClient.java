package com.zenya.utils;

import java.util.ArrayList;
import java.util.List;

/**
 * Stub for the remote config store the share codes were meant to replace.
 *
 * <p>Nothing is wired up in this build: every call fails or comes back empty, so the
 * config screen falls back to {@link ConfigStore} on disk. Kept so the callers and the
 * {@link Entry} shape stay in place for whenever a backend exists.
 */
public class ConfigClient {

	public static boolean upload(String name, String contents) {
		return false;
	}

	public static List<Entry> list() {
		return new ArrayList<>();
	}

	public static String download(String name) {
		return null;
	}

	public static boolean delete(String name) {
		return false;
	}

	/** One config as the server would describe it: name plus size and mtime in millis. */
	public record Entry(String name, long size, long updatedAt) {}
}
