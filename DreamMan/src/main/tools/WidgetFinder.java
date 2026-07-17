package main.tools;

import org.dreambot.api.wrappers.widgets.WidgetChild;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Widget lookups via reflection (v1.31 hotfix). The static accessors on
 * {@code org.dreambot.api.methods.widget.Widgets} vary between DreamBot builds - the user's
 * client rejected {@code getWidgetChild(int,int)} and {@code getWidgetChildrenContainingText}
 * outright - so instead of guessing one signature at compile time, this resolves whatever the
 * running client actually offers, once, and caches it. {@link WidgetChild} itself is stable
 * (interact/getText/isVisible/getChildren all compile), so only the FINDING is reflective.
 *
 * <p>When no by-text accessor exists at all, a deep scan walks every root widget's children
 * (reflectively) and matches text - slower, but correct on any build.
 */
public final class WidgetFinder {

    private WidgetFinder() {}

    private static final String WIDGETS = "org.dreambot.api.methods.widget.Widgets";

    private static volatile boolean resolved;
    private static Method mChildTwo;       // Widgets.xxx(int,int)  -> WidgetChild
    private static Method mChildThree;     // Widgets.xxx(int,int,int) -> WidgetChild
    private static Method mByText;         // Widgets.xxx(String) or (String...) -> List/array
    private static Method mRoots;          // Widgets.getAllWidgets()/getWidgets()/getAll()
    private static Method mWidgetChildren; // <root widget>.getChildren() (resolved lazily)
    private static Method mWidgetChild;    // <root widget>.getChild(int)
    private static Method mChildName;      // WidgetChild.getName() (resolved lazily)

    private static synchronized void resolve() {
        if (resolved) return;
        resolved = true;
        try {
            Class<?> w = Class.forName(WIDGETS);
            for (Method m : w.getMethods()) {
                Class<?>[] p = m.getParameterTypes();
                String n = m.getName().toLowerCase(Locale.ROOT);
                boolean returnsChild = WidgetChild.class.isAssignableFrom(m.getReturnType());
                if (returnsChild && p.length == 2 && p[0] == int.class && p[1] == int.class
                        && mChildTwo == null) mChildTwo = m;
                if (returnsChild && p.length == 3 && p[0] == int.class && p[1] == int.class
                        && p[2] == int.class && mChildThree == null) mChildThree = m;
                if (n.contains("containingtext") && p.length == 1
                        && (p[0] == String.class || p[0] == String[].class)
                        && mByText == null) mByText = m;
                if (p.length == 0 && (n.equals("getallwidgets") || n.equals("getwidgets")
                        || n.equals("getall") || n.equals("getloadedwidgets"))
                        && mRoots == null) mRoots = m;
            }
        } catch (Throwable ignored) { /* running without a client (tests) */ }
    }

