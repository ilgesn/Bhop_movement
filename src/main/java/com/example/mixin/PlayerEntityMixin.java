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
    private void injectTrueSourcePhysics(Vec3 movementInput, CallbackInfo ci) {
        Player player = (Player) (Object) this;

        // Keep sneak, crawl, swim, and flight states behaving normally
        if (this.isCrouching() || this.isSwimming() || this.isVisuallyCrawling() || this.isShiftKeyDown() || player.getAbilities().flying || this.isInWater() || player.isSpectator()) {
            return; 
        }

        // --- GROUND HIT BOOSTER ---
        // Checks if you are on the ground and moving upward (jumping) to chain momentum smoothly
        if (this.onGround()) {
            Vec3 vel = this.getDeltaMovement();
            if (vel.y > 0.001) { 
                double horizontalSpeed = Math.sqrt(vel.x * vel.x + vel.z * vel.z);
                if (horizontalSpeed > 0.05) {
                    // Gives your landing speed a continuous 15% push forward across the jump boundary
                    this.setDeltaMovement(vel.x * 1.15, vel.y, vel.z * 1.15);
                }
            }
        }

        // --- MID-AIR STRAFE SYSTEM ---
        // By removing ci.cancel(), Minecraft natively lets you scale blocks and stairs!
        if (!this.onGround() && !this.isFallFlying() && !this.onClimbable()) {
            
            double strafe = movementInput.x;
            double forward = movementInput.z;

            // Only run the calculation if you release W and actively tap A or D to turn
            if (strafe != 0 && forward == 0) {
                float yaw = this.getYRot();
                float rad = yaw * 0.017453292F;
                float cos = (float) Math.cos(rad);
                float sin = (float) Math.sin(rad);

                // Map strafe keys into world direction coordinates
                double xDir = strafe * cos;
                double zDir = strafe * sin;
                
                double len = Math.sqrt(xDir * xDir + zDir * zDir);
                Vec3 wishDir = (len > 0.001) ? new Vec3(xDir / len, 0, zDir / len) : Vec3.ZERO;

                Vec3 currentVelocity = this.getDeltaMovement();

                // Low tracking cap (0.15) lets minor camera sweeps give maximum velocity gains
                double wishspeed = 0.15;
                double currentspeed = currentVelocity.x * wishDir.x + currentVelocity.z * wishDir.z;
                double addspeed = wishspeed - currentspeed;

                if (addspeed > 0) {
                    // Strong acceleration coefficient mimics smooth source drifting
                    double accelSpeed = 260.0 * wishspeed * 0.05;
                    if (accelSpeed > addspeed) {
                        accelSpeed = addspeed;
                    }

                    // Append the speed onto your existing horizontal velocities cleanly
                    double nextX = currentVelocity.x + wishDir.x * accelSpeed;
                    double nextZ = currentVelocity.z + wishDir.z * accelSpeed;

                    this.setDeltaMovement(nextX, currentVelocity.y, nextZ);
                }
            }
        }
    }
}
