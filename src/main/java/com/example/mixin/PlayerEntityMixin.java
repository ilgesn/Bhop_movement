package com.example.mixin;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
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

    @Inject(method = "travel", at = @At("HEAD"))
    private void injectSourceMovement(Vec3 movementInput, CallbackInfo ci) {
        Player player = (Object) this instanceof Player ? (Player) (Object) this : null;
        if (player == null) return;

        // Apply only when airborne and moving
        if (!this.onGround() && !this.isInWater() && !this.isFallFlying() && !player.isSpectator() && !player.getAbilities().flying && !this.onClimbable()) {
            
            double strafe = movementInput.x;
            double forward = movementInput.z;

            if (strafe != 0 || forward != 0) {
                float yaw = this.getYRot();
                float rad = yaw * 0.017453292F;
                float cos = (float) Math.cos(rad);
                float sin = (float) Math.sin(rad);

                // Find the exact direction the camera/keys are pointing
                double xDir = strafe * cos - forward * sin;
                double zDir = forward * cos + strafe * sin;

                double len = Math.sqrt(xDir * xDir + zDir * zDir);
                if (len > 0.01) {
                    Vec3 wishDir = new Vec3(xDir / len, 0, zDir / len);
                    Vec3 currentVelocity = this.getDeltaMovement();
                    
                    // Standard Source Engine limits: air wishspeed cap
                    double wishspeed = 0.35; 
                    
                    // See how fast we are currently moving in our desired direction
                    double currentspeed = currentVelocity.x * wishDir.x + currentVelocity.z * wishDir.z;
                    double addspeed = wishspeed - currentspeed;

                    if (addspeed > 0) {
                        // High acceleration value (70.0) forces the speed to build up quickly when turning
                        double accelSpeed = 70.0 * wishspeed * 0.05; 
                        if (accelSpeed > addspeed) {
                            accelSpeed = addspeed;
                        }

                        double newX = currentVelocity.x + wishDir.x * accelSpeed;
                        double newZ = currentVelocity.z + wishDir.z * accelSpeed;

                        // Calculate the absolute horizontal speed we just achieved
                        double totalHorizontalSpeed = Math.sqrt(newX * newX + newZ * newZ);
                        
                        // HARD CAP: Keeps you from accelerating to infinite light-speed crashing the game
                        double maxBhopSpeed = 1.5; 
                        if (totalHorizontalSpeed > maxBhopSpeed) {
                            newX = (newX / totalHorizontalSpeed) * maxBhopSpeed;
                            newZ = (newZ / totalHorizontalSpeed) * maxBhopSpeed;
                        }

                        // Update velocity
                        this.setDeltaMovement(newX, currentVelocity.y, newZ);
                    }
                }
            }
        }
    }
}
