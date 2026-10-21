package leader.util;

import leader.event.EventTarget;
import leader.event.types.EventType;
import leader.events.MoveEvent;
import leader.events.PacketEvent;
import leader.events.TickEvent;
import leader.events.UpdateEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.server.S12PacketEntityVelocity;

public class MovementUtils {
    public static final MovementUtils INSTANCE = new MovementUtils();
    public static int noMovePackets = 0;
    public static boolean cancelMove = false;
    private static double motionX = 0.0f;
    private static double motionY = 0.0f;
    private static double motionZ = 0.0f;
    private static float fallDistance = 0f;
    private static int moveTicks = 0;
    private static final Minecraft mc = Minecraft.getMinecraft();

    public static void cancelMove() {
        if (mc.thePlayer == null) {
            return;
        }
        if (cancelMove) {
            return;
        }
        cancelMove = true;
        motionX = mc.thePlayer.motionX;
        motionY = mc.thePlayer.motionY;
        motionZ = mc.thePlayer.motionZ;
        fallDistance = mc.thePlayer.fallDistance;
    }

    public static void resetMove() {
        cancelMove = false;
        moveTicks = 0;
    }

    @EventTarget
    public void onUpdate(final UpdateEvent event) {
        if (event.getType() == EventType.POST) {
            if (mc.thePlayer == null) {
                return;
            }
            if (moveTicks > 0) {
                motionX = mc.thePlayer.motionX;
                motionZ = mc.thePlayer.motionZ;

                motionY = mc.thePlayer.motionY;
                fallDistance = mc.thePlayer.fallDistance;
                moveTicks--;
            }
            return;
        }
        if (cancelMove && mc.thePlayer != null) {
            if (moveTicks > 0) {
                return;
            }
            mc.thePlayer.motionX = motionX;
            mc.thePlayer.motionZ = motionZ;
            mc.thePlayer.motionY = motionY;
            mc.thePlayer.fallDistance = fallDistance;
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (event.getType() != EventType.PRE) {
            return;
        }
        if (mc.thePlayer == null) {
            MovementUtils.resetMove();
            return;
        }
        if (cancelMove) {
            if (noMovePackets >= 19) {
                mc.thePlayer.motionX = motionX;
                mc.thePlayer.motionY = motionY;
                mc.thePlayer.motionZ = motionZ;
                mc.thePlayer.fallDistance = fallDistance;
                moveTicks++;
            }
            if (moveTicks > 0) {
                return;
            }

            mc.thePlayer.motionX = motionX;
            mc.thePlayer.motionZ = motionZ;
            mc.thePlayer.motionY = motionY;
            mc.thePlayer.fallDistance = fallDistance;
        }
    }

    @EventTarget
    public void onMove(final MoveEvent event) {
        if (cancelMove) {
            if (moveTicks > 0) {
                return;
            }
            event.setCancelled(true);
        }
    }

    @EventTarget
    public void onPacketReceive(final PacketEvent event) {
        if (event.getPacket() instanceof S12PacketEntityVelocity && cancelMove && mc.thePlayer != null) {
            if (((S12PacketEntityVelocity) event.getPacket()).getEntityID() == mc.thePlayer.getEntityId()) {
                mc.thePlayer.motionX = motionX;
                mc.thePlayer.motionY = motionY;
                mc.thePlayer.motionZ = motionZ;
                mc.thePlayer.fallDistance = fallDistance;
                moveTicks++;
            }
        }
    }

    public static void onPacket(Packet<?> packet) {
        if (packet instanceof C03PacketPlayer) {
            if (((C03PacketPlayer) packet).isMoving()) {
                noMovePackets = 0;
            } else {
                noMovePackets++;
            }
        }
    }
}
