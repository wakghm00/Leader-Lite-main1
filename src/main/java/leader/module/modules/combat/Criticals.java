package leader.module.modules.combat;

import leader.Leader;
import leader.event.EventTarget;
import leader.event.types.EventType;
import leader.events.AttackEvent;
import leader.events.PacketEvent;
import leader.mixin.IAccessorC03PacketPlayer;
import leader.module.Module;
import leader.module.modules.movement.Fly;
import leader.property.properties.FloatProperty;
import leader.property.properties.IntProperty;
import leader.property.properties.ModeProperty;
import leader.util.PacketUtil;
import leader.util.TimerUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.network.play.client.C03PacketPlayer;

public class Criticals extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final String[] MODES = {
            "Packet", "NCPPacket", "OldBlocksMC", "OldBlocksMC2", "NoGround",
            "Hop", "TPHop", "Jump", "LowJump", "CustomMotion", "Visual"
    };

    public final ModeProperty mode = new ModeProperty("mode", 0, MODES);
    public final IntProperty delay = new IntProperty("delay", 0, 0, 500);
    public final IntProperty hurtTime = new IntProperty("hurt-time", 10, 0, 10);
    public final FloatProperty customMotionY = new FloatProperty("custom-y", 0.2F, 0.01F, 0.42F);
    private final TimerUtil timer = new TimerUtil();

    public Criticals() {
        super("Criticals", false);
    }

    @EventTarget
    public void onAttack(AttackEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null || mc.theWorld == null
                || !(event.getTarget() instanceof EntityLivingBase)) {
            return;
        }

        EntityLivingBase target = (EntityLivingBase) event.getTarget();
        if (!mc.thePlayer.onGround || mc.thePlayer.isUsingItem() || mc.thePlayer.isInWater()
                || mc.thePlayer.isInLava() || mc.thePlayer.ridingEntity != null
                || target.hurtTime > this.hurtTime.getValue()
                || !this.timer.hasTimeElapsed(this.delay.getValue().longValue())) {
            return;
        }

        Fly fly = (Fly) Leader.moduleManager.getModule(Fly.class);
        if (fly != null && fly.isEnabled()) {
            return;
        }

        double x = mc.thePlayer.posX;
        double y = mc.thePlayer.posY;
        double z = mc.thePlayer.posZ;
        boolean applied = true;
        switch (this.mode.getValue()) {
            case 0:
                PacketUtil.sendPacket(new C03PacketPlayer.C04PacketPlayerPosition(x, y + 0.0625, z, true));
                PacketUtil.sendPacket(new C03PacketPlayer.C04PacketPlayerPosition(x, y, z, false));
                break;
            case 1:
                PacketUtil.sendPacket(new C03PacketPlayer.C04PacketPlayerPosition(x, y + 0.11, z, false));
                PacketUtil.sendPacket(new C03PacketPlayer.C04PacketPlayerPosition(x, y + 0.1100013579, z, false));
                PacketUtil.sendPacket(new C03PacketPlayer.C04PacketPlayerPosition(x, y + 1.3579E-6, z, false));
                break;
            case 2:
                PacketUtil.sendPacket(new C03PacketPlayer.C04PacketPlayerPosition(x, y + 0.001091981, z, true));
                PacketUtil.sendPacket(new C03PacketPlayer.C04PacketPlayerPosition(x, y, z, false));
                break;
            case 3:
                if (mc.thePlayer.ticksExisted % 4 == 0) {
                    PacketUtil.sendPacket(new C03PacketPlayer.C04PacketPlayerPosition(x, y + 0.0011, z, true));
                    PacketUtil.sendPacket(new C03PacketPlayer.C04PacketPlayerPosition(x, y, z, false));
                } else {
                    applied = false;
                }
                break;
            case 4:
                break;
            case 5:
                mc.thePlayer.motionY = 0.1;
                mc.thePlayer.fallDistance = 0.1F;
                mc.thePlayer.onGround = false;
                break;
            case 6:
                PacketUtil.sendPacket(new C03PacketPlayer.C04PacketPlayerPosition(x, y + 0.02, z, false));
                PacketUtil.sendPacket(new C03PacketPlayer.C04PacketPlayerPosition(x, y + 0.01, z, false));
                mc.thePlayer.setPosition(x, y + 0.01, z);
                break;
            case 7:
                mc.thePlayer.motionY = 0.42;
                break;
            case 8:
                mc.thePlayer.motionY = 0.3425;
                break;
            case 9:
                mc.thePlayer.motionY = this.customMotionY.getValue().doubleValue();
                break;
            case 10:
                break;
            default:
                applied = false;
                break;
        }

        if (applied) {
            this.timer.reset();
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (this.isEnabled() && this.mode.getValue() == 4
                && event.getType() == EventType.SEND
                && event.getPacket() instanceof C03PacketPlayer) {
            ((IAccessorC03PacketPlayer) event.getPacket()).setOnGround(false);
        }
    }

    @Override
    public String[] getSuffix() {
        return new String[]{this.mode.getModeString()};
    }
}