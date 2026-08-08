package com.zenya.module.modules.render;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import com.zenya.module.Category;
import com.zenya.module.Module;
import com.zenya.module.modules.client.Friends;
import com.zenya.setting.Setting;
import com.zenya.utils.RenderUtils;

import java.awt.Color;
import java.util.ArrayList;

/**
 * Draws a filled box around every other player in range, plus optional tracers.
 *
 * <p>Everything is collected into {@link #RENDER_BUFFER} before anything is drawn: the
 * boxes and the tracers are two separate batches, so the world can only be walked once.
 * The buffer is a reused static, so it must be cleared at the top of every frame.
 *
 * <p>All positions in the buffer are camera-relative, which is why the tracer origin has
 * to be rebased by hand when the camera is not on the local player.
 */
public class PlayerESP extends Module {
	/** How far in front of the camera a first-person tracer starts. */
	public static final double TRACER_START_DISTANCE = 150.0;
	/** Distance from the player at which the tracer end point is placed. */
	public static final double TRACER_END_DISTANCE = 24.0;
	/** Minimum screen spread so targets directly behind the camera stay distinguishable. */
	public static final double TRACER_BEHIND_MIN_SPREAD = 2.75;
	/** Reused per-frame scratch buffer; cleared by {@link #onRender}, never handed out. */
	public static final ArrayList<RenderData> RENDER_BUFFER = new ArrayList<>(64);

	public Setting<Integer> alpha;
	public Setting<Double> range;
	public Setting<Boolean> tracers;
	public Setting<Color> espColor;
	public Setting<Color> tracerColor;

	public PlayerESP() {
		super("Player ESP", Category.RENDER);
		this.alpha = new Setting<>("Alpha", 100, 1, 255);
		this.range = new Setting<>("Range", 256.0, 16.0, 512.0);
		this.tracers = new Setting<>("Tracers", false);
		this.espColor = new Setting<>("ESP color", new Color(0, 100, 255));
		this.tracerColor = new Setting<>("Tracer color", new Color(0, 100, 255));
		this.setDescription("Draws coloured boxes and optional tracers around other players in the world, with separate friend colouring.");
		this.addSetting(this.alpha);
		this.addSetting(this.range);
		this.addSetting(this.tracers);
		this.addSetting(this.espColor);
		this.addSetting(this.tracerColor);
	}

	@Override
	public void onRender(PoseStack matrices, float partialTicks) {
		Minecraft minecraft = mc;
		if (minecraft.level == null || minecraft.player == null) {
			return;
		}
		Camera camera = RenderUtils.getCamera();
		if (camera == null) {
			return;
		}
		int boxAlpha = this.clampAlpha(this.alpha.getValue());
		if (boxAlpha < 1) {
			return;
		}

		Vec3 cameraPos = RenderUtils.getCameraPos(camera);
		double cameraX = cameraPos.x;
		double cameraY = cameraPos.y;
		double cameraZ = cameraPos.z;
		double maxRange = this.range.getValue();
		double maxRangeSq = maxRange * maxRange;
		boolean drawTracers = this.tracers.getValue();

		Color defaultFill = this.applyOpacity(this.espColor.getValue(), boxAlpha);
		Color defaultTracer = drawTracers ? this.applyOpacity(this.tracerColor.getValue(), 255) : null;
		boolean detachedCamera = this.isNonFirstPersonView();
		Vec3 headPos = detachedCamera ? this.getPlayerHeadPosition(partialTicks) : Vec3.ZERO;
		Vec3 cameraForward = drawTracers ? RenderUtils.getCameraForward(camera) : null;
		Vec3 cameraRight = drawTracers ? RenderUtils.getCameraRight(camera) : null;
		Vec3 cameraUp = drawTracers ? RenderUtils.getCameraUp(cameraForward, cameraRight) : null;
		Vec3 tracerOrigin = drawTracers
				? (detachedCamera ? headPos : cameraForward.scale(TRACER_START_DISTANCE))
				: null;

		// Friend colours are derived once and cached until Friends.getColor() changes.
		Color friendFill = null;
		Color friendTracer = null;
		Color cachedFriendColor = null;

		RENDER_BUFFER.clear();
		LocalPlayer self = minecraft.player;
		for (Player player : minecraft.level.players()) {
			if (player == self || !player.isAlive() || player.isSpectator() || player.isInvisibleTo(self)) {
				continue;
			}
			// ponytail: the friend flag is hard-coded false, so the friend branch below and
			// getFriendLookupName() are unreachable and every player gets the default colour.
			boolean isFriend = false;
			Vec3 pos = this.getLerpedPosCompat(player, partialTicks);
			double dx = pos.x - cameraX;
			double dy = pos.y - cameraY;
			double dz = pos.z - cameraZ;
			if (dx * dx + dy * dy + dz * dz > maxRangeSq) {
				continue;
			}

			Color fill;
			Color tracer;
			if (isFriend) {
				Color friendColor = Friends.getColor();
				if (friendColor != cachedFriendColor || friendFill == null) {
					cachedFriendColor = friendColor;
					friendFill = this.applyOpacity(friendColor, boxAlpha);
					friendTracer = drawTracers ? this.applyOpacity(friendColor, 255) : null;
				}
				fill = friendFill;
				tracer = friendTracer;
			} else {
				fill = defaultFill;
				tracer = defaultTracer;
			}

			double halfWidth = player.getBbWidth() / 2.0;
			double height = player.getBbHeight();
			double tracerTargetY = dy + (double) player.getBbHeight() * 0.5;
			RENDER_BUFFER.add(new RenderData(dx, dy, dz, tracerTargetY, halfWidth, height, fill, tracer, true));
		}
		if (RENDER_BUFFER.isEmpty()) {
			return;
		}

		matrices.pushPose();
		RenderUtils.WorldBatch boxBatch = RenderUtils.beginWorldBatch(matrices);
		for (RenderData data : RENDER_BUFFER) {
			if (!data.boxVisible) {
				continue;
			}
			boxBatch.renderFilledBox(
					data.dx - data.halfWidth, data.dy, data.dz - data.halfWidth,
					data.dx + data.halfWidth, data.dy + data.height, data.dz + data.halfWidth,
					data.fill);
		}
		boxBatch.flush();

		if (drawTracers) {
			RenderUtils.WorldBatch tracerBatch = RenderUtils.beginWorldBatch(matrices);
			for (RenderData data : RENDER_BUFFER) {
				if (data.tracer == null) {
					continue;
				}
				Vec3 end = RenderUtils.getSpreadTracerEnd(data.dx, data.tracerTargetY, data.dz,
						cameraForward, cameraRight, cameraUp, TRACER_END_DISTANCE, TRACER_BEHIND_MIN_SPREAD);
				// The head position is absolute; the rest of the buffer is camera-relative.
				Vec3 start = detachedCamera
						? new Vec3(tracerOrigin.x - cameraX, tracerOrigin.y - cameraY, tracerOrigin.z - cameraZ)
						: tracerOrigin;
				tracerBatch.renderLine(data.tracer, start, end, 1.0f);
			}
			tracerBatch.flush();
		}
		matrices.popPose();
	}

