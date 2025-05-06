package hu.scry.command;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public final class program extends JavaPlugin {

    private boolean gameActive = false;
    private int swapTime;
    private int taskId;
    private final List<Player> participants = new ArrayList<>();
    private final Random random = new Random();
    private int minSwapTime = 30;
    private int maxSwapTime = 120;
    private boolean announceSwaps = true;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfig();
        getLogger().info("DeathSwap 1.18.2 plugin enabled!");
    }

    @Override
    public void onDisable() {
        if (gameActive) {
            endGame();
        }
        getLogger().info("DeathSwap plugin disabled!");
    }

    private void loadConfig() {
        minSwapTime = getConfig().getInt("min-swap-time", 30);
        maxSwapTime = getConfig().getInt("max-swap-time", 120);
        announceSwaps = getConfig().getBoolean("announce-swaps", true);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!cmd.getName().equalsIgnoreCase("deathswap")) {
            return false;
        }

        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "start":
                handleStart(sender);
                break;
            case "stop":
                handleStop(sender);
                break;
            case "join":
                handleJoin(sender);
                break;
            case "leave":
                handleLeave(sender);
                break;
            case "settime":
                handleSetTime(sender, args);
                break;
            case "reload":
                handleReload(sender);
                break;
            default:
                sendUsage(sender);
                break;
        }
        return true;
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "DeathSwap Commands:");
        sender.sendMessage(ChatColor.YELLOW + "/deathswap start - Start the game");
        sender.sendMessage(ChatColor.YELLOW + "/deathswap stop - Stop the game");
        sender.sendMessage(ChatColor.YELLOW + "/deathswap join - Join the game");
        sender.sendMessage(ChatColor.YELLOW + "/deathswap leave - Leave the game");
        sender.sendMessage(ChatColor.YELLOW + "/deathswap settime <min> <max> - Set swap time range");
        sender.sendMessage(ChatColor.YELLOW + "/deathswap reload - Reload config");
    }

    private void handleStart(CommandSender sender) {
        if (gameActive) {
            sender.sendMessage(ChatColor.RED + "Game is already running!");
            return;
        }

        if (Bukkit.getOnlinePlayers().size() < 2) {
            sender.sendMessage(ChatColor.RED + "Need at least 2 players to start!");
            return;
        }

        startGame();
        sender.sendMessage(ChatColor.GREEN + "DeathSwap game started!");
    }

    private void handleStop(CommandSender sender) {
        if (!gameActive) {
            sender.sendMessage(ChatColor.RED + "No game is running!");
            return;
        }

        endGame();
        sender.sendMessage(ChatColor.GREEN + "DeathSwap game stopped!");
    }

    private void handleJoin(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can join the game!");
            return;
        }

        Player player = (Player) sender;

        if (!gameActive) {
            player.sendMessage(ChatColor.RED + "No game is running! Start one with /deathswap start");
            return;
        }

        if (participants.contains(player)) {
            player.sendMessage(ChatColor.RED + "You're already in the game!");
            return;
        }

        participants.add(player);
        player.sendMessage(ChatColor.GREEN + "You joined DeathSwap!");
        broadcastMessage(ChatColor.GOLD + player.getName() + " joined the game!");
    }

    private void handleLeave(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can leave the game!");
            return;
        }

        Player player = (Player) sender;

        if (!gameActive) {
            player.sendMessage(ChatColor.RED + "No game is running!");
            return;
        }

        if (!participants.remove(player)) {
            player.sendMessage(ChatColor.RED + "You weren't in the game!");
            return;
        }

        player.sendMessage(ChatColor.GREEN + "You left DeathSwap!");
        broadcastMessage(ChatColor.GOLD + player.getName() + " left the game!");

        if (participants.size() < 2) {
            endGame();
            broadcastMessage(ChatColor.RED + "Not enough players remaining. Game stopped.");
        }
    }

    private void handleSetTime(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /deathswap settime <min> <max>");
            return;
        }

        try {
            int newMin = Integer.parseInt(args[1]);
            int newMax = Integer.parseInt(args[2]);

            if (newMin <= 0 || newMax <= 0 || newMin > newMax) {
                sender.sendMessage(ChatColor.RED + "Invalid time values! Min must be <= Max and both > 0");
                return;
            }

            minSwapTime = newMin;
            maxSwapTime = newMax;
            getConfig().set("min-swap-time", minSwapTime);
            getConfig().set("max-swap-time", maxSwapTime);
            saveConfig();

            sender.sendMessage(ChatColor.GREEN + "Swap timer set to " + minSwapTime + "-" + maxSwapTime + " seconds");
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Invalid numbers! Please enter valid integers.");
        }
    }

    private void handleReload(CommandSender sender) {
        reloadConfig();
        loadConfig();
        sender.sendMessage(ChatColor.GREEN + "DeathSwap config reloaded!");
    }

    private void startGame() {
        gameActive = true;
        participants.clear();
        participants.addAll(Bukkit.getOnlinePlayers());
        
        broadcastMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "DeathSwap started!");
        broadcastMessage(ChatColor.YELLOW + "Players will be periodically swapped!");
        broadcastMessage(ChatColor.YELLOW + "Swap interval: " + minSwapTime + "-" + maxSwapTime + " seconds");

        scheduleNextSwap();
    }

    private void endGame() {
        gameActive = false;
        Bukkit.getScheduler().cancelTask(taskId);
        broadcastMessage(ChatColor.RED + "" + ChatColor.BOLD + "DeathSwap ended!");
        participants.clear();
    }

    private void scheduleNextSwap() {
        swapTime = random.nextInt(maxSwapTime - minSwapTime + 1) + minSwapTime;
        
        taskId = new BukkitRunnable() {
            @Override
            public void run() {
                if (!gameActive) {
                    cancel();
                    return;
                }
                
                if (swapTime <= 0) {
                    executeSwap();
                    scheduleNextSwap();
                } else {
                    if (announceSwaps && (swapTime % 30 == 0 || swapTime <= 10)) {
                        broadcastMessage(ChatColor.GOLD + "Swapping in " + swapTime + " seconds!");
                    }
                    swapTime--;
                }
            }
        }.runTaskTimer(this, 0, 20).getTaskId();
    }

    private void executeSwap() {
        if (participants.size() < 2) {
            endGame();
            return;
        }
        
        // Create a shuffled copy of participants
        List<Player> shuffled = new ArrayList<>(participants);
        Collections.shuffle(shuffled);
        
        // Store all locations first
        List<Location> locations = new ArrayList<>();
        for (Player player : shuffled) {
            locations.add(player.getLocation().clone()); // Clone to prevent reference issues
        }
        
        // Perform the swaps
        for (int i = 0; i < shuffled.size(); i++) {
            Player current = shuffled.get(i);
            Player next = shuffled.get((i + 1) % shuffled.size());
            
            Location nextLoc = locations.get((i + 1) % shuffled.size());
            
            // Store current player's data before teleport
            World currentWorld = current.getWorld();
            float fallDistance = current.getFallDistance();
            
            // Teleport the player
            current.teleport(nextLoc);
            
            // Restore fall distance to prevent fall damage from teleport
            current.setFallDistance(fallDistance);
            
            // Send swap message
            if (announceSwaps) {
                current.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "SWAPPED with " + next.getName() + "!");
            }
        }
        
        broadcastMessage(ChatColor.DARK_RED + "Players have been swapped!");
    }

    private void broadcastMessage(String message) {
        for (Player player : participants) {
            player.sendMessage(message);
        }
    }
}
