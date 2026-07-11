package main.data.store;

import java.util.ArrayList;
import java.util.List;

/**
 * A whole task library exported to one shareable .json file (Patch B.3). Import offers to
 * EXTEND the current library (default) or OVERWRITE it. Single-task exports remain compatible:
 * the importer detects which format a file holds.
 */
public class LibraryData {
    public int version = 1;
    public long exportedAt;
    public List<TaskData> tasks = new ArrayList<>();
}
