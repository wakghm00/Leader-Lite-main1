package leader.module.modules.player;

import leader.Leader;
import leader.module.modules.misc.Disabler;
import leader.util.PlayerUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.inventory.ContainerPlayer;
import net.minecraft.item.*;
import net.minecraft.network.play.client.C0DPacketCloseWindow;
import net.minecraft.network.play.client.C16PacketClientStatus;
import net.minecraft.world.WorldSettings.GameType;
import org.apache.commons.lang3.RandomUtils;
import leader.event.EventTarget;
import leader.event.types.EventType;
import leader.events.AttackEvent;
import leader.events.UpdateEvent;
import leader.events.WindowClickEvent;
import leader.module.Module;
import leader.property.properties.BooleanProperty;
import leader.property.properties.IntProperty;
import leader.property.properties.ModeProperty;
import leader.util.ItemUtil;
import leader.util.PacketUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

public class InvManager extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final IntProperty minDelay = new IntProperty("Min Delay", 0, 0, 20);
    public final IntProperty maxDelay = new IntProperty("Max Delay", 0, 0, 20);
    public final IntProperty openDelay = new IntProperty("Open Delay", 0, 0, 20);
    public final ModeProperty mode = new ModeProperty("Mode", 1, new String[]{"Normal", "Instant", "Spoof"});
    public final BooleanProperty silentInv = new BooleanProperty("Silent Inv", false, () -> mode.getValue() != 2);
    public final BooleanProperty autoOpen = new BooleanProperty("AutoOpen",true, () -> mode.getValue() != 2);
    public final BooleanProperty autoClose = new BooleanProperty("Auto Close", false, () -> mode.getValue() != 2);
    public final BooleanProperty autoArmor = new BooleanProperty("Auto Armor", true);
    public final BooleanProperty moreArmor = new BooleanProperty("MoreArmor", false, autoArmor::getValue);
    public final IntProperty moreArmorPairs = new IntProperty("MoreArmorPairs", 1, 1, 6, () -> autoArmor.getValue() && moreArmor.getValue());
    public final BooleanProperty dropTrash = new BooleanProperty("Drop Trash", true);
    public final IntProperty swordSlot = new IntProperty("Sword Slot", 1, 0, 9);
    public final BooleanProperty moreSword = new BooleanProperty("MoreSword", false);
    public final IntProperty moreSwordCount = new IntProperty("MoreSwordCount", 1, 1, 4, moreSword::getValue);
    public final BooleanProperty swordSwitch = new BooleanProperty("SwordSwitch", false, () -> mode.getValue() == 2);
    public final IntProperty swordSwitchDelay = new IntProperty("SwordSwitchDelay", 1, 1, 3, () -> mode.getValue() == 2 && swordSwitch.getValue());
    public final BooleanProperty swordSwitchAxe = new BooleanProperty("SwordSwitchAxe", false, () -> mode.getValue() == 2 && swordSwitch.getValue());
    public final IntProperty pickaxeSlot = new IntProperty("Pickaxe Slot", 8, 0, 9);
    public final IntProperty shovelSlot = new IntProperty("Shovel Slot", 7, 0, 9);
    public final IntProperty axeSlot = new IntProperty("Axe Slot", 9, 0, 9);
    public final IntProperty blocksSlot = new IntProperty("Blocks Slot", 2, 0, 9);
    public final IntProperty blocks = new IntProperty("Blocks", 128, 64, 2304);
    public final IntProperty throwsSlot = new IntProperty("Throws Slot", 4, 0, 9);
    public final IntProperty throwsAmount = new IntProperty("Throws Amount", 64, 16, 320);
    public final BooleanProperty rodThrowable = new BooleanProperty("Rod Throwable", false);
    public final IntProperty gappleSlot = new IntProperty("Gapple Slot", 3, 0, 9);
    public final BooleanProperty keepOre = new BooleanProperty("Keep Ore", true);
    public final BooleanProperty keepWaterBucket = new BooleanProperty("Keep Water Bucket", true);
    public final IntProperty waterBucketSlot = new IntProperty("Water Bucket Slot", 6, 0, 9, keepWaterBucket::getValue);
    public final BooleanProperty keepBowAndArrows = new BooleanProperty("Keep Bow And Arrows", true);
    public final IntProperty bowSlot = new IntProperty("Bow Slot", 5, 0, 9, keepBowAndArrows::getValue);

    private int actionDelay = 0;
    private int oDelay = 0;
    private int swordSwitchTick = 0;
    private int attackTimer = 0;
    private boolean inventoryOpen = false;
    private boolean spoofServerOpen = false;
    private boolean spoofNextClose = false;
    private boolean spoofOpenedThisTick = false;

    public InvManager() {
        super("InvManager", false);
    }

    @Override
    public String[] getSuffix() {
        return new String[]{mode.getModeString()};
    }

    public boolean shouldHideInventory() {
        return this.isEnabled() && this.mode.getValue() != 2 && silentInv.getValue()
               && mc.currentScreen instanceof GuiInventory;
    }

    private boolean isValidGameMode() {
        GameType gameType = mc.playerController.getCurrentGameType();
        return gameType == GameType.SURVIVAL || gameType == GameType.ADVENTURE;
    }

    private int convertSlotIndex(int slot) {
        if (slot >= 36) {
            return 8 - (slot - 36);
        } else {
            return slot <= 8 ? slot + 36 : slot;
        }
    }

    private void clickSlot(int windowId, int slot, int button, int mode) {
        mc.playerController.windowClick(windowId, slot, button, mode, mc.thePlayer);
    }

    private int getStackSize(int slot) {
        if (slot == -1) {
            return 0;
        } else {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(slot);
            return stack != null ? stack.stackSize : 0;
        }
    }

    private boolean isThrowable(ItemStack stack) {
        if (stack == null) return false;
        return stack.getItem() instanceof ItemSnowball || stack.getItem() instanceof ItemEgg
                || (this.rodThrowable.getValue() && stack.getItem() instanceof ItemFishingRod);
    }

    private boolean isGapple(ItemStack stack) {
        if (stack == null) return false;
        return stack.getItem() instanceof ItemAppleGold;
    }

    private boolean isOre(ItemStack stack) {
        if (stack == null) return false;
        Item item = stack.getItem();
        if (item instanceof net.minecraft.item.ItemBlock) {
            net.minecraft.block.Block block = ((net.minecraft.item.ItemBlock) item).getBlock();
            if (block instanceof net.minecraft.block.BlockOre) {
                return true;
            }
        }
        return item == net.minecraft.init.Items.diamond
                || item == net.minecraft.init.Items.emerald
                || item == net.minecraft.init.Items.iron_ingot
                || item == net.minecraft.init.Items.gold_ingot
                || item == net.minecraft.init.Items.coal
                || item == net.minecraft.init.Items.quartz
                || item == net.minecraft.init.Items.redstone;
    }

    private boolean isWaterBucket(ItemStack stack) {
        // 水桶和空桶都保护：MLG 落地舀水后手里是空桶，不能被当垃圾丢掉
        return stack != null && (stack.getItem() == net.minecraft.init.Items.water_bucket || stack.getItem() == net.minecraft.init.Items.bucket);
    }

    private int findWaterBucketSlot(int preferredSlot, boolean hotbarOnly) {
        if (preferredSlot >= 0 && preferredSlot <= 8) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(preferredSlot);
            if (this.isWaterBucket(stack)) {
                return preferredSlot;
            }
        }
        int start = hotbarOnly ? 0 : 9;
        int end = hotbarOnly ? 9 : 36;
        for (int i = start; i < end; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (this.isWaterBucket(stack)) {
                return i;
            }
        }
        return -1;
    }

    private boolean isBow(ItemStack stack) {
        return stack != null && stack.getItem() instanceof ItemBow;
    }

    private int findThrowableSlot(int preferredSlot, boolean hotbarOnly) {
        if (preferredSlot >= 0 && preferredSlot <= 8) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(preferredSlot);
            if (this.isThrowable(stack)) {
                return preferredSlot;
            }
        }
        int start = hotbarOnly ? 0 : 9;
        int end = hotbarOnly ? 9 : 36;
        for (int i = start; i < end; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (this.isThrowable(stack)) {
                return i;
            }
        }
        return -1;
    }

    private int findGappleSlot(int preferredSlot, boolean hotbarOnly) {
        if (preferredSlot >= 0 && preferredSlot <= 8) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(preferredSlot);
            if (this.isGapple(stack)) {
                return preferredSlot;
            }
        }
        int start = hotbarOnly ? 0 : 9;
        int end = hotbarOnly ? 9 : 36;
        for (int i = start; i < end; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (this.isGapple(stack)) {
                return i;
            }
        }
        return -1;
    }

    private int findBowSlot(int preferredSlot, boolean hotbarOnly) {
        if (!keepBowAndArrows.getValue()) return -1;
        if (preferredSlot >= 0 && preferredSlot <= 8) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(preferredSlot);
            if (this.isBow(stack)) {
                return preferredSlot;
            }
        }
        int start = hotbarOnly ? 0 : 9;
        int end = hotbarOnly ? 9 : 36;
        for (int i = start; i < end; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (this.isBow(stack)) {
                return i;
            }
        }
        return -1;
    }

    private boolean isArmorPiece(ItemStack stack, int armorType) {
        return stack != null && stack.getItem() instanceof ItemArmor
                && ((ItemArmor) stack.getItem()).armorType == armorType;
    }

    private boolean isMoreArmorActive() {
        return this.autoArmor.getValue() && this.moreArmor.getValue();
    }

    private double getArmorScore(ItemStack stack) {
        if (stack == null || !(stack.getItem() instanceof ItemArmor)) return -1.0;
        double durability = 1.0;
        if (stack.getMaxDamage() > 0) {
            durability = (double) (stack.getMaxDamage() - stack.getItemDamage()) / (double) stack.getMaxDamage();
        }
        // 护甲值优先，剩余耐久用于同护甲值时的取舍
        return ItemUtil.getArmorProtection(stack) + durability * 0.001;
    }

    private boolean isArmorDepleted(ItemStack stack) {
        return stack != null && stack.getMaxDamage() > 0
                && stack.getMaxDamage() - stack.getItemDamage() <= 0;
    }

    /**
     * 整理背包时用于保护护甲的槽位列表（按四种护甲各一项）。
     * More Armor 开启时护甲保护完全交给 getProtectedArmorSlots，
     * 这样耐久耗尽或超出保留数量的护甲才能被正常丢弃。
     */
    private ArrayList<Integer> getArmorSlotsForTrash(boolean checkDurability) {
        ArrayList<Integer> slots = new ArrayList<>(Arrays.asList(-1, -1, -1, -1));
        if (this.isMoreArmorActive()) return slots;
        for (int i = 0; i < 4; i++) {
            slots.set(i, ItemUtil.findArmorInventorySlot(i, checkDurability));
        }
        return slots;
    }

    /**
     * More Armor: 计算背包内需要保留的备用护甲槽位。
     * 每种护甲（头盔/胸甲/护腿/靴子）按护甲值与剩余耐久排序，保留前 pairs 件。
     * 若要更换护甲，则多保留一件，保证换装后背包里仍有 pairs 件备用。
     */
    private Set<Integer> getProtectedArmorSlots() {
        Set<Integer> slots = new HashSet<>();
        if (!this.isMoreArmorActive()) return slots;
        int pairs = this.moreArmorPairs.getValue();
        for (int armorType = 0; armorType < 4; armorType++) {
            int keep = pairs + (this.needsArmorSwap(armorType) ? 1 : 0);
            ArrayList<Integer> candidates = new ArrayList<>();
            for (int i = 0; i < 36; i++) {
                ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
                // 耐久为 0 的护甲已经没有价值，不再保留
                if (this.isArmorPiece(stack, armorType) && !this.isArmorDepleted(stack)) {
                    candidates.add(i);
                }
            }
            candidates.sort((a, b) -> Double.compare(
                    this.getArmorScore(mc.thePlayer.inventory.getStackInSlot(b)),
                    this.getArmorScore(mc.thePlayer.inventory.getStackInSlot(a))
            ));
            for (int i = 0; i < Math.min(keep, candidates.size()); i++) {
                slots.add(candidates.get(i));
            }
        }
        return slots;
    }

    private int findBestArmorBackupSlot(int armorType) {
        int bestSlot = -1;
        double bestScore = 0.0;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (!this.isArmorPiece(stack, armorType)) continue;
            if (this.isArmorDepleted(stack)) continue;
            double score = this.getArmorScore(stack);
            if (score > bestScore) {
                bestScore = score;
                bestSlot = i;
            }
        }
        return bestSlot;
    }

    /**
     * More Armor: 判断某个护甲槽是否需要更换。
     * 所穿护甲耐久耗尽，或背包里有防护更高的备用护甲时返回 true。
     * 注意这里不使用耐久度参与比较，否则护甲每掉一点耐久都会触发无意义的来回替换。
     */
    private boolean needsArmorSwap(int armorType) {
        if (!this.isMoreArmorActive()) {
            return false;
        }
        int playerArmorSlot = 39 - armorType;
        int backupSlot = this.findBestArmorBackupSlot(armorType);
        if (backupSlot == -1) {
            return false;
        }
        ItemStack worn = mc.thePlayer.inventory.getStackInSlot(playerArmorSlot);
        if (worn == null || this.isArmorDepleted(worn)) {
            return true;
        }
        return ItemUtil.getArmorProtection(mc.thePlayer.inventory.getStackInSlot(backupSlot))
               > ItemUtil.getArmorProtection(worn);
    }

    /**
     * 穿上一件护甲。More Armor 开启时，优先换上背包里最好的备用护甲。
     * @return 是否执行了操作
     */
    private boolean equipArmor(int armorType) {
        int playerArmorSlot = 39 - armorType;
        int targetSlot;
        if (this.isMoreArmorActive()) {
            if (!this.needsArmorSwap(armorType)) {
                return false;
            }
            targetSlot = this.findBestArmorBackupSlot(armorType);
        } else {
            int equippedSlot = ItemUtil.findArmorInventorySlot(armorType, true);
            int inventorySlot = ItemUtil.findArmorInventorySlot(armorType, false);
            targetSlot = equippedSlot != -1 ? equippedSlot : inventorySlot;
            if (targetSlot == -1 || targetSlot == playerArmorSlot) {
                return false;
            }
        }
        if (mc.thePlayer.inventory.getStackInSlot(playerArmorSlot) != null) {
            if (mc.thePlayer.inventory.getFirstEmptyStack() != -1) {
                this.clickSlot(mc.thePlayer.inventoryContainer.windowId, this.convertSlotIndex(playerArmorSlot), 0, 1);
            } else {
                this.clickSlot(mc.thePlayer.inventoryContainer.windowId, this.convertSlotIndex(playerArmorSlot), 1, 4);
            }
        } else {
            this.clickSlot(mc.thePlayer.inventoryContainer.windowId, this.convertSlotIndex(targetSlot), 0, 1);
        }
        return true;
    }

    private int getTotalThrowsCount() {
        int count = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (this.isThrowable(stack)) {
                count += stack.stackSize;
            }
        }
        return count;
    }

    private boolean isSwordLike(ItemStack stack) {
        if (stack == null) return false;
        if (stack.getItem() instanceof ItemSword) return true;
        // 斧子是否算武器只取决于配置，不随攻击状态变化，避免脱战时斧子被当垃圾丢掉
        return this.isSwordSwitchEnabled() && this.swordSwitchAxe.getValue()
                && stack.getItem() instanceof ItemAxe;
    }

    /**
     * Sword Switch 是否被配置开启（Spoof 模式）。
     * 只用于「保留剑/斧」这类需要长期稳定的判断，实际换剑请看 {@link #isSwordSwitchActive()}。
     */
    private boolean isSwordSwitchEnabled() {
        return this.isEnabled() && this.mode.getValue() == 2 && this.swordSwitch.getValue();
    }

    /**
     * Sword Switch 实际生效：配置开启 + 当前正在攻击。
     * SwordSwitch / SwordSwitchAxe 只在攻击时启用，脱战后不再循环换剑。
     */
    private boolean isSwordSwitchActive() {
        return this.isSwordSwitchEnabled() && this.isAttackActive();
    }

    /**
     * 是否正处于交战状态。
     * 只认真正的攻击（{@link AttackEvent}），不把「按住左键挖方块」算作攻击。
     */
    private boolean isAttackActive() {
        return this.attackTimer > 0;
    }

    /**
     * 是否需要对多余的剑做保留/丢弃管理。
     * MoreSword 所有模式都生效；SwordSwitch 只在 Spoof 模式生效。
     */
    private boolean isSwordRetentionActive() {
        return this.isEnabled()
                && (this.moreSword.getValue() || this.isSwordSwitchEnabled());
    }

    private boolean isSwordDepleted(ItemStack stack) {
        return this.isArmorDepleted(stack);
    }

    /**
     * Sword Slot 里是否已经放着一把可用的剑/斧。
     */
    private boolean isSwordSlotReady() {
        int target = this.swordSlot.getValue() - 1;
        if (target < 0 || target > 8) return false;
        ItemStack held = mc.thePlayer.inventory.getStackInSlot(target);
        return this.isSwordLike(held) && !this.isSwordDepleted(held);
    }

    /**
     * Sword Switch 正在接管 Sword Slot（Spoof 模式 + Switch 开启 + 槽里已有剑）。
     * 此时不能再要求“最好的剑必须在 Sword Slot”，否则会被循环换剑反复打乱。
     * 槽里还没有剑时必须返回 false，让正常整理流程先把剑放进去。
     */
    private boolean isSwordSwitchEngaged() {
        return this.isSwordSwitchActive() && this.isSwordSlotReady();
    }

    private double getSwordScore(ItemStack stack) {
        if (stack == null || !this.isSwordLike(stack)) return -1.0;
        double score = ItemUtil.getAttackBonus(stack);
        if (stack.getMaxDamage() > 0) {
            score += (double) (stack.getMaxDamage() - stack.getItemDamage())
                     / (double) stack.getMaxDamage() * 0.001;
        }
        return score;
    }

    /**
     * 按评分从高到低收集背包内的剑（SwordSwitchAxe 开启时含斧）。
     * 耐久耗尽的不计入。
     */
    private ArrayList<Integer> getSortedSwordSlots() {
        ArrayList<Integer> slots = new ArrayList<>();
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (!this.isSwordLike(stack) || this.isSwordDepleted(stack)) continue;
            slots.add(i);
        }
        slots.sort((a, b) -> Double.compare(
                this.getSwordScore(mc.thePlayer.inventory.getStackInSlot(b)),
                this.getSwordScore(mc.thePlayer.inventory.getStackInSlot(a))
        ));
        return slots;
    }

    /**
     * More Sword: 背包内需要保留的备选剑槽位（按评分取前 count 把）。
     * 若开启 Sword Switch，则在排序列表上多让出一把，配合循环换剑。
     */
    private Set<Integer> getProtectedSwordSlots() {
        Set<Integer> slots = new HashSet<>();
        if (!this.isSwordRetentionActive()) return slots;
        ArrayList<Integer> sorted = this.getSortedSwordSlots();
        // Sword Slot 里已有的（未耗尽的）剑始终保留，避免整理过程中被当成垃圾丢掉
        if (this.isSwordSlotReady()) {
            slots.add(this.swordSlot.getValue() - 1);
        }
        if (!this.moreSword.getValue()) {
            // 没开 MoreSword 但开了 SwordSwitch 时，切换需要多把剑，全部保留
            slots.addAll(sorted);
            return slots;
        }
        // MoreSwordCount 是「额外多留」的数量，Sword Slot 里那把另算
        int keep = this.moreSwordCount.getValue() + (this.isSwordSlotReady() ? 1 : 0);
        // 只要开着 SwordSwitch（不论此刻是否在攻击）就多留一把，避免脱战时剑被丢光
        if (this.isSwordSwitchEnabled()) keep++;
        for (int i = 0; i < Math.min(keep, sorted.size()); i++) {
            slots.add(sorted.get(i));
        }
        return slots;
    }

    private int getSwordSwitchInterval() {
        // delay 1-3 范围，按 tick 计
        return Math.max(1, this.swordSwitchDelay.getValue());
    }

    /**
     * Sword Switch: 当前是否还有可循环的下一把剑。
     * 用于判断“背包是否已整理好”，避免无剑可换时一直判定为未整理。
     */
    private boolean canCycleSwordSwitch() {
        if (!this.isSwordSwitchActive()) return false;
        int target = this.swordSlot.getValue() - 1;
        if (target < 0 || target > 8) return false;
        if (!this.isSwordSlotReady()) return false;
        ArrayList<Integer> slots = this.getSortedSwordSlots();
        if (slots.size() < 2) return false;
        for (int slot : slots) {
            if (slot != target) return true;
        }
        return false;
    }

    /**
     * Sword Switch: 循环把背包中的下一把剑换到 Sword Slot。
     * @return 是否执行了换剑点击
     */
    private boolean cycleSwordSwitch() {
        if (!this.isSwordSwitchActive()) return false;
        // 未到切换间隔时不做任何换剑
        if (this.swordSwitchTick > 0) return false;
        ArrayList<Integer> slots = this.getSortedSwordSlots();
        if (slots.size() < 2) return false;

        int target = this.swordSlot.getValue() - 1;
        if (target < 0 || target > 8) return false;

        // Sword Slot 里还不是可用的剑时，先交给正常整理流程把它填好
        if (!this.isSwordSlotReady()) return false;

        int current = slots.indexOf(target);
        int nextIndex = current == -1 ? 0 : (current + 1) % slots.size();
        int nextSlot = slots.get(nextIndex);
        if (nextSlot == target) {
            nextIndex = (nextIndex + 1) % slots.size();
            nextSlot = slots.get(nextIndex);
        }
        if (nextSlot == target) return false;

        this.swordSwitchTick = this.getSwordSwitchInterval();
        this.clickSlot(mc.thePlayer.inventoryContainer.windowId, this.convertSlotIndex(nextSlot), target, 2);
        return true;
    }

    private void spoofOpen() {
        if (!spoofServerOpen) {
            PacketUtil.sendPacket(new C16PacketClientStatus(C16PacketClientStatus.EnumState.OPEN_INVENTORY_ACHIEVEMENT));
            spoofServerOpen = true;
            spoofOpenedThisTick = true;
        }
    }

    private void spoofClose() {
        if (spoofServerOpen) {
            PacketUtil.sendPacket(new C0DPacketCloseWindow(mc.thePlayer.inventoryContainer.windowId));
            spoofServerOpen = false;
        }
    }

    private boolean isInventorySorted() {
        if (!isValidGameMode()) return true;

        // Sword Switch: 冷却结束时返回 false，让整理流程打开背包并执行一次换剑
        if (this.swordSwitchTick <= 0 && this.canCycleSwordSwitch()) {
            return false;
        }

        int preferredSwordHotbarSlot = this.swordSlot.getValue() - 1;
        int equippedSwordSlot = ItemUtil.findSwordInInventorySlot(preferredSwordHotbarSlot, true);
        int inventorySwordSlot = ItemUtil.findSwordInInventorySlot(preferredSwordHotbarSlot, false);

        int secondSwordSlot = -1;
        int prefSecondSwordSlot = -1;
        Disabler disabler = (Disabler) Leader.moduleManager.getModule(Disabler.class);
        if (disabler != null && disabler.isEnabled() && disabler.c09.getValue()) {
            int mainSword = equippedSwordSlot != -1 ? equippedSwordSlot : inventorySwordSlot;
            if (mainSword != -1) {
                secondSwordSlot = Disabler.findSecondSwordSlot(mainSword);
                prefSecondSwordSlot = disabler.secondSwordSlot.getValue() - 1;
            }
        }

        int preferredPickaxeHotbarSlot = this.pickaxeSlot.getValue() - 1;
        int equippedPickaxeSlot = ItemUtil.findInventorySlot("pickaxe", preferredPickaxeHotbarSlot, true);
        int inventoryPickaxeSlot = ItemUtil.findInventorySlot("pickaxe", preferredPickaxeHotbarSlot, false);

        int preferredShovelHotbarSlot = this.shovelSlot.getValue() - 1;
        int equippedShovelSlot = ItemUtil.findInventorySlot("shovel", preferredShovelHotbarSlot, true);
        int inventoryShovelSlot = ItemUtil.findInventorySlot("shovel", preferredShovelHotbarSlot, false);

        int preferredAxeHotbarSlot = this.axeSlot.getValue() - 1;
        int equippedAxeSlot = ItemUtil.findInventorySlot("axe", preferredAxeHotbarSlot, true);
        int inventoryAxeSlot = ItemUtil.findInventorySlot("axe", preferredAxeHotbarSlot, false);

        int preferredBlocksHotbarSlot = this.blocksSlot.getValue() - 1;
        int inventoryBlocksSlot = ItemUtil.findInventorySlot(preferredBlocksHotbarSlot);

        int preferredThrowsHotbarSlot = this.throwsSlot.getValue() - 1;
        int equippedThrowsSlot = this.findThrowableSlot(preferredThrowsHotbarSlot, true);
        int inventoryThrowsSlot = this.findThrowableSlot(preferredThrowsHotbarSlot, false);

        int preferredGappleHotbarSlot = this.gappleSlot.getValue() - 1;
        int equippedGappleSlot = this.findGappleSlot(preferredGappleHotbarSlot, true);
        int inventoryGappleSlot = this.findGappleSlot(preferredGappleHotbarSlot, false);

        int preferredWaterBucketHotbarSlot = this.waterBucketSlot.getValue() - 1;
        int equippedWaterBucketSlot = -1;
        int inventoryWaterBucketSlot = -1;
        if (keepWaterBucket.getValue()) {
            int water = this.findWaterBucketSlot(preferredWaterBucketHotbarSlot, true);
            if (water != -1) equippedWaterBucketSlot = water;
            else inventoryWaterBucketSlot = this.findWaterBucketSlot(preferredWaterBucketHotbarSlot, false);
        }

        int preferredBowHotbarSlot = this.bowSlot.getValue() - 1;
        int equippedBowSlot = -1;
        int inventoryBowSlot = -1;
        if (keepBowAndArrows.getValue()) {
            int bestBow = ItemUtil.getBestBowSlot();
            if (bestBow != -1) {
                if (bestBow <= 8) equippedBowSlot = bestBow;
                else inventoryBowSlot = bestBow;
            }
        }
        if (this.autoArmor.getValue()) {
            for (int i = 0; i < 4; i++) {
                int playerArmorSlot = 39 - i;
                // More Armor: 所穿护甲耐久耗尽且背包里有备用护甲时，需要打开背包进行替换
                if (this.isMoreArmorActive()) {
                    if (this.needsArmorSwap(i)) {
                        return false;
                    }
                    continue;
                }
                int equippedSlot = ItemUtil.findArmorInventorySlot(i, true);
                int inventorySlot = ItemUtil.findArmorInventorySlot(i, false);
                if (equippedSlot != -1 || inventorySlot != -1) {
                    if (equippedSlot != playerArmorSlot && inventorySlot != playerArmorSlot) {
                        return false;
                    }
                }
            }
        }
        // SwordSwitch 接管 Sword Slot 后，不能再用“最好的剑必须在该槽”来判断是否需要整理
        if (!this.isSwordSwitchEngaged()
                && preferredSwordHotbarSlot >= 0 && preferredSwordHotbarSlot <= 8 && (equippedSwordSlot != -1 || inventorySwordSlot != -1)) {
            if (equippedSwordSlot != preferredSwordHotbarSlot && inventorySwordSlot != preferredSwordHotbarSlot)
                return false;
        }
        if (preferredPickaxeHotbarSlot >= 0 && preferredPickaxeHotbarSlot <= 8 && (equippedPickaxeSlot != -1 || inventoryPickaxeSlot != -1)) {
            if (equippedPickaxeSlot != preferredPickaxeHotbarSlot && inventoryPickaxeSlot != preferredPickaxeHotbarSlot)
                return false;
        }
        if (preferredShovelHotbarSlot >= 0 && preferredShovelHotbarSlot <= 8 && (equippedShovelSlot != -1 || inventoryShovelSlot != -1)) {
            if (equippedShovelSlot != preferredShovelHotbarSlot && inventoryShovelSlot != preferredShovelHotbarSlot)
                return false;
        }
        if (preferredAxeHotbarSlot >= 0 && preferredAxeHotbarSlot <= 8 && (equippedAxeSlot != -1 || inventoryAxeSlot != -1)) {
            if (equippedAxeSlot != preferredAxeHotbarSlot && inventoryAxeSlot != preferredAxeHotbarSlot)
                return false;
        }
        if (preferredBlocksHotbarSlot >= 0 && preferredBlocksHotbarSlot <= 8 && inventoryBlocksSlot != -1) {
            if (inventoryBlocksSlot != preferredBlocksHotbarSlot) return false;
        }
        if (preferredThrowsHotbarSlot >= 0 && preferredThrowsHotbarSlot <= 8 && (equippedThrowsSlot != -1 || inventoryThrowsSlot != -1)) {
            if (equippedThrowsSlot != preferredThrowsHotbarSlot && inventoryThrowsSlot != preferredThrowsHotbarSlot)
                return false;
        }
        if (preferredGappleHotbarSlot >= 0 && preferredGappleHotbarSlot <= 8 && (equippedGappleSlot != -1 || inventoryGappleSlot != -1)) {
            if (equippedGappleSlot != preferredGappleHotbarSlot && inventoryGappleSlot != preferredGappleHotbarSlot)
                return false;
        }
        if (keepWaterBucket.getValue() && preferredWaterBucketHotbarSlot >= 0 && preferredWaterBucketHotbarSlot <= 8 && (equippedWaterBucketSlot != -1 || inventoryWaterBucketSlot != -1)) {
            if (equippedWaterBucketSlot != preferredWaterBucketHotbarSlot && inventoryWaterBucketSlot != preferredWaterBucketHotbarSlot)
                return false;
        }
        if (keepBowAndArrows.getValue() && preferredBowHotbarSlot >= 0 && preferredBowHotbarSlot <= 8 && (equippedBowSlot != -1 || inventoryBowSlot != -1)) {
            if (equippedBowSlot != preferredBowHotbarSlot && inventoryBowSlot != preferredBowHotbarSlot)
                return false;
        }

        if (this.dropTrash.getValue()) {
            ArrayList<Integer> equippedArmorSlots = this.getArmorSlotsForTrash(true);
            ArrayList<Integer> inventoryArmorSlots = this.getArmorSlotsForTrash(false);
            Set<Integer> protectedArmorSlots = this.getProtectedArmorSlots();
            Set<Integer> protectedSwordSlots = this.getProtectedSwordSlots();

            int currentBlockCount = this.getStackSize(inventoryBlocksSlot);
            int totalThrowsCount = this.getTotalThrowsCount();

            if (totalThrowsCount > this.throwsAmount.getValue()) {
                for (int i = 0; i < 36; i++) {
                    if (!equippedArmorSlots.contains(i) && !inventoryArmorSlots.contains(i)
                            && !protectedArmorSlots.contains(i) && !protectedSwordSlots.contains(i)
                            && equippedSwordSlot != i && inventorySwordSlot != i && secondSwordSlot != i && prefSecondSwordSlot != i
                            && equippedPickaxeSlot != i && inventoryPickaxeSlot != i
                            && equippedShovelSlot != i && inventoryShovelSlot != i
                            && equippedAxeSlot != i && inventoryAxeSlot != i
                            && inventoryBlocksSlot != i && equippedThrowsSlot != i
                            && inventoryThrowsSlot != i && equippedGappleSlot != i
                            && inventoryGappleSlot != i
                            && equippedWaterBucketSlot != i && inventoryWaterBucketSlot != i
                            && equippedBowSlot != i && inventoryBowSlot != i) {
                        ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
                        if (this.isThrowable(stack)) return false;
                    }
                }
            }

            for (int i = 0; i < 36; i++) {
                if (!equippedArmorSlots.contains(i) && !inventoryArmorSlots.contains(i)
                        && !protectedArmorSlots.contains(i) && !protectedSwordSlots.contains(i)
                        && equippedSwordSlot != i && inventorySwordSlot != i && secondSwordSlot != i && prefSecondSwordSlot != i
                        && equippedPickaxeSlot != i && inventoryPickaxeSlot != i
                        && equippedShovelSlot != i && inventoryShovelSlot != i
                        && equippedAxeSlot != i && inventoryAxeSlot != i
                        && inventoryBlocksSlot != i && equippedThrowsSlot != i
                        && inventoryThrowsSlot != i && equippedGappleSlot != i
                        && inventoryGappleSlot != i
                        && equippedWaterBucketSlot != i && inventoryWaterBucketSlot != i
                        && equippedBowSlot != i && inventoryBowSlot != i) {
                    ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
                    if (stack != null) {
                        boolean isBlock = ItemUtil.isBlock(stack);
                        boolean isThrowable = this.isThrowable(stack);
                        boolean isGapple = this.isGapple(stack);
                        boolean isOre = this.isOre(stack);
                        boolean isProtectedWater = keepWaterBucket.getValue() && isWaterBucket(stack);
                        boolean isProtectedBowArrow = false;
                        if (keepBowAndArrows.getValue()) {
                            if (ItemUtil.isArrow(stack)) {
                                isProtectedBowArrow = true;
                            } else if (isBow(stack)) {
                                int bestBow = ItemUtil.getBestBowSlot();
                                isProtectedBowArrow = (bestBow == -1 || i == bestBow);
                            }
                        }

                        if (!keepOre.getValue() && isOre) return false;
                        else if (!isThrowable && !isOre && !isGapple && !isProtectedWater && !isProtectedBowArrow
                                && (ItemUtil.isNotSpecialItem(stack) || (isBlock && currentBlockCount >= this.blocks.getValue())))
                            return false;
                        if (isBlock) currentBlockCount += stack.stackSize;
                    }
                }
            }
        }
        return true;
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (event.getType() == EventType.PRE) {
            this.spoofOpenedThisTick = false;
            if (this.actionDelay > 0) {
                this.actionDelay--;
            }
            if (this.oDelay > 0) {
                this.oDelay--;
            }
            if (this.swordSwitchTick > 0) {
                this.swordSwitchTick--;
            }
            if (this.attackTimer > 0) {
                this.attackTimer--;
            }
            if (this.isEnabled() && this.isValidGameMode() && this.mode.getValue() == 2) {
                if (this.actionDelay > 0) return;
                if (mc.thePlayer.isUsingItem()) return;
                if (isInventorySorted()) {
                    if (spoofServerOpen) spoofNextClose = true;
                    if (spoofNextClose) { spoofClose(); spoofNextClose = false; }
                    return;
                }

                if (spoofNextClose) { spoofClose(); spoofNextClose = false; return; }
                spoofOpen();
                this.oDelay = 0;
                this.inventoryOpen = true;
                if (this.spoofOpenedThisTick) return;
            }
            if (!isInventorySorted() && !(mc.currentScreen instanceof GuiInventory) && autoOpen.getValue() && this.isEnabled() && !PlayerUtil.isUsingItem() && !mc.thePlayer.isUsingItem() && !Leader.moduleManager.getModule(Scaffold.class).isEnabled()){
                mc.displayGuiScreen(new GuiInventory(mc.thePlayer));
                this.oDelay = 0;
                this.inventoryOpen = true;
                return;
            }
            boolean isInventoryOpen = (mc.currentScreen instanceof GuiInventory)
                    || (this.mode.getValue() == 2 && spoofServerOpen);
            if (!isInventoryOpen) {
                this.inventoryOpen = false;
            } else if ((mc.currentScreen instanceof GuiInventory) && !(((GuiInventory) mc.currentScreen).inventorySlots instanceof ContainerPlayer)) {
                this.inventoryOpen = false;
            } else {
                if (!this.inventoryOpen) {
                    this.inventoryOpen = true;
                    this.oDelay = this.openDelay.getValue() + 1;
                }
                if (this.oDelay <= 0 && (this.mode.getValue() == 1 || this.actionDelay <= 0)) {
                    if (this.isEnabled() && this.isValidGameMode()) {
                        ArrayList<Integer> equippedArmorSlots = this.getArmorSlotsForTrash(true);
                        ArrayList<Integer> inventoryArmorSlots = this.getArmorSlotsForTrash(false);
                        Set<Integer> protectedArmorSlots = this.getProtectedArmorSlots();
                        Set<Integer> protectedSwordSlots = this.getProtectedSwordSlots();
                        int preferredSwordHotbarSlot = this.swordSlot.getValue() - 1;
                        int equippedSwordSlot = ItemUtil.findSwordInInventorySlot(preferredSwordHotbarSlot, true);
                        int inventorySwordSlot = ItemUtil.findSwordInInventorySlot(preferredSwordHotbarSlot, false);
                        int secondSwordSlot = -1;
                        int preferredPickaxeHotbarSlot = this.pickaxeSlot.getValue() - 1;
                        int equippedPickaxeSlot = ItemUtil.findInventorySlot("pickaxe", preferredPickaxeHotbarSlot, true);
                        int inventoryPickaxeSlot = ItemUtil.findInventorySlot("pickaxe", preferredPickaxeHotbarSlot, false);
                        int preferredShovelHotbarSlot = this.shovelSlot.getValue() - 1;
                        int equippedShovelSlot = ItemUtil.findInventorySlot("shovel", preferredShovelHotbarSlot, true);
                        int inventoryShovelSlot = ItemUtil.findInventorySlot("shovel", preferredShovelHotbarSlot, false);
                        int preferredAxeHotbarSlot = this.axeSlot.getValue() - 1;
                        int equippedAxeSlot = ItemUtil.findInventorySlot("axe", preferredAxeHotbarSlot, true);
                        int inventoryAxeSlot = ItemUtil.findInventorySlot("axe", preferredAxeHotbarSlot, false);
                        int preferredBlocksHotbarSlot = this.blocksSlot.getValue() - 1;
                        int inventoryBlocksSlot = ItemUtil.findInventorySlot(preferredBlocksHotbarSlot);
                        int preferredThrowsHotbarSlot = this.throwsSlot.getValue() - 1;
                        int equippedThrowsSlot = this.findThrowableSlot(preferredThrowsHotbarSlot, true);
                        int inventoryThrowsSlot = this.findThrowableSlot(preferredThrowsHotbarSlot, false);
                        int preferredGappleHotbarSlot = this.gappleSlot.getValue() - 1;
                        int equippedGappleSlot = this.findGappleSlot(preferredGappleHotbarSlot, true);
                        int inventoryGappleSlot = this.findGappleSlot(preferredGappleHotbarSlot, false);
                        int preferredWaterBucketHotbarSlot = this.waterBucketSlot.getValue() - 1;
                        int equippedWaterBucketSlot = -1;
                        int inventoryWaterBucketSlot = -1;
                        if (keepWaterBucket.getValue()) {
                            int water = this.findWaterBucketSlot(preferredWaterBucketHotbarSlot, true);
                            if (water != -1) equippedWaterBucketSlot = water;
                            else inventoryWaterBucketSlot = this.findWaterBucketSlot(preferredWaterBucketHotbarSlot, false);
                        }
                        int preferredBowHotbarSlot = this.bowSlot.getValue() - 1;
                        int equippedBowSlot = -1;
                        int inventoryBowSlot = -1;
                        if (keepBowAndArrows.getValue()) {
                            int bestBow = ItemUtil.getBestBowSlot();
                            if (bestBow != -1) {
                                if (bestBow <= 8) equippedBowSlot = bestBow;
                                else inventoryBowSlot = bestBow;
                            }
                        }
                        Disabler disabler = (Disabler) Leader.moduleManager.getModule(Disabler.class);
                        int prefSecondSwordSlot = -1;
                        if (disabler != null && disabler.isEnabled() && disabler.c09.getValue()) {
                            int mainSword = equippedSwordSlot != -1 ? equippedSwordSlot : inventorySwordSlot;
                            if (mainSword != -1) {
                                secondSwordSlot = Disabler.findSecondSwordSlot(mainSword);
                                prefSecondSwordSlot = disabler.secondSwordSlot.getValue() - 1;
                            }
                        }

                        if (this.mode.getValue() == 0 || this.mode.getValue() == 2) {
                            if (this.autoArmor.getValue()) {
                                for (int i = 0; i < 4; i++) {
                                    if (this.equipArmor(i)) {
                                        return;
                                    }
                                }
                            }
                            if (this.cycleSwordSwitch()) {
                                return;
                            }
                            LinkedHashSet<Integer> usedHotbarSlots = new LinkedHashSet<>();
                            if (this.isSwordSwitchEngaged()) {
                                // 循环换剑正在接管该槽位，只占住槽位、不让别的工具顶掉它
                                if (preferredSwordHotbarSlot >= 0 && preferredSwordHotbarSlot <= 8) {
                                    usedHotbarSlots.add(preferredSwordHotbarSlot);
                                }
                            } else if (preferredSwordHotbarSlot >= 0 && preferredSwordHotbarSlot <= 8 && (equippedSwordSlot != -1 || inventorySwordSlot != -1)) {
                                usedHotbarSlots.add(preferredSwordHotbarSlot);
                                if (equippedSwordSlot != preferredSwordHotbarSlot && inventorySwordSlot != preferredSwordHotbarSlot) {
                                    int slot = equippedSwordSlot != -1 ? equippedSwordSlot : inventorySwordSlot;
                                    this.clickSlot(mc.thePlayer.inventoryContainer.windowId, this.convertSlotIndex(slot), preferredSwordHotbarSlot, 2);
                                    return;
                                }
                            }
                            if (secondSwordSlot != -1 && secondSwordSlot != preferredSwordHotbarSlot
                                    && prefSecondSwordSlot >= 0 && prefSecondSwordSlot <= 8
                                    && prefSecondSwordSlot != preferredSwordHotbarSlot) {
                                usedHotbarSlots.add(prefSecondSwordSlot);
                                if (secondSwordSlot != prefSecondSwordSlot) {
                                    this.clickSlot(mc.thePlayer.inventoryContainer.windowId, this.convertSlotIndex(secondSwordSlot), prefSecondSwordSlot, 2);
                                    return;
                                }
                            }
                            if (preferredPickaxeHotbarSlot >= 0 && preferredPickaxeHotbarSlot <= 8 && !usedHotbarSlots.contains(preferredPickaxeHotbarSlot) && (equippedPickaxeSlot != -1 || inventoryPickaxeSlot != -1)) {
                                usedHotbarSlots.add(preferredPickaxeHotbarSlot);
                                if (equippedPickaxeSlot != preferredPickaxeHotbarSlot && inventoryPickaxeSlot != preferredPickaxeHotbarSlot) {
                                    int slot = equippedPickaxeSlot != -1 ? equippedPickaxeSlot : inventoryPickaxeSlot;
                                    this.clickSlot(mc.thePlayer.inventoryContainer.windowId, this.convertSlotIndex(slot), preferredPickaxeHotbarSlot, 2);
                                    return;
                                }
                            }
                            if (preferredShovelHotbarSlot >= 0 && preferredShovelHotbarSlot <= 8 && !usedHotbarSlots.contains(preferredShovelHotbarSlot) && (equippedShovelSlot != -1 || inventoryShovelSlot != -1)) {
                                usedHotbarSlots.add(preferredShovelHotbarSlot);
                                if (equippedShovelSlot != preferredShovelHotbarSlot && inventoryShovelSlot != preferredShovelHotbarSlot) {
                                    int slot = equippedShovelSlot != -1 ? equippedShovelSlot : inventoryShovelSlot;
                                    this.clickSlot(mc.thePlayer.inventoryContainer.windowId, this.convertSlotIndex(slot), preferredShovelHotbarSlot, 2);
                                    return;
                                }
                            }
                            if (preferredAxeHotbarSlot >= 0 && preferredAxeHotbarSlot <= 8 && !usedHotbarSlots.contains(preferredAxeHotbarSlot) && (equippedAxeSlot != -1 || inventoryAxeSlot != -1)) {
                                usedHotbarSlots.add(preferredAxeHotbarSlot);
                                if (equippedAxeSlot != preferredAxeHotbarSlot && inventoryAxeSlot != preferredAxeHotbarSlot) {
                                    int slot = equippedAxeSlot != -1 ? equippedAxeSlot : inventoryAxeSlot;
                                    this.clickSlot(mc.thePlayer.inventoryContainer.windowId, this.convertSlotIndex(slot), preferredAxeHotbarSlot, 2);
                                    return;
                                }
                            }
                            if (preferredBlocksHotbarSlot >= 0 && preferredBlocksHotbarSlot <= 8 && !usedHotbarSlots.contains(preferredBlocksHotbarSlot) && inventoryBlocksSlot != -1) {
                                usedHotbarSlots.add(preferredBlocksHotbarSlot);
                                if (inventoryBlocksSlot != preferredBlocksHotbarSlot) {
                                    this.clickSlot(mc.thePlayer.inventoryContainer.windowId, this.convertSlotIndex(inventoryBlocksSlot), preferredBlocksHotbarSlot, 2);
                                    return;
                                }
                            }
                            if (preferredThrowsHotbarSlot >= 0 && preferredThrowsHotbarSlot <= 8 && !usedHotbarSlots.contains(preferredThrowsHotbarSlot) && (equippedThrowsSlot != -1 || inventoryThrowsSlot != -1)) {
                                usedHotbarSlots.add(preferredThrowsHotbarSlot);
                                if (equippedThrowsSlot != preferredThrowsHotbarSlot && inventoryThrowsSlot != preferredThrowsHotbarSlot) {
                                    int slot = equippedThrowsSlot != -1 ? equippedThrowsSlot : inventoryThrowsSlot;
                                    this.clickSlot(mc.thePlayer.inventoryContainer.windowId, this.convertSlotIndex(slot), preferredThrowsHotbarSlot, 2);
                                    return;
                                }
                            }
                            if (preferredGappleHotbarSlot >= 0 && preferredGappleHotbarSlot <= 8 && !usedHotbarSlots.contains(preferredGappleHotbarSlot) && (equippedGappleSlot != -1 || inventoryGappleSlot != -1)) {
                                usedHotbarSlots.add(preferredGappleHotbarSlot);
                                if (equippedGappleSlot != preferredGappleHotbarSlot && inventoryGappleSlot != preferredGappleHotbarSlot) {
                                    int slot = equippedGappleSlot != -1 ? equippedGappleSlot : inventoryGappleSlot;
                                    this.clickSlot(mc.thePlayer.inventoryContainer.windowId, this.convertSlotIndex(slot), preferredGappleHotbarSlot, 2);
                                    return;
                                }
                            }
                            if (keepWaterBucket.getValue() && preferredWaterBucketHotbarSlot >= 0 && preferredWaterBucketHotbarSlot <= 8 && !usedHotbarSlots.contains(preferredWaterBucketHotbarSlot) && (equippedWaterBucketSlot != -1 || inventoryWaterBucketSlot != -1)) {
                                usedHotbarSlots.add(preferredWaterBucketHotbarSlot);
                                if (equippedWaterBucketSlot != preferredWaterBucketHotbarSlot && inventoryWaterBucketSlot != preferredWaterBucketHotbarSlot) {
                                    int slot = equippedWaterBucketSlot != -1 ? equippedWaterBucketSlot : inventoryWaterBucketSlot;
                                    this.clickSlot(mc.thePlayer.inventoryContainer.windowId, this.convertSlotIndex(slot), preferredWaterBucketHotbarSlot, 2);
                                    return;
                                }
                            }
                            if (keepBowAndArrows.getValue() && preferredBowHotbarSlot >= 0 && preferredBowHotbarSlot <= 8 && !usedHotbarSlots.contains(preferredBowHotbarSlot) && (equippedBowSlot != -1 || inventoryBowSlot != -1)) {
                                usedHotbarSlots.add(preferredBowHotbarSlot);
                                if (equippedBowSlot != preferredBowHotbarSlot && inventoryBowSlot != preferredBowHotbarSlot) {
                                    int slot = equippedBowSlot != -1 ? equippedBowSlot : inventoryBowSlot;
                                    this.clickSlot(mc.thePlayer.inventoryContainer.windowId, this.convertSlotIndex(slot), preferredBowHotbarSlot, 2);
                                    return;
                                }
                            }

                            if (this.dropTrash.getValue()) {
                                int currentBlockCount = this.getStackSize(inventoryBlocksSlot);
                                int totalThrowsCount = this.getTotalThrowsCount();
                                if (totalThrowsCount > this.throwsAmount.getValue()) {
                                    for (int i = 35; i >= 0; i--) {
                                        if (!equippedArmorSlots.contains(i) && !inventoryArmorSlots.contains(i)
                                                && !protectedArmorSlots.contains(i) && !protectedSwordSlots.contains(i)
                                                && equippedSwordSlot != i && inventorySwordSlot != i && secondSwordSlot != i && prefSecondSwordSlot != i && prefSecondSwordSlot != i
                                                && equippedPickaxeSlot != i && inventoryPickaxeSlot != i
                                                && equippedShovelSlot != i && inventoryShovelSlot != i
                                                && equippedAxeSlot != i && inventoryAxeSlot != i
                                                && inventoryBlocksSlot != i && equippedThrowsSlot != i
                                                && inventoryThrowsSlot != i && equippedGappleSlot != i
                                                && inventoryGappleSlot != i
                                                && equippedWaterBucketSlot != i && inventoryWaterBucketSlot != i
                                                && equippedBowSlot != i && inventoryBowSlot != i) {
                                            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
                                            if (this.isThrowable(stack)) {
                                                this.clickSlot(mc.thePlayer.inventoryContainer.windowId, this.convertSlotIndex(i), 1, 4);
                                                return;
                                            }
                                        }
                                    }
                                }
                                for (int i = 0; i < 36; i++) {
                                    if (!equippedArmorSlots.contains(i) && !inventoryArmorSlots.contains(i)
                                            && !protectedArmorSlots.contains(i) && !protectedSwordSlots.contains(i)
                                            && equippedSwordSlot != i && inventorySwordSlot != i && secondSwordSlot != i && prefSecondSwordSlot != i && prefSecondSwordSlot != i
                                            && equippedPickaxeSlot != i && inventoryPickaxeSlot != i
                                            && equippedShovelSlot != i && inventoryShovelSlot != i
                                            && equippedAxeSlot != i && inventoryAxeSlot != i
                                            && inventoryBlocksSlot != i && equippedThrowsSlot != i
                                            && inventoryThrowsSlot != i && equippedGappleSlot != i
                                            && inventoryGappleSlot != i
                                            && equippedWaterBucketSlot != i && inventoryWaterBucketSlot != i
                                            && equippedBowSlot != i && inventoryBowSlot != i) {
                                        ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
                                        if (stack != null) {
                                            boolean isBlock = ItemUtil.isBlock(stack);
                                            boolean isThrowable = this.isThrowable(stack);
                                            boolean isGapple = this.isGapple(stack);
                                            boolean isOre = this.isOre(stack);
                                            boolean isProtectedWater = keepWaterBucket.getValue() && isWaterBucket(stack);
                                            boolean isProtectedBowArrow = false;
                                            if (keepBowAndArrows.getValue()) {
                                                if (ItemUtil.isArrow(stack)) {
                                                    isProtectedBowArrow = true;
                                                } else if (isBow(stack)) {
                                                    int bestBow = ItemUtil.getBestBowSlot();
                                                    isProtectedBowArrow = (bestBow == -1 || i == bestBow);
                                                }
                                            }

                                            if (!keepOre.getValue() && isOre) {
                                                this.clickSlot(mc.thePlayer.inventoryContainer.windowId, this.convertSlotIndex(i), 1, 4);
                                                return;
                                            } else if (!isThrowable && !isOre && !isGapple && !isProtectedWater && !isProtectedBowArrow
                                                    && (ItemUtil.isNotSpecialItem(stack) || (isBlock && currentBlockCount >= this.blocks.getValue()))) {
                                                this.clickSlot(mc.thePlayer.inventoryContainer.windowId, this.convertSlotIndex(i), 1, 4);
                                                return;
                                            }
                                            if (isBlock) currentBlockCount += stack.stackSize;
                                        }
                                    }
                                }
                            }
                        } else if (this.mode.getValue() == 1) {
                            if (this.autoArmor.getValue()) {
                                for (int i = 0; i < 4; i++) {
                                    this.equipArmor(i);
                                }
                            }
                            LinkedHashSet<Integer> usedHotbarSlots = new LinkedHashSet<>();
                            int prefSword = swordSlot.getValue() - 1;
                            if (prefSword >= 0 && prefSword <= 8) {
                                int eqSword = ItemUtil.findSwordInInventorySlot(prefSword, true);
                                int invSword = ItemUtil.findSwordInInventorySlot(prefSword, false);
                                if (eqSword != -1 || invSword != -1) {
                                    usedHotbarSlots.add(prefSword);
                                    if (eqSword != prefSword && invSword != prefSword) {
                                        int slot = eqSword != -1 ? eqSword : invSword;
                                        clickSlot(mc.thePlayer.inventoryContainer.windowId, convertSlotIndex(slot), prefSword, 2);
                                    }
                                }
                                // Disabler C09: sort second sword in Instant mode
                                if (secondSwordSlot != -1 && secondSwordSlot != prefSword
                                        && prefSecondSwordSlot >= 0 && prefSecondSwordSlot <= 8
                                        && prefSecondSwordSlot != prefSword) {
                                    usedHotbarSlots.add(prefSecondSwordSlot);
                                    if (secondSwordSlot != prefSecondSwordSlot) {
                                        clickSlot(mc.thePlayer.inventoryContainer.windowId, convertSlotIndex(secondSwordSlot), prefSecondSwordSlot, 2);
                                    }
                                }
                            }
                            int prefPick = pickaxeSlot.getValue() - 1;
                            if (prefPick >= 0 && prefPick <= 8 && !usedHotbarSlots.contains(prefPick)) {
                                int eqPick = ItemUtil.findInventorySlot("pickaxe", prefPick, true);
                                int invPick = ItemUtil.findInventorySlot("pickaxe", prefPick, false);
                                if (eqPick != -1 || invPick != -1) {
                                    usedHotbarSlots.add(prefPick);
                                    if (eqPick != prefPick && invPick != prefPick) {
                                        int slot = eqPick != -1 ? eqPick : invPick;
                                        clickSlot(mc.thePlayer.inventoryContainer.windowId, convertSlotIndex(slot), prefPick, 2);
                                    }
                                }
                            }
                            int prefShovel = shovelSlot.getValue() - 1;
                            if (prefShovel >= 0 && prefShovel <= 8 && !usedHotbarSlots.contains(prefShovel)) {
                                int eqShovel = ItemUtil.findInventorySlot("shovel", prefShovel, true);
                                int invShovel = ItemUtil.findInventorySlot("shovel", prefShovel, false);
                                if (eqShovel != -1 || invShovel != -1) {
                                    usedHotbarSlots.add(prefShovel);
                                    if (eqShovel != prefShovel && invShovel != prefShovel) {
                                        int slot = eqShovel != -1 ? eqShovel : invShovel;
                                        clickSlot(mc.thePlayer.inventoryContainer.windowId, convertSlotIndex(slot), prefShovel, 2);
                                    }
                                }
                            }
                            int prefAxe = axeSlot.getValue() - 1;
                            if (prefAxe >= 0 && prefAxe <= 8 && !usedHotbarSlots.contains(prefAxe)) {
                                int eqAxe = ItemUtil.findInventorySlot("axe", prefAxe, true);
                                int invAxe = ItemUtil.findInventorySlot("axe", prefAxe, false);
                                if (eqAxe != -1 || invAxe != -1) {
                                    usedHotbarSlots.add(prefAxe);
                                    if (eqAxe != prefAxe && invAxe != prefAxe) {
                                        int slot = eqAxe != -1 ? eqAxe : invAxe;
                                        clickSlot(mc.thePlayer.inventoryContainer.windowId, convertSlotIndex(slot), prefAxe, 2);
                                    }
                                }
                            }
                            int prefBlocks = blocksSlot.getValue() - 1;
                            if (prefBlocks >= 0 && prefBlocks <= 8 && !usedHotbarSlots.contains(prefBlocks)) {
                                int invBlocks = ItemUtil.findInventorySlot(prefBlocks);
                                if (invBlocks != -1) {
                                    usedHotbarSlots.add(prefBlocks);
                                    if (invBlocks != prefBlocks) {
                                        clickSlot(mc.thePlayer.inventoryContainer.windowId, convertSlotIndex(invBlocks), prefBlocks, 2);
                                    }
                                }
                            }
                            int prefThrows = throwsSlot.getValue() - 1;
                            if (prefThrows >= 0 && prefThrows <= 8 && !usedHotbarSlots.contains(prefThrows)) {
                                int eqThrows = findThrowableSlot(prefThrows, true);
                                int invThrows = findThrowableSlot(prefThrows, false);
                                if (eqThrows != -1 || invThrows != -1) {
                                    usedHotbarSlots.add(prefThrows);
                                    if (eqThrows != prefThrows && invThrows != prefThrows) {
                                        int slot = eqThrows != -1 ? eqThrows : invThrows;
                                        clickSlot(mc.thePlayer.inventoryContainer.windowId, convertSlotIndex(slot), prefThrows, 2);
                                    }
                                }
                            }
                            int prefGapple = gappleSlot.getValue() - 1;
                            if (prefGapple >= 0 && prefGapple <= 8 && !usedHotbarSlots.contains(prefGapple)) {
                                int eqGapple = findGappleSlot(prefGapple, true);
                                int invGapple = findGappleSlot(prefGapple, false);
                                if (eqGapple != -1 || invGapple != -1) {
                                    usedHotbarSlots.add(prefGapple);
                                    if (eqGapple != prefGapple && invGapple != prefGapple) {
                                        int slot = eqGapple != -1 ? eqGapple : invGapple;
                                        clickSlot(mc.thePlayer.inventoryContainer.windowId, convertSlotIndex(slot), prefGapple, 2);
                                    }
                                }
                            }
                            int prefWaterBucket = waterBucketSlot.getValue() - 1;
                            if (keepWaterBucket.getValue() && prefWaterBucket >= 0 && prefWaterBucket <= 8 && !usedHotbarSlots.contains(prefWaterBucket)) {
                                int eqWater = findWaterBucketSlot(prefWaterBucket, true);
                                int invWater = findWaterBucketSlot(prefWaterBucket, false);
                                if (eqWater != -1 || invWater != -1) {
                                    usedHotbarSlots.add(prefWaterBucket);
                                    if (eqWater != prefWaterBucket && invWater != prefWaterBucket) {
                                        int slot = eqWater != -1 ? eqWater : invWater;
                                        clickSlot(mc.thePlayer.inventoryContainer.windowId, convertSlotIndex(slot), prefWaterBucket, 2);
                                    }
                                }
                            }
                            if (keepBowAndArrows.getValue()) {
                                int prefBow = bowSlot.getValue() - 1;
                                if (prefBow >= 0 && prefBow <= 8 && !usedHotbarSlots.contains(prefBow)) {
                                    int eqBow = findBowSlot(prefBow, true);
                                    int invBow = findBowSlot(prefBow, false);
                                    if (eqBow != -1 || invBow != -1) {
                                        usedHotbarSlots.add(prefBow);
                                        if (eqBow != prefBow && invBow != prefBow) {
                                            int slot = eqBow != -1 ? eqBow : invBow;
                                            clickSlot(mc.thePlayer.inventoryContainer.windowId, convertSlotIndex(slot), prefBow, 2);
                                        }
                                    }
                                }
                            }
                            ArrayList<Integer> itemsToDrop = new ArrayList<>();
                            ArrayList<Integer> eqArmor = this.getArmorSlotsForTrash(true);
                            ArrayList<Integer> invArmor = this.getArmorSlotsForTrash(false);
                            int eqSwordDrop = ItemUtil.findSwordInInventorySlot(swordSlot.getValue() - 1, true);
                            int invSwordDrop = ItemUtil.findSwordInInventorySlot(swordSlot.getValue() - 1, false);
                            int eqPickDrop = ItemUtil.findInventorySlot("pickaxe", pickaxeSlot.getValue() - 1, true);
                            int invPickDrop = ItemUtil.findInventorySlot("pickaxe", pickaxeSlot.getValue() - 1, false);
                            int eqShovelDrop = ItemUtil.findInventorySlot("shovel", shovelSlot.getValue() - 1, true);
                            int invShovelDrop = ItemUtil.findInventorySlot("shovel", shovelSlot.getValue() - 1, false);
                            int eqAxeDrop = ItemUtil.findInventorySlot("axe", axeSlot.getValue() - 1, true);
                            int invAxeDrop = ItemUtil.findInventorySlot("axe", axeSlot.getValue() - 1, false);
                            int invBlocksDrop = ItemUtil.findInventorySlot(blocksSlot.getValue() - 1);
                            int eqThrowsDrop = findThrowableSlot(throwsSlot.getValue() - 1, true);
                            int invThrowsDrop = findThrowableSlot(throwsSlot.getValue() - 1, false);
                            int eqGappleDrop = findGappleSlot(gappleSlot.getValue() - 1, true);
                            int invGappleDrop = findGappleSlot(gappleSlot.getValue() - 1, false);
                            int eqWaterDrop = findWaterBucketSlot(waterBucketSlot.getValue() - 1, true);
                            int invWaterDrop = findWaterBucketSlot(waterBucketSlot.getValue() - 1, false);
                            int eqBowDrop = findBowSlot(bowSlot.getValue() - 1, true);
                            int invBowDrop = findBowSlot(bowSlot.getValue() - 1, false);

                            int currentBlockCount = getStackSize(invBlocksDrop);
                            int totalThrowsCount = getTotalThrowsCount();

                            if (totalThrowsCount > throwsAmount.getValue()) {
                                for (int i = 35; i >= 0; i--) {
                                    if (!eqArmor.contains(i) && !invArmor.contains(i)
                                            && !protectedArmorSlots.contains(i) && !protectedSwordSlots.contains(i)
                                            && eqSwordDrop != i && invSwordDrop != i && secondSwordSlot != i && prefSecondSwordSlot != i && prefSecondSwordSlot != i
                                            && eqPickDrop != i && invPickDrop != i
                                            && eqShovelDrop != i && invShovelDrop != i
                                            && eqAxeDrop != i && invAxeDrop != i
                                            && invBlocksDrop != i && eqThrowsDrop != i
                                            && invThrowsDrop != i && eqGappleDrop != i
                                            && invGappleDrop != i
                                            && eqWaterDrop != i && invWaterDrop != i
                                            && eqBowDrop != i && invBowDrop != i) {
                                        ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
                                        if (isThrowable(stack)) {
                                            itemsToDrop.add(i);
                                        }
                                    }
                                }
                            }
                            for (int i = 0; i < 36; i++) {
                                if (!eqArmor.contains(i) && !invArmor.contains(i)
                                        && !protectedArmorSlots.contains(i) && !protectedSwordSlots.contains(i)
                                        && eqSwordDrop != i && invSwordDrop != i && secondSwordSlot != i && prefSecondSwordSlot != i && prefSecondSwordSlot != i
                                        && eqPickDrop != i && invPickDrop != i
                                        && eqShovelDrop != i && invShovelDrop != i
                                        && eqAxeDrop != i && invAxeDrop != i
                                        && invBlocksDrop != i && eqThrowsDrop != i
                                        && invThrowsDrop != i && eqGappleDrop != i
                                        && invGappleDrop != i
                                        && eqWaterDrop != i && invWaterDrop != i
                                        && !itemsToDrop.contains(i)) {
                                    ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
                                    if (stack != null) {
                                        if (keepBowAndArrows.getValue() && isBow(stack)) {
                                            int bestBow = ItemUtil.getBestBowSlot();
                                            if (bestBow != -1 && i != bestBow) {
                                                itemsToDrop.add(i);
                                            }
                                            continue;
                                        }
                                        if (eqBowDrop != i && invBowDrop != i) {
                                            boolean isBlock = ItemUtil.isBlock(stack);
                                            boolean isThrowable = isThrowable(stack);
                                            boolean isGapple = isGapple(stack);
                                            boolean isOre = isOre(stack);
                                            boolean isProtectedWater = keepWaterBucket.getValue() && isWaterBucket(stack);
                                            boolean isProtectedBowArrow = keepBowAndArrows.getValue() && ItemUtil.isArrow(stack);

                                            if (!keepOre.getValue() && isOre) {
                                                itemsToDrop.add(i);
                                            } else if (!isThrowable && !isOre && !isGapple && !isProtectedWater && !isProtectedBowArrow
                                                    && (ItemUtil.isNotSpecialItem(stack) || (isBlock && currentBlockCount >= blocks.getValue()))) {
                                                itemsToDrop.add(i);
                                            }
                                            if (isBlock) currentBlockCount += stack.stackSize;
                                        }
                                    }
                                }
                            }
                            for (int slot : itemsToDrop) {
                                clickSlot(mc.thePlayer.inventoryContainer.windowId, convertSlotIndex(slot), 1, 4);
                            }
                        }

                        if (this.autoClose.getValue() && isInventorySorted()) {
                            mc.thePlayer.closeScreen();
                        }
                    }
                }
            }
        }
    }

    /**
     * Sword Switch / SwordSwitchAxe 只在攻击时启用：
     * 每次攻击后保持 0.5 秒的攻击状态，覆盖光环的出手间隔，脱战后立刻停止换剑。
     */
    @EventTarget
    public void onAttack(AttackEvent event) {
        if (!this.isEnabled()) return;
        if (this.mode.getValue() != 2 || !this.swordSwitch.getValue()) return;
        this.attackTimer = 10;
    }

    @EventTarget
    public void onClick(WindowClickEvent event) {
        if (this.minDelay.getValue() == 0 && this.maxDelay.getValue() == 0) {
            this.actionDelay = 0;
        } else {
            this.actionDelay = RandomUtils.nextInt(
                    this.minDelay.getValue() + 1,
                    this.maxDelay.getValue() + 2
            );
        }
    }

    @Override
    public void onEnabled() {
        this.attackTimer = 0;
        this.swordSwitchTick = 0;
        if (mode.getValue() == 2) {
            spoofServerOpen = false;
            spoofNextClose = false;
        }
    }

    @Override
    public void onDisabled() {
        this.attackTimer = 0;
        this.swordSwitchTick = 0;
        if (mode.getValue() == 2) {
            spoofClose();
            spoofServerOpen = false;
            spoofNextClose = false;
        }
    }

    @Override
    public void verifyValue(String string) {
        switch (string) {
            case "min-delay":
                if (this.minDelay.getValue() > this.maxDelay.getValue()) {
                    this.maxDelay.setValue(this.minDelay.getValue());
                }
                break;
            case "max-delay":
                if (this.minDelay.getValue() > this.maxDelay.getValue()) {
                    this.minDelay.setValue(this.maxDelay.getValue());
                }
        }
    }
}
