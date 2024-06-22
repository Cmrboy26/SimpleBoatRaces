package net.cmr.simpleboatraces;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Boat.Type;
import org.bukkit.entity.ChestBoat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.util.Vector;

import com.google.common.base.Predicates;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

public class BoatRace {

	public enum RaceState {
		WAITING,
		STARTING,
		RACING,
		ENDING
	}
	
	SimpleBoatRaces plugin;
	BoatRaceConfiguration config;
	RaceState currentState;
	
	HashSet<Player> players;
	int updateMethodTaskID = -1;
	final String name;
	
	final int MINIMUM_PLAYERS_COUNTDOWN = 20;
	final int DESIRED_PLAYERS_COUNTDOWN = 10;
	final int TIME_IN_ENDING = 5;
	final float STARTING_COUNTDOWN_TIME = 3.05f;
	final int BOAT_RACE_HALFTIME_COUNTDOWN = 30;
	
	// Waiting Variables
	HashSet<Player> queuedPlayers = new HashSet<>();
	float waitCountdown = MINIMUM_PLAYERS_COUNTDOWN;
	
	// Starting Variables
	float startingCountdown = STARTING_COUNTDOWN_TIME;
	
	// Racing Variables
	HashSet<Player> playersStillRacing;
	HashSet<Player> checkpointPlayers;
	ConcurrentHashMap<Player, Integer> bestLaps;
	ConcurrentHashMap<Player, Integer> laps;
	ConcurrentHashMap<Player, Integer> tickTimeCounter;
	float averageTicks = -1;
	int place = 0;
	boolean timerStarted = false;
	
	// Optional Powerup Variables
	public volatile HashSet<Vector> removeBlocksOnReset = new HashSet<>();
	
	public static final String PARTICLE_SELECTOR_NAME = "" + ChatColor.AQUA + "Particle Selector";
	public static final String BOAT_SELECTOR_NAME = "" + ChatColor.AQUA + "Boat Selector";
	public static final String HORN_SELECTOR_NAME = "" + ChatColor.AQUA + "Horn Selector";

	public static final String LEAVE_GAME_NAME = "" + ChatColor.RED + "Leave Game";
	public static final String FAST_START_GAME_NAME = "" + ChatColor.GOLD + "Quick Start Game";
	public static final String HORN_NAME = "" + ChatColor.GOLD + "Honk Horn";
	
	// Ending Variables
	ArrayList<Player> placeList;
	float endingCountdown = 0;
	
	public BoatRace(String name, SimpleBoatRaces plugin, BoatRaceConfiguration config) {
		this.name = name;
		this.plugin = plugin;
		this.config = config;
		this.players = new HashSet<>();
		currentState = RaceState.WAITING;
		
		BukkitScheduler scheduler = plugin.getServer().getScheduler();
		updateMethodTaskID = scheduler.scheduleSyncRepeatingTask(plugin, new Runnable() {
            @Override
            public void run() {
                update();
            }
        }, 0L, 1L);
	}
	
	private static void forAllRacingPlayers(BoatRace race, Consumer<Player> consumer) {
		race.players.iterator().forEachRemaining(consumer);
	}
	
	private static void removePlayersFromRaceIf(BoatRace race, Predicate<Player> predicate, boolean teleportOut) {
		HashSet<Player> clone = new HashSet<>(race.players);
		for (Player player : clone) {
			boolean remove = predicate.test(player);
			if (remove) {
				race.leaveRace(player, teleportOut);
			}
		}
	}
	
	/**
	 * If the game is running, do not allow the player to join the game.
	 * Will also check if the player has the proper permissions to join the race, which
	 * can be modified by overriding hasPermissionToJoinRace().
	 * If the track cannot be played, as specified by isRacePlayable(), the player will not be able to join.
	 */
	public boolean canJoinRace(Player player) {
		return hasPermissionToJoinRace(player) && currentState == RaceState.WAITING && isRacePlayable();
	}

	public boolean canQueueRace(Player player) {
		return hasPermissionToJoinRace(player) && currentState != RaceState.WAITING && isRacePlayable();
	}
	
	/**
	 * Overridable. Can be used to set custom conditions to see if a player can join the game.
	 */
	private boolean hasPermissionToJoinRace(Player player) {
		return true;
	}
	
