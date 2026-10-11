package leader.module.modules.combat;

import leader.event.EventTarget;
import leader.event.types.EventType;
import leader.events.AttackEvent;
import leader.events.PacketEvent;
import leader.events.Render3DEvent;
import leader.events.TickEvent;
import leader.events.UpdateEvent;
import leader.module.Module;
import leader.property.properties.BooleanProperty;
import leader.property.properties.FloatProperty;
import leader.property.properties.IntProperty;
import leader.property.properties.ModeProperty;
import leader.util.PacketUtil;
import leader.util.RenderUtil;
import leader.util.RotationUtil;
import leader.util.TimedPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S13PacketDestroyEntities;
import net.minecraft.network.play.server.S14PacketEntity;
import net.minecraft.network.play.server.S18PacketEntityTeleport;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;
import org.lwjgl.opengl.GL11;

import java.util.Deque;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentLinkedQueue;

public class BackTrack extends Module {

    private static final Minecraft mc = Minecraft.getMinecraft();

    public final ModeProperty mode = new ModeProperty("Mode", 0, new String[]{"Current", "Legacy"});

    public final FloatProperty minRange = new FloatProperty("Min Range", 3.0F, 1.0F, 6.0F, () -> !this.isLegacy());
    public final FloatProperty maxRange = new FloatProperty("Max Range", 6.0F, 1.0F, 6.0F, () -> !this.isLegacy());
    public final IntProperty delay = new IntProperty("Delay", 200, 0, 1000, () -> !this.isLegacy());
    public final IntProperty chance = new IntProperty("Chance", 100, 5, 100, () -> !this.isLegacy());
    public final BooleanProperty resetOnVelocity = new BooleanProperty("Reset On Velocity", true, () -> !this.isLegacy());
    public final BooleanProperty render = new BooleanProperty("Render", true, () -> !this.isLegacy());

    public final IntProperty trackMs = new IntProperty("TrackMS", 200, 1, 1000, this::isLegacy);
    public final FloatProperty maxDistance = new FloatProperty("Max Track Range", 4.0F, 3.1F, 6.0F, this::isLegacy);
    public final BooleanProperty rayTrance = new BooleanProperty("Ray Trance", true, this::isLegacy);
    public final BooleanProperty renderRealPos = new BooleanProperty("Render Real Position", true, this::isLegacy);
    public final BooleanProperty smart = new BooleanProperty("Smart", true, this::isLegacy);
    public final BooleanProperty onlyHighSpeed = new BooleanProperty("Only On Target High Speed", false, this::isLegacy);
    public final FloatProperty highSpeedThreshold = new FloatProperty("HighSpeed Threshold", 0.2F, 0.01F, 1.0F,
            () -> this.isLegacy() && this.onlyHighSpeed.getValue());

    private final Queue<TimedPacket> packetQueue = new ConcurrentLinkedQueue<>();
    private final Deque<Vec3> positionHistory = new ConcurrentLinkedDeque<>();
    private final Deque<Vec3> recentPositions = new ConcurrentLinkedDeque<>();
    private int activeMode;

    private EntityPlayer trackedPlayer;
    private Vec3 truePos;
    private Vec3 lastTruePos;
    private boolean backtracking;
    private boolean replaying;

    public BackTrack() {
        super("BackTrack", false);
    }

    @Override
    public String[] getSuffix() {
        return new String[]{this.isLegacy() ? this.trackMs.getValue() + "ms" : this.delay.getValue().toString()};
    }

    private boolean isLegacy() {
        return this.mode.getValue() == 1;
    }

    @Override
    public void verifyValue(String name) {
        if ("Mode".equals(name)) {
            this.syncMode();
        }
    }

    private void syncMode() {
        int selectedMode = this.mode.getValue();
        if (this.activeMode != selectedMode) {
            this.resetAndRelease();
            this.clearAll();
            this.activeMode = selectedMode;
        }
    }

    @Override
    public void onEnabled() {
        this.clearAll();
        this.activeMode = this.mode.getValue();
    }

    @Override
    public void onDisabled() {
        this.releaseAll();
        this.clearAll();
    }

