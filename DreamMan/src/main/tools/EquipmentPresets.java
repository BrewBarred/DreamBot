package main.tools;

import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import main.actions.Action;
import main.data.store.LocalStore;
import org.dreambot.api.methods.container.impl.Inventory;
import org.dreambot.api.methods.container.impl.equipment.Equipment;
import org.dreambot.api.methods.container.impl.equipment.EquipmentSlot;
import org.dreambot.api.wrappers.items.Item;

import java.io.File;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Equipment presets (v1.31): named snapshots of a full worn setup, persisted to
 * {@code <root>/equipment-presets.json}, plus the builder that turns a preset into a runnable
 * Task made of EXISTING actions (FindBank -> Bank withdraw -> Wield x N) - so equipping a
 * preset walks to a bank for anything missing exactly the way your scripts already do.
 */
public final class EquipmentPresets {

    private EquipmentPresets() {}

    private static final Type MAP_TYPE =
            new TypeToken<LinkedHashMap<String, LinkedHashMap<String, String>>>() {}.getType();

    /** presetName -> (slotName -> itemName). Loaded lazily. */
    private static LinkedHashMap<String, LinkedHashMap<String, String>> presets;

    private static File file() { return new File(LocalStore.getRoot(), "equipment-presets.json"); }

    private static synchronized Map<String, LinkedHashMap<String, String>> load() {
        if (presets != null) return presets;
        presets = new LinkedHashMap<>();
        try {
            File f = file();
            if (f.isFile()) {
                LinkedHashMap<String, LinkedHashMap<String, String>> m =
                        new GsonBuilder().create().fromJson(
                                new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8),
                                MAP_TYPE);
                if (m != null) presets.putAll(m);
            }
        } catch (Throwable ignored) {}
        return presets;
    }

    private static synchronized void save() {
        try {
            file().getParentFile().mkdirs();   // v1.86: a fresh install saving its FIRST preset
                                               // has no root yet - Files.write won't create it
            Files.write(file().toPath(), new GsonBuilder().setPrettyPrinting().create()
                    .toJson(presets, MAP_TYPE).getBytes(StandardCharsets.UTF_8));
        } catch (Throwable ignored) {}
    }

    // ── slot names ────────────────────────────────────────────────────────────

    /**
     * One canonical name per equipment slot (v1.86). This is THE fix for the head (and
     * necklace) slot never populating: DreamBot's {@link EquipmentSlot} calls the head slot
     * {@code HAT} and the necklace slot {@code AMULET}, while the doll's grid was keyed
     * {@code HEAD} - so those snapshots existed under names no cell ever looked up. Every
     * read/write now funnels through this normaliser, which also quietly heals any preset
     * saved under the old names, whichever way a given client build labels its slots.
     */
    public static String canonicalSlot(String raw) {
        if (raw == null) return null;
        switch (raw.trim().toUpperCase(java.util.Locale.ROOT)) {
            case "HAT": case "HEAD": case "HELMET": case "HELM": return "HEAD";
            case "AMULET": case "NECK": case "NECKLACE":         return "AMULET";
            case "ARROWS": case "AMMO": case "AMMUNITION":       return "ARROWS";
            case "CHEST": case "BODY": case "TORSO":             return "CHEST";
            case "HANDS": case "GLOVES":                         return "HANDS";
            case "FEET": case "BOOTS":                           return "FEET";
            default: return raw.trim().toUpperCase(java.util.Locale.ROOT);
        }
    }

    private static LinkedHashMap<String, String> canonicalize(Map<String, String> setup) {
        LinkedHashMap<String, String> out = new LinkedHashMap<>();
        if (setup != null)
            for (Map.Entry<String, String> e : setup.entrySet())
                out.put(canonicalSlot(e.getKey()), e.getValue());
        return out;
    }

    // ── live equipment ────────────────────────────────────────────────────────

    /** What's worn right now: canonical slot -> item name (only occupied slots). Off-EDT safe. */
    public static LinkedHashMap<String, String> readLive() {
        LinkedHashMap<String, String> out = new LinkedHashMap<>();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            try {
                Item it = Equipment.getItemInSlot(slot);
                if (it != null && it.getName() != null && !it.getName().isEmpty()
                        && !"null".equalsIgnoreCase(it.getName()))
                    out.put(canonicalSlot(slot.name()), it.getName());
            } catch (Throwable ignored) {}
        }
        return out;
    }

    // ── preset CRUD ───────────────────────────────────────────────────────────

    public static List<String> names() { return new ArrayList<>(load().keySet()); }

    public static Map<String, String> get(String name) {
        LinkedHashMap<String, String> p = load().get(name);
        return p == null ? new LinkedHashMap<>() : canonicalize(p);   // heals old slot names
    }

    public static synchronized void put(String name, Map<String, String> setup) {
        if (name == null || name.isBlank() || setup == null) return;
        load().put(name.trim(), canonicalize(setup));
        save();
    }

    public static synchronized void delete(String name) {
        if (load().remove(name) != null) save();
    }

    /** v1.86: renames a preset in place, keeping its position in the list. */
    public static synchronized boolean rename(String oldName, String newName) {
        if (oldName == null || newName == null || newName.isBlank()) return false;
        Map<String, LinkedHashMap<String, String>> all = load();
        if (!all.containsKey(oldName) || all.containsKey(newName.trim())) return false;
        LinkedHashMap<String, LinkedHashMap<String, String>> rebuilt = new LinkedHashMap<>();
        for (Map.Entry<String, LinkedHashMap<String, String>> e : all.entrySet())
            rebuilt.put(e.getKey().equals(oldName) ? newName.trim() : e.getKey(), e.getValue());
        presets = rebuilt;
        save();
        return true;
    }

    // ── the equip task ────────────────────────────────────────────────────────

    /**
     * Items in the preset that are neither worn nor in the inventory right now - these are what
     * the equip task must withdraw first.
     */
    public static List<String> missingItems(Map<String, String> setup) {
        List<String> missing = new ArrayList<>();
        Map<String, String> worn = readLive();
        for (String item : setup.values()) {
            boolean wearing = worn.containsValue(item);
            boolean carried = false;
            try { carried = Inventory.contains(item); } catch (Throwable ignored) {}
            if (!wearing && !carried) missing.add(item);
        }
        return missing;
    }

    /**
     * Builds the equip task out of EXISTING actions: (when anything's missing) FindBank +
     * Bank-withdraw per item, then Wield per item. Runs like any other task - checks, retries
     * and the overlay all behave normally.
     */
    public static main.menu.DreamBotMenu.Task buildEquipTask(String presetName,
                                                             main.menu.components.JActionSelector factory) {
        Map<String, String> setup = get(presetName);
        if (setup.isEmpty()) return null;
        List<String> missing = missingItems(setup);

        List<Action> actions = new ArrayList<>();
        if (!missing.isEmpty()) {
            Action find = factory.create("FindBank");
            if (find != null) actions.add(find);
            for (String item : missing) {
                Action bank = factory.create("Bank");
                if (bank != null) {
                    Map<String, String> cfg = new LinkedHashMap<>();
                    cfg.put("Mode", "withdraw");
                    cfg.put("Target", item);
                    cfg.put("Amount", "1");
                    bank.deserialize(cfg);
                    actions.add(bank);
                }
            }
        }
        for (String item : setup.values()) {
            Action wield = factory.create("Wield");
            if (wield != null) {
                Map<String, String> cfg = new LinkedHashMap<>();
                cfg.put("Item", item);
                wield.deserialize(cfg);
                actions.add(wield);
            }
        }
        if (actions.isEmpty()) return null;
        return new main.menu.DreamBotMenu.Task("Equip: " + presetName,
                "Auto-generated by the Equipment tab (v1.31)", actions, "Equipping " + presetName);
    }
}