	/*
	 * Used to validate if the racetrack is playable.
	 * If the worlds are not correctly set, it will return false.
	 */
	public boolean isRacePlayable() {
		Location start = getStartingLocation();
		Location wait = getWaitingLocation();
		Location lobby = getLobbyLocation();

		return start != null && wait != null && lobby != null;
	}

	public void allowQueueIn() {
		HashSet<Player> playersToJoin = new HashSet<>(queuedPlayers);
		for (Player p : playersToJoin) {
			if (canJoinRace(p)) {
				joinRace(p);
			} else {
				leaveQueue(p);
			}
		}
		queuedPlayers.clear();
		playersToJoin.clear();
	}

	public void kickAllFromQueue() {
		HashSet<Player> playersToLeave = new HashSet<>(queuedPlayers);
		for (Player p : playersToLeave) {
			leaveQueue(p);
		}
		queuedPlayers.clear();
		playersToLeave.clear();
	}
	
	// Runs every tick (1/20 of a second)
	public void update() {
		float dt = 1/20f;
		int playerCount = players.size();
		if (playerCount == 0) {
			if (currentState != RaceState.WAITING) {
				onRaceFinished(false);
				allowQueueIn();
			}
			waitCountdown = MINIMUM_PLAYERS_COUNTDOWN;
		}
		switch (currentState) {
		case WAITING: 
			// Reset the counter if no one is in the game.
			if (playerCount == 0) {
				waitCountdown = MINIMUM_PLAYERS_COUNTDOWN;
			}
			// If the minimum players are in, count down.
			if (playerCount >= config.minPlayers) {
				waitCountdown -= dt;
			}
			
			forAllRacingPlayers(this, (Player player) -> {
				float exp = ((waitCountdown % 1f));
				exp = Math.max(exp, 0);
				player.setExp(exp);
				player.setLevel((int) Math.ceil(waitCountdown));
			});
			
			if ((int) Math.ceil(waitCountdown + dt) != (int) Math.ceil(waitCountdown) && waitCountdown + dt < 6) {
				forAllRacingPlayers(this, (Player player) -> {
					player.playSound(player, Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 0f);
					if (Math.ceil(waitCountdown) != 0) {
						plugin.info(player, "Starting in "+((int)Math.ceil(waitCountdown))+"...");
					}
				});
			}
			
			// Start the game once the countdown reaches 0
			if (waitCountdown <= 0) {
				onRaceStarting();
			}
			break;
		case STARTING:
			startingCountdown -= dt;
			forAllRacingPlayers(this, (Player player) -> {
				float exp = ((startingCountdown % 1f));
				exp = Math.max(exp, 0);
				player.setExp(exp);
				player.setLevel((int) Math.ceil(startingCountdown));
				player.sendTitle(((int) Math.ceil(startingCountdown))+"", "", 0, 17, 3);
			});
			
			if ((int) Math.ceil(startingCountdown + dt) != (int) Math.ceil(startingCountdown) && startingCountdown + dt < 6) {
				forAllRacingPlayers(this, (Player player) -> {
					player.playSound(player, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 0f);
					if (Math.ceil(startingCountdown) != 0) {
						plugin.info(player, "Begin racing in "+((int)Math.ceil(startingCountdown))+"...");
					}
				});
			}
			
			if (startingCountdown <= 0) {
				onRaceStarted();
				forAllRacingPlayers(this, (Player player) -> {
					player.sendTitle(ChatColor.YELLOW + "GO!!", "", 5, 3 * 20, 10);
					player.playSound(player, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
				});
			}
			break;
		case RACING:
			forAllRacingPlayers(this, (Player player) -> {
				boolean isInLap = isInRegion(player.getLocation(), config.finishLinePos1, config.finishLinePos2);
				boolean isInCheckpoint = isInRegion(player.getLocation(), config.checkPointPos1, config.checkPointPos2);
				
				if (!laps.containsKey(player)) {
					if (!isInLap) {
						return;
					}
					laps.put(player, 0);
				}
				
				final int ticks = tickTimeCounter.getOrDefault(player, -1);
				tickTimeCounter.put(player, ticks+1);
				
				int playerlaps = laps.get(player);
				int pb = plugin.getPersonalBestTicks(this, player);
				if ((ticks > 60 || playerlaps == 0) && playersStillRacing.contains(player)) {
					String messageText = SimpleBoatRaces.getTimeFromTicks(ticks);
					net.md_5.bungee.api.ChatColor newcolor = net.md_5.bungee.api.ChatColor.YELLOW;
					if (pb != -1 && ticks >= pb) {
						newcolor = net.md_5.bungee.api.ChatColor.RED;
						messageText += " +"+SimpleBoatRaces.getTimeFromTicks(ticks-pb)+"";
					}
					TextComponent message = new TextComponent(messageText);
					message.setColor(newcolor);
					player.spigot().sendMessage(ChatMessageType.ACTION_BAR, message);
				}
				
				if (isInCheckpoint) {
					checkpointPlayers.add(player);
				}
				if (isInLap && ticks == -1) {
					tickTimeCounter.put(player, 0);
				}
				if (isInLap && checkpointPlayers.contains(player)) {
					onLap(player);
				}
			});

			if (playersStillRacing.size() == 0) {
				onRaceEnding();
				break;
			}
			// Once the place is greater than 50% of the players, give the remaining players 20 more seconds to finish
			if (place >= players.size() / 2f && !timerStarted) {
				timerStarted = true;
				final int timeRemaining = BOAT_RACE_HALFTIME_COUNTDOWN;
				forAllRacingPlayers(this, (Player player) -> {
					player.playSound(player, Sound.BLOCK_NOTE_BLOCK_PLING, 1f, .5f);
					plugin.info(player, timeRemaining+" seconds remaining in the race!");
				});
				//BukkitScheduler scheduler = plugin.getServer().getScheduler();
				BukkitRunnable runnable = new BukkitRunnable() {
					int count = 0;
					public void run() {
						count++;
						if (currentState != RaceState.RACING) {
							cancel();
						}
						if (count >= timeRemaining) {
							if (currentState == RaceState.RACING) {
								for (Player player : playersStillRacing) {
									removeFromRacetrack(player);
								}
								onRaceEnding();
							}
						}
					};
				};
				runnable.runTaskTimer(plugin, 20, 20);
				/*scheduler.scheduleSyncDelayedTask(plugin, () -> {
					if (currentState == RaceState.RACING) {
						for (Player player : playersStillRacing) {
							removeFromRacetrack(player);
						}
						onRaceEnding();
					}
				}, 20 * timeRemaining);*/
				
				for (int i = 1; i <= 5; i++) {
					final int time = i;
					BukkitRunnable iterationRunnable = new BukkitRunnable() {
						int count = 0;
						public void run() {
							count++;
							if (currentState != RaceState.RACING) {
								cancel();
							}
							if (count == timeRemaining - time) {
								if (currentState == RaceState.RACING) {
									forAllRacingPlayers(BoatRace.this, (Player player) -> {
										player.playSound(player, Sound.BLOCK_NOTE_BLOCK_PLING, 1f, .5f);
										plugin.info(player, time+" seconds remaining!");
									});
								}
							}
						};
					};
					iterationRunnable.runTaskTimer(plugin, 20, 20);
					/*scheduler.scheduleSyncDelayedTask(plugin, () -> {
						if (currentState == RaceState.RACING) {
							forAllRacingPlayers(this, (Player player) -> {
								player.playSound(player, Sound.BLOCK_NOTE_BLOCK_PLING, 1f, .5f);
								plugin.info(player, time+" seconds remaining!");
							});
						}
					}, 20 * (timeRemaining - i));*/
				}
			}
			break;
		case ENDING:
			endingCountdown -= dt;
			forAllRacingPlayers(this, (Player player) -> {
				float exp = ((endingCountdown % 1f));
				exp = Math.max(exp, 0);
				player.setExp(exp);
				player.setLevel((int) Math.ceil(endingCountdown));
			});
			
			break;
		default:
			onRaceFinished(false);
			break;
		}
	}
	
	public boolean isInRegion(Location source, Vector bound1, Vector bound2) {
	  return source.getX() >= Math.min(bound1.getX(), bound2.getX()) &&
	      source.getY() >= Math.min(bound1.getY(), bound2.getY()) &&
	      source.getZ() >= Math.min(bound1.getZ(), bound2.getZ()) &&
	      source.getX() <= Math.max(bound1.getX(), bound2.getX()) &&
	      source.getY() <= Math.max(bound1.getY(), bound2.getY()) &&
	      source.getZ() <= Math.max(bound1.getZ(), bound2.getZ()); 
	}
	
	public void joinQueue(Player player) {
		// Players should only be allowed to join if the game is in the WAITING state
		if (currentState == RaceState.WAITING) {
			throw new AssertionError("Development issue. Players should not be able to join the game in the "+currentState+" state.");
		}

		plugin.onPlayerJoinMinigame(player);

		queuedPlayers.add(player);
		
		player.teleport(getWaitingLocation());
		player.getInventory().clear();
		player.setInvulnerable(true);
		player.setHealth(20);
		player.setSaturation(20);
		player.setFoodLevel(20);
		player.setExp(0);
		player.setLevel(0);
		player.setGameMode(GameMode.ADVENTURE);
	}

	public void leaveQueue(Player player) {
		queuedPlayers.remove(player);	

		removeFromRacetrack(player);
		player.setGameMode(GameMode.ADVENTURE);
		player.getInventory().clear();
		player.teleport(getLobbyLocation());
		player.setHealth(20);
		player.setSaturation(20);
		player.setFoodLevel(20);
		player.setInvulnerable(false);
		player.setExp(0);
		player.setLevel(0);

		plugin.onPlayerLeaveMinigame(player);
	}

	public void joinRace(Player player) {
		// Players should only be allowed to join if the game is in the WAITING state
		if (currentState != RaceState.WAITING) {
			throw new AssertionError("Development issue. Players should not be able to join the game in the "+currentState+" state.");
		}

		plugin.onPlayerJoinMinigame(player);
		
		players.add(player);
		player.setGameMode(GameMode.ADVENTURE);
		player.getInventory().clear();
		player.teleport(getWaitingLocation());
		player.setHealth(20);
		player.setSaturation(20);
		player.setFoodLevel(20);
		player.setInvulnerable(true);
		setSelectorItems(player);
		setLeaveItem(player);
		setFastStartItem(player);
		
		if (currentState == RaceState.WAITING) {
			if (players.size() == config.maxPlayers && waitCountdown > DESIRED_PLAYERS_COUNTDOWN) {
				waitCountdown = DESIRED_PLAYERS_COUNTDOWN;
			}
		}
		forAllRacingPlayers(this, (Player playerr) -> {
			plugin.info(playerr, ChatColor.YELLOW + player.getName() + ChatColor.WHITE + " joined the game ("+players.size()+"/"+config.maxPlayers+")");
		});
	}
	
	public void leaveRace(Player player, boolean teleportOut) {
		players.remove(player);
		
		removeFromRacetrack(player);
		player.setGameMode(GameMode.ADVENTURE);
		player.getInventory().clear();
		if (teleportOut) player.teleport(getLobbyLocation());
		player.setHealth(20);
		player.setSaturation(20);
		player.setFoodLevel(20);
		player.setInvulnerable(false);
		player.setExp(0);
		player.setLevel(0);
		
		if (currentState == RaceState.RACING) {
			playersStillRacing.remove(player);
			laps.remove(player);
			checkpointPlayers.remove(player);
		}

		plugin.onPlayerLeaveMinigame(player);
	}
	
	public void forceStop() {
		// Kick every player from the track
		forAllRacingPlayers(this, (Player player) -> {
			player.sendMessage(ChatColor.RED + "The race has been forcefully stopped. Please wait a moment to race again.");
		});
		onRaceFinished(false);
		kickAllFromQueue();
	}
	
	public void onRaceStarting() {
		currentState = RaceState.STARTING;
		startingCountdown = STARTING_COUNTDOWN_TIME;
		
		final ArrayList<Boat> playerBoats = new ArrayList<>();
		
		int radius = 6;
		Location starting = getStartingLocation();
		// Set the air area around the boat to air
		int tx = (int) starting.getX();
		int ty = (int) starting.getY();
		int tz = (int) starting.getZ();
		for (int x = tx-radius; x < tx+radius; x++) {
			for (int y = ty-1; y < ty+1; y++) {
				for (int z = tz-radius; z < tz+radius; z++) {
					Block at = starting.getWorld().getBlockAt(x, y, z);
					if (at.isEmpty()) {
						at.setType(Material.BARRIER);
					}
				}
			}
		}

		forAllRacingPlayers(this, (Player player) -> {
			Location loc = getStartingLocation();
			
			// spread players out 3 blocks from the center
			int radiusSpread = radius - 2;

			double spreadX = (Math.random() - 0.5) * 2 * radiusSpread;
			double spreadZ = (Math.random() - 0.5) * 2 * radiusSpread;

			loc.setX(loc.getX() + spreadX);
			loc.setZ(loc.getZ() + spreadZ);
			player.teleport(loc);
			player.getInventory().clear();
			setLeaveItem(player);
			setHornItem(player);
			
			boolean chestBoat = plugin.getPlayerData(player).preferChestBoat();
			
			Boat playerBoat;
			if (chestBoat) {
				playerBoat = player.getWorld().spawn(loc, ChestBoat.class);
			} else {
				playerBoat = player.getWorld().spawn(loc, Boat.class);
			}
			
			Type type = plugin.getPlayerData(player).getBoatType();
			playerBoat.setBoatType(type);
			playerBoat.addPassenger(player);
			playerBoat.setInvulnerable(true);
			playerBoats.add(playerBoat);
			
			player.playSound(player, Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f);
		});
		
		BukkitRunnable runnable = new BukkitRunnable() {
			@Override
			public void run() {
				Location starting = getStartingLocation();
				int tx = (int) starting.getX();
				int ty = (int) starting.getY();
				int tz = (int) starting.getZ();
				for (int x = tx-radius; x < tx+radius; x++) {
					for (int y = ty-1; y < ty+1; y++) {
						for (int z = tz-radius; z < tz+radius; z++) {
							Block at = starting.getWorld().getBlockAt(x, y, z);
							if (at.getType() == Material.BARRIER) {
								at.setType(Material.AIR);
							}
						}
					}
				}
			}
		};
		runnable.runTaskLater(plugin, (long) (startingCountdown * 20));
	}
	
	public void onRaceStarted() {
		currentState = RaceState.RACING;
		playersStillRacing = new HashSet<>(players);
		checkpointPlayers = new HashSet<>();
		bestLaps = new ConcurrentHashMap<>();
		laps = new ConcurrentHashMap<>();
		forAllRacingPlayers(this, (Player player) -> {
			player.setGameMode(GameMode.SURVIVAL);
		});
		place = 0;
		timerStarted = false;
		placeList = new ArrayList<>();
		tickTimeCounter = new ConcurrentHashMap<>();
	}
	
	public void onRaceEnding() {
		currentState = RaceState.ENDING;
		
		forAllRacingPlayers(this, (Player player) -> {
			player.teleport(getWaitingLocation());
			player.playSound(player, Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f);
			int index = placeList.indexOf(player);
			int place = index + 1;
			String title = ChatColor.YELLOW + "" + place + placeSuffix(place);
			if (place == 0) {
				title = ChatColor.RED + "Lost :(";
			} else if (place <= 3) {
				title = "" + ChatColor.MAGIC + ChatColor.AQUA + "|:" 
						+ ChatColor.RESET + " " + title + " "
						+ ChatColor.RESET + ChatColor.AQUA + ":|";
			}
			String subtitle = "";
			for (int i = 3 - place; i >= 0 && place != 0; i--) {
				subtitle += " " + ChatColor.AQUA + " " + ChatColor.MAGIC + ":::::";
			}
			subtitle += ChatColor.RESET + " ";
			subtitle.substring(1);
			player.sendTitle(title, ChatColor.RESET + subtitle, 0, (int) (TIME_IN_ENDING / 2f) * 20, 10);
			setLeaveItem(player);
		});
		
		endingCountdown = TIME_IN_ENDING;
		
		// After 6 seconds, kick everyone from the game
		BukkitScheduler scheduler = plugin.getServer().getScheduler();
		scheduler.scheduleSyncDelayedTask(plugin, () -> {
			onRaceFinished(true);
			allowQueueIn();
		}, 20 * TIME_IN_ENDING);
		
		grantPlayersXP();
	}
	
	/**
	 * Can be called at any time to stop the game.
	 */
	public void onRaceFinished(boolean rejoinPlayers) {
		HashSet<Player> playersRemaining = new HashSet<>(players);
		removePlayersFromRaceIf(this, Predicates.<Player>alwaysTrue(), !rejoinPlayers);
		currentState = RaceState.WAITING;
		waitCountdown = MINIMUM_PLAYERS_COUNTDOWN;
		
		HashSet<Vector> copiedRemoveBlocks = new HashSet<>(removeBlocksOnReset);
		for (Vector vec : copiedRemoveBlocks) {
			Location l = new Location(getRaceWorld(), vec.getBlockX(), vec.getBlockY(), vec.getBlockZ());
			l.getWorld().getBlockAt(l).setType(Material.AIR);
		}
		removeBlocksOnReset.clear();
		
		if (!rejoinPlayers) {
			return;
		}
		for (Player p : playersRemaining) {
			joinRace(p);
		}
	}
	
	public void onLap(Player player) {
		checkpointPlayers.remove(player);
		int playerLaps = laps.getOrDefault(player, 0);
		playerLaps++;
		laps.put(player, playerLaps);
		
		int ticks = tickTimeCounter.get(player);
		int currentBest = bestLaps.getOrDefault(player, Integer.MAX_VALUE);
		if (ticks < currentBest) {
			bestLaps.put(player, ticks);
		}
		
		String lapInfo = "Finished Lap "+playerLaps+" in "+SimpleBoatRaces.getTimeFromTicks(ticks);
		int personalBest = plugin.getPersonalBestTicks(this, player);
		plugin.attemptToSetPersonalBestTicks(this, player, ticks);
		
		String messageText = "Lap Completed: "+SimpleBoatRaces.getTimeFromTicks(ticks);
		net.md_5.bungee.api.ChatColor newcolor = net.md_5.bungee.api.ChatColor.YELLOW;
		if (personalBest > ticks) {
			messageText = "[PERSONAL BEST!] "+messageText+" -"+SimpleBoatRaces.getTimeFromTicks(personalBest-ticks)+"";
			newcolor = net.md_5.bungee.api.ChatColor.AQUA;
		}
		TextComponent message = new TextComponent(messageText);
		message.setColor(newcolor);
		message.setBold(true);
		player.spigot().sendMessage(ChatMessageType.ACTION_BAR, message);
		
		if (personalBest > ticks) {
			// NEW PB!
			lapInfo = ChatColor.AQUA + lapInfo;
			lapInfo += ChatColor.DARK_AQUA + " [PERSONAL BEST!] (" + SimpleBoatRaces.getTimeFromTicks(personalBest-ticks) + " faster)";
			player.playSound(player, Sound.ENTITY_PLAYER_LEVELUP, 1.25f, 0.75f);
		}
		
		tickTimeCounter.put(player, 0);
		
		player.playSound(player, Sound.BLOCK_DISPENSER_FAIL, 2, 1.75f);
		plugin.info(player, lapInfo);
		
		if (playerLaps == config.laps - 1) {
			// FINAL LAP!
			forAllRacingPlayers(this, (Player p) -> {
				plugin.info(p, ChatColor.YELLOW + player.getName() + ChatColor.WHITE + " is on the "+ChatColor.BOLD+ChatColor.GOLD+ "FINAL LAP!");
			});
		}
		if (playerLaps >= config.laps) {
			// FINISHED!
			place++;
			forAllRacingPlayers(this, (Player p) -> {
				plugin.info(p, ChatColor.YELLOW + player.getName() + ChatColor.WHITE + " finished in "+place+placeSuffix(place)+" place!");
			});
			
			placeList.add(player);
			removeFromRacetrack(player);
			
			player.sendTitle(ChatColor.GOLD + "FINISHED!", ChatColor.YELLOW + "" + place+placeSuffix(place), 5, 40, 5);
			
			if (place == 1) {
				if (players.size() != 1) {
					plugin.getPlayerData(player).addWin();
				}
				player.playSound(player, Sound.ENTITY_PLAYER_LEVELUP, 10000, 1);
				player.playSound(player, Sound.UI_TOAST_CHALLENGE_COMPLETE, 10000, 1.5f);
			} else if (place == 2) {
				player.playSound(player, Sound.ENTITY_PLAYER_LEVELUP, 10000, 1);
			} else if (place == 3) {
				player.playSound(player, Sound.ENTITY_PLAYER_LEVELUP, 10000, 1);
			} else {
				player.playSound(player, Sound.ENTITY_PLAYER_LEVELUP, 10000, 2);
			}
		}
	}
	
	public String placeSuffix(int place) {
		if (place >= 4 && place <= 20) {
			return "th";
		}
		if (place % 10 == 1) {
			return "st";
		}
		if (place % 10 == 2) {
			return "nd";
		}
		if (place % 10 == 3) {
			return "rd";
		}
		return "th";
	}
	
	public void removeFromRacetrack(Player player) {
		if (!(currentState == RaceState.RACING || currentState == RaceState.STARTING)) {
			return;
		}
		Entity vehicle = player.getVehicle();
		if (vehicle != null) {
			vehicle.remove();
		}
		player.teleport(getWaitingLocation());
		player.getInventory().clear();
		player.setGameMode(GameMode.ADVENTURE);
		player.playSound(player, Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 2f);
		player.playSound(player, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
		if (playersStillRacing != null) {
			playersStillRacing.remove(player);
		}
	}
	
	public void quickstart() {
		if (currentState == RaceState.WAITING) {
			onRaceStarting();
		}
	}
	
	public void dispose() {
		BukkitScheduler scheduler = plugin.getServer().getScheduler();
		if (updateMethodTaskID != -1) {
			scheduler.cancelTask(updateMethodTaskID);
		}
	}
	
	public World getRaceWorld() {
		return getStartingLocation().getWorld();
	}
	
	public Location getWaitingLocation() {
		if (config.waitPosition == null) {
			return null;
		}
		return config.waitPosition.clone();
	}
	
	public Location getStartingLocation() {
		if (config.startingPosition == null) {
			return null;
		}
		return config.startingPosition.clone();
	}
	
	public Location getLobbyLocation() {
		if (config.lobbyPosition == null) {
			return null;
		}
		return config.lobbyPosition.clone();
	}
	
	public void setSelectorItems(Player player) {
		ItemStack particleSelector = new ItemStack(Material.FIREWORK_STAR, 1);
		ItemMeta meta = particleSelector.getItemMeta();
		meta.setDisplayName(BoatRace.PARTICLE_SELECTOR_NAME);
		particleSelector.setItemMeta(meta);
		player.getInventory().setItem(3, particleSelector);

		ItemStack boatSelector = new ItemStack(Material.CLOCK, 1);
		meta = boatSelector.getItemMeta();
		meta.setDisplayName(BoatRace.BOAT_SELECTOR_NAME);
		boatSelector.setItemMeta(meta);
		player.getInventory().setItem(4, boatSelector);

		ItemStack hornSelector = new ItemStack(Material.SUNFLOWER, 1);
		meta = hornSelector.getItemMeta();
		meta.setDisplayName(BoatRace.HORN_SELECTOR_NAME);
		hornSelector.setItemMeta(meta);
		player.getInventory().setItem(5, hornSelector);
	}
	
	public void setLeaveItem(Player player) {
		ItemStack leaveSelector = new ItemStack(Material.RED_BED, 1);
		ItemMeta meta = leaveSelector.getItemMeta();
		meta.setDisplayName(LEAVE_GAME_NAME);
		leaveSelector.setItemMeta(meta);
		player.getInventory().setItem(8, leaveSelector);
	}
	
	public void setFastStartItem(Player player) {
		if (player.isOp() || player.hasPermission("cmr.boatraces.quickstart")) {
			ItemStack leaveSelector = new ItemStack(Material.NETHER_STAR, 1);
			ItemMeta meta = leaveSelector.getItemMeta();
			meta.setDisplayName(FAST_START_GAME_NAME);
			leaveSelector.setItemMeta(meta);
			player.getInventory().setItem(0, leaveSelector);
		}
	}
	
	public void setHornItem(Player player) {
		ItemStack leaveSelector = new ItemStack(Material.SUNFLOWER, 1);
		ItemMeta meta = leaveSelector.getItemMeta();
		meta.setDisplayName(HORN_NAME);
		leaveSelector.setItemMeta(meta);
		player.getInventory().setItem(0, leaveSelector);
	}
	
	public void grantPlayersXP() {
		forAllRacingPlayers(this, (Player player) -> {
			// If there are no other players, multiply the XP by half.
			// If there ARE other players, multiply xp by 2 if first, 1.5 if second, 1.25 if third
			float multiplier = 1;
			int place = placeList.indexOf(player)+1;
			int totalPlayers = players.size();
			if (totalPlayers == 1) {
				multiplier = .75f;
			} else {
				if (place == 1) multiplier = 2;
				if (place == 2) multiplier = 1.5f;
				if (place == 3) multiplier = 1.25f;
				if (totalPlayers == 2 && place == 1) multiplier = 1.5f;
				if (totalPlayers == 2 && place == 2) multiplier = 1.25f;
				if (place == 0) multiplier = .75f; 
			}
			int ticks = bestLaps.getOrDefault(player, 0);
			float lapMinutes = config.laps * ticks / (20f * 60f);
			lapMinutes = Math.min(lapMinutes, config.laps * 1.5f);
			long xp = (long) Math.ceil(30 * lapMinutes);
			
			long previousLevel = plugin.getPlayerData(player).getLevel();
			boolean leveledUp = plugin.getPlayerData(player).addXP((long) Math.ceil(xp * multiplier));
			long currentLevel = plugin.getPlayerData(player).getLevel();
			
			String message = " \n \n"+ChatColor.GOLD+ChatColor.BOLD+" GAME OVER! ";
			message += " \n";
			String multiplierString = ChatColor.DARK_AQUA+" * "+multiplier;
			if (multiplier <= 1) {
				multiplierString = "";
				xp *= multiplier;
			}
			message += ChatColor.RESET+" + "+ChatColor.WHITE+xp+""+multiplierString+ChatColor.WHITE+" XP";
			message += " \n";
			if (leveledUp) {
				message += ChatColor.RESET+" - "+ChatColor.YELLOW+ChatColor.BOLD+"LEVELED UP! "+ChatColor.RED+ChatColor.AQUA+"["+previousLevel+" -> "+currentLevel+"]\n";
			}
			if (place != 0) {
				message += ChatColor.RESET+" - "+ChatColor.AQUA+"Finished in "+place+placeSuffix(place)+" place!";
			}
			message += " \n";
			message += " \n \n";
			player.sendMessage(message);
		});
	}
	
	public String getName() {
		return name;
	}
	
	public static class BoatRaceConfiguration implements ConfigurationSerializable {
		
		public Vector finishLinePos1, finishLinePos2;
		public Vector checkPointPos1, checkPointPos2;
		public Location waitPosition, startingPosition, lobbyPosition;
		public int maxPlayers, recommendedPlayers, minPlayers, laps;
		
		public BoatRaceConfiguration() {
			finishLinePos1 = new Vector();
			finishLinePos2 = new Vector();
			checkPointPos1 = new Vector();
			checkPointPos2 = new Vector();
			waitPosition = null;
			startingPosition = null;
			lobbyPosition = null;
			maxPlayers = 8;
			minPlayers = 1;
			laps = 3;
		}
		
		public BoatRaceConfiguration(Map<String, Object> map) {
			finishLinePos1 = (Vector) map.get("finishLinePos1");
			finishLinePos2 = (Vector) map.get("finishLinePos2");
			checkPointPos1 = (Vector) map.get("checkPointPos1");
			checkPointPos2 = (Vector) map.get("checkPointPos2");
			try {
				waitPosition = (Location) map.get("waitPosition");
				startingPosition = (Location) map.get("startingPosition");
				lobbyPosition = (Location) map.get("lobbyPosition");
			} catch (Exception e) {
				e.printStackTrace();
			}
			maxPlayers = (Integer) map.getOrDefault("maxPlayers", 6);
			minPlayers = (Integer) map.getOrDefault("minPlayers", 1);
			laps = (Integer) map.getOrDefault("laps", 3);
		}

		@Override
		public Map<String, Object> serialize() {
			Map<String, Object> map = new HashMap<>();
			map.put("maxPlayers", maxPlayers);
			map.put("minPlayers", minPlayers);
			map.put("waitPosition", waitPosition);
			map.put("startingPosition", startingPosition);
			map.put("lobbyPosition", lobbyPosition);
			map.put("finishLinePos1", finishLinePos1);
			map.put("finishLinePos2", finishLinePos2);
			map.put("checkPointPos1", checkPointPos1);
			map.put("checkPointPos2", checkPointPos2);
			return map;
		}
		
		public static BoatRaceConfiguration deserialize(Map<String, Object> map) {
			BoatRaceConfiguration configuration = new BoatRaceConfiguration(map);
			return configuration;
        }
		
	}
	
	
}
