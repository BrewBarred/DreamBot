package main.data;

import java.util.List;
import java.util.Map;

// --- Snapshot Classes & Capture Methods ---
public class BuilderSnapshot {
    public Map<String, String> selected;
    public String target;

    public String taskName;
    public String taskDescription;
    public String taskStatus;

    public List<ActionData> actions;
}
