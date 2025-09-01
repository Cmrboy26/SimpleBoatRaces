package net.cmr.simpleboatraces.ui;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.meta.ItemMeta;

import net.cmr.simpleboatraces.SimpleBoatRaces;
import net.cmr.simpleboatraces.Utils;

public class BoatSelectorGUI extends GUI {
	
	public static final int LEVEL_UNTIL_UNLOCK_INCREMENT = 3;
	public static final Material LOCKED_ITEM_MATERIAL = Material.RED_STAINED_GLASS_PANE;
	public static final Material BORDER_ITEM_MATERIAL = Material.BLACK_STAINED_GLASS_PANE;

	public BoatSelectorGUI(SimpleBoatRaces plugin, Player player) {
		super(plugin, player, "selectorgui", "Boat Selector", 54, generateEntries(plugin, player));
	}
	
	public static List<Entry> getBorderEntries(Material border, SimpleBoatRaces plugin, Player player) {
		int[] slots = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 17, 18, 26, 27, 35, 36, 44, 45, 46, 47, 48, 50, 51, 52, 53};
		List<Entry> list = new ArrayList<>();
		for (int slot : slots) {
			list.add(new Entry(border, " ", 1, slot) {
				@Override public void onUpdate(Player player) {}
				@Override public void onClick(InventoryClickEvent event) {}
			});
		}
		return list;
	}

	private static List<Entry> generateEntries(SimpleBoatRaces plugin, Player player) {
		List<Entry> list = new ArrayList<>();
		
		int columns = 7;

		ArrayList<Material> boatTypes = new ArrayList<>();
		for (Material material : Material.values()) {
			if (material.name().toUpperCase().contains("_BOAT") || material.name().toUpperCase().contains("_RAFT")) {
				boatTypes.add(material);
			}
		}

		// TODO: sort boats to make leveling better
		
		for (int i = 0; i < boatTypes.size(); i++) {
			int x = 1 + (i % columns);
			int y = i / columns;
			int slot = y * 9 + x + 9;
			/*boolean isChestBoat = i / boatTypes.size() == 1;
			Type type = boatTypes.get(i % boatTypes.size());*/

			String type = boatTypes.get(i % boatTypes.size()).toString();
			boolean isChestBoat = type.contains("_CHEST");
			type = type.replaceAll("_CHEST", "").replaceAll("_BOAT", "").replaceAll("_RAFT", "");
			int levelsToUnlock = i * LEVEL_UNTIL_UNLOCK_INCREMENT;
			list.add(createBoatEntry(type, isChestBoat, levelsToUnlock, slot, plugin, player));
		}
		list.add(new ExitGUIEntry(Material.BARRIER, ChatColor.RED + "Close", 1, 49));
		list.addAll(getBorderEntries(BoatSelectorGUI.BORDER_ITEM_MATERIAL, plugin, player));

		return list;
	}
	
	private static Entry createBoatEntry(String boatType, boolean isChestBoat, int levelToSelect, int slot, SimpleBoatRaces plugin, Player player) {
		Material boatItemMaterial = Utils.getBoatItem(boatType, isChestBoat);
		String name = Utils.capitalizeString(boatItemMaterial.name().toLowerCase().replaceAll("_", " "));
		
		Entry entry = new Entry(boatItemMaterial, ChatColor.WHITE+name, 1, slot, "what") {
			@Override
			public void onUpdate(Player player) {
				long playerLevel = plugin.getPlayerData(player).getLevel();
				ChatColor color = ChatColor.GREEN;
				if (playerLevel < levelToSelect && !player.hasPermission("cmr.boatraces.unlockall")) {
					color = ChatColor.RED;
					setMaterial(BoatSelectorGUI.LOCKED_ITEM_MATERIAL);
					setName(ChatColor.RED + name + " (Locked)");
				}
				
				String[] lore = {ChatColor.BOLD + "" + color + "Level required: " + ChatColor.RESET + color + levelToSelect};
				setLore(lore);
			}
			
			@Override
			public void updateItemMeta(ItemMeta meta) {
				String currentBoatType = plugin.getPlayerData(player).getBoatType();
				boolean chestBoat = plugin.getPlayerData(player).preferChestBoat();
				if (currentBoatType.equals(boatType) && chestBoat == isChestBoat) {
					meta.addEnchant(Enchantment.UNBREAKING, 1, true);
				} else {
					meta.removeEnchant(Enchantment.UNBREAKING);
				}
				meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
			}
			
			@Override
			public void onClick(InventoryClickEvent event) {
				long playerLevel = plugin.getPlayerData(player).getLevel();
				if (playerLevel < levelToSelect && !player.hasPermission("cmr.boatraces.unlockall")) {
					player.playSound(player, Sound.ENTITY_VILLAGER_NO, 1, 1);
					plugin.info(player, ChatColor.RED + "Not unlocked! Need "+(levelToSelect-playerLevel) + " more level"+(levelToSelect-playerLevel != 1 ? "s" : "") + " to unlock!");
				} else {
					plugin.getPlayerData(player).setBoatType(boatType);
					plugin.getPlayerData(player).setChestBoat(isChestBoat);
					plugin.getPlayerData(player).save();
					player.playSound(player, Sound.BLOCK_DISPENSER_FAIL, 1, 2);
					plugin.info(player, "Selected \""+ChatColor.YELLOW+name+ChatColor.WHITE+"\"!");
				}
			}
		};
		
		return entry;
	}
	
	@Override
	void onClose(InventoryCloseEvent event) {
		
	}
	@Override
	void onOpen(InventoryOpenEvent event) {
		
	}
	
}
