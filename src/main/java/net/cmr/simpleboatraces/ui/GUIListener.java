package net.cmr.simpleboatraces.ui;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;

public class GUIListener implements Listener {

	GUI gui;
	
	public GUIListener(GUI gui) {
		this.gui = gui;
	}
	
	public void unregisterAll() {
		InventoryClickEvent.getHandlerList().unregister(this);
		InventoryOpenEvent.getHandlerList().unregister(this);
		InventoryCloseEvent.getHandlerList().unregister(this);
	}
	
	@EventHandler
	public void onClick(InventoryClickEvent event) {
		if (event.getClickedInventory().equals(gui.getInventory())) {
			event.setCancelled(true);
			int clickedSlot = event.getSlot();
			for (Entry entry : gui.getEntries()) {
				if (entry.getSlot() == clickedSlot) {
					entry.onClick(event);
					break;
				}
			}
			gui.updateInventory();
		}
	}
	
	@EventHandler
	public void onOpen(InventoryOpenEvent event) {
		if (event.getInventory().equals(gui.getInventory())) {
			gui.onOpen(event);
		}
	}
	
	@EventHandler
	public void onClose(InventoryCloseEvent event) {
		if (event.getInventory().equals(gui.getInventory())) {
			gui.onClose(event);
			unregisterAll();
		}
	}
	
}
