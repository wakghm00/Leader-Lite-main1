package leader.module.modules.combat;

import leader.Leader;
import leader.event.EventTarget;
import leader.event.types.EventType;
import leader.event.types.Priority;
import leader.events.LoadWorldEvent;
import leader.events.PacketEvent;
import leader.events.Render2DEvent;
import leader.events.UpdateEvent;
import leader.mixin.IAccessorEntityPlayer;
import leader.mixin.IAccessorPlayerControllerMP;
import leader.module.Module;
import leader.module.modules.render.FontManager;
import leader.module.modules.render.notification.NoticeMode;
import leader.module.modules.render.notification.Notification;
import leader.property.properties.BooleanProperty;
import leader.property.properties.IntProperty;
import leader.property.properties.ModeProperty;
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
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;

import java.awt.*;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

public class AutoGapple extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final ModeProperty mode = new ModeProperty("Mode", 0, new String[]{"Vanilla", "Instant"});
    /**
     * 触发吃食所需的 tick 数，1-33 随意调。
     * Instant 模式下这个值就是 blink 的长度（原来是写死的 32）。
     * 33 = Golden Apple 的 maxItemUseDuration，即上限。
     */
    public final IntProperty ticks = new IntProperty("Ticks", 32, 1, 33);
    public final BooleanProperty autoValue = new BooleanProperty("Auto", false);
    /** Instant 模式下缓存队列的上限，超出后强制放包（原来无上限会无限膨胀） */
    private static final int MAX_QUEUE = 300;
    /**
     * 倒计时结束后等待服务端扣物品的 tick 数（1 秒）。
     * 1.8.9 客户端不会触发 onItemUseFinish，物品数量只能等服务端 setSlot 回包。
     */
    private static final int CONFIRM_TIMEOUT = 20;

    /** Instant 模式的包缓存 */
    public List<Packet<?>> packets = new ArrayList<>();
    boolean velocityed = true;
    boolean eating = false;
    boolean restart = true;

    private int slot = -1;
    private int prevSlot = -1;

    /** 两种模式共用的进度（渲染进度条用） */
    int c03s = 0;

    /** Vanilla 模式：本地已吃 tick 数 */
    private int useTicks = 0;
    /** Vanilla 模式：本次吃食开始时的槽位与物品数量，用来判断服务端是否扣了物品 */
    private int startSlot = -1;
    private int startSize = -1;
    /** Vanilla 模式：连续重发 C08 的次数，用于防死循环 */
    private int resumeAttempts = 0;
    /** Vanilla 模式：倒计时结束后等待服务端确认的 tick 数 */
    private int confirmWait = 0;

    /** Instant 模式：独立的 tick 计数器，不再从 packets 里反推 */
    private int blinkTicks = 0;

    public AutoGapple() {
        super("AutoGapple", false);
    }

    /** 当前配置的目标 tick 数，强制夹在 1-33 */
    private int targetTicks() {
        int value = this.ticks.getValue();
        return MathHelper.clamp_int(value, 1, 33);
    }

    @Override
    public void onEnabled() {
        this.packets.clear();
        this.c03s = 0;
        this.useTicks = 0;
        this.blinkTicks = 0;
        this.startSize = -1;
        this.resumeAttempts = 0;
        this.confirmWait = 0;
        this.prevSlot = -1;
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
        boolean wasEating = this.eating;
        this.eating = false;
        this.velocityed = false;
        this.useTicks = 0;
        this.blinkTicks = 0;
        this.startSize = -1;
        this.resumeAttempts = 0;
        this.confirmWait = 0;
        if (this.mode.getValue() == 1) {
            this.blink();
        } else if (wasEating) {
            this.stopUsing();
        }
        this.restoreSlot();
    }

    /** 主动中断吃食：既要清掉客户端状态，也要告诉服务端 */
    private void stopUsing() {
        if (mc.thePlayer == null) {
            return;
        }
        PacketUtil.sendPacket(new C07PacketPlayerDigging(C07PacketPlayerDigging.Action.RELEASE_USE_ITEM,
                BlockPos.ORIGIN, EnumFacing.DOWN));
        ((IAccessorEntityPlayer) mc.thePlayer).setItemInUse(null);
        ((IAccessorEntityPlayer) mc.thePlayer).setItemInUseCount(0);
    }

    // ------------------------------------------------------------------
    // 事件
    // ------------------------------------------------------------------

    @EventTarget(Priority.LOWEST)
    public final void onPacket(PacketEvent event) {
        if (!this.isEnabled() || this.mode.getValue() != 1) {
            return;
        }
        if (event.getType() != EventType.SEND || event.isCancelled()) {
            return;
        }
        Packet<?> packet = event.getPacket();

        if (!PacketUtil.isEssential(packet) && PacketUtil.isCPacket(packet)) {
            event.setCancelled(true);
            this.packets.add(packet);
            if (packet instanceof C03PacketPlayer) {
                this.blinkTicks++;
                this.c03s = Math.min(this.blinkTicks, this.targetTicks());
            }
            while (this.packets.size() > MAX_QUEUE) {
                Packet<?> dropped = this.packets.remove(0);
                if (!(dropped instanceof C09PacketHeldItemChange)) {
                    PacketUtil.sendPacketDirect(dropped);
                }
            }
        }

        KillAura killAura = (KillAura) Leader.moduleManager.modules.get(KillAura.class);
        EntityLivingBase target = killAura == null ? null : killAura.getTarget();
        if (packet instanceof C02PacketUseEntity && target != null && target.hurtTime <= 3) {
            this.send();
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

        if (this.mode.getValue() == 0) {
            this.tickVanilla();
        } else {
            this.tickInstant();
        }
    }

    @EventTarget
    public final void onWorld(final LoadWorldEvent event) {
        this.setEnabled(false);
    }

    // ------------------------------------------------------------------
    // Vanilla：正常吃食（1-33 tick），全程可移动 / 可攻击
    // ------------------------------------------------------------------

    private void tickVanilla() {
        ItemStack gapple = this.getGapple();

        // 手上已经没有任何 gapple
        if (gapple == null) {
            if (this.eating) {
                this.finishEat();
                return;
            }
            Notification.addNotification("You haven't any gapple!", "Auto Gapple", NoticeMode.Info);
            this.setEnabled(false);
            return;
        }

        if (this.prevSlot == -1) {
            this.prevSlot = mc.thePlayer.inventory.currentItem;
        }

        // 保证手上拿的是 gapple（被打断/被其它模块换槽后自动切回来）
        if (mc.thePlayer.inventory.currentItem != this.slot) {
            mc.thePlayer.inventory.currentItem = this.slot;
            this.syncSlot();
        }
        this.eating = true;

        boolean holding = this.isHoldingGapple();
        boolean active = holding && mc.thePlayer.isUsingItem();
        int remain = holding ? this.getRemainTicks() : 0;
        int target = this.targetTicks();

        // ---- 1) 正在吃：一直吃到服务端真正消耗掉为止 ----
        // 客户端的 itemInUseCount 由物品自身的 maxItemUseDuration 决定（金苹果 33），
        // 不会因为服务端短一些就提前结束，所以这里不能按 Ticks 截断，否则会比服务端更早放弃。
        if (active && remain > 0) {
            if (this.useTicks == 0) {
                this.startSlot = this.slot;
                this.startSize = gapple.stackSize;
            }
            this.useTicks++;
            this.resumeAttempts = 0;
            this.confirmWait = 0;
            this.c03s = MathHelper.clamp_int(gapple.getMaxItemUseDuration() - remain, 0, target);
            return;
        }

        // ---- 2) 客户端已经吃完（或被中途打断），等服务端扣物品 ----
        // 1.8.9 客户端吃满 maxItemUseDuration 时不会走 onItemUseFinish，
        // 物品数量只能等服务端 setSlot 回包。这段时间绝对不能重发 C08，
        // 否则服务端倒计时被重置，永远吃不完——这是高 CPS 下卡死的根因。
        if (this.useTicks > 0) {
            if (this.isConsumed()) {
                this.finishEat();
                return;
            }
            if (this.confirmWait < CONFIRM_TIMEOUT) {
                this.confirmWait++;
                this.c03s = target;
                return;
            }
            // 被打断，或等了一秒仍未扣物品 => 这一口作废重来
            this.confirmWait = 0;
            this.useTicks = 0;
            this.startSize = -1;
        }

        // ---- 3) 开始新的一口 ----
        if (++this.resumeAttempts > 40) {
            this.resumeAttempts = 0;
            this.finishEat();
            return;
        }
        this.startSlot = this.slot;
        this.startSize = gapple.stackSize;
        this.useTicks = 1;
        this.c03s = 0;
        PacketUtil.sendPacket(new C08PacketPlayerBlockPlacement(gapple));
        mc.thePlayer.setItemInUse(gapple, gapple.getMaxItemUseDuration());
    }

    /** 服务端是否已经把这一口 gapple 扣掉了 */
    private boolean isConsumed() {
        if (this.startSize < 0) {
            return false;
        }
        ItemStack stack = this.startSlot >= 0 && this.startSlot < 9
                ? mc.thePlayer.inventory.getStackInSlot(this.startSlot)
                : null;
        // 原本那一格变空，或者数量变少，都说明这一口吃到了
        return stack == null || stack.stackSize < this.startSize;
    }

    private int getRemainTicks() {
        try {
            return ((IAccessorEntityPlayer) mc.thePlayer).getItemInUseCount();
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private boolean isHoldingGapple() {
        ItemStack held = mc.thePlayer.getHeldItem();
        return held != null && held.getItem() == Items.golden_apple;
    }

    private ItemStack getGapple() {
        ItemStack stack = mc.thePlayer.inventory.getStackInSlot(this.slot);
        if (stack != null && stack.getItem() == Items.golden_apple) {
            return stack;
        }
        int found = findItem2(36, 45, Items.golden_apple);
        if (found == -1) {
            return null;
        }
        this.slot = found - 36;
        stack = mc.thePlayer.inventory.getStackInSlot(this.slot);
        return stack != null && stack.getItem() == Items.golden_apple ? stack : null;
    }

    private void finishEat() {
        this.eating = false;
        this.useTicks = 0;
        this.c03s = 0;
        this.startSize = -1;
        this.startSlot = -1;
        this.resumeAttempts = 0;
        this.confirmWait = 0;

        if (this.autoValue.getValue()) {
            // 自动连续吃：保持启用、保留 gapple 槽，下一 tick 直接开吃下一口
            this.restart = true;
            return;
        }

        this.restart = false;
        this.restoreSlot();
        this.setEnabled(false);
    }

    private void syncSlot() {
        if (mc.playerController == null) {
            return;
        }
        try {
            ((IAccessorPlayerControllerMP) mc.playerController).callSyncCurrentPlayItem();
        } catch (Throwable ignored) {
        }
    }

    private void restoreSlot() {
        if (this.prevSlot == -1) {
            return;
        }
        int target = this.prevSlot;
        this.prevSlot = -1;
        if (mc.thePlayer == null || mc.playerController == null) {
            return;
        }
        if (mc.thePlayer.inventory.currentItem != target) {
            mc.thePlayer.inventory.currentItem = target;
            this.syncSlot();
        }
    }

    // ------------------------------------------------------------------
    // Instant：blink 攒够 Ticks 后一次性补包 + 瞬吃
    // ------------------------------------------------------------------

    private void tickInstant() {
        if (this.blinkTicks < this.targetTicks()) {
            return;
        }
        this.blinkTicks = 0;
        this.c03s = 0;

        if (mc.thePlayer.inventory.currentItem != this.slot) {
            PacketUtil.sendPacketDirect(new C09PacketHeldItemChange(this.slot));
            PacketUtil.sendPacketDirect(new C08PacketPlayerBlockPlacement(mc.thePlayer.inventory.getStackInSlot(this.slot)));
            this.blink();
            PacketUtil.sendPacketDirect(new C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem));
        } else {
            PacketUtil.sendPacketDirect(new C08PacketPlayerBlockPlacement(mc.thePlayer.inventory.getStackInSlot(this.slot)));
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

    // ------------------------------------------------------------------
    // 渲染
    // ------------------------------------------------------------------

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

        int target = this.targetTicks();
        float percentage = Math.min(this.c03s, target) / (float) target;

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

    // ------------------------------------------------------------------
    // 包释放（Instant 模式）
    // ------------------------------------------------------------------

    /**
     * 把缓存里的包一直发到（含）第一个 C02PacketUseEntity 为止，其余留在队列里。
     * 原来是递归实现，队列很长时会栈溢出，这里改成迭代。
     */
    void send() {
        if (this.packets.isEmpty() || mc.getNetHandler() == null)
            return;

        while (!this.packets.isEmpty()) {
            Packet<?> packet = this.packets.remove(0);
            if (packet instanceof C09PacketHeldItemChange
                    || (packet instanceof C07PacketPlayerDigging
                    && ((C07PacketPlayerDigging) packet).getStatus() == C07PacketPlayerDigging.Action.RELEASE_USE_ITEM)) {
                continue;
            }
            PacketUtil.sendPacketDirect(packet);
            if (packet instanceof C02PacketUseEntity) {
                return;
            }
        }
    }

    void blink() {
        if (this.packets.isEmpty() || mc.getNetHandler() == null)
            return;
        while (!this.packets.isEmpty()) {
            Packet<?> packet = this.packets.remove(0);
            if (packet instanceof C09PacketHeldItemChange
                    || (packet instanceof C07PacketPlayerDigging
                    && ((C07PacketPlayerDigging) packet).getStatus() == C07PacketPlayerDigging.Action.RELEASE_USE_ITEM))
                continue;
            PacketUtil.sendPacketDirect(packet);
        }
    }
}
