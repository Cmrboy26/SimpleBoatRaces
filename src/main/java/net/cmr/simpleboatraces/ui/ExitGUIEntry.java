package net.cmr.simpleboatraces.ui;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

public class ExitGUIEntry extends Entry {

    public ExitGUIEntry(Material material, String name, int quantity, int slot) {
        super(material, name, 1, slot);
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        event.getWhoClicked().closeInventory();
    }

    @Override
    public void onUpdate(Player player) {
        // Do nothing
    }
    
}
