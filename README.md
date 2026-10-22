
# Teak's Tweaks Plugin
### [Previously known as Vanilla Tweaks]

Datapacks can sometimes cause lag, especially when running loops, or constant checks. So
how can this be solved? Well.. by using a Plugin! Spigot plugins can be optimised to run
not only faster, but more lag efficient.  
  
The Vanilla Tweaks Plugin project was started to reproduce all Vanilla Tweaks datapacks 
& crafting tweaks exactly, into one modular Spigot Plugin. Using a plugin can also allow
for more customizable, and easy to use interfaces.

## How to Use
Every Vanilla Tweaks Datapack & Crafting Tweak in the Plugin can be enabled inside
`config.yml`.  

### Folia Support
This build targets **Folia 26.2** (Minecraft 26.2). The plugin declares `folia-supported: true` and
uses Folia's region, entity, global-region, and async schedulers, so it is safe to run on a
regionised server.

A few features rely on Bukkit's scoreboard API, which Folia does not implement. Those features
degrade gracefully:

* **Count Mob Deaths**, **Track Statistics**, and **Track Raw Statistics** are disabled on Folia
  (a warning is printed on startup) because Folia does not allow creating scoreboard objectives.
* **AFK Display** uses the player list name instead of a scoreboard team.
* **Spawning Spheres** still works, but the sphere markers cannot be colored with a team color.

Use `./gradlew runFolia` to start a local Folia 26.2 server for testing.

To Enable a datapack, find it in the `packs:` section, and change `enabled: false` to `enabled: true`.  
After marking the tweak as true, restart the server to reload the packs. *(remember to save the file!)*
#### Example:
```yml
packs:
    # More Mob Heads
    # Adds a chance to receive a mob head upon killing it.
    more-mob-heads:
        enabled: true
```

Some Packs have configurable options, these could be native to vanilla tweaks, or added for the plugin, to change these just change the value in the proper config section.

#### Example:
```yml
packs:
      # Player Head Drops
    # A player will drop their head when killed by another player. The item displays who the killer is.
    player-head-drops:
        enabled: true
        # Whether to display who killed the player in the items lore (default: true)
        display-killer: true
```

### Crafting Tweaks
To Enable a crafting tweak, find it in the `crafting-tweaks:` section of `config.yml`, and change `enabled:` to `true`.  
After marking the tweak as true, a server restart is required to reload the recipes. *(remember to save the file!)*

*Note: If a tweak is disabled after being previously enabled, you may get an "Unknown Recipe" Error in the server console. Disregard this, it is just a side effect of modifying recipes.*

#### Example:
```yml
crafting-tweaks:
    # Universal Dyeing
    # Allows you to dye any dyeable block to another color, no matter what color it is (does not include Concrete).
    universal-dyeing:
        enabled: true
```
 

## Credits

All Plugin Development is by ([@teakivy](https://www.github.com/teakivy))

Original Concept:  
**Vanilla Tweaks:** https://vanillatweaks.net/
## Support

For Plugin support, Join my discord ([dsc.gg/teakivy](https://discord.com/invite/paf2fmMyHX)) and ask for help in `#❓︱support`
