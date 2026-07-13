package main.actions;

import main.components.JParamTextField;
import org.dreambot.api.methods.dialogues.Dialogues;

import javax.swing.*;
import java.util.LinkedHashMap;
import java.util.Map;

import static main.menu.MenuHandler.createParameterPanel;

/**
 * Handles NPC dialogue (Patch B.2 - roadmap Patch D pulled forward). Pair it with an
 * Interact("Talk-to") step, then one Chat step drives the conversation:
 *
 * <ul>
 *   <li><b>continue</b> - click-to-continue through the whole dialogue.</li>
 *   <li><b>option</b> - pick option #N whenever choices appear, continue otherwise.</li>
 *   <li><b>sequence</b> - reply pattern like "1,3,1": pick 1 at the first choice, 3 at the
 *       second, 1 at the third, continuing between them. The classic quest-dialogue pattern.</li>
 * </ul>
 * Completes when the dialogue ends (after having actually been in one). If no dialogue opens
 * within the retry budget the task fails rather than waiting forever.
 */
public class Chat extends Action {

    private JParamTextField paramMode;
    private JParamTextField paramSequence;

    private transient boolean sawDialogue;
    private transient int seqIndex;
    private transient long lastActAt;
    private transient long waitingSince;

    public Chat() {
        super();
        paramTarget = new JParamTextField("continue");   // reused as the mode field target label
        paramMode = paramTarget;
        paramSequence = new JParamTextField("1");
    }

    public Chat(Chat o) {
        this();
        paramMode.setParam(o.paramMode.getParam());
        paramSequence.setParam(o.paramSequence.getParam());
    }

    private String mode() {
        String m = paramMode.getParam() == null ? "continue" : paramMode.getParam().trim().toLowerCase();
        if (m.startsWith("opt")) return "option";
        if (m.startsWith("seq")) return "sequence";
        return "continue";
    }

    private int[] sequence() {
        String raw = paramSequence.getParam() == null ? "1" : paramSequence.getParam();
        String[] parts = raw.split("[,;\\s]+");
        int[] out = new int[Math.max(1, parts.length)];
        int n = 0;
        for (String p : parts) {
            if (p == null || p.isEmpty()) continue;
            out[n++] = Math.max(1, ActionUtil.parseInt(p.trim(), 1));
        }
        if (n == 0) { out[0] = 1; n = 1; }
        int[] trimmed = new int[n];
        System.arraycopy(out, 0, trimmed, 0, n);
        return trimmed;
    }

    private void resetState() {
        sawDialogue = false;
        seqIndex = 0;
        waitingSince = 0;
    }

    @Override
    public boolean execute() {
        boolean in;
        try { in = Dialogues.inDialogue(); } catch (Throwable t) { in = false; }

        long now = System.currentTimeMillis();

        if (!in) {
            if (sawDialogue) {           // we were talking and it ended - done
                resetState();
                resetAttempts();
                return true;
            }
            // waiting for a dialogue to open (the Talk-to click may still be in flight)
            if (waitingSince == 0) waitingSince = now;
            if (now - waitingSince > 2500) {
                waitingSince = now;
                noteAttempt();            // no dialogue appearing: counts against retries
            }
            return false;
        }

        sawDialogue = true;
        if (now - lastActAt < 700)        // human-ish pacing between dialogue clicks
            return false;
        lastActAt = now;

        try {
            switch (mode()) {
                case "option": {
                    int opt = sequence()[0];
                    if (!Dialogues.chooseOption(opt))
                        if (Dialogues.canContinue()) Dialogues.continueDialogue();
                    break;
                }
                case "sequence": {
                    int[] seq = sequence();
                    if (seqIndex < seq.length && Dialogues.chooseOption(seq[seqIndex])) {
                        seqIndex++;
                    } else if (Dialogues.canContinue()) {
                        Dialogues.continueDialogue();
                    }
                    break;
                }
                default:
                    if (Dialogues.canContinue()) Dialogues.continueDialogue();
                    else Dialogues.chooseOption(1);   // stuck on a choice: take the first
            }
        } catch (Throwable ignored) {}
        return false;
    }

    @Override
    public JPanel createParamPanel() {
        JPanel mode = createParameterPanel("Mode:",
                "continue = click through everything · option = always pick choice #N ·"
                        + " sequence = a reply pattern.",
                paramMode, "  e.g. \"continue\", \"option\", \"sequence\"");

        JPanel seq = createParameterPanel("Option / sequence:",
                "For option: the choice number. For sequence: comma-separated replies in order.",
                paramSequence, "  e.g. \"2\" or \"1,3,1\"");

        return ActionUtil.stack(mode, seq);
    }

    @Override
    public String getParamTarget() {
        return mode().equals("continue") ? "continue" : mode() + " " + paramSequence.getParam();
    }

    @Override
    public String toBuildString() { return "Chat → " + getParamTarget(); }

    @Override
    public Action copy() { return new Chat(this); }

    @Override
    public Map<String, String> serialize() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("Mode", paramMode.getParam());
        m.put("Sequence", paramSequence.getParam());
        return m;
    }

    @Override
    public void deserialize(Map<String, String> data) {
        if (data == null) return;
        if (data.get("Mode") != null) paramMode.setParam(data.get("Mode"));
        if (data.get("Sequence") != null) paramSequence.setParam(data.get("Sequence"));
    }
}
