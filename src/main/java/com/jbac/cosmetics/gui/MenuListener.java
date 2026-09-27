package com.jbac.cosmetics.gui;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

/**
 * While any Menu is open, every click and drag in the whole view is cancelled (top and bottom inventory),
 * which rules out shift-clicks, number keys, offhand swaps and drags moving items into the GUI.
 */
public final class MenuListener implements Listener {

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof Menu menu)) {
            return;
        }
        event.setCancelled(true);
        Inventory clicked = event.getClickedInventory();
        if (clicked != null && clicked.getHolder(false) == menu) {
            menu.handleClick(event.getSlot(), event.getClick());
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof Menu) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof Menu menu) {
            menu.closed();
        }
    }
}
