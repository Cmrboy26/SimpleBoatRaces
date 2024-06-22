package net.cmr.simpleboatraces;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vehicle;
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
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import net.cmr.lobbylib.LobbyJoinableMinigame;
import net.cmr.simpleboatraces.BoatRace.BoatRaceConfiguration;
import net.cmr.simpleboatraces.BoatRace.RaceState;
import net.cmr.simpleboatraces.PlayerData.HonkSound;
import net.cmr.simpleboatraces.PlayerData.TrailEffect;
import net.cmr.simpleboatraces.ui.BoatSelectorGUI;
import net.cmr.simpleboatraces.ui.HornSelectorGUI;
import net.cmr.simpleboatraces.ui.TrailSelectorGUI;

public final class SimpleBoatRaces extends JavaPlugin implements Listener, LobbyJoinableMinigame {

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
		BoatRace race = manager.getParticipatingRace(event.getPlayer());
		if (race != null) {
			race.leaveRace(event.getPlayer(), true);
		}
		BoatRace queuedRace = manager.getQueuedRace(event.getPlayer());
		if (queuedRace != null) {
			queuedRace.leaveQueue(event.getPlayer());
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
		if (isPlayerInMinigame(player)) {
			event.setCancelled(true);
		}
	}

	@EventHandler
	public void onVehicleEnter(VehicleEnterEvent event) {
		Entity exitedEntity = event.getEntered();
		if (!(exitedEntity instanceof Player)) {
			return;
		}
		Player player = (Player) exitedEntity;
		boolean playerInRace = isPlayerInMinigame(player);
		if (playerInRace) {
			Vehicle vehicle = event.getVehicle();
			// Don't let people get into a boat with other people
			if (vehicle.getPassengers().size() >= 1) {
				event.setCancelled(true);
				return;
			}
		}
	}
	
	@EventHandler
	public void onVehicleDestroy(VehicleDestroyEvent event) {
		if (event.getAttacker() instanceof Player) {
			Player attacker = (Player) event.getAttacker();
			if (isPlayerInMinigame(attacker) && attacker.getGameMode() != GameMode.CREATIVE) {
				event.setCancelled(true);
			}
		}
	}
	
	public boolean isInRace(Player player) {
		return manager.getParticipatingRace(player) != null;
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
		boolean isInMinigame = isPlayerInMinigame(player);
		if (isInMinigame) {
			event.setCancelled(true);
		}
	}
	
	@EventHandler
	public void onPlayerFillBucket(PlayerBucketFillEvent event) {
		if (isPlayerInMinigame(event.getPlayer())) {
			event.setCancelled(true);
		}
	}
	
