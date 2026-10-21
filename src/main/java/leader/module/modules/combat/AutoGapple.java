package leader.module.modules.combat;

import leader.Leader;
import leader.event.EventTarget;
import leader.event.types.EventType;
import leader.event.types.Priority;
import leader.events.LoadWorldEvent;
import leader.events.PacketEvent;
import leader.events.Render2DEvent;
import leader.events.UpdateEvent;
import leader.module.Module;
import leader.module.modules.render.FontManager;
import leader.module.modules.render.notification.NoticeMode;
import leader.module.modules.render.notification.Notification;
import leader.property.properties.BooleanProperty;
import leader.util.ColorUtil;
import leader.util.PacketUtil;
import leader.util.RenderUtil;
import leader.util.render.animations.impl.ContinualAnimation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C09PacketHeldItemChange;

import java.awt.*;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

public class AutoGapple extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final BooleanProperty autoValue = new BooleanProperty("Auto", false);
    public List<Packet<?>> packets = new ArrayList<>();
    boolean velocityed = true;
    boolean eating = false;
    boolean restart = true;
    private int slot = -1;
    int c03s = 0;

    public AutoGapple() {
        super("AutoGapple", false);
    }

    @Override
    public void onEnabled() {
        this.packets.clear();
        this.c03s = 0;
        this.velocityed = false;
        if (mc.thePlayer == null || mc.theWorld == null) {
            this.setEnabled(false);
            return;
        }
        this.slot = findItem2(36, 45, Items.golden_apple);
        if (this.slot != -1) {
            this.slot -= 36;
        }
    }

    public static int findItem2(final int startSlot, final int endSlot, final Item item) {
        for (int i = startSlot; i < endSlot; i++) {
            final ItemStack stack = mc.thePlayer.inventoryContainer.getSlot(i).getStack();
            if (stack != null && stack.getItem() == item)
                return i;
        }
        return -1;
    }

    @Override
    public void onDisabled() {
        this.eating = false;
        this.velocityed = false;
        this.blink();
    }

    @EventTarget(Priority.LOWEST)
    public final void onPacket(PacketEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.SEND || event.isCancelled()) {
            return;
        }
        Packet<?> packet = event.getPacket();

        if (!PacketUtil.isEssential(packet) && PacketUtil.isCPacket(packet)) {
            event.setCancelled(true);
            this.packets.add(packet);
        }

        KillAura killAura = (KillAura) Leader.moduleManager.modules.get(KillAura.class);
        EntityLivingBase target = killAura == null ? null : killAura.getTarget();
        if (packet instanceof C02PacketUseEntity && target != null && target.hurtTime <= 3) {
            this.send();
        }

        this.c03s = 0;
        for (Packet<?> index : this.packets) {
            if (index instanceof C03PacketPlayer) {
                this.c03s++;
            }
        }
    }

    @EventTarget
    public final void onUpdate(final UpdateEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE) {
            return;
        }
        if (mc.thePlayer == null || mc.thePlayer.isDead) {
            this.setEnabled(false);
            return;
        }

        if (this.slot == -1) {
            Notification.addNotification("You haven't any gapple!", "Auto Gapple", NoticeMode.Info);
            this.setEnabled(false);
            return;
        }

        if (this.c03s >= 32) {
            this.c03s = 0;
            if (mc.thePlayer.inventory.currentItem != this.slot) {
                PacketUtil.sendPacketDirect(new C09PacketHeldItemChange(this.slot));
                PacketUtil.sendPacketDirect(new C08PacketPlayerBlockPlacement(mc.thePlayer.inventory.getStackInSlot(this.slot)));
                //PacketUtil.sendPacketDirect(new C07PacketPlayerDigging(C07PacketPlayerDigging.Action.DROP_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN));
                this.blink();
                PacketUtil.sendPacketDirect(new C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem));
            } else {
                PacketUtil.sendPacketDirect(new C08PacketPlayerBlockPlacement(mc.thePlayer.inventory.getStackInSlot(this.slot)));
                //PacketUtil.sendPacketDirect(new C07PacketPlayerDigging(C07PacketPlayerDigging.Action.DROP_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN));
                this.blink();
            }
            this.packets.clear();
            this.setEnabled(false);
            if (this.autoValue.getValue()) {
                this.restart = true;
                this.setEnabled(true);
                this.restart = false;
            } else {
                this.restart = false;
            }
        }
    }

    @EventTarget
    public final void onWorld(final LoadWorldEvent event) {
        this.setEnabled(false);
    }

    private final ContinualAnimation animation = new ContinualAnimation();

    @EventTarget
    public void onRender(Render2DEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null) {
            return;
        }
        ScaledResolution resolution = new ScaledResolution(mc);
        int x = resolution.getScaledWidth() / 2;
        int y = resolution.getScaledHeight() - 75;
        float thickness = 5F;

        float percentage = Math.min(this.c03s, 32) / 32f;

        final int width = 100;
        final int half = width / 2;
        this.animation.animate((width - 2) * percentage, 40);

        RenderUtil.drawRoundedRect(x - half - 1, y - 1 - 12, x - half - 1 + width + 1, y - 1 - 12 + (int) (thickness + 1) + 12 + 3, 2, new Color(17, 17, 17, 215).getRGB());
        RenderUtil.drawRoundedRect(x - half - 1, y - 1, x - half - 1 + width + 1, y - 1 + (int) (thickness + 1), 2, new Color(17, 17, 17, 215).getRGB());

        RenderUtil.drawRoundedRectGradientH(x - half, y + 1, x - half + this.animation.getOutput(), y + 1 + thickness, 2, this.color(0), this.color(90));

        FontManager.drawString("Time", x - 12, y - 1 - 11 + 6, Color.WHITE.getRGB(), false, 18.0F);
        FontManager.drawString(new DecimalFormat("0.0").format(percentage * 100) + "%", x - 9, y + 4.5f, new Color(207, 207, 207).getRGB(), false, 12.0F);
    }

    public int color(int counter) {
        return this.color(counter, 1);
    }

    public int color(int counter, float alpha) {
        return ColorUtil.applyOpacity((ColorUtil.colorSwitch(new Color(128, 128, 255), new Color(128, 255, 255), 2000.0F, counter, 75L, 2).getRGB()), alpha);
    }

    void send() {
        if (this.packets.isEmpty() || mc.getNetHandler() == null)
            return;

        Packet<?> packet = this.packets.get(0);
        this.packets.remove(0);
        if (packet instanceof C09PacketHeldItemChange || (packet instanceof C07PacketPlayerDigging && ((C07PacketPlayerDigging) packet).getStatus() == C07PacketPlayerDigging.Action.RELEASE_USE_ITEM)) {
            this.send();
            return;
        }
        PacketUtil.sendPacketDirect(packet);
        if (!(packet instanceof C02PacketUseEntity)) {
            this.send();
        }
    }

    void blink() {
        if (this.packets.isEmpty() || mc.getNetHandler() == null)
            return;
        while (!this.packets.isEmpty()) {
            Packet<?> packet = this.packets.get(0);
            this.packets.remove(0);
            if (packet instanceof C09PacketHeldItemChange || (packet instanceof C07PacketPlayerDigging && ((C07PacketPlayerDigging) packet).getStatus() == C07PacketPlayerDigging.Action.RELEASE_USE_ITEM))
                continue;
            PacketUtil.sendPacketDirect(packet);
        }
//        ChatUtil.info("send All");
    }
}
