package xyz.sarcly.throwablebrick.entity.projectile;

import org.jetbrains.annotations.Nullable;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity.PickupPermission;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.s2c.play.GameStateChangeS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.EulerAngle;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3f;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import xyz.sarcly.throwablebrick.ThrowableBrick;
import xyz.sarcly.throwablebrick.util.Rotation;

public class BrickEntity extends ProjectileEntity {
	private static final short MAX_AGE = 1200;
	private static final double BASE_DAMAGE = 6;
	public PickupPermission pickupType = PickupPermission.DISALLOWED;
	private SoundEvent sound = SoundEvents.ENTITY_GENERIC_HURT;
	private Vec3f rotationalVelocity = Vec3f.ZERO;
	private Rotation rotation = new Rotation(0, 0, 0);
	private short age;
	private double damage;
	public boolean inGround = false;
	@Nullable private BlockState hitBlock;
	public int punchLvl;
	public int powerLvl;
	private byte initWait = -2;
	public boolean inInit = true;

	// =====CONSTRUCTORS & INIT=====

	public BrickEntity(EntityType<? extends BrickEntity> entityType, World world) {
		super((EntityType<? extends ProjectileEntity>) entityType, world);
	}

	public BrickEntity(EntityType<? extends BrickEntity> type, double x, double y, double z, World world) {
		this(type, world);
		this.setPosition(x, y, z);
	}

	public BrickEntity(EntityType<? extends BrickEntity> type, LivingEntity owner, World world) {
		this(type, owner.getX(), owner.getEyeY() - (double) 0.1f, owner.getZ(), world);
		if (owner instanceof PlayerEntity) this.pickupType = PickupPermission.ALLOWED;
		this.setOwner(owner);
		if (this.powerLvl>0) this.damage = BASE_DAMAGE+(float)this.powerLvl*0.5+0.5;
		else this.damage = BASE_DAMAGE;
	}

	@Override protected void initDataTracker() {}

	public static void receiveBrickRotate(MinecraftClient client, ClientPlayNetworkHandler handler, PacketByteBuf buf, PacketSender responseSender) {
		if (client == null || client.world == null) return;
		Entity ent = client.world.getEntityById(buf.readInt());
		if (ent instanceof BrickEntity) {
			BrickEntity brickEnt = (BrickEntity) ent;
			boolean inGround = buf.readBoolean();
			float pitch = buf.readFloat();
			float yaw = buf.readFloat();
			float roll = buf.readFloat();
			float ppitch = buf.readFloat();
			float pyaw = buf.readFloat();
			float proll = buf.readFloat();
			float pVel = buf.readFloat();
			float yVel = buf.readFloat();
			float rVel = buf.readFloat();
			client.execute(() -> {
				brickEnt.inGround = inGround;
				brickEnt.setRotation(pitch, yaw, roll, ppitch, pyaw, proll);
				brickEnt.setRotationalVelocity(new Vec3f(pVel, yVel, rVel));
			});
		}
	}
	
	// =====GETTERS=====
	
	private PacketByteBuf getPacketData() {
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeInt(this.getId());
		buf.writeBoolean(this.inGround);
		buf.writeFloat(this.rotation.getPitch());
		buf.writeFloat(this.rotation.getYaw());
		buf.writeFloat(this.rotation.getRoll());
		buf.writeFloat(this.rotation.getPrevPitch());
		buf.writeFloat(this.rotation.getPrevYaw());
		buf.writeFloat(this.rotation.getPrevRoll());
		buf.writeFloat(this.rotationalVelocity.getX());
		buf.writeFloat(this.rotationalVelocity.getY());
		buf.writeFloat(this.rotationalVelocity.getZ());
		return buf;
	}

	public Vec3f getRotationVelocity() {
		return this.rotationalVelocity;
	}

	public Rotation getRotation() {
		return this.rotation;
	}

	protected ItemStack asItemStack() {
		return new ItemStack(Items.BRICK);
	}
	
