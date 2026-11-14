package com.extantape.portmanteau.sorting;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Records quick transfers in Prism (4.4 and up), when it's installed, as item-insert and item-remove by the player at
 * the container, the way Prism records items a player moves by hand. Sorting itself moves nothing in or out, so it
 * isn't recorded. Prism's API is reached by name, so Portmanteau doesn't need Prism, and a Prism that doesn't match is
 * reported once and then left alone. A logging failure never stops the transfer.
 */
public final class PrismItems {
    static final String API = "org.prism_mc.prism.paper.api.PrismPaperApi";
    static final String ACTIVITY = "org.prism_mc.prism.paper.api.activities.PaperActivity";

    /** One kind of item and how many of it went in (positive) or came out (negative). */
    public record Change(ItemStack item, int amount) {}

    private final Object api;
    private final Object insertType, removeType;
    private final Method createItemAction, builder, recordingService;
    private final Logger log;
    private boolean warned;

    public PrismItems(Logger log) {
        Object a = null, in = null, out = null;
        Method create = null, build = null, rec = null;
        Plugin prism = Bukkit.getPluginManager().getPlugin("prism");
        if (prism != null && prism.isEnabled()) {
            try {
                ClassLoader cl = prism.getClass().getClassLoader();
                Class<?> apiClass = Class.forName(API, true, cl);
                RegisteredServiceProvider<?> reg = Bukkit.getServicesManager().getRegistration(apiClass);
                if (reg == null) throw new IllegalStateException("Prism registered no API service");
                a = reg.getProvider();
                Object registry = call(a, "actionTypeRegistry");
                in = ((Optional<?>) call(registry, "actionType", "item-insert")).orElseThrow();
                out = ((Optional<?>) call(registry, "actionType", "item-remove")).orElseThrow();
                Object factory = call(a, "actionFactory");
                for (Class<?> type : publicTypes(factory.getClass())) for (Method m : type.getMethods()) {
                    if (create == null && m.getName().equals("createItemAction") && m.getParameterCount() == 2
                            && m.getParameterTypes()[1].isAssignableFrom(ItemStack.class)) create = m;
                }
                if (create == null) throw new NoSuchMethodException("createItemAction(ActionType, ItemStack)");
                build = Class.forName(ACTIVITY, true, cl).getMethod("builder");
                if (!java.lang.reflect.Modifier.isPublic(build.getDeclaringClass().getModifiers())) throw new NoSuchMethodException("PaperActivity.builder() isn't public");
                rec = publicMethod(a, "recordingService");
                log.info("Quick transfers are recorded in Prism too.");
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                log.warning("Prism is installed but its API couldn't be reached (" + e + "). Quick transfers won't show in Prism.");
                a = null;
            }
        }
        this.log = log;
        this.api = a;
        this.insertType = in;
        this.removeType = out;
        this.createItemAction = create;
        this.builder = build;
        this.recordingService = rec;
    }

    public boolean active() { return api != null; }

    /** What a transfer moved, per kind of item: the container's contents before and after. */
    public static List<Change> changes(ItemStack[] before, ItemStack[] after) {
        List<ItemStack> kinds = new ArrayList<>();
        List<int[]> counts = new ArrayList<>();
        count(before, kinds, counts, -1);
        count(after, kinds, counts, 1);
        List<Change> out = new ArrayList<>();
        for (int i = 0; i < kinds.size(); i++) if (counts.get(i)[0] != 0) out.add(new Change(kinds.get(i), counts.get(i)[0]));
        return out;
    }

    private static void count(ItemStack[] slots, List<ItemStack> kinds, List<int[]> counts, int sign) {
        for (ItemStack s : slots) {
            if (s == null || s.isEmpty()) continue;
            int at = -1;
            for (int i = 0; i < kinds.size(); i++) if (kinds.get(i).isSimilar(s)) { at = i; break; }
            if (at < 0) { kinds.add(s.asOne()); counts.add(new int[1]); at = kinds.size() - 1; }
            counts.get(at)[0] += sign * s.getAmount();
        }
    }

    /** Records each change at the container, as the player's. Does nothing without Prism or without a place. */
    public void record(Player who, Location at, List<Change> changes) {
        if (api == null || at == null) return;
        try {
            Object factory = call(api, "actionFactory");
            Object service = recordingService.invoke(api);
            for (Change c : changes) {
                ItemStack stack = c.item().asQuantity(Math.abs(c.amount()));
                Object action = createItemAction.invoke(factory, c.amount() > 0 ? insertType : removeType, stack);
                Object b = builder.invoke(null);
                b = call(b, "action", action);
                b = call(b, "location", at);
                b = call(b, "cause", who);
                call(service, "addToQueue", call(b, "build"));
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            if (!warned) {
                warned = true;
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                log.warning("Couldn't record in Prism (" + cause + "). Quick transfers won't show in Prism until this is fixed.");
            }
        }
    }

    static Object call(Object target, String name, Object arg) throws ReflectiveOperationException {
        for (Class<?> type : publicTypes(target.getClass())) {
            for (Method m : type.getMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() == 1 && m.getParameterTypes()[0].isInstance(arg)) return m.invoke(target, arg);
            }
        }
        throw new NoSuchMethodException(target.getClass().getName() + "." + name + "(" + arg.getClass().getSimpleName() + ")");
    }

    static Object call(Object target, String name) throws ReflectiveOperationException {
        for (Class<?> type : publicTypes(target.getClass())) {
            try { return type.getMethod(name).invoke(target); } catch (NoSuchMethodException next) { /* next public type */ }
        }
        throw new NoSuchMethodException(target.getClass().getName() + "." + name + "()");
    }

    static Method publicMethod(Object target, String name) throws NoSuchMethodException {
        for (Class<?> type : publicTypes(target.getClass())) {
            try { return type.getMethod(name); } catch (NoSuchMethodException next) { /* next public type */ }
        }
        throw new NoSuchMethodException(target.getClass().getName() + "." + name + "()");
    }

    /** The public types an object can be reached through. A public method of a hidden class is invoked through one of these. */
    static List<Class<?>> publicTypes(Class<?> c) {
        java.util.LinkedHashSet<Class<?>> out = new java.util.LinkedHashSet<>();
        java.util.ArrayDeque<Class<?>> todo = new java.util.ArrayDeque<>();
        todo.add(c);
        while (!todo.isEmpty()) {
            Class<?> k = todo.poll();
            if (java.lang.reflect.Modifier.isPublic(k.getModifiers())
                    && (k.getEnclosingClass() == null || java.lang.reflect.Modifier.isPublic(k.getEnclosingClass().getModifiers()))) out.add(k);
            if (k.getSuperclass() != null) todo.add(k.getSuperclass());
            todo.addAll(List.of(k.getInterfaces()));
        }
        return List.copyOf(out);
    }
}
