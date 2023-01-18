package xyz.sarcly.throwablebrick.item;

import java.util.function.Predicate;

import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity.PickupPermission;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.RangedWeaponItem;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stats;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.util.math.Vec3f;
import net.minecraft.world.World;
import xyz.sarcly.throwablebrick.ThrowableBrick;
import xyz.sarcly.throwablebrick.entity.projectile.BrickEntity;

public class ThrowableBrickItem extends RangedWeaponItem {

	public static final Predicate<ItemStack> PROJECTILE = stack -> stack.isOf(Items.BRICK);
	private static final float baseRotVel = 10.0f;
	private static final float offsetRotVel = 8.0f;
	
	// =====CONSTRUCTOR & INIT=====

	public ThrowableBrickItem(Settings settings) {
		super(settings);
	}

	// =====GETTERS=====

	private boolean isEnchanted(ItemStack s) {
		return EnchantmentHelper.getLevel(Enchantments.POWER, s) > 0 || EnchantmentHelper.getLevel(Enchantments.PUNCH, s) > 0 || EnchantmentHelper.getLevel(Enchantments.FLAME, s) > 0 || EnchantmentHelper.getLevel(Enchantments.INFINITY, s) > 0;
	}

	@Override public int getMaxUseTime(ItemStack stack) {
		return 72000;
	}

	@Override public UseAction getUseAction(ItemStack stack) {
		return UseAction.BOW;
	}

	@Override public Predicate<ItemStack> getProjectiles() {
		return PROJECTILE;
	}

	@Override public int getRange() {
		return 15;
	}

	public static float getPullProgress(int useTicks) {
		float f = (float) useTicks / 20.0f;
		if ((f = (f * f + f * 2.0f) / 3.0f) > 1.0f) {
			f = 1.0f;
		}
		return f;
	}

	// =====SETTERS=====

	// =====METHODS=====

	@Override public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack itemStack = user.getStackInHand(hand);
		if (!user.getAbilities().creativeMode && isEnchanted(itemStack) && itemStack.getCount() <= 1) return TypedActionResult.fail(itemStack);
		if (user.getAbilities().creativeMode || itemStack.getCount() > 0) {
			user.setCurrentHand(hand);
			return TypedActionResult.consume(itemStack);
		}
		return TypedActionResult.fail(itemStack);
	}

	@Override public void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int useTicks) {
		if (stack.isEmpty()) return;
		if (!(user instanceof PlayerEntity)) return;
		PlayerEntity playerEntity = (PlayerEntity) user;
		float pullProgress = getPullProgress(getMaxUseTime(stack) - useTicks);
		if (pullProgress < 0.2) return;
		if (!world.isClient) {// <<Server Side>>
			BrickEntity brickEnt = createBrick(world, stack, playerEntity);
			brickEnt.setRotation(Vec3f.ZERO);
			brickEnt.setVelocity(playerEntity, playerEntity.getPitch(), playerEntity.getYaw(), new Vec3f((world.random.nextFloat()*baseRotVel)+offsetRotVel*(world.random.nextBoolean()?1.0f:-1.0f),(world.random.nextFloat()*baseRotVel)+offsetRotVel*(world.random.nextBoolean()?1.0f:-1.0f),(world.random.nextFloat()*baseRotVel)+offsetRotVel*(world.random.nextBoolean()?1.0f:-1.0f)), pullProgress * 1.5f, 1.0f);
			//brickEnt.setVelocity(playerEntity, playerEntity.getPitch(), playerEntity.getYaw(), new Vec3f(10f, 10f, 0f), pullProgress * 1.5f, 1.0f);
			brickEnt.punchLvl = EnchantmentHelper.getLevel(Enchantments.PUNCH, stack);
			brickEnt.powerLvl = EnchantmentHelper.getLevel(Enchantments.POWER, stack);
			if (playerEntity.getAbilities().creativeMode || EnchantmentHelper.getLevel(Enchantments.INFINITY, stack) > 0) brickEnt.pickupType = PickupPermission.CREATIVE_ONLY;
			world.spawnEntity(brickEnt);
		}
		world.playSound(null, playerEntity.getX(), playerEntity.getY(), playerEntity.getZ(), SoundEvents.ENTITY_SNOWBALL_THROW, SoundCategory.PLAYERS, 0.5f, 0.2f / (world.getRandom().nextFloat() * 0.4f + 1.2f) + pullProgress * 0.5f);
		playerEntity.incrementStat(Stats.USED.getOrCreateStat(this));
		if (playerEntity.getAbilities().creativeMode || EnchantmentHelper.getLevel(Enchantments.INFINITY, stack) > 0) return;
		stack.decrement(1);
		if (stack.isEmpty()) playerEntity.getInventory().removeOne(stack);
	}

	public BrickEntity createBrick(World world, ItemStack stack, LivingEntity shooter) {
		BrickEntity brickEnt = new BrickEntity(ThrowableBrick.BRICK_ENTITY, shooter, world);
		return brickEnt;
	}
}
