package com.zenya.module.modules.render;

import net.minecraft.client.CameraType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

import com.zenya.module.Category;
import com.zenya.module.Module;
import com.zenya.setting.Setting;

import org.joml.Vector3d;

/**
 * Flies a detached camera around while the player entity stays where it was.
 *
 * <p>Nothing here is sent to the server: the camera lives only in {@link #currentPosition}
 * and {@link #yaw}/{@link #pitch}, and every tick the real player is snapped back to the
 * rotation it had when Freecam was enabled, so looking around never leaks.
 * Smart culling is switched off while active, otherwise the chunks behind the player --
 * which the camera can now see -- would be culled away.
 * Renderers reach this module through the static {@link #instance}.
 */
public class Freecam extends Module {
	public static int MIN_SCROLL_SPEED = 1;
	public static int MAX_SCROLL_SPEED = 20;
	public static int SCROLL_SPEED_STEP = 1;

	public Vector3d currentPosition;
	public Vector3d previousPosition;
	public Vector3d velocity;
	public float yaw;
	public float pitch;
	public float previousYaw;
	public float previousPitch;
	public Setting<Integer> speed;
	public Setting<Double> lookSpeed;
	public boolean smoothing;
	public float currentSpeed;
	public Input capturedInput;
	public Vec2 capturedMovement;
	public CameraType savedPerspective;
	public boolean savedChunkCullingEnabled;
	public long lastFrameTime;
	public float savedPlayerYaw;
	public float savedPlayerPitch;
	public static Freecam instance;

	public Freecam() {
		super("Freecam", Category.RENDER);
		this.currentPosition = new Vector3d();
		this.previousPosition = new Vector3d();
		this.velocity = new Vector3d();
		this.speed = new Setting<>("Speed", 1, 1, 20);
		this.lookSpeed = new Setting<>("Look Speed", 1.0, 0.1, 5.0);
		this.smoothing = true;
		this.setDescription("Detaches the camera from your player so you can fly around and look at the world while your body stays in place.");
		instance = this;
		this.addSetting(this.speed);
		this.addSetting(this.lookSpeed);
	}

	@Override
	public void onEnable() {
		if (mc.player == null || mc.level == null) {
			this.toggle();
			return;
		}
		this.savedPerspective = mc.options.getCameraType();
		this.savedChunkCullingEnabled = mc.smartCull;
		mc.smartCull = false;
		this.savedPlayerYaw = mc.player.getYRot();
		this.savedPlayerPitch = mc.player.getXRot();
		this.yaw = mc.player.getYRot();
		this.pitch = mc.player.getXRot();

		Vec3 eyePosition = mc.player.getEyePosition(1.0f);
		this.currentPosition.set(eyePosition.x, eyePosition.y, eyePosition.z);
		this.previousPosition.set(eyePosition.x, eyePosition.y, eyePosition.z);
		this.previousYaw = this.yaw;
		this.previousPitch = this.pitch;
		this.lastFrameTime = System.currentTimeMillis();
		this.velocity.set(0.0, 0.0, 0.0);
		this.currentSpeed = this.getConfiguredSpeed();

		this.captureLivePlayerInput();
		// A body that was standing still when Freecam came on must not keep sliding.
		if (this.capturedInput == null || !hasAnyMovement(this.capturedInput)) {
			mc.player.setDeltaMovement(0.0, mc.player.getDeltaMovement().y, 0.0);
		}
	}

	/** Snapshots the movement keys at the moment Freecam turns on, so the body keeps doing what it was doing. */
	public void captureLivePlayerInput() {
		boolean forward = mc.options.keyUp.isDown();
		boolean backward = mc.options.keyDown.isDown();
		boolean left = mc.options.keyLeft.isDown();
		boolean right = mc.options.keyRight.isDown();
		boolean jump = mc.options.keyJump.isDown();
		boolean sneak = mc.options.keyShift.isDown();
		boolean sprint = mc.options.keySprint.isDown();
		this.capturedInput = new Input(forward, backward, left, right, jump, sneak, sprint);

		// Opposing keys cancel, the same way vanilla builds its movement vector.
		float forwardAxis = forward == backward ? 0.0f : (forward ? 1.0f : -1.0f);
		float strafeAxis = left == right ? 0.0f : (left ? 1.0f : -1.0f);
		this.capturedMovement = new Vec2(strafeAxis, forwardAxis);
	}

	/** Sprint alone does not count as movement -- it only modifies a direction that has to already be held. */
	public static boolean hasAnyMovement(Input input) {
		return input.forward() || input.backward() || input.left() || input.right() || input.jump() || input.shift();
	}

	@Override
	public void onDisable() {
		if (mc.player != null) {
			mc.player.setYRot(this.savedPlayerYaw);
			mc.player.setXRot(this.savedPlayerPitch);
			mc.player.setYHeadRot(this.savedPlayerYaw);
			mc.player.setYBodyRot(this.savedPlayerYaw);
		}
		if (this.savedPerspective != null) {
			mc.options.setCameraType(this.savedPerspective);
		} else {
			mc.options.setCameraType(CameraType.FIRST_PERSON);
		}
		mc.smartCull = this.savedChunkCullingEnabled;
		this.capturedInput = null;
		this.capturedMovement = null;
		this.velocity.set(0.0, 0.0, 0.0);
		this.currentSpeed = this.getConfiguredSpeed();
	}

