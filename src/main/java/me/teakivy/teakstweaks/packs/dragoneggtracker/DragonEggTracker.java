package me.teakivy.teakstweaks.packs.dragoneggtracker;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import me.teakivy.teakstweaks.packs.BasePack;
import me.teakivy.teakstweaks.utils.JsonManager;
import me.teakivy.teakstweaks.utils.log.Logger;
import me.teakivy.teakstweaks.utils.register.TTPack;
import me.teakivy.teakstweaks.utils.scheduler.RegionScanner;
import me.teakivy.teakstweaks.utils.scheduler.Scheduler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.ShulkerBox;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tracks dragon eggs and keeps a LuckPerms group on the players that own one.
 */
public class DragonEggTracker extends BasePack {

    private static final int MAX_NESTING_DEPTH = 3;
    private static final int MAX_REPORTED_RESULTS = 50;

    private static final Set<UUID> granted = ConcurrentHashMap.newKeySet();
    private static final Set<Location> placedEggs = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> pendingScans = ConcurrentHashMap.newKeySet();

    private static LuckPermsHook luckPerms;
    private static ScheduledTask scanTask;
    private static String groupName = "egg-holder";
    private static boolean removeWhenLost = true;
    private static boolean includeEnderChest = true;
    private static boolean includeNested = true;
    private static boolean scanContainers = true;
    private static boolean scanBlocks = true;
    private static int blockScanRadius = 3;

    public DragonEggTracker() {
        super(TTPack.DRAGON_EGG_TRACKER, Material.DRAGON_EGG);
    }

