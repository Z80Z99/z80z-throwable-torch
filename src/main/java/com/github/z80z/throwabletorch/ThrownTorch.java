package com.github.z80z.throwabletorch;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.entity.IEntityAdditionalSpawnData;
import net.minecraftforge.network.NetworkHooks;

import java.util.function.Consumer;

public final class ThrownTorch extends ThrowableItemProjectile implements IEntityAdditionalSpawnData {
    public record FlightSample(Vec3 from, Vec3 to) {}
    public static Consumer<FlightSample> clientTrail = sample -> {};
    private boolean recoverable;
    private int flightAge;

    public ThrownTorch(EntityType<? extends ThrownTorch> type, Level level) { super(type, level); }
    public ThrownTorch(Level level, LivingEntity owner, ItemStack stack, boolean recoverable) {
        super(GeneratedMod.THROWN_TORCH.get(), owner, level);
        ItemStack single = stack.copy();
        single.setCount(1);
        setItem(single);
        this.recoverable = recoverable;
    }
    @Override public void writeSpawnData(FriendlyByteBuf buf) {
        buf.writeInt(getOwner() == null ? -1 : getOwner().getId());
    }
    @Override public void readSpawnData(FriendlyByteBuf buf) {
        int owner = buf.readInt();
        if (owner >= 0) setOwner(level().getEntity(owner));
    }
    @Override protected Item getDefaultItem() { return Items.TORCH; }
    @Override protected float getGravity() { return 0.045F; }
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket() { return NetworkHooks.getEntitySpawningPacket(this); }

    @Override public void tick() {
        Vec3 previous = position();
        super.tick();
        if (isRemoved()) return;
        flightAge++;
        if (level().isClientSide) {
            clientTrail.accept(new FlightSample(previous, position()));
            // 出手前几刻投射物还贴脸,先不出火星,避免遮挡视野。
            if (TorchConfig.SPARKS.get() && flightAge >= 3) {
                Vec3 motion = getDeltaMovement();
                for (int i = 0; i < 2; i++) {
                    Vec3 point = previous.lerp(position(), (i + 0.5) / 2.0);
                    level().addParticle(ParticleTypes.SMALL_FLAME, point.x, point.y + 0.1, point.z,
                            -motion.x * 0.035 + (random.nextDouble() - 0.5) * 0.025,
                            0.012 + random.nextDouble() * 0.025, -motion.z * 0.035);
                }
            }
        } else if (isInWaterOrBubble() || isInLava() || flightAge >= 200 || getY() < level().getMinBuildHeight() + 1) {
            // 进入液体或飞行超时后回收为掉落物，不创建水下火把或隐形光源。
            finish(false);
        }
    }
    @Override protected void onHitBlock(BlockHitResult hit) {
        super.onHitBlock(hit);
        if (!level().isClientSide && !isRemoved()) {
            setPos(hit.getLocation().add(Vec3.atLowerCornerOf(hit.getDirection().getNormal()).scale(0.12)));
            finish(tryPlace(hit));
        }
    }
    @Override protected void onHitEntity(EntityHitResult hit) {
        // 照明工具，不造成伤害、不点燃生物。
        if (!level().isClientSide && !isRemoved()) finish(false);
    }
    private boolean tryPlace(BlockHitResult hit) {
        if (!(getOwner() instanceof ServerPlayer player) || !player.isAlive()
                || player.level() != level() || player.isSpectator() || !player.mayBuild()) return false;
        Direction face = hit.getDirection();
        if (face == Direction.DOWN) return false;
        BlockPos pos = hit.getBlockPos().relative(face);
        if (!level().hasChunkAt(pos) || !level().isInWorldBounds(pos)
                || !level().getWorldBorder().isWithinBounds(pos)
                || !level().mayInteract(player, pos) || !level().mayInteract(player, hit.getBlockPos())
                || !player.mayUseItemAt(pos, face, getItem())) return false;
        BlockState old = level().getBlockState(pos);
        if (!old.canBeReplaced() || old.hasBlockEntity() || !level().getFluidState(pos).isEmpty()) return false;
        BlockState placed = face == Direction.UP ? Blocks.TORCH.defaultBlockState()
                : Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, face);
        if (!placed.canSurvive(level(), pos)) return false;
        BlockSnapshot snapshot = BlockSnapshot.create(level().dimension(), level(), pos);
        // 暂不更新邻居，先交由领地/保护模组的标准放置事件决定是否允许。
        if (!level().setBlock(pos, placed, 18)) return false;
        if (ForgeEventFactory.onBlockPlace(player, snapshot, face)) {
            boolean wasRestoring = level().restoringBlockSnapshots;
            level().restoringBlockSnapshots = true;
            try { snapshot.restore(true, false); }
            finally { level().restoringBlockSnapshots = wasRestoring; }
            return false;
        }
        // Forge 补丁在服务端会对该区块调用 getFullStatus()，必须传真实区块，传 null 会 NPE。
        level().markAndNotifyBlock(pos, level().getChunkAt(pos), old, placed, 3, 512);
        level().gameEvent(player, GameEvent.BLOCK_PLACE, pos);
        return true;
    }
    private void finish(boolean placed) {
        if (isRemoved()) return;
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.WOOD_HIT,
                SoundSource.BLOCKS, 0.7F, placed ? 0.9F : 0.7F);
        if (!placed && recoverable) spawnAtLocation(getItem().copy());
        discard();
    }
    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Recoverable", recoverable);
        tag.putInt("FlightAge", flightAge);
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        recoverable = tag.getBoolean("Recoverable");
        flightAge = tag.getInt("FlightAge");
    }
}
