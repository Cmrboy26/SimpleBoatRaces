package net.cmr.simpleboatraces.ui;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.meta.ItemMeta;

import net.cmr.simpleboatraces.PlayerData.HonkSound;
import net.cmr.simpleboatraces.SimpleBoatRaces;

public class HornSelectorGUI extends GUI {
	
	public HornSelectorGUI(SimpleBoatRaces plugin, Player player) {
		super(plugin, player, "honkselectorgui", "Horn Selector", 54, generateEntries(plugin, player));
	}

	private static List<Entry> generateEntries(SimpleBoatRaces plugin, Player player) {
		List<Entry> list = new ArrayList<>();

		int columns = 7;
		for (int i = 0; i < HonkSound.values().length; i++) {
			int x = 1 + (i % columns);
			int y = i / columns;
			int slot = y * 9 + x + 9;

			list.add(createHonkEntry(HonkSound.values()[i], slot, plugin, player));
		}
		list.add(new ExitGUIEntry(Material.BARRIER, ChatColor.RED + "Close", 1, 49));
		list.addAll(BoatSelectorGUI.getBorderEntries(BoatSelectorGUI.BORDER_ITEM_MATERIAL, plugin, player));
		
		return list;
	}
	
	private static Entry createHonkEntry(HonkSound honkSound, int slot, SimpleBoatRaces plugin, Player player) {
		int levelToSelect = honkSound.level;
		Material boatItemMaterial = honkSound.material;
		String name = honkSound.name;
		
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
				HonkSound currentSound = plugin.getPlayerData(player).getHonkSound();
				if (currentSound == honkSound) {
					meta.addEnchant(Enchantment.DURABILITY, 1, true);
				} else {
					meta.removeEnchant(Enchantment.DURABILITY);
				}
				meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
				meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
			}
			
			@Override
			public void onClick(InventoryClickEvent event) {
				long playerLevel = plugin.getPlayerData(player).getLevel();
				if (playerLevel < levelToSelect && !player.hasPermission("cmr.boatraces.unlockall")) {
					player.playSound(player, Sound.ENTITY_VILLAGER_NO, 1, 1);
					plugin.info(player, ChatColor.RED + "Not unlocked! Need "+(levelToSelect-playerLevel) + " more level"+(levelToSelect-playerLevel != 1 ? "s" : "") + " to unlock!");
				} else {
					plugin.getPlayerData(player).setHonkSound(honkSound);
					plugin.getPlayerData(player).save();
					player.playSound(player, Sound.BLOCK_DISPENSER_FAIL, 1, 2);
					
					Sound sound = honkSound.sound;
					player.playSound(player.getLocation(), sound, 1, honkSound.pitch);

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
