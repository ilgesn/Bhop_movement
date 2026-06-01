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

        // Keep sneak, crawl, swim, and flight states behaving normally
        if (this.isCrouching() || this.isSwimming() || this.isVisuallyCrawling() || this.isShiftKeyDown() || player.getAbilities().flying || this.isInWater() || player.isSpectator()) {
            return; 
        }

        // --- GROUND HIT BOOSTER (CRASH-PROOF & ACCESS-FIXED) ---
        // Instead of player.jumping, we look at the vertical velocity vector. 
        // If you hit spacebar, Minecraft sets the vertical delta movement upward.
        if (this.onGround()) {
            Vec3 vel = this.getDeltaMovement();
            if (vel.y > 0.01) { 
                double horizontalSpeed = Math.sqrt(vel.x * vel.x + vel.z * vel.z);
                // Preserve landing velocity and scale it up by 15% across the jump boundary
                if (horizontalSpeed > 0.08) {
                    this.setDeltaMovement(vel.x * 1.15, vel.y, vel.z * 1.15);
                }
            }
        }

        // --- IN-AIR STRAFE SYSTEM ---
        if (!this.onGround() && !this.isFallFlying() && !this.onClimbable()) {
            
            double strafe = movementInput.x;
            double forward = movementInput.z;

            float yaw = this.getYRot();
            float rad = yaw * 0.017453292F;
            float cos = (float) Math.cos(rad);
            float sin = (float) Math.sin(rad);

            // Re-map keyboard inputs to world space coordinates
            double xDir = strafe * cos - forward * sin;
            double zDir = forward * cos + strafe * sin;
            
            double len = Math.sqrt(xDir * xDir + zDir * zDir);
            Vec3 wishDir = (len > 0.001) ? new Vec3(xDir / len, 0, zDir / len) : Vec3.ZERO;

            Vec3 currentVelocity = this.getDeltaMovement();
            double nextX = currentVelocity.x;
            double nextZ = currentVelocity.z;

            // Strict tracking cap window (0.12) makes mouse sensitivity incredibly sharp.
            // Small mouse movements translate to maximum velocity gains.
            double wishspeed = (strafe != 0 || forward != 0) ? 0.12 : 0;

            if (wishspeed > 0 && !wishDir.equals(Vec3.ZERO)) {
                double currentspeed = nextX * wishDir.x + nextZ * wishDir.z;
                double addspeed = wishspeed - currentspeed;

                if (addspeed > 0) {
                    // Massive acceleration multiplier tracks tiny camera changes instantly
                    double accelSpeed = 340.0 * wishspeed * 0.05;
                    if (accelSpeed > addspeed) {
                        accelSpeed = addspeed;
                    }

                    nextX += wishDir.x * accelSpeed;
                    nextZ += wishDir.z * accelSpeed;
                }
            } else {
                // High air-coasting momentum preservation when drifting
                nextX *= 0.998;
                nextZ *= 0.998;
            }

            // Native gravity tracking
            double nextY = currentVelocity.y;
            nextY -= 0.08; 
            nextY *= 0.98; 

            this.setDeltaMovement(nextX, nextY, nextZ);
            
            // Allow collision calculation loops so block stepping remains intact
            this.move(MoverType.SELF, this.getDeltaMovement());

            // Cancel the standard vanilla physics updates safely
            ci.cancel();
        }
    }
}
