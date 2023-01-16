package xyz.sarcly.throwablebrick.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.block.BlockState;
import net.minecraft.entity.projectile.PersistentProjectileEntity;

@Mixin(PersistentProjectileEntity.class)
public interface IPersistantProjectileEntityMixin {
	@Accessor("inBlockState") public BlockState getInBlockState();
	@Accessor("inBlockState") public void setInBlockState(BlockState inBlockState);
	@Invoker("shouldFall") public boolean invokeShouldFall();
	@Invoker("fall") public void fall();
}
