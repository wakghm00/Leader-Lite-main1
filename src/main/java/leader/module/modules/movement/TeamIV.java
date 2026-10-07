package leader.module.modules.movement;

import leader.event.EventTarget;
import leader.event.types.EventType;
import leader.events.RenderLivingEvent;
import leader.module.Module;
import leader.property.properties.IntProperty;
import leader.property.properties.PercentProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import org.lwjgl.opengl.GL11;

public class TeamIV extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final IntProperty range = new IntProperty("Range", 6, 1, 32);
    public final PercentProperty opacity = new PercentProperty("Opacity", 50);

    public TeamIV() {
        super("TeamIV", false);
    }

    private boolean shouldFade(EntityLivingBase entity) {
        return mc.thePlayer != null
                && entity instanceof EntityPlayer
                && entity != mc.thePlayer
                && mc.thePlayer.getDistanceToEntity(entity) <= range.getValue().floatValue();
    }

    @EventTarget
    public void onRenderLiving(RenderLivingEvent event) {
        if (!this.isEnabled() || !this.shouldFade(event.getEntity())) {
            return;
        }

        if (event.getType() == EventType.PRE) {
            GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F - opacity.getValue().floatValue() / 100.0F);
        } else if (event.getType() == EventType.POST) {
            GL11.glPopAttrib();
        }
    }
}