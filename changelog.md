# 8.2.0

* Fixed specific eye height/dimension scaling issues
* Fixed villagers not being able to find a workplace

# 8.1.9

* Improved save-data migration when upgrading MCA worlds from Minecraft 1.21.1 to 26.1.2.
* Fixed player gender and model data persistence, including gender potions and vanilla-model hitbox scaling.
* Improved villager home-bed residency and migration behavior.
* Further improved villager bed pathfinding reliability.
* Fixed invalid villager trades with empty results.
* Fixed MCA zombie villager nameplates not rendering red.
* Fixed baby age corruption.
* Fixed rare crash in blueprint screen.
* Updated translations.

# 8.1.8

* Fixed villagers being unable to move.
* Improved villager bed pathing and sleep reliability.
* Fixed using the Comb, Needle and Thread, or Villager Editor wiping villager inventories.

# 8.1.7

**IMPORTANT**
* Recommended to ***__backup__*** your world.
* This release won't be backwards compatible with previous MCA versions due to internal changes with how traits and personalities are saved

* Improved backend code for compatibility with other mods
* Added nicknames to /mca chatAI context menu so you can have roleplay names (thanks tranthienlong675!)
* Updated languages.
* Addon API:
  * Modernised trait/personality system making it easier for addons to integrate personalities/traits.
  * Personalities are no longer hardcoded by enums and have their own MCA namespace
  * Traits have their own namespace
* Archer Improvements
  * Modded crossbows/bows are supported
    * Some of them may not be due to some modded crossbows being designed for players.
  * Improved Archer logic
  * Archers now don't glitch out if their target disappears.
* Fixed children you are carrying disappearing on disconnect.
* Fixed villagers being stuck in doors
* Fixed villagers getting stuck on their way to a bed

# 8.1.6

* Fix textures

# 8.1.5

* Fixed Family Tree Screen crashing on Vulkan Graphics
* Port of 1.21.1 recent updates.
* Added persistent **ChatAI Context** editing with `/mca chatAI context`.
  * Context can be set separately for the nearest villager, player, village, and world; it is layered into AI conversations.
  * Villager context survives villager/zombie-villager conversion.
  * Editing is permission-controlled (level 3 by default, configurable through `villagerChatAIContextPermissionLevel`).
* Improved ChatAI villager backgrounds, including more varied history, goals, habits, skills, preferences, and beliefs.
* Improved villager ↔ zombie-villager conversion, preserving MCA identity and persistent data more reliably.
* Fixed villager and zombie-villager equipment preservation during conversion.
* Improved ladder and climbable pathfinding, including following, archer movement and reduced ladder-node work.
* Improved occupied-bed handling to prevent villagers crowding or behaving erratically around beds.
* Improved archer movement while mounted and general follow/teleport behavior.
* Added modded-village support to Destiny locations, including the originating mod in hover information.
* Improved the Skin Library: sandbox-compatible login and safer cancellation, stale-request protection, likes/submissions paging, editor loading, blacklist filtering, and profession-name fallbacks.
* Added EMF/ETF/other model compatibility for player and villager rendering.
* Improved compatibility with mods.
* Updated supporter, translator, and Portuguese localization data.

# 8.1.4

* Port of 1.21.1 and 1.20.1 beta
* Removed Eye Colour Slider (Use RGB colours instead), replaced with Iris Brightness
* Improved fishing behavior and compatibility with modded fishing loot.
* Improved mourning and tombstone recovery behavior.
* Improved villager pathing around collision-shaped blocks and modded water.
* Updated face and eye texture assets.
* Fixed compatibility ritual spawning and several editor/library edge cases.
* Bouquets are now handled consistently as gifts for romantic partners.

# 8.1.3

* Fixed Rumours not working and throwing an error.

# 8.1.2

* Fixed MCA Debug Editor breaking when editing a character while it is sitting down.

# 8.1.1

* Added Sirben female noises, thanks mintymacaron!
* Improved guard behavior when players attack villagers.
  * Guards now keep chasing attackers long enough to deliver their warning hits.
  * Guards now warn, attack and pardon more consistently based on the player's warning level.
  * Guard warning dialogue no longer stacks with the normal "ouch" dialogue on the same hit.
* Improved archer guards in combat.
  * Archers now draw and fire bows more reliably.
  * Archer arrows should no longer be blocked by normal hurt immunity during fast combat.
  * Left-handed villagers still look left-handed, while bows and weapons stay in the correct hand for Minecraft's
    combat checks.
* Guards without a home village can now wander instead of standing idle.
* Villagers and guards can now visibly eat food to recover health when they are safe and out of combat.
* Improved villager movement around awkward decorative blocks such as lanterns, which could previously confuse
  pathfinding.
  * Added a new MCA pathfinding tag so modpacks and addons can mark custom blocks with unusual collision shapes.
  * Added config options for extra collision checks while villagers pathfind.
  * The broader collision-check option is off by default, but can help modpacks with many custom block shapes.
* Fixed `bountyHunterInterval <= 0` crashing
* Fixed some mods breaking lighting rituals on fire.
* 
# 8.1.0

* Added the **Music Store** building, giving sad villagers another place to visit for happiness recovery.
* Added building polymorph handling for overlapping scans, including a paginated selection screen, building icons, smarter filtering for generic **House** matches, and clearer building removal/lookup behavior.
* Improved villager AI performance by reducing unnecessary guard enemy scans, caching equipment checks, caching health bonus updates, and adding retry cooldowns for failed walking/building entry logic.
* Fixed several villager editor and skin registry issues, including saving mood, infection, traits, personality, relationship hearts, legacy hair styles, body skins, clothing, and gendered skin metadata more reliably.
* Improved villager interaction UI text so **Ride Mount** changes to **Dismount** when the villager is already riding.
* Added config support for villager follow range.
* Improved guard behavior when players attack villagers.
  * Guards now keep chasing attackers long enough to deliver their warning hits.
  * Guards now warn, attack and pardon more consistently based on the player's warning level.
  * Guard warning dialogue no longer stacks with the normal "ouch" dialogue on the same hit.
