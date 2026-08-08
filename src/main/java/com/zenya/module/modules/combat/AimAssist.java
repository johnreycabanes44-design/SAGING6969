package com.zenya.module.modules.combat;

import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import com.zenya.module.Category;
import com.zenya.module.Module;
import com.zenya.setting.ModeSetting;
import com.zenya.setting.Setting;
import com.zenya.utils.KeyUtils;
import com.zenya.utils.MathUtils;
import com.zenya.utils.TimerUtils;
import com.zenya.utils.WorldUtils;
import com.zenya.utils.rotation.Rotation;
import com.zenya.utils.rotation.RotationUtils;

/**
 * Steers the player's view toward the nearest player instead of snapping to it, so
 * the movement still looks hand-made.
 *
 * <p>{@code pitchSpeed}/{@code yawSpeed} are snapshots of the speed settings refreshed
 * every {@code Speed Delay} millis rather than read per tick — that is what lets the
 * turn rate stay constant across a swing instead of tracking a slider mid-motion.
 */
public class AimAssist extends Module {
	public Setting<Boolean> stickyAim;
	public Setting<Boolean> onlyWeapon;
	public Setting<Boolean> onLeftClick;
	public ModeSetting aimAt;
	public Setting<Boolean> stopAtTargetVert;
	public Setting<Boolean> stopAtTargetHoriz;
	public Setting<Float> radius;
	public Setting<Boolean> seeOnly;
	public Setting<Boolean> lookAtNearest;
	public Setting<Float> fov;
	public Setting<Float> verticalSpeed;
	public Setting<Float> horizontalSpeed;
	public Setting<Float> speedDelay;
	public Setting<Float> chance;
	public Setting<Boolean> horizontal;
	public Setting<Boolean> vertical;
	public Setting<Float> waitOnMove;
	public ModeSetting lerpMode;
	public ModeSetting posMode;
	public TimerUtils speedTimer;
	public TimerUtils moveTimer;
	// ponytail: mouseMoved is only ever set to true and moveTimer is never read, so the
	// "Wait on Move" gate below can never fire. Left as-is to keep behaviour identical.
	public boolean mouseMoved;
	public float pitchSpeed;
	public float yawSpeed;

	public AimAssist() {
		super("Aim Assist", Category.COMBAT);
		stickyAim = new Setting<>("Sticky Aim", false);
		onlyWeapon = new Setting<>("Only Weapon", true);
		onLeftClick = new Setting<>("On Left Click", false);
		aimAt = new ModeSetting("Aim At", "Head", "Head", "Chest", "Legs");
		stopAtTargetVert = new Setting<>("Stop at Target Vert", true);
		stopAtTargetHoriz = new Setting<>("Stop at Target Horiz", false);
		radius = new Setting<>("Radius", 9999.0f, 0.1f, 9999.0f);
		seeOnly = new Setting<>("See Only", true);
		lookAtNearest = new Setting<>("Look at Nearest", false);
		fov = new Setting<>("FOV", 180.0f, 5.0f, 360.0f);
		verticalSpeed = new Setting<>("Vertical Speed", 3.0f, 0.0f, 10.0f);
		horizontalSpeed = new Setting<>("Horizontal Speed", 3.0f, 0.0f, 10.0f);
		speedDelay = new Setting<>("Speed Delay", 250.0f, 0.0f, 1000.0f);
		chance = new Setting<>("Chance", 50.0f, 0.0f, 100.0f);
		horizontal = new Setting<>("Horizontal", true);
		vertical = new Setting<>("Vertical", true);
		waitOnMove = new Setting<>("Wait on Move", 0.0f, 0.0f, 1000.0f);
		lerpMode = new ModeSetting("Lerp", "Normal", "Normal", "Smoothstep", "EaseOut");
		posMode = new ModeSetting("Pos Mode", "Normal", "Normal", "Lerped");
		speedTimer = new TimerUtils();
		moveTimer = new TimerUtils();
		mouseMoved = true;
		setDescription("Automatically aims at players for you");
		addSetting(stickyAim);
		addSetting(onlyWeapon);
		addSetting(onLeftClick);
		addSetting(aimAt);
		addSetting(stopAtTargetVert);
		addSetting(stopAtTargetHoriz);
		addSetting(radius);
		addSetting(seeOnly);
		addSetting(lookAtNearest);
		addSetting(fov);
		addSetting(verticalSpeed);
		addSetting(horizontalSpeed);
		addSetting(speedDelay);
		addSetting(chance);
		addSetting(horizontal);
		addSetting(vertical);
		addSetting(waitOnMove);
		addSetting(lerpMode);
		addSetting(posMode);
	}

