package leader.module.modules.movement;

import com.google.common.base.CaseFormat;
import leader.Leader;
import leader.event.EventTarget;
import leader.event.types.EventType;
import leader.event.types.Priority;
import leader.events.LivingUpdateEvent;
import leader.events.MoveInputEvent;
import leader.events.StrafeEvent;
import leader.events.TickEvent;
import leader.events.UpdateEvent;
import leader.management.RotationState;
import leader.mixin.IAccessorEntity;
import leader.mixin.IAccessorMinecraft;
import leader.module.Module;
import leader.module.modules.combat.KillAura;
import leader.module.modules.player.Scaffold;
import leader.property.properties.BooleanProperty;
import leader.property.properties.FloatProperty;
import leader.property.properties.IntProperty;
import leader.property.properties.ModeProperty;
import leader.property.properties.PercentProperty;
import leader.util.MoveUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;

public class Speed extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private final ModeProperty mode = new ModeProperty("mode", 0, new String[]{"TimerBalance", "Normal"});
    public final FloatProperty timerBoostMultiplier = new FloatProperty("timer-boost-multiplier", 0.5F, 0.1F, 1.0F, () -> this.mode.getValue() == 0);
    public final IntProperty lowTimerTicks = new IntProperty("low-timer-ticks", 6, 1, 10, () -> this.mode.getValue() == 0);
    public final BooleanProperty rotation = new BooleanProperty("rotation", false, () -> this.mode.getValue() == 0);
    public final FloatProperty multiplier = new FloatProperty("multiplier", 1.0F, 0.0F, 10.0F, () -> this.mode.getValue() == 1);
    public final FloatProperty friction = new FloatProperty("friction", 1.0F, 0.0F, 10.0F, () -> this.mode.getValue() == 1);
    public final PercentProperty strafe = new PercentProperty("strafe", 0, () -> this.mode.getValue() == 1);

    private int ticks;
    private boolean finished;
    private float yaw;

    public Speed() {
        super("Speed", false);
    }

    public boolean isTimerBalanceActive() {
        return isEnabled() && mode.getValue() == 0;
    }

    private boolean canBoost() {
        Scaffold scaffold = (Scaffold) Leader.moduleManager.modules.get(Scaffold.class);
        return scaffold != null && !scaffold.isEnabled() && MoveUtil.isForwardPressed()
                && mc.thePlayer.getFoodStats().getFoodLevel() > 6
                && !mc.thePlayer.isSneaking() && !mc.thePlayer.isInWater()
                && !mc.thePlayer.isInLava() && !((IAccessorEntity) mc.thePlayer).getIsInWeb();
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (event.getType() != EventType.PRE) return;
        if (!isEnabled() || mode.getValue() != 0 || !canBoost() || mc.thePlayer.onGround) {
            resetTimer();
            return;
        }
        if (!finished && ticks < lowTimerTicks.getValue() && mc.thePlayer.motionY < 0.0D) {
            ticks++;
            ((IAccessorMinecraft) mc).getTimer().timerSpeed = timerBoostMultiplier.getValue();
            if (ticks == lowTimerTicks.getValue()) finished = true;
        }
        if (finished && ticks > 0) {
            ticks--;
            ((IAccessorMinecraft) mc).getTimer().timerSpeed = 2.0F;
            if (ticks == 0) resetTimer();
        }
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        KillAura killAura = (KillAura) Leader.moduleManager.modules.get(KillAura.class);
        if (!isEnabled() || mode.getValue() != 0 || event.getType() != EventType.PRE
                || !rotation.getValue() || !canBoost() || (killAura != null && killAura.isEnabled())) return;
        if (!mc.thePlayer.onGround && mc.gameSettings.keyBindForward.isKeyDown()
                && !mc.gameSettings.keyBindLeft.isKeyDown() && !mc.gameSettings.keyBindRight.isKeyDown()) {
            yaw = -45.0F;
        } else {
            yaw = getYawOffsetFromKeys();
        }
        event.setRotation(mc.thePlayer.rotationYaw - yaw, mc.thePlayer.rotationPitch, 2);
        event.setPervRotation(mc.thePlayer.rotationYaw - yaw, 2);
    }

    private float getYawOffsetFromKeys() {
        KeyBinding forward = mc.gameSettings.keyBindForward;
        KeyBinding back = mc.gameSettings.keyBindBack;
        KeyBinding left = mc.gameSettings.keyBindLeft;
        KeyBinding right = mc.gameSettings.keyBindRight;
        if (forward.isKeyDown() && left.isKeyDown()) return 45.0F;
        if (forward.isKeyDown() && right.isKeyDown()) return -45.0F;
        if (back.isKeyDown() && left.isKeyDown()) return 135.0F;
        if (back.isKeyDown() && right.isKeyDown()) return -135.0F;
        if (back.isKeyDown()) return 180.0F;
        if (left.isKeyDown()) return 90.0F;
        if (right.isKeyDown()) return -90.0F;
        return 0.0F;
    }

    @EventTarget
    public void onMove(MoveInputEvent event) {
        KillAura killAura = (KillAura) Leader.moduleManager.modules.get(KillAura.class);
        if (isEnabled() && mode.getValue() == 0 && rotation.getValue() && canBoost()
                && (killAura == null || !killAura.isEnabled()) && RotationState.isActived()
                && RotationState.getPriority() == 2.0F && MoveUtil.isForwardPressed()) {
            MoveUtil.fixStrafe(RotationState.getSmoothedYaw());
        }
    }

    @EventTarget(Priority.LOW)
    public void onStrafe(StrafeEvent event) {
        if (mode.getValue() != 1 || !isEnabled() || !canBoost()) return;
        if (mc.thePlayer.onGround) {
            mc.thePlayer.motionY = 0.42F;
            MoveUtil.setSpeed(MoveUtil.getJumpMotion() * multiplier.getValue(), MoveUtil.getMoveYaw());
            return;
        }
        if (friction.getValue() != 1.0F) event.setFriction(event.getFriction() * friction.getValue());
        if (strafe.getValue() > 0) {
            double speed = MoveUtil.getSpeed();
            MoveUtil.setSpeed(speed * (100.0D - strafe.getValue().intValue()) / 100.0D, MoveUtil.getDirectionYaw());
            MoveUtil.addSpeed(speed * strafe.getValue().intValue() / 100.0D, MoveUtil.getMoveYaw());
            MoveUtil.setSpeed(speed);
        }
    }

    @EventTarget(Priority.LOW)
    public void onLivingUpdate(LivingUpdateEvent event) {
        if (mode.getValue() == 1 && isEnabled() && canBoost()) mc.thePlayer.movementInput.jump = false;
    }

    @Override
    public void onDisabled() {
        resetTimer();
    }

    private void resetTimer() {
        ((IAccessorMinecraft) mc).getTimer().timerSpeed = 1.0F;
        ticks = 0;
        finished = false;
    }

    @Override
    public String[] getSuffix() {
        return new String[]{CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, mode.getModeString())};
    }
}