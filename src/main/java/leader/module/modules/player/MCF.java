package leader.module.modules.player;

import leader.Leader;
import leader.event.EventTarget;
import leader.events.KeyEvent;
import leader.module.Module;
import leader.util.ChatUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MCF extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public MCF() {
        super("MCF", false, true);
    }

    @EventTarget
    public void onKey(KeyEvent event) {
        if (this.isEnabled() && event.getKey() == -98) {
            if (mc.objectMouseOver != null && mc.objectMouseOver.typeOfHit == MovingObjectType.ENTITY && mc.objectMouseOver.entityHit instanceof EntityPlayer) {
                String hitName = mc.objectMouseOver.entityHit.getName();
                toggleFriend(hitName, true);
            }
        }
    }

    public List<String> getPlayerNames() {
        Map<String, String> names = new LinkedHashMap<>();
        if (mc.theWorld != null) {
            for (EntityPlayer player : mc.theWorld.playerEntities) {
                addPlayerName(names, player == null ? null : player.getName());
            }
        }
        if (mc.getNetHandler() != null) {
            for (NetworkPlayerInfo info : mc.getNetHandler().getPlayerInfoMap()) {
                if (info != null && info.getGameProfile() != null) {
                    addPlayerName(names, info.getGameProfile().getName());
                }
            }
        }
        return new ArrayList<>(names.values());
    }

    public boolean isPlayerFriend(String playerName) {
        return playerName != null && Leader.friendManager.isFriend(playerName);
    }

    public void setPlayerFriend(String playerName, boolean friend) {
        if (!isValidPlayerName(playerName)) {
            return;
        }
        boolean currentlyFriend = Leader.friendManager.isFriend(playerName);
        if (friend && !currentlyFriend) {
            Leader.friendManager.add(playerName);
            ChatUtil.sendFormatted(String.format("%sAdded &o%s&r to your friend list&r", Leader.clientName, playerName));
        } else if (!friend && currentlyFriend) {
            Leader.friendManager.remove(playerName);
            ChatUtil.sendFormatted(String.format("%sRemoved &o%s&r from your friend list&r", Leader.clientName, playerName));
        }
    }

    public void toggleFriend(String playerName, boolean notify) {
        if (!isValidPlayerName(playerName)) {
            return;
        }
        if (!Leader.friendManager.isFriend(playerName)) {
            Leader.friendManager.add(playerName);
            if (notify) ChatUtil.sendFormatted(String.format("%sAdded &o%s&r to your friend list&r", Leader.clientName, playerName));
        } else {
            Leader.friendManager.remove(playerName);
            if (notify) ChatUtil.sendFormatted(String.format("%sRemoved &o%s&r from your friend list&r", Leader.clientName, playerName));
        }
    }

    private void addPlayerName(Map<String, String> names, String playerName) {
        if (!isValidPlayerName(playerName)) {
            return;
        }
        String key = playerName.toLowerCase();
        if (!names.containsKey(key)) {
            names.put(key, playerName);
        }
    }

    private boolean isValidPlayerName(String playerName) {
        return playerName != null
                && !playerName.isEmpty()
                && (mc.thePlayer == null || !playerName.equalsIgnoreCase(mc.thePlayer.getName()));
    }
}
