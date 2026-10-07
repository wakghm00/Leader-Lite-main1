package leader.module.modules.movement;

import leader.Leader;
import leader.module.Module;
import leader.module.modules.combat.KillAura;
import leader.property.Property;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Teammates extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private final Map<String, Boolean> players = new LinkedHashMap<>();

    public Teammates() {
        super("Teammates", false);
    }

    @Override
    public void onEnabled() {
        this.refreshPlayers();
        this.disableConflictingModules();
    }

    public List<String> getPlayerNames() {
        this.refreshPlayers();
        return new ArrayList<>(this.players.keySet());
    }

    public boolean isPlayerAttackable(String playerName) {
        if (playerName == null) {
            return false;
        }
        this.refreshPlayers();
        return this.players.getOrDefault(playerName, true);
    }

    public boolean isPlayerBlocked(String playerName) {
        return !this.isPlayerAttackable(playerName);
    }

    public void togglePlayerAttackable(String playerName) {
        // 直接从 map 获取状态，避免调用 isPlayerAttackable 触发 refreshPlayers
        boolean current = this.players.getOrDefault(playerName, true);
        this.setPlayerAttackable(playerName, !current);
    }

    public void setPlayerAttackable(String playerName, boolean attackable) {
        if (playerName == null || mc.thePlayer == null) {
            return;
        }
        if (playerName.equalsIgnoreCase(mc.thePlayer.getName())) {
            return;
        }
        // 直接设置状态，不调用 refreshPlayers 避免覆盖
        this.players.put(playerName, attackable);
    }

    private void refreshPlayers() {
        if (mc.theWorld == null || mc.thePlayer == null) {
            this.players.clear();
            return;
        }
        Map<String, Boolean> refreshed = new LinkedHashMap<>();
        for (EntityPlayer entityPlayer : mc.theWorld.playerEntities) {
            if (entityPlayer == null || entityPlayer == mc.thePlayer) {
                continue;
            }
            String playerName = entityPlayer.getName();
            if (playerName == null || playerName.isEmpty()) {
                continue;
            }
            refreshed.put(playerName, this.players.getOrDefault(playerName, true));
        }
        this.players.clear();
        this.players.putAll(refreshed);
    }

    private void disableConflictingModules() {
        for (Module module : Leader.moduleManager.modules.values()) {
            if (module == this || module.getClass() == KillAura.class || !module.isEnabled()) {
                continue;
            }
            if (this.isTeamsModule(module)) {
                module.setEnabled(false);
            }
        }
    }

    private boolean isTeamsModule(Module module) {
        for (Field field : module.getClass().getDeclaredFields()) {
            field.setAccessible(true);
            try {
                Object value = field.get(module);
                if (value instanceof Property<?>) {
                    Property<?> property = (Property<?>) value;
                    String name = property.getName();
                    if (name.equalsIgnoreCase("Teams") || name.toLowerCase().contains("teams")) {
                        return true;
                    }
                }
            } catch (IllegalAccessException ignored) {
            }
        }
        return false;
    }
}
