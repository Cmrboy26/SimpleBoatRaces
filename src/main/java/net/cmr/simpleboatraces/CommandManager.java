package net.cmr.simpleboatraces;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import net.cmr.simpleboatraces.BoatRace.BoatRaceConfiguration;
import net.cmr.simpleboatraces.BoatRace.RaceState;
import net.cmr.simpleboatraces.ui.HornSelectorGUI;
import net.cmr.simpleboatraces.ui.BoatSelectorGUI;
import net.cmr.simpleboatraces.ui.TrailSelectorGUI;
import net.md_5.bungee.api.ChatColor;

public class CommandManager implements CommandExecutor, TabCompleter {

	private final SimpleBoatRaces plugin;
	
	public CommandManager(SimpleBoatRaces plugin) {
		this.plugin = plugin;
	}
	
	public boolean isAdmin(CommandSender sender) {
		return sender.hasPermission("cmr.boatraces.admin") || sender.isOp();
	}
	public boolean canViewList(CommandSender sender) {
		return sender.hasPermission("cmr.boatraces.list") || isAdmin(sender);
	}
	public boolean canJoin(CommandSender sender) {
		return sender.hasPermission("cmr.boatraces.join") || isAdmin(sender);
	}
	public boolean canQuickStart(CommandSender sender) {
		return sender.hasPermission("cmr.boatraces.quickstart") || isAdmin(sender);
	}
	
	// ADD LEVEL LEADERBAORDS
	
	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		boolean isAdmin = isAdmin(sender);
		boolean canViewList = canViewList(sender);
		boolean canJoin = canJoin(sender);
		