    private void clearAll() {
        this.packetQueue.clear();
        this.trackedPlayer = null;
        this.truePos = null;
        this.lastTruePos = null;
        this.backtracking = false;
        this.positionHistory.clear();
        this.recentPositions.clear();
    }

    private void resetAndRelease() {
        this.trackedPlayer = null;
        this.truePos = null;
        this.lastTruePos = null;
        this.positionHistory.clear();
        this.recentPositions.clear();
        this.releaseAll();
    }

    private void releaseAll() {
        this.backtracking = false;
        this.replaying = true;
        try {
            while (!this.packetQueue.isEmpty()) {
                TimedPacket timedPacket = this.packetQueue.poll();
                if (timedPacket == null) continue;
                if (mc.thePlayer != null && mc.theWorld != null && mc.getNetHandler() != null) {
                    PacketUtil.receivePacket(timedPacket.getPacket());
                }
            }
        } finally {
            this.replaying = false;
        }
    }

    private boolean isTrackedValid() {
        if (mc.thePlayer == null || mc.theWorld == null || this.trackedPlayer == null) return false;
        if (this.trackedPlayer.isDead || this.trackedPlayer.deathTime > 0) return false;
        if (!mc.theWorld.playerEntities.contains(this.trackedPlayer)) return false;
        float range = this.isLegacy() ? this.maxDistance.getValue() : this.maxRange.getValue();
        return mc.thePlayer.getDistanceToEntity(this.trackedPlayer) <= range + 3.0F;
    }

    private AxisAlignedBB buildBox(Vec3 pos) {
        double width = this.trackedPlayer.width / 2.0 + this.trackedPlayer.getCollisionBorderSize();
        double height = this.trackedPlayer.height + this.trackedPlayer.getCollisionBorderSize();
        return new AxisAlignedBB(
                pos.xCoord - width, pos.yCoord, pos.zCoord - width,
                pos.xCoord + width, pos.yCoord + height, pos.zCoord + width);
    }

    private void checkBacktrackRange() {
        if (this.trackedPlayer == null || this.truePos == null) return;
        double visibleDist = RotationUtil.distanceToBox(this.trackedPlayer.getEntityBoundingBox());
        double trueDist = RotationUtil.distanceToBox(this.buildBox(this.truePos));
        if (visibleDist <= 3.0 && trueDist >= this.minRange.getValue() && trueDist < this.maxRange.getValue()) {
            this.backtracking = true;
        } else {
            this.backtracking = false;
        }
    }

    private void processQueue() {
        long delayMs = this.isLegacy() ? this.trackMs.getValue() : this.delay.getValue();
        this.replaying = true;
        try {
            while (!this.packetQueue.isEmpty()) {
                TimedPacket timedPacket = this.packetQueue.peek();
                if (timedPacket == null) break;
                if (!timedPacket.getCold().getPass(delayMs)) break;
                this.packetQueue.poll();
                PacketUtil.receivePacket(timedPacket.getPacket());
            }
        } finally {
            this.replaying = false;
        }
    }

    @EventTarget
    public void onAttack(AttackEvent event) {
        if (!this.isEnabled()) return;
        this.syncMode();
        if (mc.thePlayer == null || mc.theWorld == null) {
            this.resetAndRelease();
            return;
        }
        Entity entity = event.getTarget();
        if (!(entity instanceof EntityPlayer)) return;
        EntityPlayer player = (EntityPlayer) entity;
        if (player == mc.thePlayer) return;
        if (this.isLegacy() && this.onlyHighSpeed.getValue()) {
            double dx = player.posX - player.prevPosX;
            double dy = player.posY - player.prevPosY;
            double dz = player.posZ - player.prevPosZ;
            double speed = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (speed < this.highSpeedThreshold.getValue()) return;
        }
        if (this.trackedPlayer != null && this.trackedPlayer.getEntityId() == player.getEntityId()) return;
        if (!this.isLegacy() && Math.random() > this.chance.getValue() / 100.0) {
            this.resetAndRelease();
            return;
        }
        this.releaseAll();
        this.positionHistory.clear();
        this.recentPositions.clear();
        this.trackedPlayer = player;
        this.truePos = player.getPositionVector();
        this.lastTruePos = this.truePos;
        if (this.isLegacy()) {
            this.positionHistory.add(this.truePos);
            this.recentPositions.add(this.truePos);
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!this.isEnabled() || event.getType() == EventType.POST) return;
        this.syncMode();
        if (mc.thePlayer == null || mc.theWorld == null) {
            this.resetAndRelease();
            return;
        }
        if (this.trackedPlayer == null) {
            this.releaseAll();
            return;
        }
        if (!this.isTrackedValid()) {
            this.resetAndRelease();
            return;
        }
        if (this.isLegacy()) {
            this.updateLegacyTargetLogic();
            this.processQueue();
            if (this.packetQueue.isEmpty() && this.trackedPlayer != null) {
                this.truePos = this.trackedPlayer.getPositionVector();
            }
            return;
        }
        this.lastTruePos = this.truePos;
        if (this.backtracking) {
            this.checkBacktrackRange();
        }
        this.processQueue();
    }

