package net.mca.datafix;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.templates.TypeTemplate;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Base schema for MCA-owned payloads.
 */
public final class McaDataSchema extends Schema {
    public McaDataSchema(int versionKey, Schema parent) {
        super(versionKey, parent);
    }

    @Override
    public void registerTypes(
            Schema schema,
            Map<String, Supplier<TypeTemplate>> entityTypes,
            Map<String, Supplier<TypeTemplate>> blockEntityTypes
    ) {
        schema.registerType(true, McaDataFixers.MCA_DATA, DSL::remainder);
    }

    @Override
    public Map<String, Supplier<TypeTemplate>> registerEntities(Schema schema) {
        return Map.of();
    }

    @Override
    public Map<String, Supplier<TypeTemplate>> registerBlockEntities(Schema schema) {
        return Map.of();
    }
}
