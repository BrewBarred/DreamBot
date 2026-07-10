package main.data.store;

import java.util.ArrayList;
import java.util.List;

/**
 * The unfinished task the user was assembling in the Task Builder tab, so a draft survives
 * a restart. Mirrors the useful part of {@code BuilderSnapshot} without the two broken fields
 * (a raw target string and a raw Map) that caused the old builder-restore NPE.
 */
public class BuilderData {
    public String taskName;
    public String taskDescription;
    public String taskStatus;
    /** Patch B: the builder's auto-delay checkbox + range, so the draft restores completely. */
    public boolean autoDelay = false;
    public int autoDelayMinMs = 600;
    public int autoDelayMaxMs = 1400;
    public List<main.data.ActionData> actions = new ArrayList<>();
}
