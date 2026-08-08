package com.zenya.utils.rotation;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Yaw/pitch maths shared by the aim and block-placement modules.
 *
 * <p>Differences are folded through {@link Mth#wrapDegrees} so a rotation always
 * takes the short way round, with one deliberate exception: {@link #diff} does
 * not wrap, so it reports the raw gap and callers that want the short path have
 * to wrap it themselves.
 */
public class RotationUtils {
	public static Minecraft mc = Minecraft.getInstance();

	public static Vec3 getPlayerPos(Player player) {
		return new Vec3(player.getX(), player.getY(), player.getZ());
	}

	// ponytail: pitch's sine and cosine are swapped relative to the usual look-vector
	// formula, so this is not a real direction on its own. Left as-is on purpose;
	// WorldUtils.rotationToVec has the identical quirk and the two must agree.
	public static Vec3 rotationToVec(float yaw, float pitch) {
		float pitchRad = pitch * ((float) Math.PI / 180);
		float yawRad = -yaw * ((float) Math.PI / 180);
		float sinYaw = Mth.sin(yawRad);
		float cosYaw = Mth.cos(yawRad);
		float sinPitch = Mth.sin(pitchRad);
		float cosPitch = Mth.cos(pitchRad);
		return new Vec3(cosYaw * sinPitch, -cosPitch, sinYaw * sinPitch);
	}

	public static Vec3 rotationToVec(Player player) {
		return rotationToVec(player.getYRot(), player.getXRot());
	}

	/** Component-wise absolute gap. Not wrapped, so 350 vs 10 yaw reports 340, not 20. */
	public static Rotation diff(Rotation from, Rotation to) {
		double yawGap = Math.abs(Math.max(from.yaw(), to.yaw()) - Math.min(from.yaw(), to.yaw()));
		double pitchGap = Math.abs(Math.max(from.pitch(), to.pitch()) - Math.min(from.pitch(), to.pitch()));
		return new Rotation(yawGap, pitchGap);
	}

	/** Interpolates in float precision on purpose: this feeds the float yaw/pitch setters. */
	public static Rotation lerp(Rotation from, Rotation to, double progress) {
		return new Rotation(
				Mth.lerp((float) progress, (float) from.yaw(), (float) to.yaw()),
				Mth.lerp((float) progress, (float) from.pitch(), (float) to.pitch()));
	}

	/** Manhattan distance over the unwrapped {@link #diff}, not an angular distance. */
	public static double distance(Rotation from, Rotation to) {
		Rotation gap = diff(from, to);
		return gap.yaw() + gap.pitch();
	}

	public static Vec3 getRotationVec() {
		return rotationToVec(mc.player);
	}

	/** Rotation that points {@code from} at the world position {@code target}. */
	public static Rotation getDirection(Entity from, Vec3 target) {
		double deltaX = target.x - from.getX();
		double deltaY = target.y - from.getY();
		double deltaZ = target.z - from.getZ();
		double horizontal = Mth.sqrt((float) (deltaX * deltaX + deltaZ * deltaZ));
		return new Rotation(
				Mth.wrapDegrees(Math.toDegrees(Math.atan2(deltaZ, deltaX)) - 90.0),
				-Mth.wrapDegrees(Math.toDegrees(Math.atan2(deltaY, horizontal))));
	}

	/** Angular gap between the local player's current look and {@code target}, in degrees. */
	public static double getAngleToRotation(Rotation target) {
		double yaw = Mth.wrapDegrees(mc.player.getYRot());
		double pitch = Mth.wrapDegrees(mc.player.getXRot());
		double yawGap = Mth.wrapDegrees(yaw - target.yaw());
		double pitchGap = Mth.wrapDegrees(pitch - target.pitch());
		return Math.sqrt(yawGap * yawGap + pitchGap * pitchGap);
	}

	/**
	 * Eases {@code from} towards {@code to} with an ease-out-cubic fed into an
	 * ease-out-back, so the turn overshoots slightly and settles back — the
	 * overshoot is what makes it read as a hand movement rather than a snap.
	 */
	public static float easeOutBackDegrees(float from, float to, float progress) {
		double eased = 1.0 - Math.pow(1.0 - progress, 3.0);
		return from + Mth.wrapDegrees(to - from)
				* (float) (1.0 + 2.70158 * Math.pow(eased - 1.0, 3.0) + 1.70158 * Math.pow(eased - 1.0, 2.0));
	}
}
