package net.conczin.mca.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;

public class InteractionParticle extends SingleQuadParticle {
    protected InteractionParticle(ClientLevel world, double x, double y, double z, SpriteSet sprite, RandomSource random) {
        super(world, x, y, z, sprite.get(random));
        this.xd *= 0.01F;
        this.yd *= 0.01F;
        this.zd *= 0.01F;
        this.yd += 0.1D;
        this.quadSize *= 1.5F;
        this.lifetime = 20;
        this.hasPhysics = false;
    }

    @Override
    public Layer getLayer() {
        return Layer.OPAQUE;
    }

    @Override
    public float getQuadSize(float tickDelta) {
        return 0.3F;
    }

    @Override
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

        @Override
        public Particle createParticle(
                SimpleParticleType particleType,
                ClientLevel world,
                double x,
                double y,
                double z,
                double velocityX,
                double velocityY,
                double velocityZ,
                RandomSource random
        ) {
            InteractionParticle heartparticle = new InteractionParticle(world, x, y + 0.5D, z, this.sprite, random);
            heartparticle.setColor(1.0F, 1.0F, 1.0F);
            return heartparticle;
        }
    }
}
