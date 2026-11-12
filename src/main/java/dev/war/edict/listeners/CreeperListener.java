package dev.war.edict.listeners;

import dev.war.edict.Edict;
import dev.war.edict.config.RuleStore;
import dev.war.edict.model.Rules;
import org.bukkit.entity.Creeper;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

/** creeper_block_damage: stops explosions from breaking blocks. */
public class CreeperListener implements Listener {

    private static final String ALSO_OTHER = "also-other-explosions";

    private final Edict plugin;

    public CreeperListener(Edict plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        RuleStore store = plugin.store();
        var world = event.getEntity().getWorld();
        if (store.enabled(Rules.CREEPER, world)) return;
        if (event.getEntity() instanceof Creeper || store.bool(Rules.CREEPER, ALSO_OTHER, world)) {
            event.blockList().clear();
        }
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent event) {
        RuleStore store = plugin.store();
        var world = event.getBlock().getWorld();
        if (!store.enabled(Rules.CREEPER, world) && store.bool(Rules.CREEPER, ALSO_OTHER, world)) {
            event.blockList().clear();
        }
    }
}
