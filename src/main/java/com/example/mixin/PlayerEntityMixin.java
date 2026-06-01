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
    private void injectSourceMovement(Vec3 movementInput, CallbackInfo ci) {
        Player player = (Player) (Object) this;

        // Ensure we only touch air movement, completely ignoring creative flight, swimming, or ladder climbing
        if (!this.onGround() && !this.isInWater() && !this.isFallFlying() && !player.isSpectator() && !player.getAbilities().flying && !this.onClimbable()) {
            
            // Get movement keys (Strafe = A/D, Forward = W/S)
            double strafe = movementInput.x;
            double forward = movementInput.z;
            
            // Calculate direction angles based on where the camera is looking
            float yaw = this.getYRot();
            float rad = yaw * 0.017453292F;
            float cos = (float) Math.cos(rad);
            float sin = (float) Math.sin(rad);
            
            // Translate the WASD keys into horizontal world coordinates
            double xDir = strafe * cos - forward * sin;
            double zDir = forward * cos + strafe * sin;
            
            double len = Math.sqrt(xDir * xDir + zDir * zDir);
            Vec3 wishDir = (len > 0.01) ? new Vec3(xDir / len, 0, zDir / len) : Vec3.ZERO;

            Vec3 currentVelocity = this.getDeltaMovement();
            
            // --- SOURCE ENGINE AIR ACCELERATION ---
            // Air wishspeed is restricted to a small cap to allow strafe acceleration without infinite speed
            double wishspeed = (strafe != 0 || forward != 0) ? 0.32 : 0;
            
            // Project current horizontal velocity onto our desired direction vector
            double currentspeed = currentVelocity.x * wishDir.x + currentVelocity.z * wishDir.z;
            double addspeed = wishspeed - currentspeed;
            
            double nextX = currentVelocity.x;
            double nextZ = currentVelocity.z;

            if (addspeed > 0 && !wishDir.equals(Vec3.ZERO)) {
                // Air accelerate scale (30.0 mimics standard Source engine air control)
                double accelSpeed = 30.0 * wishspeed * 0.05; 
                if (accelSpeed > addspeed) {
                    accelSpeed = addspeed;
                }
                
                nextX += wishDir.x * accelSpeed;
                nextZ += wishDir.z * accelSpeed;
            }

            // Apply standard gravity and vertical drag matching Minecraft's engine
            double nextY = currentVelocity.y;
            nextY -= 0.08;
            nextY *= 0.98;

            // Set our newly calculated vectors
            this.setDeltaMovement(nextX, nextY, nextZ);
            
            // Use the native travel processing loop for the move call to properly calculate step-height block collisions!
            this.move(MoverType.SELF, this.getDeltaMovement());
            
            // Stop vanilla from overriding our calculations
            ci.cancel(); 
        }
    }
}
