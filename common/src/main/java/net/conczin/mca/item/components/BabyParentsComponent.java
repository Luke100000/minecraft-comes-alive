package net.conczin.mca.item.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record BabyParentsComponent(UUID mother, UUID father, String motherName, String fatherName) {
   public static final Codec<BabyParentsComponent> CODEC = RecordCodecBuilder.create(
      instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("mother").forGetter(component -> component.mother()),
            UUIDUtil.CODEC.fieldOf("father").forGetter(component -> component.father()),
            Codec.STRING.fieldOf("motherName").forGetter(component -> component.motherName()),
            Codec.STRING.fieldOf("fatherName").forGetter(component -> component.fatherName())
         )
         .apply(instance, (mother, father, motherName, fatherName) -> new BabyParentsComponent(mother, father, motherName, fatherName))
   );
   public static final StreamCodec<ByteBuf, BabyParentsComponent> STREAM_CODEC = StreamCodec.composite(
      UUIDUtil.STREAM_CODEC,
      component -> component.mother(),
      UUIDUtil.STREAM_CODEC,
      component -> component.father(),
      ByteBufCodecs.STRING_UTF8,
      component -> component.motherName(),
      ByteBufCodecs.STRING_UTF8,
      component -> component.fatherName(),
      (mother, father, motherName, fatherName) -> new BabyParentsComponent(mother, father, motherName, fatherName)
   );
}
