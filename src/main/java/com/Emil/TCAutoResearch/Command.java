package com.Emil.TCAutoResearch;

import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;

public class Command extends CommandBase {

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public String getCommandName() {
        return "TCAutoResearch";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/TCAutoResearch <debug|weights>";
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (args.length == 1 && "debug".equalsIgnoreCase(args[0])) {
            minecraft.displayGuiScreen(new GuiResearchDebug(minecraft.currentScreen));
        } else if (args.length == 1
            && ("weights".equalsIgnoreCase(args[0]) || "costs".equalsIgnoreCase(args[0]))) {
                minecraft.displayGuiScreen(new GuiAspectCosts(minecraft.currentScreen));
            } else {
                sender.addChatMessage(new ChatComponentText(getCommandUsage(sender)));
            }
    }
}
