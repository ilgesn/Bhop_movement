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

        // ONLY apply changes while mid-air (not flying, swimming, or on a ladder)
        if (!this.onGround() && !this.isInWater() && !this.isFallFlying() && !player.isSpectator() && !player.getAbilities().flying && !this.onClimbable()) {
            
            double strafe = movementInput.x;
            double forward = movementInput.z;

            // Only calculate if the player is actively holding a movement key (W, A, S, or D)
            if (strafe != 0 || forward != 0) {
                float yaw = this.getYRot();
                float rad = yaw * 0.017453292F;
                float cos = (float) Math.cos(rad);
                float sin = (float) Math.sin(rad);

                // Calculate the direction the player WANTS to go based on keys pressed
                double xDir = strafe * cos - forward * sin;
                double zDir = forward * cos + strafe * sin;

                double len = Math.sqrt(xDir * xDir + zDir * zDir);
                if (len > 0.01) {
                    Vec3 wishDir = new Vec3(xDir / len, 0, zDir / len);

                    // Get current horizontal velocity
                    Vec3 currentVelocity = this.getDeltaMovement();
                    
                    // Caps the wishspeed to match traditional source movement limits
                    double wishspeed = 0.28; 
                    
                    // See how much of our current speed aligns with our target direction
                    double currentspeed = currentVelocity.x * wishDir.x + currentVelocity.z * wishDir.z;
                    double addspeed = wishspeed - currentspeed;

                    if (addspeed > 0) {
                        // 32.0 mimics the high air-acceleration of standard Counter-Strike / Quake
                        double accelSpeed = 32.0 * wishspeed * 0.05; 
                        if (accelSpeed > addspeed) {
                            accelSpeed = addspeed;
                        }

                        // Gently add the acceleration to the existing velocity vectors
                        double newX = currentVelocity.x + wishDir.x * accelSpeed;
                        double newZ = currentVelocity.z + wishDir.z * accelSpeed;

                        // Apply the updated speed without touching the Y-axis (leaving gravity untouched)
                        this.setDeltaMovement(newX, currentVelocity.y, newZ);
                    }
                }
            }
        }
    }
}
