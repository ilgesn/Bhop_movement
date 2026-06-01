package com.example.mixin;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class PlayerEntityMixin extends LivingEntity {

    protected PlayerEntityMixin(EntityType<? extends LivingEntity> entityType, Level level) {
        super(entityType, level);
    }

    @Inject(method = "travel", at = @At("HEAD"), cancellable = true)
    private void injectTrueSourcePhysics(Vec3 movementInput, CallbackInfo ci) {
        Player player = (Player) (Object) this;

        // Safely protect crawl/sneak/swim speeds from being broken
        if (this.isCrouching() || this.isSwimming() || this.isVisuallyCrawling() || this.isShiftKeyDown() || player.getAbilities().flying || this.isInWater() || player.isSpectator()) {
            return; 
        }

        // --- THE CRASH-PROOF GROUND BOOSTER ---
        // If you are on the ground and jumping, we intercept it right here inside travel!
        if (this.onGround() && player.jumping) {
            Vec3 vel = this.getDeltaMovement();
            double horizontalSpeed = Math.sqrt(vel.x * vel.x + vel.z * vel.z);
            
            // If we have existing speed from landing a jump, multiply it by 1.15 to stack velocity!
            if (horizontalSpeed > 0.08) {
                this.setDeltaMovement(vel.x * 1.15, vel.y, vel.z * 1.15);
            }
        }

        // --- AIR STRAFE BALANCING ---
        if (!this.onGround() && !this.isFallFlying() && !this.onClimbable()) {
            
            double strafe = movementInput.x;
            double forward = movementInput.z;

            float yaw = this.getYRot();
            float rad = yaw * 0.017453292F;
            float cos = (float) Math.cos(rad);
            float sin = (float) Math.sin(rad);

            // Translate keyboard inputs into a world direction vector
            double xDir = strafe * cos - forward * sin;
            double zDir = forward * cos + strafe * sin;
            
            double len = Math.sqrt(xDir * xDir + zDir * zDir);
            Vec3 wishDir = (len > 0.001) ? new Vec3(xDir / len, 0, zDir / len) : Vec3.ZERO;

            Vec3 currentVelocity = this.getDeltaMovement();
            double nextX = currentVelocity.x;
            double nextZ = currentVelocity.z;

            // Using a tiny tracking window (0.15) means your velocity vector realigns instantly.
            // This is what lets you gain massive speed with subtle mouse turns instead of wild desk sweeps!
            double wishspeed = (strafe != 0 || forward != 0) ? 0.15 : 0;

            if (wishspeed > 0 && !wishDir.equals(Vec3.ZERO)) {
                double currentspeed = nextX * wishDir.x + nextZ * wishDir.z;
                double addspeed = wishspeed - currentspeed;

                if (addspeed > 0) {
                    // Strong, snappy air-acceleration coefficient 
                    double accelSpeed = 310.0 * wishspeed * 0.05;
                    if (accelSpeed > addspeed) {
                        accelSpeed = addspeed;
                    }

                    nextX += wishDir.x * accelSpeed;
                    nextZ += wishDir.z * accelSpeed;
                }
            } else {
                // High momentum preservation when drifting through the air without keys pressed
                nextX *= 0.998;
                nextZ *= 0.998;
            }

            // Normal Minecraft gravity calculations
            double nextY = currentVelocity.y;
            nextY -= 0.08; 
            nextY *= 0.98; 

            this.setDeltaMovement(nextX, nextY, nextZ);
            
            // Keeps block collision scaling smooth and functional
            this.move(MoverType.SELF, this.getDeltaMovement());

            // Safely cancel vanilla calculations while mid-air
            ci.cancel();
        }
    }
}
