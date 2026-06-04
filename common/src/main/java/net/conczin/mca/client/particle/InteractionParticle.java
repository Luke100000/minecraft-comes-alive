package net.conczin.mca.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;

public class InteractionParticle extends SingleQuadParticle {
    protected InteractionParticle(ClientLevel world, double x, double y, double z, TextureAtlasSprite sprite) {
        super(world, x, y, z, sprite);
        this.xd *= 0.01F;
        this.yd *= 0.01F;
        this.zd *= 0.01F;
        this.yd += 0.1D;
        this.quadSize *= 1.5F;
        this.lifetime = 20;
        this.hasPhysics = false;
    }

    @Override
    protected Layer getLayer() {
        return Layer.OPAQUE;
    }

    public float getQuadSize(float tickDelta) {
        return 0.3F;
    }

    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.age++ >= this.lifetime) {
            this.remove();
        } else {
            //this.move(this.xd, this.yd, this.zd);
            if (this.y == this.yo) {
                this.xd *= 1.1D;
                this.zd *= 1.1D;
            }

            this.xd *= 0.86F;
            this.yd *= 0.86F;
            this.zd *= 0.86F;
            if (this.onGround) {
                this.xd *= 0.7F;
                this.zd *= 0.7F;
            }

        }
    }

    public static class Factory implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprite;

        public Factory(SpriteSet sprite) {
            this.sprite = sprite;
        }

        public Particle createParticle(SimpleParticleType particleType, ClientLevel world, double x, double y, double z, double velocityX, double velocityY, double velocityZ) {
            InteractionParticle heartparticle = new InteractionParticle(world, x, y + 0.5D, z, this.sprite.first());
            heartparticle.setColor(1.0F, 1.0F, 1.0F);
            return heartparticle;
        }

        @Override
        public Particle createParticle(SimpleParticleType particleType, ClientLevel world, double x, double y, double z, double velocityX, double velocityY, double velocityZ, RandomSource random) {
            return createParticle(particleType, world, x, y, z, velocityX, velocityY, velocityZ);
        }
    }
}
