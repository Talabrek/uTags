# uTags

A flexible prefix and suffix tag system for Minecraft Spigot servers.

## Features

*   Assign prefix and suffix tags to players.
*   Manage tags through commands and GUI menus (if applicable).
*   Customize player name colors independently from prefix tags.

## Important Change: Name Color Decoupling

Previously, selecting a prefix tag would also change the player's name color based on the tag's color. This behavior has been **removed**. Prefix tags now only display the tag itself, without affecting the player's name color.

To set your name color, please use the new `/tag namecolor` command detailed below.

## Commands

### Player Commands

*   **/tag list**: Displays available tags.
*   **/tag set <tag_name>**: Sets your active prefix tag.
*   **/tag clear**: Clears your active prefix tag.
*   **/tag namecolor <color_code|reset>**: Sets your display name color using standard Minecraft color codes (e.g., `&a`, `&b`, `&c`). Use `reset` to revert to the default color.
    *   *Example:* `/tag namecolor &a` (sets name color to light green)
    *   *Example:* `/tag namecolor reset` (resets name color)
*   **/tag request <tag_text>**: Requests a custom tag (requires admin approval).


### Name Color GUI

*   **/name**: Opens a GUI menu allowing you to select your desired name color from the pre-configured options.
*(Add other player commands if they exist)*

### Admin Commands

*(Add admin commands like /tag create, /tag delete, /tag approve, etc., if they exist)*

## Permissions

*   `utags.command.list`: Allows use of `/tag list`.
*   `utags.command.set`: Allows use of `/tag set`.
*   `utags.command.clear`: Allows use of `/tag clear`.
*   `utags.command.namecolor`: Allows use of `/tag namecolor`.
*   `utags.command.request`: Allows use of `/tag request`.
*   `utags.name.gui`: Allows use of `/name` and access to the name color GUI.
*   `utags.admin`: Grants access to all admin commands.

*(Add specific admin command permissions if they exist)*

## Configuration

*(Details about config.yml, including database credentials)*
### Name Colors
Database connection details (host, port, name, user, password) are also configured within `config.yml`.


The available colors for the `/changenamecolor` GUI are defined in `config.yml` under the `name-colors` section. You can customize the list of available colors there.

Example `config.yml` section:

```yaml
name-colors:
  - "&a" # Light Green
  - "&b" # Aqua
  - "&c" # Light Red
  - "&e" # Yellow
  # Add more color codes as needed
```


## Installation

1.  Place the `uTags.jar` file into your server's `plugins` folder.
2.  Restart or reload your server.


