package mca.client.particle;

import net.minecraft.client.particle.*;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.DefaultParticleType;

public class InteractionParticle extends SpriteBillboardParticle {
    protected InteractionParticle(ClientWorld world, double x, double y, double z) {
        super(world, x, y, z);
        this.velocityX *= 0.01F;
        this.velocityY *= 0.01F;
        this.velocityZ *= 0.01F;
        this.velocityY += 0.1D;
        this.scale *= 1.5F;
        this.maxAge = 20;
        this.collidesWithWorld = false;
    }

    @Override
    public ParticleTextureSheet getType() {
        return ParticleTextureSheet.PARTICLE_SHEET_OPAQUE;
    }

    public float getSize(float tickDelta) {
        return 0.3F;
    }

    public void tick() {
        this.prevPosX = this.x;
        this.prevPosY = this.y;
        this.prevPosZ = this.z;
        if (this.age++ >= this.maxAge) {
            this.markDead();
        } else {
            //this.move(this.xd, this.yd, this.zd);
            if (this.y == this.prevPosY) {
                this.velocityX *= 1.1D;
                this.velocityZ *= 1.1D;
            }

            this.velocityX *= 0.86F;
            this.velocityY *= 0.86F;
            this.velocityZ *= 0.86F;
            if (this.onGround) {
                this.velocityX *= 0.7F;
                this.velocityZ *= 0.7F;
            }

        }
    }

    public static class Factory implements ParticleFactory<DefaultParticleType> {
        private final SpriteProvider sprite;

        public Factory(SpriteProvider sprite) {
            this.sprite = sprite;
        }

        public Particle createParticle(DefaultParticleType particleType, ClientWorld world, double x, double y, double z, double velocityX, double velocityY, double velocityZ) {
            InteractionParticle heartparticle = new InteractionParticle(world, x, y + 0.5D, z);
            heartparticle.setSprite(this.sprite);
            heartparticle.setColor(1.0F, 1.0F, 1.0F);
            return heartparticle;
        }
    }
}
