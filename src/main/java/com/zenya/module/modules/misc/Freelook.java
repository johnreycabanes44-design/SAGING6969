package com.zenya.module.modules.misc;

import net.minecraft.client.CameraType;
import net.minecraft.util.Mth;

import com.zenya.module.ActivatableModule;
import com.zenya.module.Category;
import com.zenya.setting.Setting;

/**
 * Third-person camera that swings around independently of the player's body.
 *
 * <p>The angles live here rather than on the player, so nothing is sent to the server; the
 * mouse hook and the camera renderer reach them through the static {@link #instance}. Smart
 * culling is forced off while active because the camera sees chunks behind the player that
 * would otherwise be culled, and it is restored along with the saved perspective on exit.
 */
public class Freelook extends ActivatableModule {
	public static float MIN_DISTANCE = 1.0f;
	public static float MAX_DISTANCE = 15.0f;
	public static float MIN_SENSITIVITY = 0.1f;
	public static float MAX_SENSITIVITY = 3.0f;
	public static Freelook instance;

	public Setting<Float> distance;
	public Setting<Float> sensitivity;
	public boolean active;
	public CameraType savedPerspective;
	public boolean savedChunkCullingEnabled;
	public float cameraYaw;
	public float cameraPitch;

	public Freelook() {
		super("FreeLook", Category.MISC);
		this.distance = new Setting<>("Distance", 4.0f, 1.0f, 15.0f);
		this.sensitivity = new Setting<>("Sensitivity", 1.0f, 0.1f, 3.0f);
		this.setDescription("Lets you swing the third-person camera around independently of your character so you can look behind without turning.");
		instance = this;
		this.addSetting(this.distance);
		this.addSetting(this.sensitivity);
	}

	@Override
	public void onEnable() {
		this.active = false;
		this.savedPerspective = null;
		this.activateCamera();
	}

	@Override
	public void onDisable() {
		this.deactivateCamera();
	}

	@Override
	public void onTick() {
		if (mc.player == null || mc.options == null || mc.getWindow() == null) {
			this.deactivateCamera();
			return;
		}
		if (this.active) {
			this.ensureCameraState();
		}
	}

	@Override
	public void onActivationKeyPressed() {
		if (!this.isEnabled()) {
			return;
		}
		if (this.active) {
			this.deactivateCamera();
		} else {
			this.activateCamera();
		}
	}

	public boolean isCameraActive() {
		return this.isEnabled() && this.active && mc.player != null;
	}

	/** Turns one raw mouse movement into camera rotation; the pitch is clamped the way vanilla clamps it. */
	public void consumeMouseDelta(double deltaX, double deltaY) {
		if (!this.isCameraActive()) {
			return;
		}
		double scale = 0.15 * (double) this.getSensitivity();
		this.cameraYaw = Mth.wrapDegrees(this.cameraYaw + (float) (deltaX * scale));
		this.cameraPitch = Mth.clamp(this.cameraPitch + (float) (deltaY * scale), -90.0f, 90.0f);
	}

	public float getCameraYaw() {
		return this.cameraYaw;
	}

	public float getCameraPitch() {
		return this.cameraPitch;
	}

	public float getDistance() {
		return Mth.clamp(this.distance.getValue().floatValue(), 1.0f, 15.0f);
	}

	/** Always on: the camera keeps vanilla's wall clipping, no setting exposes it. */
	public boolean shouldWallClip() {
		return true;
	}

	public float getSensitivity() {
		return Mth.clamp(this.sensitivity.getValue().floatValue(), 0.1f, 3.0f);
	}

	/** Snapshots the perspective and culling flag, then swings into third person from the player's current rotation. */
	public void activateCamera() {
		if (this.active || mc.player == null || mc.options == null) {
			this.ensureCameraState();
			return;
		}
		this.savedPerspective = mc.options.getCameraType();
		this.savedChunkCullingEnabled = mc.smartCull;
		mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
		mc.smartCull = false;
		this.cameraYaw = mc.player.getYRot();
		this.cameraPitch = mc.player.getXRot();
		this.active = true;
	}

	/** Vanilla can drop the perspective back to first person or front view (F5, respawn) while we are active. */
	public void ensureCameraState() {
		if (!this.active || mc.options == null) {
			return;
		}
		if (!mc.options.getCameraType().isMirrored() && !mc.options.getCameraType().isFirstPerson()) {
			return;
		}
		mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
		mc.smartCull = false;
	}

	public void deactivateCamera() {
		if (!this.active) {
			return;
		}
		this.active = false;
		if (mc.options != null) {
			mc.options.setCameraType(this.savedPerspective == null ? CameraType.FIRST_PERSON : this.savedPerspective);
		}
		mc.smartCull = this.savedChunkCullingEnabled;
	}
}
