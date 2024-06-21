package net.cmr.simpleboatraces.ui;

import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import net.cmr.simpleboatraces.SimpleBoatRaces;

public abstract class GUI {

	private final int ID;
	private final int size;
	private final String title;
	private Inventory inventory;
	private List<Entry> entries;
	private Player player;
	public SimpleBoatRaces plugin;
	private GUIListener listener;
	
	public GUI(SimpleBoatRaces plugin, Player player, String ID, String title, int size, List<Entry> entries) {
		this.plugin = plugin;
		this.ID = ID.hashCode();
		this.title = title;
		this.size = size;
		this.entries = entries;
		this.player = player;
	}
	
	private void buildInventory() {
		inventory = Bukkit.createInventory(null, size, title);
		updateInventory();
	}
	
	public void updateInventory() {
		for (Entry entry : entries) {
			entry.onUpdate(player);
			ItemStack stack = new ItemStack(entry.getMaterial(), entry.getQuantity());
			ItemMeta meta = stack.getItemMeta();
			meta.setDisplayName(entry.getName());
			meta.setLore(entry.getLore());
			entry.updateItemMeta(meta);
			stack.setItemMeta(meta);
			inventory.setItem(entry.getSlot(), stack);
		}
	}
	
	public void showGUI() {
		buildInventory();
		player.openInventory(getInventory());
		if (listener != null) {
			listener.unregisterAll();
		}
		this.listener = new GUIListener(this);
		plugin.getServer().getPluginManager().registerEvents(listener, plugin);
	}
	
	public List<Entry> getEntries() {
		return entries;
	}
	public Inventory getInventory() {
		return inventory;
	}
	public Player getPlayer() {
		return player;
	}
	
	abstract void onOpen(InventoryOpenEvent event);
	abstract void onClose(InventoryCloseEvent event);
	
	public boolean equals(Object object) {
		if (object instanceof GUI) {
			GUI gui = (GUI) object;
			if (gui.ID == this.ID) {
				return true;
			}
		}
		return false;
	}
	
}
