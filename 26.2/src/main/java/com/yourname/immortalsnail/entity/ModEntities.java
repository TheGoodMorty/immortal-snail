package com.yourname.immortalsnail.entity;

import com.yourname.immortalsnail.ImmortalSnail;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;

public class ModEntities {

    public static final ResourceKey<EntityType<?>> SNAIL_KEY =
            ResourceKey.create(Registries.ENTITY_TYPE,
                    Identifier.fromNamespaceAndPath(ImmortalSnail.MOD_ID, "snail"));

    public static final EntityType<SnailEntity> SNAIL = EntityType.Builder
            .<SnailEntity>of(SnailEntity::new, MobCategory.MISC)
            // Physics dimensions: 0.25 wide x 0.25 tall (4px x 4px) —
            // matches the visual model exactly, so when the hitbox edge is
            // on top of a block the model reads as standing on it instead
            // of hovering off the edge.
            .sized(0.25f, 0.25f)
            .clientTrackingRange(10)
            .build(SNAIL_KEY);

    public static void register() {
        Registry.register(BuiltInRegistries.ENTITY_TYPE, SNAIL_KEY, SNAIL);
        // Register default attributes. Without this, DefaultAttributeRegistry.get(SNAIL)
        // returns null and LivingEntity's constructor NPEs when calling getMaxHealth().
        FabricDefaultAttributeRegistry.register(SNAIL, SnailEntity.createAttributes());
    }

    public static AttributeSupplier.Builder createSnailAttributes() {
        return SnailEntity.createAttributes();
    }
}