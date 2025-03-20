package com.blockworlds.utags.services;

import com.blockworlds.utags.uTags;
import com.blockworlds.utags.utils.ErrorHandler;
import org.bukkit.configuration.file.FileConfiguration;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * Service class for secure configuration management in the uTags plugin.
 * Handles sensitive configuration values, environment variables, and configuration validation.
 */
public class ConfigurationManager {

    private final uTags plugin;
    private final ErrorHandler errorHandler;
    private final FileConfiguration config;
    
    // Cache for configuration values
    private final Map<String, Object> configCache = new HashMap<>();
    
    // Environment variable prefix for uTags configuration
    private static final String ENV_PREFIX = "UTAGS_";
    
    /**
     * Creates a new ConfigurationManager.
     *
     * @param plugin The uTags plugin instance
     * @param errorHandler The error handler to use
     */
    public ConfigurationManager(uTags plugin, ErrorHandler errorHandler) {
        this.plugin = plugin;
        this.errorHandler = errorHandler;
        this.config = plugin.getConfig();
        
        // Ensure config defaults are set
        plugin.saveDefaultConfig();
        
        // Validate configuration
        validateConfiguration();
    }
    
    /**
     * Validates the configuration and sets missing values to defaults.
     */
    private void validateConfiguration() {
        // Check critical configuration values
        validateDatabaseConfig();
        validateSecurityConfig();
        validateGeneralConfig();
        
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
        
        // Password is special case - could be from env var
        if (!config.contains("database.password") && !hasEnvironmentVariable("DATABASE_PASSWORD")) {
            config.set("database.password", "change_me");
            plugin.getLogger().warning("Database password set to default value. Please change it immediately!");
        }
        
        // Connection pool settings
        if (!config.contains("database.pool")) {
            config.createSection("database.pool");
        }
        
        checkConfig("database.pool.max-size", 10);
        checkConfig("database.pool.min-idle", 3);
        checkConfig("database.pool.idle-timeout", 30000);
        checkConfig("database.pool.connection-timeout", 10000);
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
        checkConfig("security.encryption-enabled", false);
    }
    
