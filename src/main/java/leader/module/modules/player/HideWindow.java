package leader.module.modules.player;

import leader.event.EventTarget;
import leader.event.types.EventType;
import leader.events.GuiOpenEvent;
import leader.events.LoadWorldEvent;
import leader.events.PacketEvent;
import leader.events.Render2DEvent;
import leader.events.TickEvent;
import leader.module.Module;
import leader.property.properties.BooleanProperty;
import leader.property.properties.FloatProperty;
import leader.property.properties.TextProperty;
import leader.util.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.network.play.server.S2EPacketCloseWindow;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.ResourceLocation;

public class HideWindow extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final ResourceLocation ICON = new ResourceLocation("textures/gui/container/generic_54.png");

    public final BooleanProperty onlyWhileCrouching = new BooleanProperty("Only While Crouching", false);
    public final BooleanProperty whitelist = new BooleanProperty("Whitelist", false);
    public final TextProperty whitelistNames = new TextProperty("Whitelist Names", "Upgrades,Traps", () -> this.whitelist.getValue());
    public final FloatProperty iconScale = new FloatProperty("Icon Scale", 1.0F, 0.5F, 3.0F);
    public final FloatProperty iconX = new FloatProperty("Icon X", 0.5F, 0.0F, 1.0F);
    public final FloatProperty iconY = new FloatProperty("Icon Y", 0.05F, 0.0F, 1.0F);

    private GuiContainer hiddenGui;
    private boolean restoring;

    public HideWindow() {
        super("HideWindow", false);
    }

    @Override
    public String[] getSuffix() {
        return this.hiddenGui == null ? new String[0] : new String[]{"Hidden"};
    }

    @Override
    public void onDisabled() {
        if (this.hiddenGui != null && mc.thePlayer != null) {
            mc.thePlayer.closeScreen();
        }
        this.hiddenGui = null;
    }

    @EventTarget
    public void onGuiOpen(GuiOpenEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null || event.getGui() == null) {
            return;
        }
        if (this.restoring) {
            return;
        }
        if (event.getGui() instanceof GuiInventory && this.hiddenGui != null) {
            GuiContainer gui = this.hiddenGui;
            this.hiddenGui = null;
            this.restoring = true;
            mc.displayGuiScreen(gui);
            this.restoring = false;
            event.stop();
            return;
        }
        if (!(event.getGui() instanceof GuiContainer) || event.getGui() instanceof GuiInventory) {
            return;
        }
        GuiContainer gui = (GuiContainer) event.getGui();
        if (onlyWhileCrouching.getValue() && !mc.thePlayer.isSneaking()) {
            return;
        }
        if (whitelist.getValue() && !matchesWhitelist(gui)) {
            return;
        }
        this.hiddenGui = gui;
        mc.thePlayer.openContainer = gui.inventorySlots;
        event.stop();
    }

    @EventTarget
    public void onLoadWorld(LoadWorldEvent event) {
        this.hiddenGui = null;
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (event.getType() == EventType.RECEIVE && event.getPacket() instanceof S2EPacketCloseWindow) {
            this.hiddenGui = null;
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (event.getType() == EventType.PRE && this.hiddenGui != null && mc.currentScreen != null) {
            mc.displayGuiScreen(null);
        }
    }

    @EventTarget
    public void onRender(Render2DEvent event) {
        if (!this.isEnabled() || this.hiddenGui == null || mc.currentScreen != null || mc.gameSettings.showDebugInfo) {
            return;
        }
        ScaledResolution resolution = new ScaledResolution(mc);
        int size = Math.max(8, Math.round(16.0F * iconScale.getValue()));
        float x = iconX.getValue() * resolution.getScaledWidth();
        float y = iconY.getValue() * resolution.getScaledHeight();
        float left = x - size / 2.0F;
        float top = y - size / 2.0F;
        mc.getTextureManager().bindTexture(ICON);
        Gui.drawModalRectWithCustomSizedTexture((int) left, (int) top, 0, 0, size, size, 176, 166);
        RenderUtil.drawRect(left + 3, top + 3, left + size - 3, top + 4, 0xFFFFFFFF);
        RenderUtil.drawRect(left + 3, top + size - 4, left + size - 3, top + size - 3, 0xFFFFFFFF);
        RenderUtil.drawRect(left + 3, top + 3, left + 4, top + size - 3, 0xFFFFFFFF);
        RenderUtil.drawRect(left + size - 4, top + 3, left + size - 3, top + size - 3, 0xFFFFFFFF);
        RenderUtil.drawRect(left + 5, top + 5, left + size - 5, top + size - 4, 0xD9000000);
        RenderUtil.drawRect(left + 5, top + 5, left + size - 5, top + 6, 0xFFFFFFFF);
        RenderUtil.drawRect(left + size / 2.0F - 1, top + 6, left + size / 2.0F + 1, top + size - 6, 0xFFFF5555);
    }

    private boolean matchesWhitelist(GuiContainer gui) {
        if (!(gui.inventorySlots instanceof ContainerChest)) {
            return false;
        }
        IChatComponent displayName = ((ContainerChest) gui.inventorySlots).getLowerChestInventory().getDisplayName();
        String title = displayName == null ? "" : displayName.getUnformattedText().toLowerCase();
        for (String name : whitelistNames.getValue().split(",")) {
            if (!name.trim().isEmpty() && title.contains(name.trim().toLowerCase())) {
                return true;
            }
        }
        return false;
    }
}