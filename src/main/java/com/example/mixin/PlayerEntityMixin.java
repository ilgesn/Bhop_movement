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

        // CRAWL/SNEAK FIX: If the player is crouching, crawling, swimming, or flying, completely ignore this code 
        if (this.isCresting() || this.isVisuallyCrawling() || this.isShiftKeyDown() || player.getAbilities().flying || this.isInWater() || player.isSpectator()) {
            return; 
        }

        // Run ONLY when airborne
        if (!this.onGround() && !this.isFallFlying() && !this.onClimbable()) {
            
            double strafe = movementInput.x;
            double forward = movementInput.z;

            float yaw = this.getYRot();
            float rad = yaw * 0.017453292F;
            float cos = (float) Math.cos(rad);
            float sin = (float) Math.sin(rad);

            // Translate keys to absolute horizontal world coordinates
            double xDir = strafe * cos - forward * sin;
            double zDir = forward * cos + strafe * sin;
            
            double len = Math.sqrt(xDir * xDir + zDir * zDir);
            Vec3 wishDir = (len > 0.001) ? new Vec3(xDir / len, 0, zDir / len) : Vec3.ZERO;

            Vec3 currentVelocity = this.getDeltaMovement();
            double nextX = currentVelocity.x;
            double nextZ = currentVelocity.z;

            // Tight wishspeed cap allows smaller angles to build speed
            double wishspeed = (strafe != 0 || forward != 0) ? 0.26 : 0;

            if (wishspeed > 0 && !wishDir.equals(Vec3.ZERO)) {
                double currentspeed = nextX * wishDir.x + nextZ * wishDir.z;
                double addspeed = wishspeed - currentspeed;

                if (addspeed > 0) {
                    // SMOOTH STRAFE MODIFIER: Increased scaling parameter 
                    // This lets you gain optimal speed with minor camera adjustments
                    double accelSpeed = 165.0 * wishspeed * 0.05;
                    if (accelSpeed > addspeed) {
                        accelSpeed = addspeed;
                    }

                    nextX += wishDir.x * accelSpeed;
                    nextZ += wishDir.z * accelSpeed;
                }
            } else {
                // Smooth frictionless drift when gliding
                nextX *= 0.998;
                nextZ *= 0.998;
            }

            // Regular Minecraft Gravity calculations
            double nextY = currentVelocity.y;
            nextY -= 0.08; 
            nextY *= 0.98; 

            this.setDeltaMovement(nextX, nextY, nextZ);
            
            // Processes collision steps over block edges natively
            this.move(MoverType.SELF, this.getDeltaMovement());

            // Kill the vanilla physics method before it overrides us
            ci.cancel();
        }
    }
}
