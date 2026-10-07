package leader.command.commands;

import leader.Leader;
import leader.command.Command;
import leader.module.Module;
import leader.util.ChatUtil;

import java.util.ArrayList;
import java.util.Arrays;

public class ListCommand extends Command {
    public ListCommand() {
        super(new ArrayList<>(Arrays.asList("list", "l", "modules", "leader")));
    }

    @Override
    public void runCommand(ArrayList<String> args) {
        if (!Leader.moduleManager.modules.isEmpty()) {
            ChatUtil.sendFormatted(String.format("%sModules:&r", Leader.clientName));
            for (Module module : Leader.moduleManager.modules.values()) {
                ChatUtil.sendFormatted(String.format("%s»&r %s&r", module.isHidden() ? "&8" : "&7", module.formatModule()));
            }
        }
    }
}
