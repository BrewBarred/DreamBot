package main.actions;

import main.components.JParamTextField;
import main.tools.LootTracker;
import org.dreambot.api.methods.container.impl.Inventory;
import org.dreambot.api.methods.map.Tile;
import org.dreambot.api.wrappers.interactive.GameObject;
import org.dreambot.api.wrappers.interactive.NPC;

import javax.swing.*;
import java.util.LinkedHashMap;
import java.util.Map;

import static main.menu.MenuHandler.createParameterPanel;

/**
 * Interacts with the nearest NPC or game object matching a name, using a chosen verb, within a
 * radius. Patch B.2 rebuilds the core around <b>target latching</b>:
 *
 * <p>Previously "until gone" re-resolved <i>the nearest match by name</i> every poll - in a cow
 * pen there is always another Cow, so the step never ended. Now the first successful interaction
 * LATCHES that specific instance, and the stop condition is evaluated against it alone: the step
 * completes when <i>your</i> cow is dead/despawned, no matter how many others are standing around.
 *
 * <p>Combat verbs also skip candidates that are already fighting someone else (the "can't attack
 * a cow that's in combat" case): a busy target is a soft retry - pick another - not a failure.
 * When the latched target dies, its tile is recorded in {@link LootTracker} so Loot prefers your
 * own drops. A genuine failure (nothing matching in range, repeated failed clicks) counts against
 * the Retries budget; when that runs out the engine fails the task and moves on. Set Retries to
 * "inf" to keep trying forever.
 */
public class Interact extends Action {

    private JParamTextField paramAction;
    private JParamTextField paramRadius;
    private JParamTextField paramUntil;
    private JParamTextField paramRetries;

    // ── Latched target state (transient - never serialised) ──
    private transient NPC latchedNpc;
    private transient GameObject latchedObj;
    private transient Tile latchedTile;            // last known tile (for kill recording)
    private transient String latchedName;          // name of the latched NPC (drop-table learning)
    private transient long lastIssueAt;            // last time we sent the interaction
    private transient long lastNoCandidateNoteAt;  // rate-limits "nothing in range" attempts

    public Interact() {
        super();
        paramTarget  = new JParamTextField("Cow");
        paramAction  = new JParamTextField("Attack");
        paramRadius  = new JParamTextField("12");
        paramUntil   = new JParamTextField("auto");
        paramRetries = new JParamTextField("12");
    }

    public Interact(Interact o) {
        this();
        paramTarget.setParam(o.paramTarget.getParam());
        paramAction.setParam(o.paramAction.getParam());
        paramRadius.setParam(o.paramRadius.getParam());
        paramUntil.setParam(o.paramUntil.getParam());
        paramRetries.setParam(o.paramRetries.getParam());
    }

    private boolean isCombatVerb() {
        String v = paramAction.getParam() == null ? "" : paramAction.getParam().toLowerCase();
        return v.contains("attack") || v.contains("fight") || v.contains("kill");
    }

    private String resolveMode() {
        String m = paramUntil.getParam() == null ? "auto" : paramUntil.getParam().trim().toLowerCase();
        if (m.equals("auto"))
            return isCombatVerb() ? "gone" : "once";
        return m;
    }

    private void syncRetryBudget() {
        String r = paramRetries.getParam() == null ? "" : paramRetries.getParam().trim().toLowerCase();
        if (r.startsWith("inf") || r.equals("0") || r.equals("-1"))
            maxAttempts = 0;                       // retry forever
        else
            maxAttempts = ActionUtil.parseInt(r, 12);
    }

    private void unlatch() {
        latchedNpc = null;
        latchedObj = null;
        latchedTile = null;
    }

    /** Picks a fresh candidate: nearest name match in radius; combat skips busy targets. */
    private void pickCandidate(String name, int radius, boolean combat) {
        NPC npc = ActionUtil.nearestNpc(name, radius, combat);
        if (npc != null) {
            latchedNpc = npc;
            latchedTile = npc.getTile();
            try { latchedName = npc.getName(); } catch (Throwable t) { latchedName = name; }
            return;
        }
        GameObject obj = ActionUtil.nearestObject(name, radius);
        if (obj != null) {
            latchedObj = obj;
            latchedTile = obj.getTile();
        }
    }

    private boolean latchedExists() {
        try {
            if (latchedNpc != null) return latchedNpc.exists();
            if (latchedObj != null) return latchedObj.exists();
        } catch (Throwable ignored) {}
        return false;
    }

    private boolean issueInteract(String verb) {
        lastIssueAt = System.currentTimeMillis();
        try {
            if (latchedNpc != null) return latchedNpc.interact(verb);
            if (latchedObj != null) return latchedObj.interact(verb);
        } catch (Throwable ignored) {}
        return false;
    }

