package dev.diona.southside.module.modules.combat;

import cc.polyfrost.oneconfig.config.options.impl.Switch;
import cc.polyfrost.oneconfig.renderer.NanoVGHelper;
import cc.polyfrost.oneconfig.renderer.font.Fonts;
import dev.diona.southside.Southside;
import dev.diona.southside.event.events.*;
import dev.diona.southside.gui.style.ClientStyle;
import dev.diona.southside.module.Category;
import dev.diona.southside.module.Module;
import dev.diona.southside.module.modules.client.Notification;
import dev.diona.southside.util.network.PacketUtil;
import dev.diona.southside.util.player.MovementUtils;
import dev.diona.southside.util.render.ColorUtil;
import dev.diona.southside.util.render.RenderUtil;
import dev.diona.southside.util.render.RoundUtil;
import dev.diona.southside.util.render.animations.impl.ContinualAnimation;
import me.bush.eventbus.annotation.EventListener;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.*;
import net.minecraft.util.EnumHand;

import java.awt.*;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class AutoGapple extends Module {
    public final Switch autoValue = new Switch("Auto", false);
    public List<Packet<?>> packets = new ArrayList<>();
    boolean velocityed = true;
    boolean eating = false;
    boolean restart = true;
    private int slot = -1;
    int c03s = 0;

    public AutoGapple(String name, String description, Category category, boolean visible) {
        super(name, description, category, visible);
    }

    @Override
    public boolean onEnable() {
        MovementUtils.cancelMove();
        packets.clear();
        c03s = 0;
        velocityed = false;
        this.slot = findItem2(36, 45, Items.GOLDEN_APPLE);
        if (this.slot != -1) {
            this.slot -= 36;
        }
        return true;
    }

    public static int findItem2(final int startSlot, final int endSlot, final Item item) {
        for (int i = startSlot; i < endSlot; i++) {
            final ItemStack stack = mc.player.inventoryContainer.getSlot(i).getStack();
            if (stack != null && stack.getItem() == item)
                return i;
        }
        return -1;
    }

    @Override
    public boolean onDisable() {
        eating = false;
        velocityed = false;
        MovementUtils.resetMove();
        blink();
        return true;
    }

    @EventListener
    public final void onPacket(HigherPacketEvent event) {
        Packet<?> packet = event.getPacket();

        if (!PacketUtil.isEssential(packet) && PacketUtil.isCPacket(packet)) {
            event.setCancelled(true);
            packets.add(packet);
        }

        if (packet instanceof CPacketUseEntity && Objects.requireNonNull(KillAura.getTarget()).hurtTime <= 3) {
            Objects.requireNonNull(KillAura.getTarget());
            send();
        }

        c03s = 0;
        for (Packet<?> index : packets) {
            if (index instanceof CPacketPlayer) {
                c03s++;
            }
        }
    }

    @EventListener
    public final void onUpdate(final UpdateEvent event) {
        if (mc.player == null || mc.player.isDead) {
            this.setEnable(false);
            return;
        }

        if (this.slot == -1) {
            Notification.addNotificationKeepTime("You haven't any gapple!", "Auto Gapple", Notification.NotificationType.WARN, 3);
            this.setEnable(false);
            return;
        }

        if (this.c03s >= 32) {
            c03s = 0;
            if (mc.player.inventory.currentItem != slot) {
                mc.getConnection().sendPacketNoHigherEvent(new CPacketHeldItemChange(slot));
                mc.getConnection().sendPacketNoHigherEvent(new CPacketPlayerTryUseItem(EnumHand.MAIN_HAND));
                //mc.getConnection().sendPacketNoHigherEvent(new CPacketPlayerDigging(CPacketPlayerDigging.Action.DROP_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN));
                blink();
                mc.getConnection().sendPacketNoHigherEvent(new CPacketHeldItemChange(mc.player.inventory.currentItem));
            } else {
                mc.getConnection().sendPacketNoHigherEvent(new CPacketPlayerTryUseItem(EnumHand.MAIN_HAND));
                //mc.getConnection().sendPacketNoHigherEvent(new CPacketPlayerDigging(CPacketPlayerDigging.Action.DROP_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN));
                blink();
            }
            packets.clear();
            this.setEnable(false);
            if (this.autoValue.getValue()) {
                restart = true;
                setEnable(true);
                restart = false;
            } else {
                restart = false;
            }
        }
    }

    @EventListener
    public final void onWorld(final WorldEvent event) {
        this.setEnable(false);
    }

    private final ContinualAnimation animation = new ContinualAnimation();

    @EventListener
    public void onRender(NewRender2DEvent event) {
        NanoVGHelper nanovg = NanoVGHelper.INSTANCE;
        ScaledResolution resolution = new ScaledResolution(mc);
        int x = resolution.getScaledWidth() / 2;
        int y = resolution.getScaledHeight() - 75;
        float thickness = 5F;

        float percentage = Math.min(c03s, 32) / 32f;

        final int width = 100;
        final int half = width / 2;
        animation.animate((width - 2) * percentage, 40);

        RoundUtil.drawRound(x - half - 1, y - 1 - 12, width + 1, (int) (thickness + 1) + 12 + 3, 2, new Color(17, 17, 17, 215));
        RoundUtil.drawRound(x - half - 1, y - 1, width + 1, (int) (thickness + 1), 2, new Color(17, 17, 17, 215));

        RoundUtil.drawGradientHorizontal(x - half, y + 1, animation.getOutput(), thickness, 2, new Color(color(0)), new Color(color(90)));

        nanovg.setupAndDraw(true, vg -> {
            nanovg.drawText(vg,"Time", x - 12, y - 1 - 11 + 6, Color.WHITE.getRGB(), 10, Fonts.BOLD);
            nanovg.drawText(vg,new DecimalFormat("0.0").format(percentage * 100) + "%", x- 9, y + 4.5f, new Color(207, 207, 207).getRGB(), 6, Fonts.BOLD);
        });
    }

    public int color(int counter) {
        return color(counter, 1);
    }

    public int color(int counter, float alpha) {
        return ColorUtil.applyOpacity((ColorUtil.colorSwitch(new Color(128, 128, 255), new Color(128, 255, 255), 2000.0F, counter, 75L, 2).getRGB()), alpha);
    }

    void send() {
        if (packets.isEmpty())
            return;

        Packet<?> packet = packets.get(0);
        packets.remove(0);
        if (packet instanceof CPacketHeldItemChange || (packet instanceof CPacketPlayerDigging && ((CPacketPlayerDigging) packet).getAction() == CPacketPlayerDigging.Action.RELEASE_USE_ITEM)) {
            send();
            return;
        }
        mc.getConnection().sendPacketNoHigherEvent(packet);
        if (!(packet instanceof CPacketUseEntity)) {
            send();
        }
    }

    void blink() {
        if (packets.isEmpty())
            return;
        while (!packets.isEmpty()) {
            Packet<?> packet = packets.get(0);
            packets.remove(0);
            if (packet instanceof CPacketHeldItemChange || (packet instanceof CPacketPlayerDigging && ((CPacketPlayerDigging) packet).getAction() == CPacketPlayerDigging.Action.RELEASE_USE_ITEM))
                continue;
            mc.getConnection().sendPacketNoHigherEvent(packet);
        }
//        ChatUtil.info("send All");
    }
}