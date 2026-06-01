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

    // --- 1. GROUND TO AIR SPEED PRESERVATION ---
    // This hooks into the exact moment you press spacebar to jump.
    // Instead of resetting your speed to default, it grabs your current velocity and flings you forward!
    @Inject(method = "jumpFromGround", at = @At("TAIL"))
    private void onJumpBoost(CallbackInfo ci) {
        Player player = (Player) (Object) this;
        if (this.isCrouching() || this.isSwimming() || this.isInWater() || player.getAbilities().flying) return;

        Vec3 vel = this.getDeltaMovement();
        double horizontalSpeed = Math.sqrt(vel.x * vel.x + vel.z * vel.z);

        // If we already have momentum from a previous jump, give it a stacking 10% boost forward!
        if (horizontalSpeed > 0.1) {
            this.setDeltaMovement(vel.x * 1.12, vel.y, vel.z * 1.12);
        }
    }

    // --- 2. AIR STRAFE MATHEMATICS ---
    @Inject(method = "travel", at = @At("HEAD"), cancellable = true)
    private void injectTrueSourcePhysics(Vec3 movementInput, CallbackInfo ci) {
        Player player = (Player) (Object) this;

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

            // Turn keyboard inputs into vector coordinates
            double xDir = strafe * cos - forward * sin;
            double zDir = forward * cos + strafe * sin;
            
            double len = Math.sqrt(xDir * xDir + zDir * zDir);
            Vec3 wishDir = (len > 0.001) ? new Vec3(xDir / len, 0, zDir / len) : Vec3.ZERO;

            Vec3 currentVelocity = this.getDeltaMovement();
            double nextX = currentVelocity.x;
            double nextZ = currentVelocity.z;

            // Tight wishspeed tracking window so minor camera movements register
            double wishspeed = (strafe != 0 || forward != 0) ? 0.22 : 0;

            if (wishspeed > 0 && !wishDir.equals(Vec3.ZERO)) {
                double currentspeed = nextX * wishDir.x + nextZ * wishDir.z;
                double addspeed = wishspeed - currentspeed;

                if (addspeed > 0) {
                    // Strong acceleration coefficient to ensure the vector snaps cleanly
                    double accelSpeed = 240.0 * wishspeed * 0.05;
                    if (accelSpeed > addspeed) {
                        accelSpeed = addspeed;
                    }

                    nextX += wishDir.x * accelSpeed;
                    nextZ += wishDir.z * accelSpeed;
                }
            } else {
                // Low air friction keeps you from losing speed mid-air when W is released
                nextX *= 0.996;
                nextZ *= 0.996;
            }

            // Gravity loop
            double nextY = currentVelocity.y;
            nextY -= 0.08; 
            nextY *= 0.98; 

            this.setDeltaMovement(nextX, nextY, nextZ);
            this.move(MoverType.SELF, this.getDeltaMovement());
            ci.cancel();
        }
    }
}
