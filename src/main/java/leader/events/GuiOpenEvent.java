package leader.events;

import leader.event.events.EventStoppable;
import net.minecraft.client.gui.GuiScreen;

public class GuiOpenEvent extends EventStoppable {
    private GuiScreen gui;

    public GuiOpenEvent(GuiScreen gui) {
        this.gui = gui;
    }

    public GuiScreen getGui() {
        return this.gui;
    }

    public void setGui(GuiScreen gui) {
        this.gui = gui;
    }
}