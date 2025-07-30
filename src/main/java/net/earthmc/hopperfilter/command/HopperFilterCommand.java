package net.earthmc.hopperfilter.command;

import net.earthmc.hopperfilter.HopperFilter;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class HopperFilterCommand implements CommandExecutor {

    private final HopperFilter plugin;

    public HopperFilterCommand(HopperFilter plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("hopperfilter.reload")) {
                sender.sendMessage(ChatColor.RED + "You don't have permission to do that.");
                return true;
            }

            plugin.reloadConfig(); // Reload config.yml
            plugin.loadSettings();
            sender.sendMessage(ChatColor.GREEN + "HopperFilter config reloaded.");
            return true;
        }

        sender.sendMessage(ChatColor.YELLOW + "Usage: /" + label + " reload");
        return true;
    }
}