    private void updateLegacyTargetLogic() {
        if (this.trackedPlayer == null || this.truePos == null) return;
        try {
            Vec3 currentPos = this.trackedPlayer.getPositionVector();
            this.recentPositions.addLast(currentPos);
            if (this.recentPositions.size() > 5) {
                this.recentPositions.removeFirst();
            }
            if (this.recentPositions.size() == 5
                    && this.recentPositions.getFirst().distanceTo(currentPos) > 5.0) {
                this.resetAndRelease();
                return;
            }

            this.positionHistory.addLast(currentPos);
            if (this.positionHistory.size() > 10) {
                this.positionHistory.removeFirst();
            }
            Vec3 playerPos = mc.thePlayer.getPositionVector();
            if (this.truePos.distanceTo(playerPos) > this.maxDistance.getValue()) {
                this.resetAndRelease();
                return;
            }
            if (this.smart.getValue() && !this.positionHistory.isEmpty()) {
                double distReal = this.truePos.distanceTo(playerPos);
                double distHistory = this.positionHistory.getFirst().distanceTo(playerPos);
                if (distReal <= distHistory) {
                    this.resetAndRelease();
                    return;
                }
            }
            this.lastTruePos = this.truePos;
        } catch (Exception ex) {
            this.resetAndRelease();
        }
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE) return;
        this.syncMode();
        if (!this.isLegacy()) return;
        if (mc.thePlayer == null || mc.theWorld == null) {
            this.resetAndRelease();
            return;
        }
        if (this.trackedPlayer == null || this.truePos == null) return;
        if (!this.isTrackedValid()) {
            this.resetAndRelease();
            return;
        }
        if (this.rayTrance.getValue()
                && RotationUtil.rayTrace(this.buildBox(this.truePos), event.getNewYaw(), event.getNewPitch(),
                this.maxDistance.getValue()) == null) {
            this.resetAndRelease();
        }
    }

    @EventTarget
    public void onReceivePacket(PacketEvent event) {
        if (!this.isEnabled() || this.replaying) return;
        if (event.getType() == EventType.SEND || event.isCancelled()) return;
        this.syncMode();
        if (mc.thePlayer == null || mc.theWorld == null) {
            this.resetAndRelease();
            return;
        }
        Packet<?> packet = event.getPacket();

        if (!this.isLegacy() && packet instanceof S08PacketPlayerPosLook) {
            this.resetAndRelease();
            return;
        }

        if (!this.isLegacy() && packet instanceof S12PacketEntityVelocity
                && ((S12PacketEntityVelocity) packet).getEntityID() == mc.thePlayer.getEntityId()) {
            if (this.resetOnVelocity.getValue()) {
                this.resetAndRelease();
            }
            return;
        }

        if (this.trackedPlayer == null || this.truePos == null) return;
        if (!this.isTrackedValid()) {
            this.resetAndRelease();
            return;
        }

        boolean trackedMove = false;
        if (packet instanceof S14PacketEntity) {
            S14PacketEntity wrapper = (S14PacketEntity) packet;
            Entity entity = wrapper.getEntity(mc.theWorld);
            if (entity != null && entity.getEntityId() == this.trackedPlayer.getEntityId()) {
                this.truePos = this.truePos.addVector(
                        wrapper.func_149062_c() / 32.0D,
                        wrapper.func_149061_d() / 32.0D,
                        wrapper.func_149064_e() / 32.0D);
                if (!this.isLegacy()) this.checkBacktrackRange();
                trackedMove = true;
            }
        } else if (packet instanceof S18PacketEntityTeleport) {
            S18PacketEntityTeleport wrapper = (S18PacketEntityTeleport) packet;
            if (wrapper.getEntityId() == this.trackedPlayer.getEntityId()) {
                this.truePos = new Vec3(
                        wrapper.getX() / 32.0D,
                        wrapper.getY() / 32.0D,
                        wrapper.getZ() / 32.0D);
                if (!this.isLegacy()) this.checkBacktrackRange();
                trackedMove = true;
            }
        } else if (packet instanceof S13PacketDestroyEntities) {
            for (int id : ((S13PacketDestroyEntities) packet).getEntityIDs()) {
                if (id == this.trackedPlayer.getEntityId()) {
                    this.resetAndRelease();
                    return;
                }
            }
        }

        if (trackedMove && (this.isLegacy() || this.backtracking)) {
            this.packetQueue.add(new TimedPacket(packet));
            event.setCancelled(true);
        }
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (!this.isEnabled()) return;
        this.syncMode();
        if (mc.thePlayer == null || mc.theWorld == null) {
            this.resetAndRelease();
            return;
        }
        if (this.trackedPlayer == null || this.truePos == null) return;
        if (!this.isTrackedValid()) {
            this.resetAndRelease();
            return;
        }

        Vec3 smoothed;
        if (this.isLegacy()) {
            if (!this.renderRealPos.getValue() || this.lastTruePos == null) return;
            smoothed = this.getLegacySmoothedPosition(event.getPartialTicks());
        } else {
            if (!this.render.getValue() || !this.backtracking) return;
            float partialTicks = event.getPartialTicks();
            Vec3 from = this.lastTruePos != null ? this.lastTruePos : this.truePos;
            smoothed = new Vec3(
                    from.xCoord + (this.truePos.xCoord - from.xCoord) * partialTicks,
                    from.yCoord + (this.truePos.yCoord - from.yCoord) * partialTicks,
                    from.zCoord + (this.truePos.zCoord - from.zCoord) * partialTicks);
        }

        AxisAlignedBB box = this.buildBox(smoothed).offset(
                -mc.getRenderManager().viewerPosX,
                -mc.getRenderManager().viewerPosY,
                -mc.getRenderManager().viewerPosZ);

        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.disableDepth();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        RenderUtil.drawFilledBox(box, 255, 255, 255);
        if (!this.isLegacy()) {
            RenderUtil.drawBoundingBox(box, 255, 255, 255, 204, 1.5F);
        }
        GlStateManager.enableDepth();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    private Vec3 getLegacySmoothedPosition(float partialTicks) {
        if (this.positionHistory.isEmpty()) {
            return new Vec3(
                    this.lastTruePos.xCoord + (this.truePos.xCoord - this.lastTruePos.xCoord) * partialTicks,
                    this.lastTruePos.yCoord + (this.truePos.yCoord - this.lastTruePos.yCoord) * partialTicks,
                    this.lastTruePos.zCoord + (this.truePos.zCoord - this.lastTruePos.zCoord) * partialTicks);
        }
        double totalWeight = 0;
        double x = 0, y = 0, z = 0;
        Object[] history = this.positionHistory.toArray();
        int size = history.length;
        for (int i = 0; i < size; i++) {
            double weight = (i + 1) / (double) size;
            Vec3 pos = (Vec3) history[i];
            x += pos.xCoord * weight;
            y += pos.yCoord * weight;
            z += pos.zCoord * weight;
            totalWeight += weight;
        }
        double currentWeight = 3;
        x += this.truePos.xCoord * currentWeight;
        y += this.truePos.yCoord * currentWeight;
        z += this.truePos.zCoord * currentWeight;
        totalWeight += currentWeight;
        return new Vec3(x / totalWeight, y / totalWeight, z / totalWeight);
    }
}