	@Override public boolean shouldRender(double distance) {
		double d = this.getBoundingBox().getAverageSideLength() * 10.0;
		if (Double.isNaN(d)) d = 1.0;
		return distance < (d *= 64.0 * PersistentProjectileEntity.getRenderDistanceMultiplier()) * d;
	}

	@Override public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		this.age = nbt.getShort("Age");
		if (nbt.contains("Damage", NbtElement.NUMBER_TYPE)) {
			this.damage = nbt.getDouble("Damage");
		}
		this.pickupType = PickupPermission.fromOrdinal(nbt.getByte("Pickup"));
		if (nbt.contains("SoundEvent", NbtElement.STRING_TYPE)) {
			this.sound = Registry.SOUND_EVENT.getOrEmpty(new Identifier(nbt.getString("SoundEvent"))).orElse(this.sound);
		}
		if (nbt.contains("HitBlock", NbtElement.COMPOUND_TYPE)) {
			this.hitBlock = NbtHelper.toBlockState(nbt.getCompound("HitBlock"));
		}
		this.inGround = nbt.getBoolean("InGround");
		NbtList nbtRotVel = nbt.getList("RotationalVelocity", NbtElement.FLOAT_TYPE);
		this.setRotationalVelocity(new Vec3f(nbtRotVel.getFloat(0), nbtRotVel.getFloat(1), nbtRotVel.getFloat(2)));
	}

	// =====SETTERS=====

	public void setVelocity(Entity shooter, float pitch, float yaw, Vec3f rotVel, float speed, float divergence) {
		Vec3d velocity = Vec3d.fromPolar(pitch, yaw).normalize();
		velocity = velocity.add(this.random.nextTriangular(0.0, 0.0172275 * (double) divergence), this.random.nextTriangular(0.0, 0.0172275 * (double) divergence), this.random.nextTriangular(0.0, 0.0172275 * (double) divergence));
		velocity = velocity.multiply(speed);
		Vec3d shooterVel = shooter.getVelocity();
		velocity = velocity.add(shooterVel.x, shooter.isOnGround() ? 0.0f : shooterVel.y, shooterVel.z);
		this.setRotationalVelocity(rotVel);
		this.setVelocity(velocity);
	}
	
	public void setRotation(float pitch, float yaw, float roll) {
		this.rotation.setRotation(pitch, yaw, roll);
	}
	
	public void setRotation(float pitch, float yaw, float roll, float prevPitch, float prevYaw, float prevRoll) {
		this.rotation = new Rotation(pitch, yaw, roll, prevPitch, prevYaw, prevRoll);
	}
	
	public void setRotation(EulerAngle rotation) {
		this.setRotation(rotation.getPitch(), rotation.getYaw(), rotation.getRoll());
	}

	public void setRotation(Vec3f rotation) {
		this.setRotation(rotation.getX(), rotation.getY(), rotation.getZ());
	}

	public void setRotationalVelocity(Vec3f rotationVel) {
		this.rotationalVelocity = rotationVel;
	}

	@Override public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		nbt.putShort("Age", this.age);
		nbt.putByte("Pickup", (byte) this.pickupType.ordinal());
		nbt.putDouble("Damage", this.damage);
		nbt.putString("SoundEvent", Registry.SOUND_EVENT.getId(this.sound).toString());
		if (this.hitBlock != null) {
			nbt.put("HitBlock", NbtHelper.fromBlockState(this.hitBlock));
		}
		nbt.putBoolean("InGround", this.inGround);
		nbt.put("RotationalVelocity", this.toNbtList(this.rotationalVelocity.getX(), this.rotationalVelocity.getY(), this.rotationalVelocity.getZ()));
	}

	// =====METHODS=====

	@Override public void tick() {
		ThrowableBrick.LOGGER.info("============"+(world.isClient?"CLIENT":"SERVER")+" TICK============");
		super.tick();
		Vec3d vel = this.getVelocity();
		Vec3d pos = this.getPos();
		Vec3f rotVel = this.getRotationVelocity();
		Rotation rot = this.getRotation();
		BlockPos curBlockPos = this.getBlockPos();
		BlockState curBlockState = this.world.getBlockState(curBlockPos);
		VoxelShape curVoxelShape = curBlockState.getCollisionShape(this.world, curBlockPos);
		if (!curBlockState.isAir() || !curVoxelShape.isEmpty()) {
			for (Box box : curVoxelShape.getBoundingBoxes()) {
				if (!box.offset(curBlockPos).contains(pos)) continue;
				this.inGround = true;
				break;
			}
		}
		if (this.inGround) {
			if (this.hitBlock != curBlockState && this.shouldFall()) {
				this.fall();
				return;
			}
			//TODO: make brick look nicer when it hits the ground
			//BrickEntityHelper.getRotation(pos, pos, pos, rotVel, vel, rotVel);
			if (!this.world.isClient) this.tickAge();
			return;
		}
		Vec3d posVel = pos.add(vel);
		HitResult hitResult = this.world.raycast(new RaycastContext(pos, posVel, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, this));
		if (hitResult.getType() != HitResult.Type.MISS) {
			posVel = hitResult.getPos();
		}
		while (!this.isRemoved()) {
			EntityHitResult hitEntResult = ProjectileUtil.getEntityCollision(this.world, this, pos, posVel, this.getBoundingBox().stretch(this.getVelocity()).expand(1.0), this::canHit);
			if (hitEntResult != null) {
				hitResult = hitEntResult;
			}
			if (hitResult != null && hitResult.getType() == HitResult.Type.ENTITY) {
				Entity hitEnt = ((EntityHitResult) hitEntResult).getEntity();
				Entity ownerEnt = this.getOwner();
				if (hitEnt instanceof PlayerEntity && ownerEnt instanceof PlayerEntity && !((PlayerEntity) ownerEnt).shouldDamagePlayer((PlayerEntity) hitEnt)) {
					hitResult = null;
					hitEntResult = null;
				}
			}
			if (hitResult != null) {
				this.onCollision(hitResult);
				this.velocityDirty = true;
			}
			if (hitEntResult == null) break; // hit nothing, break
			hitResult = null;
		}
		rotVel = this.getRotationVelocity();
		vel = this.getVelocity();// refresh velocity in case hit
		pos = this.getPos();
		double xVel = vel.x;
		double yVel = vel.y;
		double zVel = vel.z;
		float drag = 0.99f;
		if (this.isTouchingWater()) {
			for (int o = 0; o < 4; ++o) this.world.addParticle(ParticleTypes.BUBBLE, pos.x * 0.25, pos.y * 0.25, pos.z * 0.25, xVel, yVel, zVel);
			drag = 0.975f;
		}
		this.setPosition(this.getX() + xVel, this.getY() + yVel, this.getZ() + zVel);
		this.setRotation(rot.getPitch() + rotVel.getX(), rot.getYaw() + rotVel.getY(), rot.getRoll() + rotVel.getZ());
		rotVel.scale(drag);
		Vec3d nextVel = vel.multiply(drag);
		if (!this.hasNoGravity()) nextVel = nextVel.subtract(0, 0.05f, 0);
		this.setVelocity(nextVel);
		this.setRotationalVelocity(rotVel);
		this.checkBlockCollision();
		if (!this.world.isClient) {
			for (ServerPlayerEntity ply : PlayerLookup.tracking(this)) {
				ServerPlayNetworking.send(ply, ThrowableBrick.BRICK_ROTATE_PACKET, this.getPacketData());
			}
		}
		if (this.world.isClient && this.inInit && this.initWait++ >= 0) this.inInit = false;
	}

	@Override protected void onEntityHit(EntityHitResult entityHitResult) {
		DamageSource damageSource;
        Entity ownEnt = this.getOwner();
        Entity hitEnt = entityHitResult.getEntity();
        int damage = MathHelper.ceil(MathHelper.clamp(this.getVelocity().length() * this.damage, 0.0, Double.MAX_VALUE));
        if (ownEnt == null)  damageSource = DamageSource.thrownProjectile(this, null);
        else {
            damageSource = DamageSource.thrownProjectile(this, ownEnt);
            if (ownEnt instanceof LivingEntity) ((LivingEntity)ownEnt).onAttacking(hitEnt);
        }
        if (hitEnt.damage(damageSource, damage)) {
            if (hitEnt.getType() == EntityType.ENDERMAN) return;
            if (hitEnt instanceof LivingEntity) {
                LivingEntity hitLivingEnt = (LivingEntity)hitEnt;
                if (this.punchLvl > 0) {
                    double d = Math.max(0.0, 1.0 - hitLivingEnt.getAttributeValue(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE));
                    Vec3d vec3d = this.getVelocity().multiply(1.0, 0.0, 1.0).normalize().multiply((double)this.punchLvl * 0.6 * d);
                    if (vec3d.lengthSquared() > 0.0) {
                        hitLivingEnt.addVelocity(vec3d.x, 0.1, vec3d.z);
                    }
                }
                if (!this.world.isClient && ownEnt instanceof LivingEntity) {
                    EnchantmentHelper.onUserDamaged(hitLivingEnt, ownEnt);
                    EnchantmentHelper.onTargetDamaged((LivingEntity)ownEnt, hitLivingEnt);
                }
                if (ownEnt != null && hitLivingEnt != ownEnt && hitLivingEnt instanceof PlayerEntity && ownEnt instanceof ServerPlayerEntity && !this.isSilent()) {
                    ((ServerPlayerEntity)ownEnt).networkHandler.sendPacket(new GameStateChangeS2CPacket(GameStateChangeS2CPacket.PROJECTILE_HIT_PLAYER, GameStateChangeS2CPacket.DEMO_OPEN_SCREEN));
                }
            }
            this.playSound(this.sound, 1.0f, 1.2f / (this.random.nextFloat() * 0.2f + 0.9f));
        } else {
            this.setVelocity(this.getVelocity().multiply(-0.04));
            if (!this.world.isClient && this.getVelocity().lengthSquared() < 1.0E-7) {
                if (this.pickupType == PickupPermission.ALLOWED) {
                    this.dropStack(this.asItemStack(), 0.1f);
                }
                this.discard();
            }
        }
	}

	@Override protected void onBlockHit(BlockHitResult blockHitResult) {
		this.hitBlock = this.world.getBlockState(blockHitResult.getBlockPos());
		this.hitBlock.onProjectileHit(this.world, this.hitBlock, blockHitResult, this);
		Vec3d vel = blockHitResult.getPos().subtract(this.getPos());
		this.setVelocity(vel);
		Vec3d pos = vel.normalize().multiply(0.02f);
		this.setPos(this.getX() - pos.getX(), this.getY() - pos.getY(), this.getZ() - pos.getZ());
		this.setRotationalVelocity(Vec3f.ZERO);
		this.inGround = true;
	}

	private void tickAge() {
		if (++this.age >= MAX_AGE) this.discard();
	}

	private void fall() {
		this.inGround = false;
		this.setVelocity(this.getVelocity().multiply(this.random.nextFloat() * 0.2f, this.random.nextFloat() * 0.2f, this.random.nextFloat() * 0.2f));
		Vec3f rotVel = this.getRotationVelocity();
		rotVel.multiplyComponentwise(this.random.nextFloat() * 0.5f, this.random.nextFloat() * 0.5f, this.random.nextFloat() * 0.5f);
		this.setRotationalVelocity(rotVel);
		this.age = 0;
	}

	private boolean shouldFall() {
		return this.inGround && this.world.isSpaceEmpty(new Box(this.getPos(), this.getPos()).expand(0.3));
	}
}