	@Override
	public void onEnable() {
		mouseMoved = true;
		pitchSpeed = verticalSpeed.getValue();
		yawSpeed = horizontalSpeed.getValue();
		speedTimer.reset();
	}

	@Override
	public void onTick() {
		if (speedTimer.delay(waitOnMove.getValue()) && !mouseMoved) {
			mouseMoved = true;
			speedTimer.reset();
		}
		if (mc.player == null || mc.screen != null) {
			return;
		}
		if (onlyWeapon.getValue() && !mc.player.getMainHandItem().is(ItemTags.SWORDS)
				&& !(mc.player.getMainHandItem().getItem() instanceof AxeItem)) {
			return;
		}
		if (onLeftClick.getValue() && !KeyUtils.isKeyPressed(0)) {
			return;
		}

		Player target = WorldUtils.findNearestPlayer(mc.player, radius.getValue(), seeOnly.getValue(), true);
		// Sticky aim overrides the nearest-player pick so a fight does not hand off mid-combo.
		if (stickyAim.getValue() && mc.player.getLastHurtMob() instanceof Player lastHurt) {
			target = lastHurt;
		}
		if (target == null) {
			return;
		}

		if (speedTimer.delay(speedDelay.getValue())) {
			pitchSpeed = verticalSpeed.getValue();
			yawSpeed = horizontalSpeed.getValue();
			speedTimer.reset();
		}

		Vec3 targetPos = new Vec3(target.getX(), target.getY(), target.getZ());
		Vec3 aimPoint = posMode.is("Lerped") ? target.getPosition(1.0f) : targetPos;
		if (aimAt.is("Chest")) {
			aimPoint = aimPoint.add(0.0, -0.5, 0.0);
		} else if (aimAt.is("Legs")) {
			aimPoint = aimPoint.add(0.0, -1.2, 0.0);
		}
		// Offset toward the near corner of the hitbox instead of its centre.
		if (lookAtNearest.getValue()) {
			double offsetX = mc.player.getX() - target.getX() > 0.0 ? 0.29 : -0.29;
			double offsetZ = mc.player.getZ() - target.getZ() > 0.0 ? 0.29 : -0.29;
			aimPoint = aimPoint.add(offsetX, 0.0, offsetZ);
		}

		Rotation wanted = RotationUtils.getDirection(mc.player, aimPoint);
		if (RotationUtils.getAngleToRotation(wanted) > fov.getValue() / 2.0) {
			return;
		}

		float yawStep = yawSpeed / 50.0f;
		float pitchStep = pitchSpeed / 50.0f;
		float newYaw = mc.player.getYRot();
		float newPitch = mc.player.getXRot();
		if (lerpMode.is("Smoothstep")) {
			newYaw = (float) MathUtils.smoothStepLerp(yawStep, mc.player.getYRot(), (float) wanted.yaw());
			newPitch = (float) MathUtils.smoothStepLerp(pitchStep, mc.player.getXRot(), (float) wanted.pitch());
		} else if (lerpMode.is("Normal")) {
			newYaw = lerp(yawStep, mc.player.getYRot(), (float) wanted.yaw());
			newPitch = lerp(pitchStep, mc.player.getXRot(), (float) wanted.pitch());
		} else if (lerpMode.is("EaseOut")) {
			newYaw = RotationUtils.easeOutBackDegrees(mc.player.getYRot(), (float) wanted.yaw(), yawStep);
			newPitch = RotationUtils.easeOutBackDegrees(mc.player.getXRot(), (float) wanted.pitch(), pitchStep);
		}

		if (MathUtils.randomInt(1, 100) <= chance.getValue() && mouseMoved) {
			HitResult crosshair = WorldUtils.getHitResult(stickyAim.getValue() ? 9999.0 : radius.getValue());
			if (horizontal.getValue()) {
				// Already on the target: stop turning so the aim does not overshoot past it.
				if (stopAtTargetHoriz.getValue() && crosshair instanceof EntityHitResult hit && hit.getEntity() == target) {
					return;
				}
				mc.player.setYRot(newYaw);
			}
			if (vertical.getValue()) {
				if (stopAtTargetVert.getValue() && crosshair instanceof EntityHitResult hit && hit.getEntity() == target) {
					return;
				}
				mc.player.setXRot(newPitch);
			}
		}
	}

	/** Moves {@code from} a {@code factor} of the way to {@code to} along the shorter arc. */
	public float lerp(float factor, float from, float to) {
		return from + Mth.wrapDegrees(to - from) * factor;
	}
}
