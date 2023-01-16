package xyz.sarcly.throwablebrick.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;

import net.minecraft.item.Item;
import net.minecraft.item.Items;
import xyz.sarcly.throwablebrick.item.ThrowableBrickItem;

public class ThrowableBrickStaticMixins {

	@Mixin(Items.class)
	public static abstract class ThrowableBrickItemMixin {
		@Redirect(
			slice = @Slice(
				from = @At(
					value = "CONSTANT",
					args = {"stringValue=brick"},
					ordinal = 0
				)
			), 
			at = @At(
				value = "NEW",
				target = "Lnet/minecraft/item/Item;*",
				ordinal=0
			),
			method="<clinit>")
		private static Item getBrick(Item.Settings settings) {
			return new ThrowableBrickItem(settings);
		}	
	}
}