* Improved archer guards in combat.
  * Archers now draw and fire bows more reliably.
  * Archer arrows should no longer be blocked by normal hurt immunity during fast combat.
  * Left-handed villagers still look left-handed, while bows and weapons stay in the correct hand for Minecraft's combat checks.
* Guards without a home village can now wander instead of standing idle.
* Villagers and guards can now visibly eat food to recover health when they are safe and out of combat.
* Improved villager movement around awkward decorative blocks such as lanterns, which could previously confuse pathfinding.
  * Added a new MCA pathfinding tag so modpacks and addons can mark custom blocks with unusual collision shapes.
  * Added config options for extra collision checks while villagers pathfind.
  * The broader collision-check option is off by default, but can help modpacks with many custom block shapes.

# 7.7.18-beta.10

* Improved smart selection filtering in the polymorph UI: the generic **House** option is automatically filtered out if **Big House** is also matched, preventing unnecessary choice prompts when scanning.
* Empty icons don't render as graveyard anymore, House option now has an icon.
* Improved building removal logic.

# 7.7.18-beta.9
* The **Ride Mount** button on the villager interaction screen now changes to **Dismount** if the villager is riding something, making it easy to get them down.
* Fixed a bug on dedicated servers where changing a villager's mood, infection, traits, personality, or relationship hearts in the editor wouldn't save.
* Improved how villager skins and clothing registries are loaded behind the scenes to be cleaner and more flexible.

* Added a **Music Store** building.
  * Villagers can now visit the Music Store when they are feeling sad.
  * This helps raise their happiness, similar to how the inn works.

* Improved overlapping building detection.
  * When multiple building structures overlap, you can now choose which one should be used.

* Improved villager AI performance.
  * Villagers now do less unnecessary work every tick.
  * Large villages should have fewer lag spikes and smoother server performance.
  * Guard enemy scans are now cheaper, running only for villagers actively guarding, following a player, or fighting (non-combat villagers skip this scan entirely to save performance).
  * Villagers are less likely to waste pathfinding work when moving around the village.
  * Villagers should no longer forget homes or jobs just because a path search briefly failed.
  * Equipment checks for guards and archers are now cached, reducing repeated work.
  * Villager health bonus updates are now cached instead of being recalculated every tick.

* Fixed an issue where some villager movement state updates were running far more often than intended.
* Fixed excessive repeated max-health modifier updates on villagers.
* Fixed some expensive villager walking logic that could retry too many times in one tick by adding a retry cooldown after failed attempts.

# 8.0.6

* Fixed gifting boquets/engagement rings causing errors.

# 8.0.5

* Villagers should stop floating in passenger seats such as boats.
* Baby nametags are correctly adjusted
* Added old skins texture back.
* Fixed guards attacking tamed/owned Zombie Nautiluses.
* Tamed/owned mobs are no longer targeted or attacked by guards.
* Villagers should be able to ride any entity that is rideable, including modded entities.
* Fix Mood, Infection Progress and hearts not being editable due to recent modifications.


# 7.9.9

* Fixed nameless villagers being saved as their profession label after opening the Villager Editor.

# 7.9.8

* Fixed vilagers sometimes losing their trades.
* Improved performance
* Villagers shouldn't drown anymore.
* Fixed RGB eye slider.

# 7.9.7

* Fixed villager editor not saving changes, sorry!
* Fixed eye color customization and Heterochromia traits getting wiped in the editor.
* Added genetic eye color slider in the editor's eyes customization page.

# 7.9.6

* Added a "No Aging" trait to stop specific villagers from growing up or aging (available only through the Villager Editor).
* Added an "Infertile" trait, meaning some villagers cannot have children (can be removed using the MCA Debug Book).
* Added a configuration option to choose which type of headstone/grave spawns when a villager dies.
* Selecting clothing or picking an outfit in the editor or library will no longer make the villager bald.
* Spouses are no longer considered biologically related when looking at family lines.
* Babies, children, and relatives (other than spouses) can no longer flirt, kiss, or accept romantic items.
* Children can no longer have the "Flirty" personality.
* When children grow up into teens and adults, their personalities will now re-roll for natural character development.
* Guards now actively fight enemies while following you.
* Fixed guards holding their weapons or shields in the wrong hand if they are left-handed.
* Fixed a multiplayer issue where editing villagers or using custom skins would sometimes cause sync issues.
* Fixed building scans so blocks like candles and water are correctly recognized in custom building definitions.

# 7.9.5

* Fixed trait pagination in the villager editor
* Reduced dynamic skin texture churn while Immersive Library assets are still loading
* Advancement book addition can be toggled with a config option.

# 7.9.4

* Clothes can be locked now, and are automatically clocked when using Needle and Thread / Editor
* Added `villagerChatAIFuseSystemPrompt` config flag for extra scuffed setups.
* Maybe fixed the "Spouse not found" issue.
* Fixed destiny spawning you out of world border.
* Tombstones now have more explosion resistance, and spawn better after death-by-explosion.
* Tombstones now drop their NBT, allowing them to be moved.
* Fixed high characters from glitching into blocks.
* Guard armor can now be configured.

# 7.9.3

* Fix multiplayer sync.
* Fix changing skins making the character bald.

# 7.9.2

* Fix dedicated server crashes
* Fix eyes showing when invisible.

# 7.9.1

