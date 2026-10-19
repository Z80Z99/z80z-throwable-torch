package cn.blockforge.generated.moda3c26b8e.api;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.eventbus.api.Cancelable;
import net.minecraftforge.eventbus.api.Event;

/** 在 MinecraftForge.EVENT_BUS 发布；不依赖任何第三方动画库。 */
public class TorchAnimationEvent extends Event {
    public enum Phase { START, RELEASE, CANCEL }
    private final Player player;
    private final InteractionHand hand;
    private final Phase phase;
    private final float charge;

    public TorchAnimationEvent(Player player, InteractionHand hand, Phase phase, float charge) {
        this.player = player;
        this.hand = hand;
        this.phase = phase;
        this.charge = charge;
    }
    public Player getPlayer() { return player; }
    public InteractionHand getHand() { return hand; }
    public Phase getAnimationPhase() { return phase; }
    public float getCharge() { return charge; }

    /** 客户端每次绘制第一/第三人称动画前发布；取消仅阻止本模组姿势，不影响投掷。 */
    @Cancelable
    public static final class Pose extends TorchAnimationEvent {
        private final boolean firstPerson;
        private final float partialTick;
        public Pose(Player player, InteractionHand hand, Phase phase, float charge,
                    boolean firstPerson, float partialTick) {
            super(player, hand, phase, charge);
            this.firstPerson = firstPerson;
            this.partialTick = partialTick;
        }
        public boolean isFirstPerson() { return firstPerson; }
        public float getPartialTick() { return partialTick; }
    }
}
