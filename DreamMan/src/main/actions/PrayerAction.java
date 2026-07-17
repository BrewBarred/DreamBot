package main.actions;

import main.components.JParamTextField;

import javax.swing.*;
import java.util.LinkedHashMap;
import java.util.Map;

import static main.menu.MenuHandler.createParameterPanel;

/**
 * Activates or deactivates a prayer (v1.31), validating BEFORE clicking: the prayer must exist,
 * your base level must meet its requirement, and - the part that matters mid-fight - your
 * CURRENT prayer points must be above zero. Drained points make the attempt fail (never
 * silently spam-click), so a "prayer points below X" or failing-action check can drink a
 * restore/brew and this action retries with points back.
 */
public class PrayerAction extends Action {

    @Override public boolean acceptsEntityTarget() { return false; }

    private JParamTextField paramState;   // on | off

    public PrayerAction() {
        super();
        paramTarget = new JParamTextField("Protect from Melee");
        paramState = new JParamTextField("on");
        maxAttempts = 8;
    }

    public PrayerAction(PrayerAction o) {
        this();
        paramTarget.setParam(o.paramTarget.getParam());
        paramState.setParam(o.paramState.getParam());
    }

    /**
     * v1.31 hotfix: DreamBot's Prayer enum exposes no level accessor on this build, so the
     * OSRS requirements live here, keyed by enum name. Unknown names (future prayers) fall
     * back to 1 - the live points check still guards them.
     */
    private static final java.util.Map<String, Integer> LEVELS = new java.util.HashMap<>();
    static {
        String[][] t = {
            {"THICK_SKIN","1"},{"BURST_OF_STRENGTH","4"},{"CLARITY_OF_THOUGHT","7"},
            {"SHARP_EYE","8"},{"MYSTIC_WILL","9"},{"ROCK_SKIN","10"},
            {"SUPERHUMAN_STRENGTH","13"},{"IMPROVED_REFLEXES","16"},{"RAPID_RESTORE","19"},
            {"RAPID_HEAL","22"},{"PROTECT_ITEM","25"},{"HAWK_EYE","26"},{"MYSTIC_LORE","27"},
            {"STEEL_SKIN","28"},{"ULTIMATE_STRENGTH","31"},{"INCREDIBLE_REFLEXES","34"},
            {"PROTECT_FROM_MAGIC","37"},{"PROTECT_FROM_MISSILES","40"},{"PROTECT_FROM_MELEE","43"},
            {"EAGLE_EYE","44"},{"MYSTIC_MIGHT","45"},{"RETRIBUTION","46"},{"REDEMPTION","49"},
            {"SMITE","52"},{"PRESERVE","55"},{"CHIVALRY","60"},{"PIETY","70"},
            {"RIGOUR","74"},{"AUGURY","77"},
        };
        for (String[] row : t) LEVELS.put(row[0], Integer.parseInt(row[1]));
    }

    private static int requiredLevel(org.dreambot.api.methods.prayer.Prayer p) {
        return LEVELS.getOrDefault(p.name(), 1);
    }

    private boolean wantOn() {
        String v = paramState.getParam() == null ? "on" : paramState.getParam().trim().toLowerCase();
        return !v.startsWith("off") && !v.startsWith("de");
    }

    private org.dreambot.api.methods.prayer.Prayer resolve() {
        String want = paramTarget.getParam() == null ? ""
                : paramTarget.getParam().trim().toLowerCase().replaceAll("[^a-z]+", "_");
        for (org.dreambot.api.methods.prayer.Prayer p
                : org.dreambot.api.methods.prayer.Prayer.values())
            if (p.name().toLowerCase().equals(want)
                    || p.name().toLowerCase().replace("_", " ")
                       .equals(paramTarget.getParam().trim().toLowerCase()))
                return p;
        // relaxed contains-match ("piety", "melee"...)
        for (org.dreambot.api.methods.prayer.Prayer p
                : org.dreambot.api.methods.prayer.Prayer.values())
            if (p.name().toLowerCase().replace("_", " ")
                    .contains(paramTarget.getParam().trim().toLowerCase()))
                return p;
        return null;
    }

    @Override
    public boolean execute() {
        org.dreambot.api.methods.prayer.Prayer prayer = resolve();
        if (prayer == null) { noteAttempt(); return false; }
        boolean on = wantOn();
        try {
            if (org.dreambot.api.methods.prayer.Prayers.isActive(prayer) == on)
                return true;                                     // already there

            if (on) {
                // v1.31: validate the LIVE values at cast time, not config time.
                int level = org.dreambot.api.methods.skills.Skills.getRealLevel(
                        org.dreambot.api.methods.skills.Skill.PRAYER);
                int points = org.dreambot.api.methods.skills.Skills.getBoostedLevel(
                        org.dreambot.api.methods.skills.Skill.PRAYER);
                if (level < requiredLevel(prayer) || points <= 0) {
                    // can't activate right now (level too low / points drained) - a real
                    // attempt failure, so your checks can restore points and this retries.
                    noteAttempt();
                    return false;
                }
            }
            noteAttempt();
            org.dreambot.api.methods.prayer.Prayers.toggle(on, prayer);
        } catch (Throwable ignored) { noteAttempt(); }
        return false;
    }

    @Override public String getParamTarget() { return paramTarget.getParam(); }
    @Override public Action copy() { return new PrayerAction(this); }

    @Override public String conflictGroup() { return "prayer"; }

    @Override
    public JPanel createParamPanel() {
        JPanel p = createParameterPanel("Prayer:",
                "Which prayer. Activation checks your LIVE level and points first - drained "
                        + "points fail the attempt so a check can drink a restore and retry.",
                paramTarget, "  e.g. \"Protect from Melee\", \"Piety\"");
        JPanel st = createParameterPanel("State:",
                "Turn it on or off.", paramState, "  \"on\" or \"off\"");
        return ActionUtil.stack(p, st);
    }

    @Override
    public Map<String, String> serialize() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("Prayer", paramTarget.getParam());
        m.put("State", paramState.getParam());
        return m;
    }

    @Override
    public void deserialize(Map<String, String> data) {
        if (data == null) return;
        if (data.get("Prayer") != null) paramTarget.setParam(data.get("Prayer"));
        if (data.get("State") != null) paramState.setParam(data.get("State"));
    }
}
