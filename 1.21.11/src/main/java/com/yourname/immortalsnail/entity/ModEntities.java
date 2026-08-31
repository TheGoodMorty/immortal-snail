package com.yourname.immortalsnail.entity;

import com.yourname.immortalsnail.ImmortalSnail;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.registry.Registry;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.attribute.DefaultAttributeContainer;

public class ModEntities {

    public static final RegistryKey<EntityType<?>> SNAIL_KEY =
            RegistryKey.of(RegistryKeys.ENTITY_TYPE,
                    Identifier.of(ImmortalSnail.MOD_ID, "snail"));

    public static final EntityType<SnailEntity> SNAIL = EntityType.Builder
            .<SnailEntity>create(SnailEntity::new, SpawnGroup.MISC)
            // Physics dimensions: 0.25 wide x 0.25 tall (4px x 4px) —
            // matches the visual model exactly, so when the hitbox edge is
            // on top of a block the model reads as standing on it instead
            // of hovering off the edge.
            .dimensions(0.25f, 0.25f)
            .maxTrackingRange(10)
            .build(SNAIL_KEY);

    public static void register() {
        Registry.register(Registries.ENTITY_TYPE, SNAIL_KEY, SNAIL);
        // Register default attributes. Without this, DefaultAttributeRegistry.get(SNAIL)
        // returns null and LivingEntity's constructor NPEs when calling getMaxHealth().
        FabricDefaultAttributeRegistry.register(SNAIL, SnailEntity.createAttributes());
    }

    public static DefaultAttributeContainer.Builder createSnailAttributes() {
        return SnailEntity.createAttributes();
    }
}
