package com.blockworlds.utags.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * Manages configuration for the uTags plugin.
 * Handles loading, saving, and accessing configuration values.
 */
public class ConfigurationManager {
    private final JavaPlugin plugin;
    private FileConfiguration config;
    private FileConfiguration messages;
    private final Map<String, Object> configCache = new HashMap<>();
    
    // Environment variable prefix for uTags configuration
    private static final String ENV_PREFIX = "UTAGS_";
    
    /**
     * Creates a new ConfigurationManager.
     *
     * @param plugin The JavaPlugin instance
     */
    public ConfigurationManager(JavaPlugin plugin) {
        this.plugin = plugin;
        loadConfig();
        loadMessages();
    }
    
    /**
     * Loads the main configuration file.
     */
    private void loadConfig() {
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            plugin.saveDefaultConfig();
        }
        
        config = plugin.getConfig();
        validateConfig();
    }
    
    /**
     * Loads the messages file.
     */
    private void loadMessages() {
        File messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        
        messages = YamlConfiguration.loadConfiguration(messagesFile);
        
        // Load defaults from jar if they exist
        InputStream defaultMessagesStream = plugin.getResource("messages.yml");
        if (defaultMessagesStream != null) {
            YamlConfiguration defaultMessages = YamlConfiguration.loadConfiguration(
                new InputStreamReader(defaultMessagesStream, StandardCharsets.UTF_8));
            messages.setDefaults(defaultMessages);
        }
    }
    
    /**
     * Validates the configuration and sets missing values to defaults.
     */
    private void validateConfig() {
        // Check critical configuration values
        validateDatabaseConfig();
        validateSecurityConfig();
        
        // Save any changes
        plugin.saveConfig();
    }
    
    /**
     * Validates database configuration settings.
     */
    private void validateDatabaseConfig() {
        // Check if database section exists
        if (!config.contains("database")) {
            config.createSection("database");
        }
        
        // Required database parameters with defaults
        checkConfig("database.host", "localhost");
        checkConfig("database.port", 3306);
        checkConfig("database.database", "utags");
        checkConfig("database.username", "root");
        checkConfig("database.password", "change_me");
        
        // Connection pool settings
        if (!config.contains("database.pool")) {
            config.createSection("database.pool");
        }
        
        checkConfig("database.pool.max-size", 10);
        checkConfig("database.pool.min-idle", 3);
    }
    
    /**
     * Validates security configuration settings.
     */
    private void validateSecurityConfig() {
        // Create security section if it doesn't exist
        if (!config.contains("security")) {
            config.createSection("security");
        }
        
        // Security settings with defaults
        checkConfig("security.log-unauthorized-attempts", true);
        checkConfig("security.max-failed-attempts", 5);
        checkConfig("security.attempt-expiration-seconds", 300);
    }
    
    /**
     * Checks if a configuration value exists and sets it to a default if not.
     *
     * @param path The configuration path
     * @param defaultValue The default value
     */
    private void checkConfig(String path, Object defaultValue) {
        if (!config.contains(path)) {
            config.set(path, defaultValue);
            plugin.getLogger().info("Setting default value for " + path);
        }
    }
    
    /**
     * Gets a string configuration value, with support for environment variables.
     *
     * @param path The configuration path
     * @param defaultValue The default value if not found
     * @return The configuration value
     */
    public String getString(String path, String defaultValue) {
        // Check cache first
        if (configCache.containsKey(path)) {
            return (String) configCache.get(path);
        }
        
        // Check environment variable first
        String envVar = pathToEnvVar(path);
        String value = System.getenv(envVar);
        
        if (value == null) {
            // Fall back to config file
            value = config.getString(path, defaultValue);
        } else {
            plugin.getLogger().fine("Using environment variable " + envVar + " for " + path);
        }
        
        // Cache the value
        configCache.put(path, value);
        
        return value;
    }
    
    /**
     * Gets an integer configuration value.
     *
     * @param path The configuration path
     * @param defaultValue The default value if not found
     * @return The configuration value
     */
    public int getInt(String path, int defaultValue) {
        // Check cache first
        if (configCache.containsKey(path)) {
            return (int) configCache.get(path);
        }
        
        // Check environment variable first
        String envVar = pathToEnvVar(path);
        String envValue = System.getenv(envVar);
        
        int value;
        if (envValue != null) {
            try {
                value = Integer.parseInt(envValue);
                plugin.getLogger().fine("Using environment variable " + envVar + " for " + path);
            } catch (NumberFormatException e) {
                value = config.getInt(path, defaultValue);
                plugin.getLogger().warning("Invalid integer in environment variable " + envVar + ": " + envValue);
            }
        } else {
            // Fall back to config file
            value = config.getInt(path, defaultValue);
        }
        
        // Cache the value
        configCache.put(path, value);
        
        return value;
    }
    
    /**
     * Gets a boolean configuration value.
     *
     * @param path The configuration path
     * @param defaultValue The default value if not found
     * @return The configuration value
     */
    public boolean getBoolean(String path, boolean defaultValue) {
        // Check cache first
        if (configCache.containsKey(path)) {
            return (boolean) configCache.get(path);
        }
        
        // Check environment variable first
        String envVar = pathToEnvVar(path);
        String envValue = System.getenv(envVar);
        
        boolean value;
        if (envValue != null) {
            value = Boolean.parseBoolean(envValue);
            plugin.getLogger().fine("Using environment variable " + envVar + " for " + path);
        } else {
            // Fall back to config file
            value = config.getBoolean(path, defaultValue);
        }
        
        // Cache the value
        configCache.put(path, value);
        
        return value;
    }
    
    /**
     * Gets a message from the messages file.
     *
     * @param key The message key
     * @return The message, or the key if not found
     */
    public String getMessage(String key) {
        String message = messages.getString(key);
        if (message == null) {
            plugin.getLogger().warning("Missing message for key: " + key);
            return key;
        }
        return message;
    }
    
    /**
     * Converts a configuration path to an environment variable name.
     *
     * @param path The configuration path
     * @return The environment variable name
     */
    private String pathToEnvVar(String path) {
        return ENV_PREFIX + path.toUpperCase().replace(".", "_");
    }
    
    /**
     * Invalidates the configuration cache.
     */
    public void invalidateCache() {
        configCache.clear();
    }
    
    /**
     * Reloads the configuration from disk.
     */
    public void reloadConfig() {
        plugin.reloadConfig();
        config = plugin.getConfig();
        
        try {
            File messagesFile = new File(plugin.getDataFolder(), "messages.yml");
            messages = YamlConfiguration.loadConfiguration(messagesFile);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error loading messages.yml", e);
        }
        
        invalidateCache();
        validateConfig();
    }
    
    /**
     * Gets a section from the configuration.
     *
     * @param path The section path
     * @return The configuration section, or null if not found
     */
    public ConfigurationSection getSection(String path) {
        return config.getConfigurationSection(path);
    }
    
    /**
     * Gets the raw FileConfiguration object for direct access.
     * 
     * @return The FileConfiguration object
     */
    public FileConfiguration getConfig() {
        return config;
    }
    
    /**
     * Gets the raw messages FileConfiguration object for direct access.
     * 
     * @return The messages FileConfiguration object
     */
    public FileConfiguration getMessagesConfig() {
        return messages;
    }
    
    /**
     * Saves the messages file.
     */
    public void saveMessages() {
        try {
            File messagesFile = new File(plugin.getDataFolder(), "messages.yml");
            messages.save(messagesFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save messages.yml", e);
        }
    }
}
