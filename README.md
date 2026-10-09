# uTags

Chat tags and name colors for Paper servers. Players pick a prefix or suffix tag from a menu, and choose the color
of their name. Tags are shown through LuckPerms prefixes and suffixes, so they work with any chat plugin that
reads them. Free and open source (MIT).

Built for Paper 1.21.4 and newer. Requires [LuckPerms](https://luckperms.net/). Optional: Floodgate (Bedrock forms),
PlaceholderAPI (placeholder unlock rules).

## Features

- Prefix and suffix tags, picked from a paged menu (`/tag`) or set with a command.
- Permission-gated tags: a player sees and uses a tag only if they have `utags.tag.<name>` (admins can mark a tag
  public so everyone can see it).
- Custom tags: players request their own tag, staff accept or deny it from a menu. The number of custom tags a
  player may have is set by permission (`utags.custom1`, `utags.custom2`, ...).
- Colorable tags: players can recolor the brackets and the text of tags marked colorable.
- Name colors, independent from tags, chosen in a menu (`/name`) or with `/tag namecolor`.
- Admin menu and commands to create, edit and delete tags.
- Bedrock forms: with Floodgate installed, Bedrock players get native forms instead of chest menus.
- Earned unlocks: grant a tag when a player reaches a playtime, completes an advancement, gains a permission or
  passes a PlaceholderAPI check.
- Network sync: servers sharing one MySQL database see each other's tag, selection and color changes within
  seconds.
- Grants ledger and API: store plugins and other plugins grant and revoke tags through `/tag admin grant` or the
  `UTagsApi` service, with replay-safe operation ids.
- Stores its data in SQLite (no setup) or MySQL / MariaDB (to share tags between servers).

## Installation

1. Install LuckPerms.
2. Put `uTags-<version>.jar` into the server's `plugins` folder and start the server.
3. Give your players `utags.tag.<name>` for the tags they may use, and create tags with `/tag admin gui`.

Updating from an older uTags that used MySQL: just replace the jar. Your `config.yml` already has a
`database.host`, so uTags keeps using MySQL and migrates the tables itself.

## Commands

All commands are player-only: the console gets "This command can only be used by players." Arguments in `<>` are
required, `[]` are optional.

| Command | What it does |
| --- | --- |
| `/tag` | Opens the tag menu. |
| `/tag set <tag>` | Sets your prefix tag (prefix tags only; set suffix tags from the menu). |
| `/tag namecolor <&code\|reset>` | Sets (or resets) your name color, for example `/tag namecolor &a`. |
| `/tag request <tag>` | Requests a custom tag (needs a free custom tag slot). |
| `/tag help [page]` | Lists the commands. |
| `/name` | Opens the name color menu. |
| `/tag admin gui` | Opens the admin menu. |
| `/tag admin create <name> <display> <prefix\|suffix\|both> <weight> <true\|false>` | Creates a tag from the item in your hand; the last argument is whether the tag is public. |
| `/tag admin edit <tag> <attribute> <value>` | Edits a tag. Attributes: `name`, `display`, `type`, `public`, `color`, `colorable`, `weight`. |
| `/tag admin delete <name>` | Deletes a tag. |
| `/tag admin requests` | Opens the menu of pending custom tag requests. |
| `/tag admin purge <tags\|requests> confirm` | Deletes all tags or all requests (without `confirm` it only asks you to confirm). |
| `/tag admin debug` | Toggles debug logging. |
| `/tag admin grant <player> <tag> <source> <opId>` | Grants a tag through the ledger (console and store plugins). The same `opId` again is a replay and changes nothing; the answer says GRANTED, ALREADY_OWNED or UNKNOWN_TAG and `replayed`. |
| `/tag admin revoke <player> <tag> <source> <opId>` | Revokes the grants of that source. Answers REVOKED_GONE, REVOKED_STILL_OWNED (another source or the permission remains) or NOT_HELD. |
| `/tag admin grants <player>` | Lists every ledger grant of a player, with source, op id and whether it is revoked. |
| `/tag admin import <file>` | Creates or updates tags from `plugins/UTags/import/<file>.yml` and reports created, updated and skipped. |

## Importing tags

`/tag admin import heritage` reads `plugins/UTags/import/heritage.yml`. Running it again changes nothing for tags
that already match. Per tag: `name`, `display` (required), `type` (`prefix`, `suffix` or `both`, default `both`),
`public` (default false), `colorable` (default true), `weight` (default 0), `material` (default `NAME_TAG`).
The Heritage year tags ("Islander since 2012" to "2026") look like this:

```yaml
tags:
  - { name: islander-2012, display: "&6[Islander since 2012]", type: suffix, weight: 2012, material: NAME_TAG }
  - { name: islander-2013, display: "&6[Islander since 2013]", type: suffix, weight: 2013, material: NAME_TAG }
  # ... one entry per year up to 2026
  - { name: islander-2026, display: "&6[Islander since 2026]", type: suffix, weight: 2026, material: NAME_TAG }
```

## Grants and the API

A player owns a tag when the ledger holds an unrevoked grant for it or they have `utags.tag.<name>`; existing
permissions keep working and are not converted. Other plugins use the `UTagsApi` service (ServicesManager) to grant and
revoke by source and operation id, so a retried call never grants twice.

## Permissions

| Permission | Default | Allows |
| --- | --- | --- |
| `utags.tag` | everyone | Using `/tag`. |
| `utags.tag.<name>` | none | Using the tag called `<name>`. |
| `utags.custom<N>` | none | Custom tag slot N (`utags.custom1`, `utags.custom2`, ...). |
| `utags.color.change` | none | Changing the colors of colorable tags. |
| `utags.name.gui` | everyone | Using `/name`. |
| `utags.command.namecolor` | none | Using `/tag namecolor`. |
| `utags.namecolor.<key>` | none | One name color from `name-colors` in the config (`utags.namecolor.*` for all). |
| `utags.staff` | none | Accepting or denying custom tag requests in the requests menu; sees the number of pending requests on join. |
| `utags.admin` | none | All `/tag admin` commands (add `utags.staff` to act on requests). |

## Configuration

`plugins/UTags/config.yml`:

```yaml
storage:
  type: sqlite          # sqlite (default) or mysql
  sqlite:
    file: "utags.db"    # inside plugins/UTags

# Only read when storage.type is mysql
#database:
#  host: "localhost"
#  port: 3306
#  database: "utags"
#  username: "user"
#  password: "password"
```

- **SQLite** needs no setup: the data lives in `plugins/UTags/utags.db`. Use one SQLite file per server.
- **MySQL / MariaDB** is for several servers sharing one set of tags: set `storage.type: mysql` and fill in the
  `database` section.
- Without `storage.type`, uTags uses MySQL when the config has a `database.host` and SQLite otherwise.
- `name-colors`: the colors offered in `/name`, each needing `utags.namecolor.<key>`.
- `messages`: texts shown to players (Minecraft `&` color codes).
- `accept-command` / `deny-command`: optional console commands run when staff accept or deny a request, for example
  `mail send %player% Your custom tag request has been accepted!`. `%player%` is the requesting player. Empty means
  no command.
- `gui`: the item lore of custom tag slots.
- `sync.enabled` / `sync.poll-seconds` (MySQL only, default on, every 3 s): network sync between servers sharing
  the database. Each server keeps a random id in `plugins/UTags/server-id`. **If you copy a server's folder to make
  another server, delete `server-id` in the copy**, or the two servers ignore each other's changes.
- `bedrock.forms` (default true): Bedrock forms when Floodgate is installed. Admin menus stay chest menus.
- `unlocks.rules` / `unlocks.check-minutes`: earned unlock rules; the commented examples in `config.yml` show the
  format. An unlock is granted once (source `unlock:<id>`) and never taken back.

## AI disclosure

Much of this plugin's code was written with Claude, Anthropic's AI, at the author's direction. The author designed
the plugin and tests it on a live server.

## License

[MIT](LICENSE), copyright 2026 Blockworlds LLC.
