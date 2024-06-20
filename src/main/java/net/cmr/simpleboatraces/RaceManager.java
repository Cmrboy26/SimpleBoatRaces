package net.cmr.simpleboatraces;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import net.cmr.simpleboatraces.BoatRace.BoatRaceConfiguration;

public class RaceManager {

	private final SimpleBoatRaces plugin;
	private HashMap<String, BoatRace> races;
	
	public static final String RACE_KEY = "races";
	
	public RaceManager(SimpleBoatRaces plugin) {
		this.plugin = plugin;
		this.races = new HashMap<>();
	}
	
	public HashMap<String, BoatRace> getRaces() {
		return races;
	}
	
	public BoatRace getPlayerRace(Player player) {
		for (BoatRace race : races.values()) {
			if (race.players.contains(player)) {
				return race;
			}
		}
		return null;
	}
	
	public void loadRaces() {
		unloadRaces();
		FileConfiguration config = plugin.getConfig();
		Object racesObj = config.get(RACE_KEY);
		if (racesObj == null) {
			HashMap<String, BoatRaceConfiguration> emptyMap = new HashMap<String, BoatRaceConfiguration>();
			emptyMap.put("example", new BoatRaceConfiguration());
			config.createSection(RACE_KEY, emptyMap);
			plugin.saveConfig();
			plugin.getLogger().info("No races found on file. Creating example...");
		}
		ConfigurationSection section = (ConfigurationSection) racesObj;
		
		Map<String, Object> map = (Map<String, Object>) section.getValues(false);
		int number = 1;
		for (String raceKey : map.keySet()) {
			try {
				BoatRaceConfiguration brconf = (BoatRaceConfiguration) map.get(raceKey);
				plugin.getLogger().info("Loading race \""+raceKey+"\"... ("+number+"/"+map.size()+")");
				number++;
				BoatRace race = new BoatRace(raceKey, plugin, brconf);
				races.put(raceKey, race);
			} catch(Exception e) {
				e.printStackTrace();
			}
		}
		plugin.getLogger().info("Loaded races.");
	}
	
	public void unloadRaces() {
		// Kick everyone from the current boat race
		for (BoatRace race : races.values()) {
			race.forceStop();
			race.dispose();
		}
		races.clear();
	}
	
	public void createNewRace(String name) {
		BoatRaceConfiguration brconf = new BoatRaceConfiguration();
		updateConfig(name, brconf);
	}
	
	public void updateConfig(String name, BoatRaceConfiguration brconf) {
		FileConfiguration config = plugin.getConfig();
		ConfigurationSection races = config.getConfigurationSection(RACE_KEY);
		// Shouldn't be null because loadRaces sets this to be not null
		if (races == null) {
			throw new NullPointerException("Races configuration is null.");
		}
		Map<String, Object> map = (Map<String, Object>) races.getValues(false);
		map.put(name, brconf);
		this.races.put(name, new BoatRace(name, plugin, brconf));
		config.createSection(RACE_KEY, map);
		plugin.saveConfig();
	}
	
	public void deleteRace(String name) {
		FileConfiguration config = plugin.getConfig();
		ConfigurationSection races = config.getConfigurationSection(RACE_KEY);
		// Shouldn't be null because loadRaces sets this to be not null
		if (races == null) {
			throw new NullPointerException("Races configuration is null.");
		}
		Map<String, Object> map = (Map<String, Object>) races.getValues(false);
		map.remove(name);
		this.races.get(name).dispose();
		this.races.remove(races.get(name));
		config.createSection(RACE_KEY, map);
		plugin.saveConfig();
	}
	
}
