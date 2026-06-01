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
        Player player = (Object) this instanceof Player ? (Player) (Object) this : null;
        if (player == null) return;

        // ONLY override physics while mid-air
        if (!this.onGround() && !this.isInWater() && !this.isFallFlying() && !player.isSpectator() && !player.getAbilities().flying && !this.onClimbable()) {
            
            double strafe = movementInput.x;
            double forward = movementInput.z;

            float yaw = this.getYRot();
            float rad = yaw * 0.017453292F;
            float cos = (float) Math.cos(rad);
            float sin = (float) Math.sin(rad);

            // 1. Calculate the direction the player WANTS to go based on keys held
            double xDir = strafe * cos - forward * sin;
            double zDir = forward * cos + strafe * sin;
            double len = Math.sqrt(xDir * xDir + zDir * zDir);
            
            Vec3 wishDir = (len > 0.01) ? new Vec3(xDir / len, 0, zDir / len) : Vec3.ZERO;
            Vec3 currentVelocity = this.getDeltaMovement();

            // 2. Set air control limits (0.30 wishspeed matches Counter-Strike perfectly)
            double wishspeed = (strafe != 0 || forward != 0) ? 0.30 : 0;
            
            double nextX = currentVelocity.x;
            double nextZ = currentVelocity.z;

            // 3. Apply smooth air acceleration ONLY if they are pressing keys
            if (wishspeed > 0 && !wishDir.equals(Vec3.ZERO)) {
                double currentspeed = currentVelocity.x * wishDir.x + currentVelocity.z * wishDir.z;
                double addspeed = wishspeed - currentspeed;

                if (addspeed > 0) {
                    // Lowered acceleration modifier (20.0) makes the turning feel incredibly buttery and smooth
                    double accelSpeed = 20.0 * wishspeed * 0.05; 
                    if (accelSpeed > addspeed) {
                        accelSpeed = addspeed;
                    }
                    nextX += wishDir.x * accelSpeed;
                    nextZ += wishDir.z * accelSpeed;
                }
            } else {
                // NO KEYS HELD: Instead of stopping instantly, we preserve 99% of your speed per frame!
                // This lets you coast through the air seamlessly when you let go of W.
                nextX *= 0.995;
                nextZ *= 0.995;
            }

            // 4. Preserve standard Minecraft gravity calculations
            double nextY = currentVelocity.y;
            nextY -= 0.08; // Normal gravity pull
            nextY *= 0.98; // Normal terminal velocity drag

            // Apply our unified, smooth vectors
            this.setDeltaMovement(nextX, nextY, nextZ);
            
            // Run native collision detection so you can still step over blocks cleanly
            this.move(MoverType.SELF, this.getDeltaMovement());
            
            // CRITICAL: Cancel vanilla travel physics so Minecraft can't slam the brakes on us!
            ci.cancel(); 
        }
    }
}
