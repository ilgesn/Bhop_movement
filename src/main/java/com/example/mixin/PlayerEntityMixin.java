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

        // Apply only when airborne, not flying, swimming, or climbing
        if (!this.onGround() && !this.isInWater() && !this.isFallFlying() && !player.isSpectator() && !player.getAbilities().flying && !this.onClimbable()) {
            
            double strafe = movementInput.x;
            double forward = movementInput.z;

            // Only run calculations if a movement key is actively held
            if (strafe != 0 || forward != 0) {
                float yaw = this.getYRot();
                float rad = yaw * 0.017453292F;
                float cos = (float) Math.cos(rad);
                float sin = (float) Math.sin(rad);

                // Translate movement inputs to world vectors based on camera angle
                double xDir = strafe * cos - forward * sin;
                double zDir = forward * cos + strafe * sin;

                double len = Math.sqrt(xDir * xDir + zDir * zDir);
                if (len > 0.01) {
                    Vec3 wishDir = new Vec3(xDir / len, 0, zDir / len);
                    Vec3 currentVelocity = this.getDeltaMovement();
                    
                    // Traditional Source wishspeed tracking cap per frame
                    double wishspeed = 0.30; 
                    
                    // Measure current velocity projection along our intended direction
                    double currentspeed = currentVelocity.x * wishDir.x + currentVelocity.z * wishDir.z;
                    double addspeed = wishspeed - currentspeed;

                    if (addspeed > 0) {
                        // ACCELERATION LOWERED: Dropped down heavily for a smooth, progressive build-up
                        double accelSpeed = 15.0 * wishspeed * 0.05; 
                        if (accelSpeed > addspeed) {
                            accelSpeed = addspeed;
                        }

                        // Apply the new horizontal velocities smoothly
                        double newX = currentVelocity.x + wishDir.x * accelSpeed;
                        double newZ = currentVelocity.z + wishDir.z * accelSpeed;

                        // NO SPEED CAP: Total horizontal speed is allowed to grow infinitely!
                        this.setDeltaMovement(newX, currentVelocity.y, newZ);
                    }
                }
            }
        }
    }
}
