package main.watchers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import main.actions.Action;
import main.data.ActionData;
import main.data.store.ProfileCodec;
import org.dreambot.api.utilities.Logger;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Serializes {@link Trigger} lists to/from a single JSON string (Patch B.4). Because actions
 * already round-trip as {@code type + Map<String,String>} via {@link ProfileCodec}, a trigger's
 * response chain serializes the same way - so a whole watcher (condition + nested actions) fits
 * in one string. That lets triggers ride inside an action's existing {@code serialize()} map and
 * inside the profile without any change to the JSON schema those already use.
 */
public final class TriggerCodec {

    private TriggerCodec() {}

    private static final Gson GSON = new GsonBuilder().create();
    private static final Type LIST_TYPE = new TypeToken<List<TriggerDTO>>() {}.getType();

    /** Flat, Gson-friendly mirror of a Trigger. */
    private static final class TriggerDTO {
        String condition;
        String arg;
        boolean enabled = true;
        boolean replacesAction = false;
        long cooldownMs = 3000;
        int chancePercent = 100;        // v1.30: whole-check fire chance
        boolean timerEnabled = false;   // v1.30: run-every timer
        long timerIntervalMs = 0;
        String control = "NONE";        // v1.33: post-response queue control
        boolean chainedElse = false;    // v1.50: else-if of the trigger above
        java.util.List<ClauseDTO> clauses = new ArrayList<>();   // v1.33: ANDed extra conditions
        List<ActionData> response = new ArrayList<>();
    }

    /** v1.33: one ANDed extra condition (with optional NOT). */
    private static final class ClauseDTO {
        String condition;
        String arg = "";
        boolean negate = false;
        boolean or = false;          // v1.50: connector to the previous term (false = AND)
    }

    /** @return a JSON string for the triggers, or "" for an empty/null list. */
    public static String toJson(List<Trigger> triggers) {
        if (triggers == null || triggers.isEmpty()) return "";
        List<TriggerDTO> dtos = new ArrayList<>();
        for (Trigger t : triggers) {
            if (t == null || t.getCondition() == null) continue;
            TriggerDTO d = new TriggerDTO();
            d.condition = t.getCondition().name();
            d.arg = t.getArg();
            d.enabled = t.isEnabled();
            d.replacesAction = t.replacesAction();
            d.cooldownMs = t.getCooldownMs();
            d.chancePercent = t.getChancePercent();          // v1.30
            d.timerEnabled = t.isTimerEnabled();             // v1.30
            d.timerIntervalMs = t.getTimerIntervalMs();      // v1.30
            d.control = t.getControl().name();               // v1.33
            d.chainedElse = t.isChainedElse();               // v1.50
            for (Trigger.Clause cl : t.getExtraClauses()) {  // v1.33
                if (cl == null || cl.condition == null) continue;
                ClauseDTO cd = new ClauseDTO();
                cd.condition = cl.condition.name();
                cd.arg = cl.arg;
                cd.negate = cl.negate;
                cd.or = cl.or;                       // v1.50
                d.clauses.add(cd);
            }
            for (Action a : t.getResponse())
                if (a != null) d.response.add(ProfileCodec.toData(a));
            dtos.add(d);
        }
        try { return GSON.toJson(dtos, LIST_TYPE); }
        catch (Throwable e) {
            Logger.log(Logger.LogType.WARN, "[TriggerCodec] encode failed: " + e);
            return "";
        }
    }

    /** Parses triggers from a JSON string (empty/garbage → empty list, never throws). */
    public static List<Trigger> fromJson(String json) {
        List<Trigger> out = new ArrayList<>();
        if (json == null || json.isBlank()) return out;
        try {
            List<TriggerDTO> dtos = GSON.fromJson(json, LIST_TYPE);
            if (dtos == null) return out;
            for (TriggerDTO d : dtos) {
                Condition c = Condition.fromName(d.condition);
                if (c == null) continue;
                Trigger t = new Trigger(c, d.arg);
                t.setEnabled(d.enabled);
                t.setReplacesAction(d.replacesAction);
                t.setCooldownMs(d.cooldownMs);
                t.setChancePercent(d.chancePercent);                 // v1.30 (100 in old saves)
                t.setTimer(d.timerEnabled, d.timerIntervalMs);       // v1.30 (off in old saves)
                try { t.setControl(Trigger.Control.valueOf(d.control)); }   // v1.33 (NONE if absent)
                catch (Throwable ignored) { t.setControl(Trigger.Control.NONE); }
                t.setChainedElse(d.chainedElse);                             // v1.50
                if (d.clauses != null)                                      // v1.33
                    for (ClauseDTO cd : d.clauses) {
                        Condition cc = Condition.fromName(cd.condition);
                        if (cc != null)
                            t.getExtraClauses().add(
                                    new Trigger.Clause(cc, cd.arg, cd.negate, cd.or));   // v1.50
                    }
                if (d.response != null)
                    for (ActionData ad : d.response) {
                        Action a = ProfileCodec.fromData(ad);
                        if (a != null) t.getResponse().add(a);
                    }
                out.add(t);
            }
        } catch (Throwable e) {
            Logger.log(Logger.LogType.WARN, "[TriggerCodec] decode failed: " + e);
        }
        return out;
    }
}
