package main.actions;

import main.components.JParamTextField;
import main.tools.Rand;

import javax.swing.*;
import java.util.LinkedHashMap;
import java.util.Map;

import static main.menu.MenuHandler.createParameterPanel;

/**
 * Waits between actions - the humanising glue. Patch B (v1.0.11) adds one-click modes backed by
 * the {@link Rand} delay tiers, so you pick a feel instead of always typing numbers:
 * <ul>
 *   <li><b>Fixed range</b> - the original behaviour: a random wait between Min and Max ms.</li>
 *   <li><b>Quick</b> - tiny jitter for tight sequences ({@link Rand#quickDelay()}, ~60-220ms).</li>
 *   <li><b>Realistic</b> - a natural between-actions pause ({@link Rand#actionDelay()}, roughly
 *       0.3-3s with randomised bounds for extra spread).</li>
 *   <li><b>Semi-AFK xN</b> - a long look-away delay ({@link Rand#afkDelay(int)}, ~1.8-4.2s times
 *       the multiplier).</li>
 * </ul>
 * Non-blocking: it reports "not done" until the delay elapses (so pausing still works), then
 * resets so a repeated task re-randomises the wait each pass. Saved tasks from before this patch
 * load unchanged (missing Mode defaults to Fixed range).
 */
public class Wait extends Action {

    /** Mode keys as serialised; order matches {@link #MODE_LABELS}. */
    static final String[] MODE_KEYS   = { "fixed", "quick", "realistic", "afk" };
    static final String[] MODE_LABELS = { "Fixed range", "Quick (~60-220ms)", "Realistic (~0.3-3s)", "Semi-AFK xN" };

    private JParamTextField paramMin;
    private JParamTextField paramMax;
    private JParamTextField paramAfkMult;
    private JComboBox<String> modeCombo;

    /** One of {@link #MODE_KEYS}; kept as a field so copy/serialize work without the combo. */
    private String mode = "fixed";

    /** Transient countdown target (not serialised). 0 = not currently waiting. */
    private long endTime = 0L;

    public Wait() {
        super();
        paramMin = new JParamTextField("400");
        paramMax = new JParamTextField("1200");
        paramAfkMult = new JParamTextField("1");
    }

    public Wait(Wait o) {
        this();
        paramMin.setParam(o.paramMin.getParam());
        paramMax.setParam(o.paramMax.getParam());
        paramAfkMult.setParam(o.paramAfkMult.getParam());
        this.mode = o.mode;
    }

    /** Picks the next delay for the current mode (called once per wait cycle). */
    private int rollDelay() {
        switch (mode) {
            case "quick":     return Rand.quickDelay();
            case "realistic": return Rand.actionDelay();
            case "afk":       return Rand.afkDelay(ActionUtil.parseInt(paramAfkMult.getParam(), 1));
            default: {
                int min = ActionUtil.parseInt(paramMin.getParam(), 400);
                int max = ActionUtil.parseInt(paramMax.getParam(), 1200);
                if (min < 0) min = 0;
                if (max < min) max = min;
                return Rand.nextInt(min, max);
            }
        }
    }

    @Override
    public boolean execute() {
        long now = System.currentTimeMillis();
        if (endTime == 0L)
            endTime = now + rollDelay();

        if (now >= endTime) {
            endTime = 0L;          // reset so the next run re-randomises
            return true;
        }
        return false;
    }

    @Override
    public JPanel createParamPanel() {
        // A fresh combo per panel build (panels are rebuilt on every selection change); the
        // `mode` field is the source of truth, so no state lives in the old combo.
        modeCombo = new JComboBox<>(MODE_LABELS);
        modeCombo.setSelectedIndex(modeIndex(mode));

        JPanel modePanel = createParameterPanel("Mode:",
                "How the wait length is chosen. Quick / Realistic / Semi-AFK use humanised tiers.",
                modeCombo, "");

        JPanel min = createParameterPanel("Min (ms):",
                "Shortest wait, in milliseconds (Fixed range only).",
                paramMin, "  e.g. \"400\"");

        JPanel max = createParameterPanel("Max (ms):",
                "Longest wait, in milliseconds (Fixed range only).",
                paramMax, "  e.g. \"1200\"");

        JPanel mult = createParameterPanel("AFK x:",
                "Multiplier for the Semi-AFK tier (1 = ~1.8-4.2s, 3 = ~5.4-12.6s).",
                paramAfkMult, "  e.g. \"2\"");

        // show only the fields the selected mode actually uses
        final JComboBox<String> combo = modeCombo;
        Runnable sync = () -> {
            String m = MODE_KEYS[Math.max(0, combo.getSelectedIndex())];
            mode = m;
            boolean fixed = "fixed".equals(m);
            boolean afk   = "afk".equals(m);
            min.setVisible(fixed);
            max.setVisible(fixed);
            mult.setVisible(afk);
        };
        combo.addActionListener(e -> sync.run());
        sync.run();

        return ActionUtil.stack(modePanel, min, max, mult);
    }

    private static int modeIndex(String key) {
        for (int i = 0; i < MODE_KEYS.length; i++)
            if (MODE_KEYS[i].equals(key)) return i;
        return 0;
    }

    @Override
    public String getParamTarget() {
        switch (mode) {
            case "quick":     return "Quick";
            case "realistic": return "Realistic";
            case "afk":       return "AFK x" + ActionUtil.parseInt(paramAfkMult.getParam(), 1);
            default:          return paramMin.getParam() + "-" + paramMax.getParam() + "ms";
        }
    }

    @Override
    public String toBuildString() {
        return "Wait → " + getParamTarget();
    }

    @Override
    public Action copy() {
        return new Wait(this);
    }

    @Override
    public Map<String, String> serialize() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("Mode", mode);
        m.put("Min", paramMin.getParam());
        m.put("Max", paramMax.getParam());
        m.put("Afk", paramAfkMult.getParam());
        return m;
    }

    @Override
    public void deserialize(Map<String, String> data) {
        if (data == null) return;
        if (data.get("Mode") != null) mode = data.get("Mode");   // absent in pre-B saves -> "fixed"
        if (data.get("Min") != null) paramMin.setParam(data.get("Min"));
        if (data.get("Max") != null) paramMax.setParam(data.get("Max"));
        if (data.get("Afk") != null) paramAfkMult.setParam(data.get("Afk"));
        // the mode combo is rebuilt from `mode` on the next createParamPanel(), so no UI sync here
    }
}
