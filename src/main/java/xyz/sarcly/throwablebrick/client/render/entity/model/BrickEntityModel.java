package xyz.sarcly.throwablebrick.client.render.entity.model;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import xyz.sarcly.throwablebrick.ThrowableBrick;

@Environment(value = EnvType.CLIENT)
public class BrickEntityModel extends Model {

	public static final Identifier TEXTURE = new Identifier(ThrowableBrick.MODID, "textures/entity/brick.png");
	private final ModelPart root;

	public BrickEntityModel(ModelPart root) {
		super(RenderLayer::getEntitySolid);
		this.root = root;
	}

	public static TexturedModelData getTexturedModelData() {
		ModelData modelData = new ModelData();
		modelData.getRoot().addChild("brick", ModelPartBuilder.create().uv(0, 0).cuboid(-4.0f, -1.0f, -2.0f, 8.0f, 2.0f, 4.0f), ModelTransform.NONE);
		return TexturedModelData.of(modelData, 32, 32);
	}

	@Override public void render(MatrixStack matrices, VertexConsumer vertices, int light, int overlay, float red, float green, float blue, float alpha) {
		this.root.render(matrices, vertices, light, overlay, red, green, blue, alpha);
	}
}
