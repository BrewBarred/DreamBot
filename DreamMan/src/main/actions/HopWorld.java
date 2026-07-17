package main.actions;

import main.components.JParamTextField;

import javax.swing.*;
import java.util.LinkedHashMap;
import java.util.Map;

import static main.menu.MenuHandler.createParameterPanel;

/**
 * Hops world (v1.31), with an optional crowd condition: "only hop when at least X other
 * players are within Y tiles" - the ore-thief escape. When the crowd condition is set and NOT
 * met, the action completes instantly (no hop needed). Picks a random world matching your
 * current membership (f2p list on an f2p world, members list otherwise).
 */
public class HopWorld extends Action {

    @Override public boolean acceptsEntityTarget() { return false; }

    /** Curated stable world lists (avoid pvp/skill-total/speedrun worlds). */
    private static final int[] F2P = {301, 308, 316, 326, 335, 371, 379, 382, 394, 397, 398, 417, 436, 451};
    private static final int[] P2P = {302, 303, 304, 305, 306, 307, 309, 310, 312, 313, 314, 320, 321, 322};

    private JParamTextField paramPlayers;   // min OTHER players (0 = always hop)
    private JParamTextField paramRadius;

    private transient boolean hopIssued;
    private transient int fromWorld;
    private transient long issuedAt;

    public HopWorld() {
        super();
        paramPlayers = new JParamTextField("0");
        paramRadius = new JParamTextField("10");
        paramTarget = paramPlayers;
        maxAttempts = 6;
    }

    public HopWorld(HopWorld o) {
        this();
        paramPlayers.setParam(o.paramPlayers.getParam());
        paramRadius.setParam(o.paramRadius.getParam());
    }

    private int othersNearby(int radius) {
        try {
            var me = org.dreambot.api.methods.interactive.Players.getLocal();
            if (me == null) return 0;
            var myTile = me.getTile();
            var all = org.dreambot.api.methods.interactive.Players.all(p ->
                    p != null && p.getTile() != null && !p.getName().equals(me.getName()));
            if (all == null || myTile == null) return 0;
            int n = 0;
            for (var p : all)
                if (myTile.distance(p.getTile()) <= radius) n++;
            return n;
        } catch (Throwable t) { return 0; }
    }

    @Override
    public boolean execute() {
        try {
            if (hopIssued) {
                // v1.31 hotfix: the world number is read via WorldInfo (reflection) because
                // WorldHopper.getCurrentWorld() doesn't exist on this build. When NO accessor
                // exists (currentWorld()==0), completion falls back to "hop was issued and 5s
                // passed" - imperfect, but never wedges the queue.
                int now = main.tools.WorldInfo.currentWorld();
                if (now != 0 && fromWorld != 0 && now != fromWorld) { hopIssued = false; return true; }
                long since = System.currentTimeMillis() - issuedAt;
                if ((now == 0 || fromWorld == 0) && since > 5_000) { hopIssued = false; return true; }
                if (since > 12_000) { hopIssued = false; noteAttempt(); }
                return false;
            }

            int minPlayers = ActionUtil.parseInt(paramPlayers.getParam(), 0);
            int radius = Math.max(1, ActionUtil.parseInt(paramRadius.getParam(), 10));
            if (minPlayers > 0 && othersNearby(radius) < minPlayers)
                return true;   // not crowded - no hop needed, step done

            boolean members = false;
            try {
                var w = org.dreambot.api.methods.world.Worlds.getCurrent();
                members = w != null && w.isMembers();
            } catch (Throwable ignored) {}
            int[] pool = members ? P2P : F2P;
            fromWorld = main.tools.WorldInfo.currentWorld();   // v1.31 hotfix: 0 = unknown
            int pick;
            int guard = 0;
            do {
                pick = pool[java.util.concurrent.ThreadLocalRandom.current().nextInt(pool.length)];
            } while (pick == fromWorld && ++guard < 8);

            noteAttempt();
            if (org.dreambot.api.methods.worldhopper.WorldHopper.hopWorld(pick)) {
                hopIssued = true;
                issuedAt = System.currentTimeMillis();
            }
        } catch (Throwable ignored) { noteAttempt(); }
        return false;
    }

    @Override public String getParamTarget() { return paramPlayers.getParam(); }
    @Override public Action copy() { return new HopWorld(this); }
    @Override public String conflictGroup() { return "movement"; }

    @Override
    public JPanel createParamPanel() {
        JPanel pl = createParameterPanel("Only if players >=:",
                "Hop only when at least this many OTHER players are nearby. 0 = always hop. "
                        + "When the condition isn't met, the step completes without hopping.",
                paramPlayers, "  e.g. \"2\" (0 = always)");
        JPanel ra = createParameterPanel("Within (tiles):",
                "How close counts as \"nearby\" for the player check.",
                paramRadius, "  e.g. \"10\"");
        return ActionUtil.stack(pl, ra);
    }

    @Override
    public Map<String, String> serialize() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("MinPlayers", paramPlayers.getParam());
        m.put("Radius", paramRadius.getParam());
        return m;
    }

    @Override
    public void deserialize(Map<String, String> data) {
        if (data == null) return;
        if (data.get("MinPlayers") != null) paramPlayers.setParam(data.get("MinPlayers"));
        if (data.get("Radius") != null) paramRadius.setParam(data.get("Radius"));
    }
}
