package leader.module.modules.combat;

import leader.Leader;
import leader.event.EventManager;
import leader.event.EventTarget;
import leader.event.types.EventType;
import leader.events.AttackEvent;
import leader.events.PacketEvent;
import leader.events.UpdateEvent;
import leader.mixin.IAccessorEntity;
import leader.module.Module;
import leader.property.properties.BooleanProperty;
import leader.property.properties.IntProperty;
import leader.util.PacketUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S14PacketEntity;
import net.minecraft.network.play.server.S19PacketEntityStatus;
import net.minecraft.network.play.server.S32PacketConfirmTransaction;
import net.minecraft.potion.Potion;

import java.util.ArrayList;
import java.util.List;

public class GrimReduceVelocity extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private final IntProperty reduceTicks = new IntProperty("Reduce Ticks", 14, 1, 20);
    private final IntProperty teleportDisableTicks = new IntProperty("Teleport Disable Ticks", 2, 1, 7);
    private final IntProperty range = new IntProperty("Range", 8, 1, 100);
    private final BooleanProperty delayPlus = new BooleanProperty("Delay Plus", false);
    private final BooleanProperty delayTillGround = new BooleanProperty("Delay Till Ground", true);
    private final BooleanProperty jumpReset = new BooleanProperty("Jump Reset", true);
    private final BooleanProperty extraHit = new BooleanProperty("Extra Hit", true);
    private final BooleanProperty stopSprint = new BooleanProperty("Stop Sprint", true);
    private final BooleanProperty onSwingDisable = new BooleanProperty("On Swing Disable on Aura", true);

    private final List<Packet<?>> delayedPackets = new ArrayList<>();
    private boolean delaying;
    private boolean pendingJumpReset;
    private int reduceTimer;

    public GrimReduceVelocity() {
        super("GrimReduceVelocity", false);
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!isEnabled() || event.getType() != EventType.RECEIVE || event.isCancelled()
                || mc.thePlayer == null || isInLiquidOrWeb()
                || mc.thePlayer.ticksExisted <= teleportDisableTicks.getValue()) {
            return;
        }

        Packet<?> packet = event.getPacket();
        if (packet instanceof S12PacketEntityVelocity
                && ((S12PacketEntityVelocity) packet).getEntityID() == mc.thePlayer.getEntityId()
                && !delaying && (delayTillGround.getValue() || delayPlus.getValue())) {
            delaying = true;
            reduceTimer = 0;
            pendingJumpReset = true;
            delayedPackets.add(packet);
            event.setCancelled(true);
        } else if (delaying && isSyncPacket(packet)) {
            delayedPackets.add(packet);
            event.setCancelled(true);
        }
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!isEnabled() || mc.thePlayer == null || event.getType() != EventType.PRE) {
            return;
        }

        if (delaying) {
            reduceTimer++;
            boolean release = (mc.thePlayer.onGround && (!delayPlus.getValue() || !hasCombatTarget()))
                    || (!delayTillGround.getValue() && reduceTimer >= 1)
                    || reduceTimer > 25;
            if (release) {
                releasePackets();
            }
        }

        if (pendingJumpReset && jumpReset.getValue() && mc.thePlayer.onGround
                && isForwardPressed() && !isInLiquidOrWeb()
                && !mc.thePlayer.isPotionActive(Potion.jump)) {
            mc.thePlayer.movementInput.jump = true;
            pendingJumpReset = false;
        }

        if (reduceTimer <= reduceTicks.getValue() && shouldExtraHit()) {
            EntityLivingBase target = getTarget();
            if (target != null) {
                attack(target);
                if (stopSprint.getValue()) {
                    mc.thePlayer.setSprinting(false);
                }
            }
        }
    }

    private boolean shouldExtraHit() {
        KillAura aura = (KillAura) Leader.moduleManager.getModule(KillAura.class);
        return extraHit.getValue() && aura != null && aura.isEnabled()
                && (!onSwingDisable.getValue() || mc.thePlayer.isSwingInProgress)
                && mc.thePlayer.hurtTime > 0;
    }

    private EntityLivingBase getTarget() {
        KillAura aura = (KillAura) Leader.moduleManager.getModule(KillAura.class);
        if (aura == null || !aura.isEnabled() || aura.getTarget() == null
                || aura.getTarget() == mc.thePlayer
                || mc.thePlayer.getDistanceToEntity(aura.getTarget()) > range.getValue()) {
            return null;
        }
        return aura.getTarget();
    }

    private void attack(EntityLivingBase target) {
        EventManager.call(new AttackEvent(target));
        PacketUtil.sendPacket(new C0APacketAnimation());
        PacketUtil.sendPacket(new C02PacketUseEntity(target, C02PacketUseEntity.Action.ATTACK));
    }

    private void releasePackets() {
        delaying = false;
        pendingJumpReset = false;
        for (Packet<?> packet : delayedPackets) {
            PacketUtil.receivePacket(packet);
        }
        delayedPackets.clear();
    }

    private boolean isSyncPacket(Packet<?> packet) {
        return packet instanceof S14PacketEntity
                || packet instanceof S19PacketEntityStatus
                || packet instanceof S32PacketConfirmTransaction;
    }

    private boolean hasCombatTarget() {
        return getTarget() != null;
    }

    private boolean isInLiquidOrWeb() {
        return mc.thePlayer.isInWater() || mc.thePlayer.isInLava()
                || ((IAccessorEntity) mc.thePlayer).getIsInWeb();
    }

    private boolean isForwardPressed() {
        return mc.thePlayer.movementInput.moveForward != 0.0F;
    }

    @Override
    public void onEnabled() {
        delaying = false;
        pendingJumpReset = false;
        reduceTimer = 0;
        delayedPackets.clear();
    }

    @Override
    public void onDisabled() {
        releasePackets();
    }

    @Override
    public String[] getSuffix() {
        return new String[]{"grim"};
    }
}