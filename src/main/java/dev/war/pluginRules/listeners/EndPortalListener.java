package dev.war.pluginRules.listeners;

import dev.war.pluginRules.PluginRules;
import dev.war.pluginRules.config.RuleStore;
import dev.war.pluginRules.model.Rules;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** enter_end_portal: cancels end portal travel and tells the player why. */
public class EndPortalListener implements Listener {

    private static final long MESSAGE_COOLDOWN_MS = 3000L;

    private final PluginRules plugin;
    private final Map<UUID, Long> lastMessage = new HashMap<>();

    public EndPortalListener(PluginRules plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerPortal(PlayerPortalEvent event) {
        if (event.getCause() != TeleportCause.END_PORTAL) return;
        RuleStore store = plugin.store();
        Player player = event.getPlayer();
        var world = player.getWorld();
        if (store.enabled(Rules.END_PORTAL, world)) return;
        event.setCancelled(true);

        long now = System.currentTimeMillis();
        Long last = lastMessage.get(player.getUniqueId());
        if (last != null && now - last < MESSAGE_COOLDOWN_MS) return;
        lastMessage.put(player.getUniqueId(), now);
        String custom = store.string(Rules.END_PORTAL, "message", world);
        if (custom == null || custom.isBlank()) {
            plugin.lang().send(player, "rule.enter_end_portal.blocked");
        } else {
            player.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&', custom));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastMessage.remove(event.getPlayer().getUniqueId());
    }
}
