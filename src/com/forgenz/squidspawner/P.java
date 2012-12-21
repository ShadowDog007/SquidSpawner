package com.forgenz.squidspawner;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import com.forgenz.mobmanager.MMChunk;
import com.forgenz.mobmanager.MMCoord;
import com.forgenz.mobmanager.MMLayer;
import com.forgenz.mobmanager.MMWorld;
import com.forgenz.mobmanager.Spiral;

public class P extends JavaPlugin implements Runnable
{
	public static P p = null;
	public static MemoryConfiguration cfg = null;

	private com.forgenz.mobmanager.P mobManager = null;
	
	private Random rand = new Random(System.currentTimeMillis());
	
	private int taskId;

	@Override
	public void onEnable()
	{
		p = this;

		saveDefaultConfig();
		cfg = getConfig();

		mobManager = (com.forgenz.mobmanager.P) getServer().getPluginManager().getPlugin("MobManager");
		
		if (mobManager == null || getServer().getTicksPerAnimalSpawns() <= 0)
		{
			getLogger().warning("Missing MobManager");
			getServer().getPluginManager().disablePlugin(this);
			return;
		}
		
		getLogger().info("Hooked into MobManager");
		
		taskId = getServer().getScheduler().scheduleSyncRepeatingTask(this, this, getServer().getTicksPerAnimalSpawns(), getServer().getTicksPerAnimalSpawns());
		// And we are done :D
	}

	@Override
	public void onDisable()
	{
		p = null;
		cfg = null;
	}

	@Override
	public void run()
	{
		if (p == null || mobManager == null)
		{
			getServer().getScheduler().cancelTask(taskId);
			return;
		}
		
		long start = System.currentTimeMillis();
		
		int chunkRange = com.forgenz.mobmanager.P.cfg.getInt("SpawnChunkDistance");
		int numAttempts = cfg.getInt("SquidSpawnAttemptsPerPlayer", 10);
		
		getLogger().info("Starting Squid Spawn run");
		
		for (MMWorld mmWorld : mobManager.worlds.values())
		{
			// Ignore Nether & The End
			if (mmWorld.getWorld().getEnvironment() != Environment.NORMAL)
				continue;
			
			getLogger().info("Attempting to spawn Squid in " + mmWorld.getWorld().getName());
			
			// Makes sure the squid count is accurate
			mmWorld.updateNumMobs();
			
			int maxSquid = mmWorld.maxSquid();
			// Loop while we are under the limit don't let the task run too long
			while (maxSquid > mmWorld.getNumSquid() && System.currentTimeMillis() - start < 10)
			{
				// Iterates through each player and spawns some squid around them
				Iterator<Player> it = mmWorld.getWorld().getPlayers().iterator();
				while (it.hasNext() && maxSquid > mmWorld.getNumSquid() && System.currentTimeMillis() - start < 10)
				{
					int numSuccessful = 0;
					Player p = it.next();
					
					// Populate the list of chunks around the player
					ArrayList<MMCoord> chunksAround = new  ArrayList<MMCoord>(5);
					Spiral spiralGen = new Spiral(new MMCoord(p.getLocation().getChunk().getX(), p.getLocation().getChunk().getZ()), chunkRange);
					
					while (!spiralGen.isFinished())
						chunksAround.add(spiralGen.run());
					
					for (int i = 0; i < numAttempts; ++i)
					{
						double x;
						double z;
						double y;
						
						// Pick a chunk
						MMChunk mmChunk = null;
						// Make sure we get a chunk that exists
						for (int j = 0; j < 5; ++j)
						{
							mmChunk = mmWorld.getChunk(chunksAround.get(rand.nextInt(chunksAround.size())));
							if (mmChunk != null)
								break;
						}
						// If we didn't find a chunk after 5 attempts we skip this player
						if (mmChunk == null)
							continue;
						
						// Pick x/z coordinates
						x = rand.nextDouble() * 15 + (mmChunk.getChunk().getX() << 4);
						z = rand.nextDouble() * 15 + (mmChunk.getChunk().getZ() << 4);
						
						// Fetch the layers the player is in
						List<MMLayer> mmLayers = mmChunk.getLayersAt(p.getLocation().getBlockY());
						
						// Pick a layer for the spawn
						MMLayer layer = mmLayers.get(rand.nextInt(mmLayers.size()));
						
						// Pick y coordinate
						y = rand.nextDouble() * (layer.getMaxY() - layer.getMaxY()) + layer.getMinY();
						
						World craftWorld = mmWorld.getWorld();
						
						// Create a location for the spawn
						Location loc = new Location(craftWorld, x, y, z, rand.nextFloat() * 360, rand.nextFloat() * 360);
						
						// Check if the spawn location is safe
						if (loc.getBlock().getType() != Material.WATER && loc.getBlock().getType() != Material.STATIONARY_WATER)
						{
							getLogger().info(String.format("Failed to spawn squid at %d,%d,%d", loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()));
							continue;
						}
						
						// Spawn the squid
						craftWorld.spawnEntity(loc, EntityType.SQUID);
						++numSuccessful;
					}
					
					getLogger().info(String.format("Spawned %d squid near %s", numSuccessful, p.getName()));
				}
				
				// Just reduce maxSquid a bit for next iteration to make sure that the task will finish
				maxSquid *= 0.8;
			}
		}
	}
}
