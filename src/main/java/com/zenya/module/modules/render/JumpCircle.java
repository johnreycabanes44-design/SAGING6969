package com.zenya.module.modules.render;

import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;

import com.mojang.blaze3d.vertex.PoseStack;
import com.zenya.module.Category;
import com.zenya.module.Module;
import com.zenya.setting.Setting;
import com.zenya.utils.RenderUtils;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * Draws an expanding ring on the spot where the player left the ground.
 *
 * <p>A ring is spawned on the rising edge of "airborne and moving up", which is what
 * {@link #wasJumping} tracks — without it every tick of the jump would spawn one.
 *
 * <p>Rings only ever store their origin and birth timestamp: radius and alpha are
 * re-derived from age during the render pass, so ticking never has to touch them and a
 * dropped frame cannot desync the animation.
 */
public class JumpCircle extends Module {
	/** Line segments per ring; also the divisor that turns a segment index into an angle. */
	private static final int CIRCLE_SEGMENTS = 64;

	public Setting<Color> circleColor;
	public Setting<Integer> fadeTime;
	public Setting<Float> maxSize;
	public Setting<Float> lineWidth;
	public List<Circle> circles;
	public boolean wasJumping;

	public JumpCircle() {
		super("Jump Circle", Category.RENDER);
		this.circleColor = new Setting<>("Color", new Color(255, 255, 255, 200));
		this.fadeTime = new Setting<>("Fade Time", 1200, 100, 3000);
		this.maxSize = new Setting<>("Max Size", 1.8f, 0.5f, 4.0f);
		this.lineWidth = new Setting<>("Line Width", 4.0f, 1.0f, 10.0f);
		this.circles = new ArrayList<>();
		this.wasJumping = false;
		this.setDescription("Displays a clean expanding circle when you jump.");
		this.addSetting(this.circleColor);
		this.addSetting(this.fadeTime);
		this.addSetting(this.maxSize);
		this.addSetting(this.lineWidth);
	}

	@Override
	public void onTick() {
		if (mc.player == null) {
			return;
		}
		boolean jumping = !mc.player.onGround() && mc.player.getDeltaMovement().y > 0.0;
		if (jumping && !this.wasJumping) {
			this.circles.add(new Circle(new Vec3(mc.player.getX(), mc.player.getY(), mc.player.getZ()), System.currentTimeMillis()));
		}
		this.wasJumping = mc.player.getDeltaMovement().y > 0.0 && !mc.player.onGround();
		long now = System.currentTimeMillis();
		this.circles.removeIf(circle -> now - circle.startTime > (long) this.fadeTime.getValue().intValue());
	}

	@Override
	public void onRender(PoseStack poseStack, float partialTicks) {
		if (this.circles.isEmpty() || mc.player == null) {
			return;
		}
		Camera camera = RenderUtils.getCamera();
		if (camera == null) {
			return;
		}
		Vec3 cameraPos = RenderUtils.getCameraPos(camera);
		RenderUtils.WorldBatch batch = RenderUtils.beginWorldBatch(poseStack);
		long now = System.currentTimeMillis();
		for (Circle circle : this.circles) {
			float age = (float) (now - circle.startTime) / (float) this.fadeTime.getValue().intValue();
			if (age > 1.0f) continue;
			float radius = this.easeOutExpo(age) * this.maxSize.getValue();
			// Alpha falls off faster than linearly so the ring is mostly gone before it stops growing.
			float fade = (float) Math.pow(1.0f - age, 1.5);
			Color base = this.circleColor.getValue();
			Color faded = new Color(base.getRed(), base.getGreen(), base.getBlue(), (int) ((float) base.getAlpha() * fade));
			float width = this.lineWidth.getValue();
			for (int segment = 0; segment < CIRCLE_SEGMENTS; ++segment) {
				double angle = (double) segment * Math.PI * 2.0 / (double) CIRCLE_SEGMENTS;
				double nextAngle = (double) (segment + 1) * Math.PI * 2.0 / (double) CIRCLE_SEGMENTS;
				Vec3 from = new Vec3(circle.pos.x + Math.sin(angle) * (double) radius, circle.pos.y, circle.pos.z + Math.cos(angle) * (double) radius);
				Vec3 to = new Vec3(circle.pos.x + Math.sin(nextAngle) * (double) radius, circle.pos.y, circle.pos.z + Math.cos(nextAngle) * (double) radius);
				batch.renderLine(faded, from.subtract(cameraPos), to.subtract(cameraPos), width);
			}
		}
		batch.flush();
	}

	/** Growth curve: nearly full size within the first fifth of the fade, then creeps. */
	public float easeOutExpo(float progress) {
		return progress == 1.0f ? 1.0f : 1.0f - (float) Math.pow(2.0, -10.0 * (double) progress);
	}

	/** One live ring: where the jump started and when, everything else is derived from age. */
	public static class Circle {
		public Vec3 pos;
		public long startTime;

		public Circle(Vec3 pos, long startTime) {
			this.pos = pos;
			this.startTime = startTime;
		}
	}
}
