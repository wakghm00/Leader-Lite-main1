package leader.module.modules.movement;

import leader.event.EventTarget;
import leader.event.types.EventType;
import leader.events.MoveInputEvent;
import leader.events.PacketEvent;
import leader.events.UpdateEvent;
import leader.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.client.C03PacketPlayer;

public class Stasis extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public Stasis() {
        super("Stasis", false, false);
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE || mc.thePlayer == null) {
            return;
        }

        mc.thePlayer.motionX = 0.0D;
        mc.thePlayer.motionY = 0.0D;
        mc.thePlayer.motionZ = 0.0D;
    }

    @EventTarget
    public void onMoveInput(MoveInputEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null) {
            return;
        }

        mc.thePlayer.movementInput.moveForward = 0.0F;
        mc.thePlayer.movementInput.moveStrafe = 0.0F;
        mc.thePlayer.movementInput.jump = false;
        mc.thePlayer.movementInput.sneak = false;
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.SEND
                || !(event.getPacket() instanceof C03PacketPlayer)
                || event.getPacket() instanceof C03PacketPlayer.C05PacketPlayerLook) {
            return;
        }

        event.setCancelled(true);
    }
}