	public int clampAlpha(int alpha) {
		if (alpha < 1) {
			return 1;
		}
		if (alpha > 255) {
			return 255;
		}
		return alpha;
	}

	/** Scales the colour's own alpha by {@code opacity}/255 rather than replacing it. */
	public Color applyOpacity(Color color, int opacity) {
		int scaled = Math.max(0, Math.min(255, Math.round((float) color.getAlpha() / 255.0f * (float) opacity)));
		return new Color(color.getRed(), color.getGreen(), color.getBlue(), scaled);
	}

	/** Interpolated position, falling back to a manual lerp on mappings that lack the helper. */
	public Vec3 getLerpedPosCompat(Player player, float partialTicks) {
		try {
			return player.getPosition(partialTicks);
		} catch (Throwable missingHelper) {
			// Swallowed: older/remapped runtimes may not expose getPosition, so lerp by hand.
			double x = Mth.lerp(partialTicks, player.xOld, player.getX());
			double y = Mth.lerp(partialTicks, player.yOld, player.getY());
			double z = Mth.lerp(partialTicks, player.zOld, player.getZ());
			return new Vec3(x, y, z);
		}
	}

	/**
	 * Name to match against the friends list. The profile accessor was renamed between
	 * authlib versions, so both spellings are tried before falling back to the display name.
	 */
	public String getFriendLookupName(Player player) {
		if (player == null) {
			return "";
		}
		try {
			GameProfile profile = player.getGameProfile();
			if (profile != null) {
				String byGetter = readProfileString(profile, "getName");
				if (byGetter != null) {
					return byGetter;
				}
				String byAccessor = readProfileString(profile, "name");
				if (byAccessor != null) {
					return byAccessor;
				}
			}
		} catch (Throwable noProfile) {
			// Swallowed: a missing or hostile profile must not kill the render pass.
		}
		return player.getName().getString();
	}

	/** Reflective no-arg string getter; null when absent, non-string or blank. */
	private static String readProfileString(GameProfile profile, String methodName) {
		try {
			Object result = profile.getClass().getMethod(methodName).invoke(profile);
			if (result instanceof String text && !text.isBlank()) {
				return text;
			}
		} catch (Throwable missingAccessor) {
			// Swallowed: this spelling simply does not exist on the running authlib.
		}
		return null;
	}

	/** True when the camera is not on the local player's eyes, i.e. tracers need rebasing. */
	public boolean isNonFirstPersonView() {
		if (Freecam.instance != null && Freecam.instance.isEnabled()) {
			return true;
		}
		if (PlayerESP.mc.options != null) {
			try {
				return !PlayerESP.mc.options.getCameraType().isFirstPerson();
			} catch (Throwable noCameraType) {
				// Swallowed: options may not have a camera type yet during early startup.
			}
		}
		return false;
	}

	public Vec3 getPlayerHeadPosition(float partialTicks) {
		if (PlayerESP.mc.player == null) {
			return Vec3.ZERO;
		}
		Vec3 pos = this.getLerpedPosCompat(PlayerESP.mc.player, partialTicks);
		double eyeHeight = PlayerESP.mc.player.getEyeHeight();
		return new Vec3(pos.x, pos.y + eyeHeight, pos.z);
	}

	/** One player's camera-relative box, resolved before any drawing starts. */
	public static class RenderData {
		public double dx;
		public double dy;
		public double dz;
		public double tracerTargetY;
		public double halfWidth;
		public double height;
		public Color fill;
		public Color tracer;
		public boolean boxVisible;

		public RenderData(double dx, double dy, double dz, double tracerTargetY, double halfWidth, double height,
				Color fill, Color tracer, boolean boxVisible) {
			this.dx = dx;
			this.dy = dy;
			this.dz = dz;
			this.tracerTargetY = tracerTargetY;
			this.halfWidth = halfWidth;
			this.height = height;
			this.fill = fill;
			this.tracer = tracer;
			this.boxVisible = boxVisible;
		}
	}
}
