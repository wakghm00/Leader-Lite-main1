package leader.module.modules.player;

import leader.event.EventTarget;
import leader.event.types.EventType;
import leader.events.TickEvent;
import leader.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.Scoreboard;

public class HealthBypass extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public HealthBypass() {
        super("HealthBypass", false);
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE || mc.thePlayer == null || mc.theWorld == null) {
            return;
        }

        Scoreboard scoreboard = mc.theWorld.getScoreboard();
        if (scoreboard == null) {
            return;
        }

        ScoreObjective objective = scoreboard.getObjectiveInDisplaySlot(2);
        if (objective == null) {
            return;
        }

        for (EntityPlayer player : mc.theWorld.playerEntities) {
            if (player == mc.thePlayer) {
                continue;
            }

            float health = scoreboard.getValueFromObjective(player.getName(), objective).getScorePoints();
            if ((health > 0.0F || player.isDead) && !Float.isNaN(health) && !Float.isInfinite(health)) {
                float maxHealth = player.getMaxHealth();
                if (maxHealth > 0.0F) {
                    health = Math.min(health, maxHealth);
                }
                player.setHealth(health);
            }
        }
    }
}
