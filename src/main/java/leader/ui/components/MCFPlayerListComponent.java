package leader.ui.components;

import leader.module.modules.player.MCF;
import leader.ui.Component;
import leader.ui.GuiText;
import leader.util.RenderUtil;

import java.awt.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class MCFPlayerListComponent implements Component {
    private static final int ROW_HEIGHT = 14;
    private static final int HEADER_HEIGHT = 16;

    private final ModuleComponent parentModule;
    private final MCF mcf;
    private boolean expanded;
    private int offsetY;

    public MCFPlayerListComponent(ModuleComponent parentModule, int offsetY) {
        this.parentModule = parentModule;
        this.mcf = (MCF) parentModule.mod;
        this.offsetY = offsetY;
        this.expanded = false;
    }

    @Override
    public void draw(AtomicInteger offset) {
        int rowX = parentModule.category.getX() + 8;
        int rowY = parentModule.category.getY() + offsetY;
        int rowWidth = parentModule.category.getWidth() - 16;
        List<String> playerNames = mcf.getPlayerNames();

        RenderUtil.drawRoundedRectWithGl(rowX, rowY + 1, rowX + rowWidth, rowY + HEADER_HEIGHT - 1, 4,
                new Color(255, 255, 255, 14).getRGB());
        String title = "Players " + (expanded ? "v" : ">") + " (" + playerNames.size() + ")";
        GuiText.draw(GuiText.trim(title, rowWidth - 6), rowX + 3,
                rowY + (HEADER_HEIGHT - GuiText.height()) / 2.0F, new Color(215, 218, 225).getRGB());

        if (!expanded) {
            return;
        }

        if (playerNames.isEmpty()) {
            int emptyY = rowY + HEADER_HEIGHT;
            GuiText.draw("No players", rowX + 3, emptyY + 2, new Color(145, 148, 156).getRGB());
            return;
        }

        for (int i = 0; i < playerNames.size(); i++) {
            String playerName = playerNames.get(i);
            boolean friend = mcf.isPlayerFriend(playerName);
            int playerRowY = rowY + HEADER_HEIGHT + i * ROW_HEIGHT;
            int rowColor = i % 2 == 0 ? new Color(255, 255, 255, 8).getRGB() : new Color(255, 255, 255, 4).getRGB();
            RenderUtil.drawRoundedRectWithGl(rowX + 2, playerRowY + 1, rowX + rowWidth - 2,
                    playerRowY + ROW_HEIGHT - 1, 3, rowColor);

            String state = friend ? "true" : "false";
            int stateColor = friend ? new Color(80, 230, 120).getRGB() : new Color(245, 90, 90).getRGB();
            int stateWidth = GuiText.width(state);
            GuiText.draw(GuiText.trim(playerName, rowWidth - stateWidth - 12), rowX + 5,
                    playerRowY + (ROW_HEIGHT - GuiText.height()) / 2.0F, new Color(215, 218, 225).getRGB());
            GuiText.draw(state, rowX + rowWidth - stateWidth - 5,
                    playerRowY + (ROW_HEIGHT - GuiText.height()) / 2.0F, stateColor);
        }
    }

    @Override
    public void update(int mousePosX, int mousePosY) {
    }

    @Override
    public void mouseDown(int x, int y, int button) {
        if (!parentModule.panelExpand || button != 0) {
            return;
        }

        int rowX = parentModule.category.getX() + 8;
        int rowY = parentModule.category.getY() + offsetY;
        int rowWidth = parentModule.category.getWidth() - 16;
        if (x > rowX && x < rowX + rowWidth && y > rowY && y < rowY + HEADER_HEIGHT) {
            expanded = !expanded;
            return;
        }

        if (!expanded) {
            return;
        }

        List<String> playerNames = mcf.getPlayerNames();
        for (int i = 0; i < playerNames.size(); i++) {
            int playerRowY = rowY + HEADER_HEIGHT + i * ROW_HEIGHT;
            if (x > rowX && x < rowX + rowWidth && y > playerRowY && y < playerRowY + ROW_HEIGHT) {
                String playerName = playerNames.get(i);
                mcf.setPlayerFriend(playerName, !mcf.isPlayerFriend(playerName));
                return;
            }
        }
    }

    @Override
    public void mouseReleased(int x, int y, int button) {
    }

    @Override
    public void keyTyped(char chatTyped, int keyCode) {
    }

    @Override
    public void setComponentStartAt(int newOffsetY) {
        this.offsetY = newOffsetY;
    }

    @Override
    public int getHeight() {
        if (!expanded) {
            return HEADER_HEIGHT;
        }
        int playerCount = mcf.getPlayerNames().size();
        return HEADER_HEIGHT + Math.max(1, playerCount) * ROW_HEIGHT;
    }

    @Override
    public boolean isVisible() {
        return parentModule.panelExpand;
    }
}