    /**
     * Validates general configuration settings.
     */
    private void validateGeneralConfig() {
        // Check if essential settings exist
        checkConfig("default-tag", "member");
        checkConfig("frame-material", "BLACK_STAINED_GLASS_PANE");
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
     * Gets a configuration value, with support for environment variables.
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
     * Gets the database password with special handling for security.
     *
     * @return The database password
     */
    public String getDatabasePassword() {
        // First check environment variable
        String password = System.getenv("UTAGS_DATABASE_PASSWORD");
        if (password != null) {
            return password;
        }
        
        // Then check config
        password = config.getString("database.password");
        
        // Check if the password is encrypted
        if (password != null && password.startsWith("ENC:")) {
            try {
                // Decrypt the password
                password = password.substring(4); // Remove "ENC:" prefix
                return decryptPassword(password);
            } catch (Exception e) {
                errorHandler.logError("Error decrypting database password", e);
                // Fall back to treating it as plaintext if decryption fails
                return password.substring(4);
            }
        }
        
        return password;
    }
    
    /**
     * Encrypts the database password and updates the configuration.
     *
     * @param plainPassword The password to encrypt
     * @return True if successful, false otherwise
     */
    public boolean encryptDatabasePassword(String plainPassword) {
        if (plainPassword == null || plainPassword.isEmpty()) {
            return false;
        }
        
        try {
            // Only encrypt if not already encrypted
            if (!plainPassword.startsWith("ENC:")) {
                String encryptedPassword = encryptPassword(plainPassword);
                config.set("database.password", "ENC:" + encryptedPassword);
                plugin.saveConfig();
                
                plugin.getLogger().info("Database password has been encrypted in the configuration");
                return true;
            }
            return true;
        } catch (Exception e) {
            errorHandler.logError("Failed to encrypt database password", e);
            return false;
        }
    }
    
    /**
     * Encrypts a password using the server-specific key.
     *
     * @param password The password to encrypt
     * @return The encrypted password
     */
    private String encryptPassword(String password) {
        try {
            // Get the server-specific encryption key
            String encryptionKey = getServerEncryptionKey();
            byte[] encKeyBytes = encryptionKey.getBytes(StandardCharsets.UTF_8);
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            encKeyBytes = sha.digest(encKeyBytes);
            
            // Create the cipher
            SecretKeySpec secretKey = new SecretKeySpec(encKeyBytes, "AES");
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            
            // Encrypt the password
            byte[] encryptedBytes = cipher.doFinal(password.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encryptedBytes);
        } catch (Exception e) {
            errorHandler.logError("Error encrypting password", e);
            return password; // Return plaintext if encryption fails
        }
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
     * Checks if an environment variable exists.
     *
     * @param name The variable name without the prefix
     * @return True if the environment variable exists
     */
    private boolean hasEnvironmentVariable(String name) {
        return System.getenv(ENV_PREFIX + name) != null;
    }
    
    /**
     * Decrypts an encrypted password.
     *
     * @param encryptedPassword The encrypted password
     * @return The decrypted password
     */
    private String decryptPassword(String encryptedPassword) {
        try {
            // Get the server-specific encryption key
            String encryptionKey = getServerEncryptionKey();
            byte[] encKeyBytes = encryptionKey.getBytes(StandardCharsets.UTF_8);
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            encKeyBytes = sha.digest(encKeyBytes);
            
            // Create the cipher
            SecretKeySpec secretKey = new SecretKeySpec(encKeyBytes, "AES");
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            
            // Decrypt the password
            byte[] decodedPassword = Base64.getDecoder().decode(encryptedPassword);
            byte[] decryptedBytes = cipher.doFinal(decodedPassword);
            return new String(decryptedBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            errorHandler.logError("Error decrypting password", e);
            return encryptedPassword; // Return as-is if decryption fails
        }
    }
    
    /**
     * Gets a server-specific encryption key.
     *
     * @return The encryption key
     */
    private String getServerEncryptionKey() {
        try {
            // Try to get or create a server-specific key
            File keyFile = new File(plugin.getDataFolder(), ".server_key");
            
            if (!keyFile.exists()) {
                // Generate a new key
                String key = generateRandomKey();
                keyFile.getParentFile().mkdirs(); // Ensure directory exists
                keyFile.createNewFile();
                Files.write(keyFile.toPath(), key.getBytes(StandardCharsets.UTF_8));
                
                // Set restrictive permissions on the key file
                keyFile.setReadable(false, false);
                keyFile.setReadable(true, true);
                keyFile.setWritable(false, false);
                keyFile.setWritable(true, true);
                
                return key;
            } else {
                // Read existing key
                byte[] keyBytes = Files.readAllBytes(keyFile.toPath());
                return new String(keyBytes, StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            errorHandler.logError("Error getting server encryption key", e);
            // Fall back to a default (less secure) method if the file access fails
            return "uTags_Default_Key_" + plugin.getDataFolder().getAbsolutePath().hashCode();
        }
    }
    
    /**
     * Generates a random encryption key.
     *
     * @return A random key
     */
    private String generateRandomKey() {
        byte[] key = new byte[32]; // 256 bits
        new java.security.SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
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
        invalidateCache();
        validateConfiguration();
    }
    
    /**
     * Creates a secure configuration file with documentation.
     */
    public void createSecureConfigDocumentation() {
        File securityGuide = new File(plugin.getDataFolder(), "security_guide.md");
        
        try {
            if (!securityGuide.exists()) {
                securityGuide.getParentFile().mkdirs();
                securityGuide.createNewFile();
                
                String documentation = 
                    "# uTags Security Guide\n\n" +
                    "## Configuration Security\n\n" +
                    "### Database Credentials\n\n" +
                    "There are several ways to securely configure database credentials:\n\n" +
                    "1. **Environment Variables**:\n" +
                    "   - Set the following environment variables on your server:\n" +
                    "     - `UTAGS_DATABASE_HOST`\n" +
                    "     - `UTAGS_DATABASE_PORT`\n" +
                    "     - `UTAGS_DATABASE_DATABASE`\n" +
                    "     - `UTAGS_DATABASE_USERNAME`\n" +
                    "     - `UTAGS_DATABASE_PASSWORD`\n\n" +
                    "2. **Password Encryption**:\n" +
                    "   - Enable password encryption in the config.yml file:\n" +
                    "   ```yaml\n" +
                    "   security:\n" +
                    "     encryption-enabled: true\n" +
                    "   ```\n" +
                    "   - Run the command `/tag admin encrypt` to encrypt the current password\n\n" +
                    "### General Security Settings\n\n" +
                    "```yaml\n" +
                    "security:\n" +
                    "  log-unauthorized-attempts: true   # Log unauthorized access attempts\n" +
                    "  max-failed-attempts: 5           # Number of failed attempts before triggering alerts\n" +
                    "  attempt-expiration-seconds: 300  # Window for counting failed attempts\n" +
                    "```\n\n" +
                    "## File Permissions\n\n" +
                    "Ensure that your config.yml file and the .server_key file have restrictive permissions:\n\n" +
                    "- Only the server user should be able to read these files\n" +
                    "- The files should not be accessible to other users on the system\n\n" +
                    "## Permission System\n\n" +
                    "uTags uses a comprehensive permission system. Ensure that you only grant the minimum permissions necessary:\n\n" +
                    "- `utags.admin` - Full administrative access (use with caution)\n" +
                    "- `utags.create` - Ability to create tags\n" +
                    "- `utags.delete` - Ability to delete tags\n" +
                    "- `utags.edit` - Ability to edit tags\n" +
                    "- `utags.manage.requests` - Ability to manage tag requests\n" +
                    "- `utags.view.requests` - Ability to view tag requests\n\n" +
                    "## Input Validation\n\n" +
                    "The plugin validates all user inputs to prevent security issues. Tag names and displays are checked for:\n\n" +
                    "- Maximum length\n" +
                    "- Allowed characters\n" +
                    "- Proper format (color codes, brackets, etc.)\n\n" +
                    "## Security Logging\n\n" +
                    "Security events are logged to help identify potential issues:\n\n" +
                    "- Unauthorized access attempts\n" +
                    "- Invalid inputs\n" +
                    "- Configuration changes\n\n" +
                    "Check your server logs regularly for [SECURITY] tagged messages.\n";
                
                Files.write(securityGuide.toPath(), documentation.getBytes(StandardCharsets.UTF_8));
                plugin.getLogger().log(Level.INFO, "Created security guide at: " + securityGuide.getAbsolutePath());
            }
        } catch (Exception e) {
            errorHandler.logError("Failed to create security guide", e);
        }
    }
}
