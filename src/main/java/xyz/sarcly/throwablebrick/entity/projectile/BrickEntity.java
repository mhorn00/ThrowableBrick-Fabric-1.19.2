package xyz.sarcly.throwablebrick.entity.projectile;

import org.jetbrains.annotations.Nullable;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.util.GlfwUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
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
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3f;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import xyz.sarcly.throwablebrick.ThrowableBrick;
import xyz.sarcly.throwablebrick.util.BrickEntityHelper;

public class BrickEntity extends ProjectileEntity {
	private static final TrackedData<Byte> PROJECTILE_FLAGS = DataTracker.registerData(PersistentProjectileEntity.class, TrackedDataHandlerRegistry.BYTE);
	private static final int CRIT_FLAG = 1;
	public PickupPermission pickupType = PickupPermission.DISALLOWED;
	private SoundEvent sound = SoundEvents.ENTITY_ARROW_HIT;
	private Vec3f rotationalVelocity = Vec3f.ZERO;
	private float roll;
	public float prevRoll;
	private short age;
	private double damage;
	public boolean inGround = false;
	public boolean visible = false;
	private boolean needUpdateClient = true;
	private byte updateCount = -2;
	public Vec3d offset = Vec3d.ZERO;
	public Vec3d prevOffset = Vec3d.ZERO;
	@Nullable private BlockState hitBlock;

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
	}

	@Override protected void initDataTracker() {
		this.dataTracker.startTracking(PROJECTILE_FLAGS, (byte) 0);
	}

	public static void receiveBrickRotate(MinecraftClient client, ClientPlayNetworkHandler handler, PacketByteBuf buf, PacketSender responseSender) {
		if (client == null || client.world == null) return;
		Entity ent = client.world.getEntityById(buf.readInt());
		if (ent instanceof BrickEntity) {
			BrickEntity brickEnt = (BrickEntity) ent;
			boolean inGround = buf.readBoolean();
			float pitch = buf.readFloat();
			float yaw = buf.readFloat();
			float roll = buf.readFloat();
			float pVel = buf.readFloat();
			float yVel = buf.readFloat();
			float rVel = buf.readFloat();
			double oX = buf.readDouble();
			double oY = buf.readDouble();
			double oZ = buf.readDouble();
			client.execute(() -> {
				brickEnt.inGround = inGround;
				brickEnt.setRotation(pitch, yaw, roll);
				brickEnt.setRotationalVelocity(new Vec3f(pVel, yVel, rVel));
				brickEnt.offset = new Vec3d(oX, oY, oZ);
			});
		}
	}

	// =====GETTERS=====

	private PacketByteBuf getPacketData(int id, Vec3f rotation, Vec3f rotationVelocity) {
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeInt(id);
		buf.writeBoolean(this.inGround);
		buf.writeFloat(rotation.getX());
		buf.writeFloat(rotation.getY());
		buf.writeFloat(rotation.getZ());
		buf.writeFloat(rotationVelocity.getX());
		buf.writeFloat(rotationVelocity.getY());
		buf.writeFloat(rotationVelocity.getZ());
		buf.writeDouble(offset.getX());
		buf.writeDouble(offset.getY());
		buf.writeDouble(offset.getZ());
		return buf;
	}

	public Vec3f getRotationVelocity() {
		return this.rotationalVelocity;
	}

	public Vec3f getRotation() {
		return new Vec3f(this.getPitch(), this.getYaw(), this.getRoll());
	}

	public float getRoll() {
		return this.roll;
	}

	public boolean isCrit() {
		return (this.dataTracker.get(PROJECTILE_FLAGS) & 1) != 0;
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
		this.setCrit(nbt.getBoolean("Crit"));
		if (nbt.contains("SoundEvent", NbtElement.STRING_TYPE)) {
			this.sound = Registry.SOUND_EVENT.getOrEmpty(new Identifier(nbt.getString("SoundEvent"))).orElse(this.sound);
		}
		if (nbt.contains("HitBlock", NbtElement.COMPOUND_TYPE)) {
			this.hitBlock = NbtHelper.toBlockState(nbt.getCompound("HitBlock"));
		}
		this.inGround = nbt.getBoolean("InGround");
		this.setRoll(nbt.getFloat("Roll"));
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
		this.setPitch(pitch);
		this.setYaw(yaw);
		this.setRoll(roll);
	}

	public void setRotation(Vec3f rotation) {
		this.setPitch(rotation.getX());
		this.setYaw(rotation.getY());
		this.setRoll(rotation.getZ());
	}

	@Override public void setPitch(float pitch) {
		if (!Float.isFinite(pitch)) {
			Util.error("Invalid entity pitch: " + pitch + ", discarding.");
			return;
		} else {
			float p = ((pitch + 180) % 360) - 180;
			if (pitch >= 180) {
				super.setPitch(p);
				this.prevPitch = p - (pitch - this.prevPitch);
			} else if (pitch < -180) {
				super.setPitch(p);
				this.prevPitch = p + (pitch - this.prevPitch);
			} else {
				this.prevPitch = this.getPitch();
				super.setPitch(p);
			}
		}
	}

	@Override public void setYaw(float yaw) {
		if (!Float.isFinite(yaw)) {
			Util.error("Invalid entity yaw: " + yaw + ", discarding.");
			return;
		} else {
			float y = ((yaw + 180) % 360) - 180;
			if (yaw >= 180) {
				super.setYaw(y);
				this.prevYaw = y - (yaw - this.prevYaw);
			} else if (yaw < -180) {
				super.setYaw(yaw);
				this.prevYaw = y + (yaw - this.prevYaw);
			} else {
				this.prevYaw = this.getYaw();
				super.setYaw(yaw);
			}
		}
	}

	public void setRoll(float roll) {
		if (!Float.isFinite(roll)) {
			Util.error("Invalid entity roll: " + roll + ", discarding.");
			return;
		} else {
			float r = ((roll + 180) % 360) - 180;
			if (roll >= 180) {
				this.roll = r;
				this.prevRoll = r - (roll - this.prevRoll);
			} else if (roll < -180) {
				this.roll = r;
				this.prevRoll = r + (roll - this.prevRoll);
			} else {
				this.prevRoll = this.roll;
				this.roll = r;
			}
		}
	}

	public void setRotationalVelocity(Vec3f rotationVel) {
		this.rotationalVelocity = rotationVel;
	}

	public void setCrit(boolean crit) {
		this.setProjectileFlag(CRIT_FLAG, crit);
	}

	private void setProjectileFlag(int index, boolean flag) {
		byte b = this.dataTracker.get(PROJECTILE_FLAGS);
		if (flag) {
			this.dataTracker.set(PROJECTILE_FLAGS, (byte) (b | index));
		} else {
			this.dataTracker.set(PROJECTILE_FLAGS, (byte) (b & ~index));
		}
	}

	@Override public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		nbt.putShort("Age", this.age);
		nbt.putByte("Pickup", (byte) this.pickupType.ordinal());
		nbt.putDouble("Damage", this.damage);
		nbt.putBoolean("Crit", this.isCrit());
		nbt.putString("SoundEvent", Registry.SOUND_EVENT.getId(this.sound).toString());
		if (this.hitBlock != null) {
			nbt.put("HitBlock", NbtHelper.fromBlockState(this.hitBlock));
		}
		nbt.putBoolean("InGround", this.inGround);
		nbt.putFloat("Roll", this.getRoll());
		nbt.put("RotationalVelocity", this.toNbtList(this.rotationalVelocity.getX(), this.rotationalVelocity.getY(), this.rotationalVelocity.getZ()));
	}

	// =====METHODS=====

	@Override public void tick() {
		super.tick();
		Vec3d vel = this.getVelocity();
		Vec3d pos = this.getPos();
		Vec3f rotVel = this.getRotationVelocity();
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
			BrickEntityHelper.getRotation(pos, pos, pos, rotVel, vel, rotVel);
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
		this.setRotation(this.getPitch() + rotVel.getX(), this.getYaw() + rotVel.getY(), this.getRoll() + rotVel.getZ());
		rotVel.scale(drag);
		Vec3d nextVel = vel.multiply(drag);
		if (!this.hasNoGravity()) nextVel = nextVel.subtract(0, 0.05f, 0);
		this.setVelocity(nextVel);
		this.setRotationalVelocity(rotVel);
		this.checkBlockCollision();// TODO: Look into checkBlockCollision
		if (this.updateCount <= -1 && this.updateCount++ == -1) this.visible = true;
		if (!this.world.isClient && this.needUpdateClient) {
			for (ServerPlayerEntity ply : PlayerLookup.tracking(this)) {
				ServerPlayNetworking.send(ply, ThrowableBrick.BRICK_ROTATE_PACKET, this.getPacketData(this.getId(), this.getRotation(), this.getRotationVelocity()));
			}
			this.needUpdateClient = false;
		}
	}

	@Override protected void onEntityHit(EntityHitResult entityHitResult) {
		GlfwUtil.makeJvmCrash();// TODO: onEntityHit
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
		this.setCrit(false);
		this.sound = SoundEvents.ENTITY_ARROW_HIT;
		this.needUpdateClient = true;
	}

	private void tickAge() {
		if (++this.age >= 1200) this.discard();
	}

	private void fall() {
		this.inGround = false;
		this.setVelocity(this.getVelocity().multiply(this.random.nextFloat() * 0.2f, this.random.nextFloat() * 0.2f, this.random.nextFloat() * 0.2f));
		this.needUpdateClient = true;
		this.age = 0;
	}

	private boolean shouldFall() {
		return this.inGround && this.world.isSpaceEmpty(new Box(this.getPos(), this.getPos()).expand(0.3));
	}
}
