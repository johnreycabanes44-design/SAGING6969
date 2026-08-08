package com.zenya.module.modules.donut;

import com.zenya.module.Category;
import com.zenya.module.Module;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundChatCommandSignedPacket;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.sounds.SoundEvents;

/**
 * Swallows an outgoing {@code /pay} and prints the confirmation line the server would
 * have sent, so a stream or screenshot shows a payment that never happened.
 *
 * <p>{@link #INSTANCE} exists because {@link #intercept(String)} is static and is also
 * reached from the chat path, which has no module reference to hand; it is the last
 * constructed instance and may be stale if the module is ever re-registered.
 */
public class FakePay
extends Module {
	public static FakePay INSTANCE;

	public FakePay() {
		super("FakePay", Category.DONUT);
		this.setDescription("Fakes paying money to other players when you run /pay.");
		INSTANCE = this;
	}

	@Override
	public boolean onPacketSend(Packet packet) {
		if (!this.isEnabled()) {
			return false;
		}
		if (packet instanceof ServerboundChatCommandSignedPacket signedCommand) {
			return intercept(signedCommand.command());
		}
		if (packet instanceof ServerboundChatPacket chat) {
			return intercept(chat.message());
		}
		return false;
	}

	/** Returns true when the message was a {@code /pay} and the caller should drop it. */
	public static boolean intercept(String message) {
		if (INSTANCE == null || !INSTANCE.isEnabled()) {
			return false;
		}
		if (message == null || message.isBlank()) {
			return false;
		}
		String command = message.trim();
		if (command.startsWith("/")) {
			command = command.substring(1);
		}
		String[] parts = command.split("\\s+");
		if (parts.length < 3 || !parts[0].equalsIgnoreCase("pay")) {
			return false;
		}
		String target = parts[1];
		String amount = parts[2].replace(",", "").replace("$", "");
		showFakeConfirmation(target, amount);
		return true;
	}

	// Queued on the main thread: the packet hook can fire off the render thread.
	public static void showFakeConfirmation(String target, String amount) {
		if (mc.player == null) {
			return;
		}
		mc.execute(() -> {
			if (mc.player == null) {
				return;
			}
			mc.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.9f, 1.0f);
			MutableComponent line = Component.literal("You paid ").withStyle(ChatFormatting.GRAY)
					.append(Component.literal(target).withStyle(ChatFormatting.GREEN))
					.append(Component.literal(" $" + amount).withStyle(ChatFormatting.GOLD));
			mc.gui.getChat().addMessage(line);
		});
	}
}
