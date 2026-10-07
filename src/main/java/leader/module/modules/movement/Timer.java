package leader.module.modules.movement;

import leader.Leader;
import leader.event.EventTarget;
import leader.event.types.EventType;
import leader.events.TickEvent;
import leader.mixin.IAccessorMinecraft;
import leader.module.Module;
import leader.property.properties.FloatProperty;
import net.minecraft.client.Minecraft;

public class Timer extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final FloatProperty speed = new FloatProperty("speed", 1.0F, 0.0F, 2.0F);

    public Timer() {
        super("Timer", false);
    }

    public float getConfiguredSpeed() {
        return speed.getValue();
    }

    @Override
    public String[] getSuffix() {
        return new String[]{String.valueOf(speed.getValue())};
    }

    @Override
    public void onEnabled() {
        applyTimerSpeed();
    }

    @Override
    public void onDisabled() {
        resetTimerSpeed();
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (isEnabled() && event.getType() == EventType.PRE) {
            applyTimerSpeed();
        }
    }

    private void applyTimerSpeed() {
        Speed speedModule = (Speed) Leader.moduleManager.modules.get(Speed.class);
        if (speedModule != null && speedModule.isTimerBalanceActive()) {
            return;
        }
        if (mc.thePlayer != null && mc.theWorld != null) {
            ((IAccessorMinecraft) mc).getTimer().timerSpeed = speed.getValue();
        } else {
            resetTimerSpeed();
        }
    }

    private void resetTimerSpeed() {
        ((IAccessorMinecraft) mc).getTimer().timerSpeed = 1.0F;
    }

    public static boolean shouldSkipLocalUpdate() {
        if (Leader.moduleManager == null) {
            return false;
        }
        Timer timer = (Timer) Leader.moduleManager.modules.get(Timer.class);
        return timer != null && timer.isEnabled() && timer.speed.getValue() <= 0.0F
            && mc.thePlayer != null && mc.theWorld != null;
    }
}
