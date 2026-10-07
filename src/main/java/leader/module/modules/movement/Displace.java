package leader.module.modules.movement;

import leader.Leader;
import leader.enums.BlinkModules;
import leader.event.EventTarget;
import leader.event.types.EventType;
import leader.event.types.Priority;
import leader.events.MoveInputEvent;
import leader.events.PacketEvent;
import leader.events.Render3DEvent;
import leader.events.TickEvent;
import leader.events.UpdateEvent;
import leader.module.Module;
import leader.module.modules.combat.KillAura;
import leader.property.properties.BooleanProperty;
import leader.property.properties.FloatProperty;
import leader.property.properties.IntProperty;
import leader.property.properties.ModeProperty;
import leader.util.RotationUtil;
import leader.util.TeamUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import org.lwjgl.opengl.GL11;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class Displace extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final int WINDOW_TICKS = 10;
    private static final int SCAN_DIRECTIONS = 32;
    private static final int SCAN_RINGS = 12;
    private static final int SCAN_DEPTH = 10;
    private static final double SCAN_STEP = 0.5D;
    private static final double DYNAMIC_DISTANCE = 6.0D;

    public final ModeProperty angle = new ModeProperty("angle", 0, new String[]{"STATIC", "DYNAMIC"});
    public final FloatProperty yawOffset = new FloatProperty("yaw-offset", 90.0F, 0.0F, 180.0F);
    public final IntProperty delay = new IntProperty("delay", 0, 0, 500);
    public final ModeProperty direction = new ModeProperty("direction", 0, new String[]{"LEFT", "RIGHT"});
    public final BooleanProperty showDirection = new BooleanProperty("show-direction", true);
    public final BooleanProperty findVoid = new BooleanProperty("find-void", false, () -> angle.getValue() == 0);
    public final BooleanProperty blink = new BooleanProperty("blink", false);
    public final BooleanProperty ignoreTeammates = new BooleanProperty("ignore-teammates", true);
    public final BooleanProperty requireKnockback = new BooleanProperty("has-knockback", false);

    private final double[] scanX = new double[SCAN_DIRECTIONS];
    private final double[] scanZ = new double[SCAN_DIRECTIONS];
    private final Map<Integer, Integer> targetWindows = new HashMap<>();
    private EntityPlayer target;
    private float displacementYaw;
    private boolean displaceThisTick;
    private boolean compensateNextTick;
    private boolean displaceLeft;
    private boolean releaseBlinkNextTick;
    private int ticks;

    public Displace() {
        super("Displace", false);
        for (int i = 0; i < SCAN_DIRECTIONS; i++) {
            double radians = Math.PI * 2.0D * i / SCAN_DIRECTIONS;
            scanX[i] = Math.cos(radians);
            scanZ[i] = Math.sin(radians);
        }
    }

    @Override
    public String[] getSuffix() {
        return new String[]{delay.getValue() + "ms"};
    }

    @Override
    public void onEnabled() {
        resetState();
    }

    @Override
    public void onDisabled() {
        resetState();
        Leader.blinkManager.setBlinkState(false, BlinkModules.DISPLACE);
    }

    private void resetState() {
        target = null;
        displaceThisTick = false;
        compensateNextTick = false;
        releaseBlinkNextTick = false;
        ticks = 0;
        targetWindows.clear();
    }

    private boolean moving() {
        return mc.gameSettings.keyBindForward.isKeyDown()
                || mc.gameSettings.keyBindBack.isKeyDown()
                || mc.gameSettings.keyBindLeft.isKeyDown()
                || mc.gameSettings.keyBindRight.isKeyDown();
    }

    private EntityPlayer getTarget() {
        KillAura killAura = (KillAura) Leader.moduleManager.modules.get(KillAura.class);
        if (killAura != null && killAura.isEnabled() && killAura.isAttackAllowed()
                && killAura.getTarget() instanceof EntityPlayer) {
            return (EntityPlayer) killAura.getTarget();
        }
        if (!mc.gameSettings.keyBindAttack.isKeyDown() || mc.theWorld == null) {
            return null;
        }
        EntityPlayer closest = null;
        double closestDistance = 9.0D * 9.0D;
        for (Entity entity : mc.theWorld.loadedEntityList) {
            if (!(entity instanceof EntityPlayer) || entity == mc.thePlayer || entity.isDead) {
                continue;
            }
            EntityPlayer player = (EntityPlayer) entity;
            if (ignoreTeammates.getValue() && !TeamUtil.isTarget(player)) {
                continue;
            }
            double distance = mc.thePlayer.getDistanceSqToEntity(player);
            if (distance < closestDistance) {
                closest = player;
                closestDistance = distance;
            }
        }
        return closest;
    }

    @EventTarget(Priority.HIGHEST)
    public void onUpdate(UpdateEvent event) {
        if (!isEnabled() || event.getType() != EventType.PRE || mc.thePlayer == null || mc.theWorld == null) {
            return;
        }
        ticks++;
        pruneWindows();
        target = getTarget();
        boolean hasKnockback = mc.thePlayer.getHeldItem() != null
                && net.minecraft.enchantment.EnchantmentHelper.getKnockbackModifier(mc.thePlayer) > 0;
        if (target == null || (requireKnockback.getValue() && !hasKnockback)
                || (!hasKnockback && !moving())) {
            displaceThisTick = false;
            compensateNextTick = false;
            return;
        }

        Float voidYaw = angle.getValue() == 1 ? findDynamicVoidYaw(target) : findStaticVoidYaw(target);
        if (voidYaw != null) {
            displacementYaw = voidYaw;
        } else {
            displaceLeft = direction.getValue() == 0;
            float baseYaw = event.getYaw();
            displacementYaw = baseYaw + (displaceLeft ? -yawOffset.getValue() : yawOffset.getValue());
        }

        displaceThisTick = !displaceThisTick;
        if (displaceThisTick && !allowedInWindow(target)) {
            displaceThisTick = false;
            compensateNextTick = false;
            return;
        }
        if (!displaceThisTick) {
            compensateNextTick = false;
            int key = mc.gameSettings.keyBindAttack.getKeyCode();
            if (key != 0) {
                KeyBinding.onTick(key);
            }
            return;
        }

        event.setRotation(displacementYaw, event.getPitch(), 10);
    }

    @EventTarget(Priority.LOWEST)
    public void onMoveInput(MoveInputEvent event) {
        if (!isEnabled() || mc.thePlayer == null) {
            return;
        }
        if (compensateNextTick && !displaceThisTick) {
            mc.thePlayer.movementInput.moveStrafe = displaceLeft ? -1.0F : 1.0F;
            compensateNextTick = false;
            return;
        }
        if (displaceThisTick && !mc.thePlayer.isPotionActive(net.minecraft.potion.Potion.moveSpeed)) {
            if (moving()) {
                mc.thePlayer.movementInput.moveForward = 1.0F;
                compensateNextTick = true;
            }
        }
    }

    @EventTarget(Priority.HIGH)
    public void onPacket(PacketEvent event) {
        if (!isEnabled() || !blink.getValue() || !displaceThisTick
                || event.getType() != EventType.SEND || !(event.getPacket() instanceof C03PacketPlayer)) {
            return;
        }
        if (Leader.blinkManager.getBlinkingModule() == BlinkModules.NONE) {
            if (Leader.blinkManager.setBlinkState(true, BlinkModules.DISPLACE)) {
                releaseBlinkNextTick = true;
            }
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (event.getType() == EventType.PRE && releaseBlinkNextTick) {
            Leader.blinkManager.setBlinkState(false, BlinkModules.DISPLACE);
            releaseBlinkNextTick = false;
        }
    }

    @EventTarget
    public void onRender(Render3DEvent event) {
        if (!isEnabled() || !showDirection.getValue() || target == null || target.isDead) {
            return;
        }
        double partial = event.getPartialTicks();
        double x = target.lastTickPosX + (target.posX - target.lastTickPosX) * partial;
        double y = target.lastTickPosY + target.height * 0.5D - mc.getRenderManager().viewerPosY;
        double z = target.lastTickPosZ + (target.posZ - target.lastTickPosZ) * partial;
        double radians = Math.toRadians(displacementYaw);
        double forwardX = -Math.sin(radians);
        double forwardZ = Math.cos(radians);
        double viewerX = mc.getRenderManager().viewerPosX;
        double viewerZ = mc.getRenderManager().viewerPosZ;
        x -= viewerX;
        z -= viewerZ;

        GL11.glPushMatrix();
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_LINE_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glLineWidth(2.0F);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 0.9F);
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex3d(x, y, z);
        GL11.glVertex3d(x + forwardX * 1.3D, y, z + forwardZ * 1.3D);
        GL11.glVertex3d(x + forwardX * 1.3D, y, z + forwardZ * 1.3D);
        GL11.glVertex3d(x + forwardX * 0.9D - forwardZ * 0.25D, y, z + forwardZ * 0.9D + forwardX * 0.25D);
        GL11.glVertex3d(x + forwardX * 1.3D, y, z + forwardZ * 1.3D);
        GL11.glVertex3d(x + forwardX * 0.9D + forwardZ * 0.25D, y, z + forwardZ * 0.9D - forwardX * 0.25D);
        GL11.glEnd();
        GL11.glPopAttrib();
        GL11.glPopMatrix();
    }

    private boolean allowedInWindow(EntityPlayer player) {
        int delayTicks = (int) Math.ceil(delay.getValue() / 50.0D);
        Integer start = targetWindows.get(player.getEntityId());
        if (start == null || ticks - start >= WINDOW_TICKS) {
            targetWindows.put(player.getEntityId(), ticks);
            return true;
        }
        return delayTicks == 0 || ticks - start >= delayTicks;
    }

    private void pruneWindows() {
        if (mc.theWorld == null) {
            targetWindows.clear();
            return;
        }
        Iterator<Map.Entry<Integer, Integer>> iterator = targetWindows.entrySet().iterator();
        while (iterator.hasNext()) {
            Entity entity = mc.theWorld.getEntityByID(iterator.next().getKey());
            if (!(entity instanceof EntityPlayer) || entity.isDead) {
                iterator.remove();
            }
        }
    }

    private Float findStaticVoidYaw(EntityPlayer player) {
        if (!findVoid.getValue()) {
            return null;
        }
        for (int ring = 1; ring <= SCAN_RINGS; ring++) {
            double radius = ring * SCAN_STEP;
            for (int i = 0; i < SCAN_DIRECTIONS; i++) {
                double x = player.posX + scanX[i] * radius;
                double z = player.posZ + scanZ[i] * radius;
                if (isVoidColumn(x, player.posY, z)) {
                    updateSide(player, x, z);
                    return yawTo(x - player.posX, z - player.posZ);
                }
            }
        }
        return null;
    }

    private Float findDynamicVoidYaw(EntityPlayer player) {
        double bestScore = 0.0D;
        double bestX = 0.0D;
        double bestZ = 0.0D;
        for (int i = 0; i < SCAN_DIRECTIONS; i++) {
            double score = 0.0D;
            for (double distance = SCAN_STEP; distance <= DYNAMIC_DISTANCE; distance += SCAN_STEP) {
                if (!isVoidColumn(player.posX + scanX[i] * distance, player.posY, player.posZ + scanZ[i] * distance)) {
                    break;
                }
                score += DYNAMIC_DISTANCE - distance + 1.0D;
            }
            if (score > bestScore) {
                bestScore = score;
                bestX = player.posX + scanX[i];
                bestZ = player.posZ + scanZ[i];
            }
        }
        if (bestScore <= 0.0D) {
            return null;
        }
        updateSide(player, bestX, bestZ);
        return yawTo(bestX - player.posX, bestZ - player.posZ);
    }

    private float yawTo(double x, double z) {
        return (float) (Math.toDegrees(Math.atan2(z, x)) - 90.0D);
    }

    private boolean isVoidColumn(double x, double y, double z) {
        int blockX = MathHelper.floor_double(x);
        int blockZ = MathHelper.floor_double(z);
        int startY = MathHelper.floor_double(y) - 1;
        for (int blockY = startY; blockY >= Math.max(0, startY - SCAN_DEPTH); blockY--) {
            if (!mc.theWorld.isAirBlock(new BlockPos(blockX, blockY, blockZ))) {
                return false;
            }
        }
        return true;
    }

    private void updateSide(EntityPlayer player, double x, double z) {
        double targetX = player.posX - mc.thePlayer.posX;
        double targetZ = player.posZ - mc.thePlayer.posZ;
        double voidX = x - mc.thePlayer.posX;
        double voidZ = z - mc.thePlayer.posZ;
        displaceLeft = targetX * voidZ - targetZ * voidX < 0.0D;
    }
}
