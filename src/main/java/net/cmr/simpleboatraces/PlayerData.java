package net.cmr.simpleboatraces;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.function.Function;

import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
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
	final String HONK_KEY = "honk_sound";
	final String TRAIL_KEY = "trail_effect";
	
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

	public enum HonkSound {
		BASS("Bass Horn", Sound.BLOCK_NOTE_BLOCK_BASS, Material.STONE, 0),
		FLUTE("Flute Horn", Sound.BLOCK_NOTE_BLOCK_FLUTE, Material.BAMBOO, 5),
		BELL("Bicycle Bell", Sound.BLOCK_NOTE_BLOCK_BELL, Material.BELL, 10),
		DREAM_HORN("Dream Horn", Sound.ITEM_GOAT_HORN_SOUND_7, Material.GOAT_HORN, 12, 60, 1),
		AMETHYST("Amethyst Bell", Sound.BLOCK_AMETHYST_CLUSTER_BREAK, Material.AMETHYST_CLUSTER, 15),
		PONDER_HORN("Ponder Horn", Sound.ITEM_GOAT_HORN_SOUND_0, Material.GOAT_HORN, 18, 30, 1),
		GHAST("Ghast Whistle", Sound.ENTITY_GHAST_SCREAM, Material.GHAST_TEAR, 20, 4),
		SHEEP("Sheep Bell", Sound.ENTITY_SHEEP_AMBIENT, Material.SHEEP_SPAWN_EGG, 25),
		SING_HORN("Sing Horn", Sound.ITEM_GOAT_HORN_SOUND_1, Material.GOAT_HORN, 28, 20, 1),
		COW("Cow Horn", Sound.ENTITY_COW_HURT, Material.LEATHER, 30),
		AMETHYST_BLOCK("Chime Bell", Sound.BLOCK_AMETHYST_BLOCK_RESONATE, Material.AMETHYST_BLOCK, 35),
		ALLAY("Allay Horn", Sound.ENTITY_ALLAY_DEATH, Material.STRIDER_SPAWN_EGG, 40),
		DRAGON("Dragon Horn", Sound.ENTITY_ENDER_DRAGON_GROWL, Material.DRAGON_EGG, 45, 30),
		FOG_HORN("Fog Horn", Sound.ITEM_GOAT_HORN_SOUND_6, Material.GOAT_HORN, 50, 65, 1),
		;

		public final String name;
		public final Sound sound;
		public final Material material;
		public int tickCooldown = 0;
		public int level = 0;
		public float pitch = 2;
		HonkSound(String name, Sound sound, Material material, int level) {
			this.name = name;
			this.sound = sound;
			this.material = material;
			this.level = level;
		}
		HonkSound(String name, Sound sound, Material material, int level, int tickCooldown) {
			this(name, sound, material, level);
			this.tickCooldown = tickCooldown;
		}
		HonkSound(String name, Sound sound, Material material, int level, int tickCooldown, float pitch) {
			this(name, sound, material, level, tickCooldown);
			this.pitch = pitch;
		}
	}

	public HonkSound getHonkSound() {
		String storedValue = config.getString(HONK_KEY, HonkSound.values()[0].name());
		try {
			return HonkSound.valueOf(storedValue);
		} catch (IllegalArgumentException e) {
			setHonkSound(HonkSound.values()[0]);
		}
		return HonkSound.values()[0];
	}
	public void setHonkSound(HonkSound sound) {
		config.set(HONK_KEY, sound.name());
		onChangeConfig();
	}

	public enum TrailEffect {
		NONE("None", null, Material.BARRIER, 0),
		CRIT("Spikes", Particle.CRIT, Material.POINTED_DRIPSTONE, 5, 3),
		HEARTS("Hearts", Particle.HEART, Material.RED_DYE, 8),
		ANGRY_VILLAGER("Angry Villager", Particle.VILLAGER_ANGRY, Material.VILLAGER_SPAWN_EGG, 12),
		ENCHANT("Enchant", Particle.ENCHANTMENT_TABLE, Material.ENCHANTING_TABLE, 15, 7),
		SPLASHING("Splashing", Particle.WATER_SPLASH, Material.WATER_BUCKET, 20, 3),
		FIRE("Fire", Particle.FLAME, Material.FLINT_AND_STEEL, 25),
		CHERRY_LEAVES("Cherry Leaves", Particle.CHERRY_LEAVES, Material.CHERRY_SAPLING, 30, 2),
		PORTAL("Portal", Particle.REVERSE_PORTAL, Material.RESPAWN_ANCHOR, 40, 4),
		SMOKE("Smoke", Particle.CAMPFIRE_COSY_SMOKE, Material.CAMPFIRE, 50),
		;

		public final String name;
		public final Particle effect;
		public final Material material;
		public int level = 0;
		public int quantity = 1;

		TrailEffect(String name, Particle effect, Material material, int level) {
			this.name = name;
			this.effect = effect;
			this.material = material;
			this.level = level;
		}
		TrailEffect(String name, Particle effect, Material material, int level, int quantity) {
			this(name, effect, material, level);
			this.quantity = quantity;
		}
	}

	public TrailEffect getTrailEffect() {
		String storedValue = config.getString(TRAIL_KEY, TrailEffect.NONE.name());
		try {
			return TrailEffect.valueOf(storedValue);
		} catch (IllegalArgumentException e) {
			setTrailEffect(TrailEffect.NONE);
		}
		return TrailEffect.NONE;
	}
	public void setTrailEffect(TrailEffect effect) {
		config.set(TRAIL_KEY, effect.name());
		onChangeConfig();
	}
	
}
