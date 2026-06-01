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

            // Run calculations if ANY movement key is pressed (including W!)
            if (strafe != 0 || forward != 0) {
                float yaw = this.getYRot();
                float rad = yaw * 0.017453292F;
                float cos = (float) Math.cos(rad);
                float sin = (float) Math.sin(rad);

                // Translate WASD combination inputs to a single unified world vector
                double xDir = strafe * cos - forward * sin;
                double zDir = forward * cos + strafe * sin;

                double len = Math.sqrt(xDir * xDir + zDir * zDir);
                if (len > 0.01) {
                    // This is the exact direction the player is trying to move horizontally
                    Vec3 wishDir = new Vec3(xDir / len, 0, zDir / len);
                    Vec3 currentVelocity = this.getDeltaMovement();
                    
                    // We increase the tracking speed cap so holding W doesn't clip the calculation
                    double wishspeed = 0.45; 
                    
                    // Project current velocity onto our movement vector
                    double currentspeed = currentVelocity.x * wishDir.x + currentVelocity.z * wishDir.z;
                    double addspeed = wishspeed - currentspeed;

                    // If we are moving slower than our target direction speed, accelerate!
                    if (addspeed > 0) {
                        // Kept at a smooth, progressive build-up rate
                        double accelSpeed = 18.0 * wishspeed * 0.05; 
                        if (accelSpeed > addspeed) {
                            accelSpeed = addspeed;
                        }

                        // Seamlessly add the new speed to your current horizontal velocity
                        double newX = currentVelocity.x + wishDir.x * accelSpeed;
                        double newZ = currentVelocity.z + wishDir.z * accelSpeed;

                        // Uncapped: Speed climbs higher with every single jump
                        this.setDeltaMovement(newX, currentVelocity.y, newZ);
                    }
                }
            }
        }
    }
}
