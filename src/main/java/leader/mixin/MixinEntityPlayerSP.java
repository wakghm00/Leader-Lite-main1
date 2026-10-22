package leader.mixin;

import leader.Leader;
import leader.event.EventManager;
import leader.event.types.EventType;
import leader.events.LivingUpdateEvent;
import leader.events.MoveInputEvent;
import leader.events.PlayerUpdateEvent;
import leader.events.SlowDownEvent;
import leader.events.UpdateEvent;
import leader.management.RotationState;
import leader.module.modules.misc.AntiDebuff;
import leader.module.modules.movement.NoSlow;
import leader.module.modules.movement.NoSlow2;
import leader.module.modules.movement.Timer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.potion.Potion;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovementInput;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@SideOnly(Side.CLIENT)
@Mixin(value = {EntityPlayerSP.class}, priority = 9999)
public abstract class MixinEntityPlayerSP extends MixinEntityPlayer {
    @Unique
    private float overrideYaw = Float.NaN;
    @Unique
    private float overridePitch = Float.NaN;
    @Unique
    private float pendingYaw;
    @Unique
    private float pendingPitch;
    @Shadow
    private float lastReportedYaw;
    @Shadow
    private float lastReportedPitch;
    @Shadow
    public float renderArmYaw;
    @Shadow
    public float prevRenderArmYaw;
    @Shadow
    protected int sprintToggleTimer;
    @Shadow
    public MovementInput movementInput;

    @Inject(
            method = {"onUpdate"},
            at = {@At("HEAD")}
    )
    private void onUpdate(CallbackInfo callbackInfo) {
        if (Timer.shouldSkipLocalUpdate()) {
            callbackInfo.cancel();
            return;
        }
        if (this.worldObj.isBlockLoaded(new BlockPos(this.posX, 0.0, this.posZ))) {
            UpdateEvent event = new UpdateEvent(EventType.PRE, this.lastReportedYaw, this.lastReportedPitch, this.rotationYaw, this.rotationPitch);
            EventManager.call(event);
            RotationState.applyState(event.isRotated() && !this.isRiding(), event.getNewYaw(), event.getNewPitch(), event.getPreYaw(), event.isRotating());
            if (event.isRotated()) {
                this.pendingYaw = this.rotationYaw;
                this.pendingPitch = this.rotationPitch;
                this.overrideYaw = event.getNewYaw();
                this.overridePitch = event.getNewPitch();
            } else {
                this.pendingYaw = Float.NaN;
                this.pendingPitch = Float.NaN;
                this.overrideYaw = Float.NaN;
                this.overridePitch = Float.NaN;
            }
        }
    }

    @Inject(
            method = {"onUpdate"},
            at = {@At("RETURN")}
    )
    private void postUpdate(CallbackInfo callbackInfo) {
        if (this.worldObj.isBlockLoaded(new BlockPos(this.posX, 0.0, this.posZ))) {
            if (!Float.isNaN(this.pendingYaw) && !Float.isNaN(this.pendingPitch)) {
                this.lastReportedYaw = this.rotationYaw;
                this.lastReportedPitch = this.rotationPitch;
                this.rotationYaw = this.rotationYaw + MathHelper.wrapAngleTo180_float(this.pendingYaw - this.rotationYaw);
                this.rotationPitch = this.pendingPitch;
                this.prevRotationYaw = this.rotationYaw;
                this.prevRotationPitch = this.rotationPitch;
                this.prevRenderArmYaw = this.rotationYaw - (this.renderArmYaw - this.prevRenderArmYaw) * 2.0F;
                this.renderArmYaw = this.rotationYaw;
            }
            EventManager.call(new UpdateEvent(EventType.POST, this.lastReportedYaw, this.lastReportedPitch, this.rotationYaw, this.rotationPitch));
        }
    }

    @Redirect(
            method = {"onUpdate"},
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/entity/EntityPlayerSP;isRiding()Z"
            )
    )
    private boolean onRidding(EntityPlayerSP entityPlayerSP) {
        if (!Float.isNaN(this.overrideYaw) && !Float.isNaN(this.overridePitch)) {
            this.rotationYaw = this.overrideYaw;
            this.rotationPitch = this.overridePitch;
        }
        return entityPlayerSP.isRiding();
    }

    @Inject(
            method = {"onUpdate"},
            at = {@At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/entity/EntityPlayerSP;onUpdateWalkingPlayer()V"
            )}
    )
    private void onMotionUpdate(CallbackInfo callbackInfo) {
        EventManager.call(new PlayerUpdateEvent());
    }

    @Inject(
            method = {"onLivingUpdate"},
            at = {@At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/entity/AbstractClientPlayer;onLivingUpdate()V"
            )}
    )
    private void onLivingUpdate(CallbackInfo callbackInfo) {
        EventManager.call(new LivingUpdateEvent());
    }

    @Inject(
            method = {"onLivingUpdate"},
            at = {@At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/MovementInput;updatePlayerMoveState()V",
                    shift = At.Shift.AFTER
            )}
    )
    private void updateMove(CallbackInfo callbackInfo) {
        EventManager.call(new MoveInputEvent());
        // 移植自 Rise：使用物品时的移动减速事件
        if (Leader.moduleManager == null) {
            return;
        }
        NoSlow2 noSlow2 = (NoSlow2) Leader.moduleManager.modules.get(NoSlow2.class);
        if (noSlow2 == null || !noSlow2.shouldHandleSlowDown()) {
            return;
        }
        SlowDownEvent event = new SlowDownEvent(0.2F, 0.2F);
        EventManager.call(event);
        this.sprintToggleTimer = 0;
        if (event.isCancelled()) {
            return;
        }
        this.movementInput.moveStrafe *= event.getStrafeMultiplier();
        this.movementInput.moveForward *= event.getForwardMultiplier();
    }

    @Redirect(
            method = {"onLivingUpdate"},
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/entity/EntityPlayerSP;isUsingItem()Z"
            )
    )
    private boolean isUsing(EntityPlayerSP entityPlayerSP) {
        NoSlow noSlow = (NoSlow) Leader.moduleManager.modules.get(NoSlow.class);
        NoSlow2 noSlow2 = (NoSlow2) Leader.moduleManager.modules.get(NoSlow2.class);
        if (noSlow2 != null && noSlow2.shouldHandleSlowDown()) {
            // 减速逻辑完全由 NoSlow2 + SlowDownEvent 接管
            return false;
        }
        return (!noSlow.isEnabled() || !noSlow.isAnyActive()) && entityPlayerSP.isUsingItem();
    }

    @Redirect(
            method = {"onLivingUpdate"},
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/entity/EntityPlayerSP;isPotionActive(Lnet/minecraft/potion/Potion;)Z"
            )
    )
    private boolean checkPotion(EntityPlayerSP entityPlayerSP, Potion potion) {
        if (potion == Potion.confusion && Leader.moduleManager != null) {
            AntiDebuff antiDebuff = (AntiDebuff) Leader.moduleManager.modules.get(AntiDebuff.class);
            if (antiDebuff.isEnabled() && antiDebuff.nausea.getValue()) {
                return false;
            }
        }
        return ((IAccessorEntityLivingBase) entityPlayerSP).getActivePotionsMap().containsKey(potion.id);
    }
}
