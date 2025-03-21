package com.blockworlds.utags;

import com.blockworlds.utags.controller.impl.AdminCommandController;
import com.blockworlds.utags.controller.impl.HelpCommandController;
import com.blockworlds.utags.controller.impl.MenuClickController;
import com.blockworlds.utags.controller.impl.PlayerLoginController;
import com.blockworlds.utags.controller.impl.TagCommandController;
import com.blockworlds.utags.controller.impl.TagPreviewController;
import com.blockworlds.utags.di.ApplicationModule;
import com.blockworlds.utags.di.ServiceContainer;
import com.blockworlds.utags.repository.impl.DatabaseConnector;
import com.blockworlds.utags.service.TagService;
import com.blockworlds.utags.util.ErrorHandler;
import com.blockworlds.utags.view.MenuBuilder;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;

import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Main plugin class for uTags.
 * Manages plugin lifecycle and component registration.
 */
public class UTags extends JavaPlugin {
    private LuckPerms luckPerms;
    private ApplicationModule module;
    private ServiceContainer container;
    private final Map<UUID, String> previewTags = new HashMap<>();
    
    @Override
    public void onEnable() {
        try {
            // Initialize core dependencies
            if (!setupLuckPerms()) {
                getLogger().severe("LuckPerms not found! Disabling uTags...");
                getServer().getPluginManager().disablePlugin(this);
                return;
            }
            
            if (!loadConfig()) {
                getLogger().severe("Failed to load configuration! Disabling uTags...");
                getServer().getPluginManager().disablePlugin(this);
                return;
            }
            
            // Initialize dependency injection
            initializeDI();
            
            // Register commands and listeners
            registerCommandsAndListeners();
            
            // Schedule periodic tasks
            schedulePeriodicTasks();
            
            getLogger().info("uTags has been enabled successfully!");
        } catch (Exception e) {
            getLogger().severe("Critical error during plugin initialization: " + e.getMessage());
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (container != null) {
            // Close database connections
            try {
                DatabaseConnector dbConnector = container.get(DatabaseConnector.class);
                if (dbConnector != null) {
                    dbConnector.close();
                }
            } catch (Exception e) {
                getLogger().warning("Error closing database connections: " + e.getMessage());
            }
            
            // Clear container
            container.clear();
        }
        
        getLogger().info("uTags has been disabled!");
    }
    
    /**
     * Initializes the dependency injection container and registers all components.
     */
    private void initializeDI() {
        try {
            module = new ApplicationModule(this, getLogger(), luckPerms);
            container = module.getContainer();
            getLogger().info("Dependency injection initialized successfully");
        } catch (Exception e) {
            getLogger().severe("Error initializing dependency injection: " + e.getMessage());
            throw new RuntimeException("Failed to initialize dependency injection", e);
        }
    }
    
    /**
     * Sets up the LuckPerms dependency.
     * 
     * @return true if successful, false otherwise
     */
    private boolean setupLuckPerms() {
        try {
            if (getServer().getPluginManager().getPlugin("LuckPerms") != null) {
                luckPerms = LuckPermsProvider.get();
                return true;
            }
            return false;
        } catch (Exception e) {
            getLogger().severe("Error setting up LuckPerms: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Loads the plugin configuration.
     * 
     * @return true if successful, false otherwise
     */
    private boolean loadConfig() {
        try {
            File configFile = new File(getDataFolder(), "config.yml");
            if (!configFile.exists()) {
                getLogger().info("Creating default configuration file...");
                saveDefaultConfig();
            }
            
            reloadConfig();
            return true;
        } catch (Exception e) {
            getLogger().severe("Error loading configuration: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Registers commands and event listeners from the container.
     */
    private void registerCommandsAndListeners() {
        try {
            // Register command with multiple handlers
            registerCommand("tag", 
                container.get(TagCommandController.class),
                container.get(AdminCommandController.class),
                container.get(HelpCommandController.class)
            );
            
            // Register listeners
            registerListeners(
                container.get(MenuClickController.class),
                container.get(PlayerLoginController.class),
                container.get(TagPreviewController.class)
            );
            
            getLogger().info("Commands and listeners registered successfully");
        } catch (Exception e) {
            getLogger().severe("Error registering commands and listeners: " + e.getMessage());
            throw new RuntimeException("Failed to register commands and listeners", e);
        }
    }
    
    /**
     * Registers a command with the specified executor and completer.
     *
     * @param name The command name
     * @param executor The command executor
     */
    private void registerCommand(String name, Object... handlers) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().warning("Command '" + name + "' not found in plugin.yml");
            return;
        }
        
        // Find the main command executor and tab completer
        CommandExecutor executor = null;
        TabCompleter completer = null;
        
        for (Object handler : handlers) {
            if (handler instanceof CommandExecutor && executor == null) {
                executor = (CommandExecutor) handler;
            }
            if (handler instanceof TabCompleter && completer == null) {
                completer = (TabCompleter) handler;
            }
        }
        
        if (executor != null) {
            command.setExecutor(executor);
        }
        if (completer != null) {
            command.setTabCompleter(completer);
        }
    }
    
    /**
     * Registers multiple listeners with the plugin.
     *
     * @param listeners The listeners to register
     */
    private void registerListeners(Listener... listeners) {
        for (Listener listener : listeners) {
            getServer().getPluginManager().registerEvents(listener, this);
        }
    }
    
    /**
     * Schedules periodic tasks.
     */
    private void schedulePeriodicTasks() {
        // Schedule tag request checker
        long checkInterval = 5 * 60 * 20; // 5 minutes in ticks
        getServer().getScheduler().runTaskTimer(this, () -> {
            try {
                TagService tagService = container.get(TagService.class);
                tagService.notifyStaffOfPendingRequests();
            } catch (Exception e) {
                container.get(ErrorHandler.class).logError("Error checking tag requests", e);
            }
        }, checkInterval, checkInterval);
    }
    
    /**
     * Gets a reference to the tag preview map.
     * Used by controllers to access and manage tag previews.
     *
     * @return The tag preview map
     */
    public Map<UUID, String> getPreviewTags() {
        return previewTags;
    }
    
    /**
     * Gets a service from the container.
     * Public accessor for other plugins that might want to integrate.
     *
     * @param <T> The service type
     * @param type The service class
     * @return The service instance
     */
    public <T> T getService(Class<T> type) {
        return container.get(type);
    }
    
    /**
     * Gets the menu builder.
     * Convenience method for backward compatibility.
     *
     * @return The menu builder
     */
    public MenuBuilder getMenuBuilder() {
        return container.get(MenuBuilder.class);
    }
}
