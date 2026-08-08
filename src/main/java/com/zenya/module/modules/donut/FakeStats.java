package com.zenya.module.modules.donut;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.numbers.BlankFormat;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.ScoreAccess;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

import com.zenya.module.Category;
import com.zenya.module.Module;
import com.zenya.setting.Setting;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shows fake numbers in the server's sidebar scoreboard, client-side only.
 *
 * <p>The server's objective is never edited — it is remembered in
 * {@link #capturedSidebarObjective}, mirrored into a local objective named
 * {@value #OBJECTIVE_NAME}, and put back on disable. Each mirrored line that contains a
 * digit has its first number swapped for the next setting value, in declaration order
 * (money, shards, kills, deaths, playtime), so the sidebar layout is whatever the server
 * sent and only the numbers are ours.
 */
public class FakeStats extends Module {
	/** Name of the local mirror objective; lines belonging to it are never captured. */
	private static final String OBJECTIVE_NAME = "water_fake_stats";

	/** The vanilla sidebar only draws 15 lines, so more would never be seen. */
	private static final int MAX_LINES = 15;

	public static FakeStats instance;
	public Setting<String> money;
	public Setting<String> shards;
	public Setting<String> kills;
	public Setting<String> deaths;
	public Setting<String> playtime;
	public Objective capturedSidebarObjective;
	public String capturedObjectiveName;
	public Objective fakeObjective;
	public FakeValues cachedFakeValues;
	public Object trackedWorld;
	public String lastSignature;
	public boolean dirty;
	public long lastUpdateMillis;

	public FakeStats() {
		super("FakeStats", Category.DONUT);
		this.money = new Setting<>("Money", "67b");
		this.shards = new Setting<>("Shards", "frost");
		this.kills = new Setting<>("Kills", "69");
		this.deaths = new Setting<>("Deaths", "1");
		this.playtime = new Setting<>("Playtime", "67d");
		this.lastSignature = "";
		instance = this;
		this.setDescription("Replaces sidebar scoreboard stats locally.");
		this.addSetting(this.money);
		this.addSetting(this.shards);
		this.addSetting(this.kills);
		this.addSetting(this.deaths);
		this.addSetting(this.playtime);
	}

	public static FakeStats getInstance() {
		return instance;
	}

	@Override
	public void onEnable() {
		this.trackedWorld = mc.level;
		this.capturedSidebarObjective = null;
		this.capturedObjectiveName = null;
		this.fakeObjective = null;
		this.lastSignature = "";
		this.money.setValue("67b");
		this.shards.setValue("frost");
		this.kills.setValue("69");
		this.deaths.setValue("1");
		this.playtime.setValue("67d");
		this.cachedFakeValues = readSettings();
		this.dirty = true;
	}

	@Override
	public void onDisable() {
		this.trackedWorld = null;
		this.lastSignature = "";
		this.dirty = false;
		Objective captured = this.capturedSidebarObjective;
		Objective fake = this.fakeObjective;
		this.capturedSidebarObjective = null;
		this.capturedObjectiveName = null;
		this.fakeObjective = null;
		if (mc.level != null) {
			Scoreboard scoreboard = mc.level.getScoreboard();
			try {
				if (fake != null) {
					scoreboard.removeObjective(fake);
				}
				if (captured != null && scoreboard.getObjectives().contains(captured)) {
					scoreboard.setDisplayObjective(DisplaySlot.SIDEBAR, captured);
				}
			} catch (Exception e) {
				// Restoring is best effort: the world can be torn down while we disable.
			}
		}
	}

	@Override
	public void onTick() {
		if (mc.level == null) {
			this.capturedSidebarObjective = null;
			this.capturedObjectiveName = null;
			this.fakeObjective = null;
			this.trackedWorld = null;
			this.lastSignature = "";
			return;
		}
		if (mc.level != this.trackedWorld) {
			this.capturedSidebarObjective = null;
			this.capturedObjectiveName = null;
			this.fakeObjective = null;
			this.lastSignature = "";
			this.trackedWorld = mc.level;
			this.dirty = true;
		}
		this.ensureSidebarCapture();
		if (this.capturedSidebarObjective == null) {
			return;
		}
		Scoreboard scoreboard = mc.level.getScoreboard();
		if (!scoreboard.getObjectives().contains(this.capturedSidebarObjective)) {
			this.capturedSidebarObjective = null;
			this.ensureSidebarCapture();
			if (this.capturedSidebarObjective == null) {
				return;
			}
		}

		Objective source = this.capturedSidebarObjective;
		List<SidebarLine> lines = new ArrayList<>();
		List<PlayerScoreEntry> entries = new ArrayList<>(scoreboard.listPlayerScores(source));
		entries.removeIf(PlayerScoreEntry::isHidden);
		entries.sort(Comparator.comparingInt(PlayerScoreEntry::value).reversed());
		if (entries.size() > MAX_LINES) {
			entries = new ArrayList<>(entries.subList(0, MAX_LINES));
		}
		for (PlayerScoreEntry entry : entries) {
			int score = entry.value();
			MutableComponent text;
			if (entry.display() != null) {
				text = entry.display().copy();
			} else {
				MutableComponent owner = entry.ownerName() != null
						? entry.ownerName().copy()
						: Component.literal(entry.owner());
				text = PlayerTeam.formatNameForTeam(scoreboard.getPlayersTeam(entry.owner()), owner).copy();
			}
			lines.add(new SidebarLine(score, text));
		}

		MutableComponent title = source.getDisplayName() != null
				? source.getDisplayName().copy()
				: Component.literal("Donut SMP");

		FakeValues values = readSettings();
		if (this.cachedFakeValues == null || !this.cachedFakeValues.toKey().equals(values.toKey())) {
			this.cachedFakeValues = values;
			this.dirty = true;
		}

		// 10 and 58 are appended as numbers, not as '\n' and ':' — the signature is only ever
		// compared against itself, so the separators just have to be stable.
		StringBuilder signature = new StringBuilder(title.getString());
		for (SidebarLine line : lines) {
			signature.append(10).append(line.score()).append(58).append(line.text().getString());
		}
		signature.append(10).append(this.cachedFakeValues != null ? this.cachedFakeValues.toKey() : "");
		SidebarSnapshot snapshot = new SidebarSnapshot(title, lines, signature.toString());
		if (!snapshot.signature().equals(this.lastSignature) || this.fakeObjective == null) {
			this.dirty = true;
		}

		// Rebuilding drops and re-adds the objective, so it is rate limited to twice a second.
		if (this.dirty && this.cachedFakeValues != null && System.currentTimeMillis() - this.lastUpdateMillis >= 500L) {
			this.lastUpdateMillis = System.currentTimeMillis();
			Objective stale = scoreboard.getObjective(OBJECTIVE_NAME);
			if (stale != null) {
				scoreboard.removeObjective(stale);
			}
			this.fakeObjective = scoreboard.addObjective(OBJECTIVE_NAME, ObjectiveCriteria.DUMMY,
					snapshot.title().copy(), ObjectiveCriteria.RenderType.INTEGER, true, BlankFormat.INSTANCE);
			scoreboard.setDisplayObjective(DisplaySlot.SIDEBAR, this.fakeObjective);

			List<SidebarLine> snapshotLines = snapshot.lines();
			int nextValue = 0;
			String[] fakeValues = new String[5];
			fakeValues[0] = this.cachedFakeValues.money();
			fakeValues[1] = this.cachedFakeValues.shards();
			fakeValues[2] = this.cachedFakeValues.kills();
			fakeValues[3] = this.cachedFakeValues.deaths();
			fakeValues[4] = this.cachedFakeValues.playtime();
			for (int i = 0; i < snapshotLines.size(); ++i) {
				SidebarLine line = snapshotLines.get(i);
				ScoreHolder holder = ScoreHolder.forNameOnly("fake_stats_line_" + i);
				ScoreAccess access = scoreboard.getOrCreatePlayerScore(holder, this.fakeObjective);
				access.set(snapshotLines.size() - i);
				Component text;
				if (line.text().getString().trim().matches(".*\\d.*") && nextValue < fakeValues.length) {
					text = this.replaceFirstNumber(line.text(), fakeValues[nextValue], extractTextSegments(line.text()));
					++nextValue;
				} else {
					text = line.text().copy();
				}
				access.display(text);
				access.numberFormatOverride(BlankFormat.INSTANCE);
			}
			this.lastSignature = snapshot.signature();
			this.dirty = false;
		}
		if (this.fakeObjective != null && scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR) != this.fakeObjective) {
			scoreboard.setDisplayObjective(DisplaySlot.SIDEBAR, this.fakeObjective);
		}
	}

	/** Swaps the first "$ amount" in the tab-list footer for the fake balance, keeping its style. */
	public Component fakeFooterText(Component footer) {
		if (this.cachedFakeValues == null) {
			return footer;
		}
		String raw = footer.getString();
		Matcher matcher = Pattern.compile("(\\$\\s*)([0-9][0-9.,]*[KkMmBbTt]?)").matcher(raw);
		if (!matcher.find()) {
			return footer;
		}
		String tail = raw.substring(matcher.end(2));
		String amount = this.cachedFakeValues.money();
		String head = raw.substring(0, matcher.start(2));
		String replaced = head + amount + tail;
		List<TextSegment> segments = extractTextSegments(footer);
		Style style = segments.isEmpty() ? Style.EMPTY : segments.get(0).style();
		return Component.literal(replaced).setStyle(style);
	}

	/**
	 * Rebuilds {@code text} with everything up to its first number kept verbatim (segment
	 * styles and all) and the number itself replaced by {@code replacement}. Anything after
	 * the number is dropped; if there is no number the segments are returned unchanged.
	 */
	public Component replaceFirstNumber(Component text, String replacement, List<TextSegment> segments) {
		String raw = text.getString();
		int numberStart = -1;
		for (int i = 0; i < raw.length(); ++i) {
			char c = raw.charAt(i);
			if (!Character.isDigit(c) && (c != '-' || i + 1 >= raw.length() || !Character.isDigit(raw.charAt(i + 1)))) {
				continue;
			}
			numberStart = i;
			break;
		}
		if (numberStart < 0) {
			MutableComponent unchanged = Component.empty();
			for (TextSegment segment : segments) {
				unchanged.append(Component.literal(segment.value()).setStyle(segment.style()));
			}
			return unchanged;
		}

		MutableComponent out = Component.empty();
		int remaining = numberStart;
		for (TextSegment segment : segments) {
			if (remaining <= 0) {
				break;
			}
			String value = segment.value();
			int taken = Math.min(value.length(), remaining);
			out.append(Component.literal(value.substring(0, taken)).setStyle(segment.style()));
			remaining -= taken;
		}

		// The replacement inherits the style of the segment the number starts in.
		Style style = Style.EMPTY;
		int offset = numberStart;
		for (TextSegment segment : segments) {
			if (!segment.value().isEmpty()) {
				style = segment.style();
			}
			if (offset < segment.value().length()) {
				style = segment.style();
				break;
			}
			offset -= segment.value().length();
		}
		out.append(Component.literal(replacement).setStyle(style));
		return out;
	}

	/**
	 * Remembers the objective the server is showing, so we know what to mirror and what to
	 * restore. Falls back to the remembered name and then to any other objective, because a
	 * resource-pack reload can hand us back a different instance under the same name.
	 */
	public void ensureSidebarCapture() {
		if (mc.level == null) {
			return;
		}
		Scoreboard scoreboard = mc.level.getScoreboard();
		Objective displayed = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
		if (displayed != null && !OBJECTIVE_NAME.equals(displayed.getName())) {
			this.capturedSidebarObjective = displayed;
			this.capturedObjectiveName = displayed.getName();
			return;
		}
		if (this.capturedSidebarObjective != null && scoreboard.getObjectives().contains(this.capturedSidebarObjective)) {
			return;
		}
		if (this.capturedObjectiveName != null
				&& (displayed = scoreboard.getObjective(this.capturedObjectiveName)) != null
				&& !OBJECTIVE_NAME.equals(displayed.getName())) {
			this.capturedSidebarObjective = displayed;
			return;
		}
		for (Objective objective : scoreboard.getObjectives()) {
			if (OBJECTIVE_NAME.equals(objective.getName())) {
				continue;
			}
			this.capturedSidebarObjective = objective;
			this.capturedObjectiveName = objective.getName();
			return;
		}
	}

	/** Current setting values, with the blanks filled in so a line never renders empty. */
	private FakeValues readSettings() {
		return new FakeValues(
				defaultIfEmpty(this.money.getValue(), "0"),
				defaultIfEmpty(this.shards.getValue(), "0"),
				defaultIfEmpty(this.kills.getValue(), "0"),
				defaultIfEmpty(this.deaths.getValue(), "0"),
				defaultIfEmpty(this.playtime.getValue(), "0m"));
	}

	/** Flattens a component into its styled runs, so a number can be replaced without losing colours. */
	public static List<TextSegment> extractTextSegments(Component text) {
		List<TextSegment> segments = new ArrayList<>();
		text.visit((style, content) -> {
			if (!content.isEmpty()) {
				segments.add(new TextSegment(content, style));
			}
			return Optional.empty();
		}, Style.EMPTY);
		return segments;
	}

	public static String defaultIfEmpty(String value, String fallback) {
		if (value == null) {
			return fallback;
		}
		String trimmed = value.trim();
		if (trimmed.isEmpty()) {
			return fallback;
		}
		return trimmed;
	}

	public record FakeValues(String money, String shards, String kills, String deaths, String playtime) {
		/** Cheap equality key: the sidebar is only rebuilt when this changes. */
		public String toKey() {
			return money + "|" + shards + "|" + kills + "|" + deaths + "|" + playtime;
		}
	}

	public record SidebarLine(int score, Component text) {}

	public record SidebarSnapshot(Component title, List<SidebarLine> lines, String signature) {}

	public record TextSegment(String value, Style style) {}
}
