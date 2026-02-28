//package main.actions;
//
//import java.util.Set;
//import java.util.function.Function;
//import java.util.function.Supplier;
//
//// -----------------------------------------------------------------------
//// Entry — holds how to BUILD and how to SCAN for each action type
//// -----------------------------------------------------------------------
//public class ActionLoader {
//    public final Supplier<Action> constructor;
//    public final Supplier<Set<String>> scanner;
//    public final Supplier<Boolean> updater;
//
//    public ActionLoader(Supplier<Action> constructor, Supplier<Set<String>> scanner, Supplier<Boolean> refresher) {
//        this.constructor = constructor;
//        this.scanner = scanner;
//        this.updater = refresher;
//    }
//}