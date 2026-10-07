package leader.ui.components;

import leader.module.modules.movement.Teammates;
import leader.ui.Component;
import leader.ui.GuiText;
import net.minecraft.client.Minecraft;

import java.awt.*;
import java.util.concurrent.atomic.AtomicInteger;

public class TeammatesListComponent implements Component {
    private final ModuleComponent parentModule;
    private final Teammates teammates;
    private int offsetY;
    private int x;
    private int y;

    public TeammatesListComponent(ModuleComponent parentModule, int offsetY) {
        this.parentModule = parentModule;
        this.teammates = (Teammates) parentModule.mod;
        this.offsetY = offsetY;
    }

    @Override
    public void draw(AtomicInteger offset) {
        if (!parentModule.panelExpand || teammates == null) {
            return;
        }

        int rowX = parentModule.category.getX() + 8;
        int rowWidth = parentModule.category.getWidth() - 16;
        int rowHeight = 14;
        int index = 0;

        for (String playerName : teammates.getPlayerNames()) {
            int rowY = parentModule.category.getY() + offsetY + index * rowHeight;
            boolean attackable = teammates.isPlayerAttackable(playerName);
            
            // Draw name
            GuiText.draw(playerName, rowX, rowY + 2, new Color(215, 218, 225).getRGB());
            
            // Draw + and -
            int plusX = rowX + rowWidth - 30;
            int minusX = rowX + rowWidth - 15;
            
            int plusColor = attackable ? new Color(0, 255, 0).getRGB() : new Color(100, 100, 100).getRGB();
            int minusColor = !attackable ? new Color(255, 0, 0).getRGB() : new Color(100, 100, 100).getRGB();
            
            GuiText.draw("+", plusX, rowY + 2, plusColor);
            GuiText.draw("-", minusX, rowY + 2, minusColor);
            
            index++;
        }
    }

    @Override
    public void update(int mousePosX, int mousePosY) {
        this.y = this.parentModule.category.getY() + this.offsetY;
        this.x = this.parentModule.category.getX();
    }

    @Override
    public void mouseDown(int x, int y, int button) {
        if (!parentModule.panelExpand || button != 0) {
            return;
        }

        int rowHeight = 14;
        int index = 0;
        int rowWidth = parentModule.category.getWidth() - 16;
        int rowX = parentModule.category.getX() + 8;
        
        for (String playerName : teammates.getPlayerNames()) {
            int rowY = parentModule.category.getY() + offsetY + index * rowHeight;
            if (y > rowY && y < rowY + rowHeight) {
                int plusX = rowX + rowWidth - 30;
                int minusX = rowX + rowWidth - 15;
                
                if (x > plusX - 5 && x < plusX + 10) {
                    teammates.setPlayerAttackable(playerName, true);
                    return;
                } else if (x > minusX - 5 && x < minusX + 10) {
                    teammates.setPlayerAttackable(playerName, false);
                    return;
                }
            }
            index++;
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
        return teammates.getPlayerNames().size() * 14;
    }

    @Override
    public boolean isVisible() {
        return parentModule.panelExpand;
    }
}
