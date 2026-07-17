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
    // v1.32b: gather-mode completion tracking (mining/woodcutting/fishing). We finish ONE
    // resource by watching the player animate then stop, or the node deplete - not by waiting
    // for an inventory slot to fill (which deadlocks on a full bag and false-succeeds on theft).
    private transient boolean gatherAnimating;     // have we seen the gathering animation start?
    private transient int gatherStartCount;        // inventory size when this node was latched
    private transient long gatherIssuedAt;         // when we last issued a gather interact

    /** v1.31: inventory requirements gating completion ("" = off). */
    private JParamTextField paramExpect;
    /** v1.31 hotfix: count GAINS since the step started instead of absolute totals. */
    private JCheckBox chkExpectGains;
    /** Baseline counts snapshotted when the step starts (gains mode only). */
    private transient java.util.Map<String, Integer> expectStart;
    /** Running total at the last gate check - PROGRESS makes completions free (hotfix2). */
    private transient int lastExpectHave = -1;

    public Interact() {
        super();
        paramTarget  = new JParamTextField("Cow");
        paramAction  = new JParamTextField("Attack");
        paramRadius  = new JParamTextField("12");
        paramUntil   = new JParamTextField("auto");
        paramRetries = new JParamTextField("12");
        paramExpect  = new JParamTextField("");
        chkExpectGains = new JCheckBox("Count gains since this step started (not totals)");
        chkExpectGains.setOpaque(false);
        chkExpectGains.setToolTipText("<html>OFF (totals): complete when the inventory HOLDS the"
                + " listed counts; already holding them skips the step.<br>ON (gains): complete"
                + " when this step has GAINED the listed counts - use this for alternating"
                + " loops (mine 1 copper, then 1 tin, repeat) where leftovers from the last"
                + " lap must not skip the step.</html>");
    }

    public Interact(Interact o) {
        this();
        paramTarget.setParam(o.paramTarget.getParam());
        paramAction.setParam(o.paramAction.getParam());
        paramRadius.setParam(o.paramRadius.getParam());
        paramUntil.setParam(o.paramUntil.getParam());
        paramRetries.setParam(o.paramRetries.getParam());
        paramExpect.setParam(o.paramExpect.getParam());
        chkExpectGains.setSelected(o.chkExpectGains.isSelected());
    }

    private boolean isCombatVerb() {
        String v = paramAction.getParam() == null ? "" : paramAction.getParam().toLowerCase();
        return v.contains("attack") || v.contains("fight") || v.contains("kill");
    }

    private String resolveMode() {
        String m = paramUntil.getParam() == null ? "auto" : paramUntil.getParam().trim().toLowerCase();
        if (m.equals("auto"))
            // v1.32b: non-combat auto is now "gather" (animation/depletion aware) rather than
            // "once" (fire-and-forget). Gathering waits for the resource to actually be worked.
            return isCombatVerb() ? "gone" : "gather";
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

    /** v1.32b: is the local player mid-animation (mining/chopping/fishing swing)? */
    private static boolean isPlayerAnimating() {
        try {
            var p = org.dreambot.api.methods.interactive.Players.getLocal();
            return p != null && p.getAnimation() != -1;
        } catch (Throwable t) {
            return false;
        }
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
        // ── v1.31: expected-items gate (reworked in the hotfix) ──────────────
        // Two interpretations of "Expect: Copper ore x1":
        //   TOTALS (checkbox off) - complete when the inventory HOLDS the counts; already
        //     holding them skips the step. Right for "gather until I have N" flows where the
        //     items get banked/used before the next lap.
        //   GAINS (checkbox on) - complete when this step has GAINED the counts since it
        //     started. Right for alternating loops (mine 1 copper, then 1 tin, repeat) where
        //     last lap's leftovers must NOT skip the step or starve one rock. The baseline is
        //     snapshotted each time the step begins.
        java.util.Map<String, Integer> expect = ActionUtil.parseItemList(paramExpect.getParam());
        boolean gains = chkExpectGains.isSelected();
        if (!expect.isEmpty()) {
            if (gains && expectStart == null) {
                expectStart = new java.util.HashMap<>();
                for (String item : expect.keySet())
                    expectStart.put(item, invCount(item));
            }
            if (lastExpectHave < 0)
                lastExpectHave = expectHaveTotal(expect, gains);   // baseline for progress
            if (expectSatisfied(expect, gains)) {
                unlatch();
                expectStart = null;
                lastExpectHave = -1;
                return true;   // totals: pre-satisfied skip · gains: target reached mid-step
            }
            // v1.32b: a FULL inventory ends the step even if the count isn't met - you can't
            // collect any more, so spinning retries is pointless. Whatever you gathered is the
            // result; the next task (bank / drop) takes over. This is the core "gets stuck on
            // a full bag" fix - gathering completion no longer depends on a free slot.
            if (Inventory.isFull()) {
                unlatch();
                expectStart = null;
                lastExpectHave = -1;
                return true;
            }
        }
        boolean done = executeCore();
        if (done && !expect.isEmpty() && !expectSatisfied(expect, gains)) {
            // The interaction finished (target gone / inv full) but the goal isn't met yet.
            // hotfix2: completions that PROGRESSED the counts are FREE - mining 26 rocks
            // toward "Copper ore x13, Tin ore x13" must not eat 26 retries. Only fruitless
            // completions (sniped rock, missed swing, wrong drop) burn the budget, so
            // "Retries: 12" means twelve consecutive failures, not twelve rocks.
            int haveNow = expectHaveTotal(expect, gains);
            boolean progressed = haveNow > lastExpectHave;
            lastExpectHave = haveNow;
            unlatch();
            if (!progressed) noteAttempt();
            return false;
        }
        if (done) { expectStart = null; lastExpectHave = -1; }
        return done;
    }

    /** Sum of (gains-adjusted) counts across the expected items - the progress meter. */
    private int expectHaveTotal(java.util.Map<String, Integer> expect, boolean gains) {
        int total = 0;
        for (String item : expect.keySet()) {
            int have = invCount(item);
            if (gains && expectStart != null) have -= expectStart.getOrDefault(item, 0);
            total += Math.max(0, have);
        }
        return total;
    }

    private static int invCount(String item) {
        try { return org.dreambot.api.methods.container.impl.Inventory.count(item); }
        catch (Throwable t) { return 0; }
    }

    private boolean expectSatisfied(java.util.Map<String, Integer> expect, boolean gains) {
        for (java.util.Map.Entry<String, Integer> e : expect.entrySet()) {
            int have = invCount(e.getKey());
            if (gains && expectStart != null)
                have -= expectStart.getOrDefault(e.getKey(), 0);
            if (have < e.getValue()) return false;
        }
        return true;
    }

    @Override
    public void resetAttempts() {
        super.resetAttempts();
        // v1.31 hotfix: the engine calls this when the step (re)starts - a fresh gains
        // baseline belongs to each entry, so "mine 1 more" means one more THIS lap.
        expectStart = null;
        lastExpectHave = -1;
    }

    private boolean executeCore() {
        String name = paramTarget.getParam();
        String verb = paramAction.getParam();
        int radius = ActionUtil.parseInt(paramRadius.getParam(), 12);
        String mode = resolveMode();
        boolean combat = isCombatVerb();
        syncRetryBudget();

        if ((mode.equals("inv-full") || mode.equals("gather")) && Inventory.isFull()) {
            // v1.32b: a full bag ends gathering (move on to banking) rather than spinning.
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
                boolean minedItOurselves = gatherAnimating;   // v1.32b
                unlatch();
                resetAttempts();
                if (mode.equals("gone"))
                    return true;
                if (mode.equals("gather")) {
                    // v1.32b: the node depleted. If WE were animating it, that's a finished
                    // gather - complete this cycle. If we never animated (someone else mined it,
                    // or we were still walking up), just re-target: no false success.
                    if (minedItOurselves) return true;
                    // else fall through to pick another node (costs no attempts)
                }
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
                } else if (mode.equals("gather")) {
                    // v1.32b: gather completion by ANIMATION, not inventory gain. While the
                    // pickaxe/axe/rod is working we wait; when it STOPS and we've gone idle this
                    // resource is done - complete the cycle (the Expect gate, if set, decides
                    // whether to gather another). Issued but never animated = the click missed,
                    // so re-issue. This is what makes a full bag or a stolen node not deadlock
                    // or false-succeed: completion tracks the actual gathering action.
                    long sinceIssue = System.currentTimeMillis() - gatherIssuedAt;
                    if (isPlayerAnimating()) {
                        gatherAnimating = true;
                    } else if (gatherAnimating && ActionUtil.isIdle() && sinceIssue > 600) {
                        unlatch();
                        resetAttempts();
                        return true;                       // animated then stopped = one gather
                    } else if (!gatherAnimating && ActionUtil.isIdle() && sinceIssue > 2500) {
                        if (!issueInteract(verb)) unlatch();
                        gatherIssuedAt = System.currentTimeMillis();
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
        if (mode.equals("gather")) {
            // v1.32b: node latched and interact issued - start a gather cycle and wait for the
            // animation to run and stop (handled on subsequent polls above).
            if (sent) {
                gatherAnimating = false;
                gatherIssuedAt = System.currentTimeMillis();
                return false;
            }
            noteAttempt();
            unlatch();
            return false;
        }
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
                "When this step is finished: auto / once / gone / inv-full. For gathering (mine,"
                        + " chop, fish) auto watches the animation + node depletion - a full bag"
                        + " or a stolen rock won't stall it. \"gone\" tracks the exact target you"
                        + " engaged - other same-name NPCs nearby don't matter.",
                paramUntil, "  auto = smart · gone = YOUR target dead/despawned · inv-full = bag full");

        JPanel retries = createParameterPanel("Retries:",
                "Failed tries before the task gives up (nothing in range / clicks failing)."
                        + " A busy target never costs a retry - another one is picked instead.",
                paramRetries, "  e.g. \"12\" or \"inf\" to never give up");

        JPanel expect = createParameterPanel("Expect items:",
                "The step only completes once these counts are met - \"mine until the ore is"
                        + " actually in your bag\". The checkbox below picks HOW they're"
                        + " counted. Blank = off. Tip: the Target field takes comma lists"
                        + " (\"Copper rocks, Tin rocks\") and works whichever is closest.",
                paramExpect, "  e.g. \"Copper ore x1, Tin ore x1\"  or  \"Coal x8\"");
        JPanel gainsRow = new JPanel(new java.awt.BorderLayout());
        gainsRow.setOpaque(false);
        gainsRow.add(chkExpectGains, java.awt.BorderLayout.WEST);

        return ActionUtil.stack(target, action, radius, until, retries, expect, gainsRow);
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
        m.put("Expect", paramExpect.getParam());
        m.put("ExpectMode", chkExpectGains.isSelected() ? "gains" : "total");
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
        if (data.get("Expect") != null) paramExpect.setParam(data.get("Expect"));
        if (data.get("ExpectMode") != null)
            chkExpectGains.setSelected("gains".equalsIgnoreCase(data.get("ExpectMode").trim()));
    }
}
