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

    @Inject(method = "aiStep", at = @At("HEAD"))
    private void beforeAIStep(CallbackInfo ci) {
        Player player = (Player) (Object) this;

        // Only run while airborne, ignoring water, ladders, and creative flight
        if (!this.onGround() && !this.isInWater() && !this.isFallFlying() && !player.isSpectator() && !player.getAbilities().flying && !this.onClimbable()) {
            
            // Re-apply a tiny fraction of velocity to counter Minecraft's brutal air-damping loop
            // This stops the game from forcefully slowing you down when W is released!
            Vec3 vel = this.getDeltaMovement();
            double horizontalSpeed = Math.sqrt(vel.x * vel.x + vel.z * vel.z);

            if (horizontalSpeed > 0.05) {
                // Counteract the default 0.91 air drag by multiplying your horizontal speed back up slightly
                this.setDeltaMovement(vel.x * 1.085, vel.y, vel.z * 1.085);
            }
        }
    }

    @Inject(method = "travel", at = @At("HEAD"))
    private void onTravelHead(Vec3 movementInput, CallbackInfo ci) {
        Player player = (Player) (Object) this;

        if (!this.onGround() && !this.isInWater() && !this.isFallFlying() && !player.isSpectator() && !player.getAbilities().flying && !this.onClimbable()) {
            double strafe = movementInput.x;
            double forward = movementInput.z;

            // Only accelerate if they are actively strafing (A or D) and NOT holding W
            if (strafe != 0 && forward == 0) {
                float yaw = this.getYRot();
                float rad = yaw * 0.017453292F;
                float cos = (float) Math.cos(rad);
                float sin = (float) Math.sin(rad);

                // Target direction based on A or D press
                double xDir = strafe * cos;
                double zDir = strafe * sin;
                double len = Math.sqrt(xDir * xDir + zDir * zDir);

                if (len > 0.01) {
                    Vec3 wishDir = new Vec3(xDir / len, 0, zDir / len);
                    Vec3 currentVelocity = this.getDeltaMovement();

                    // Classic Source Engine air control limits
                    double wishspeed = 0.30;
                    double currentspeed = currentVelocity.x * wishDir.x + currentVelocity.z * wishDir.z;
                    double addspeed = wishspeed - currentspeed;

                    if (addspeed > 0) {
                        // Snappy air acceleration coefficient
                        double accelSpeed = 25.0 * wishspeed * 0.05;
                        if (accelSpeed > addspeed) {
                            accelSpeed = addspeed;
                        }

                        // Gently apply the new horizontal vectors
                        this.setDeltaMovement(
                            currentVelocity.x + wishDir.x * accelSpeed,
                            currentVelocity.y,
                            currentVelocity.z + wishDir.z * accelSpeed
                        );
                    }
                }
            }
        }
    }
}
