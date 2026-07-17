package main.actions;

import main.components.JParamTextField;

import javax.swing.*;
import java.util.LinkedHashMap;
import java.util.Map;

import static main.menu.MenuHandler.createParameterPanel;

/**
 * Casts a spell (v1.31). Built f2p-first (the whole Normal book is matched by name) but takes
 * a spellbook so members books work the moment you have them: normal / ancient / lunar /
 * arceuus. Validates the LIVE magic level before casting - a drained level fails the attempt so
 * checks can restore and retry, same philosophy as prayers.
 */
public class CastSpell extends Action {

    @Override public boolean acceptsEntityTarget() { return false; }

    private JParamTextField paramBook;

    private transient boolean cast;

    public CastSpell() {
        super();
        paramTarget = new JParamTextField("Varrock Teleport");
        paramBook = new JParamTextField("normal");
        maxAttempts = 8;
    }

    public CastSpell(CastSpell o) {
        this();
        paramTarget.setParam(o.paramTarget.getParam());
        paramBook.setParam(o.paramBook.getParam());
    }

    /**
     * v1.31 hotfix: the Spell interface exposes no level accessor on this build, so the
     * Normal book's requirements live here (keyed by enum name). Members books skip the
     * pre-check and just attempt the cast - the client refuses invalid casts anyway.
     */
    private static final java.util.Map<String, Integer> NORMAL_LEVELS = new java.util.HashMap<>();
    static {
        String[][] t = {
            {"HOME_TELEPORT","0"},{"WIND_STRIKE","1"},{"CONFUSE","3"},{"WATER_STRIKE","5"},
            {"ENCHANT_LEVEL_1","7"},{"EARTH_STRIKE","9"},{"WEAKEN","11"},{"FIRE_STRIKE","13"},
            {"BONES_TO_BANANAS","15"},{"WIND_BOLT","17"},{"CURSE","19"},{"LOW_LEVEL_ALCHEMY","21"},
            {"WATER_BOLT","23"},{"VARROCK_TELEPORT","25"},{"ENCHANT_LEVEL_2","27"},
            {"EARTH_BOLT","29"},{"LUMBRIDGE_TELEPORT","31"},{"TELEKINETIC_GRAB","33"},
            {"FIRE_BOLT","35"},{"FALADOR_TELEPORT","37"},{"CRUMBLE_UNDEAD","39"},
            {"TELEPORT_TO_HOUSE","40"},{"WIND_BLAST","41"},{"SUPERHEAT_ITEM","43"},
            {"CAMELOT_TELEPORT","45"},{"WATER_BLAST","47"},{"ENCHANT_LEVEL_3","49"},
            {"IBAN_BLAST","50"},{"SNARE","50"},{"MAGIC_DART","50"},{"ARDOUGNE_TELEPORT","51"},
            {"EARTH_BLAST","53"},{"HIGH_LEVEL_ALCHEMY","55"},{"CHARGE_WATER_ORB","56"},
            {"ENCHANT_LEVEL_4","57"},{"WATCHTOWER_TELEPORT","58"},{"FIRE_BLAST","59"},
            {"CHARGE_EARTH_ORB","60"},{"BONES_TO_PEACHES","60"},{"TROLLHEIM_TELEPORT","61"},
            {"WIND_WAVE","62"},{"CHARGE_FIRE_ORB","63"},{"TELEPORT_TO_APE_ATOLL","64"},
            {"WATER_WAVE","65"},{"CHARGE_AIR_ORB","66"},{"VULNERABILITY","66"},
            {"ENCHANT_LEVEL_5","68"},{"KOUREND_CASTLE_TELEPORT","69"},{"EARTH_WAVE","70"},
            {"ENFEEBLE","73"},{"TELEOTHER_LUMBRIDGE","74"},{"FIRE_WAVE","75"},{"ENTANGLE","79"},
            {"STUN","80"},{"CHARGE","80"},{"WIND_SURGE","81"},{"TELEOTHER_FALADOR","82"},
            {"WATER_SURGE","85"},{"TELE_BLOCK","85"},{"ENCHANT_LEVEL_6","87"},
            {"TELEOTHER_CAMELOT","90"},{"EARTH_SURGE","90"},{"ENCHANT_LEVEL_7","93"},
            {"FIRE_SURGE","95"},
        };
        for (String[] row : t) NORMAL_LEVELS.put(row[0], Integer.parseInt(row[1]));
    }

    private org.dreambot.api.methods.magic.Spell resolve() {
        String want = paramTarget.getParam() == null ? ""
                : paramTarget.getParam().trim().toLowerCase();
        if (want.isEmpty()) return null;
        String book = paramBook.getParam() == null ? "normal" : paramBook.getParam().trim().toLowerCase();
        Enum<?>[] values;
        try {
            if (book.startsWith("anc"))      values = org.dreambot.api.methods.magic.Ancient.values();
            else if (book.startsWith("lun")) values = org.dreambot.api.methods.magic.Lunar.values();
            else if (book.startsWith("arc")) values = org.dreambot.api.methods.magic.Arceuus.values();
            else                             values = org.dreambot.api.methods.magic.Normal.values();
        } catch (Throwable t) { return null; }
        for (Enum<?> e : values) {
            String n = e.name().toLowerCase().replace("_", " ");
            if (n.equals(want)) return (org.dreambot.api.methods.magic.Spell) e;
        }
        for (Enum<?> e : values) {
            String n = e.name().toLowerCase().replace("_", " ");
            if (n.contains(want)) return (org.dreambot.api.methods.magic.Spell) e;
        }
        return null;
    }

    @Override
    public boolean execute() {
        if (cast) { cast = false; return true; }
        org.dreambot.api.methods.magic.Spell spell = resolve();
        if (spell == null) { noteAttempt(); return false; }
        try {
            // v1.31 hotfix: level pre-check only when we KNOW the requirement (Normal book by
            // enum name); members books go straight to the cast attempt.
            Integer req = spell instanceof Enum
                    ? NORMAL_LEVELS.get(((Enum<?>) spell).name()) : null;
            int level = org.dreambot.api.methods.skills.Skills.getBoostedLevel(
                    org.dreambot.api.methods.skills.Skill.MAGIC);
            if (req != null && level < req) {
                noteAttempt();   // drained/too low RIGHT NOW - let a check restore, then retry
                return false;
            }
            noteAttempt();
            if (org.dreambot.api.methods.magic.Magic.castSpell(spell))
                cast = true;   // settle one poll for the cast to land, then complete
        } catch (Throwable ignored) {}
        return false;
    }

    @Override public String getParamTarget() { return paramTarget.getParam(); }
    @Override public Action copy() { return new CastSpell(this); }

    @Override
    public JPanel createParamPanel() {
        JPanel sp = createParameterPanel("Spell:",
                "The spell name. Checks your LIVE magic level before casting; a drained level "
                        + "fails the attempt so a restoring check can kick in.",
                paramTarget, "  e.g. \"Wind Strike\", \"Varrock Teleport\"");
        JPanel bk = createParameterPanel("Spellbook:",
                "normal (f2p + members), ancient, lunar, or arceuus.",
                paramBook, "  usually \"normal\"");
        return ActionUtil.stack(sp, bk);
    }

    @Override
    public Map<String, String> serialize() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("Spell", paramTarget.getParam());
        m.put("Book", paramBook.getParam());
        return m;
    }

    @Override
    public void deserialize(Map<String, String> data) {
        if (data == null) return;
        if (data.get("Spell") != null) paramTarget.setParam(data.get("Spell"));
        if (data.get("Book") != null) paramBook.setParam(data.get("Book"));
    }
}
