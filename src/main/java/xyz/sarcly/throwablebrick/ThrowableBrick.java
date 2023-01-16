package xyz.sarcly.throwablebrick;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;
import xyz.sarcly.throwablebrick.client.render.entity.BrickEntityRenderer;
import xyz.sarcly.throwablebrick.client.render.entity.model.BrickEntityModel;
import xyz.sarcly.throwablebrick.entity.projectile.BrickEntity;

public class ThrowableBrick implements ModInitializer, ClientModInitializer {
	public static final String MODID = "throwablebrick";
	public static final Logger LOGGER = LoggerFactory.getLogger(MODID);

	public static final Identifier BRICK_ROTATE_PACKET = new Identifier(MODID, "brick_rotate_packet");
	
	//Entities
	public static final EntityType<BrickEntity> BRICK_ENTITY = FabricEntityTypeBuilder.<BrickEntity>create(SpawnGroup.MISC, BrickEntity::new).dimensions(EntityDimensions.fixed(0.525f, 0.525f)).trackRangeBlocks(6).trackedUpdateRate(20).build();

	//Models
	public static final EntityModelLayer BRICK_MODEL = new EntityModelLayer(new Identifier(MODID, "brick"), "main");
	
	@Override public void onInitialize() {
		Registry.register(Registry.ENTITY_TYPE, new Identifier(MODID, "brick_entity"), BRICK_ENTITY);
	}

	@Override public void onInitializeClient() {
		ClientPlayNetworking.registerGlobalReceiver(BRICK_ROTATE_PACKET, BrickEntity::receiveBrickRotate);
		EntityModelLayerRegistry.registerModelLayer(BRICK_MODEL, BrickEntityModel::getTexturedModelData);
		EntityRendererRegistry.register(BRICK_ENTITY, BrickEntityRenderer::new);
	}
	
	
}
