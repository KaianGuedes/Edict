package dev.war.pluginRules.paper;

import dev.war.pluginRules.PluginRules;
import dev.war.pluginRules.gui.PromptManager;
import dev.war.pluginRules.platform.Platform;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Paper (and Folia) implementation. Loaded by reflection only when Paper is detected, so Spigot never touches
 * these classes. Uses Adventure for item names, lore and titles, teleportAsync, and the region schedulers on Folia.
 */
public final class PaperPlatform implements Platform {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final PluginRules plugin;
    private final boolean folia;

    public PaperPlatform(PluginRules plugin, boolean folia) {
        this.plugin = plugin;
        this.folia = folia;
    }

    @Override
    public String name() {
        return folia ? "Folia" : "Paper";
    }

    @Override
    public boolean isPaper() {
        return true;
    }

    @Override
    public boolean isFolia() {
        return folia;
    }

    @Override
    public void teleport(Player player, Location target, Runnable after) {
        player.teleportAsync(target).whenComplete((ok, err) -> {
            if (after != null) runForPlayer(player, after);
        });
    }

    @Override
    public void runForPlayer(Player player, Runnable task) {
        if (folia) {
            player.getScheduler().run(plugin, t -> task.run(), null);
        } else if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    @Override
    public void runGlobal(Runnable task) {
        if (folia) {
            Bukkit.getGlobalRegionScheduler().run(plugin, t -> task.run());
        } else if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    private static Component component(String legacy) {
        return LEGACY.deserialize(legacy).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    @Override
    public void applyText(ItemMeta meta, String name, List<String> lore) {
        meta.displayName(component(name));
        List<Component> lines = new ArrayList<>(lore.size());
        for (String l : lore) lines.add(component(l));
        meta.lore(lines);
    }

    @Override
    public Inventory createInventory(InventoryHolder holder, int size, String legacyTitle) {
        return Bukkit.createInventory(holder, size, LEGACY.deserialize(legacyTitle));
    }

    @Override
    public void registerChatPrompt(PromptManager prompts) {
        Bukkit.getPluginManager().registerEvents(new Listener() {
            @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
            public void onChat(AsyncChatEvent event) {
                String text = PlainTextComponentSerializer.plainText().serialize(event.message());
                if (prompts.consume(event.getPlayer(), text)) {
                    event.setCancelled(true);
                }
            }
        }, plugin);
    }
}
