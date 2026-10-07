# DodgeList

Fabric client mod for Minecraft 26.1.2, made for Hypixel parties. Warns you when a player on the
dodge list joins your party or is already in a party you join, and can kick them automatically.

## Install

Put these in your `mods` folder:

- `dodgelist-<version>.jar` (from `build/libs`)
- [Fabric API](https://modrinth.com/mod/fabric-api)
- [Fabric Language Kotlin](https://modrinth.com/mod/fabric-language-kotlin)

## Commands

| Command | |
|---|---|
| `/dodgelist f7` / `/dodgelist m7` | Show that list, with current names |
| `/dodgelist reload` | Download the list again |
| `/dodgelist autokick` | Toggle kicking listed players who join your party |
| `/dodgelist autokick share` | Toggle whether account shares are kicked too |
| `/dodgelist report <f7\|m7> <ign> <reason>` | Report a player; staff get a thread on Discord |
| `/dodgelist link <code>` | Link your Discord account for reporting (code from `/modcode`) |

Autokick only kicks players who join without an invite (Party Finder or an open party), and only
works when you can kick (party leader or moderator). Invited players are announced but never kicked.

## How it works

- The list is downloaded on startup, whenever you join a server, and every 5 minutes.
- Hypixel party messages (Party Finder joins, `joined the party`, `You'll be partying with`,
  `/p list`) are checked against the list by UUID, so name changes don't matter.
- The warning says which list (F7, M7) the player is on, and whether they were marked as an
  account share.
- Each dodged player is announced once per party. Leaving, being kicked or the party disbanding
  starts a new party.
- To report, verify in the Discord, run `/modcode` there and enter the command it gives you
  (`/dodgelist link <code>`). Reports are filed under your Discord account. Keep the code private.

Settings are saved in `config/dodgelist.json`.

## Build

Requires JDK 25.

```
./gradlew build
```