    @Override
    public boolean execute() {
        String name = paramTarget.getParam();
        String verb = paramAction.getParam();
        int radius = ActionUtil.parseInt(paramRadius.getParam(), 12);
        String mode = resolveMode();
        boolean combat = isCombatVerb();
        syncRetryBudget();

        if (mode.equals("inv-full") && Inventory.isFull()) {
            unlatch();
            return true;
        }

        // ── latched target bookkeeping ──
        if ((latchedNpc != null || latchedObj != null)) {
            if (!latchedExists()) {
                // OUR target is gone. For combat that means the kill landed - record the spot
                // so Loot favours these drops - and for "gone" mode the step is complete.
                if (combat && latchedTile != null)
                    LootTracker.recordKill(latchedTile, latchedName);
                Tile done = latchedTile;
                unlatch();
                resetAttempts();
                if (mode.equals("gone"))
                    return true;
                // inv-full keeps harvesting: fall through to pick the next one
            } else {
                // keep the last-known tile fresh for kill recording
                try {
                    if (latchedNpc != null) latchedTile = latchedNpc.getTile();
                    else if (latchedObj != null) latchedTile = latchedObj.getTile();
                } catch (Throwable ignored) {}

                if (combat && latchedNpc != null) {
                    boolean engaged;
                    try { engaged = latchedNpc.isInCombat(); } catch (Throwable t) { engaged = true; }
                    long sinceIssue = System.currentTimeMillis() - lastIssueAt;
                    if (!engaged && ActionUtil.isIdle() && sinceIssue > 2500) {
                        // Not fighting and we are idle: either our click missed or someone else
                        // finished/claimed it. Re-issue on the SAME latched target once; if it
                        // engages elsewhere next poll, drop the latch and pick another (soft
                        // retry - other candidates exist, so this costs no attempts).
                        if (!issueInteract(verb))
                            unlatch();
                    }
                } else if (!combat && ActionUtil.isIdle()
                        && System.currentTimeMillis() - lastIssueAt > 2500) {
                    issueInteract(verb);   // gathering: keep working the same node/tree
                }
                return false;              // still waiting on the latched target
            }
        }

        // ── no latch: acquire one ──
        pickCandidate(name, radius, combat);

        if (latchedNpc == null && latchedObj == null) {
            // Nothing valid in range. For combat this includes "all cows are taken": that's a
            // real attempt (rate-limited to one per 2s so the budget means seconds, not polls).
            if (mode.equals("inv-full") && !combat)
                return true;               // nothing left to gather - complete, don't stall
            long now = System.currentTimeMillis();
            if (now - lastNoCandidateNoteAt > 2000) {
                lastNoCandidateNoteAt = now;
                noteAttempt();
            }
            return false;
        }

        boolean sent = issueInteract(verb);
        if (mode.equals("once")) {
            if (sent) { resetAttempts(); unlatch(); return true; }
            noteAttempt();
            unlatch();
            return false;
        }
        if (!sent) {
            noteAttempt();
            unlatch();
        }
        return false;
    }

    @Override
    public JPanel createParamPanel() {
        JPanel target = createParameterPanel("Target:",
                "The NPC or object to interact with (nearest match wins).",
                paramTarget, "  e.g. \"Cow\", \"Oak tree\", \"Bank booth\"");

        JPanel action = createParameterPanel("Action:",
                "The right-click option / verb to use on it.",
                paramAction, "  e.g. \"Attack\", \"Chop down\", \"Mine\", \"Talk-to\", \"Open\"");

        JPanel radius = createParameterPanel("Radius:",
                "Only match targets within this many tiles.",
                paramRadius, "  e.g. \"12\"");

        JPanel until = createParameterPanel("Wait until:",
                "When this step is finished: auto / once / gone / inv-full. \"gone\" tracks the"
                        + " exact target you engaged - other same-name NPCs nearby don't matter.",
                paramUntil, "  auto = smart · gone = YOUR target dead/despawned · inv-full = bag full");

        JPanel retries = createParameterPanel("Retries:",
                "Failed tries before the task gives up (nothing in range / clicks failing)."
                        + " A busy target never costs a retry - another one is picked instead.",
                paramRetries, "  e.g. \"12\" or \"inf\" to never give up");

        return ActionUtil.stack(target, action, radius, until, retries);
    }

    @Override
    public String getParamTarget() {
        return paramTarget.getParam();
    }

    @Override
    public String toBuildString() {
        return "Interact → " + paramAction.getParam() + " " + paramTarget.getParam()
                + " (until " + resolveMode() + ")";
    }

    @Override
    public Action copy() {
        return new Interact(this);
    }

    @Override
    public Map<String, String> serialize() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("Target", paramTarget.getParam());
        m.put("Action", paramAction.getParam());
        m.put("Radius", paramRadius.getParam());
        m.put("Until", paramUntil.getParam());
        m.put("Retries", paramRetries.getParam());
        return m;
    }

    @Override
    public void deserialize(Map<String, String> data) {
        if (data == null) return;
        if (data.get("Target") != null) paramTarget.setParam(data.get("Target"));
        if (data.get("Action") != null) paramAction.setParam(data.get("Action"));
        if (data.get("Radius") != null) paramRadius.setParam(data.get("Radius"));
        if (data.get("Until")  != null) paramUntil.setParam(data.get("Until"));
        if (data.get("Retries") != null) paramRetries.setParam(data.get("Retries"));
    }
}
