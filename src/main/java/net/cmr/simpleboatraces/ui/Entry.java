package net.cmr.simpleboatraces.ui;

import java.util.Arrays;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.meta.ItemMeta;

public abstract class Entry {

	private Material material;
	private String name;
	private int quantity;
	private int slot;
	private List<String> lore;
	
	public Entry(Material material, String name, int quantity, int slot, String...lore) {
		this.material = material;
		this.quantity = quantity;
		this.name = name;
		this.slot = slot;
		this.lore = Arrays.asList(lore);
	}
	
	public Material getMaterial() { return material; }
	public String getName() { return name; }
	public int getQuantity() { return quantity; }
	public int getSlot() { return slot; }
	public List<String> getLore() { return lore; }
	
	public void setMaterial(Material material) {
		this.material = material;
	}
	public void setName(String name) {
		this.name = name;
	}
	public void setQuantity(int quantity) {
		this.quantity = quantity;
	}
	public void setLore(String...lore) {
		this.lore = Arrays.asList(lore);
	}
	
	// Overridable
	public void updateItemMeta(ItemMeta meta) {
		
	}
	
	public abstract void onClick(InventoryClickEvent event);
	public abstract void onUpdate(Player player);
	
}
