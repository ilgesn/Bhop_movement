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

        // Run ONLY when airborne, ignoring creative flight, swimming, ladders, or spectators
        if (!this.onGround() && !this.isInWater() && !this.isFallFlying() && !player.isSpectator() && !player.getAbilities().flying && !this.onClimbable()) {
            
            // 1. Extract raw keyboard inputs (A/D = strafe, W/S = forward)
            double strafe = movementInput.x;
            double forward = movementInput.z;

            // 2. Translate those inputs relative to where your camera is pointing
            float yaw = this.getYRot();
            float rad = yaw * 0.017453292F;
            float cos = (float) Math.cos(rad);
            float sin = (float) Math.sin(rad);

            double xDir = strafe * cos - forward * sin;
            double zDir = forward * cos + strafe * sin;
            
            double len = Math.sqrt(xDir * xDir + zDir * zDir);
            Vec3 wishDir = (len > 0.001) ? new Vec3(xDir / len, 0, zDir / len) : Vec3.ZERO;

            Vec3 currentVelocity = this.getDeltaMovement();
            double nextX = currentVelocity.x;
            double nextZ = currentVelocity.z;

            // 3. THE CS:GO AIR-ACCELERATE FORMULA
            // CS:GO caps your air "wishspeed" to a very low value (approx 30 units, or 0.30 here).
            // This is the secret to why you don't fly sideways instantly, but smoothly curve!
            double wishspeed = (strafe != 0 || forward != 0) ? 0.30 : 0;

            if (wishspeed > 0 && !wishDir.equals(Vec3.ZERO)) {
                // Determine our current speed along our intended direction vector
                double currentspeed = nextX * wishDir.x + nextZ * wishDir.z;
                
                // See how much extra speed we are allowed to add before hitting the air limit
                double addspeed = wishspeed - currentspeed;

                if (addspeed > 0) {
                    // Air Accelerate constant (Set to 120.0 to match CS:GO / Half-Life bunnyhop servers)
                    // This creates a smooth, progressive build-up curve over time instead of a scuffy jerk.
                    double accelSpeed = 120.0 * wishspeed * 0.05;
                    if (accelSpeed > addspeed) {
                        accelSpeed = addspeed;
                    }

                    nextX += wishDir.x * accelSpeed;
                    nextZ += wishDir.z * accelSpeed;
                }
            } else {
                // If no keys are held, preserve 99.8% of horizontal momentum (True frictionless glide)
                nextX *= 0.998;
                nextZ *= 0.998;
            }

            // 4. Native Minecraft Gravity Processing
            double nextY = currentVelocity.y;
            nextY -= 0.08; // Gravity acceleration drop per frame
            nextY *= 0.98; // Air resistance cap on falling speed

            // 5. Commit vectors and execute collision safety loops
            this.setDeltaMovement(nextX, nextY, nextZ);
            
            // This natively processes step-height blocks so you can scale hurdles smoothly
            this.move(MoverType.SELF, this.getDeltaMovement());

            // 6. Hard cancel vanilla physics so client and server don't fight
            ci.cancel();
        }
    }
}
