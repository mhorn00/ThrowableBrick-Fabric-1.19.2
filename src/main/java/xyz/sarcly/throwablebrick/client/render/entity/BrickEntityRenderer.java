package xyz.sarcly.throwablebrick.client.render.entity;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Matrix3f;
import net.minecraft.util.math.Matrix4f;
import net.minecraft.util.math.Vec3f;
import xyz.sarcly.throwablebrick.ThrowableBrick;
import xyz.sarcly.throwablebrick.client.render.entity.model.BrickEntityModel;
import xyz.sarcly.throwablebrick.entity.projectile.BrickEntity;
import xyz.sarcly.throwablebrick.util.Rotation;

@Environment(value=EnvType.CLIENT)
public class BrickEntityRenderer extends EntityRenderer<BrickEntity> {
	public static final Identifier TEXTURE = new Identifier(ThrowableBrick.MODID, "textures/entity/brick.png");
	private final BrickEntityModel model;
	
	public BrickEntityRenderer(EntityRendererFactory.Context context) {
		super(context);
		this.model = new BrickEntityModel(context.getPart(ThrowableBrick.BRICK_MODEL));
	}

	@Override public void render(BrickEntity brickEnt, float yaw, float tickDelta, MatrixStack matrixStack, VertexConsumerProvider vertexConsumerProvider, int light) {
		if (!brickEnt.inInit) {
			matrixStack.push();
			Rotation r = brickEnt.getRotation();
			float x = MathHelper.lerp(tickDelta, r.getPrevPitch(), r.getPitch());
			float y = MathHelper.lerp(tickDelta, r.getPrevYaw(), r.getYaw());
			float z = MathHelper.lerp(tickDelta, r.getPrevRoll(), r.getRoll());
			//ThrowableBrick.LOGGER.info("### tickDelta="+tickDelta+", g="+brickEnt.inGround+", CURRENT ROTATION:("+r.getPrevPitch()+", "+r.getPrevYaw()+", "+r.getPrevRoll()+"), PREV ROTATION:("+r.getPitch()+", "+r.getYaw()+", "+r.getRoll()+"), RENDER ROTATION:("+x+", "+y+", "+z+")");
			matrixStack.translate(0.0f, 0.25f, 0.0f);
			matrixStack.multiply(Vec3f.POSITIVE_X.getDegreesQuaternion(x));
			matrixStack.multiply(Vec3f.POSITIVE_Y.getDegreesQuaternion(y));
			matrixStack.multiply(Vec3f.POSITIVE_Z.getDegreesQuaternion(z));
	        VertexConsumer vertexConsumer = ItemRenderer.getDirectItemGlintConsumer(vertexConsumerProvider, this.model.getLayer(this.getTexture(brickEnt)), false, false);
	        this.model.render(matrixStack, vertexConsumer, light, OverlayTexture.DEFAULT_UV, 1.0f, 1.0f, 1.0f, 1.0f);
	        matrixStack.pop();
			super.render(brickEnt, yaw, tickDelta, matrixStack, vertexConsumerProvider, light);
		}
	}

	public void vertex(Matrix4f positionMatrix, Matrix3f normalMatrix, VertexConsumer vertexConsumer, int x, int y, int z, float u, float v, int normalX, int normalZ, int normalY, int light) {
		vertexConsumer.vertex(positionMatrix, x, y, z).color(255, 255, 255, 255).texture(u, v).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(normalMatrix, normalX, normalY, normalZ).next();
	}

	@Override public Identifier getTexture(BrickEntity brickEnt) {
		return TEXTURE;
	}

}
