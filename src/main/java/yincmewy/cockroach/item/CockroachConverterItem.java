package yincmewy.cockroach.item;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import yincmewy.cockroach.Cockroach;
import yincmewy.cockroach.entity.CockroachEntity;
import yincmewy.cockroach.entity.RainbowLightningBolt;

import javax.annotation.Nullable;
import java.util.List;

public final class CockroachConverterItem extends Item {
    public static final double RANGE = 32.0D;
    public static final int COOLDOWN_TICKS = 10;

    public CockroachConverterItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        Vec3 start = player.getEyePosition();
        Vec3 end = start.add(player.getLookAngle().scale(RANGE));
        BlockHitResult blockHit = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(player, start, blockHit.getLocation(),
                player.getBoundingBox().expandTowards(end.subtract(start)).inflate(1.0D),
                entity -> canConvert(entity) && entity.isPickable(), start.distanceToSqr(blockHit.getLocation()));
        Vec3 strike = entityHit != null ? entityHit.getEntity().position()
                : blockHit.getType() != HitResult.Type.MISS ? blockHit.getLocation() : null;
        if (strike == null) return InteractionResultHolder.pass(player.getItemInHand(hand));
        activate(level, player, strike);
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getPlayer() == null) return InteractionResult.PASS;
        activate(context.getLevel(), context.getPlayer(), context.getClickLocation());
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }

    public void activate(Level level, Player player, Vec3 position) {
        if (!(level instanceof ServerLevel server) || player.getCooldowns().isOnCooldown(this)) return;
        if (strike(server, position)) player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
    }

    public static boolean canConvert(Entity entity) {
        return entity instanceof Mob && !(entity instanceof CockroachEntity) && entity.isAlive();
    }

    public static boolean strike(ServerLevel level, Vec3 position) {
        RainbowLightningBolt bolt = Cockroach.RAINBOW_LIGHTNING.get().create(level);
        if (bolt == null) return false;
        bolt.moveTo(position);
        if (!level.addFreshEntity(bolt)) return false;
        // Vanilla lightning's strike box: 3 blocks horizontally, from Y-3 to Y+9.
        AABB hitBox = new AABB(position.x - 3, position.y - 3, position.z - 3,
                position.x + 3, position.y + 9, position.z + 3);
        for (Mob target : level.getEntitiesOfClass(Mob.class, hitBox, CockroachConverterItem::canConvert)) {
            convert(level, target);
        }
        return true;
    }

    @Nullable
    public static CockroachEntity convert(ServerLevel level, Mob target) {
        if (!canConvert(target)) return null;
        CockroachEntity roach = Cockroach.COCKROACH.get().create(level);
        if (roach == null) return null;
        var dimensions = Cockroach.COCKROACH.get().getDimensions();
        AABB box = target.getBoundingBox();
        double sourceVolume = box.getXsize() * box.getYsize() * box.getZsize();
        double baseVolume = (double) dimensions.width * dimensions.width * dimensions.height;
        float size = (float) Math.cbrt(sourceVolume / baseVolume);
        if (!CockroachEntity.isValidSize(size)) return null;
        roach.setCockroachSize(size);
        roach.moveTo(target.getX(), target.getY(), target.getZ(), target.getYRot(), target.getXRot());
        roach.yBodyRot = target.yBodyRot;
        roach.yHeadRot = target.yHeadRot;
        roach.setCustomName(target.getCustomName());
        roach.setCustomNameVisible(target.isCustomNameVisible());
        roach.setNoAi(target.isNoAi());
        roach.setSilent(target.isSilent());
        roach.setInvulnerable(target.isInvulnerable());
        if (target.isPersistenceRequired()) roach.setPersistenceRequired();
        roach.setHealth(roach.getMaxHealth() * Mth.clamp(target.getHealth() / target.getMaxHealth(), 0.0F, 1.0F));
        if (!level.addFreshEntity(roach)) return null;
        // Discard instead of killing: no lightning damage, death drops or experience.
        target.discard();
        return roach;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("想要更多蟑螂？"));
    }
}
