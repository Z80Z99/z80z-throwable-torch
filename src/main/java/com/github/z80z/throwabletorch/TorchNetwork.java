package com.github.z80z.throwabletorch;

import com.github.z80z.throwabletorch.api.TorchAnimationEvent;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class TorchNetwork {
    private static final String VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID, "main"),
            () -> VERSION, VERSION::equals, VERSION::equals);
    // 只由客户端初始化赋值；专用服务器不会加载客户端类。
    public static Consumer<Animation> clientAnimation = packet -> {};
    public enum Action { START, RELEASE, CANCEL }

    public record Control(Action action, InteractionHand hand) {
        static void encode(Control packet, FriendlyByteBuf buf) {
            buf.writeEnum(packet.action); buf.writeEnum(packet.hand);
        }
        static Control decode(FriendlyByteBuf buf) {
            return new Control(buf.readEnum(Action.class), buf.readEnum(InteractionHand.class));
        }
        static void handle(Control packet, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player != null) TorchServer.handle(player, packet);
            });
            context.setPacketHandled(true);
        }
    }
    public record Animation(UUID playerId, InteractionHand hand, TorchAnimationEvent.Phase phase, float charge) {
        static void encode(Animation packet, FriendlyByteBuf buf) {
            buf.writeUUID(packet.playerId); buf.writeEnum(packet.hand);
            buf.writeEnum(packet.phase); buf.writeFloat(packet.charge);
        }
        static Animation decode(FriendlyByteBuf buf) {
            return new Animation(buf.readUUID(), buf.readEnum(InteractionHand.class),
                    buf.readEnum(TorchAnimationEvent.Phase.class), buf.readFloat());
        }
        static void handle(Animation packet, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> clientAnimation.accept(packet));
            context.setPacketHandled(true);
        }
    }
    public static void register() {
        CHANNEL.registerMessage(0, Control.class, Control::encode, Control::decode, Control::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(1, Animation.class, Animation::encode, Animation::decode, Animation::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
    public static void control(Action action, InteractionHand hand) {
        CHANNEL.sendToServer(new Control(action, hand));
    }
    public static void animate(ServerPlayer player, InteractionHand hand, TorchAnimationEvent.Phase phase, float charge) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                new Animation(player.getUUID(), hand, phase, charge));
    }
    private TorchNetwork() {}
}