    @Override
    public void init() {
        super.init();

        ConfigurationSection config = getConfig();
        if (config == null) return;

        String configuredGroup = config.getString("group", "egg-holder");
        groupName = configuredGroup == null ? "egg-holder" : configuredGroup.toLowerCase(Locale.ROOT);

        luckPerms = null;
        if (!groupName.matches("[a-z0-9_-]+")) {
            Logger.warning(Component.text("Invalid LuckPerms group name '" + groupName + "' in the dragon-egg-tracker config. The group will not be managed."));
        } else if (TTPack.DRAGON_EGG_TRACKER.isAvailable()) {
            luckPerms = new LuckPermsHook(groupName);
            luckPerms.ensureGroup(config.getBoolean("create-group", true));
        }

        removeWhenLost = config.getBoolean("remove-group-when-lost", true);
        includeEnderChest = config.getBoolean("include-ender-chest", true);
        includeNested = config.getBoolean("include-nested-containers", true);
        scanContainers = config.getBoolean("scan-containers", true);
        scanBlocks = config.getBoolean("scan-blocks", true);
        blockScanRadius = Math.max(0, config.getInt("block-scan-radius", 3));

        loadState();

        for (Player player : Bukkit.getOnlinePlayers()) {
            requestScan(player);
        }

        int interval = Math.max(1, config.getInt("check-interval", 10));
        scanTask = Scheduler.runTimer(() -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                requestScan(player);
            }
        }, 20L * interval, 20L * interval);
    }

    @Override
    public void unregister() {
        super.unregister();
        Scheduler.cancel(scanTask);
        scanTask = null;
        saveState();
    }

    // ---------------------------------------------------------------------
    // Role management
    // ---------------------------------------------------------------------

    private static void requestScan(Player player) {
        if (player == null || !player.isOnline()) return;

        UUID uuid = player.getUniqueId();
        if (!pendingScans.add(uuid)) return;

        Scheduler.runFor(player, () -> {
            pendingScans.remove(uuid);
            scanPlayer(player, false);
        });
    }

    private static void scanPlayer(Player player, boolean force) {
        int eggs = countPlayerEggs(player).total();
        UUID uuid = player.getUniqueId();

        if (eggs > 0) {
            boolean changed = granted.add(uuid);
            if (changed || force) {
                if (!applyGroup(uuid, true)) {
                    // LuckPerms is not ready yet; retry on the next scan.
                    if (changed) granted.remove(uuid);
                } else if (changed) {
                    saveState();
                }
            }
            return;
        }

        if (!removeWhenLost) return;

        if (granted.remove(uuid)) {
            if (!applyGroup(uuid, false)) {
                granted.add(uuid);
            } else {
                saveState();
            }
        }
    }

    private static boolean applyGroup(UUID uuid, boolean give) {
        return luckPerms != null && luckPerms.apply(uuid, give);
    }

    // ---------------------------------------------------------------------
    // Inventory scanning
    // ---------------------------------------------------------------------

    private static PlayerEggCount countPlayerEggs(Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        int inventory = countInContents(contents, false, 0);
        int nested = includeNested
                ? countInContents(contents, true, 0) - inventory
                : 0;

        ItemStack cursor = player.getItemOnCursor();
        if (isEgg(cursor)) {
            inventory += cursor.getAmount();
        }

        int enderChest = 0;
        if (includeEnderChest) {
            ItemStack[] enderContents = player.getEnderChest().getContents();
            int direct = countInContents(enderContents, false, 0);
            enderChest = includeNested
                    ? countInContents(enderContents, true, 0)
                    : direct;
        }

        return new PlayerEggCount(inventory, nested, enderChest);
    }

    private static int countInContents(ItemStack[] contents, boolean nested, int depth) {
        if (contents == null) return 0;

        int count = 0;
        for (ItemStack stack : contents) {
            if (isEgg(stack)) {
                count += stack.getAmount();
                continue;
            }

            if (!nested || depth >= MAX_NESTING_DEPTH) continue;
            count += countNested(stack, depth);
        }
        return count;
    }

    private static int countNested(ItemStack stack, int depth) {
        if (stack == null || stack.getType() == Material.AIR) return 0;

        ItemMeta meta = stack.getItemMeta();
        if (meta instanceof BlockStateMeta blockStateMeta
                && blockStateMeta.getBlockState() instanceof ShulkerBox shulkerBox) {
            return countInContents(shulkerBox.getInventory().getContents(), true, depth + 1);
        }

        if (meta instanceof BundleMeta bundleMeta) {
            return countInContents(bundleMeta.getItems().toArray(new ItemStack[0]), true, depth + 1);
        }

        return 0;
    }

    private static boolean isEgg(ItemStack stack) {
        return stack != null && stack.getType() == Material.DRAGON_EGG && stack.getAmount() > 0;
    }

    // ---------------------------------------------------------------------
    // /trackegg scan
    // ---------------------------------------------------------------------

    /**
     * Scan online players, loaded containers, item entities, item frames and known placed eggs.
     */
    public static void scan(CommandSender sender) {
        new ScanSession(sender).start();
    }

    private static void scanPlayerForCommand(Player player, ScanSession session) {
        PlayerEggCount count = countPlayerEggs(player);

        if (count.inventory() > 0) {
            session.add(new EggLocation(Type.PLAYER_INVENTORY, player.getName(), null, count.inventory()));
        }
        if (count.nested() > 0) {
            session.add(new EggLocation(Type.PLAYER_NESTED, player.getName(), null, count.nested()));
        }
        if (count.enderChest() > 0) {
            session.add(new EggLocation(Type.PLAYER_ENDER_CHEST, player.getName(), null, count.enderChest()));
        }
    }

    private static void scanContainer(BlockState state, ScanSession session) {
        if (!(state instanceof InventoryHolder holder)) return;

        try {
            Inventory inventory = holder.getInventory();
            if (inventory == null || !session.visited.add(inventory)) return;

            int count = countInContents(inventory.getContents(), includeNested, 0);
            if (count > 0) {
                session.add(new EggLocation(Type.CONTAINER, formatMaterial(state.getType()), state.getLocation(), count));
            }
        } catch (RuntimeException ignored) {
            // Some inventory holders are not accessible from the region thread.
        }
    }

    private static void scanEntity(Entity entity, ScanSession session) {
        if (entity instanceof Player) return;

        if (entity instanceof Item item) {
            ItemStack stack = item.getItemStack();
            if (isEgg(stack)) {
                session.add(new EggLocation(Type.ITEM, null, item.getLocation(), stack.getAmount()));
            }
            return;
        }

        if (entity instanceof ItemFrame frame) {
            ItemStack stack = frame.getItem();
            if (isEgg(stack)) {
                session.add(new EggLocation(Type.ITEM_FRAME, null, frame.getLocation(), stack.getAmount()));
            }
            return;
        }

        if (!scanContainers) return;

        if (entity instanceof InventoryHolder holder) {
            try {
                Inventory inventory = holder.getInventory();
                if (inventory == null || !session.visited.add(inventory)) return;

                int count = countInContents(inventory.getContents(), includeNested, 0);
                if (count > 0) {
                    session.add(new EggLocation(Type.CONTAINER_ENTITY, formatEntity(entity), entity.getLocation(), count));
                }
            } catch (RuntimeException ignored) {
                // Ignore inventories that cannot be read.
            }
        }
    }

    private static void sendReport(CommandSender sender, List<EggLocation> results, int players, int chunks) {
        sender.sendMessage(prefix());

        if (results.isEmpty()) {
            sender.sendMessage(mm("<gray>No dragon eggs found in online player inventories, loaded containers, or loaded chunks."));
            sender.sendMessage(footer(players, chunks));
            return;
        }

        List<EggLocation> sorted = new ArrayList<>(results);
        sorted.sort(Comparator
                .comparing(EggLocation::type)
                .thenComparing(location -> location.detail() == null ? "" : location.detail())
                .thenComparing(location -> location.amount()));

        int limit = Math.min(sorted.size(), MAX_REPORTED_RESULTS);
        for (int i = 0; i < limit; i++) {
            sender.sendMessage(format(sorted.get(i)));
        }

        if (sorted.size() > limit) {
            sender.sendMessage(mm("<gray>... and <white>" + (sorted.size() - limit) + "</white> more."));
        }

        sender.sendMessage(footer(players, chunks));
    }

    private static Component format(EggLocation egg) {
        String amount = String.valueOf(egg.amount());
        String detail = egg.detail() == null ? "" : egg.detail();

        return switch (egg.type()) {
            case PLAYER_INVENTORY -> mm("<yellow>• <white><player></white> has <white><amount>x</white> dragon egg in their inventory",
                    Placeholder.unparsed("player", detail), Placeholder.unparsed("amount", amount));
            case PLAYER_NESTED -> mm("<yellow>• <white><player></white> has <white><amount>x</white> dragon egg inside a container item in their inventory",
                    Placeholder.unparsed("player", detail), Placeholder.unparsed("amount", amount));
            case PLAYER_ENDER_CHEST -> mm("<yellow>• <white><player></white> has <white><amount>x</white> dragon egg in their ender chest",
                    Placeholder.unparsed("player", detail), Placeholder.unparsed("amount", amount));
            case CONTAINER -> mm("<yellow>• <white><type></white> at <white><location></white> contains <white><amount>x</white> dragon egg",
                    Placeholder.unparsed("type", detail), Placeholder.unparsed("location", formatLocation(egg.location())), Placeholder.unparsed("amount", amount));
            case CONTAINER_ENTITY -> mm("<yellow>• <white><type></white> at <white><location></white> contains <white><amount>x</white> dragon egg",
                    Placeholder.unparsed("type", detail), Placeholder.unparsed("location", formatLocation(egg.location())), Placeholder.unparsed("amount", amount));
            case ITEM -> mm("<yellow>• Dropped item at <white><location></white>",
                    Placeholder.unparsed("location", formatLocation(egg.location())));
            case ITEM_FRAME -> mm("<yellow>• Item frame at <white><location></white>",
                    Placeholder.unparsed("location", formatLocation(egg.location())));
            case PLACED -> mm("<yellow>• Placed block at <white><location></white>",
                    Placeholder.unparsed("location", formatLocation(egg.location())));
        };
    }

    private static Component prefix() {
        return mm("<dark_gray>[<gold><bold>Egg Tracker</bold></gold>]</dark_gray>");
    }

    private static Component footer(int players, int chunks) {
        return mm("<dark_gray>Scanned <white><players></white> players and <white><chunks></white> loaded chunks. Group: <white><group></white>",
                Placeholder.unparsed("players", String.valueOf(players)),
                Placeholder.unparsed("chunks", String.valueOf(chunks)),
                Placeholder.unparsed("group", groupName));
    }

    private static String formatLocation(Location location) {
        if (location == null) return "unknown";
        String world = location.getWorld() == null ? "unknown" : location.getWorld().getName();
        return world + " " + location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ();
    }

    private static String formatMaterial(Material material) {
        String name = material.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    private static String formatEntity(Entity entity) {
        String name = entity.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    private static Component mm(String message, net.kyori.adventure.text.minimessage.tag.resolver.TagResolver... resolvers) {
        return MiniMessage.miniMessage().deserialize(message, resolvers);
    }

    // ---------------------------------------------------------------------
    // Placed egg tracking
    // ---------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        if (block.getType() != Material.DRAGON_EGG) return;

        placedEggs.add(block.getLocation());
        saveState();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.DRAGON_EGG) return;

        placedEggs.remove(block.getLocation());
        saveState();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        removePlacedEggs(event.blockList());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        removePlacedEggs(event.blockList());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        removeMovedEggs(event.getBlocks(), event.getBlock().getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        removeMovedEggs(event.getBlocks(), event.getBlock().getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.LEFT_CLICK_BLOCK) return;

        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.DRAGON_EGG) return;

        // A dragon egg teleports when hit. The new position is found on the next tick.
        placedEggs.remove(block.getLocation());
        Location center = block.getLocation();
        Scheduler.runAtLater(center, () -> findNearbyEgg(center), 1L);
        saveState();
    }

    private static void removePlacedEggs(List<Block> blocks) {
        boolean changed = false;
        for (Block block : blocks) {
            if (block.getType() != Material.DRAGON_EGG) continue;
            changed |= placedEggs.remove(block.getLocation());
        }
        if (changed) saveState();
    }

    private static void removeMovedEggs(List<Block> blocks, Location center) {
        boolean changed = false;
        for (Block block : blocks) {
            if (block.getType() != Material.DRAGON_EGG) continue;
            changed |= placedEggs.remove(block.getLocation());
        }
        if (changed) {
            Scheduler.runAtLater(center, () -> findNearbyEgg(center), 1L);
            saveState();
        }
    }

    private static void findNearbyEgg(Location center) {
        World world = center.getWorld();
        if (world == null) return;

        int centerX = center.getBlockX();
        int centerY = center.getBlockY();
        int centerZ = center.getBlockZ();
        int minY = Math.max(world.getMinHeight(), centerY - 8);
        int maxY = Math.min(world.getMaxHeight() - 1, centerY + 8);

        for (int x = centerX - 16; x <= centerX + 16; x++) {
            for (int z = centerZ - 16; z <= centerZ + 16; z++) {
                for (int y = minY; y <= maxY; y++) {
                    if (world.getBlockAt(x, y, z).getType() == Material.DRAGON_EGG) {
                        placedEggs.add(new Location(world, x, y, z));
                    }
                }
            }
        }

        saveState();
    }

    // ---------------------------------------------------------------------
    // Player events
    // ---------------------------------------------------------------------

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // Force a check on join so a stale group is removed and a group that was manually removed
        // from a player who still owns an egg is restored.
        Scheduler.runFor(player, () -> scanPlayer(player, true));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        pendingScans.remove(player.getUniqueId());
        scanPlayer(player, false);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        requestScan(event.getPlayer());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        Scheduler.runForLater(player, () -> requestScan(player), 1L);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        requestScan(event.getPlayer());
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            requestScan(player);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            requestScan(player);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            requestScan(player);
        }
    }

    @EventHandler
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        requestScan(event.getPlayer());
    }

    @EventHandler
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        requestScan(event.getPlayer());
    }

    // ---------------------------------------------------------------------
    // Persistence
    // ---------------------------------------------------------------------

    private static void loadState() {
        LinkedHashMap<String, Object> json;
        try {
            json = JsonManager.getFromFile("data/dragon_egg_tracker.json");
        } catch (RuntimeException exception) {
            Logger.warning(Component.text("Failed to read data/dragon_egg_tracker.json: " + exception.getMessage()));
            return;
        }
        if (json == null) return;

        Object grantedObject = json.get("granted");
        if (grantedObject instanceof List<?> list) {
            for (Object entry : list) {
                try {
                    granted.add(UUID.fromString(String.valueOf(entry)));
                } catch (IllegalArgumentException ignored) {
                    // Skip malformed entries.
                }
            }
        }

        Object placedObject = json.get("placed");
        if (placedObject instanceof List<?> list) {
            for (Object entry : list) {
                Location location = deserializeLocation(String.valueOf(entry));
                if (location != null) {
                    placedEggs.add(location);
                }
            }
        }
    }

    private static void saveState() {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("granted", granted.stream().map(UUID::toString).toList());
        json.put("placed", placedEggs.stream().map(DragonEggTracker::serializeLocation).toList());
        JsonManager.saveToFile(new LinkedHashMap<>(json), "data/dragon_egg_tracker.json");
    }

    private static String serializeLocation(Location location) {
        String world = location.getWorld() == null ? "" : location.getWorld().getName();
        return world + ";" + location.getBlockX() + ";" + location.getBlockY() + ";" + location.getBlockZ();
    }

    private static Location deserializeLocation(String serialized) {
        String[] parts = serialized.split(";");
        if (parts.length != 4) return null;

        World world = Bukkit.getWorld(parts[0]);
        if (world == null) return null;

        try {
            return new Location(world,
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]),
                    Integer.parseInt(parts[3]));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    // ---------------------------------------------------------------------
    // Data types
    // ---------------------------------------------------------------------

    private record PlayerEggCount(int inventory, int nested, int enderChest) {
        int total() {
            return inventory + nested + enderChest;
        }
    }

    private record EggLocation(Type type, String detail, Location location, int amount) {
    }

    private enum Type {
        PLAYER_INVENTORY,
        PLAYER_NESTED,
        PLAYER_ENDER_CHEST,
        CONTAINER,
        CONTAINER_ENTITY,
        ITEM,
        ITEM_FRAME,
        PLACED
    }

    private static final class ScanSession {
        private final CommandSender sender;
        private final List<EggLocation> results = new CopyOnWriteArrayList<>();
        private final Set<Inventory> visited = ConcurrentHashMap.newKeySet();
        private final Set<Location> placedLocations = ConcurrentHashMap.newKeySet();
        private final AtomicInteger pending = new AtomicInteger();
        private final AtomicBoolean started = new AtomicBoolean(false);
        private final AtomicBoolean reported = new AtomicBoolean(false);
        private final int players;
        private final int loadedChunks;

        private ScanSession(CommandSender sender) {
            this.sender = sender;
            this.players = Bukkit.getOnlinePlayers().size();

            int chunks = 0;
            for (World world : Bukkit.getWorlds()) {
                chunks += world.getLoadedChunks().length;
            }
            this.loadedChunks = chunks;
        }

        private void start() {
            sender.sendMessage(prefix().append(Component.text(" Scanning online players and loaded containers...", NamedTextColor.GRAY)));

            for (Player player : Bukkit.getOnlinePlayers()) {
                task();
                Scheduler.runFor(player, () -> {
                    try {
                        scanPlayerForCommand(player, this);
                    } finally {
                        done();
                    }
                }, this::done);
            }

            for (World world : Bukkit.getWorlds()) {
                if (scanContainers) {
                    task();
                    RegionScanner.forEachBlockState(world, state -> scanContainer(state, this), this::done);
                }

                task();
                RegionScanner.forEachEntity(world, entity -> scanEntity(entity, this), this::done);
            }

            if (scanBlocks) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    task();
                    RegionScanner.forEachBlock(player.getWorld(), player.getLocation(), blockScanRadius, Material.DRAGON_EGG,
                            location -> {
                                placedEggs.add(location.getBlock().getLocation());
                                addPlaced(location);
                            },
                            () -> {
                                saveState();
                                done();
                            });
                }
            }

            for (Location location : List.copyOf(placedEggs)) {
                if (!isLoaded(location)) continue;

                task();
                Scheduler.runAt(location, () -> {
                    try {
                        if (location.getBlock().getType() == Material.DRAGON_EGG) {
                            addPlaced(location);
                        } else {
                            placedEggs.remove(location);
                        }
                    } finally {
                        done();
                    }
                });
            }

            started.set(true);
            if (pending.get() == 0) report();
        }

        private void task() {
            pending.incrementAndGet();
        }

        private void done() {
            if (pending.decrementAndGet() == 0 && started.get()) {
                report();
            }
        }

        private void add(EggLocation location) {
            results.add(location);
        }

        private void addPlaced(Location location) {
            Location blockLocation = location.getBlock().getLocation();
            if (!placedLocations.add(blockLocation)) return;
            add(new EggLocation(Type.PLACED, null, blockLocation, 1));
        }

        private void report() {
            if (!reported.compareAndSet(false, true)) return;
            Scheduler.run(() -> sendReport(sender, results, players, loadedChunks));
        }

        private static boolean isLoaded(Location location) {
            World world = location.getWorld();
            return world != null && world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4);
        }
    }
}
