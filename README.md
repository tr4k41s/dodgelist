# DodgeList

Fabric client mod for Minecraft 26.1.2. Warns you when a player on the dodge list joins your party
or is already in a party you join.

## Install

Put these in your `mods` folder:

- `dodgelist-<version>.jar` (from `build/libs`)
- [Fabric API](https://modrinth.com/mod/fabric-api)
- [Fabric Language Kotlin](https://modrinth.com/mod/fabric-language-kotlin)

## How it works

- The list is downloaded on startup, whenever you join a server, and every 5 minutes.
- Party messages (Party Finder joins, `joined the party`, `You'll be partying with`, `/p list`) are
  checked against the list by UUID, so name changes don't matter. The warning shows the player's
  current name.
- The warning says which list (F7, M7) the player is on, and whether they were marked as an
  account share.
- Each player only triggers a warning once every 10 minutes.

The list URL is set in `config/dodgelist.json`.

## Build

Requires JDK 25.

```
./gradlew build
```
