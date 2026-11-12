package dev.war.edict.listeners;

import dev.war.edict.Edict;
import dev.war.edict.config.RuleStore;
import dev.war.edict.model.Rules;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * safe_void_fall (The End only): falling out of the End never kills, and below the trigger Y the player is sent
 * to the matching overworld at the arrival Y (clamped to that world's build height).
 */
public class VoidFallListener implements Listener {

    private static final String R = Rules.VOID_FALL;
    /** How long the no-fall-damage shield lasts at most. */
    private static final long SHIELD_MS = 120_000L;
    /** Shield is only dropped on landing after this long, so the teleport tick itself does not clear it. */
    private static final long SHIELD_MIN_MS = 1500L;

    private final Edict plugin;
    private final Map<UUID, Long> shield = new HashMap<>();
    private final Set<UUID> teleporting = new HashSet<>();

    public VoidFallListener(Edict plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        RuleStore store = plugin.store();

        if (event.getCause() == EntityDamageEvent.DamageCause.VOID) {
            World w = player.getWorld();
            if (w.getEnvironment() == World.Environment.THE_END && store.enabled(R, w)) {
                event.setCancelled(true);
            }
        } else if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            Long until = shield.get(player.getUniqueId());
            if (until != null) {
                if (System.currentTimeMillis() <= until) {
                    event.setCancelled(true);
                }
                shield.remove(player.getUniqueId());
            }
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        World world = player.getWorld();

        if (!shield.isEmpty()) tickShield(player);
        if (world.getEnvironment() != World.Environment.THE_END) return;
        Location to = event.getTo();
        if (to == null) return;

        RuleStore store = plugin.store();
        if (!store.enabled(R, world)) return;
        if (to.getY() > store.integer(R, "the-end-y", world)) return;
        trigger(player, to);
    }

    /** Sends a player who fell out of the End to the matching overworld. Public so tests can drive it. */
    public void trigger(Player player, Location from) {
        UUID id = player.getUniqueId();
        if (!teleporting.add(id)) return;
        World end = player.getWorld();
        RuleStore store = plugin.store();
        World dest = plugin.resolver().overworldFor(end);
        if (dest == null) {
            teleporting.remove(id);
            return;
        }

        int wanted = store.integer(R, "overworld-y", end);
        int margin = store.integer(R, "max-height-margin", end);
        int top = dest.getMaxHeight() - margin;
        int bottom = dest.getMinHeight() + margin;
        int y = Math.max(bottom, Math.min(wanted, top));
        if (y != wanted) {
            plugin.resolver().warn("clamp:" + end.getName() + ":" + dest.getName() + ":" + wanted,
                    "safe_void_fall: overworld-y " + wanted + " is outside the build height of '" + dest.getName()
                            + "' (" + dest.getMinHeight() + " to " + dest.getMaxHeight() + "); arriving at Y " + y + " instead.");
        }

        boolean fallDamage = store.bool(R, "fall-damage", end);
        boolean keepVelocity = store.bool(R, "keep-velocity", end);
        Vector velocity = keepVelocity ? player.getVelocity() : null;
        Location target = new Location(dest, from.getX(), y, from.getZ(), from.getYaw(), from.getPitch());

        plugin.platform().teleport(player, target, () -> {
            teleporting.remove(id);
            player.setFallDistance(0f);
            player.setVelocity(velocity != null ? velocity : new Vector(0, 0, 0));
            if (!fallDamage) shield.put(id, System.currentTimeMillis() + SHIELD_MS);
        });
    }

    private void tickShield(Player player) {
        Long until = shield.get(player.getUniqueId());
        if (until == null) return;
        long now = System.currentTimeMillis();
        if (now > until) {
            shield.remove(player.getUniqueId());
        } else if (now > until - SHIELD_MS + SHIELD_MIN_MS && player.isOnGround()) {
            shield.remove(player.getUniqueId());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        shield.remove(event.getPlayer().getUniqueId());
        teleporting.remove(event.getPlayer().getUniqueId());
    }

    /** For tests: is the no-fall-damage shield active for this player. */
    public boolean hasShield(UUID id) {
        return shield.containsKey(id);
    }
}
