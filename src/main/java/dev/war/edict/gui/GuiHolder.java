package dev.war.edict.gui;

import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;

/** Marks an inventory as a Edict menu and maps its slots to click actions. */
public final class GuiHolder implements InventoryHolder {

    /** What happens when a slot is clicked. */
    @FunctionalInterface
    public interface SlotAction {
        void click(ClickType type);
    }

    private final Map<Integer, SlotAction> actions = new HashMap<>();
    private Inventory inventory;
    private Runnable renderer;

    void bind(Inventory inventory, Runnable renderer) {
        this.inventory = inventory;
        this.renderer = renderer;
    }

    void action(int slot, SlotAction action) {
        actions.put(slot, action);
    }

    SlotAction actionAt(int slot) {
        return actions.get(slot);
    }

    /** Clears and draws the menu again with current values. */
    void refresh() {
        actions.clear();
        inventory.clear();
        renderer.run();
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
