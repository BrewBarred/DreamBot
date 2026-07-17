package main.menu.components;

import javax.swing.Icon;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;

/**
 * Small vector UI icons drawn in code (Patch B.16). Crisp at any size, themeable by colour, and no
 * new image assets to ship. Each factory returns a Swing {@link Icon} you can drop on a button.
 */
public final class UIIcons {

    private UIIcons() {}

    /** Base class: fixed size, antialiased, a paint hook. */
    private abstract static class Vec implements Icon {
        final int size; final Color color;
        Vec(int size, Color color) { this.size = size; this.color = color; }
        public int getIconWidth() { return size; }
        public int getIconHeight() { return size; }
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.translate(x, y);
            g2.setColor(color);
            g2.setStroke(new BasicStroke(Math.max(1.4f, size / 12f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            paintVec(g2);
            g2.dispose();
        }
        abstract void paintVec(Graphics2D g);
    }

    /** ⟳ refresh: a near-circle with an arrowhead. */
    public static Icon refresh(int s, Color c) {
        return new Vec(s, c) {
            void paintVec(Graphics2D g) {
                int p = s / 6, d = s - 2 * p;
                g.drawArc(p, p, d, d, 70, 260);   // open circle
                // arrowhead at the gap
                double a = Math.toRadians(70);
                int cx = s / 2, cy = s / 2, r = d / 2;
                int ax = (int) (cx + r * Math.cos(a)), ay = (int) (cy - r * Math.sin(a));
                Path2D ah = new Path2D.Float();
                ah.moveTo(ax - s / 8f, ay - s / 10f);
                ah.lineTo(ax + s / 12f, ay - s / 12f);
                ah.lineTo(ax + s / 10f, ay + s / 6f);
                g.draw(ah);
            }
        };
    }

    /** ↧ import / download: arrow into a tray. */
    public static Icon importIcon(int s, Color c) {
        return new Vec(s, c) {
            void paintVec(Graphics2D g) {
                int cx = s / 2;
                g.drawLine(cx, s / 6, cx, (int) (s * 0.62));                 // shaft
                g.drawLine(cx - s / 6, (int) (s * 0.44), cx, (int) (s * 0.62)); // arrow left
                g.drawLine(cx + s / 6, (int) (s * 0.44), cx, (int) (s * 0.62)); // arrow right
                g.drawLine(s / 6, (int) (s * 0.78), s - s / 6, (int) (s * 0.78)); // tray
            }
        };
    }

    /** 📁 folder. */
    public static Icon folder(int s, Color c) {
        return new Vec(s, c) {
            void paintVec(Graphics2D g) {
                int top = (int) (s * 0.30), bot = (int) (s * 0.76);
                Path2D f = new Path2D.Float();
                f.moveTo(s * 0.14, top);
                f.lineTo(s * 0.42, top);
                f.lineTo(s * 0.50, top - s * 0.08);
                // tab
                Path2D tab = new Path2D.Float();
                tab.moveTo(s * 0.14, top);
                tab.lineTo(s * 0.14, top - s * 0.06);
                tab.lineTo(s * 0.34, top - s * 0.06);
                tab.lineTo(s * 0.40, top);
                g.draw(tab);
                g.drawRect((int) (s * 0.14), top, (int) (s * 0.72), bot - top);
            }
        };
    }

    /** ☁/server: a small stack of drums (a database cylinder). */
    public static Icon server(int s, Color c) {
        return new Vec(s, c) {
            void paintVec(Graphics2D g) {
                int w = (int) (s * 0.6), x = (s - w) / 2;
                int eh = (int) (s * 0.16);  // ellipse height
                int y1 = (int) (s * 0.20), y2 = (int) (s * 0.44);
                g.drawOval(x, y1, w, eh);
                g.drawArc(x, y2, w, eh, 180, 180);
                g.drawLine(x, y1 + eh / 2, x, y2 + eh / 2);
                g.drawLine(x + w, y1 + eh / 2, x + w, y2 + eh / 2);
                // second drum
                int y3 = (int) (s * 0.58);
                g.drawArc(x, y3, w, eh, 180, 180);
                g.drawLine(x, y2 + eh / 2, x, y3 + eh / 2);
                g.drawLine(x + w, y2 + eh / 2, x + w, y3 + eh / 2);
            }
        };
    }

    /** 🔍 filter (funnel). */
    public static Icon filter(int s, Color c) {
        return new Vec(s, c) {
            void paintVec(Graphics2D g) {
                Path2D f = new Path2D.Float();
                f.moveTo(s * 0.18, s * 0.24);
                f.lineTo(s * 0.82, s * 0.24);
                f.lineTo(s * 0.56, s * 0.52);
                f.lineTo(s * 0.56, s * 0.80);
                f.lineTo(s * 0.44, s * 0.70);
                f.lineTo(s * 0.44, s * 0.52);
                f.closePath();
                g.draw(f);
            }
        };
    }

    /** ↕ sort (up/down arrows). */
    public static Icon sort(int s, Color c) {
        return new Vec(s, c) {
            void paintVec(Graphics2D g) {
                int l = (int) (s * 0.36), r = (int) (s * 0.64);
                g.drawLine(l, (int) (s * 0.2), l, (int) (s * 0.8));
                g.drawLine(l - s / 10, (int) (s * 0.34), l, (int) (s * 0.2));
                g.drawLine(l + s / 10, (int) (s * 0.34), l, (int) (s * 0.2));
                g.drawLine(r, (int) (s * 0.2), r, (int) (s * 0.8));
                g.drawLine(r - s / 10, (int) (s * 0.66), r, (int) (s * 0.8));
                g.drawLine(r + s / 10, (int) (s * 0.66), r, (int) (s * 0.8));
            }
        };
    }

    /** ⬆ publish (arrow up out of a tray). */
    public static Icon publish(int s, Color c) {
        return new Vec(s, c) {
            void paintVec(Graphics2D g) {
                int cx = s / 2;
                g.drawLine(cx, (int) (s * 0.20), cx, (int) (s * 0.60));
                g.drawLine(cx - s / 6, (int) (s * 0.36), cx, (int) (s * 0.20));
                g.drawLine(cx + s / 6, (int) (s * 0.36), cx, (int) (s * 0.20));
                g.drawLine(s / 6, (int) (s * 0.78), s - s / 6, (int) (s * 0.78));
            }
        };
    }

    /** v1.31: copy icon (two overlapping pages) for the Logs tab. */
    public static Icon copy(int s, Color c) {
        return new Icon() {
            public int getIconWidth() { return s; }
            public int getIconHeight() { return s; }
            public void paintIcon(Component comp, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(c);
                g2.setStroke(new BasicStroke(Math.max(1f, s / 9f)));
                int w = (int) (s * 0.55), h = (int) (s * 0.65);
                g2.drawRoundRect(x + s - w - 1, y + 1, w, h, 3, 3);           // back page
                g2.setColor(new Color(0x26, 0x26, 0x26));
                g2.fillRoundRect(x + 1, y + s - h - 1, w, h, 3, 3);
                g2.setColor(c);
                g2.drawRoundRect(x + 1, y + s - h - 1, w, h, 3, 3);           // front page
                g2.dispose();
            }
        };
    }

    /** v1.31: save icon (floppy) for the Logs tab. */
    public static Icon save(int s, Color c) {
        return new Icon() {
            public int getIconWidth() { return s; }
            public int getIconHeight() { return s; }
            public void paintIcon(Component comp, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(c);
                g2.setStroke(new BasicStroke(Math.max(1f, s / 9f)));
                int p = Math.max(1, s / 8);
                g2.drawRoundRect(x + p, y + p, s - 2 * p, s - 2 * p, 3, 3);   // body
                int lw = (s - 2 * p) / 2;
                g2.drawRect(x + p + (s - 2 * p - lw) / 2, y + p, lw, (s - 2 * p) / 3);   // label
                g2.drawRect(x + p + 2, y + p + (s - 2 * p) / 2, s - 2 * p - 4,
                        (s - 2 * p) / 2 - 1);                                  // shutter
                g2.dispose();
            }
        };
    }

    /** A small filled (on) or hollow (off) status dot - replaces the \u25cf/\u25cb glyphs. */
    public static Icon dot(int s, Color c, boolean filled) {
        return new Icon() {
            public int getIconWidth() { return s; }
            public int getIconHeight() { return s; }
            public void paintIcon(Component comp, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int p = Math.max(1, s / 5);
                Ellipse2D e = new Ellipse2D.Float(x + p, y + p, s - 2f * p, s - 2f * p);
                g2.setColor(c);
                if (filled) g2.fill(e);
                else { g2.setStroke(new BasicStroke(Math.max(1f, s / 10f))); g2.draw(e); }
                g2.dispose();
            }
        };
    }

    /** 🔍 search (magnifier). Replaces the emoji label that showed as a missing-glyph box. */
    public static Icon search(int s, Color c) {
        return new Vec(s, c) {
            void paintVec(Graphics2D g) {
                int d = (int) (s * 0.52);
                int x = (int) (s * 0.14), y = (int) (s * 0.14);
                g.drawOval(x, y, d, d);
                g.drawLine(x + d - s / 20, y + d - s / 20,
                        (int) (s * 0.84), (int) (s * 0.84));
            }
        };
    }

    /** A filled or outline star, for the rating widget. */
    public static Icon star(int s, Color c, boolean filled) {
        return new Icon() {
            public int getIconWidth() { return s; }
            public int getIconHeight() { return s; }
            public void paintIcon(Component comp, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.translate(x, y);
                Path2D star = starPath(s);
                if (filled) { g2.setColor(c); g2.fill(star); }
                else {
                    g2.setColor(c);
                    g2.setStroke(new BasicStroke(Math.max(1f, s / 14f)));
                    g2.draw(star);
                }
                g2.dispose();
            }
        };
    }

    private static Path2D starPath(int s) {
        Path2D p = new Path2D.Float();
        double cx = s / 2.0, cy = s / 2.0, outer = s * 0.44, inner = s * 0.18;
        for (int i = 0; i < 10; i++) {
            double ang = Math.PI / 2 + i * Math.PI / 5;
            double r = (i % 2 == 0) ? outer : inner;
            double px = cx + r * Math.cos(ang), py = cy - r * Math.sin(ang);
            if (i == 0) p.moveTo(px, py); else p.lineTo(px, py);
        }
        p.closePath();
        return p;
    }
}