		if (!(sender instanceof Player)) {
			plugin.info(sender, ChatColor.RED + "Only players can use this command.");
			return true;
		}
		Player player = (Player) sender;
		if (args.length == 0) {
			plugin.info(sender, ChatColor.RED + "Not enough arguments.");
			return true;
		}
		switch (args[0]) {
		case "join": {
			if (!canJoin) {
				plugin.info(sender, getNoPermissionsString()); 
				return true;
			}
			if (args.length == 1) {
				plugin.info(sender, ChatColor.RED + "Not enough arguments.");
				return true;
			}
			String mapName = args[1];
			BoatRace race = plugin.manager.getRaces().get(mapName);
			if (race == null) {
				plugin.info(sender, ChatColor.RED + "Race \""+mapName+"\" does not exist. Use /boatraces list to view all racetracks.");
				return true;
			}
			
			if (race.players.contains(player)) {
				plugin.info(sender, ChatColor.RED + "You're already in the race!");
				return true;
			}
			
			boolean playerCanJoinQueue = race.canQueueRace(player);

			boolean playerCanJoin = race.canJoinRace(player);
			if (!playerCanJoin) {
				if (playerCanJoinQueue) {
					race.joinQueue(player);
					plugin.info(sender, ChatColor.RED + "You are now in the queue. Please wait for the race to end.");
					return true;
				}
				plugin.info(sender, ChatColor.RED + "Cannot join this match.");
				return true;
			}
			
			boolean racePlayable = race.isRacePlayable();
			if (!racePlayable) {
				plugin.info(sender, ChatColor.RED + "This racetrack cannot be played. Contact an admin and tell them to fix this.");
				return true;
			}
			
			plugin.info(sender, "Joining race \""+mapName+"\"...");
			
			BoatRace currentRace = plugin.manager.getPlayerRace(player);
			if (currentRace != null && !currentRace.equals(race)) {
				// If the player is in another race already, kick them from that race.
				currentRace.leaveRace(player, false);
			}
			race.joinRace(player);
			break;
		}
		case "leave": {
			if (!canJoin) {
				plugin.info(sender, getNoPermissionsString()); 
				return true;
			}
			
			BoatRace currentRace = plugin.manager.getPlayerRace(player);
			if (currentRace != null) {
				currentRace.leaveRace(player, true);
				plugin.info(sender, "Leaving race...");
			} else {
				BoatRace queuedRace = plugin.manager.getQueuedRace(player);
				if (queuedRace != null) {
					queuedRace.leaveQueue(player);
					plugin.info(sender, "Leaving queue...");
				} else {
					plugin.info(sender, ChatColor.RED + "You aren't in a race!");
				}
			}
			break;
		}
		case "stop": {
			if (!isAdmin) {
				plugin.info(sender, getNoPermissionsString()); 
				return true;
			}
			if (args.length == 1) {
				// Try stopping the current race the player is in
				BoatRace currentRace = plugin.manager.getPlayerRace(player);
				if (currentRace != null) {
					plugin.info(sender, "Force stopping the race...");
					currentRace.forceStop();
				} else {
					plugin.info(sender, ChatColor.RED + "You aren't in a race! Please specify a race to force stop.");
				}
				return true;
			}
			
			// Stop the race that the player specified
			String mapName = args[1];
			BoatRace race = plugin.manager.getRaces().get(mapName);
			if (race == null) {
				plugin.info(sender, ChatColor.RED + "Race \""+mapName+"\" does not exist.");
				return true;
			}
			plugin.info(sender, "Force stopping the race...");
			race.forceStop();
			break;
		}
		case "start": {
			if (!canQuickStart(sender)) {
				plugin.info(sender, getNoPermissionsString()); 
				return true;
			}
			if (args.length == 1) {
				// Try stopping the current race the player is in
				BoatRace currentRace = plugin.manager.getPlayerRace(player);
				if (currentRace != null) {
					if (currentRace.currentState == RaceState.WAITING) {
						plugin.info(sender, "Force starting the race...");
						currentRace.quickstart();
					} else {
						plugin.info(sender, ChatColor.RED + "Cannot force start race while "+currentRace.currentState.name().toLowerCase()+"!");
					}
				} else {
					plugin.info(sender, ChatColor.RED + "You aren't in a race!");
				}
				return true;
			}
		}
		case "lb":
		case "leaderboard":
		case "personalbest":
		case "pb": {
			if (!canJoin) {
				plugin.info(sender, getNoPermissionsString()); 
				return true;
			}
			BoatRace race = null;
			if (args.length <= 1) {
				race = plugin.manager.getPlayerRace(player);
				if (race == null) {
					plugin.info(sender, ChatColor.RED + "Not enough arguments. Must be in a race or specify a racetrack.");
					return true;
				}
			} else {
				String mapName = args[1];
				race = plugin.manager.getRaces().get(mapName);
				if (race == null) {
					plugin.info(sender, ChatColor.RED + "Race \""+mapName+"\" does not exist. Use /boatraces list to view all racetracks.");
					return true;
				} 
			}
			
			boolean highscore = args[0].equalsIgnoreCase("leaderboard") || args[0].equalsIgnoreCase("lb");
			if (highscore) {
				Map<String, Object> highscoreMap = plugin.getRacetrackScores(race);
				if (highscoreMap == null) {
					plugin.info(sender, ChatColor.RED + "No scores have been stored for racetrack \""+race.getName()+"\"");
					return true;
				}
				List<Entry<String, Object>> orderedList = new ArrayList<>(highscoreMap.entrySet());
				Comparator<Entry<String, Object>> comparator = new Comparator<Entry<String, Object>>() {
					public int compare(Map.Entry<String,Object> o1, Map.Entry<String,Object> o2) {
						int score1 = Integer.parseInt(o1.getValue().toString());
						int score2 = Integer.parseInt(o2.getValue().toString());
						return score1 - score2;
					};
				};
				orderedList.sort(comparator);
				
				String finalMessage = "Highscores for \""+race.getName()+"\":";
				
				for (int i = 0; i < 10; i++) {
					if (i >= orderedList.size()) {
						break;
					}
					Entry<String, Object> entry = orderedList.get(i);
					UUID uuid = UUID.fromString(entry.getKey());
					Integer ticks = Integer.parseInt(entry.getValue().toString());
					String playerName = Bukkit.getOfflinePlayer(uuid).getName();
					String message = ChatColor.WHITE + " - "
									+ ChatColor.GOLD + "" + (i+1) + race.placeSuffix(i+1) 
									+ ChatColor.WHITE + ": "
									+ ChatColor.YELLOW + playerName
									+ ChatColor.WHITE + " | " 
									+ ChatColor.AQUA + SimpleBoatRaces.getTimeFromTicks(ticks);
					finalMessage += "\n"+message;
				}
				plugin.info(sender, finalMessage);
				return true;
			}
			
			int pb = plugin.getPersonalBestTicks(race, player);
			if (pb == -1) {
				plugin.info(sender, "You haven't played on \""+race.getName()+"\"!");
			} else {
				String pbString = SimpleBoatRaces.getTimeFromTicks(pb);
				plugin.info(sender, "Your personal best on race \""+race.getName()+"\" is "+pbString);
			}
			break;
		}
		case "create": {
			if (!isAdmin) {
				plugin.info(sender, getNoPermissionsString()); 
				return true;
			}
			if (args.length == 1) {
				plugin.info(sender, ChatColor.RED + "Not enough arguments.");
				return true;
			}
			String mapName = args[1];
			BoatRace race = plugin.manager.getRaces().get(mapName);
			if (race != null) {
				if (args.length < 3) {
					plugin.info(sender, ChatColor.RED + "Not enough arguments.");
					return true;
				}
				String key = args[2];
				BoatRaceConfiguration config = race.config;
				Map<String, Object> configMap = config.serialize();
				if (!configMap.containsKey(key)) {
					plugin.info(sender, ChatColor.RED + "Incorrect key. Please select from the command suggestions.");
					return true;
				}
				
				if (key.toLowerCase().contains("pos")) {
					// Vector
					Location playerLocation = player.getLocation();
					Vector pos = playerLocation.toVector();
					
					configMap.put(key, pos);
					race.config = new BoatRaceConfiguration(configMap);
					plugin.manager.updateConfig(race.getName(), race.config);
					
					plugin.info(sender, "Successfully set "+key+" to "+pos.toString());
					return true;
				}
				
				if (key.toLowerCase().contains("world") && args.length <= 3) {
					// String
					String readString = player.getWorld().getName();
					
					configMap.put(key, readString);
					race.config = new BoatRaceConfiguration(configMap);
					plugin.manager.updateConfig(race.getName(), race.config);

					plugin.info(sender, "Successfully set "+key+" to "+readString);
					return true;
				}
				
				if (args.length <= 3) {
					plugin.info(sender, ChatColor.RED + "Not enough arguments.");
					return true;
				} 
				
				String providedValue = args[3];
				
				if (key.toLowerCase().contains("world")) {
					// String
					String readString = providedValue;
					
					configMap.put(key, readString);
					race.config = new BoatRaceConfiguration(configMap);
					plugin.manager.updateConfig(race.getName(), race.config);

					plugin.info(sender, "Successfully set "+key+" to "+readString);
					return true;
				}
				
				if (key.toLowerCase().contains("laps") || key.toLowerCase().contains("players")) {
					// Integer
					try {
						Integer readInteger = Integer.parseInt(providedValue);
						
						configMap.put(key, readInteger);
						race.config = new BoatRaceConfiguration(configMap);
						plugin.manager.updateConfig(race.getName(), race.config);
						
						plugin.info(sender, "Successfully set "+key+" to "+readInteger);
						return true;
					} catch (Exception e) {
						plugin.info(sender, ChatColor.RED + "Provided value was not a number.");
						return true;
					}
				}
				
				return true;
			} else {
				// create new with the name specified
				plugin.manager.createNewRace(mapName);
				plugin.info(sender, "Created new race \""+mapName+"\"!");
			}
			return true;
		}
		case "delete": {
			if (!isAdmin) {
				plugin.info(sender, getNoPermissionsString()); 
				return true;
			}
			if (args.length < 2) {
				plugin.info(sender, ChatColor.RED + "Not enough arguments.");
				return true;
			}
			String mapName = args[1];
			BoatRace race = plugin.manager.getRaces().get(mapName);
			if (race == null) {
				plugin.info(sender, ChatColor.RED + "Race \""+mapName+"\" does not exist.");
				return true;
			}
			
			if (args.length < 3) {
				plugin.info(sender, ChatColor.RED + "WARNING! You are about to DELETE \""+race.getName()+"\"! Add \"confirm\" at the end of the command to confirm.");
				return true;
			}
			String confirmed = args[2];
			if (confirmed.equals("confirm")) {
				plugin.manager.deleteRace(mapName);
				plugin.reloadMaps();
				plugin.info(sender, "Deleted racetrack \""+race.getName()+"\"");
				return true;
			}
			
			break;
		}
		case "list": {
			if (!canViewList) {
				plugin.info(sender, getNoPermissionsString()); 
				return true;
			}
			String names = "Available maps:";
			for (String name : plugin.manager.getRaces().keySet()) {
				names += ChatColor.WHITE + "\n-" + ChatColor.YELLOW + " \"" + name + "\"";
			}
			plugin.info(sender, names);
			break;
		}
		case "stats": {
			if (!canJoin) {
				plugin.info(sender, getNoPermissionsString());
				return true;
			}

			UUID uuid = player.getUniqueId();
			String pname = player.getName();
			if (args.length == 2) {
				// Get the stats of another player
				if (!isAdmin) {
					plugin.info(sender, getNoPermissionsString());
					return true;
				}
				String name = args[1];
				@SuppressWarnings("deprecation")
				OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(name);
				uuid = targetPlayer.getUniqueId();
				pname = targetPlayer.getName();
			}
			
			try {
				PlayerData data = new PlayerData(plugin, uuid);
				Boat.Type boat = data.getBoatType();
				boolean chestBoat = data.preferChestBoat();
				String message = "" + pname + "'s stats:";
				message += "\n -"+ChatColor.YELLOW+" Wins: "+ChatColor.WHITE+data.getWins();
				message += "\n -"+ChatColor.YELLOW+" Level: "+ChatColor.WHITE+data.getLevel();
				message += "\n -"+ChatColor.YELLOW+" XP to Level Up: "+ChatColor.WHITE+data.getXPToLevelUp();
				message += "\n -"+ChatColor.YELLOW+" Boat Type: "+ChatColor.WHITE+Utils.capitalizeString(Utils.getBoatItem(boat, chestBoat).name().replaceAll("_", " ").toLowerCase());
				message += "\n -"+ChatColor.YELLOW+" Honk Sound: "+ChatColor.WHITE+data.getHonkSound().name;
				message += "\n -"+ChatColor.YELLOW+" Trail Effect: "+ChatColor.WHITE+data.getTrailEffect().name;
				plugin.info(sender, message);
				return true;
			} catch (IOException e) {
				plugin.info(player, "An error occured while getting the stats.");
				return true;
			}
		}
		case "config": {
			if (!isAdmin) {
				plugin.info(sender, getNoPermissionsString());
				return true;
			}
			plugin.info(sender, ChatColor.GREEN + "Reloading config...");
			plugin.reloadMaps();
			plugin.info(sender, ChatColor.GREEN + "Config reloaded.");
			break;
		}
		case "boat": {
			if (!canJoin) {
				plugin.info(sender, getNoPermissionsString());
				return true;
			}
			BoatSelectorGUI gui = new BoatSelectorGUI(plugin, player);
			gui.showGUI();
			break;
		}
		case "trail": {
			if (!canJoin) {
				plugin.info(sender, getNoPermissionsString());
				return true;
			}
			TrailSelectorGUI gui = new TrailSelectorGUI(plugin, player);
			gui.showGUI();
			break;
		}
		case "honk":
		case "horn": {
			if (!canJoin) {
				plugin.info(sender, getNoPermissionsString());
				return true;
			}
			HornSelectorGUI gui = new HornSelectorGUI(plugin, player);
			gui.showGUI();
			break;
		}
		default: {
			plugin.info(sender, ChatColor.RED + "Unknown argument.");
			return true;
		}
		}
		return true;
	}
	
	private static String getNoPermissionsString() {
		return ChatColor.RED + "No permission.";
	}

	@Override
	public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
		boolean isAdmin = isAdmin(sender);
		boolean canViewList = canViewList(sender);
		boolean canJoin = canJoin(sender);
		
		// plugin.getLogger().info(args.length + " : "+command.getName());
		if (!command.getName().equalsIgnoreCase("boatraces")) {
			return null;
		}
		
		if (args.length == 1) {
			List<String> list = new ArrayList<>();
			if (canJoin) {
				list.add("join");
				list.add("leave");
				list.add("personalbest");
				list.add("leaderboard");
				list.add("stats");
				list.add("boat");
				list.add("trail");
				list.add("horn");
			}
			if (canViewList) {
				list.add("list");
			}
			if (isAdmin) {
				list.add("stop");
				list.add("start");
				list.add("create");
				list.add("config");
			}
			list.removeIf((String string) -> {
				return string != null && !string.contains(args[0].toLowerCase());
			});
			return list;
		}
		
		
		switch (args[0]) {
		case "join":
			if (canJoin) {
				List<String> end = getMaps();
				end.removeIf((String string) -> {
					return string != null && !string.contains(args[1].toLowerCase());
				});
				return end;
			}
			break;
		case "stop":
			if (isAdmin) {
				List<String> end = getMaps();
				end.removeIf((String string) -> {
					return string != null && !string.contains(args[1].toLowerCase());
				});
				return end;
			}
			break;
		case "leaderboard":
		case "pb":
		case "personalbest":
			if (canJoin) {
				List<String> end = getMaps();
				end.removeIf((String string) -> {
					return string != null && !string.contains(args[1].toLowerCase());
				});
				return end;
			}
			break;
		case "create":
			if (args.length == 2) {
				List<String> end = getMaps();
				end.removeIf((String string) -> {
					return string != null && !string.contains(args[1].toLowerCase());
				});
				return end;
			}
			if (args.length == 3) {
				return new ArrayList<>(new BoatRaceConfiguration().serialize().keySet());
			}
		default: 
			return null;
		}
		return null;
	}
	
	private List<String> getMaps() {
		return new ArrayList<>(plugin.manager.getRaces().keySet());
	}
	
}
