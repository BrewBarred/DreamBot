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
        List<ActionData> response = new ArrayList<>();
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
