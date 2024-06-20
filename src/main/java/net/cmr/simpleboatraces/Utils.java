package net.cmr.simpleboatraces;

import org.bukkit.Material;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Boat.Type;

public class Utils {

	public static Material getBoatItem(Boat.Type type, boolean chestBoat) {
		String desiredBoatName = type.name()+"_"+(chestBoat ? "CHEST_BOAT" : "BOAT");
		if (type == Type.BAMBOO) {
			desiredBoatName = desiredBoatName.replaceAll("BOAT", "RAFT");
		}
		Material boatItemMaterial = Material.valueOf(desiredBoatName);
		return boatItemMaterial;
	}
	
	public static String capitalizeString(String string) {
		char[] chars = string.toLowerCase().toCharArray();
		boolean found = false;
		for (int i = 0; i < chars.length; i++) {
			if (!found && Character.isLetter(chars[i])) {
				chars[i] = Character.toUpperCase(chars[i]);
				found = true;
			} else if (Character.isWhitespace(chars[i]) || chars[i] == '.' || chars[i] == '\'') {
				found = false;
			}
		}
		return String.valueOf(chars);
	}
	
}
