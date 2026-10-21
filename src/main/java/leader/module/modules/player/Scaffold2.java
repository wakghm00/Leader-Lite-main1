package leader.module.modules.player;

import leader.Leader;
import leader.event.EventTarget;
import leader.event.types.EventType;
import leader.event.types.Priority;
import leader.events.LivingUpdateEvent;
import leader.events.LoadWorldEvent;
import leader.events.MoveInputEvent;
import leader.events.UpdateEvent;
import leader.management.RotationState;
import leader.module.Module;
import leader.property.properties.BooleanProperty;
import leader.property.properties.FloatProperty;
import leader.property.properties.IntProperty;
import leader.property.properties.ModeProperty;
import leader.util.BlockUtil;
import leader.util.ItemUtil;
import leader.util.MoveUtil;
import leader.util.RotationUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

public class Scaffold2 extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final int ROTATION_PRIORITY = 6;

    public final ModeProperty blockSelection = new ModeProperty(
            "Block Selection", 0, new String[]{"Maximum", "Nearest", "Skip Amount"});
    public final IntProperty skipAmount = new IntProperty("Skip Amount", 1, 0, 64,
            () -> blockSelection.getValue() == 2);
    public final FloatProperty searchRange = new FloatProperty("Search Range", 4.5F, 2.5F, 6.0F);
    public final IntProperty placeDelay = new IntProperty("Place Delay", 0, 0, 5);
    public final FloatProperty rotationSpeed = new FloatProperty("Rotation Speed", 180.0F, 1.0F, 180.0F);
    public final BooleanProperty diagonalPriority = new BooleanProperty("Diagonal Priority", true);
    public final BooleanProperty telly = new BooleanProperty("Telly", true);
    public final BooleanProperty rotation = new BooleanProperty("Rotation", true);
    public final BooleanProperty swing = new BooleanProperty("Swing", true);
    public final BooleanProperty itemSpoof = new BooleanProperty("Item Spoof", false);

    private Target target;
    private int originalSlot = -1;
    private int delayTicks;
    private boolean legacyWasEnabled;

    public Scaffold2() {
        super("Scaffold2", false);
    }

    @Override
    public String[] getSuffix() {
        return new String[]{blockSelection.getModeString()};
    }

    @Override
    public void onEnabled() {
        legacyWasEnabled = false;
        if (Leader.moduleManager != null) {
            Module legacy = Leader.moduleManager.getModule(Scaffold.class);
            if (legacy != null && legacy.isEnabled()) {
                legacyWasEnabled = true;
                legacy.setEnabled(false);
            }
        }
        originalSlot = mc.thePlayer == null ? -1 : mc.thePlayer.inventory.currentItem;
        delayTicks = 0;
        target = null;
    }

    @Override
    public void onDisabled() {
        target = null;
        delayTicks = 0;
        if (mc.thePlayer != null && originalSlot >= 0 && originalSlot < 9) {
            mc.thePlayer.inventory.currentItem = originalSlot;
        }
        if (legacyWasEnabled && Leader.moduleManager != null) {
            Module legacy = Leader.moduleManager.getModule(Scaffold.class);
            if (legacy != null && !legacy.isEnabled()) legacy.setEnabled(true);
        }
        legacyWasEnabled = false;
        originalSlot = -1;
    }

    @EventTarget(Priority.HIGH)
    public void onUpdate(UpdateEvent event) {
        if (!isEnabled() || event.getType() != EventType.PRE) return;
        if (mc.thePlayer == null || mc.theWorld == null || mc.playerController == null || mc.currentScreen != null) {
            target = null;
            return;
        }

        int slot = chooseBlockSlot();
        if (slot < 0) {
            target = null;
            return;
        }
        if (mc.thePlayer.inventory.currentItem != slot) {
            mc.thePlayer.inventory.currentItem = slot;
        }

        target = findTarget(event.getYaw());
        if (target == null) return;

        if (rotation.getValue()) {
            float yaw = stepRotation(event.getYaw(), target.yaw, rotationSpeed.getValue());
            float pitch = stepPitch(event.getPitch(), target.pitch, rotationSpeed.getValue());
            event.setRotation(yaw, pitch, ROTATION_PRIORITY);
            event.setPervRotation(yaw, ROTATION_PRIORITY);
            target.aimYaw = yaw;
            target.aimPitch = pitch;
        } else {
            target.aimYaw = event.getYaw();
            target.aimPitch = event.getPitch();
        }

        if (delayTicks > 0) {
            delayTicks--;
            return;
        }
        if (tryPlace(target)) {
            delayTicks = placeDelay.getValue();
            target = null;
        }
    }

    @EventTarget
    public void onMoveInput(MoveInputEvent event) {
        if (!isEnabled() || mc.thePlayer == null) return;
        if (RotationState.isActived() && RotationState.getPriority() == ROTATION_PRIORITY
                && MoveUtil.isForwardPressed()) {
            MoveUtil.fixStrafe(RotationState.getSmoothedYaw());
        }
    }

    @EventTarget
    public void onLivingUpdate(LivingUpdateEvent event) {
        if (!isEnabled() || !telly.getValue() || mc.thePlayer == null || mc.currentScreen != null) return;
        if (mc.thePlayer.onGround && !mc.gameSettings.keyBindJump.isKeyDown()
                && MoveUtil.isForwardPressed() && mc.thePlayer.movementInput != null) {
            mc.thePlayer.movementInput.jump = true;
        }
    }

    @EventTarget
    public void onWorldLoad(LoadWorldEvent event) {
        target = null;
        delayTicks = 0;
    }

    private void disableLegacyScaffold() {
        if (Leader.moduleManager == null) return;
        Module legacy = Leader.moduleManager.getModule(Scaffold.class);
        if (legacy != null && legacy.isEnabled()) legacy.setEnabled(false);
    }

    private int chooseBlockSlot() {
        int selected = mc.thePlayer.inventory.currentItem;
        ItemStack held = mc.thePlayer.inventory.getStackInSlot(selected);
        boolean skipHeld = blockSelection.getValue() == 2
                && ItemUtil.isBlock(held) && held.stackSize <= skipAmount.getValue();
        int bestSlot = -1;
        int bestCount = -1;
        int bestDistance = Integer.MAX_VALUE;

        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(slot);
            if (!ItemUtil.isBlock(stack) || (skipHeld && slot == selected)) continue;
            int count = stack.stackSize;
            int distance = Math.abs(slot - selected);
            boolean better;
            if (blockSelection.getValue() == 1 || blockSelection.getValue() == 2) {
                better = distance < bestDistance
                        || distance == bestDistance && (bestSlot < 0 || slot < bestSlot);
            } else {
                better = count > bestCount
                        || count == bestCount && (distance < bestDistance
                        || distance == bestDistance && (bestSlot < 0 || slot < bestSlot));
            }
            if (better) {
                bestSlot = slot;
                bestCount = count;
                bestDistance = distance;
            }
        }
        return bestSlot;
    }

    private Target findTarget(float currentYaw) {
        BlockPos below = new BlockPos(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ).down();
        Vec3 eyes = mc.thePlayer.getPositionEyes(1.0F);
        Target best = null;
        double bestScore = Double.MAX_VALUE;
        float movementYaw = MoveUtil.getMoveYaw();

        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                BlockPos place = below.add(x, 0, z);
                if (!BlockUtil.isReplaceable(place)) continue;

                for (EnumFacing face : EnumFacing.values()) {
                    BlockPos support = place.offset(face.getOpposite());
                    if (!isValidSupport(support)) continue;

                    Vec3 hit = getFacePoint(support, face);
                    float[] rotations = RotationUtil.getRotations(
                            hit.xCoord, hit.yCoord, hit.zCoord,
                            eyes.xCoord, eyes.yCoord, eyes.zCoord);
                    rotations[1] = MathHelper.clamp_float(rotations[1], -90.0F, 90.0F);
                    if (eyes.squareDistanceTo(hit) > searchRange.getValue() * searchRange.getValue()) continue;

                    MovingObjectPosition trace = RotationUtil.rayTrace(
                            rotations[0], rotations[1], searchRange.getValue(), 1.0F);
                    if (!isValidTrace(trace, support, face)) continue;

                    double score = eyes.squareDistanceTo(hit);
                    if (diagonalPriority.getValue()) {
                        float diagonal = diagonalYaw(movementYaw);
                        float delta = Math.abs(MathHelper.wrapAngleTo180_float(rotations[0] - diagonal));
                        score += delta * 0.01D;
                    }
                    if (best == null || score < bestScore) {
                        bestScore = score;
                        best = new Target(support, face, hit, rotations[0], rotations[1]);
                    }
                }
            }
        }
        return best;
    }

    private boolean tryPlace(Target candidate) {
        if (!BlockUtil.isReplaceable(candidate.support.offset(candidate.face))) return false;
        if (!isValidSupport(candidate.support)) return false;

        MovingObjectPosition trace = RotationUtil.rayTrace(
                candidate.aimYaw, candidate.aimPitch, searchRange.getValue(), 1.0F);
        if (!isValidTrace(trace, candidate.support, candidate.face)) return false;

        ItemStack held = mc.thePlayer.inventory.getCurrentItem();
        if (!ItemUtil.isBlock(held)) return false;
        boolean placed = mc.playerController.onPlayerRightClick(
                mc.thePlayer, mc.theWorld, held, candidate.support, candidate.face, trace.hitVec);
        if (!placed) return false;

        if (swing.getValue()) mc.thePlayer.swingItem();
        else mc.getNetHandler().addToSendQueue(new net.minecraft.network.play.client.C0APacketAnimation());
        return true;
    }

    private boolean isValidSupport(BlockPos position) {
        return !BlockUtil.isReplaceable(position)
                && !BlockUtil.isInteractable(position)
                && BlockUtil.isSolid(mc.theWorld.getBlockState(position).getBlock());
    }

    private boolean isValidTrace(MovingObjectPosition trace, BlockPos support, EnumFacing face) {
        return trace != null
                && trace.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                && support.equals(trace.getBlockPos())
                && face == trace.sideHit;
    }

    private Vec3 getFacePoint(BlockPos support, EnumFacing face) {
        double x = support.getX() + 0.5D;
        double y = support.getY() + 0.5D;
        double z = support.getZ() + 0.5D;
        switch (face) {
            case DOWN: y = support.getY() + 0.05D; break;
            case UP: y = support.getY() + 0.95D; break;
            case NORTH: z = support.getZ() + 0.05D; break;
            case SOUTH: z = support.getZ() + 0.95D; break;
            case WEST: x = support.getX() + 0.05D; break;
            case EAST: x = support.getX() + 0.95D; break;
            default: break;
        }
        return new Vec3(x, y, z);
    }

    private float stepRotation(float current, float target, float speed) {
        float delta = MathHelper.wrapAngleTo180_float(target - current);
        return MathHelper.wrapAngleTo180_float(current + MathHelper.clamp_float(delta, -speed, speed));
    }

    private float stepPitch(float current, float target, float speed) {
        return MathHelper.clamp_float(current + MathHelper.clamp_float(target - current, -speed, speed), -90.0F, 90.0F);
    }

    private float diagonalYaw(float yaw) {
        return MathHelper.wrapAngleTo180_float(Math.round((yaw - 45.0F) / 90.0F) * 90.0F + 45.0F);
    }

    public int getSlot() {
        return originalSlot;
    }

    private static final class Target {
        private final BlockPos support;
        private final EnumFacing face;
        private final Vec3 hit;
        private final float yaw;
        private final float pitch;
        private float aimYaw;
        private float aimPitch;

        private Target(BlockPos support, EnumFacing face, Vec3 hit, float yaw, float pitch) {
            this.support = support;
            this.face = face;
            this.hit = hit;
            this.yaw = yaw;
            this.pitch = pitch;
            this.aimYaw = yaw;
            this.aimPitch = pitch;
        }
    }
}
