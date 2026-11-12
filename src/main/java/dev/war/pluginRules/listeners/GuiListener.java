package dev.war.pluginRules.listeners;

import dev.war.pluginRules.PluginRules;
import dev.war.pluginRules.gui.GuiHolder;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Feeds clicks to the GUI. Deliberately avoids InventoryView, which changed from a class to an interface in
 * 1.21, so one jar works on every version.
 */
public class GuiListener implements Listener {

    private final PluginRules plugin;

    public GuiListener(PluginRules plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof GuiHolder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!player.hasPermission(PluginRules.ADMIN_PERMISSION)) {
            player.closeInventory();
            return;
        }
        // clicks in the player's own inventory (raw slot beyond the menu) are cancelled and ignored
        plugin.gui().handleClick(player, event.getInventory(), event.getRawSlot(), event.getClick());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof GuiHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.gui().forget(event.getPlayer().getUniqueId());
        plugin.prompts().cancel(event.getPlayer().getUniqueId());
    }
}