	@Override
	public void onTick() {
		if (mc.player == null) {
			return;
		}
		// Pin the body every tick: the camera's yaw/pitch must never reach the server.
		mc.player.setYRot(this.savedPlayerYaw);
		mc.player.setXRot(this.savedPlayerPitch);
		mc.player.setYHeadRot(this.savedPlayerYaw);
		mc.player.setYBodyRot(this.savedPlayerYaw);
	}

	/** Advances the camera by one frame. Driven by the render loop, so it is wall-clock timed, not tick timed. */
	public void updateCameraMovement() {
		if (mc.player == null) {
			return;
		}
		this.previousPosition.set(this.currentPosition);
		this.previousYaw = this.yaw;
		this.previousPitch = this.pitch;

		long now = System.currentTimeMillis();
		float deltaSeconds = (float) (now - this.lastFrameTime) / 1000.0f;
		this.lastFrameTime = now;
		// Cap a stutter so the camera cannot teleport, and stand in one 60fps frame when
		// two calls land inside the same millisecond.
		deltaSeconds = Math.min(deltaSeconds, 0.1f);
		if (deltaSeconds < 0.001f) {
			deltaSeconds = 0.016f;
		}
		this.currentSpeed = this.getConfiguredSpeed();

		float yawRadians = (float) Math.toRadians(this.yaw);
		double forwardX = -Math.sin(yawRadians);
		double forwardZ = Math.cos(yawRadians);
		double strafeX = -Math.cos(yawRadians);
		double strafeZ = -Math.sin(yawRadians);
		double moveX = 0.0;
		double moveY = 0.0;
		double moveZ = 0.0;
		double step = (double) this.currentSpeed * 0.5;

		// ponytail: options is null-checked here but dereferenced unguarded on every line below
		if (mc.options != null && mc.options.keySprint.isDown()) {
			step *= 2.0;
		}
		if (mc.options.keyUp.isDown()) {
			moveX += forwardX * step;
			moveZ += forwardZ * step;
		}
		if (mc.options.keyDown.isDown()) {
			moveX -= forwardX * step;
			moveZ -= forwardZ * step;
		}
		if (mc.options.keyRight.isDown()) {
			moveX += strafeX * step;
			moveZ += strafeZ * step;
		}
		if (mc.options.keyLeft.isDown()) {
			moveX -= strafeX * step;
			moveZ -= strafeZ * step;
		}
		if (mc.options.keyJump.isDown()) {
			moveY = step;
		}
		if (mc.options.keyShift.isDown()) {
			moveY -= step;
		}

		if (this.smoothing) {
			// Frame-rate independent exponential approach: 99.9% of the gap closed per second.
			double blend = 1.0 - Math.pow(0.001, deltaSeconds);
			this.velocity.x = Mth.lerp(blend, this.velocity.x, moveX * 2.0);
			this.velocity.y = Mth.lerp(blend, this.velocity.y, moveY * 2.0);
			this.velocity.z = Mth.lerp(blend, this.velocity.z, moveZ * 2.0);
		} else {
			this.velocity.set(moveX * 2.0, moveY * 2.0, moveZ * 2.0);
		}
		this.currentPosition.x += this.velocity.x * deltaSeconds;
		this.currentPosition.y += this.velocity.y * deltaSeconds;
		this.currentPosition.z += this.velocity.z * deltaSeconds;
	}

	public void onScrollWheel(double scrollDelta) {
		int newSpeed = this.speed.getValue() + (int) Math.signum(scrollDelta);
		this.speed.setValue(Mth.clamp(newSpeed, 1, 20));
	}

	public void updateRotation(double deltaYaw, double deltaPitch) {
		this.yaw += (float) deltaYaw;
		this.pitch += (float) deltaPitch;
		this.yaw = Mth.wrapDegrees(this.yaw);
		this.pitch = Mth.clamp(this.pitch, -90.0f, 90.0f);
	}

	public double getInterpolatedX(float partialTicks) {
		return Mth.lerp(partialTicks, this.previousPosition.x, this.currentPosition.x);
	}

	public double getInterpolatedY(float partialTicks) {
		return Mth.lerp(partialTicks, this.previousPosition.y, this.currentPosition.y);
	}

	public double getInterpolatedZ(float partialTicks) {
		return Mth.lerp(partialTicks, this.previousPosition.z, this.currentPosition.z);
	}

	public float getInterpolatedYaw(float partialTicks) {
		return Mth.lerp(partialTicks, this.previousYaw, this.yaw);
	}

	public float getInterpolatedPitch(float partialTicks) {
		return Mth.lerp(partialTicks, this.previousPitch, this.pitch);
	}

	/** Mouse delta multiplier, so the "Look Speed" setting reads as 1.0 = half of raw mouse motion. */
	public float getLookSensitivity() {
		return (float) (0.5 * this.lookSpeed.getValue());
	}

	/** Steps the speed setting by one in the direction of {@code delta}; a zero delta is ignored. */
	public void adjustSpeed(double delta) {
		if (delta == 0.0) {
			return;
		}
		int newSpeed = this.speed.getValue() + (int) Math.signum(delta);
		this.speed.setValue(Mth.clamp(newSpeed, 1, 20));
	}

	public float getConfiguredSpeed() {
		return Mth.clamp(this.speed.getValue().floatValue(), 1.0f, 20.0f);
	}
}
