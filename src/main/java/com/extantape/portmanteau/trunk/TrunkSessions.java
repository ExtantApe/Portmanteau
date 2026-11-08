package com.extantape.portmanteau.trunk;

import com.extantape.portmanteau.transaction.VerifiedBytes;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * One live inventory per owner and scope. Every trunk of that scope the owner opens shows the same inventory, so two
 * views can't disagree. It's loaded from the player's data once and is the truth until it's saved and unloaded.
 *
 * <p>It's saved to the player's data the tick after any change, every half second while open, and on every close, quit,
 * teleport, world change, death, world save and shutdown. A save that changes nothing writes nothing.
 */
final class TrunkSessions {
    static final class Session {
        final UUID owner;
        final String scope;
        final TrunkHolder holder;
        final Inventory inventory;
        byte[] saved;
        boolean dirty;

        Session(UUID owner, String scope, TrunkHolder holder, Inventory inventory, byte[] saved) {
            this.owner = owner;
            this.scope = scope;
            this.holder = holder;
            this.inventory = inventory;
            this.saved = saved;
        }
    }

    private final Plugin plugin;
    private final TrunkStore store;
    private final AdminCopies copies;
    private final boolean adminCopies;
    private final Logger log;
    private final Map<String, Session> sessions = new HashMap<>();

    TrunkSessions(Plugin plugin, TrunkStore store, AdminCopies copies, boolean adminCopies) {
        this.plugin = plugin;
        this.store = store;
        this.copies = copies;
        this.adminCopies = adminCopies;
        this.log = plugin.getLogger();
    }

    private static String key(UUID owner, String scope) {
        return owner + "|" + scope;
    }

    /** The live inventory, loading it if needed. Null, with the reason logged, if the stored data can't be read. */
    Session open(Player owner, String scope, Component title) {
        Session s = sessions.get(key(owner.getUniqueId(), scope));
        if (s != null) return s;
        TrunkStore.Loaded loaded = store.load(owner, scope);
        if (loaded instanceof TrunkStore.Corrupt c) {
            String msg = "The multiversal trunk of " + owner.getName() + " (" + owner.getUniqueId() + ") for scope " + scope
                    + " can't be read: " + c.reason() + ". It stays shut and its stored data is left untouched. The latest "
                    + "readable copy is in plugins/Portmanteau/trunk-copies/" + owner.getUniqueId() + "/.";
            log.severe(msg);
            tellAdmins(msg);
            return null;
        }
        TrunkStore.Contents contents = (TrunkStore.Contents) loaded;
        TrunkHolder holder = new TrunkHolder(owner.getUniqueId(), scope);
        Inventory inv = Bukkit.createInventory(holder, TrunkStore.SLOTS, title);
        holder.attach(inv);
        inv.setContents(contents.items());
        // The inventory must hold exactly what was stored, or it isn't safe to use.
        if (!Arrays.equals(TrunkStore.serialize(inv.getContents()), contents.bytes())) {
            String msg = "The multiversal trunk of " + owner.getName() + " for scope " + scope + " didn't load back exactly as "
                    + "stored, so it stays shut and its stored data is left untouched.";
            log.severe(msg);
            tellAdmins(msg);
            return null;
        }
        s = new Session(owner.getUniqueId(), scope, holder, inv, contents.bytes());
        sessions.put(key(owner.getUniqueId(), scope), s);
        return s;
    }

    Session find(Inventory inv) {
        if (!(inv.getHolder(false) instanceof TrunkHolder h)) return null;
        Session s = sessions.get(key(h.owner(), h.scope()));
        return s != null && s.inventory == inv ? s : null;
    }

    void markDirty(Session s) {
        if (s.dirty) return;
        s.dirty = true;
        Player owner = Bukkit.getPlayer(s.owner);
        if (owner == null) return;
        owner.getScheduler().run(plugin, task -> {
            if (s.dirty) commit(s, "change");
        }, null);
    }

    /** Saves the live contents to the owner's data if they differ. Returns false if the save failed. */
    boolean commit(Session s, String why) {
        s.dirty = false;
        Player owner = Bukkit.getPlayer(s.owner);
        if (owner == null) {
            log.severe("A multiversal trunk for " + s.owner + " is open but its owner isn't online, so it can't be saved ("
                    + why + "). This shouldn't happen; please report it.");
            return false;
        }
        ItemStack[] contents = s.inventory.getContents();
        byte[] now = TrunkStore.serialize(contents);
        if (Arrays.equals(now, s.saved)) return true;
        VerifiedBytes.Result r = store.save(owner, s.scope, contents);
        if (r.ok()) {
            s.saved = now;
            if (adminCopies) copies.write(s.owner, s.scope, cloneAll(contents));
            return true;
        }
        String msg = "Saving the multiversal trunk of " + owner.getName() + " for scope " + s.scope + " (" + why + ") failed: "
                + r.reason() + ". The previous saved contents were kept, and the live trunk will be saved again on the next change.";
        log.severe(msg);
        tellAdmins(msg);
        return false;
    }

    /** Saves and forgets a session once nobody is looking at it. */
    void release(Session s, Player closing) {
        commit(s, "close");
        boolean othersLooking = s.inventory.getViewers().stream().anyMatch(v -> !v.equals(closing));
        if (!othersLooking && Arrays.equals(TrunkStore.serialize(s.inventory.getContents()), s.saved)) {
            sessions.remove(key(s.owner, s.scope));
        }
    }

    /** Closes the player's trunk view, if any, so it's saved before a teleport, world change, death or quit. */
    void closeViewOf(Player p, InventoryCloseEvent.Reason reason) {
        Inventory top = p.getOpenInventory().getTopInventory();
        if (top.getHolder(false) instanceof TrunkHolder || top.getHolder(false) instanceof ReadOnlyHolder) p.closeInventory(reason);
    }

    void commitAll(String why) {
        for (Session s : new ArrayList<>(sessions.values())) commit(s, why);
    }

    /** Commits every session of a player and forgets them. Called on quit, after their view is closed. */
    void unloadPlayer(UUID owner) {
        for (Session s : new ArrayList<>(sessions.values())) {
            if (!s.owner.equals(owner)) continue;
            if (commit(s, "quit")) sessions.remove(key(s.owner, s.scope));
        }
    }

    /** Closes every view and saves everything, for shutdown or switching the feature off. */
    void shutdown() {
        for (Player p : Bukkit.getOnlinePlayers()) closeViewOf(p, InventoryCloseEvent.Reason.PLUGIN);
        for (Session s : new ArrayList<>(sessions.values())) {
            if (commit(s, "shutdown")) sessions.remove(key(s.owner, s.scope));
        }
        if (!sessions.isEmpty()) log.severe(sessions.size() + " multiversal trunk(s) couldn't be saved at shutdown. See the errors above.");
    }

    int open() {
        return sessions.size();
    }

    List<Session> all() {
        return List.copyOf(sessions.values());
    }

    private static ItemStack[] cloneAll(ItemStack[] in) {
        ItemStack[] out = new ItemStack[in.length];
        for (int i = 0; i < in.length; i++) out[i] = in[i] == null ? null : in[i].clone();
        return out;
    }

    private void tellAdmins(String msg) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission("portmanteau.admin")) p.sendMessage(Component.text("[Portmanteau] " + msg, NamedTextColor.RED));
        }
    }
}
