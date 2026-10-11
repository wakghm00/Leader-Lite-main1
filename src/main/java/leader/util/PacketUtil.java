package leader.util;

import net.minecraft.client.Minecraft;
import net.minecraft.network.INetHandler;
import net.minecraft.network.Packet;
import net.minecraft.network.ThreadQuickExitException;
import net.minecraft.network.play.INetHandlerPlayClient;

public class PacketUtil {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final ThreadLocal<Boolean> sendingDirect = ThreadLocal.withInitial(() -> false);

    public static boolean isSendingDirect() {
        return sendingDirect.get();
    }

    public static void sendPacketDirect(Packet<?> packet) {
        boolean previous = sendingDirect.get();
        sendingDirect.set(true);
        try {
            mc.getNetHandler().getNetworkManager().sendPacket(packet, null);
        } finally {
            sendingDirect.set(previous);
        }
    }

    public static void sendPacket(Packet<?> packet) {
        mc.getNetHandler().getNetworkManager().sendPacket(packet);
    }

    public static void sendPacketNoEvent(Packet<?> packet) {
        mc.getNetHandler().getNetworkManager().sendPacket(packet, null);
    }
    public static void receivePacket(Packet<?> packet) {
        if (packet == null)
            return;
        try {
            Packet<INetHandlerPlayClient> casted = castPacket(packet);
            casted.processPacket(mc.getNetHandler());
        } catch (ThreadQuickExitException ignored) {
        }
    }
    @SuppressWarnings("unchecked")
    public static <H extends INetHandler> Packet<H> castPacket(Packet<?> packet) throws ClassCastException {
        return (Packet<H>) packet;
    }
}
