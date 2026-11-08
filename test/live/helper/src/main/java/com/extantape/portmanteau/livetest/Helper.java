package com.extantape.portmanteau.livetest;

import com.extantape.portmanteau.sorting.api.PortmanteauSortedEvent;
import com.extantape.portmanteau.sorting.api.SortingApi;
import com.extantape.portmanteau.trunk.TrunkStore;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.BundleContents;
import io.papermc.paper.datacomponent.item.ItemContainerContents;
import io.papermc.paper.datacomponent.item.ItemLore;
import io.papermc.paper.datacomponent.item.PotionContents;
import io.papermc.paper.datacomponent.item.WrittenBookContent;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.block.data.type.Chest;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ChestBoat;
import org.bukkit.entity.ChestedHorse;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.minecart.StorageMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredListener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionType;
import org.jetbrains.annotations.NotNull;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Test-only plugin for the live tests. Never install it on a real server. One console command, {@code pmt <id> <what>
 * ...}, answers with a log line {@code PMT-RESULT <id> <json>} that the test script waits for.
 */
public final class Helper extends JavaPlugin implements Listener {
    private final AtomicInteger sortedEvents = new AtomicInteger();
    private final Map<String, Inventory> guis = new HashMap<>();

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, e -> e.registrar().register(
                Commands.literal("pmt").requires(s -> !(s.getSender() instanceof Player))
                        .then(Commands.argument("args", StringArgumentType.greedyString()).executes(ctx -> {
                            String[] a = StringArgumentType.getString(ctx, "args").split(" ");
                            String id = a[0];
                            String json;
                            try {
                                json = run(a);
                            } catch (Exception ex) {
                                json = obj("error", ex.toString());
                                getLogger().log(java.util.logging.Level.WARNING, "pmt " + String.join(" ", a) + " failed", ex);
                            }
                            getLogger().info("PMT-RESULT " + id + " " + json);
                            return Command.SINGLE_SUCCESS;
                        })).build(), "Portmanteau live-test helper", List.of()));
        getLogger().info("PMT-READY");
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
    public void onClickLog(org.bukkit.event.inventory.InventoryClickEvent e) {
        if (Boolean.getBoolean("pmt.logclicks")) getLogger().info("CLICK " + e.getClick() + " action=" + e.getAction() + " slot=" + e.getRawSlot()
                + " current=" + e.getCurrentItem() + " cursor=" + e.getCursor().getType());
    }

    @EventHandler
    public void onSorted(PortmanteauSortedEvent e) {
        sortedEvents.incrementAndGet();
    }

    private String run(String[] a) throws Exception {
        String what = a[1];
        return switch (what) {
            case "ping" -> obj("ok", true);
            case "world" -> {
                World w = Bukkit.getWorld(a[2]);
                if (w == null) w = new WorldCreator(a[2]).type(WorldType.FLAT).generateStructures(false).createWorld();
                w.setSpawnLocation(0, -60, 0);
                w.setGameRule(org.bukkit.GameRules.ADVANCE_TIME, false);
                w.setGameRule(org.bukkit.GameRules.SPAWN_MOBS, false);
                yield obj("world", w.getName());
            }
            case "place" -> {
                Block b = block(a, 2);
                b.setType(Material.valueOf(a[6]), false);
                yield obj("ok", true);
            }
            case "doublechest" -> {
                Block left = block(a, 2), right = left.getRelative(BlockFace.EAST);
                for (Block b : List.of(left, right)) b.setType(Material.CHEST, false);
                Chest l = (Chest) left.getBlockData(), r = (Chest) right.getBlockData();
                l.setFacing(BlockFace.SOUTH); r.setFacing(BlockFace.SOUTH);
                l.setType(Chest.Type.LEFT); r.setType(Chest.Type.RIGHT);
                left.setBlockData(l, false); right.setBlockData(r, false);
                yield obj("size", ((Container) left.getState(false)).getInventory().getSize());
            }
            case "fill" -> {
                Inventory inv = inventoryAt(a, 2);
                fill(inv, new Random(Long.parseLong(a[6])), Integer.parseInt(a[7]), a.length > 8 && a[8].equals("overstack"), firstStorage(inv));
                int over = 0;
                for (ItemStack i : inv.getContents()) if (i != null && !i.isEmpty() && i.getAmount() > i.getMaxStackSize()) over++;
                String l = ledger(inv.getContents());
                yield l.substring(0, l.length() - 1) + ",\"over\":" + over + "}";
            }
            case "fillplayer" -> {
                Player p = player(a[2]);
                ItemStack[] s = new ItemStack[36];
                Random r = new Random(Long.parseLong(a[3]));
                int n = Integer.parseInt(a[4]);
                for (int i = 0; i < n; i++) s[r.nextInt(36)] = randomItem(r, false);
                p.getInventory().setStorageContents(s);
                yield ledger(p.getInventory().getStorageContents());
            }
            case "fillentity" -> {
                Inventory inv = entityInventory(a[2]);
                fill(inv, new Random(Long.parseLong(a[3])), Integer.parseInt(a[4]), false, firstStorage(inv));
                yield ledger(inv.getContents());
            }
            case "ledger" -> switch (a[2]) {
                case "block" -> ledger(inventoryAt(a, 3).getContents());
                case "player" -> {
                    Player p = player(a[3]);
                    List<ItemStack> all = new ArrayList<>(java.util.Arrays.asList(p.getInventory().getContents()));
                    all.add(p.getItemOnCursor());
                    yield ledger(all.toArray(ItemStack[]::new));
                }
                case "storage" -> ledger(player(a[3]).getInventory().getStorageContents());
                case "hotbar" -> ledger(java.util.Arrays.copyOf(player(a[3]).getInventory().getStorageContents(), 9));
                case "ender" -> ledger(player(a[3]).getEnderChest().getContents());
                case "entity" -> ledger(entityInventory(a[3]).getContents());
                case "gui" -> ledger(guis.get(a[3]).getContents());
                case "trunk" -> {
                    var loaded = new TrunkStore(portmanteau()).load(player(a[3]), a[4]);
                    yield loaded instanceof TrunkStore.Contents c ? ledger(c.items()) : obj("corrupt", ((TrunkStore.Corrupt) loaded).reason());
                }
                case "open" -> ledger(player(a[3]).getOpenInventory().getTopInventory().getContents());
                default -> throw new IllegalArgumentException(a[2]);
            };
            case "sorted" -> {
                // Whether a sort would change anything: sorts a copy through the API.
                Inventory src = switch (a[2]) {
                    case "block" -> inventoryAt(a, 3);
                    case "entity" -> entityInventory(a[3]);
                    default -> throw new IllegalArgumentException(a[2]);
                };
                ItemStack[] c = src.getContents();
                int skip = src.getHolder(false) instanceof ChestedHorse ? 2 : 0;
                Inventory copy = Bukkit.createInventory(null, Math.max(9, ((c.length - skip + 8) / 9) * 9));
                ItemStack[] body = new ItemStack[copy.getSize()];
                for (int i = skip; i < c.length; i++) body[i - skip] = c[i] == null ? null : c[i].clone();
                copy.setContents(body);
                yield obj("result", SortingApi.sort(copy, null).name());
            }
            case "events" -> obj("sorted", sortedEvents.get());
            case "fuzz" -> fuzz(Integer.parseInt(a[2]), Long.parseLong(a[3]));
            case "allitems" -> allItems();
            case "trunkfuzz" -> trunkFuzz(player(a[2]), Integer.parseInt(a[3]), Long.parseLong(a[4]));
            case "trunkbytes" -> {
                PersistentDataContainer entry = trunkEntry(player(a[2]), a[3]);
                byte[] bytes = entry == null ? null : entry.get(new NamespacedKey(portmanteau(), "items"), PersistentDataType.BYTE_ARRAY);
                yield obj("sha", bytes == null ? "none" : sha(bytes));
            }
            case "corrupt" -> {
                Player p = player(a[2]);
                NamespacedKey root = new NamespacedKey(portmanteau(), "trunk");
                PersistentDataContainer all = p.getPersistentDataContainer().get(root, PersistentDataType.TAG_CONTAINER);
                int done = 0;
                for (NamespacedKey k : all == null ? java.util.Set.<NamespacedKey>of() : all.getKeys()) {
                    PersistentDataContainer entry = all.get(k, PersistentDataType.TAG_CONTAINER);
                    if (!a[3].equals(entry.get(new NamespacedKey(portmanteau(), "scope"), PersistentDataType.STRING))) continue;
                    NamespacedKey ik = new NamespacedKey(portmanteau(), "items");
                    byte[] b = entry.get(ik, PersistentDataType.BYTE_ARRAY);
                    b[b.length / 2] ^= 0x5A;
                    entry.set(ik, PersistentDataType.BYTE_ARRAY, b);
                    all.set(k, PersistentDataType.TAG_CONTAINER, entry);
                    done++;
                }
                if (all != null) p.getPersistentDataContainer().set(root, PersistentDataType.TAG_CONTAINER, all);
                yield obj("corrupted", done);
            }
            case "gui" -> {
                Player p = player(a[2]);
                Inventory inv = switch (a[3]) {
                    case "customholder" -> Bukkit.createInventory(new MenuHolder(), 27, Component.text("Stand-in menu"));
                    case "noholder" -> Bukkit.createInventory(null, 27, Component.text("Stand-in, no holder"));
                    case "chesttype" -> Bukkit.createInventory(null, InventoryType.CHEST, Component.text("Stand-in chest type"));
                    case "grave" -> Bukkit.createInventory(new GraveHolder(), 54, Component.text("Stand-in grave"));
                    case "preview" -> Bukkit.createInventory(p, 36, Component.text("Stand-in inventory preview"));
                    case "shulkeritem" -> Bukkit.createInventory(new MenuHolder(), InventoryType.SHULKER_BOX, Component.text("Shulker from item"));
                    default -> throw new IllegalArgumentException(a[3]);
                };
                fill(inv, new Random(a[3].hashCode()), Math.min(20, inv.getSize()), false, 0);
                guis.put(a[3], inv);
                p.openInventory(inv);
                yield ledger(inv.getContents());
            }
            case "spawn" -> {
                World w = Bukkit.getWorld(a[2]);
                Location at = new Location(w, Double.parseDouble(a[3]) + 0.5, Double.parseDouble(a[4]), Double.parseDouble(a[5]) + 0.5);
                Entity e = switch (a[6]) {
                    case "donkey" -> w.spawnEntity(at, EntityType.DONKEY);
                    case "mule" -> w.spawnEntity(at, EntityType.MULE);
                    case "llama" -> w.spawnEntity(at, EntityType.LLAMA);
                    case "chestboat" -> w.spawnEntity(at, EntityType.OAK_CHEST_BOAT);
                    case "chestminecart" -> w.spawnEntity(at, EntityType.CHEST_MINECART);
                    default -> throw new IllegalArgumentException(a[6]);
                };
                if (e instanceof ChestedHorse h) {
                    h.setTamed(true);
                    h.setCarryingChest(true);
                    h.setAI(false);
                    if (h instanceof org.bukkit.entity.Llama l) l.setStrength(5);
                }
                yield obj("uuid", e.getUniqueId().toString());
            }
            case "openentity" -> {
                Player p = player(a[2]);
                p.openInventory(entityInventory(a[3]));
                yield obj("ok", true);
            }
            case "openblock" -> {
                Player p = player(a[2]);
                p.openInventory(inventoryAt(a, 3));
                yield obj("ok", true);
            }
            case "openender" -> {
                Player p = player(a[2]);
                p.openInventory(p.getEnderChest());
                yield obj("ok", true);
            }
            case "fillender" -> {
                Player p = player(a[2]);
                fill(p.getEnderChest(), new Random(Long.parseLong(a[3])), Integer.parseInt(a[4]), false, 0);
                yield ledger(p.getEnderChest().getContents());
            }
            case "interact" -> {
                // The event Paper fires when a player right-clicks a block, for servers where the bots' clicks don't arrive.
                Player p = player(a[2]);
                Block b = block(a, 3);
                var e = new org.bukkit.event.player.PlayerInteractEvent(p, org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK,
                        p.getInventory().getItemInMainHand(), b, BlockFace.UP, org.bukkit.inventory.EquipmentSlot.HAND);
                Bukkit.getPluginManager().callEvent(e);
                yield obj("block", e.useInteractedBlock().name(), "item", e.useItemInHand().name());
            }
            case "placetrunk" -> {
                // Places a trunk the way a player does: the block goes down, then Paper's place event fires with the
                // trunk item in hand, so Portmanteau's own listener marks it.
                Player p = player(a[2]);
                Block b = block(a, 3);
                ItemStack trunk = giveViaCommand("trunk", p);
                var replaced = b.getState();
                b.setType(Material.LODESTONE, false);
                var e = new org.bukkit.event.block.BlockPlaceEvent(b, replaced, b.getRelative(BlockFace.DOWN), trunk, p, true,
                        org.bukkit.inventory.EquipmentSlot.HAND);
                Bukkit.getPluginManager().callEvent(e);
                if (e.isCancelled()) b.setBlockData(replaced.getBlockData(), false);
                yield obj("placed", !e.isCancelled());
            }
            case "claim" -> Claims.claim(player(a[2]), Bukkit.getWorld(a[3]), Integer.parseInt(a[4]), Integer.parseInt(a[5]),
                    Integer.parseInt(a[6]), Integer.parseInt(a[7]));
            case "trust" -> Claims.trust(Bukkit.getWorld(a[2]), Integer.parseInt(a[3]), Integer.parseInt(a[4]), player(a[5]));
            case "ignoreclaims" -> Claims.ignore(player(a[2]), Boolean.parseBoolean(a[3]));
            case "uuid" -> obj("uuid", player(a[2]).getUniqueId().toString());
            case "workbench" -> {
                player(a[2]).openWorkbench(null, true);
                yield obj("ok", true);
            }
            case "close" -> {
                player(a[2]).closeInventory();
                yield obj("ok", true);
            }
            case "top" -> {
                Inventory top = player(a[2]).getOpenInventory().getTopInventory();
                InventoryHolder h = top.getHolder(false);
                yield obj("type", top.getType().name(), "holder", h == null ? "none" : h.getClass().getName(), "size", top.getSize());
            }
            case "tp" -> {
                Player p = player(a[2]);
                p.teleport(new Location(Bukkit.getWorld(a[3]), Double.parseDouble(a[4]) + 0.5, Double.parseDouble(a[5]), Double.parseDouble(a[6]) + 0.5));
                yield obj("world", p.getWorld().getName());
            }
            case "where" -> {
                Player p = player(a[2]);
                yield obj("world", p.getWorld().getName(), "x", p.getLocation().getBlockX(), "y", p.getLocation().getBlockY(), "z", p.getLocation().getBlockZ());
            }
            case "kill" -> {
                Player p = player(a[2]);
                p.setHealth(0);
                yield obj("ok", true);
            }
            case "gamemode" -> {
                player(a[2]).setGameMode(GameMode.valueOf(a[3].toUpperCase()));
                yield obj("ok", true);
            }
            case "give" -> {
                Player p = player(a[2]);
                p.getInventory().addItem(new ItemStack(Material.valueOf(a[3]), Integer.parseInt(a[4])));
                yield obj("ok", true);
            }
            case "setslot" -> {
                Player p = player(a[2]);
                int slot = Integer.parseInt(a[3]);
                p.getInventory().setItem(slot, a[4].equals("AIR") ? null : new ItemStack(Material.valueOf(a[4]), Integer.parseInt(a[5])));
                yield obj("ok", true);
            }
            case "clear" -> {
                Player p = player(a[2]);
                p.getInventory().clear();
                p.setItemOnCursor(null);
                yield obj("ok", true);
            }
            case "slots" -> {
                Player p = player(a[2]);
                Inventory inv = a[3].equals("top") ? p.getOpenInventory().getTopInventory() : p.getInventory();
                List<String> out = new ArrayList<>();
                for (ItemStack i : inv.getContents()) out.add(i == null || i.isEmpty() ? "-" : i.getType().getKey().getKey() + "x" + i.getAmount());
                yield "{\"slots\":[" + String.join(",", out.stream().map(s -> "\"" + s + "\"").toList()) + "]}";
            }
            case "craft" -> {
                Player p = player(a[2]);
                ItemStack[] matrix = new ItemStack[9];
                String[] spec = a[3].split(",");
                for (int i = 0; i < 9; i++) matrix[i] = craftItem(spec[i]);
                ItemStack result = Bukkit.craftItem(matrix, p.getWorld(), p);
                String kind = result == null || result.isEmpty() ? "nothing" : kind(result);
                yield obj("result", kind, "type", result == null ? "AIR" : result.getType().name());
            }
            case "holds" -> {
                // How many of our items (key or trunk) a player has, by persistent data, and how many plain look-alikes.
                Player p = player(a[2]);
                int keys = 0, trunks = 0;
                for (ItemStack i : p.getInventory().getContents()) {
                    String k = kind(i);
                    if ("multiversal_key".equals(k)) keys += i.getAmount();
                    if ("multiversal_trunk".equals(k)) trunks += i.getAmount();
                }
                yield obj("keys", keys, "trunks", trunks);
            }
            case "drops" -> {
                // Items lying near a block: what breaking it dropped.
                Block b = block(a, 2);
                Map<String, Integer> found = new TreeMap<>();
                for (Entity e : b.getWorld().getNearbyEntities(b.getLocation().add(0.5, 0.5, 0.5), 3, 3, 3)) {
                    if (e instanceof org.bukkit.entity.Item it) {
                        String k = kind(it.getItemStack());
                        found.merge(k != null ? k : it.getItemStack().getType().getKey().getKey(), it.getItemStack().getAmount(), Integer::sum);
                        if (a.length > 6 && a[6].equals("clear")) e.remove();
                    }
                }
                yield json(found);
            }
            case "blocktype" -> obj("type", block(a, 2).getType().name());
            case "displays" -> {
                Block b = block(a, 2);
                long n = b.getWorld().getNearbyEntities(b.getLocation().add(0.5, 0.5, 0.5), 1, 1, 1).stream().filter(e -> e instanceof ItemDisplay).count();
                yield obj("displays", n);
            }
            case "explode" -> {
                Block b = block(a, 2);
                b.getWorld().createExplosion(b.getLocation().add(0.5, 0.5, 0.5), Float.parseFloat(a[6]), false, true);
                yield obj("ok", true);
            }
            case "breakas" -> {
                Player p = player(a[2]);
                Block b = block(a, 3);
                yield obj("broken", p.breakBlock(b));
            }
            case "power" -> obj("power", block(a, 2).getBlockPower());
            case "saveplayer" -> {
                player(a[2]).saveData();
                yield obj("ok", true);
            }
            case "fault" -> {
                System.setProperty(a[2], a[3]);
                yield obj("ok", true);
            }
            case "tps" -> obj("tps", Bukkit.getTPS()[0], "mspt", Bukkit.getAverageTickTime());
            case "features" -> features();
            case "pdc" -> {
                Player p = player(a[2]);
                List<String> keys = p.getPersistentDataContainer().getKeys().stream().map(NamespacedKey::toString).sorted().toList();
                NamespacedKey root = new NamespacedKey("portmanteau", "trunk");
                PersistentDataContainer all = p.getPersistentDataContainer().get(root, PersistentDataType.TAG_CONTAINER);
                List<String> scopes = new ArrayList<>();
                if (all != null) for (NamespacedKey k : all.getKeys()) {
                    PersistentDataContainer e = all.get(k, PersistentDataType.TAG_CONTAINER);
                    scopes.add(e.get(new NamespacedKey("portmanteau", "scope"), PersistentDataType.STRING));
                }
                yield "{\"keys\":" + list(keys) + ",\"scopes\":" + list(scopes) + "}";
            }
            case "cursor" -> {
                ItemStack c = player(a[2]).getItemOnCursor();
                yield obj("cursor", c.isEmpty() ? "-" : c.getType().getKey().getKey() + "x" + c.getAmount());
            }
            case "op" -> {
                player(a[2]).setOp(Boolean.parseBoolean(a[3]));
                yield obj("ok", true);
            }
            default -> throw new IllegalArgumentException("unknown " + what);
        };
    }

    // --- items ---

    private static final Material[] PLAIN = {Material.STONE, Material.OAK_LOG, Material.RED_WOOL, Material.WHITE_WOOL, Material.DIRT,
            Material.COBBLESTONE, Material.BREAD, Material.APPLE, Material.REDSTONE, Material.ARROW, Material.ENDER_PEARL, Material.EGG,
            Material.IRON_INGOT, Material.DIAMOND, Material.OAK_PLANKS, Material.GLASS, Material.TORCH, Material.SNOWBALL, Material.HONEY_BOTTLE};
    private static final Material[] GEAR = {Material.DIAMOND_SWORD, Material.IRON_PICKAXE, Material.BOW, Material.NETHERITE_CHESTPLATE,
            Material.WOODEN_AXE, Material.SHIELD, Material.ELYTRA, Material.FISHING_ROD, Material.TRIDENT, Material.MACE};

    static ItemStack randomItem(Random r, boolean overstack) {
        int kind = r.nextInt(12);
        ItemStack item;
        switch (kind) {
            case 0, 1, 2, 3 -> {
                Material m = PLAIN[r.nextInt(PLAIN.length)];
                item = new ItemStack(m, 1 + r.nextInt(new ItemStack(m).getMaxStackSize()));
            }
            case 4 -> {
                item = new ItemStack(GEAR[r.nextInt(GEAR.length)]);
                if (r.nextBoolean()) item.addUnsafeEnchantment(Enchantment.UNBREAKING, 1 + r.nextInt(3));
                if (r.nextBoolean() && item.getType().getMaxDurability() > 0) item.setData(DataComponentTypes.DAMAGE, r.nextInt(item.getType().getMaxDurability()));
            }
            case 5 -> {
                item = new ItemStack(PLAIN[r.nextInt(PLAIN.length)], 1 + r.nextInt(8));
                item.setData(DataComponentTypes.CUSTOM_NAME, Component.text("Named " + r.nextInt(4)));
            }
            case 6 -> {
                item = new ItemStack(PLAIN[r.nextInt(PLAIN.length)], 1 + r.nextInt(8));
                item.setData(DataComponentTypes.LORE, ItemLore.lore(List.of(Component.text("Lore " + r.nextInt(3)))));
            }
            case 7 -> {
                Material m = new Material[]{Material.POTION, Material.SPLASH_POTION, Material.LINGERING_POTION}[r.nextInt(3)];
                item = new ItemStack(m);
                PotionType t = new PotionType[]{PotionType.SWIFTNESS, PotionType.HEALING, PotionType.NIGHT_VISION, PotionType.WATER}[r.nextInt(4)];
                item.setData(DataComponentTypes.POTION_CONTENTS, PotionContents.potionContents().potion(t).build());
            }
            case 8 -> {
                item = new ItemStack(Material.WRITTEN_BOOK);
                item.setData(DataComponentTypes.WRITTEN_BOOK_CONTENT, WrittenBookContent.writtenBookContent("Book " + r.nextInt(3), "Tester")
                        .addPage(Component.text("Page " + r.nextInt(100))).build());
            }
            case 9 -> {
                item = new ItemStack(r.nextBoolean() ? Material.SHULKER_BOX : Material.RED_SHULKER_BOX);
                List<ItemStack> inside = new ArrayList<>();
                for (int i = 0; i < 1 + r.nextInt(5); i++) inside.add(new ItemStack(PLAIN[r.nextInt(PLAIN.length)], 1 + r.nextInt(16)));
                item.setData(DataComponentTypes.CONTAINER, ItemContainerContents.containerContents(inside));
            }
            case 10 -> {
                item = new ItemStack(Material.BUNDLE);
                item.setData(DataComponentTypes.BUNDLE_CONTENTS, BundleContents.bundleContents(List.of(new ItemStack(Material.STONE, 1 + r.nextInt(16)))));
            }
            default -> {
                Material m = PLAIN[r.nextInt(PLAIN.length)];
                item = new ItemStack(m, 1 + r.nextInt(5));
                item.addUnsafeEnchantment(Enchantment.MENDING, 1);
            }
        }
        if (overstack && r.nextInt(30) == 0) {
            // An illegal stack, as another plugin might leave: more than the item's own limit.
            item.setData(DataComponentTypes.MAX_STACK_SIZE, 4);
            item.setAmount(10);
        }
        return item;
    }

    private static void fill(Inventory inv, Random r, int count, boolean overstack, int first) {
        ItemStack[] c = inv.getContents();
        for (int i = 0; i < count; i++) {
            int slot = first + r.nextInt(c.length - first);
            c[slot] = randomItem(r, overstack);
        }
        inv.setContents(c);
    }

    private static int firstStorage(Inventory inv) {
        return inv.getHolder(false) instanceof ChestedHorse ? 2 : 0;
    }

    private ItemStack craftItem(String token) {
        return switch (token) {
            case "air", "-" -> null;
            case "key" -> giveViaCommand("key");
            case "trunk" -> giveViaCommand("trunk");
            case "fakekey" -> {
                ItemStack i = new ItemStack(Material.RECOVERY_COMPASS);
                i.setData(DataComponentTypes.CUSTOM_NAME, Component.text("Multiversal Key"));
                i.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
                yield i;
            }
            default -> new ItemStack(Material.valueOf(token.toUpperCase()));
        };
    }

    private ItemStack giveViaCommand(String what) {
        return giveViaCommand(what, Bukkit.getOnlinePlayers().iterator().next());
    }

    /**
     * A real key or trunk item, given beforehand by the test with /portmanteau trunk give, so the test uses Portmanteau's
     * own item. Takes one from anyone online who has one.
     */
    private ItemStack giveViaCommand(String what, Player preferred) {
        String want = what.equals("key") ? "multiversal_key" : "multiversal_trunk";
        List<Player> order = new ArrayList<>();
        order.add(preferred);
        order.addAll(Bukkit.getOnlinePlayers());
        for (Player p : order) {
            ItemStack[] c = p.getInventory().getContents();
            for (int i = 0; i < c.length; i++) {
                if (!want.equals(kind(c[i]))) continue;
                ItemStack one = c[i].clone();
                one.setAmount(1);
                c[i].setAmount(c[i].getAmount() - 1);
                p.getInventory().setContents(c);
                return one;
            }
        }
        throw new IllegalStateException("no " + want + " to take; give one first");
    }

    private static String kind(ItemStack i) {
        if (i == null || i.isEmpty()) return null;
        return i.getPersistentDataContainer().get(new NamespacedKey("portmanteau", "item"), PersistentDataType.STRING);
    }

    // --- fuzz ---

    private String fuzz(int rounds, long seed) {
        Random r = new Random(seed);
        int sorted = 0, already = 0, left = 0, bad = 0;
        long nanos = 0;
        List<String> problems = new ArrayList<>();
        for (int round = 0; round < rounds; round++) {
            int size = new int[]{9, 27, 54}[r.nextInt(3)];
            Inventory inv = Bukkit.createInventory(null, size);
            if (r.nextInt(10) == 0) inv.setMaxStackSize(1 + r.nextInt(64));
            fill(inv, r, 1 + r.nextInt(size), true, 0);
            ItemStack[] before = deepCopy(inv.getContents());
            String ledgerBefore = ledgerHash(before);
            long start = System.nanoTime();
            SortingApi.Result res = SortingApi.sort(inv, null);
            nanos += System.nanoTime() - start;
            String ledgerAfter = ledgerHash(inv.getContents());
            switch (res) {
                case SORTED -> sorted++;
                case ALREADY_SORTED -> already++;
                case LEFT_UNCHANGED -> left++;
                default -> { bad++; problems.add("round " + round + ": " + res); }
            }
            if (!ledgerBefore.equals(ledgerAfter)) { bad++; problems.add("round " + round + ": items changed"); }
            if (res != SortingApi.Result.SORTED && !layoutHash(before).equals(layoutHash(inv.getContents()))) {
                bad++;
                problems.add("round " + round + ": " + res + " but the layout changed");
            }
            if (res == SortingApi.Result.SORTED && SortingApi.sort(inv, null) != SortingApi.Result.ALREADY_SORTED) {
                bad++;
                problems.add("round " + round + ": sorting again changed it");
            }
        }
        return "{\"rounds\":" + rounds + ",\"sorted\":" + sorted + ",\"already\":" + already + ",\"leftUnchanged\":" + left
                + ",\"bad\":" + bad + ",\"avgMicros\":" + (nanos / 1000.0 / rounds) + ",\"problems\":" + list(problems.stream().limit(10).toList()) + "}";
    }

    private String allItems() {
        List<Material> items = new ArrayList<>();
        for (Material m : Registry.MATERIAL) if (m.isItem() && !m.isAir()) items.add(m);
        int sorts = 0, bad = 0;
        List<String> problems = new ArrayList<>();
        for (int i = 0; i < items.size(); i += 54) {
            Inventory inv = Bukkit.createInventory(null, 54);
            List<Material> batch = new ArrayList<>(items.subList(i, Math.min(items.size(), i + 54)));
            java.util.Collections.shuffle(batch, new Random(i));
            for (int j = 0; j < batch.size(); j++) inv.setItem(j, new ItemStack(batch.get(j)));
            String before = ledgerHash(inv.getContents());
            SortingApi.Result res = SortingApi.sort(inv, null);
            sorts++;
            if (res == SortingApi.Result.FAILED || res == SortingApi.Result.NOT_RUNNING || !before.equals(ledgerHash(inv.getContents()))) {
                bad++;
                problems.add("batch " + i + ": " + res);
            }
        }
        return "{\"items\":" + items.size() + ",\"batches\":" + sorts + ",\"bad\":" + bad + ",\"problems\":" + list(problems) + "}";
    }

    private String trunkFuzz(Player p, int rounds, long seed) {
        TrunkStore store = new TrunkStore(portmanteau());
        Random r = new Random(seed);
        String scope = "test:fuzz";
        int bad = 0;
        List<String> problems = new ArrayList<>();
        for (int round = 0; round < rounds; round++) {
            ItemStack[] contents = new ItemStack[TrunkStore.SLOTS];
            for (int i = 0; i < r.nextInt(55); i++) contents[r.nextInt(TrunkStore.SLOTS)] = randomItem(r, false);
            var saved = store.save(p, scope, contents);
            if (!saved.ok()) { bad++; problems.add("round " + round + ": save " + saved.reason()); continue; }
            var loaded = store.load(p, scope);
            if (!(loaded instanceof TrunkStore.Contents c) || !layoutHash(c.items()).equals(layoutHash(contents))) {
                bad++;
                problems.add("round " + round + ": loaded back differently");
            }
        }
        // Leave nothing behind in the player's data.
        NamespacedKey root = new NamespacedKey(portmanteau(), "trunk");
        PersistentDataContainer all = p.getPersistentDataContainer().get(root, PersistentDataType.TAG_CONTAINER);
        if (all != null) {
            for (NamespacedKey k : all.getKeys()) {
                if (scope.equals(all.get(k, PersistentDataType.TAG_CONTAINER).get(new NamespacedKey(portmanteau(), "scope"), PersistentDataType.STRING))) all.remove(k);
            }
            p.getPersistentDataContainer().set(root, PersistentDataType.TAG_CONTAINER, all);
        }
        return "{\"rounds\":" + rounds + ",\"bad\":" + bad + ",\"problems\":" + list(problems.stream().limit(10).toList()) + "}";
    }

    private PersistentDataContainer trunkEntry(Player p, String scope) {
        PersistentDataContainer all = p.getPersistentDataContainer().get(new NamespacedKey(portmanteau(), "trunk"), PersistentDataType.TAG_CONTAINER);
        if (all == null) return null;
        for (NamespacedKey k : all.getKeys()) {
            PersistentDataContainer e = all.get(k, PersistentDataType.TAG_CONTAINER);
            if (scope.equals(e.get(new NamespacedKey(portmanteau(), "scope"), PersistentDataType.STRING))) return e;
        }
        return null;
    }

    private String features() {
        Plugin pm = Bukkit.getPluginManager().getPlugin("Portmanteau");
        Map<String, Integer> listeners = new TreeMap<>();
        for (RegisteredListener l : HandlerList.getRegisteredListeners(pm)) {
            listeners.merge(l.getListener().getClass().getSimpleName(), 1, Integer::sum);
        }
        List<String> recipes = new ArrayList<>();
        for (var it = Bukkit.recipeIterator(); it.hasNext(); ) {
            Recipe r = it.next();
            if (r instanceof org.bukkit.Keyed k && k.getKey().getNamespace().equals("portmanteau")) recipes.add(k.getKey().getKey());
        }
        var map = Bukkit.getCommandMap();
        List<String> commands = new ArrayList<>();
        for (String c : List.of("sort", "isort", "portmanteau")) if (map.getCommand(c) != null) commands.add(c);
        List<String> files = new ArrayList<>();
        String[] names = pm.getDataFolder().list();
        if (names != null) files.addAll(List.of(names));
        java.util.Collections.sort(files);
        return "{\"listeners\":" + json(listeners) + ",\"recipes\":" + list(recipes) + ",\"commands\":" + list(commands)
                + ",\"files\":" + list(files) + ",\"api\":" + SortingApi.running() + "}";
    }

    // --- helpers ---

    private Plugin portmanteau() {
        return Bukkit.getPluginManager().getPlugin("Portmanteau");
    }

    private static Player player(String name) {
        Player p = Bukkit.getPlayerExact(name);
        if (p == null) throw new IllegalArgumentException("no player " + name);
        return p;
    }

    private static Block block(String[] a, int i) {
        World w = Bukkit.getWorld(a[i]);
        if (w == null) throw new IllegalArgumentException("no world " + a[i]);
        return w.getBlockAt(Integer.parseInt(a[i + 1]), Integer.parseInt(a[i + 2]), Integer.parseInt(a[i + 3]));
    }

    private static Inventory inventoryAt(String[] a, int i) {
        Block b = block(a, i);
        if (!(b.getState(false) instanceof Container c)) throw new IllegalArgumentException("no container at " + b);
        return c.getInventory();
    }

    private static Inventory entityInventory(String uuid) {
        Entity e = Bukkit.getEntity(UUID.fromString(uuid));
        if (e instanceof ChestedHorse h) return h.getInventory();
        if (e instanceof ChestBoat b) return b.getInventory();
        if (e instanceof StorageMinecart m) return m.getInventory();
        throw new IllegalArgumentException("no storage entity " + uuid);
    }

    private static ItemStack[] deepCopy(ItemStack[] in) {
        ItemStack[] out = new ItemStack[in.length];
        for (int i = 0; i < in.length; i++) out[i] = in[i] == null ? null : in[i].clone();
        return out;
    }

    /** Every item counted by its full serialized bytes, so any change to any item's data shows. */
    static String ledgerHash(ItemStack[] slots) {
        TreeMap<String, Long> counts = new TreeMap<>();
        for (ItemStack i : slots) {
            if (i == null || i.isEmpty()) continue;
            ItemStack one = i.clone();
            one.setAmount(1);
            counts.merge(Base64.getEncoder().encodeToString(one.serializeAsBytes()), (long) i.getAmount(), Long::sum);
        }
        return sha(counts.toString().getBytes());
    }

    static String layoutHash(ItemStack[] slots) {
        StringBuilder sb = new StringBuilder();
        for (ItemStack i : slots) sb.append(i == null || i.isEmpty() ? "-" : Base64.getEncoder().encodeToString(i.serializeAsBytes())).append('|');
        return sha(sb.toString().getBytes());
    }

    private static String ledger(ItemStack[] slots) {
        long total = 0;
        for (ItemStack i : slots) if (i != null && !i.isEmpty()) total += i.getAmount();
        return obj("items", total, "ledger", ledgerHash(slots), "layout", layoutHash(slots));
    }

    static String sha(byte[] b) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b)).substring(0, 16);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String obj(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return json(m);
    }

    private static String json(Map<String, ?> m) {
        StringBuilder sb = new StringBuilder("{");
        for (var e : m.entrySet()) {
            if (sb.length() > 1) sb.append(',');
            sb.append('"').append(e.getKey()).append("\":").append(value(e.getValue()));
        }
        return sb.append('}').toString();
    }

    private static String value(Object v) {
        if (v instanceof Number || v instanceof Boolean) return String.valueOf(v);
        return "\"" + String.valueOf(v).replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static String list(List<String> items) {
        return "[" + String.join(",", items.stream().map(Helper::value).toList()) + "]";
    }

    /** Stands in for another plugin's menu. */
    static final class MenuHolder implements InventoryHolder {
        @Override public @NotNull Inventory getInventory() { throw new UnsupportedOperationException(); }
    }

    /** Stands in for a grave plugin's holder. */
    static final class GraveHolder implements InventoryHolder {
        @Override public @NotNull Inventory getInventory() { throw new UnsupportedOperationException(); }
    }
}
