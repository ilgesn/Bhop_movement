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

        // FIXED TYPO: Using native isCrouching() and isSwimming() to safely protect crawl/sneak speeds
        if (this.isCrouching() || this.isSwimming() || this.isVisuallyCrawling() || this.isShiftKeyDown() || player.getAbilities().flying || this.isInWater() || player.isSpectator()) {
            return; 
        }

        // Run ONLY when airborne
        if (!this.onGround() && !this.isFallFlying() && !this.onClimbable()) {
            
            double strafe = movementInput.x;
            double forward = movementInput.z;

            float yaw = this.getYRot();
            float rad = yaw * 0.017453292F;
            float cos = (float) Math.cos(rad);
            float sin = (float) Math.sin(rad);

            // Translate movement keys to world vectors based on your camera angle
            double xDir = strafe * cos - forward * sin;
            double zDir = forward * cos + strafe * sin;
            
            double len = Math.sqrt(xDir * xDir + zDir * zDir);
            Vec3 wishDir = (len > 0.001) ? new Vec3(xDir / len, 0, zDir / len) : Vec3.ZERO;

            Vec3 currentVelocity = this.getDeltaMovement();
            double nextX = currentVelocity.x;
            double nextZ = currentVelocity.z;

            // 1. SMALLER TRACKING WINDOW (0.18):
            // By making this smaller, your camera angle matches your speed vector much faster.
            // This stops you from sliding violently sideways and completely kills the "wonky glide."
            double wishspeed = (strafe != 0 || forward != 0) ? 0.18 : 0;

            if (wishspeed > 0 && !wishDir.equals(Vec3.ZERO)) {
                double currentspeed = nextX * wishDir.x + nextZ * wishDir.z;
                double addspeed = wishspeed - currentspeed;

                if (addspeed > 0) {
                    // 2. HIGHER ACCELERATION GAIN (280.0):
                    // Because the tracking window is smaller, we punch up the acceleration coefficient.
                    // Now, making a tiny, micro-movement with your mouse will give you maximum speed gains!
                    double accelSpeed = 280.0 * wishspeed * 0.05;
                    if (accelSpeed > addspeed) {
                        accelSpeed = addspeed;
                    }

                    nextX += wishDir.x * accelSpeed;
                    nextZ += wishDir.z * accelSpeed;
                }
            } else {
                // Smooth frictionless drift when gliding through the air with no keys pressed
                nextX *= 0.998;
                nextZ *= 0.998;
            }

            // Regular Minecraft Gravity calculations
            double nextY = currentVelocity.y;
            nextY -= 0.08; 
            nextY *= 0.98; 

            this.setDeltaMovement(nextX, nextY, nextZ);
            
            // Handle block collisions and stairs natively
            this.move(MoverType.SELF, this.getDeltaMovement());

            // Successfully override vanilla travel physics
            ci.cancel();
        }
    }
}