* Villagers now show up with their correct face and skin icons on map mods (such as Xaero's maps and JourneyMap) instead of appearing as broken pink squares.
* Archer villagers' arrows will now consistently hit and damage their targets instead of occasionally bouncing off without doing anything.
* Fixed villagers appearing in the wrong spot or floating next to their beds while sleeping.
* Fixed the Minecraft Comes Alive keybind translation key category
* Bug fixes
* Hats / Models correctly render.
* Fixed Library Editor

# 7.9.0

* Overhauled the villager editor with the new hair, face, skin, eye, preset, preview and export controls.
* Added data-driven body skins, face textures, eye definitions, layered hair parts and hair style definitions.
* Added layered hair rendering, dyeable body skins, dyeable eyes, heterochromia eye colors and rainbow eyes.
* Added voice preview support in the editor.
* Added an admin command to override village requirement.
* Improved villager bed pathfinding.
* Increased the maximum configured villager pathfinding distance.
* General AI improvements.
* Make guards use their own entity search sensor, instead of vanillas to remove a 40 tick reaction time
* Archer fixes and improvements.
    * Stop archers from eating to heal up while in fighting stance to improve combat efficiency.
    * Fixed archers not recognizing crossbows.
    * Archers now flee if too close to a mob.
* Fixed quotation marks showing up around villager names when migrating worlds from older mod versions.
* Fixed guards occasionally ignoring active threats.
* Fixed baby, toddler and child villagers not reliably being pickable.

# 7.8.6

* Added optional player hitbox scaling based on MCA size and width via `scalePlayerHitboxWithSizeAndWidth`.
    * Keep disabled on PvP-heavy servers to prevent size-based advantages.
* Fixed golden apple on cure being consumed twice
* Fixed baby item sent to oblivion on drop
* Fixed adopting children not always updating parents
* Fixed doors not always getting closed
* Trades are no longer reshuffled when switching workplace but not profession
* Archers now use bows even when not on duty
* Fixed sleeping offset when using villager model
* Added textures for adventurer and cultist in Squidward mode
* Fixed various crashes
* Fixed missing names for villagers spawned by spawn egg
* Fixed cultist trades

# 7.8.5

* Fixed Recipes

# 7.8.4

* General fixes and pathfinding improvements
* Fixed Debug Screen
* Guard target priorities now support entity type tags such as `#minecraft:undead`.
    * Refresh your MCA Config to propagate the changes.
* Iron golems no longer are able to target villagers.
* Fixed MCA zombie villager nameplates no longer rendering red.
* Generic Villager Archer AI Class improvements
    * Bows now animate when shooting like players do.
    * Archers now keep skeleton-style distance and respond to threats while outside a home village.
* Fixed Villager Zombie Conversion, now they shouldn't disappear.
* Fixed a bug with villagers being stuck when trying to path to their beds.
    * This will be backported eventually to earlier versions.
* New config option villagerPathfindingDistance for long villager routes to remembered points of interest such as beds.
* Fixed first-person MCA hand skin rendering when using the villager model.
* Fixed MCA babies.
* Fixed players blinking.
* Added message when destiny location cannot be found.

# 7.8.3

* Surprise, more fixes!
* Relaxed fabric loader version

# 7.8.2

* Even more fixes

# 7.8.1

* Fixed crashes
* Fixed loot tables

# 7.8.0

* Ported to Minecraft 26.1.2 (Thanks Mikulasz12!)
* Fixed crashes and a few issues

# 7.7.10

* Improves family tree search
* Fixes mail inbox loading

# 7.7.9

* Fixed crashes

# 7.7.8

* Villagers joined via inn now have random region names
* Added Easy Anvil compat

# 7.7.7

* Synced translations
* Updated TTS server URL in config

# 7.7.6

* Fixed issues with config (Thanks pau101!)
* Synced translations
* Fixed camera eye adjustment when using the player model

# 7.7.5

* Fixed MCA AI auth

# 7.7.4

* Fixed root advancement

# 7.7.3

* Fixed a crash on dedicated servers

# 7.7.2

* Fixed crashes (Thanks alfuwu!)
* Fixed color blind shader loading

# 7.7.0

* Ported to 1.21.1

# 7.6.10

* Maybe fixed empty villages on pre-generated worlds (Thanks SlayerTheChikken!)

# 7.6.8/9

* Fixed a crash with llm command parsing

# 7.6.7

* Fixed a crash with The Aether
* Added missing tombstone loot tables and fixed particle textures
* Increased twin chance to 5%

# 7.6.6

* Allow tripplets, quadruplets, quintuplets and beyond
* Fixed a crash in the skin editor
* Disabled guard teleportation by default again
* Added experimental support for commands via ChatAI (`villagerChatAIUseTools` in the config, will react to trading, go
  home, stay here, ...) (Thanks AdrisJ6 and Player2!)

# 7.6.5

* Fixed a crash

# 7.6.4

* Rose gold does no longer classify as gold, interfering with recipes
* Fixed some GUI crashes
* Fixed deaths counting sometimes twice (two mails, double hearts, ...)
* ChatAI related stuff:
    * Added better integration
      with [Player2](https://github.com/Luke100000/minecraft-comes-alive/wiki/GPT3-based-conversations#Player2)
    * Villager no longer starts to yap when you mention a fraction of their name
    * Villagers will now try to respond in your selected language by default
* TTS related stuff (https://github.com/Luke100000/minecraft-comes-alive/wiki/TTS):
    * Added realtime TTS (experimental, worse quality, less languages, but works together with the ChatAI)
    * Added TTS support for ElevenLabs (thanks BinaryGun951!) (Requires an ElevenLabs API key!)

# 7.6.3

* Turned villager teleportation back on by default
* Fixed spawn group causing issues with some sound mods
* Modded jobless traders now show the trade button

# 7.6.2

* Fixed crash on Turkish locale
* Fixed Cribs crashing on wood compat mods
* Fixed glowing villagers being visible
* Fixed crash when using structure tags in destiny

# 7.6.1

* Fixed crash on Forge

# 7.6.0

* Added cribs, a lot of cribs, in all colors and woods
* Some gift registry updates
* Destiny now supports structure tags
* Fixed compatibility issues with Productive Bees
* Renamed names data dir to avoid conflicts

# 7.5.22

* Fixed Library Auth once again.
* Fixed Steve skins being converted to Steve skins again.

# 7.5.21

* Fixed Library Auth when updating from older version

# 7.5.20

* Fixed Library Auth

# 7.5.19

* Fixed trait shaders
* Rose gold dust is no longer part of the gold dust group to remove conflicts
* Fixed incompatibility with Productive Bees
* Added experimental long term memory to ChatAI
* Hopefully fixed crashes
* Villagers no longer grieve at single graves
* Villager can now glow while invisible
* You can now whistle villagers when they sit in vehicles
* Height now affects Hemoglobin levels
* Villagers no longer jump in front of gates

# 7.5.18

* Updated contributor book
* Fixed ReaperSpawner eating all your CPU

# 7.5.17

* Now its compatible with Cobblemon!
* Fixed TTS again!

# 7.5.16

* Fixed issues with building jars

# 7.5.15

* Merged [Inworld integration](https://github.com/Luke100000/minecraft-comes-alive/wiki/GPT3-based-conversations)
  branch (thanks CSCMe!)
* Fixed incompatibility with Cobblemon (thanks Apion!)
* Fixed AI issues with Grim Reapers, causing him to go much higher than intended
* Fixed issues when using Arabic numerals
* Fixed crashes when using TTS
* Fixed Inn spamming adventurers
* Probably fixed deadlocks related to SpawnQueue and ReaperSpawner
* Fixed incompatibility with AutoTranslation and related mods

# 7.5.14

* TTS language is now detected automatically

# 7.5.13

* Updated chatAI to v2
    * See https://github.com/Luke100000/minecraft-comes-alive/wiki/GPT3-based-conversations
    * Older versions will no longer work, technical reasons
* Added TTS v2
    * See https://github.com/Luke100000/minecraft-comes-alive/wiki/TTS

# 7.5.12

* Fixed various smaller issues
* Personalities now have a few more phrases
* Fixed harvest task not dropping items
* Set workplace no longer messes up trades

# 7.5.11

* Fixed a crash

# 7.5.10

* Fixed data loading issues on systems with locales having non-western digits.
* Villagers no longer harvest blocks in claimed regions
* Added support for Let's Do Bakery
* Fixed root advancement to appear on world load
* Fixed villager marriage limit math
* You can no longer restrict bells or gravestones, causing them to get stuck
* Vanilla player models no longer modify your eye height
* Player eye height now automatically refreshes on world join and editor changes
* The editor now tells you if an incompatible mod disabled custom models
* Removed vanilla mechanics for turning villagers into zombie villagers as this causes undefined behavior
* Added HSV hair color option to editor
* Added loot tables in case mods rely on them and let zombie villagers drop some flesh

# 7.5.9

* Wrong procreation cooldown on new worlds

# 7.5.8

* AI performance improvements
* Dropped babies in the inventory no longer end up in the backrooms
* Inverted marriage slider as this is more what people expect
* Fixed large players receiving damage on the ceiling
* Married adventurers no longer despawn
* Added a procreation cooldown (3 in-game days by default)
* Added attack-text cooldown
* Fixed trait influenced gender preferences
* Added gender override config flag for players

# 7.5.7

* Fixed a crash

# 7.5.6

* Added missing loot tables for some headstones

# 7.5.5

* Fixed server crash

# 7.5.4

* Removed size requirements of blueprint buildings
* Restored C2ME compatibility
* Fixed drunk behavior at fence gates when smart fence AI is disabled (which it is by default)
* Fixed marriage slider not being saved
* Fixed fallback translations on 1.19.4+
* Added morph to the list of don't-render-custom-arms-when-installed list
* You can no longer squish babies on the ceiling
* Archers can now equip crossbows
* The camera height is now correct
* Added the ancient city as a rumor and starting location
* Female cultists now also wear robes
* Skin library now has...
    * Much less data transfer
    * A report system
    * Filter buttons for hair and clothing
    * Hopefully less connection issues thanks to a dedicated domain
    * Global skins now actually work

# 7.5.3

* Ported to 1.20
* Invalid skins are now hidden by default in the Skin Library
* Added even more sanity checks when uploading stuff
* Fixed father/mother at baby item not always being correct

# 7.5.2

* Removed gold dust
* Added compression to skin library networking
* Skin Editor will now reject most invalid skins
    * Existing invalid skins are marked
* Changing workspace will rebuild trades

# 7.5.1

* Fixed a critical performance issue when childrens are stuck in a wall
* Disabled smarter door AI (which can open gates) by default due to reports of villagers not closing the door
* Added a few config flags for customizing the destiny screen

# 7.5.0

* Added experimental Skin library, editor and uploader
* Added civil registry, a log writing down all events in a village
* Added the villager tracker, a compass being able to track the last known position of villagers
* Fixed baby growth command
* Move state and infection status are no longer on the gravestone
* Fixed hugging being restricted to adults
* Decreased tombstone text render distance to tackle lag

# 7.4.9

* Added compat with Chunks fade in

# 7.4.8

* Fixed a crash
* Enabled toddler clothes
* Extended Sirben lore
* 1.19.3+ chiseled bookshelves now support MCA books
* Golden apples can now cure children
    * Using an apple on non-infected villagers will work as a gift now
* Fixed compatibility with Custom Trading Screen
* Added `fractionOfVanillaZombies` Config flag
* Added `overwriteAllZombiesWithZombieVillagers` Config flag
* Fixed a few purple uninitialized skin bois
* Fixed "monster" advancement

# 7.4.7

* Fixed villagers not spawning
* Fixed not being invisible while in destiny
* Fixed a crash when telling stories in certain modded environments
* Syned translations

# 7.4.6

* Added optional AI voice acting
* Fixed reaction when lactose intolerant
* Graveyards now print the minimum requirements correctly
* Fixed some configs not being synced properly from the server
* Improved whistle on sleeping villagers
* Restricted buildings no longer increase (visual) max population
* You can now set the home properly when changing the village
* Adventurers no longer move in
* Cooldown for being hurt messages
* Added rumors, destiny and spawning to threads, decreasing lag and potentially fixing some timeouts
* Added Armourers Workshop compatibility

# 7.4.5

* Fixed blueprint again
* Fixed babbling babies on dedicated servers
* Fixed AI not always responding

# 7.4.4

* Fixed missing skin color on hands
* Fixed blueprint on dedicated servers

# 7.4.3

* Fixed crash when villagers have friends lol

# 7.4.2

* Fixed invalid task
* Player in destiny now become invisible instead of spectator
* Decreased village merging radius
* Graveyards with only 1 or 2 headstones are not shown on the map anymore
* Improved setting work place

# 7.4.1

* Improved AI and interaction times
* Villager will now be audible to all (nearby) players
* Villager can now speak to you while following or staying
* Improved compatibility with some datapacks
* Asking to go home now disabling following/staying
* Improved and fixed set home, restricted homes, blueprint home sync and go home
* Made all chances in the Config floats
* Added nameTagDistance to control range of name-tags
* Fixed Armor rendering issue
* Fixed server crash when trying to eat something with status effects
* Improved harvesting task
* Fixed Sirben having the voice of god
* Fixed config crash errors not being printed
* Fixed compatibility with First Person mod and co
* Fixed babies speaking fluent english

# 7.4.0

* Added AI benefits for patrons

# 7.4.0 rc 4

* Fixed placing babies

# 7.4.0 rc 3

* Fixed GPT3 AI on dedicated servers
* AI now has a sense of biome, weather and daytime
* Fixed crash on 1.19.3
* Fixed a lag spike and improved overall village performance
* Synced Patrons list
* Fixed wrong baby name when placing from creative
* Fixed 1.19.3 on Forge

# 7.4.0 rc 1, 2

* Simplified Villages
    * Villager no longer rely on a village to find a home
    * Buildings are no longer required and automatic scanning is disabled by default
        * Frees a bit of CPU
        * This can be toggled in the blueprint
        * Manual tagging is possible too for more control
    * Buildings do however grant buffs and upgrades to the community, boosting the mood and enhancing guards armor
* Added special vision for Sirbens
* Added PTG-3 based villager chat AI
    * Enable in Config
    * Speak their name once to start a conversation
* Added more phrases
    * Especially if you are a parent
* Fewer crashes when crippling the Config
* Improved dialogues
    * Player Gender
    * Added Greeting back
    * Villager no longer welcome you if you are a Mayor or Monarch
* Staff of Life now has 10 uses instead of 5
* Fixed armor texture on female players
* Fixed performance issue related to villager renderer
* Fixed Turkish locale issues again
* You can now start a village with a room (e.g. underground)
* Added more phrases
* Fixed villagers using the steve second layer
* Villager can now open gates
* Villager now prefer paths and dislike stepping on grass (potential unstable)
* Improved grieving
    * Villager will grieve when a close villager dies
    * Villager will also grieve once a week at a graveyard
* Improved taxes
    * Tax items can now be configured
* Fixed graveyards disappearing
* Villager with family or friends will now spawn a gravestone if none was found
* Updated most textures to SoyTutta
* Updated and added additional headstones
* Fixed blocked buildings
* Added rock paper scissor dialogue options for children

# 7.3.21

* Added `percentageOfVanillaVillages` as a config value to randomly keep vanilla villages
* Fixed adventurers spawning in unloaded chunk
* Fixed crash when disabling MCA player model
* Added tooltip to editor to avoid confusion when choosing vanilla model
* Fixed players also having randomly colored hair
* Several Changes to the Naming systems in MCA
    * Player Naming has been fixed and works properly, much like how /nickname systems work (If you have an existing
      custom name, MCA will use that instead)
    * It is no longer possible to have a whitespace/empty name, and multiple safeguards have been placed to prevent
      exploits.
    * The `Nameless Traveler` code has been removed in favor of the above fix.
* Added a Homosexuality Trait as a possible chance to spawn with
    * This trait cannot be inherited from past/to future generations
    * Having this trait will enforce gender restrictions in Relationship Items and Villagers entering relations with
      those of the same gender
    * Due to this trait being available, some relationship items being gifted may result in `incompatible` responses.
    * In the event of this trait being applied alongside the bisexual trait, the homosexual trait will take priority.
* Added `professionConversionsMap` as a config value, made for mod compatibility
    * Designed to be able to use clothing from another profession, if your mod does not supply any to us
    * Example: You can make a Butcher wear Armorer's clothing, or villagers wear a certain professions clothing by
      default.
    * Only Adult clothing is used in this, baby and child clothing remains unchanged.
* Added `playerRendererBlacklist` to disable certain render elements of the player model if certain mods are present
    * Supported Values: `arms`, `left_arm`, `right_arm`, `all`, `block_player`, `block_villager`
* Fixed #373 (Gamemode being switched before user finishes destiny)
    * Should also resolve the falling-through-world issue
* Fixed #239, #368 (Compatibility Fix for older Spectrum Versions)
* Added `villagerInteractionItemBlacklist` to limit certain items from being used to interact with MCA villagers
    * By default, buckets are included to resolve Issue #273
* Added command to convert vanilla villager within range
* Fixed a possible crash when trying to edit a villager with an empty name; should now give it a random name instead
* The `canBeAttractedTo` checks for a Player/Villager relationship now properly respect traits
    * IE the same logic that is used for villagers now also applies to players
* Added `shaderLocationsMap` to allow specifying custom shaders dependent on traits
    * If the camera entity has the specified trait, it'll apply the shader, and remove it when the current camera entity
      does not.
    * Requires `enablePlayerShaders` to be true to utilize this feature
* Added functionality for `Lactose Intolerance`
    * Applies a Poison effect after usage, similar to if a spider eye were consumed
* Traits that are not meant to be equipped by players will no longer be seen in the Editor or Destiny Screen
    * This can be bypassed via the `bypassTraitRestrictions` setting

# 7.3.20

* Added backwards compatibility for 1.16.5 and 1.17.1, to align with the EOL of 1.19.0 and 1.19.1
    * 1.19.0, 1.19.1, 1.17.1 and 1.16.5 are now officially considered EOL, and users should upgrade to retain support
* You can no longer set the home of a villager who is either there temporarily or does not require a home
* Fixed trades
* Fixed equipment dropping
* Fixed arms being funky in multiplayer

# 7.3.19

* Official Support has been added for the Quilt ModLoader (Requires QSL + Quilted Fabric API)
* Added `villagerDimensionBlacklist`, modded villager whitelists, and `allowedSpawnReasons` as new config options
    * Advanced Usage Only, tampering can lead to tears :(
* Multiple Build Script adjustments to align with universal packaging + full automation
* Fixed some wrong relationships on older worlds

# 7.3.18

* Fabric and Forge are now packaged as one universal jar file
* Fixed trait inheritance change
* Fixed updating villager name not reflecting change in Blueprint
* Fixed profession name in Waila etc
* Fixed outdated infection book

# 7.3.17

* Fixed an issue with the Bone Meal Check in `HarvestingTask` not taking into account modded items
* Fixed an issue relating to a mismatched slot checked when a villager is left-handed and `HarvestingTask#bonemealCrop`
  was ran
* Rewritten `HarvestingTask#plantSeed` to allow modded plants to be properly planted, if specified in
  the `villager_plantable` tag and a valid `BlockItem`
    * This also fixes pumpkin and melon seeds not properly planting, despite being in the tag
* Added a `minBuildingSize` as a counterpart to the previously implemented `maxBuildingSize` config option
    * I'm not sure why someone wanted this, but...ok.
* Editor Screen Paperdoll models will now follow your mouse, just like how the Inventory Screen behaves

# 7.3.16

* Fixed wrong pitch for babies
* Pitch slowly increases with age
* Fixed inconsistencies in relationship data with the Matchmaker's Ring
    * Resolves cases of incest + Added `canBeAttractedTo` check support
* Fixed a missing `getGender` check in creating a player's Family Tree entry
* Fixed enchantments glint on villagers
* Fixed using mca villager spawn eggs on mca villagers
* Fixed Sneak-Interactions with mca villagers
    * Should now open trades on applicable villagers
    * Villagers that are Jobless will disagree with the proper sound effect
* Fixed silent sound effect compatibility with Celebrate Sounds

# 7.3.15

* Fixed multiple rendering issues that were causing invisibility to not work on Villagers
    * Also applies to players using the custom villager model
* Added a `villagerRestockNotification` config option
    * If enabled, will notify anyone in a villager's home village when a trade restock occurs
* Undo the magical edit made to the failing villager state (ERR_EASTER_EGG_FLUKE)
* Modified the Gift Satisfaction for ranged weapons to based off the range instead of a static `15`
* Added the Angry and Celebratory Voice Lines for Villagers when using MCA voices
* Added a `showNotificationsAsChat` config option to toggle villager notification style
    * If true, the normal action bar notifications will instead show in chat.
* Added preliminary/supplementary data for 7.4.0 content
* Misc. Build Pipeline cleanups
* Fixed mail notification
* Fixed offline players not receiving letter of condolence
* Villagers are no longer pissed when killing a Zombie Villager
* Zombie Villagers without any family won't be buried
* Infected villagers being killed by a zombie no longer duplicate their inventory
* Infection now lasts longer
* Adventurers with high hearts may stay without asking
* Mood slowly change on its own, with slight tendency towards neutral
* Fixed `getGender` checks for PlayerSaveData (Now should properly be reflected!)
* Villagers have a voice pitch gene

# 7.3.14

* Fixed a crash that can occur when leaving a villager's name in the editor empty when switching tabs
* Modified a failing villager state into something more...magical ;)

# 7.3.13

* Added Support for 1.19.1
* Sneaking + Interacting with a villager with the editor item will now open their inventory!
* Added Left-Handed Trait as a possible chance to spawn with
    * This trait can be inherited from past/to future generations
    * Having this trait will change their dominant hand in most tasks to be their left hand (Known to the player as the
      off-hand slot)
    * Some examples of this include Work Tasks, EquipmentSet's and Melee Attacks (For Equipment, if a preset already
      uses both hands, it'll remain unchanged.)
    * Given Minecraft was never intended to support this type of gameplay, further tuning may be required in a future
      update.

# 7.3.11/7.3.12

* Misc. Patches for 1.18.2 and 1.19 Dependencies (1.18.2 officially identifies as LTS now!)
* Added `innArrivalNotification` config setting, for notifying players in the village that a new traveller has arrived!
* Added a Night Owl schedule, in which Cultist's and Outlaws have a chance to use, based on the `nightOwlChance` config
  setting (Default: 50% Chance)
    * Enable `allowAnyNightOwl` to be able to apply this same chance to other professions
    * Guard's will also now use `nightOwlChance` instead of using a random boolean to determine their schedule (Meaning
      if you want more guards at night, increase `nightOwlChance`)
* Added a Bisexuality Trait as a possible chance to spawn with
    * This trait cannot be inherited from past/to future generations
    * Having this trait will bypass gender restrictions in Relationship Items and Villagers entering relations with
      those of the same gender
    * Due to this trait being available, some relationship items being gifted may result in `incompatible` responses.

# 7.3.10

* Fixed Villager Fate achievements (Happy hunting!)
* Added an achievement for dropping a baby? (There's more to this right?!)

# 7.3.9

* You can no longer trade with archers
* Fixed crash in blueprint
* Fixed villager following you after trade
* Fixed villagers not working when previously told to stay
* Fixed harvesting tasks not always harvesting
* Added phrases for working
* Villager no longer work when panicking
* Villager can heal faster when eating

# 7.3.8

* Fixed forge server
* Villager no longer make surprise sounds while trading
* Fixed staying and following commands causing high CPU usage
    * Panicking staying villagers will now run
        * They will not return to original point yet, will be fixed in guards-update
* Fixed issues when server and client have different java versions

# 7.3.7

* Fixed server crashing
* Fixed some sounds not triggering
* Enabled voices by default
* Gave sirben more personality

# 7.3.6

* Finished sounds
    * Normalized and denoised existing ones
    * Added trading, hurt, snoring and coughing
    * Added sounds for females
* Reputation is now the sum of all hearts
    * Reputation has been renamed to hearts
* Villages with less than 3 (configurable) buildings are now considered settlements
    * They will not trigger the enter-village notification
* Children now grow up in 16 days instead of 8 (configurable)
* Babies no longer greet you
* Added rose gold dust recipe and therefore a way to obtain rose gold
    * Removed rose gold ore
* Being in a relationship helps for some interactions
* A higher villager levels decreases infection rate
* Fixed a few minor bugs
* If you hit a villager, it will no longer follow you
* Fixed armor texture on villagers

# 7.3.5

* Added potion of femininity/masculinity
* Fixed promised villager marrying
* Fixed black hair issue
* Removed duplicate jobless skins
* Adventurers no longer claim beds
* Adventurers no longer complain about too crowded places
* Adventurers now actually charge you when hiring them
* Hopefully fixed Stuck-in-spectator mode bugs
* Added fully vanilla mode to player model selection
* Added a hint to the limited `/mca editor`
* Reduced which villagers are converted to support mods (Easy Villagers)
    * E.g. Igloo will have vanilla villagers now, for technical reasons
* Less mca baby zombie villagers
* Fixed apologizing to villagers after hit
* Made interactions easier, except for stories if you are lying
* Made bounty hunters more rare

# 7.3.4

* Engagement rings now set the relationship to engaged
    * Engaged villagers won't marry someone else
* Gifting a bouquet prevents villagers from marrying other villagers
* More config for inn spawning behavior
* Added (deceased) father and mother for all spawned villagers
* Fixed compatibility issues with Origins mod
* Added rainbow trait
* Hair color now blends when color is gifted again
* 2% of villagers dye their hair (configurable)

# 7.3.3

* Parents with same gender are now properly registered

# 7.3.2

* Added support for 1.19
* Added support for advancements tied to fate
* Added Adventurers
    * Spawn twice a day at inns
    * Despawn after 2 days
    * Can trade, be hired and asked to stay
* Villager now chooses the best equipment
* Added more eye variants
* Fixed zombies not always using zombie clothing
* Villager on fire will now burn their clothes
* The Sirben cult appeared
* Added 50.000 names from 55 different countries
    * Config option available to use modern USA names only
* Destiny now sets spawn location
* The /mca editor has been replaced by a limited version (configurable) to prefer comb and needle and string items
* You can now start a village without villagers using the blueprint

# 7.3.1

* Traders now spawn in Inns
* Added comb to modify the hairstyle of villagers and players
* Added needle and thread to modify clothes of villagers and players
* Fixed advancements and book rewards
* Improved name distribution
* Marriage and Birth notifications are now only printed within the village boundaries, or when being friends
* Added config flag to disable boobs
* Added support for Immersive Weathering
* Fixed a few crashes
* Taxes are now once a week
* Fixed performance issue
* Fixed persistent zombie villagers despawning

# 7.3.0

* Updated translations
* Fixed crash on dedicated server when picking up children
* Cleaned up config, added link to config wiki
* Villager can no longer plant modded plants to remove a crash
* Fixed a few crashes

# 7.3.0 alpha 3

* Switched to an injected based player model to hopefully improve mod support
* Using the Player model now makes use of size and gender
* Females are now in average 5% shorter than males
* You can now choose between player and villager model in the destiny screen
* Fixed modded profession being naked
* Fixed massive family crashing whistle
* Fixed root advancement
* Fixed Gifting advancements
* Fixed missing riding phrase
* Fixed duplication issue when villager use bonemeal
* Fixed chore animations
* Added wandering around when no tasks have been found
* Fixed young villagers not holding tools correctly

# 7.3.0 alpha 2

* Fixed Destiny partly working on dedicated servers
* Fixed mod conflicts
* Added clothing and hair selection
* Bounty hunter no longer attack while in creative
* Gifting a golden apple to a child now properly reduces the stack
* Fixed a few wrong buttons
* Added a few more config flags to control destiny, teleportation and editor access
* Sneaking no longer breaks the model
* Editor offers a button to select player or villager skin
* Fixed issues with resizing window while in editor

# 7.3.0 alpha 1

* Added destiny
    * You are asked to customize the player
    * Then you can choose from a set of spawn location to start your journey
* Massive dialogue overhaul with over 300 new phrases
    * Added Rumor dialogue
    * Added Time specific dialogues
* Grumpy, Gloomy and Shy personalities

# 7.2.0

* Ported to 1.18.2
* Modded Villager professions now display properly in all mca interfaces
* Fixed incompatibility with eldritch mobs
* Villager get 5 extra hearts per level
* Added config flag to use squidward models
* Fixed sleeping
* Adjusted villager teleportation to be more configurable
* Different ages will now move at different speed
* Genes now affect speed
* Converted villagers will now retain custom nbt data and age
* Fixed inventory disappearing on convert
* Fixed marriage and family tree loss on convert
* Maximum building size and radius are now configurable
* Fixed UI Scaling issues with interaction buttons
* Fixed issues of bounty hunters spawning within villages if your y value is below its bounds
* Added Village Merging
* Fixed villagers struck by lighting
* Added electrified trait
* Increased button widths to better support different languages
* Decrease revenge aggression based on the guards' relation to you
* Added guard target list to config
* Added aborting children by unconventional means
* Updated the Blueprint Interface to appear more cohesive
* Added `/mca-admin forceBuildingType <type>` to force a building's type
* Fixed issues with Chores not working in 1.18.x
* Added modded support to `ChoppingTask` as well as several optimizations
* Mining Speed Multipliers can now effect `ChoppingTask` speed (The original 7 seconds is also configurable)
* Fixed potential crashes when villagers perform Harvesting chores (Planting seeds throwing a NPE)
* Fixed player marriage not saving
* Sneaking before interacting with a villager will now open trading

# 7.1.0

* Ported to 1.18 (And 1.18.1)
* Fixed missing chest tag
* Added baby clothes
* Fixed villagers not fully moving out of the old building

# 7.0.8

* Readded blacksmith functionality
* Fixed scaling-flickering with iguana tweaks
* Added text when trying to assign to invalid buildings
* Improved interaction layout
* Staff of Life can no longer be enchanted
* Fixed chores phrase names
* Command kill no longer counts as murder
* Added config flag to disable name tags
* Fixed log spam regarding invalid bounding boxes
* Fixed issues when assigning family in editor
* Buildings now support modded chests
* Villagers will now use your editor name
* Fixed letter author and creative mode usage
* Strengthened Grim Reaper
* Added mod support for atmospheric, autumity, berry good, buzzier bees, environmental, neopolitan and upgrade aquatic
* Villager now recognize and estimate the value of every (modded) armor, tool, sword, bow and food as a gift (accuracy
  not guaranteed)

# 7.0.7

* Experienced villagers no longer become guards
* The king can assign archers and guards at will
* Fixed king rank
* Can no longer pickup teens
* Fixed curing zombie villagers
* Added missing translations
* Added book of supporter
* Fixed gift desaturation not working
* Improved teleportation, especially when following the player
* Fixed the pixel gap of headstones
* Fixed sleeping villagers not waking up when moved around
* Added letter of condolence
* Fixed dimension issues with player and villager data
* Added mail system, used to notify the player about the death of family members
* Glass roofs are now supported
* Added more jobless skins
* Updated translations and fixed wrong variable syntax
* Added some admin commands
* Temporary disabled baby tracker
* You can now trade with family
* Fixed inventory duplication bug
* Fixed deadlock in relation with reaper spawner
* Villager marriages now respect player hearts
* Fixed gifting golden apple not reducing by 1
* Fixed crash when hovering over unmarried villagers marriage-symbol
* Villagers will also update baby time
* Fixed datapack crash on some system locales
* Hopefully fixed stuck-at-sleeping issues after loading world
* Adding a building will also look for graveyards to decrease player confusion

# 7.0.6

* Fixed guards aggression towards mobs
* Fixed profession change not always switching clothes
* Added Family Tree item to search
* Fixed crash
* Fixed reaper summoning on some server

# 7.0.5

* Fixed issue with natural breeding
* Blueprint will now better display vertically stacked buildings
* Villager preview in the editor is now animated
* Fixed wasting charges on already reviving villagers
* Fixed a crash
* Fixed opposite gender bug
* Fixed villager marrying relatives
* Guards now attack mca zombie villagers
* No more sliding baby zombie villagers
* Slightly enhanced village boundary determination
* Fixed uninitialized zombie villager babies
* Fixed flower pots with flowers not being recognized
* Lost babies can now be retrieved by the spouse
* Fixed crash on dedicated server when using randomized baby name
* Village will now interact with each other
* Iron golems will now slap the villager when hit accidentally and then chill
* Guards will now support their citizen and have a custom dialogue when the player is the attacker
* Improved archer AI
* Fixed villager getting stuck in doors
* Guards no longer panic when a raid happens
* A wiped-out village will only send a last, bigger bounty hunter wave
* Added all items to recipe book
* Reworked female villager model
* Fixed a bunch of marriage issues caused on death
* Spouse and parents can now be modified in the villager editor
* Fixed guard spam
* Rank Mayor can now make villagers guards or archers manually
* If the Grim Reaper summoning fails, feedback on why is given
* Villager are now silent by default, configurable
* Villages can now be renamed
* Unlocked King rank

# 7.0.4

* Fixed widow icon
* Player and villager marriage symbol now swapped
* Taxes are initially set to 0%
* Whistle recipe now requires gold instead of rose gold
* Rings are no longer usable as gold ingots
* Fixed a crash related to building detection
* Integrated community re-shaded dna icon
* Added Vegetarian trait
* Fixed missing meat gift phrase
* Replaced names by accurate database of babies born in the US in 2010
* Fixed graves text for formatted names
* Fixed reviving for villager died by height or void
* When adopting, your spouse also becomes your children's mother
* Decreased villager knockback
* Fixed incorrect amount of bounty hunters
* Added two more headstones
* Fixed crash caused by zombie villagers on dedicated servers
* Only player with merchant rank or higher will receive tax notifications
* mca-admin commands no require op permission
* Fixed smaller issues with building recognition
* Automatic building scanning can now be disabled
* Next to Buildings, you can now add more restrictive "rooms" instead in case your build is not recognized otherwise
* Buildings can no longer intersect
* If adding a building fails, a proper error message is now shown
* Updating existing, intersected buildings work now
* Fixed some villagers being confused on where they live
* Fixed outdated translation variables
* Setting the workplace makes them jobless for now, effectively causing them to look for a new job
* You use both matchmaker rings now
* Gifting cake works on every adult married villager
* Buildings can now be marked as restricted, preventing villagers from moving in
* Voice acting is now disabled by default
* Fixed guards on duty randomly looking into the sky when talking to
* Fixed at least one teleporting-away-while-following bug

# 7.0.3

* Attempting to talk to a zombie won't prevent you from performing an action
* Fixed interaction fatigue reset
* Added Interaction and gift analysis
* Overhauled gift desaturation.
    * Hearts reward will decrease, but won't drop below 0.
    * Desaturation uses a configurable exponential curve, slightly favoring awesome stuff.
    * Once a day by default, the villager forgets about the latest gift in the queue
* Fixed "datapack" crash
* Building tasks are now required to advance in ranks
* Removed bed reserving, beds are searched on demand
* Fixed villager-keep-following-you problem
* Fixed greeting AI
* Increase percentage of adult villagers
* Fixed changing clothes of unemployed villagers
* Increased frequency of marriage, births and guard spawns

# 7.0.2

* Fixed Server crash
* Fixed crash when setting clothes or haircut when playing on a server
* Added config flag to disable voice acting
* Fixed scythe loosing its charge on non-tombstones
* Fixed staff of life charges
* You can no longer adopt adults
* Fixed grown-up message appearing after world join
* Fixed building detection on certain coordinates
* Fixed tall villagers being too tall to live
* Fixed phrases not being translated on dedicated servers
* Synced Translations

# 7.0.1

* Fixed traits syncing issues and chance math
* Fixed translation keys

# 7.0.0

* Giant initial update. This list may have missing parts.
* Added mca villager and zombie villager
* Added genetics, personality, traits and mood
* Added dialogue engine
    * Ported classic interactions
    * Added adoption
    * Added divorce and divorce papers
* Added enhanced gifting
    * Has a saturation Queue
    * Respects villagers specific needs
* Added wedding ring and engagement ring
* Added Grim Reaper
* Added graves, resurrection, Staff of Life and the Scythe
* Added guards and archers
* Added blueprint
    * Added village management
    * Added automatic building and village recognition
    * Added initial building types to extend village functions
    * Added rank, task system
* Added taxes
* Added chores
* Added book with enhanced visuals
* Added Advancements
* Added Architecture to support Fabric and Forge
* Added voice acting
* Added initial translations´
