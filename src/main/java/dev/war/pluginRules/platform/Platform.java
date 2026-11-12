package dev.war.pluginRules.platform;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/** Everything that differs between Spigot and Paper (and Folia). */
public interface Platform {

    /** Display name for logs: Spigot, Paper or Folia. */
    String name();

    boolean isPaper();

    boolean isFolia();

    /** Teleports the player and runs {@code after} once they have arrived. */
    void teleport(Player player, Location target, Runnable after);

    /** Runs a task on the thread that owns the player. */
    void runForPlayer(Player player, Runnable task);

    /** Runs a task on the main (or global region) thread. */
    void runGlobal(Runnable task);

    /** Sets name and lore from legacy-colored (section sign) strings. */
    void applyText(ItemMeta meta, String name, List<String> lore);

    Inventory createInventory(InventoryHolder holder, int size, String legacyTitle);

    /** Registers the chat listener that feeds {@link dev.war.pluginRules.gui.PromptManager}. */
    void registerChatPrompt(dev.war.pluginRules.gui.PromptManager prompts);
}
