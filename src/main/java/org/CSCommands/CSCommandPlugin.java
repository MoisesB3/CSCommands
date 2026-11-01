package org.CSCommands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CSCommandPlugin extends JavaPlugin implements Listener, TabExecutor {

    // Class-level fields (moved outside methods)
    private final Map<UUID, DuelData> playerDataStorage = new ConcurrentHashMap<>();
    private String menuCommand = "mduel";  // The command that will be executed via sudo
    private String finalDuelCommand = "1v1";

    @Override
    public void onEnable() {
        // Don't save default config since we don't have one
        // saveDefaultConfig();
        loadConfiguration();

        // LOUD startup logging
        System.out.println("==========================================");
        System.out.println("CSCommands Plugin Starting Up!");
        System.out.println("Version: " + getDescription().getVersion());
        System.out.println("Author: " + getDescription().getAuthors());
        System.out.println("==========================================");

        getServer().getPluginManager().registerEvents(this, this);
        System.out.println("✓ Event listener registered successfully");

        if (getCommand("pduel") != null) {
            getCommand("pduel").setExecutor(this);
            System.out.println("✓ pduel command registered");
        } else {
            System.out.println("✗ pduel command NOT found in plugin.yml!");
        }

        if (getCommand("cscommand") != null) {
            getCommand("cscommand").setExecutor(this);
            System.out.println("✓ cscommand registered");
        } else {
            System.out.println("✗ cscommand NOT found in plugin.yml!");
        }

        // Add debug command
        if (getCommand("csdebug") != null) {
            getCommand("csdebug").setExecutor(this);
            System.out.println("✓ csdebug command registered");
        }

        getLogger().info("CSCommands Plugin v" + getDescription().getVersion() + " enabled!");
        getLogger().info("Intercepting /duel commands and executing sudo player " + menuCommand);
        System.out.println("CSCommands Plugin fully loaded and ready!");
        System.out.println("==========================================");
    }

    @Override
    public void onDisable() {
        playerDataStorage.clear();
        getLogger().info("CSCommands Plugin disabled!");
    }

    private void loadConfiguration() {
        reloadConfig();
        menuCommand = getConfig().getString("commands.menu-command", "mduel");
        finalDuelCommand = getConfig().getString("commands.final-duel-command", "1v1");

        getConfig().addDefault("commands.menu-command", "mduel");
        getConfig().addDefault("commands.final-duel-command", "1v1");
        getConfig().addDefault("messages.enabled", true);
        getConfig().addDefault("messages.duel-intercepted", "§aDuel request sent to %target%! Select your kit.");
        getConfig().addDefault("messages.usage", "§cUsage: /duel <playername>");
        getConfig().addDefault("messages.no-data", "§cNo duel data found. Please use /duel <playername> first.");
        getConfig().addDefault("settings.duel-expiry-time", 300000);
        getConfig().options().copyDefaults(true);
        saveConfig();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage();
        String[] parts = message.split(" ");

        if (parts.length >= 1 && parts[0].equalsIgnoreCase("/duel")) {
            Player player = event.getPlayer();

            // Bypass: Allow /duel accept to go through to the original duel plugin
            if (parts.length >= 2 && parts[1].equalsIgnoreCase("accept")) {
                getLogger().info("Allowing /duel accept to pass through for " + player.getName());
                return; // Let the original duel plugin handle this
            }

            if (!player.hasPermission("cscommands.use")) {
                player.sendMessage("§cYou don't have permission to use this command!");
                event.setCancelled(true);
                return;
            }

            if (parts.length >= 2) {
                String targetPlayerName = parts[1];

                Player targetPlayer = Bukkit.getPlayer(targetPlayerName);
                if (targetPlayer == null) {
                    player.sendMessage("§cPlayer '" + targetPlayerName + "' is not online!");
                    event.setCancelled(true);
                    return;
                }

                if (targetPlayer.equals(player)) {
                    player.sendMessage("§cYou cannot duel yourself!");
                    event.setCancelled(true);
                    return;
                }

                DuelData duelData = new DuelData(targetPlayerName, System.currentTimeMillis());
                playerDataStorage.put(player.getUniqueId(), duelData);

                event.setCancelled(true);

                // Send message only if enabled in config
                if (getConfig().getBoolean("messages.enabled", true)) {
                    String message_intercepted = getConfig().getString("messages.duel-intercepted", "§aDuel request sent to %target%! Select your kit.")
                            .replace("%target%", targetPlayerName);
                    player.sendMessage(message_intercepted);
                }

                Bukkit.getScheduler().runTask(this, () -> {
                    // Use sudo to execute the menu command as the player
                    String sudoCommand = "sudo " + player.getName() + " " + menuCommand;
                    getLogger().info("Executing: " + sudoCommand);
                    boolean result = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), sudoCommand);
                    getLogger().info("Sudo command result: " + result);

                    // Fallback: try direct execution if sudo fails
                    if (!result) {
                        getLogger().info("Sudo failed, trying direct execution");
                        Bukkit.dispatchCommand(player, menuCommand);
                    }
                });

                getLogger().info("Intercepted /duel from " + player.getName() + " targeting " + targetPlayerName);

            } else {
                event.setCancelled(true);
                if (getConfig().getBoolean("messages.enabled", true)) {
                    String usageMsg = getConfig().getString("messages.usage", "§cUsage: /duel <playername>");
                    player.sendMessage(usageMsg);
                }
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        playerDataStorage.remove(event.getPlayer().getUniqueId());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        switch (command.getName().toLowerCase()) {
            case "pduel":
                return handlePduelCommand(sender, args);
            case "cscommand":
                return handleCSCommand(sender, args);
            case "csdebug":
                return handleDebugCommand(sender, args);
            default:
                return false;
        }
    }

    private boolean handlePduelCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cThis command can only be used by players!");
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("cscommands.pduel")) {
            player.sendMessage("§cYou don't have permission to use this command!");
            return true;
        }

        DuelData duelData = playerDataStorage.get(player.getUniqueId());
        if (duelData == null) {
            if (getConfig().getBoolean("messages.enabled", true)) {
                String noDataMsg = getConfig().getString("messages.no-data", "§cNo duel data found. Please use /duel <playername> first.");
                player.sendMessage(noDataMsg);
            }
            return true;
        }

        // Use configurable expiry time
        long expiryTime = getConfig().getLong("settings.duel-expiry-time", 300000);
        if (System.currentTimeMillis() - duelData.timestamp > expiryTime) {
            playerDataStorage.remove(player.getUniqueId());
            if (getConfig().getBoolean("messages.enabled", true)) {
                player.sendMessage("§cDuel request has expired. Please send a new one.");
            }
            return true;
        }

        if (args.length >= 3) {
            String playerNameArg = args[0];
            String gamemode = args[1];
            String kit = args[2];

            String targetName = playerNameArg.equals("%CSCDATA_PlayerName%") ?
                    duelData.targetPlayerName : playerNameArg;

            String finalCommand = String.format("%s %s %s %s",
                    finalDuelCommand, targetName, gamemode, kit);

            getLogger().info("Executing final duel: /" + finalCommand + " for " + player.getName());

            Bukkit.getScheduler().runTask(this, () -> {
                Bukkit.dispatchCommand(player, finalCommand);
            });

            playerDataStorage.remove(player.getUniqueId());

        } else {
            if (getConfig().getBoolean("messages.enabled", true)) {
                player.sendMessage("§cInvalid pduel format! Expected: /pduel %CSCDATA_PlayerName% <gamemode> <kit>");
            }
            return false;
        }

        return true;
    }

    private boolean handleCSCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("cscommands.admin")) {
            sender.sendMessage("§cYou don't have permission to use this command!");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage("§6CSCommands Plugin v" + getDescription().getVersion());
            sender.sendMessage("§e/cscommand reload §7- Reload configuration");
            sender.sendMessage("§e/cscommand info §7- Show plugin information");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload":
                loadConfiguration();
                sender.sendMessage("§aConfiguration reloaded!");
                return true;
            case "info":
                sender.sendMessage("§6=== CSCommands Plugin Info ===");
                sender.sendMessage("§eVersion: §7" + getDescription().getVersion());
                sender.sendMessage("§eActive duel requests: §7" + playerDataStorage.size());
                sender.sendMessage("§eMenu command: §7/" + menuCommand);
                sender.sendMessage("§eFinal duel command: §7/" + finalDuelCommand);
                return true;
            default:
                sender.sendMessage("§cUnknown subcommand. Use /cscommand for help.");
                return true;
        }
    }

    private boolean handleDebugCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("cscommands.admin")) {
            sender.sendMessage("§cNo permission!");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage("§6=== CSCommands Debug Menu ===");
            sender.sendMessage("§e/csdebug info §7- Show plugin info");
            sender.sendMessage("§e/csdebug test <player> §7- Test menu execution");
            sender.sendMessage("§e/csdebug sudo <player> §7- Test sudo command");
            sender.sendMessage("§e/csdebug storage §7- Show stored data");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "info":
                sender.sendMessage("§6=== Plugin Debug Info ===");
                sender.sendMessage("§ePlugin Name: §7" + getDescription().getName());
                sender.sendMessage("§eVersion: §7" + getDescription().getVersion());
                sender.sendMessage("§eMain Class: §7" + getDescription().getMain());
                sender.sendMessage("§eEnabled: §7" + isEnabled());
                sender.sendMessage("§eEvent Priority: §7HIGHEST");
                sender.sendMessage("§eMenu Command: §7" + menuCommand);
                sender.sendMessage("§eStored Data Count: §7" + playerDataStorage.size());
                System.out.println("DEBUG INFO requested by " + sender.getName());
                return true;

            case "test":
                if (args.length < 2) {
                    sender.sendMessage("§cUsage: /csdebug test <player>");
                    return true;
                }
                String targetName = args[1];
                Player target = Bukkit.getPlayer(targetName);
                if (target == null) {
                    sender.sendMessage("§cPlayer not found!");
                    return true;
                }

                sender.sendMessage("§6Testing menu execution for: §e" + target.getName());
                System.out.println("=== MANUAL DEBUG TEST ===");
                System.out.println("Tester: " + sender.getName());
                System.out.println("Target: " + target.getName());
                System.out.println("Attempting mduel execution...");

                Bukkit.getScheduler().runTask(this, () -> {
                    System.out.println("→ Direct mduel test:");
                    boolean directResult = Bukkit.dispatchCommand(target, "mduel");
                    System.out.println("→ Direct result: " + directResult);
                    sender.sendMessage("§eDirect mduel result: §7" + directResult);

                    System.out.println("→ Sudo mduel test:");
                    boolean sudoResult = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "sudo " + target.getName() + " mduel");
                    System.out.println("→ Sudo result: " + sudoResult);
                    sender.sendMessage("§eSudo mduel result: §7" + sudoResult);
                });
                return true;

            case "sudo":
                if (args.length < 2) {
                    sender.sendMessage("§cUsage: /csdebug sudo <player>");
                    return true;
                }
                String sudoTarget = args[1];
                Player sudoPlayer = Bukkit.getPlayer(sudoTarget);
                if (sudoPlayer == null) {
                    sender.sendMessage("§cPlayer not found!");
                    return true;
                }

                System.out.println("=== SUDO DEBUG TEST ===");
                System.out.println("Testing sudo command: sudo " + sudoPlayer.getName() + " mduel");
                boolean result = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "sudo " + sudoPlayer.getName() + " mduel");
                System.out.println("Sudo test result: " + result);
                sender.sendMessage("§eSudo test result: §7" + result);
                return true;

            case "storage":
                sender.sendMessage("§6=== Stored Duel Data ===");
                if (playerDataStorage.isEmpty()) {
                    sender.sendMessage("§7No stored data");
                } else {
                    for (Map.Entry<UUID, DuelData> entry : playerDataStorage.entrySet()) {
                        Player p = Bukkit.getPlayer(entry.getKey());
                        String playerName = p != null ? p.getName() : "Unknown";
                        sender.sendMessage("§e" + playerName + " §7-> §e" + entry.getValue().targetPlayerName);
                    }
                }
                return true;

            default:
                sender.sendMessage("§cUnknown debug command!");
                return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (command.getName().equals("cscommand") && sender.hasPermission("cscommands.admin")) {
            if (args.length == 1) {
                completions.add("reload");
                completions.add("info");
            }
        }

        return completions;
    }

    public String getStoredPlayerName(Player player) {
        DuelData data = playerDataStorage.get(player.getUniqueId());
        return data != null ? data.targetPlayerName : null;
    }

    public void setStoredPlayerName(Player player, String targetName) {
        playerDataStorage.put(player.getUniqueId(), new DuelData(targetName, System.currentTimeMillis()));
    }

    public void clearStoredData(Player player) {
        playerDataStorage.remove(player.getUniqueId());
    }

    private static class DuelData {
        final String targetPlayerName;
        final long timestamp;

        DuelData(String targetPlayerName, long timestamp) {
            this.targetPlayerName = targetPlayerName;
            this.timestamp = timestamp;
        }
    }
}