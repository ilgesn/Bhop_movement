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

@Mixin(LivingEntity.class)
public abstract class PlayerEntityMixin extends LivingEntity {

    protected PlayerEntityMixin(EntityType<? extends LivingEntity> entityType, Level level) {
        super(entityType, level);
    }

    @Inject(method = "travel", at = @At("HEAD"), cancellable = true)
    private void injectSourceMovement(Vec3 movementInput, CallbackInfo ci) {
        // Force the mixin to only apply if the entity running this code is actually a player
        if (!((Object) this instanceof Player player)) {
            return;
        }

        // Apply ONLY while airborne (ignoring creative flight, swimming, spectator, and ladders)
        if (!this.onGround() && !this.isInWater() && !this.isFallFlying() && !player.isSpectator() && !player.getAbilities().flying && !this.onClimbable()) {
            
            double strafe = movementInput.x;
            double forward = movementInput.z;

            float yaw = this.getYRot();
            float rad = yaw * 0.017453292F;
            float cos = (float) Math.cos(rad);
            float sin = (float) Math.sin(rad);

            // Calculate movement intent direction based on camera angle
            double xDir = strafe * cos - forward * sin;
            double zDir = forward * cos + strafe * sin;
            double len = Math.sqrt(xDir * xDir + zDir * zDir);
            
            Vec3 wishDir = (len > 0.01) ? new Vec3(xDir / len, 0, zDir / len) : Vec3.ZERO;
            Vec3 currentVelocity = this.getDeltaMovement();

            // Source Engine air limit tracking
            double wishspeed = (strafe != 0 || forward != 0) ? 0.30 : 0;
            
            double nextX = currentVelocity.x;
            double nextZ = currentVelocity.z;

            if (wishspeed > 0 && !wishDir.equals(Vec3.ZERO)) {
                double currentspeed = currentVelocity.x * wishDir.x + currentVelocity.z * wishDir.z;
                double addspeed = wishspeed - currentspeed;

                if (addspeed > 0) {
                    // Increased to 35.0 to give you an unmistakable, distinct speed boost when strafing
                    double accelSpeed = 35.0 * wishspeed * 0.05; 
                    if (accelSpeed > addspeed) {
                        accelSpeed = addspeed;
                    }
                    nextX += wishDir.x * accelSpeed;
                    nextZ += wishDir.z * accelSpeed;
                }
            } else {
                // Smooth coasting friction when gliding with no keys held (retains 99.5% speed)
                nextX *= 0.995;
                nextZ *= 0.995;
            }

            // Standard Minecraft Gravity loop
            double nextY = currentVelocity.y;
            nextY -= 0.08; 
            nextY *= 0.98; 

            // Apply unified vectors to the entity configuration
            this.setDeltaMovement(nextX, nextY, nextZ);
            
            // Run collision checks so blocks can be stepped over natively
            this.move(MoverType.SELF, this.getDeltaMovement());
            
            // Cancel vanilla physics completely on BOTH client and server screens
            ci.cancel(); 
        }
    }
}