	@EventHandler
	public void onPlayerPlace(BlockPlaceEvent e) {
		BoatRace race = manager.getJoinedRace(e.getPlayer());
		boolean playerIsInRace = isPlayerInMinigame(e.getPlayer());
	    if (playerIsInRace) {
	    	if (e.getItemInHand().hasItemMeta()) {
	    		String name = e.getItemInHand().getItemMeta().getDisplayName();
	    		if (name.equals(BoatRace.HORN_NAME)) {
	    			e.setCancelled(true);
	    			return;
	    		} else if (name.contains(BoatRace.HORN_SELECTOR_NAME)) {
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
	    		} else if (name.contains(BoatRace.PARTICLE_SELECTOR_NAME)) {
	    			e.setCancelled(true);
	    			return;
	    		}
	    	}
			if (race == null) return;
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
	public void onBoatMove(VehicleMoveEvent move) {
		for (Entity passenger : move.getVehicle().getPassengers()) {
			if (passenger instanceof Player) {
				Player player = (Player) passenger;
				BoatRace race = manager.getJoinedRace(player);
				if (race != null) {
					TrailEffect effect = getPlayerData(player).getTrailEffect();
					if (effect == null || effect.effect == null) {
						return;
					}
					Location playAt = move.getTo().clone().add(0, 0.5, 0);
					double spread = 0;
					if (effect.quantity > 1) {
						spread = 0.2d * effect.quantity;
					}

					double data = 0;
					if (effect == TrailEffect.SMOKE) {
						data = .03d;
						spread = 0;
					}
					if (effect == TrailEffect.CHERRY_LEAVES) {
						data = 1.3d;
					}

					playAt.getWorld().spawnParticle(effect.effect, playAt, effect.quantity, spread, spread, spread, data);
				}
			}
		}
	}

	@EventHandler
	public void onPlayerBreak(BlockBreakEvent e) {
		if (isPlayerInMinigame(e.getPlayer())) {
			e.setCancelled(true);
		}
	}
	
	@EventHandler
	public void onPlayerDrop(PlayerDropItemEvent e) {
		if (isPlayerInMinigame(e.getPlayer())) {
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
	    
	    BoatRace race = manager.getJoinedRace(p);
		boolean playerInRace = isPlayerInMinigame(p);
	    if (playerInRace) {
			if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
				Block clickedBlock = event.getClickedBlock();
				Material type = clickedBlock.getType();
				if (type.isInteractable()) {
					event.setCancelled(true);
				}
			}

	    	ItemStack held = p.getInventory().getItemInMainHand();
	    	if (held.hasItemMeta() && race != null) {
	    		String name = held.getItemMeta().getDisplayName();
	    		if (name.contains(BoatRace.PARTICLE_SELECTOR_NAME)) {
					TrailSelectorGUI gui = new TrailSelectorGUI(this, p);
					gui.showGUI();
	    		} else if (name.contains(BoatRace.BOAT_SELECTOR_NAME)) {
	    			BoatSelectorGUI gui = new BoatSelectorGUI(this, p);
	    			gui.showGUI();
	    		} else if (name.contains(BoatRace.HORN_SELECTOR_NAME)) {
	    			HornSelectorGUI gui = new HornSelectorGUI(this, p);
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
	
	Random rareHonkRandom = new Random();
	
	HashSet<Player> honkCooldown = new HashSet<>();

	public void honk(Player p) {
		HonkSound honkSound = getPlayerData(p).getHonkSound();
		Sound sound = honkSound.sound;

		if (honkCooldown.contains(p)) {
			return;
		}

		if (honkSound.tickCooldown > 0) {
			honkCooldown.add(p);
			BukkitRunnable honkTask = new BukkitRunnable() {
				@Override
				public void run() {
					honkCooldown.remove(p);
				}
			};
			honkTask.runTaskLater(this, honkSound.tickCooldown);
		}

		float pitch = honkSound.pitch;

		if (rareHonkRandom.nextInt(200) == 0) {
			p.getWorld().playSound(p, sound, 2, .5f);
			return;
		}
		p.getWorld().playSound(p, sound, 2, pitch);
	}

	@Override
	public String getMinigameName() {
		return "Boat Racing";
	}

	@Override
	public ChatColor[] getMinigameTitleColor() {
		return new ChatColor[] {ChatColor.AQUA, ChatColor.BOLD};
	}

	@Override
	public boolean isPlayerInMinigame(Player arg0) {
		return manager != null && manager.getJoinedRace(arg0) != null;
	}

	@Override
	public void kickPlayerFromMinigame(Player arg0) {
		BoatRace race = manager.getParticipatingRace(arg0);
		if (race != null) {
			race.leaveRace(arg0, true);
			return;
		}
		race = manager.getQueuedRace(arg0);
		if (race != null) {
			race.leaveQueue(arg0);
		}
	}

	@Override
	public String getMinigameDescription() {
		return "Compete with others to get the first place on ice boat tracks!";
	}

	@Override
	public Material getMinigameIcon() {
		return Material.BAMBOO_RAFT;
	}

	@Override
	public void joinMinigame(Player arg0) {
		// Find a random match to join (prefer ones with the most people)
		BoatRace highestPlayerRace = null;
		int highestPlayerCount = -1;
		BoatRace largestQueueRace = null;
		int largestQueuePlayerCount = -1;
		for (BoatRace race : manager.getRaces().values()) {
			if (!race.isRacePlayable()) {
				continue;
			}

			int players = race.players.size();
			boolean joinable = race.canJoinRace(arg0);
			if (joinable && players > highestPlayerCount) {
				highestPlayerCount = players;
				highestPlayerRace = race;
			}
			if (!joinable) {
				if (race.canQueueRace(arg0) && players > largestQueuePlayerCount) {
					largestQueuePlayerCount = players;
					largestQueueRace = race;
				}
			}
		}
		if (largestQueuePlayerCount == 0 && highestPlayerCount == 0) {
			// There is no one playing or queued, join a random track.
			List<BoatRace> races = new ArrayList<>(manager.getRaces().values());
			Collections.shuffle(races);
			for (BoatRace race : races) {
				if (race.canJoinRace(arg0)) {
					race.joinRace(arg0);
					return;
				}
			}
		}

		if (highestPlayerRace != null) {
			highestPlayerRace.joinRace(arg0);
			return;
		}
		// If there is not a playable race, find a race with the most amount of players with a queue
		if (largestQueueRace != null) {
			largestQueueRace.joinQueue(arg0);
			return;
		}
		// Both should not be null ever (unless there are no tracks available)
		arg0.sendMessage(ChatColor.RED+"There are no available tracks to join. Please try again later.");
	}
	
}
