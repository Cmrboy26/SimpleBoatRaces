package net.cmr.simpleboatraces;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.entity.Boat.Type;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import net.cmr.simpleboatraces.BoatRace.BoatRaceConfiguration;
import net.cmr.simpleboatraces.BoatRace.RaceState;
import net.cmr.simpleboatraces.ui.SelectorGUI;
import net.md_5.bungee.api.ChatColor;

public final class SimpleBoatRaces extends JavaPlugin implements Listener {

	static {
        ConfigurationSerialization.registerClass(BoatRaceConfiguration.class);
    }
	
	RaceManager manager;
	File personalBestFile;
	public FileConfiguration pbconfig;
	public ConcurrentHashMap<Player, PlayerData> playerDataMap;
	
	@Override
	public void onEnable() {
		super.onEnable();
		playerDataMap = new ConcurrentHashMap<>();
		for (Player player : getServer().getOnlinePlayers()) {
			addPlayerData(player);
		}
		
		CommandManager cmd = new CommandManager(this);
		this.getCommand("boatraces").setExecutor(cmd);
		this.getServer().getPluginManager().registerEvents(this, this);
		this.getCommand("boatraces").setTabCompleter(cmd);
		
		if (!getDataFolder().exists()) {
			getDataFolder().mkdir();
		}
		personalBestFile = new File(getDataFolder(), "pb.yml");
		if (!personalBestFile.exists()) {
            try {
            	personalBestFile.createNewFile();
                pbconfig = YamlConfiguration.loadConfiguration(personalBestFile);
                try {
                	pbconfig.save(personalBestFile);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } catch (Exception e) {
                Bukkit.getLogger().info("Could not generate the personal best file.");
            }
		} else {
            pbconfig = YamlConfiguration.loadConfiguration(personalBestFile);
		}
		
		manager = new RaceManager(this);
		manager.loadRaces();
	}
	
	@Override
	public void onDisable() {
		super.onDisable();
		
		for (Player player : getServer().getOnlinePlayers()) {
			removePlayerData(player);
		}
		
		manager.unloadRaces();
		manager = null;
		
		getLogger().info("Disabled SimpleBoatRaces.");
	}
	
	public void addPlayerData(Player player) {
		PlayerData data;
		if (playerDataMap.containsKey(player)) return;
		try {
			data = new PlayerData(this, player);
			playerDataMap.put(player, data);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public void removePlayerData(Player player) {
		PlayerData data = playerDataMap.get(player);
		data.save();
		playerDataMap.remove(player);
	}
	
	public PlayerData getPlayerData(Player player) {
		if (!playerDataMap.containsKey(player)) {
			addPlayerData(player);
		}
		return playerDataMap.get(player);
	}
	
	public void info(CommandSender sender, String message) {
		sender.sendMessage(ChatColor.YELLOW + "[BoatRaces] " + ChatColor.WHITE + message);
	}
	
	public void attemptToSetPersonalBestTicks(BoatRace race, Player player, int ticks) {
		UUID uuid = player.getUniqueId();
		String raceName = race.getName();
		
		// caout-3523-52353: # uuid
		//   usermade-racetrack: 23523 # track name: fastest lap in ticks
		
		ConfigurationSection section = (ConfigurationSection) pbconfig.get(raceName);
		Map<String, Object> map = null;
		if (section == null) {
			map = new HashMap<>();
			map.put(uuid.toString(), ticks);
			pbconfig.createSection(raceName, map);
			savePBFile();
			return;
		} else {
			map = (Map<String, Object>) section.getValues(false);
		}
		
		Object valueAt = map.get(uuid.toString());
		if (valueAt != null) {
			Integer readTicks = (Integer) valueAt;
			if (readTicks <= ticks) {
				// If the value in file is lower than what we have, don't do anything
				return;
			}
		}
		map.put(uuid.toString(), ticks);
		pbconfig.createSection(raceName, map);
		savePBFile();
	}
	
	public int getPersonalBestTicks(BoatRace race, Player player) {
		UUID uuid = player.getUniqueId();
		String raceName = race.getName();
		
		ConfigurationSection section = (ConfigurationSection) pbconfig.getConfigurationSection(raceName);
		if (section == null) return -1;
		Map<String, Object> map = (Map<String, Object>) section.getValues(false);
		
		//@SuppressWarnings("unchecked")
		//Map<String, Object> map = pbconfig.getObject(uuid.toString(), Map.class);
		if (map == null) return -1;
		return (Integer) map.getOrDefault(uuid.toString(), -1);
	}
	
	public void reloadMaps() {
		reloadConfig();
		manager.loadRaces();
		//plugin.onDisable();
		//plugin.reloadConfig();
		//plugin.onEnable();
	}
	
	public Map<String, Object> getRacetrackScores(BoatRace race) {
		String raceName = race.getName();
		ConfigurationSection section = pbconfig.getConfigurationSection(raceName);
		if (section == null) return null;
		return section.getValues(false);
	}
	
	public static String getTimeFromTicks(int ticks) {
		int millis = (int) ((ticks % 20) / 20f * 1000);
		int seconds = (int) Math.floor(ticks / 20);
		int minutes = (int) Math.floor(seconds / 60);
		seconds = seconds % 60;

		String millisString = millis+"";
		while (millisString.length() != 3) {
			millisString = "0"+millisString;
		}
		String secondsString = seconds+"";
		while (secondsString.length() != 2) {
			secondsString = "0"+secondsString;
		}
		
		return minutes+":"+secondsString+"."+millisString;
	}
	
	public void savePBFile() {
		try {
            pbconfig.save(personalBestFile);
        } catch (IOException ex) {
            getLogger().log(Level.SEVERE, "Could not save pb file to " + personalBestFile, ex);
        }
	}
	
	@EventHandler
	public void onPlayerJoin(PlayerJoinEvent event) {
		addPlayerData(event.getPlayer());
	}
	
	@EventHandler
	public void onPlayerLeave(PlayerQuitEvent event) {
		BoatRace race = manager.getPlayerRace(event.getPlayer());
		if (race != null) {
			race.leaveRace(event.getPlayer(), true);
		}
		removePlayerData(event.getPlayer());
	}
	
	@EventHandler
	public void onVehicleExit(VehicleExitEvent event) {
		Entity exitedEntity = event.getExited();
		if (!(exitedEntity instanceof Player)) {
			return;
		}
		Player player = (Player) exitedEntity;
		BoatRace race = manager.getPlayerRace(player);
		if (race != null) {
			event.setCancelled(true);
		}
	}
	
	@EventHandler
	public void onVehicleDestroy(VehicleDestroyEvent event) {
		event.setCancelled(true);
		if (event.getAttacker() instanceof Player) {
			Player attacker = (Player) event.getAttacker();
			if (attacker.getGameMode() == GameMode.CREATIVE) {
				event.setCancelled(false);
			}
		}
	}
	
	public boolean isInRace(Player player) {
		return manager.getPlayerRace(player) != null;
	}
	
	@EventHandler
	public void onEntityDamage(EntityDamageEvent ede) {
		if (ede.getCause() == DamageCause.SUFFOCATION) {
			ede.setCancelled(true);
		}
	}
	
	@EventHandler
	public void onPlayerInventoryEvent(InventoryClickEvent event) {
		if (!(event.getWhoClicked() instanceof Player)) return;
		Player player = (Player) event.getWhoClicked();
	    BoatRace race = manager.getPlayerRace(player);
	    if (race != null) {
	    	event.setCancelled(true);
	    }
	}
	
	@EventHandler
	public void onPlayerFillBucket(PlayerBucketFillEvent event) {
		event.setCancelled(isInRace(event.getPlayer()));
	}
	
	@EventHandler
	public void onPlayerPlace(BlockPlaceEvent e) {
		BoatRace race = manager.getPlayerRace(e.getPlayer());
	    if (race != null) {
	    	getLogger().info(e.getItemInHand().toString());
	    	if (e.getItemInHand().hasItemMeta()) {
	    		String name = e.getItemInHand().getItemMeta().getDisplayName();
	    		if (name.equals(BoatRace.HORN_NAME)) {
	    			e.setCancelled(true);
	    			return;
	    		} else if (name.contains(BoatRace.LEAVE_GAME_NAME)) {
	    			e.setCancelled(true);
	    			return;
	    		} else if (name.contains(BoatRace.BOAT_SELECTOR_NAME)) {
	    			e.setCancelled(true);
	    			return;
	    		} else if (name.contains(BoatRace.FAST_START_GAME_NAME)) {
	    			e.setCancelled(true);
	    			return;
	    		}
	    	}
	    	if (race.currentState == RaceState.RACING) {
	    		if (e.getBlockReplacedState().getType() == Material.AIR) {
		    		race.removeBlocksOnReset.add(e.getBlock().getLocation().toVector());
	    		} else {
	    			info(e.getPlayer(), ChatColor.RED + "Can't place here!");
	    			e.setCancelled(true);
	    		}
	    	} else {
	    		e.setCancelled(true);
    			return;
	    	}
	    }
	}
	
	@EventHandler
	public void onPlayerBreak(BlockBreakEvent e) {
		BoatRace race = manager.getPlayerRace(e.getPlayer());
	    if (race != null) {
	    	e.setCancelled(true);
	    }
	}
	
	@EventHandler
	public void onPlayerDrop(PlayerDropItemEvent e) {
		BoatRace race = manager.getPlayerRace(e.getPlayer());
	    if (race != null) {
	    	e.setCancelled(true);
	    }
	}
	
	@EventHandler()
	public void onPlayerUse(PlayerInteractEvent event){
	    Player p = event.getPlayer();
	    if (event.getHand() == EquipmentSlot.OFF_HAND) {
	    	return;
	    }
	    
	    if (event.getAction() == Action.PHYSICAL) {
	    	return;
	    }
	    
	    BoatRace race = manager.getPlayerRace(p);
	    if (race != null) {
	    	ItemStack held = p.getInventory().getItemInMainHand();
	    	if (held.hasItemMeta()) {
	    		String name = held.getItemMeta().getDisplayName();
	    		if (name.contains(BoatRace.BOAT_SELECTOR_NAME)) {
	    			// Holding the selector while in game. Change their desired boat.
	    			SelectorGUI gui = new SelectorGUI(this, p);
	    			gui.showGUI();
	    		} else if (name.contains(BoatRace.FAST_START_GAME_NAME)) {
	    			race.quickstart();
	    		} else if (name.contains(BoatRace.LEAVE_GAME_NAME)) {
					info(p, "Leaving race...");
	    			race.leaveRace(p, true);
	    		} else if (name.contains(BoatRace.HORN_NAME)) {
	    			honk(p);
	    		}
	    	}
	    }
	}
	
	public void honk(Player p) {
		BukkitRunnable honkTask = new BukkitRunnable() {
			@Override
			public void run() {
				p.getWorld().playSound(p, Sound.ENTITY_COW_HURT, 2, 2);
			}
		};
		honkTask.runTaskLater(this, 0);
		/*honkTask = new BukkitRunnable() {
			@Override
			public void run() {
				p.getWorld().playSound(p, Sound.ENTITY_COW_HURT, 1, 2);
			}
		};
		honkTask.runTaskLater(this, 4);*/
	}
	
}
