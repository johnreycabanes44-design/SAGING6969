package com.zenya.module.modules.misc;

import com.mojang.blaze3d.platform.NativeImage;
import com.zenya.gui.ClickGUI;
import com.zenya.module.Category;
import com.zenya.module.Module;
import com.zenya.module.ModuleManager;
import com.zenya.module.modules.client.ZenyaPlus;
import com.zenya.setting.Setting;
import com.zenya.utils.renderer.RenderUtil;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Now-playing card driven by the Windows SMTC (System Media Transport Controls),
 * so it works for any media app rather than just Spotify and needs no OAuth token.
 *
 * <p>Two temporary PowerShell scripts do the talking: one polls the current session
 * on a background scheduler, one sends prev/toggle/next. Because a poll costs a
 * process spawn, playback position is only sampled occasionally and interpolated in
 * between - {@link #anchorPosMs} is the last sampled position and {@link #anchorTimeMs}
 * the wall clock at which it was sampled; everything drawn is derived from that pair.
 */
public class SpotifyHUD extends Module {
	public static final int BASE_W = 228;
	public static final int BASE_H = 74;
	public static final int BASE_ART = 50;
	public static final int BASE_PAD_X = 8;
	public static final float SCROLL_PX_S = 28.0f;
	public static final int SCROLL_GAP = 18;
	public static final int ROW_H = 8;
	public static final int GAP_TITLE = 3;
	public static final int GAP_ART = 5;
	public static final int GAP_BAR = 4;
	public static final int GAP_TIME = 5;
	public static final int BAR_H_B = 3;
	public static final int MARGIN_R = 8;
	public static final int MARGIN_B = 8;
	public static final String POLL_SCRIPT = "[void][System.Reflection.Assembly]::LoadFile('C:\\Windows\\Microsoft.NET\\Framework64\\v4.0.30319\\System.Runtime.WindowsRuntime.dll')\r\n$null = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager,Windows.Media.Control,ContentType=WindowsRuntime]\r\n$g = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object { $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -like 'IAsyncOperation*' })[0]\r\nfunction Aw($op,$t){$m=$g.MakeGenericMethod($t);$task=$m.Invoke($null,@($op));$task.GetAwaiter().GetResult()}\r\n$asStreamForRead = [System.IO.WindowsRuntimeStreamExtensions].GetMethods() | Where-Object { $_.Name -eq 'AsStreamForRead' -and $_.GetParameters().Count -eq 1 } | Select-Object -First 1\r\ntry {\r\n  $mgr = Aw([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])\r\n  $s = $mgr.GetCurrentSession()\r\n  if ($s) {\r\n    $p = Aw($s.TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])\r\n    $tl = $s.GetTimelineProperties()\r\n    $pb = $s.GetPlaybackInfo()\r\n    if ($p.Title) {\r\n      if ($p.Thumbnail -and $asStreamForRead) {\r\n        try {\r\n          $stream = Aw($p.Thumbnail.OpenReadAsync()) ([Windows.Storage.Streams.IRandomAccessStreamWithContentType])\r\n          $netStream = $asStreamForRead.Invoke($null, @($stream))\r\n          $outPath = Join-Path $env:TEMP 'zenya_spotify_art.png'\r\n          $fs = [System.IO.File]::Create($outPath)\r\n          $netStream.CopyTo($fs)\r\n          $fs.Close()\r\n          $netStream.Close()\r\n        } catch {}\r\n      }\r\n      Write-Output ($p.Artist + '|||' + $p.Title + '|||' + [long]$tl.Position.TotalMilliseconds + '|||' + [long]$tl.EndTime.TotalMilliseconds + '|||' + ($pb.PlaybackStatus.ToString() -eq 'Playing'))\r\n    }\r\n  }\r\n} catch {}\r\n";
	public static final String CTRL_SCRIPT = "param([string]$action)\r\n[void][System.Reflection.Assembly]::LoadFile('C:\\Windows\\Microsoft.NET\\Framework64\\v4.0.30319\\System.Runtime.WindowsRuntime.dll')\r\n$null = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager,Windows.Media.Control,ContentType=WindowsRuntime]\r\n$g = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object { $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -like 'IAsyncOperation*' })[0]\r\nfunction Aw($op,$t){$m=$g.MakeGenericMethod($t);$task=$m.Invoke($null,@($op));$task.GetAwaiter().GetResult()}\r\ntry {\r\n  $mgr = Aw([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])\r\n  $s = $mgr.GetCurrentSession()\r\n  if ($s) {\r\n    switch ($action) {\r\n      'next'   { Aw($s.TrySkipNextAsync()) ([bool]) | Out-Null }\r\n      'prev'   { Aw($s.TrySkipPreviousAsync()) ([bool]) | Out-Null }\r\n      'toggle' { Aw($s.TryTogglePlayPauseAsync()) ([bool]) | Out-Null }\r\n    }\r\n  }\r\n} catch {}\r\n";
	public static final Identifier ART_ID = Identifier.fromNamespaceAndPath("zenya", "spotify_art");

	public static SpotifyHUD INSTANCE;

	public Setting<Double> scale = new Setting<>("Scale", 1.0, 0.6, 2.0);
	/** -1 means "not placed yet"; {@link #posX()} then anchors the card to the screen edge. */
	public Setting<Integer> positionX = new Setting<>("X", -1, -2000, 4000);
	public Setting<Integer> positionY = new Setting<>("Y", -1, -2000, 4000);
	public volatile String title = "";
	public volatile String artist = "";
	public volatile boolean isPlaying;
	public volatile long anchorPosMs;
	public volatile long anchorTimeMs;
	public volatile long durMs;
	public ScheduledExecutorService scheduler;
	public ExecutorService actionExec;
	public File pollScriptFile;
	public File ctrlScriptFile;
	public File artFile;
	public DynamicTexture artTex;
	public volatile long artLastModified;
	public volatile boolean hasArt;
	public float fade;
	public long lastNanos;
	public float scrollX;
	public long scrollNanos;
	public boolean dragging;
	public int dragOffX;
	public int dragOffY;
	/** Live drag position; {@link Integer#MIN_VALUE} means "not being dragged". */
	public int liveX = Integer.MIN_VALUE;
	public int liveY = Integer.MIN_VALUE;
	public volatile boolean hasPolledOnce;
	public AtomicBoolean polling = new AtomicBoolean(false);
	public AtomicInteger fastPollCount = new AtomicInteger(0);

	public SpotifyHUD() {
		super("SpotifyHUD", Category.MISC);
		setDescription("Shows currently playing media with real-time controls.");
		addSetting(scale);
		addSetting(positionX);
		addSetting(positionY);
		INSTANCE = this;
	}

	public static SpotifyHUD getInstance() {
		return INSTANCE;
	}

	public float sc() {
		return scale.getValue().floatValue();
	}

	/** Scales a base-size (design-time) pixel value by the user's scale setting. */
	public int s(int base) {
		return Math.round(base * sc());
	}

	public int cardW() {
		return Math.round(BASE_W * sc());
	}

	public int cardH() {
		return Math.round(BASE_H * sc());
	}

	public int posX() {
		if (mc == null || mc.getWindow() == null) {
			return 0;
		}
		if (liveX != Integer.MIN_VALUE) {
			return liveX;
		}
		if (positionX.getValue() >= 0) {
			return positionX.getValue();
		}
		return mc.getWindow().getGuiScaledWidth() - cardW() - MARGIN_R;
	}

	public int posY() {
		if (mc == null || mc.getWindow() == null) {
			return 0;
		}
		if (liveY != Integer.MIN_VALUE) {
			return liveY;
		}
		if (positionY.getValue() >= 0) {
			return positionY.getValue();
		}
		return mc.getWindow().getGuiScaledHeight() - cardH() - MARGIN_B;
	}

	@Override
	public void onEnable() {
		startBackground();
	}

	@Override
	public void onTick() {
		if (scheduler == null || scheduler.isShutdown()) {
			startBackground();
		}
	}

	/** Writes the helper scripts and starts the poll/control executors from scratch. */
	public void startBackground() {
		fade = 0.0f;
		scrollX = 0.0f;
		lastNanos = 0L;
		scrollNanos = 0L;
		hasPolledOnce = false;
		fastPollCount.set(0);
		writeScripts();
		artFile = new File(System.getenv("TEMP"), "zenya_spotify_art.png");

		Thread firstPoll = new Thread(this::poll, "zenya-spotify-init");
		firstPoll.setDaemon(true);
		firstPoll.start();

		scheduler = Executors.newSingleThreadScheduledExecutor(task -> {
			Thread thread = new Thread(task, "zenya-spotify");
			thread.setDaemon(true);
			return thread;
		});
		actionExec = Executors.newSingleThreadExecutor(task -> {
			Thread thread = new Thread(task, "zenya-spotify-ctrl");
			thread.setDaemon(true);
			return thread;
		});

		// Poll fast for the first ten attempts so the card fills in quickly after enable,
		// then park the counter at MAX_VALUE so this task retires and only the 2s one runs.
		scheduler.scheduleAtFixedRate(() -> {
			if (fastPollCount.getAndIncrement() >= 10) {
				return;
			}
			poll();
			if (!title.isEmpty()) {
				fastPollCount.set(Integer.MAX_VALUE);
			}
		}, 800L, 800L, TimeUnit.MILLISECONDS);
		scheduler.scheduleAtFixedRate(this::poll, 2L, 2L, TimeUnit.SECONDS);
	}

	@Override
	public void onDisable() {
		if (scheduler != null) {
			scheduler.shutdownNow();
		}
		if (actionExec != null) {
			actionExec.shutdownNow();
		}
		title = "";
		artist = "";
		isPlaying = false;
		anchorPosMs = 0L;
		anchorTimeMs = 0L;
		durMs = 0L;
		fade = 0.0f;
		scrollX = 0.0f;
		hasArt = false;
		if (artTex != null) {
			try {
				mc.getTextureManager().release(ART_ID);
			} catch (Exception releaseFailed) {
				// Releasing a texture the manager already dropped is harmless.
			}
			artTex = null;
		}
	}

	/** Drops both PowerShell helpers into temp files; nulls them out if that fails. */
	public void writeScripts() {
		try {
			pollScriptFile = File.createTempFile("zenya_smtc_poll_", ".ps1");
			pollScriptFile.deleteOnExit();
			try (FileWriter writer = new FileWriter(pollScriptFile, StandardCharsets.UTF_8)) {
				writer.write(POLL_SCRIPT);
			}

			ctrlScriptFile = File.createTempFile("zenya_smtc_ctrl_", ".ps1");
			ctrlScriptFile.deleteOnExit();
			try (FileWriter writer = new FileWriter(ctrlScriptFile, StandardCharsets.UTF_8)) {
				writer.write(CTRL_SCRIPT);
			}
		} catch (Exception writeFailed) {
			// No temp dir or no write permission: null files make poll()/runAction() no-ops.
			pollScriptFile = null;
			ctrlScriptFile = null;
		}
	}

	/**
	 * Runs the poll script once and folds the result into the anchor pair. Re-entrant
	 * calls are dropped rather than queued, so a slow PowerShell spawn cannot pile up.
	 */
	public void poll() {
		// ponytail: this repair runs before the `polling` CAS below, so the init thread and
		// the scheduler thread can both rewrite the scripts at once and race on the fields.
		if (pollScriptFile == null || !pollScriptFile.exists()) {
			writeScripts();
		}
		if (pollScriptFile == null) {
			hasPolledOnce = true;
			return;
		}
		if (!polling.compareAndSet(false, true)) {
			return;
		}
		try {
			long startMs = System.currentTimeMillis();
			String[] command = {
					"powershell.exe", "-NoProfile", "-NonInteractive",
					"-ExecutionPolicy", "Bypass", "-File", pollScriptFile.getAbsolutePath()
			};
			Process process = new ProcessBuilder(command).redirectErrorStream(true).start();

			String payload = null;
			try (BufferedReader reader = new BufferedReader(
					new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
				String line;
				while ((line = reader.readLine()) != null) {
					line = line.trim();
					if (!line.contains("|||")) {
						continue;
					}
					payload = line;
					break;
				}
			}
			process.waitFor(8L, TimeUnit.SECONDS);

			// Midpoint of the spawn window is the least-wrong timestamp for the sample.
			long sampledAtMs = (startMs + System.currentTimeMillis()) / 2L;
			if (payload != null) {
				String[] fields = payload.split("\\|\\|\\|", -1);
				if (fields.length >= 2 && !fields[1].trim().isEmpty()) {
					String newTitle = fields[1].trim();
					String newArtist = fields[0].trim();
					long reportedPosMs = fields.length > 2 ? parseLong(fields[2]) : 0L;
					long reportedDurMs = fields.length > 3 ? parseLong(fields[3]) : 0L;
					boolean playing = fields.length > 4 && fields[4].trim().equalsIgnoreCase("True");

					boolean trackChanged = !newTitle.equals(title);
					boolean playStateChanged = playing != isPlaying;
					long predictedPosMs = isPlaying
							? anchorPosMs + Math.max(0L, sampledAtMs - anchorTimeMs)
							: anchorPosMs;
					// More than four seconds off the prediction means the user seeked.
					boolean seeked = Math.abs(reportedPosMs - predictedPosMs) > 4000L;

					if (trackChanged) {
						scrollX = 0.0f;
					}
					title = newTitle;
					artist = newArtist;
					durMs = reportedDurMs;
					isPlaying = playing;
					if (trackChanged || playStateChanged || seeked || anchorTimeMs == 0L) {
						anchorPosMs = reportedPosMs;
						anchorTimeMs = sampledAtMs;
					}
				}
			}
		} catch (Exception pollFailed) {
			// One lost sample is not worth logging; the next tick just tries again.
		} finally {
			hasPolledOnce = true;
			polling.set(false);
		}
	}

	public static long parseLong(String text) {
		try {
			return Long.parseLong(text.trim());
		} catch (Exception notANumber) {
			// The script emits an empty field when a property is missing.
			return 0L;
		}
	}

	/** Fires "prev", "toggle" or "next" at the current SMTC session on the control thread. */
	public static void runAction(String action) {
		if (INSTANCE == null || INSTANCE.actionExec == null || INSTANCE.ctrlScriptFile == null) {
			return;
		}
		long nowMs = System.currentTimeMillis();
		// Flip the play state optimistically so the card reacts before the next poll lands.
		if ("toggle".equals(action)) {
			if (INSTANCE.isPlaying) {
				long playedMs = INSTANCE.anchorTimeMs > 0L ? Math.max(0L, nowMs - INSTANCE.anchorTimeMs) : 0L;
				INSTANCE.anchorPosMs += playedMs;
				INSTANCE.anchorTimeMs = nowMs;
				INSTANCE.isPlaying = false;
			} else {
				INSTANCE.anchorTimeMs = nowMs;
				INSTANCE.isPlaying = true;
			}
		}
		INSTANCE.actionExec.submit(() -> {
			try {
				String[] command = {
						"powershell.exe", "-NoProfile", "-NonInteractive",
						"-ExecutionPolicy", "Bypass", "-File", INSTANCE.ctrlScriptFile.getAbsolutePath(),
						"-action", action
				};
				Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
				process.waitFor(5L, TimeUnit.SECONDS);
				if (INSTANCE.scheduler != null && !INSTANCE.scheduler.isShutdown()) {
					INSTANCE.scheduler.submit(INSTANCE::poll);
				}
			} catch (Exception actionFailed) {
				// Control is best-effort; the optimistic state is corrected by the next poll.
			}
		});
	}

	/** Returns true when the click landed on one of the three transport buttons. */
	public static boolean handleClick(double mouseX, double mouseY) {
		if (INSTANCE == null || !INSTANCE.isEnabled()) {
			return false;
		}
		if (mc == null || mc.font == null) {
			return false;
		}
		int cardX = INSTANCE.posX();
		int cardY = INSTANCE.posY();
		int cardWidth = INSTANCE.cardW();
		int cardHeight = INSTANCE.cardH();
		Font font = mc.font;

		String prevGlyph = "\u25c0\u25c0";
		String playGlyph = INSTANCE.isPlaying ? "\u2759\u2759" : "\u25b6";
		String nextGlyph = "\u25b6\u25b6";

		int controlsY = INSTANCE.computeLayout(cardY, cardHeight)[4];
		int centerX = cardX + cardWidth / 2;
		int glyphGap = INSTANCE.s(14);
		int playX = centerX - font.width(playGlyph) / 2;
		int prevX = playX - glyphGap - font.width(prevGlyph);
		int nextX = playX + font.width(playGlyph) + glyphGap;
		int hitPadX = INSTANCE.s(5);
		int hitPadY = INSTANCE.s(3);

		int[][] hitBoxes = {
				{ prevX - hitPadX, controlsY - hitPadY, font.width(prevGlyph) + 2 * hitPadX, ROW_H + 2 * hitPadY },
				{ playX - hitPadX, controlsY - hitPadY, font.width(playGlyph) + 2 * hitPadX, ROW_H + 2 * hitPadY },
				{ nextX - hitPadX, controlsY - hitPadY, font.width(nextGlyph) + 2 * hitPadX, ROW_H + 2 * hitPadY }
		};
		String[] actions = { "prev", "toggle", "next" };

		for (int i = 0; i < hitBoxes.length; ++i) {
			int[] box = hitBoxes[i];
			if (mouseX >= box[0] && mouseX <= box[0] + box[2] && mouseY >= box[1] && mouseY <= box[1] + box[3]) {
				runAction(actions[i]);
				return true;
			}
		}
		return false;
	}

	/** Re-uploads the cover art if the poll script has written a newer PNG. */
	public void tryLoadArt() {
		if (artFile == null || !artFile.exists()) {
			return;
		}
		long modified = artFile.lastModified();
		// A tiny file means PowerShell is still copying the stream; wait for the next pass.
		if (artFile.length() < 200L) {
			return;
		}
		if (modified == artLastModified && artTex != null) {
			return;
		}
		try {
			byte[] png = Files.readAllBytes(artFile.toPath());
			try (ByteArrayInputStream in = new ByteArrayInputStream(png)) {
				NativeImage image = NativeImage.read(in);
				if (artTex != null) {
					try {
						mc.getTextureManager().release(ART_ID);
					} catch (Exception releaseFailed) {
						// Releasing a texture the manager already dropped is harmless.
					}
				}
				artTex = new DynamicTexture(() -> "spotify_art", image);
				mc.getTextureManager().register(ART_ID, artTex);
				artLastModified = modified;
				hasArt = true;
			}
		} catch (Exception loadFailed) {
			// A half-written or corrupt PNG just leaves the previous art on screen.
		}
	}

	/**
	 * Y coordinate of each stacked row - title, artist, progress bar, times, controls -
	 * vertically centred inside the card so it stays centred at any scale.
	 */
	public int[] computeLayout(int cardY, int cardHeight) {
		int barH = Math.max(2, s(BAR_H_B));
		int stackH = ROW_H + s(GAP_TITLE) + ROW_H + s(GAP_ART) + barH + s(GAP_BAR) + ROW_H + s(GAP_TIME) + ROW_H;
		int titleY = cardY + (cardHeight - stackH) / 2;
		int artistY = titleY + ROW_H + s(GAP_TITLE);
		int barY = artistY + ROW_H + s(GAP_ART);
		int timeY = barY + barH + s(GAP_BAR);
		int controlsY = timeY + ROW_H + s(GAP_TIME);
		return new int[] { titleY, artistY, barY, timeY, controlsY };
	}

	public static void render(GuiGraphics graphics, float partialTick) {
		if (INSTANCE == null || !INSTANCE.isEnabled()) {
			return;
		}
		INSTANCE.renderCard(graphics);
	}

	public void renderCard(GuiGraphics graphics) {
		if (mc == null || mc.player == null) {
			return;
		}
		long nowNanos = System.nanoTime();
		float dt = lastNanos == 0L ? 0.016f : Math.min(0.1f, (nowNanos - lastNanos) / 1.0E9f);
		lastNanos = nowNanos;
		// Frame-rate independent ease towards fully opaque.
		fade += (1.0f - fade) * (1.0f - (float) Math.exp(-12.0f * dt));
		float alpha = fade;
		if (alpha < 0.01f) {
			return;
		}
		tryLoadArt();

		int cardWidth = cardW();
		int cardHeight = cardH();
		int artSize = s(BASE_ART);
		int padX = s(BASE_PAD_X);
		// The card slides in from off the right edge while it fades.
		int cardX = posX() + Math.round((1.0f - alpha) * (cardWidth + s(20)));
		int cardY = posY();
		Font font = mc.font;
		boolean editing = isEditingScreen();

		Color accent = ZenyaPlus.getAccentColor();
		int accentRgb = accent.getRed() << 16 | accent.getGreen() << 8 | accent.getBlue();
		int backgroundColor = clampA((int) (230.0f * alpha)) << 24 | 0x0D1117;
		int borderColor = clampA((int) (180.0f * alpha)) << 24 | 0x1E2632;
		int artBackgroundColor = clampA((int) (210.0f * alpha)) << 24 | 0x141A22;
		int titleColor = clampA((int) (255.0f * alpha)) << 24 | 0xFFFFFF;
		int artistColor = clampA((int) (200.0f * alpha)) << 24 | 0x8090A0;
		int barTrackColor = clampA((int) (200.0f * alpha)) << 24 | 0x1A2030;
		int timeColor = clampA((int) (170.0f * alpha)) << 24 | 0x506070;
		int controlColor = clampA((int) (210.0f * alpha)) << 24 | 0xCCCCCC;
		int noteColor = clampA((int) (150.0f * alpha)) << 24 | accentRgb;
		int accentColor = clampA((int) (205.0f * alpha)) << 24 | accentRgb;
		int dividerColor = clampA((int) (60.0f * alpha)) << 24 | 0xFFFFFF;
		int radius = s(6);

		RenderUtil.drawRoundedRect(graphics, cardX, cardY, cardWidth, cardHeight, radius, backgroundColor, false);
		RenderUtil.drawOutline(graphics, cardX - 1, cardY - 1, cardWidth + 2, cardHeight + 2, radius + 1, 1.0f, borderColor, false);
		if (editing) {
			handleDrag(cardX, cardY, cardWidth, cardHeight);
			RenderUtil.drawOutline(graphics, cardX - 2, cardY - 2, cardWidth + 4, cardHeight + 4, radius + 2, 1.0f, 0x884A9CFF, false);
		}
		// Second pass over the background hides the inner edge of the outlines above.
		RenderUtil.drawRoundedRect(graphics, cardX, cardY, cardWidth, cardHeight, radius, backgroundColor, false);
		RenderUtil.drawRoundedRect(graphics, cardX + radius, cardY, cardWidth - 2 * radius, Math.max(2, s(2)), 0.0f, accentColor, false);

		int artX = cardX + padX;
		int artY = cardY + (cardHeight - artSize) / 2;
		RenderUtil.drawRoundedRect(graphics, artX, artY, artSize, artSize, s(4), artBackgroundColor, false);
		if (hasArt) {
			graphics.blit(RenderPipelines.GUI_TEXTURED, ART_ID, artX, artY, 0.0f, 0.0f, artSize, artSize, artSize, artSize);
		} else {
			int noteWidth = font.width("\u266b");
			graphics.drawString(font, "\u266b", artX + (artSize - noteWidth) / 2, artY + (artSize - 9) / 2, noteColor, false);
		}

		int dividerX = artX + artSize + padX / 2;
		graphics.fill(dividerX, cardY + s(6), dividerX + 1, cardY + cardHeight - s(6), dividerColor);

		int contentX = artX + artSize + padX;
		int contentWidth = Math.max(20, cardX + cardWidth - padX - contentX);
		int[] rows = computeLayout(cardY, cardHeight);
		int titleY = rows[0];
		int artistY = rows[1];
		int barY = rows[2];
		int timeY = rows[3];
		int controlsY = rows[4];
		int barH = Math.max(2, s(BAR_H_B));

		String titleText;
		if (!hasPolledOnce) {
			titleText = "Connecting to Spotify...";
		} else if (title.isEmpty()) {
			titleText = "Nothing playing";
		} else {
			titleText = title;
		}
		if (!titleText.isEmpty()) {
			int titleWidth = font.width(titleText);
			if (titleWidth > contentWidth) {
				// Too long to fit: scroll it, drawing a second copy one gap behind so the
				// wrap point is never visible inside the scissor window.
				long scrollNow = System.nanoTime();
				float scrollDt = scrollNanos == 0L ? 0.0f : Math.min(0.1f, (scrollNow - scrollNanos) / 1.0E9f);
				scrollNanos = scrollNow;
				scrollX += SCROLL_PX_S * scrollDt;
				if (scrollX > titleWidth + SCROLL_GAP) {
					scrollX = 0.0f;
				}
				int scrollOffset = (int) scrollX;
				graphics.enableScissor(contentX, titleY - 1, contentX + contentWidth, titleY + 10);
				graphics.drawString(font, titleText, contentX - scrollOffset, titleY, titleColor, false);
				graphics.drawString(font, titleText, contentX - scrollOffset + titleWidth + SCROLL_GAP, titleY, titleColor, false);
				graphics.disableScissor();
			} else {
				scrollX = 0.0f;
				graphics.drawString(font, titleText, contentX, titleY, titleColor, false);
			}
		}

		String artistText;
		if (!artist.isEmpty()) {
			artistText = artist;
		} else if (title.isEmpty()) {
			artistText = "Open Spotify or play a track";
		} else {
			artistText = "";
		}
		if (!artistText.isEmpty()) {
			graphics.drawString(font, font.plainSubstrByWidth(artistText, contentWidth), contentX, artistY, artistColor, false);
		}

		RenderUtil.drawRoundedRect(graphics, contentX, barY, contentWidth, barH, barH / 2.0f, barTrackColor, false);

		long nowMs = System.currentTimeMillis();
		long posMs;
		if (isPlaying && anchorTimeMs > 0L) {
			posMs = anchorPosMs + Math.max(0L, nowMs - anchorTimeMs);
			if (durMs > 0L) {
				posMs = Math.min(durMs, posMs);
			}
		} else {
			posMs = anchorPosMs;
		}
		if (durMs > 0L && posMs > 0L) {
			float progress = Math.min(1.0f, (float) posMs / (float) durMs);
			int filledWidth = Math.max(barH, Math.round(contentWidth * progress));
			RenderUtil.drawRoundedRect(graphics, contentX, barY, filledWidth, barH, barH / 2.0f, accentColor, false);
			int knobRadius = Math.max(2, s(BAR_H_B));
			fillCircle(graphics, contentX + filledWidth, barY + barH / 2, knobRadius, accentColor);
		}

		long posSeconds = posMs / 1000L;
		long durSeconds = durMs / 1000L;
		String posText = fmt(posSeconds);
		String durText = durSeconds > 0L ? fmt(durSeconds) : "--:--";
		graphics.drawString(font, posText, contentX, timeY, timeColor, false);
		graphics.drawString(font, durText, contentX + contentWidth - font.width(durText), timeY, timeColor, false);

		String prevGlyph = "\u25c0\u25c0";
		String playGlyph = isPlaying ? "\u2759\u2759" : "\u25b6";
		String nextGlyph = "\u25b6\u25b6";
		int centerX = cardX + cardWidth / 2;
		int glyphGap = s(14);
		int playX = centerX - font.width(playGlyph) / 2;
		int prevX = playX - glyphGap - font.width(prevGlyph);
		int nextX = playX + font.width(playGlyph) + glyphGap;
		graphics.drawString(font, prevGlyph, prevX, controlsY, controlColor, false);
		graphics.drawString(font, playGlyph, playX, controlsY, controlColor, false);
		graphics.drawString(font, nextGlyph, nextX, controlsY, controlColor, false);
	}

	/** The card is only draggable while one of these screens is open. */
	public static boolean isEditingScreen() {
		if (mc == null) {
			return false;
		}
		return mc.screen instanceof ChatScreen
				|| mc.screen instanceof InventoryScreen
				|| mc.screen instanceof ClickGUI;
	}

	/**
	 * Polls the raw mouse instead of using screen callbacks, so dragging works on any
	 * screen. The position is only committed to the settings on button release.
	 */
	public void handleDrag(int cardX, int cardY, int cardWidth, int cardHeight) {
		if (mc == null || mc.mouseHandler == null || mc.getWindow() == null) {
			return;
		}
		double mouseX = mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / mc.getWindow().getScreenWidth();
		double mouseY = mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / mc.getWindow().getScreenHeight();
		boolean hovered = mouseX >= cardX && mouseX <= cardX + cardWidth
				&& mouseY >= cardY && mouseY <= cardY + cardHeight;
		boolean held = GLFW.glfwGetMouseButton(mc.getWindow().handle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
		if (held) {
			if (hovered && !dragging) {
				dragging = true;
				dragOffX = (int) mouseX - cardX;
				dragOffY = (int) mouseY - cardY;
			}
			if (dragging) {
				liveX = (int) mouseX - dragOffX;
				liveY = (int) mouseY - dragOffY;
			}
		} else if (dragging) {
			dragging = false;
			positionX.setValue(liveX);
			positionY.setValue(liveY);
			ModuleManager.INSTANCE.saveConfig();
		}
	}

	/** Filled circle drawn as horizontal spans, one per pixel row. */
	public void fillCircle(GuiGraphics graphics, int centerX, int centerY, int radius, int color) {
		for (int row = 0; row < radius * 2; ++row) {
			float dy = (radius - row) - 0.5f;
			float halfWidth = (float) Math.sqrt(Math.max(0.0, (double) radius * radius - (double) dy * dy));
			int span = (int) halfWidth;
			if (span <= 0) {
				continue;
			}
			graphics.fill(centerX - span, centerY - radius + row, centerX + span, centerY - radius + row + 1, color);
		}
	}

	public static String fmt(long seconds) {
		return String.format("%d:%02d", seconds / 60L, seconds % 60L);
	}

	/** Clamps an alpha byte so the fade multiplier cannot push it out of 0..255. */
	public static int clampA(int alpha) {
		return Math.max(0, Math.min(255, alpha));
	}
}
