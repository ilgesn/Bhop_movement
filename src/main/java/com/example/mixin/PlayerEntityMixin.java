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
        // Only apply custom physics if the player is airborne, not swimming, and not using an elytra
        if (!this.onGround() && !this.isInWater() && !this.isGliding()) {
            double strafe = movementInput.x;
            double forward = movementInput.z;
            float yaw = this.getYaw();

            Vec3 wishDir = getWishDirection(strafe, forward, yaw);
            Vec3 currentVelocity = this.getDeltaMovement();
            
            // Calculate air strafe acceleration (Source Engine style)
            double wishspeed = (strafe != 0 || forward != 0) ? 0.28 : 0;
            double currentspeed = currentVelocity.x * wishDir.x + currentVelocity.z * wishDir.z;
            double addspeed = wishspeed - currentspeed;
            
            if (addspeed > 0) {
                double accel = 0.1 * wishspeed;
                double accelspeed = Math.min(accel, addspeed);
                
                Vec3 newVel = currentVelocity.add(wishDir.scale(accelspeed));
                this.setDeltaMovement(newVel.x, currentVelocity.y, newVel.z);
            }
            
            // Apply the new physics movement calculations to the player
            this.move(MoverType.SELF, this.getDeltaMovement());
            ci.cancel(); // Bypass vanilla air resistance/friction
        }
    }

    private Vec3 getWishDirection(double strafe, double forward, float yaw) {
        float rad = yaw * 0.017453292F;
        float cos = (float) Math.cos(rad);
        float sin = (float) Math.sin(rad);
        
        double x = strafe * cos - forward * sin;
        double z = forward * cos + strafe * sin;
        return new Vec3(x, 0, z).normalize();
    }
}
