package dev.war.edict.platform;

import dev.war.edict.Edict;
import dev.war.edict.gui.PromptManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/** Plain Spigot API implementation, used on Spigot and as the fallback anywhere. */
public class BukkitPlatform implements Platform {

    protected final Edict plugin;

    public BukkitPlatform(Edict plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "Spigot";
    }

    @Override
    public boolean isPaper() {
        return false;
    }

    @Override
    public boolean isFolia() {
        return false;
    }

    @Override
    public void teleport(Player player, Location target, Runnable after) {
        player.teleport(target);
        if (after != null) after.run();
    }

    @Override
    public void runForPlayer(Player player, Runnable task) {
        runGlobal(task);
    }

    @Override
    public void runGlobal(Runnable task) {
        if (Bukkit.isPrimaryThread()) task.run();
        else Bukkit.getScheduler().runTask(plugin, task);
    }

    @Override
    public void applyText(ItemMeta meta, String name, List<String> lore) {
        meta.setDisplayName("§r" + name);
        meta.setLore(lore);
    }

    @Override
    public Inventory createInventory(InventoryHolder holder, int size, String legacyTitle) {
        return Bukkit.createInventory(holder, size, legacyTitle);
    }

    @Override
    public void registerChatPrompt(PromptManager prompts) {
        Bukkit.getPluginManager().registerEvents(new Listener() {
            @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
            @SuppressWarnings("deprecation")
            public void onChat(AsyncPlayerChatEvent event) {
                if (prompts.consume(event.getPlayer(), event.getMessage())) {
                    event.setCancelled(true);
                }
            }
        }, plugin);
    }
}