    /** The widget at parent/child[/sub], or null. sub < 0 means "no sub-child". */
    public static WidgetChild byIds(int parent, int child, int sub) {
        resolve();
        try {
            if (sub >= 0 && mChildThree != null)
                return (WidgetChild) mChildThree.invoke(null, parent, child, sub);
            WidgetChild base = null;
            if (mChildTwo != null)
                base = (WidgetChild) mChildTwo.invoke(null, parent, child);
            if (base == null && mRoots != null) {
                Object root = rootWidget(parent);
                if (root != null) base = childOf(root, child);
            }
            if (base == null) return null;
            if (sub < 0) return base;
            WidgetChild[] kids = base.getChildren();
            if (kids != null && sub < kids.length) return kids[sub];
            return null;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Every visible widget whose TEXT <b>or NAME</b> contains {@code text} (case-insensitive,
     * colour tags stripped). Never null.
     *
     * <p>The name matters: production-interface item slots (the furnace's "Bronze bar") are
     * MODEL widgets - getText() is empty and the human-readable string lives in the widget's
     * name, exactly what the client shows in hover tooltips.
     */
    public static List<WidgetChild> containingText(String text) {
        resolve();
        List<WidgetChild> out = new ArrayList<>();
        if (text == null || text.isBlank()) return out;
        String needle = text.toLowerCase(Locale.ROOT);

        // 1) the client's own by-text accessor, when it has one
        if (mByText != null) {
            try {
                Object r = mByText.getParameterTypes()[0] == String[].class
                        ? mByText.invoke(null, (Object) new String[]{text})
                        : mByText.invoke(null, text);
                collectChildren(r, out);
                if (!out.isEmpty()) return out;
            } catch (Throwable ignored) {}
        }

        // 2) deep scan: every root widget's children, plus nested children
        if (mRoots != null) {
            try {
                Object roots = mRoots.invoke(null);
                if (roots instanceof Collection<?>) {
                    for (Object root : (Collection<?>) roots) scanRoot(root, needle, out);
                } else if (roots instanceof Object[]) {
                    for (Object root : (Object[]) roots) scanRoot(root, needle, out);
                }
            } catch (Throwable ignored) {}
        }
        return out;
    }

    // ── plumbing ──────────────────────────────────────────────────────────────

    private static Object rootWidget(int id) {
        try {
            Class<?> w = Class.forName(WIDGETS);
            for (Method m : w.getMethods()) {
                Class<?>[] p = m.getParameterTypes();
                if (p.length == 1 && p[0] == int.class
                        && !WidgetChild.class.isAssignableFrom(m.getReturnType())
                        && m.getName().toLowerCase(Locale.ROOT).startsWith("getwidget"))
                    return m.invoke(null, id);
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static WidgetChild childOf(Object rootWidget, int idx) {
        if (rootWidget == null) return null;
        try {
            if (mWidgetChild == null || mWidgetChild.getDeclaringClass() != rootWidget.getClass()) {
                for (Method m : rootWidget.getClass().getMethods()) {
                    if (m.getName().equals("getChild") && m.getParameterCount() == 1
                            && m.getParameterTypes()[0] == int.class) { mWidgetChild = m; break; }
                }
            }
            if (mWidgetChild != null) {
                Object c = mWidgetChild.invoke(rootWidget, idx);
                if (c instanceof WidgetChild) return (WidgetChild) c;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static void scanRoot(Object rootWidget, String needle, List<WidgetChild> out) {
        if (rootWidget == null) return;
        try {
            if (mWidgetChildren == null
                    || !mWidgetChildren.getDeclaringClass().isAssignableFrom(rootWidget.getClass())) {
                for (Method m : rootWidget.getClass().getMethods()) {
                    if (m.getName().equals("getChildren") && m.getParameterCount() == 0) {
                        mWidgetChildren = m;
                        break;
                    }
                }
            }
            if (mWidgetChildren == null) return;
            collectMatches(mWidgetChildren.invoke(rootWidget), needle, out);
        } catch (Throwable ignored) {}
    }

    private static void collectMatches(Object kids, String needle, List<WidgetChild> out) {
        if (kids == null) return;
        Iterable<?> it = kids instanceof Collection<?> ? (Collection<?>) kids
                : kids instanceof Object[] ? java.util.Arrays.asList((Object[]) kids) : null;
        if (it == null) return;
        for (Object o : it) {
            if (!(o instanceof WidgetChild)) continue;
            WidgetChild c = (WidgetChild) o;
            try {
                if (c.isVisible()
                        && (containsClean(c.getText(), needle)
                            || containsClean(nameOf(c), needle)))
                    out.add(c);
                WidgetChild[] nested = c.getChildren();
                if (nested != null) collectMatches(nested, needle, out);
            } catch (Throwable ignored) {}
        }
    }

    /** The widget's name via reflection (not on the compile-time surface), or null. */
    private static String nameOf(WidgetChild c) {
        try {
            if (mChildName == null) {
                for (String n : new String[]{"getName", "getWidgetName"}) {
                    try {
                        Method m = c.getClass().getMethod(n);
                        if (m.getReturnType() == String.class) { mChildName = m; break; }
                    } catch (Throwable ignored) {}
                }
            }
            if (mChildName != null) return (String) mChildName.invoke(c);
        } catch (Throwable ignored) {}
        return null;
    }

    /** Case-insensitive contains with OSRS colour/format tags stripped ("<col=..>Bronze bar"). */
    private static boolean containsClean(String haystack, String needle) {
        if (haystack == null || haystack.isEmpty()) return false;
        return haystack.replaceAll("<[^>]*>", "")
                .toLowerCase(Locale.ROOT).contains(needle);
    }

    private static void collectChildren(Object r, List<WidgetChild> out) {
        if (r instanceof Collection<?>) {
            for (Object o : (Collection<?>) r) if (o instanceof WidgetChild) out.add((WidgetChild) o);
        } else if (r instanceof Object[]) {
            for (Object o : (Object[]) r) if (o instanceof WidgetChild) out.add((WidgetChild) o);
        } else if (r instanceof WidgetChild) {
            out.add((WidgetChild) r);
        }
    }
}
