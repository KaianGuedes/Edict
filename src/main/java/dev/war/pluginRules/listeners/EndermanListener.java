package dev.war.pluginRules.listeners;

import dev.war.pluginRules.PluginRules;
import dev.war.pluginRules.config.RuleStore;
import dev.war.pluginRules.model.Rules;
import org.bukkit.Material;
import org.bukkit.entity.Enderman;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;

/** enderman_block_theft: stops endermen from picking up (and optionally placing) blocks. */
public class EndermanListener implements Listener {

    private final PluginRules plugin;

    public EndermanListener(PluginRules plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onEndermanChangeBlock(EntityChangeBlockEvent event) {
        if (!(event.getEntity() instanceof Enderman)) return;
        RuleStore store = plugin.store();
        var world = event.getBlock().getWorld();
        // taking a block turns it into air, placing turns air into a block
        boolean taking = event.getTo().isAir();
        if (taking) {
            if (!store.enabled(Rules.ENDERMAN, world)) event.setCancelled(true);
        } else if (!store.bool(Rules.ENDERMAN, "allow-place", world)) {
            event.setCancelled(true);
        }
    }
}
