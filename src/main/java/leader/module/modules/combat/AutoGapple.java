package leader.module.modules.combat;

import leader.event.EventTarget;
import leader.event.types.EventType;
import leader.event.types.Priority;
import leader.events.LoadWorldEvent;
import leader.events.PacketEvent;
import leader.events.Render2DEvent;
import leader.events.TickEvent;
import leader.module.Module;
import leader.property.properties.BooleanProperty;
import leader.util.PacketUtil;
import leader.util.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.item.ItemAppleGold;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C09PacketHeldItemChange;

import java.util.ArrayDeque;
import java.util.Deque;

public class AutoGapple extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final int MOVEMENT_THRESHOLD = 32;

    public final BooleanProperty auto = new BooleanProperty("Auto", false);

    private final Deque<Packet<?>> packets = new ArrayDeque<>();
    private int movementPackets;
    private int appleSlot = -1;
    private boolean completing;

    public AutoGapple() {
        super("AutoGapple", false);
    }

    @Override
    public void onEnabled() {
        this.clearState();
        if (mc.thePlayer == null || mc.theWorld == null) {
            this.setEnabled(false);
            return;
        }
        this.appleSlot = this.findAppleSlot();
        if (this.appleSlot == -1) {
            this.setEnabled(false);
        }
    }

    @Override
    public void onDisabled() {
        if (mc.getNetHandler() != null) {
            this.flushPackets();
        } else {
            this.packets.clear();
        }
        this.clearState();
    }

    @EventTarget(Priority.LOWEST)
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled() || this.completing || event.getType() != EventType.SEND
                || event.isCancelled() || mc.thePlayer == null || mc.theWorld == null) {
            return;
        }

        Packet<?> packet = event.getPacket();
        if (packet instanceof C02PacketUseEntity
                && ((C02PacketUseEntity) packet).getAction() == C02PacketUseEntity.Action.ATTACK) {
            event.setCancelled(true);
            PacketUtil.sendPacketDirect(new C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem));
            PacketUtil.sendPacketDirect(packet);
            return;
        }
        if (!this.isDelayable(packet)) {
            return;
        }

        event.setCancelled(true);
        this.packets.offer(packet);
        if (packet instanceof C03PacketPlayer) {
            this.movementPackets++;
        }
    }

    @EventTarget(Priority.HIGH)
    public void onTick(TickEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE || mc.thePlayer == null || mc.theWorld == null) {
            return;
        }
        if (mc.thePlayer.isDead) {
            this.setEnabled(false);
            return;
        }
        if (this.appleSlot == -1 || !this.isApple(this.appleSlot)) {
            this.appleSlot = this.findAppleSlot();
            if (this.appleSlot == -1) {
                this.setEnabled(false);
                return;
            }
        }
        if (this.movementPackets >= MOVEMENT_THRESHOLD) {
            this.eatApple();
        }
    }

    @EventTarget
    public void onWorld(LoadWorldEvent event) {
        this.packets.clear();
        this.clearState();
        if (this.isEnabled()) {
            this.setEnabled(false);
        }
    }

    @EventTarget
    public void onRender(Render2DEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null) {
            return;
        }

        ScaledResolution resolution = new ScaledResolution(mc);
        float progress = Math.min(1.0F, this.movementPackets / (float) MOVEMENT_THRESHOLD);
        int width = 120;
        int height = 6;
        int left = (resolution.getScaledWidth() - width) / 2;
        int top = resolution.getScaledHeight() - 45;
        RenderUtil.drawRoundedRect(left - 2, top - 13, left + width + 2, top + height + 2, 2.0F, 0xAA111111);
        RenderUtil.drawRoundedRect(left, top, left + width, top + height, 2.0F, 0xFF333333);
        if (progress > 0.0F) {
            RenderUtil.drawRoundedRect(left, top, left + Math.max(2.0F, width * progress), top + height, 2.0F, 0xFF80FFFF);
        }
        String text = String.format("Gapple %d%%", Math.round(progress * 100.0F));
        mc.fontRendererObj.drawStringWithShadow(text,
                resolution.getScaledWidth() / 2.0F - mc.fontRendererObj.getStringWidth(text) / 2.0F,
                top - 11.0F, 0xFFFFFFFF);
    }

    private int findAppleSlot() {
        if (mc.thePlayer == null) {
            return -1;
        }
        for (int slot = 0; slot < 9; slot++) {
            if (this.isApple(slot)) {
                return slot;
            }
        }
        return -1;
    }

    private boolean isApple(int slot) {
        if (mc.thePlayer == null || slot < 0 || slot >= 9) {
            return false;
        }
        ItemStack stack = mc.thePlayer.inventory.getStackInSlot(slot);
        return stack != null && stack.getItem() instanceof ItemAppleGold && stack.stackSize > 0;
    }

    private boolean isDelayable(Packet<?> packet) {
        if (packet instanceof C08PacketPlayerBlockPlacement && this.isHoldingSword()) {
            return false;
        }
        if (packet instanceof C07PacketPlayerDigging
                && ((C07PacketPlayerDigging) packet).getStatus() == C07PacketPlayerDigging.Action.RELEASE_USE_ITEM
                && this.isHoldingSword()) {
            return false;
        }
        return packet instanceof C03PacketPlayer
                || packet instanceof C02PacketUseEntity
                || packet instanceof C07PacketPlayerDigging
                || packet instanceof C08PacketPlayerBlockPlacement
                || packet instanceof C09PacketHeldItemChange;
    }

    private boolean isHoldingSword() {
        ItemStack held = mc.thePlayer == null ? null : mc.thePlayer.getHeldItem();
        return held != null && held.getItem() instanceof ItemSword;
    }

    private void eatApple() {
        if (this.appleSlot == -1 || !this.isApple(this.appleSlot) || mc.getNetHandler() == null) {
            this.setEnabled(false);
            return;
        }

        this.completing = true;
        int currentSlot = mc.thePlayer.inventory.currentItem;
        try {
            if (currentSlot != this.appleSlot) {
                PacketUtil.sendPacketDirect(new C09PacketHeldItemChange(this.appleSlot));
            }
            ItemStack apple = mc.thePlayer.inventory.getStackInSlot(this.appleSlot);
            PacketUtil.sendPacketDirect(new C08PacketPlayerBlockPlacement(apple));
            this.flushPackets();
            if (currentSlot != this.appleSlot) {
                PacketUtil.sendPacketDirect(new C09PacketHeldItemChange(currentSlot));
            }
        } finally {
            this.completing = false;
            this.packets.clear();
            this.movementPackets = 0;
        }

        if (!this.auto.getValue()) {
            this.setEnabled(false);
        } else {
            this.appleSlot = this.findAppleSlot();
            if (this.appleSlot == -1) {
                this.setEnabled(false);
            }
        }
    }

    private void flushPackets() {
        while (!this.packets.isEmpty()) {
            Packet<?> packet = this.packets.poll();
            this.decrementMovement(packet);
            if (!this.shouldDrop(packet)) {
                PacketUtil.sendPacketDirect(packet);
            }
        }
    }

    private boolean shouldDrop(Packet<?> packet) {
        if (packet instanceof C09PacketHeldItemChange) {
            return true;
        }
        return packet instanceof C07PacketPlayerDigging
                && ((C07PacketPlayerDigging) packet).getStatus() == C07PacketPlayerDigging.Action.RELEASE_USE_ITEM;
    }

    private void decrementMovement(Packet<?> packet) {
        if (packet instanceof C03PacketPlayer && this.movementPackets > 0) {
            this.movementPackets--;
        }
    }

    private void clearState() {
        this.movementPackets = 0;
        this.appleSlot = -1;
        this.completing = false;
    }
}
