package dev.war.edict.listeners;

import dev.war.edict.Edict;
import dev.war.edict.config.RuleStore;
import dev.war.edict.model.Rules;
import org.bukkit.Material;
import org.bukkit.entity.Enderman;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;

/** enderman_block_theft: stops endermen from picking up (and optionally placing) blocks. */
public class EndermanListener implements Listener {

    private final Edict plugin;

    public EndermanListener(Edict plugin) {
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
