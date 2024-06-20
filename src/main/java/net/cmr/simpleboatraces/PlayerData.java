package net.cmr.simpleboatraces;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.function.Function;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Boat.Type;
import org.bukkit.entity.Player;

public class PlayerData {
	
	File playerFile;
	FileConfiguration config;
	
	// Player Data
	final String SCORE_KEY = "score";
	final String BOAT_SELECTION_KEY = "boat_type";
	final String CHEST_BOAT_SELECTION_KEY = "chest_boat";
	final String WINS_KEY = "wins";
	
	public PlayerData(SimpleBoatRaces plugin, Player player) throws IOException {
		this(plugin, player.getUniqueId());
	}
	
	public PlayerData(SimpleBoatRaces plugin, UUID uuid) throws IOException {
		File playerFolder = new File(plugin.getDataFolder()+File.separator+"players");
		if (!playerFolder.exists()) playerFolder.mkdirs();
		playerFile = new File(plugin.getDataFolder()+File.separator+"players"+File.separator+uuid.toString()+".yml");
		if (!playerFile.exists()) playerFile.createNewFile();
		config = YamlConfiguration.loadConfiguration(playerFile);
	}
	
	public void save() {
		try {
			config.save(playerFile);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public void onChangeConfig() {
		save();
	}

	Function<Double, Double> levelFunction = (Double xp) -> {
		double result = Math.sqrt(xp / 5d);
		return Math.floor(result);
	};
	
	// inverse of level function
	Function<Double, Double> xpFunction = (Double level) -> {
		return level * level * 5;
	};
	
	// Player starts at level zero. 
	public long getLevel() {
		return levelFunction.apply((double) getXP()).longValue();
	}
	
	public long getXPToLevelUp() {
		long nextXP = xpFunction.apply((double) getLevel() + 1).longValue();
		return nextXP - getXP();
	}
	
	public long getXP() {
		return config.getLong(SCORE_KEY, 0);
	}
	public void setXP(long value) {
		config.set(SCORE_KEY, value);
		onChangeConfig();
	}
	/*
	 * Returns true if the player leveled up.
	 */
	public boolean addXP(long xp) {
		long previousLevel = getLevel();
		setXP(xp + getXP());
		long newLevel = getLevel();
		return previousLevel != newLevel;
	}
	
	public Type getBoatType() {
		String storedValue = config.getString(BOAT_SELECTION_KEY, Type.OAK.name());
		Type parsedValue = Type.valueOf(storedValue);
		if (parsedValue == null) parsedValue = Type.OAK;
		return parsedValue;
	}
	public void setBoatType(Type type) {
		config.set(BOAT_SELECTION_KEY, type.name());
		onChangeConfig();
	}
	
	public boolean preferChestBoat() {
		return config.getBoolean(CHEST_BOAT_SELECTION_KEY, false);
	}
	public void setChestBoat(boolean chestBoat) {
		config.set(CHEST_BOAT_SELECTION_KEY, chestBoat);
		onChangeConfig();
	}
	
	public long getWins() {
		return config.getLong(WINS_KEY, 0);
	}
	public void addWin() {
		config.set(WINS_KEY, getWins()+1);
		onChangeConfig();
	}
	
}
