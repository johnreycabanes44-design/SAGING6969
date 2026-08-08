package com.zenya.mixin;

import com.zenya.module.ModuleManager;
import com.zenya.utils.TickRateUtil;
import io.netty.channel.ChannelFutureListener;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The client's packet tap: every inbound packet reaches the modules from here, and any
 * module may veto an outbound one. Both hooks sit on the netty thread, so anything they
 * call has to be safe off the render thread.
 */
@Mixin(Connection.class)
public class ClientConnectionMixin {
	@Inject(method = "genericsFtw", at = @At("HEAD"))
	private static void onHandlePacket(Packet<?> packet, PacketListener listener, CallbackInfo ci) {
		// Time updates arrive once per server tick, which is what TickRateUtil measures.
		if (packet instanceof ClientboundSetTimePacket) {
			TickRateUtil.INSTANCE.onPacket();
		}
		ModuleManager.INSTANCE.onPacketReceive(packet);
	}

	@Inject(method = "Lnet/minecraft/network/Connection;send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V",
			at = @At("HEAD"), cancellable = true)
	private void onSend(Packet<?> packet, ChannelFutureListener callbacks, boolean flush, CallbackInfo ci) {
		if (ModuleManager.INSTANCE.onPacketSend(packet)) {
			ci.cancel();
		}
	}
}
