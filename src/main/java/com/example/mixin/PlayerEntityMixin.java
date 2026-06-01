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

        if (!this.onGround() && !this.isInWater() && !this.isFallFlying() && !player.isSpectator() && !player.getAbilities().flying) {
            double strafe = movementInput.x;
            double forward = movementInput.z;
            float yaw = this.getYRot();

            float rad = yaw * 0.017453292F;
            float cos = (float) Math.cos(rad);
            float sin = (float) Math.sin(rad);
            
            double x = strafe * cos - forward * sin;
            double z = forward * cos + strafe * sin;
            
            double len = Math.sqrt(x * x + z * z);
            Vec3 wishDir = (len != 0) ? new Vec3(x / len, 0, z / len) : Vec3.ZERO;

            Vec3 currentVelocity = this.getDeltaMovement();
            
            double wishspeed = (strafe != 0 || forward != 0) ? 0.28 : 0;
            double currentspeed = currentVelocity.x * wishDir.x + currentVelocity.z * wishDir.z;
            double addspeed = wishspeed - currentspeed;
            
            double nextX = currentVelocity.x;
            double nextZ = currentVelocity.z;

            if (addspeed > 0) {
                double accel = 0.1 * wishspeed;
                double accelspeed = Math.min(accel, addspeed);
                
                nextX += wishDir.x * accelspeed;
                nextZ += wishDir.z * accelspeed;
            }
            
            nextX *= 0.99;
            nextZ *= 0.99;

            // --- Gravity Logic ---
            double nextY = currentVelocity.y;
            nextY -= 0.08; 
            nextY *= 0.98; 

            // DIAGNOSTIC WATERMARK: This will spam your console if the NEW code is working
            System.out.println("======> BHOP MOD IS ALIVE! Gravity Y is: " + nextY);

            this.setDeltaMovement(nextX, nextY, nextZ);
            this.move(MoverType.SELF, this.getDeltaMovement());
            ci.cancel(); 
        }
    }
}
