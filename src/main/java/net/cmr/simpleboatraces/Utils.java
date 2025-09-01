package net.cmr.simpleboatraces;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;

public class Utils {

	public static Material getBoatItem(String boatType, boolean chestBoat) {
		String desiredBoatName = boatType + (chestBoat ? "_CHEST" : "") + (boatType.contains("BAMBOO") ? "_RAFT" : "_BOAT");
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
	
	public static Boat spawnBoat(World world, Location loc, String boatType, boolean chestBoat) {
		String desiredBoatName = boatType + (chestBoat ? "_CHEST" : "") + (boatType.contains("BAMBOO") ? "_RAFT" : "_BOAT");
		EntityType entityType = EntityType.valueOf(desiredBoatName);
		return (Boat) world.spawnEntity(loc, entityType);
	}

}
