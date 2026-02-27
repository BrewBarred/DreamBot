package main.actions;

import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

// -----------------------------------------------------------------------
// Entry — holds how to BUILD and how to SCAN for each action type
// -----------------------------------------------------------------------
public class ActionLoader {
    public final Supplier<Action> constructor;
    public final Supplier<Set<String>> scanner;

    public ActionLoader(Supplier<Action> smartConstructor, Supplier<Set<String>> scanner) {
        this.constructor = smartConstructor;
        this.scanner = scanner;
    }
}