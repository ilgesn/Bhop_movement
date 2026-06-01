
package com.example.mixin;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin extends LivingEntity {

    protected PlayerEntityMixin(EntityType<? extends LivingEntity> entityType, World world) {
        super(entityType, world);
    }

    @Inject(method = "travel", at = @At("HEAD"), cancellable = true)
    private void injectSourceMovement(Vec3d movementInput, CallbackInfo ci) {
        // Run this calculations only if the player is in mid-air
        if (!this.isOnGround() && !this.isTouchingWater() && !this.isFallFlying()) {
            double forward = movementInput.z;
            double strafe = movementInput.x;
            
            if (forward == 0 && strafe == 0) return;

            float yaw = this.getYaw();
            Vec3d wishDir = getWishDirection(strafe, forward, yaw);

            double wishSpeed = 1.2; // Speed cap setting
            double airAccelerate = 15.0; // Air-strafe acceleration rate

            Vec3d currentVelocity = this.getVelocity();
            double currentSpeedInWishDir = currentVelocity.dotProduct(wishDir);
            double addSpeed = wishSpeed - currentSpeedInWishDir;
            
            if (addSpeed > 0) {
                double accelSpeed = airAccelerate * wishSpeed * 0.05; 
                if (accelSpeed > addSpeed) {
                    accelSpeed = addSpeed;
                }
                this.setVelocity(currentVelocity.add(wishDir.multiply(accelSpeed)));
            }

            // Apply standard downward gravity physics manually since we interrupted the method
            Vec3d vel = this.getVelocity();
            this.setVelocity(vel.x, vel.y - 0.08, vel.z); 
            this.move(net.minecraft.entity.MovementType.SELF, this.getVelocity());
            ci.cancel(); // Halt default vanilla air physics calculations
        }
    }

    private Vec3d getWishDirection(double strafe, double forward, float yaw) {
        double radians = Math.toRadians(yaw);
        double sin = Math.sin(radians);
        double cos = Math.cos(radians);
        double x = strafe * cos - forward * sin;
        double z = forward * cos + strafe * sin;
        return new Vec3d(x, 0, z).normalize();
    }
}
