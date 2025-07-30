package net.earthmc.hopperfilter;

import net.earthmc.hopperfilter.command.HopperFilterCommand;
import net.earthmc.hopperfilter.listener.HopperRenameListener;
import net.earthmc.hopperfilter.listener.InventoryActionListener;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

public final class HopperFilter extends JavaPlugin {

    private static HopperFilter instance;
    public int itemsPerTransfer = 1;
    @Override
    public void onEnable() {
        instance = this;

        registerListeners(
                new HopperRenameListener(),
                new InventoryActionListener(this)
        );
        saveDefaultConfig(); // Copies config.yml to plugin folder if not present
        reloadConfig();      // Loads or reloads config from disk
        loadSettings();

        //add Reload Command for config
        this.getCommand("hopperfilter").setExecutor(new HopperFilterCommand(this));
        int itemsPerTransfer = getConfig().getInt("transfer.items-per-transfer", 1);
        getLogger().info("Transfer rate set to: " + itemsPerTransfer + " items per 8 ticks.");
    }

    public void loadSettings() {
        itemsPerTransfer = getConfig().getInt("transfer.items-per-transfer", 1);
    }

    public int getItemsPerTransfer() {
        return itemsPerTransfer;
    }


    private void registerListeners(Listener... listeners) {
        for (Listener listener : listeners) {
            getServer().getPluginManager().registerEvents(listener, this);
        }
    }

    public static HopperFilter getInstance() {
        return instance;
    }
}
