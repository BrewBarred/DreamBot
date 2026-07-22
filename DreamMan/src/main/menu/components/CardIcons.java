package main.menu.components;

import main.menu.Theme;

import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Ready-made listing icons for the Card Builder (v1.61). A finished card needs an icon, and the
 * roadmap's rule is that nobody should have to hunt down an image file just to publish - so this
 * class supplies both halves of the fix:
 *
 * <ul>
 *   <li><b>{@link #defaults()}</b> - a small set of hand-drawn 128x128 icons (sword, pickaxe,
 *       potion, ...) in the app's own gold-on-dark theme, pick-one-and-go.
 *   <li><b>{@link #random(String)}</b> - a generated identicon: a mirrored 5x5 block pattern on a
 *       muted dark background, different every click. Seeding with the listing name plus the clock
 *       means "Random" always produces something new, never a repeat loop.
 * </ul>
 *
 * <p>Everything is drawn in code and encoded as base64 PNG on the spot, exactly the format the
 * v1.60 {@code ScriptListing.icon} field / {@code MarketCard.decodeIcon} pipeline already speaks.
 * A flat 128x128 PNG comes out at 1-4 KB - two orders of magnitude under the server's 300 KB cap -
 * so nothing generated here can ever trip the size check.
 */
public final class CardIcons {

    private CardIcons() {}

    /** Canvas size for every generated icon - the market's recommended icon size. */
    public static final int SIZE = 128;

    /** One named default: its display name and its base64 PNG payload. */
    public static final class DefaultIcon {
        public final String name;
        public final String base64;
        DefaultIcon(String name, String base64) { this.name = name; this.base64 = base64; }
    }

    // drawn once, then handed out; ~10 tiny PNGs is nothing to keep around
    private static List<DefaultIcon> cache;

    /** The built-in set, drawn lazily on first use. */
    public static synchronized List<DefaultIcon> defaults() {
        if (cache != null) return cache;
        List<DefaultIcon> out = new ArrayList<>();
        out.add(new DefaultIcon("Sword",    draw(CardIcons::sword)));
        out.add(new DefaultIcon("Shield",   draw(CardIcons::shield)));
        out.add(new DefaultIcon("Pickaxe",  draw(CardIcons::pickaxe)));
        out.add(new DefaultIcon("Axe",      draw(CardIcons::axe)));
        out.add(new DefaultIcon("Fish",     draw(CardIcons::fish)));
        out.add(new DefaultIcon("Potion",   draw(CardIcons::potion)));
        out.add(new DefaultIcon("Coins",    draw(CardIcons::coins)));
        out.add(new DefaultIcon("Star",     draw(CardIcons::star)));
        out.add(new DefaultIcon("Scroll",   draw(CardIcons::scroll)));
        out.add(new DefaultIcon("Gear",     draw(CardIcons::gear)));

        // v1.72: one icon per skill, plus the movement/utility set. Tinted so a wall of them is
        // scannable - all-gold glyphs at 40px were indistinguishable in the defaults popup.
        out.add(new DefaultIcon("Attack",        draw(CardIcons::sword,     C_COMBAT)));
        out.add(new DefaultIcon("Strength",      draw(CardIcons::fist,      C_COMBAT)));
        out.add(new DefaultIcon("Defence",       draw(CardIcons::shield,    C_COMBAT)));
        out.add(new DefaultIcon("Ranged",        draw(CardIcons::bow,       C_COMBAT)));
        out.add(new DefaultIcon("Magic",         draw(CardIcons::wand,      C_MAGIC)));
        out.add(new DefaultIcon("Prayer",        draw(CardIcons::prayer,    C_MAGIC)));
        out.add(new DefaultIcon("Runecrafting",  draw(CardIcons::rune,      C_MAGIC)));
        out.add(new DefaultIcon("Hitpoints",     draw(CardIcons::heart,     C_COMBAT)));
        out.add(new DefaultIcon("Agility",       draw(CardIcons::boot,      C_MOVE)));
        out.add(new DefaultIcon("Herblore",      draw(CardIcons::potion,    C_NATURE)));
        out.add(new DefaultIcon("Thieving",      draw(CardIcons::purse,     C_GOLD)));
        out.add(new DefaultIcon("Crafting",      draw(CardIcons::needle,    C_CRAFT)));
        out.add(new DefaultIcon("Fletching",     draw(CardIcons::arrow,     C_CRAFT)));
        out.add(new DefaultIcon("Slayer",        draw(CardIcons::skull,     C_COMBAT)));
        out.add(new DefaultIcon("Hunter",        draw(CardIcons::paw,       C_NATURE)));
        out.add(new DefaultIcon("Mining",        draw(CardIcons::pickaxe,   C_CRAFT)));
        out.add(new DefaultIcon("Smithing",      draw(CardIcons::anvil,     C_CRAFT)));
        out.add(new DefaultIcon("Fishing",       draw(CardIcons::fish,      C_WATER)));
        out.add(new DefaultIcon("Cooking",       draw(CardIcons::pot,       C_FIRE)));
        out.add(new DefaultIcon("Firemaking",    draw(CardIcons::flame,     C_FIRE)));
        out.add(new DefaultIcon("Woodcutting",   draw(CardIcons::axe,       C_NATURE)));
        out.add(new DefaultIcon("Farming",       draw(CardIcons::plant,     C_NATURE)));
        out.add(new DefaultIcon("Construction",  draw(CardIcons::hammer,    C_CRAFT)));
        out.add(new DefaultIcon("Sailing",       draw(CardIcons::anchor,    C_WATER)));
        out.add(new DefaultIcon("Teleport",      draw(CardIcons::portal,    C_MAGIC)));
        out.add(new DefaultIcon("Travel",        draw(CardIcons::compass,   C_MOVE)));
        out.add(new DefaultIcon("Banking",       draw(CardIcons::vault,     C_GOLD)));
        out.add(new DefaultIcon("Quest",         draw(CardIcons::scroll,    C_GOLD)));
        cache = out;
        return out;
    }

    /**
     * A fresh random identicon. The {@code salt} (typically the listing name) folds into the seed
     * so two scripts randomised in the same instant still differ; the time term makes every click
     * of the Random button a new pattern rather than a fixed hash of the name.
     */
    public static String random(String salt) {
        long seed = System.nanoTime() ^ (salt == null ? 0 : (long) salt.hashCode() << 17);
        Random rng = new Random(seed);

        // muted dark background hue + a brighter block tone in the same family, so a wall of
        // random icons still sits comfortably next to the drawn defaults
        float hue = rng.nextFloat();
        Color bg    = Color.getHSBColor(hue, 0.30f + rng.nextFloat() * 0.15f, 0.16f + rng.nextFloat() * 0.06f);
        Color block = Color.getHSBColor(hue, 0.42f + rng.nextFloat() * 0.25f, 0.62f + rng.nextFloat() * 0.28f);

        BufferedImage img = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(bg);
        g.fillRoundRect(0, 0, SIZE, SIZE, 24, 24);

        // 5x5 grid, left half random, right half mirrored - the classic identicon symmetry
        int cells = 5, pad = 14;
        int cell = (SIZE - pad * 2) / cells;
        boolean[][] on = new boolean[cells][cells];
        for (int x = 0; x <= cells / 2; x++)
            for (int y = 0; y < cells; y++) {
                boolean b = rng.nextFloat() < 0.46f;
                on[x][y] = b;
                on[cells - 1 - x][y] = b;
            }
        g.setColor(block);
        for (int x = 0; x < cells; x++)
            for (int y = 0; y < cells; y++)
                if (on[x][y])
                    g.fillRoundRect(pad + x * cell + 2, pad + y * cell + 2, cell - 4, cell - 4, 6, 6);

        g.setColor(new Color(block.getRed(), block.getGreen(), block.getBlue(), 90));
        g.setStroke(new BasicStroke(3f));
        g.drawRoundRect(1, 1, SIZE - 3, SIZE - 3, 24, 24);
        g.dispose();
        return toBase64Png(img);
    }

    /** Encodes an image as raw base64 PNG - the exact shape the icon field stores. */
    public static String toBase64Png(BufferedImage img) {
        try {
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(img, "png", bos);
            return java.util.Base64.getEncoder().encodeToString(bos.toByteArray());
        } catch (Exception ex) {
            return null;   // ImageIO PNG never fails in practice; null just means "no icon set"
        }
    }

    // ── the drawn defaults ──────────────────────────────────────────────────────────────────

    private interface Glyph { void paint(Graphics2D g); }

    /** Shared canvas: warm dark card background, gold strokes, rounded corners. */
    /** v1.72: tinted variant - skills read far faster in a grid when they aren't all gold. */
    private static String draw(Glyph glyph, Color tint) {
        BufferedImage img = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Theme.SURFACE_2_ALT);
        g.fillRoundRect(0, 0, SIZE, SIZE, 24, 24);
        g.setColor(Theme.BORDER_STRONG);
        g.setStroke(new BasicStroke(3f));
        g.drawRoundRect(1, 1, SIZE - 3, SIZE - 3, 24, 24);
        g.setColor(tint);
        g.setStroke(new BasicStroke(8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        glyph.paint(g);
        g.dispose();
        return toBase64Png(img);
    }

    private static String draw(Glyph glyph) {
        BufferedImage img = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Theme.SURFACE_2_ALT);
        g.fillRoundRect(0, 0, SIZE, SIZE, 24, 24);
        g.setColor(Theme.BORDER_STRONG);
        g.setStroke(new BasicStroke(3f));
        g.drawRoundRect(1, 1, SIZE - 3, SIZE - 3, 24, 24);
        g.setColor(Theme.ACCENT);
        g.setStroke(new BasicStroke(8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        glyph.paint(g);
        g.dispose();
        return toBase64Png(img);
    }

    private static void sword(Graphics2D g) {
        g.drawLine(38, 90, 92, 36);                 // blade
        g.drawLine(88, 32, 98, 28);                 // tip
        g.drawLine(30, 74, 54, 98);                 // guard
        g.drawLine(30, 98, 42, 86);                 // grip
        g.fillOval(24, 100, 12, 12);                // pommel
    }

    private static void shield(Graphics2D g) {
        Path2D p = new Path2D.Float();
        p.moveTo(64, 26); p.lineTo(98, 38); p.lineTo(94, 76);
        p.quadTo(88, 96, 64, 106);
        p.quadTo(40, 96, 34, 76);
        p.lineTo(30, 38); p.closePath();
        g.draw(p);
        g.drawLine(64, 40, 64, 92);
        g.drawLine(44, 60, 84, 60);
    }

    private static void pickaxe(Graphics2D g) {
        g.drawLine(46, 96, 88, 40);                 // handle
        Path2D head = new Path2D.Float();
        head.moveTo(60, 26); head.quadTo(88, 22, 104, 46);   // curved pick head
        g.draw(head);
    }

    private static void axe(Graphics2D g) {
        g.drawLine(50, 100, 84, 40);                // handle
        Path2D blade = new Path2D.Float();
        blade.moveTo(78, 30); blade.quadTo(104, 34, 100, 62);
        blade.quadTo(88, 52, 72, 52); blade.closePath();
        g.draw(blade);
    }

    private static void fish(Graphics2D g) {
        g.drawOval(30, 48, 56, 32);                 // body
        Path2D tail = new Path2D.Float();
        tail.moveTo(86, 56); tail.lineTo(102, 46); tail.lineTo(102, 82); tail.lineTo(86, 72);
        g.draw(tail);
        g.fillOval(42, 58, 7, 7);                   // eye
    }

    private static void potion(Graphics2D g) {
        g.drawLine(56, 26, 72, 26);                 // cork line
        g.drawLine(58, 30, 58, 52);
        g.drawLine(70, 30, 70, 52);
        Path2D bulb = new Path2D.Float();
        bulb.moveTo(58, 52);
        bulb.quadTo(30, 74, 46, 98);
        bulb.quadTo(64, 110, 82, 98);
        bulb.quadTo(98, 74, 70, 52);
        g.draw(bulb);
        g.drawLine(48, 84, 80, 84);                 // liquid line
    }

    private static void coins(Graphics2D g) {
        g.drawOval(34, 66, 44, 26);                 // bottom coin
        g.drawOval(44, 50, 44, 26);                 // middle coin
        g.drawOval(52, 34, 44, 26);                 // top coin
    }

    private static void star(Graphics2D g) {
        Path2D p = new Path2D.Float();
        double cx = 64, cy = 66, R = 40, r = 17;
        for (int i = 0; i < 10; i++) {
            double a = Math.toRadians(-90 + i * 36);
            double rad = (i % 2 == 0) ? R : r;
            double x = cx + rad * Math.cos(a), y = cy + rad * Math.sin(a);
            if (i == 0) p.moveTo(x, y); else p.lineTo(x, y);
        }
        p.closePath();
        g.draw(p);
    }

    private static void scroll(Graphics2D g) {
        g.drawRoundRect(38, 30, 52, 68, 10, 10);    // sheet
        g.drawOval(30, 24, 68, 14);                 // top roll
        g.drawLine(48, 54, 80, 54);
        g.drawLine(48, 68, 80, 68);
        g.drawLine(48, 82, 70, 82);
    }

    private static void gear(Graphics2D g) {
        double cx = 64, cy = 64;
        for (int i = 0; i < 8; i++) {               // teeth
            double a = Math.toRadians(i * 45);
            g.drawLine((int) (cx + 30 * Math.cos(a)), (int) (cy + 30 * Math.sin(a)),
                       (int) (cx + 42 * Math.cos(a)), (int) (cy + 42 * Math.sin(a)));
        }
        g.drawOval(34, 34, 60, 60);
        g.drawOval(52, 52, 24, 24);
    }

    // ── v1.72: the skill palette ─────────────────────────────────────────────────────────────
    private static final Color C_COMBAT = new Color(0xC8, 0x62, 0x50);
    private static final Color C_MAGIC  = new Color(0x8A, 0x7C, 0xD8);
    private static final Color C_NATURE = new Color(0x76, 0xB0, 0x6A);
    private static final Color C_CRAFT  = new Color(0xC9, 0xA2, 0x5E);
    private static final Color C_WATER  = new Color(0x5D, 0xA0, 0xE8);
    private static final Color C_FIRE   = new Color(0xE0, 0x92, 0x40);
    private static final Color C_GOLD   = new Color(0xDC, 0xB4, 0x3C);
    private static final Color C_MOVE   = new Color(0x6F, 0xC2, 0xB4);

    private static void fist(Graphics2D g) {
        g.drawRoundRect(38, 50, 52, 44, 16, 16);
        g.drawLine(38, 66, 90, 66);
    }
    private static void bow(Graphics2D g) {
        Path2D p = new Path2D.Float();
        p.moveTo(44, 26); p.quadTo(92, 64, 44, 102);
        g.draw(p);
        g.drawLine(44, 26, 44, 102);
        g.drawLine(50, 64, 96, 64);
    }
    private static void wand(Graphics2D g) {
        g.drawLine(38, 96, 84, 44);
        g.drawLine(84, 30, 84, 52);
        g.drawLine(73, 41, 95, 41);
    }
    private static void prayer(Graphics2D g) {
        g.drawLine(64, 26, 64, 100);
        g.drawLine(40, 52, 88, 52);
    }
    private static void rune(Graphics2D g) {
        Path2D p = new Path2D.Float();
        p.moveTo(64, 24); p.lineTo(100, 64); p.lineTo(64, 104); p.lineTo(28, 64);
        p.closePath();
        g.draw(p);
    }
    private static void heart(Graphics2D g) {
        Path2D p = new Path2D.Float();
        p.moveTo(64, 100);
        p.curveTo(18, 68, 34, 30, 64, 50);
        p.curveTo(94, 30, 110, 68, 64, 100);
        g.draw(p);
    }
    private static void boot(Graphics2D g) {
        Path2D p = new Path2D.Float();
        p.moveTo(44, 28); p.lineTo(44, 82); p.lineTo(96, 82); p.lineTo(76, 60); p.lineTo(66, 28);
        g.draw(p);
        g.drawLine(36, 96, 100, 96);
    }
    private static void purse(Graphics2D g) {
        g.drawRoundRect(36, 50, 56, 50, 18, 18);
        Path2D p = new Path2D.Float();
        p.moveTo(52, 50); p.quadTo(64, 26, 76, 50);
        g.draw(p);
    }
    private static void needle(Graphics2D g) {
        g.drawLine(34, 100, 92, 38);
        g.drawOval(88, 26, 18, 18);
    }
    private static void arrow(Graphics2D g) {
        g.drawLine(34, 98, 94, 34);
        g.drawLine(94, 34, 72, 38);
        g.drawLine(94, 34, 90, 56);
        g.drawLine(34, 98, 46, 86);
    }
    private static void skull(Graphics2D g) {
        g.drawOval(38, 30, 52, 48);
        g.drawLine(50, 92, 78, 92);
        g.drawOval(50, 48, 10, 10);
        g.drawOval(70, 48, 10, 10);
    }
    private static void paw(Graphics2D g) {
        g.drawOval(48, 56, 34, 30);
        g.drawOval(38, 34, 15, 15);
        g.drawOval(60, 28, 15, 15);
        g.drawOval(82, 40, 15, 15);
    }
    private static void anvil(Graphics2D g) {
        Path2D p = new Path2D.Float();
        p.moveTo(28, 52); p.lineTo(100, 52); p.lineTo(84, 74); p.lineTo(48, 74);
        p.closePath();
        g.draw(p);
        g.drawLine(64, 74, 64, 92);
        g.drawLine(44, 96, 84, 96);
    }
    private static void pot(Graphics2D g) {
        g.drawRoundRect(34, 54, 60, 44, 12, 12);
        g.drawLine(28, 54, 100, 54);
        Path2D steam = new Path2D.Float();
        steam.moveTo(58, 40); steam.quadTo(68, 30, 58, 22);
        g.draw(steam);
    }
    private static void flame(Graphics2D g) {
        Path2D p = new Path2D.Float();
        p.moveTo(64, 100);
        p.curveTo(28, 82, 52, 52, 60, 26);
        p.curveTo(84, 50, 100, 74, 64, 100);
        g.draw(p);
    }
    private static void plant(Graphics2D g) {
        g.drawLine(64, 102, 64, 54);
        Path2D l = new Path2D.Float();
        l.moveTo(64, 66); l.quadTo(34, 56, 40, 30);
        g.draw(l);
        Path2D r = new Path2D.Float();
        r.moveTo(64, 58); r.quadTo(94, 48, 88, 24);
        g.draw(r);
    }
    private static void hammer(Graphics2D g) {
        g.drawLine(40, 100, 78, 56);
        Path2D h = new Path2D.Float();
        h.moveTo(62, 40); h.lineTo(104, 40); h.lineTo(104, 62); h.lineTo(70, 62);
        g.draw(h);
    }
    private static void anchor(Graphics2D g) {
        g.drawLine(64, 40, 64, 100);
        g.drawOval(54, 22, 20, 20);
        g.drawLine(40, 56, 88, 56);
        Path2D p = new Path2D.Float();
        p.moveTo(32, 76); p.quadTo(40, 104, 64, 104);
        p.quadTo(88, 104, 96, 76);
        g.draw(p);
    }
    private static void portal(Graphics2D g) {
        g.drawOval(34, 22, 60, 84);
        g.drawOval(50, 44, 28, 40);
    }
    private static void compass(Graphics2D g) {
        g.drawOval(26, 26, 76, 76);
        Path2D n = new Path2D.Float();
        n.moveTo(52, 78); n.lineTo(80, 46); n.lineTo(76, 82);
        p2(g, n);
    }
    private static void p2(Graphics2D g, Path2D p) { p.closePath(); g.draw(p); }
    private static void vault(Graphics2D g) {
        g.drawRoundRect(28, 32, 72, 66, 10, 10);
        g.drawOval(52, 52, 26, 26);
        g.drawLine(64, 98, 64, 106);
    }
}
