Minecraft Comes Alive Reborn
=====================

[![Crowdin](https://badges.crowdin.net/minecraft-comes-alive-2/localized.svg)](https://crowdin.com/project/minecraft-comes-alive-2)

Hosted on
[CurseForge](https://www.curseforge.com/minecraft/mc-mods/minecraft-comes-alive-reborn) and
[Modrinth](https://modrinth.com/mod/minecraft-comes-alive-reborn)

Minecraft Comes Alive (MCA) is a Minecraft mod that replaces Minecraft's villagers with normal player-like NPCs. It
works in single player, LAN, and SMP.

Villagers can be interacted with - you can talk to them, ask them to follow you, set their home, give them gifts, etc.
Interacting with people builds relationships. Get your relationship high enough, and you will be able to marry someone.

After getting married, you will be able to have children who will do many chores for you such as: Farming, Fishing,
Woodcutting, Hunting, and Mining.

Children will eventually grow up into adults. Adults can get married and have children of their own, and this cycle can
repeat indefinitely!

MCA Reborn is a rewrite of MCA for Minecraft 1.16.5 and upwards, featuring extended village management, villager
genetics, and various enhancements.

## Configuration (Minecraft 1.21.1)

**Fabric:** Forge Config API Port (FCP) is **optional**. Without it, MCA runs with
no in-game configuration editor: edit `config/mca-common.toml`,
`config/mca-server.toml` (and `config/mca-client.toml` on clients) manually.
Restart Minecraft or the dedicated server to apply file changes. An existing
`<world>/serverconfig/mca-server.toml` overrides the global SERVER settings
for that world. The client receives authoritative SERVER settings when joining
a Fabric server; private COMMON and CLIENT settings stay local.

When **FCP is installed on Fabric**, MCA registers its **existing searchable
configuration screen** instead of supplying a different editor. Mod Menu can
provide a convenient way to open a mod's configuration screen. FCP is not
bundled or required by MCA. NeoForge uses its built-in native configuration
system and existing searchable screen; it does not require FCP.

**Without FCP**, manually edit TOML and restart; there is no configuration
GUI or live file watcher. Malformed TOML is left untouched so it can be fixed,
and individual invalid entries fall back to validated defaults. **With FCP**,
FCP itself owns TOML loading/correction, saving, GUI changes and its normal
file watcher/reload semantics; FCP may correct noncanonical TOML files.
MCA migrates recognized old TOML setting paths before FCP loads the files,
preserving backups named `mca-*.toml.pre-mca-migration.bak`.

Fabric servers own SERVER settings in multiplayer. When both server and client
have FCP, FCP's native synchronization is used; other Fabric combinations
receive a validated MCA SERVER snapshot, without synchronizing private
COMMON or CLIENT credentials. For FCP-backed live SERVER changes, connected
clients with FCP must reconnect to receive FCP's native updated SERVER
settings; MCA resends its snapshot to clients without FCP.

## Compatibilities

MCA is usually compatible with every mod, except when it comes to recognising items (e.g. gifting).

Following mods have the required resource packs included and are therefore fully compatible:

- Farmer's Delight
- Atmospheric
- Autumity
- Berry Good
- Buzzier Bees
- Environmental
- Neopolitan
- Upgrade Aquatic
- [Player2](https://player2.game/)

## Contributing

Any contributions to are welcome, but please
check https://github.com/Luke100000/minecraft-comes-alive/wiki/Porting#for-developers.
Clone into your workspace, set it up, make your changes, and submit a pull request for review.

Alternatively you can help translating MCA into your language
on [Crowdin](https://crowdin.com/project/minecraft-comes-alive-2)!

Or you can Join the [Discord Community](https://discord.com/invite/EjYwZUJbpf) for questions, suggestions, or social
interactions.

## Credits

- [Cleora](https://www.planetminecraft.com/member/cleora/)
- WildBamaBoy
- SheWolfDeadly
- ntzrmtthihu777
- ko2fan
- Akjosch
- Innectic
- Sollace
- CDAGaming
- And [many more](resources/assets/mca/api/supporters/contributors.json)!
