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
    /**
     * v1.79: sort order - two bars of unequal length with an arrow beside them, the standard
     * "ordered list" reading. The old version was a bare set of lines that said "menu" more
     * than "sort", which is why it never looked like it belonged next to "newest first".
     */
    public static Icon sort(int s, Color c) {
        return new Vec(s, c) {
            void paintVec(Graphics2D g2) {
                g2.setStroke(new BasicStroke(Math.max(1.5f, s / 11f),
                        BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                // descending bars: long -> short, so the icon reads as an ordering
                float x = s * 0.16f;
                g2.drawLine(Math.round(x), Math.round(s * 0.28f),
                            Math.round(s * 0.72f), Math.round(s * 0.28f));
                g2.drawLine(Math.round(x), Math.round(s * 0.50f),
                            Math.round(s * 0.56f), Math.round(s * 0.50f));
                g2.drawLine(Math.round(x), Math.round(s * 0.72f),
                            Math.round(s * 0.40f), Math.round(s * 0.72f));
                // the direction arrow
                float ax = s * 0.86f;
                g2.drawLine(Math.round(ax), Math.round(s * 0.22f),
                            Math.round(ax), Math.round(s * 0.78f));
                g2.drawLine(Math.round(ax - s * 0.11f), Math.round(s * 0.62f),
                            Math.round(ax), Math.round(s * 0.78f));
                g2.drawLine(Math.round(ax + s * 0.11f), Math.round(s * 0.62f),
                            Math.round(ax), Math.round(s * 0.78f));
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
    /** v1.60: a left/right chevron for the market-ready strip's bounded arrows. */
    public static Icon chevron(int s, Color c, boolean left) {
        return new Vec(s, c) {
            void paintVec(Graphics2D g2) {
                int midY = s / 2;
                int x1 = left ? (int) (s * 0.62) : (int) (s * 0.38);
                int x2 = left ? (int) (s * 0.36) : (int) (s * 0.64);
                g2.drawLine(x1, (int) (s * 0.24), x2, midY);
                g2.drawLine(x2, midY, x1, (int) (s * 0.76));
            }
        };
    }

    /** v1.60: the favourite heart (ported from the menu's v1.59 drawnHeart, colour now a param). */
    public static Icon heart(int s, Color c, boolean filled) {
        return new Vec(s, c) {
            void paintVec(Graphics2D g2) {
                Path2D.Float h = new Path2D.Float();
                float w = s, t = s;
                h.moveTo(w / 2f, t * 0.86f);
                h.curveTo(-w * 0.18f, t * 0.42f, w * 0.16f, -t * 0.14f, w / 2f, t * 0.28f);
                h.curveTo(w * 0.84f, -t * 0.14f, w * 1.18f, t * 0.42f, w / 2f, t * 0.86f);
                h.closePath();
                if (filled) g2.fill(h);
                else { g2.setStroke(new BasicStroke(Math.max(1.2f, s / 11f))); g2.draw(h); }
            }
        };
    }

    /** v1.60: a speech bubble for the per-card comments toggle. */
    public static Icon comment(int s, Color c) {
        return new Vec(s, c) {
            void paintVec(Graphics2D g2) {
                int w = (int) (s * 0.78), h = (int) (s * 0.56);
                int x = (int) (s * 0.10), y = (int) (s * 0.12);
                g2.drawRoundRect(x, y, w, h, s / 3, s / 3);
                // the tail
                int tx = x + (int) (w * 0.28);
                g2.drawLine(tx, y + h, tx - (int) (s * 0.06), y + h + (int) (s * 0.20));
                g2.drawLine(tx - (int) (s * 0.06), y + h + (int) (s * 0.20), tx + (int) (s * 0.14), y + h);
            }
        };
    }

    /** v1.60: a picture placeholder, shown when a listing has no icon (or a broken one). */
    public static Icon image(int s, Color c) {
        return new Vec(s, c) {
            void paintVec(Graphics2D g2) {
                int m = Math.max(1, s / 8);
                int w = s - m * 2, h = s - m * 2;
                g2.drawRoundRect(m, m, w, h, s / 6, s / 6);
                // sun
                int r = Math.max(2, s / 7);
                g2.drawOval(m + w / 5, m + h / 5, r, r);
                // mountains
                int base = m + (int) (h * 0.82);
                g2.drawLine(m + (int) (w * 0.12), base, m + (int) (w * 0.42), m + (int) (h * 0.45));
                g2.drawLine(m + (int) (w * 0.42), m + (int) (h * 0.45), m + (int) (w * 0.62), base);
                g2.drawLine(m + (int) (w * 0.55), base, m + (int) (w * 0.74), m + (int) (h * 0.58));
                g2.drawLine(m + (int) (w * 0.74), m + (int) (h * 0.58), m + (int) (w * 0.90), base);
            }
        };
    }

    /** v1.60: a small x, for the strip card's "remove from market-ready" button. */
    public static Icon cross(int s, Color c) {
        return new Vec(s, c) {
            void paintVec(Graphics2D g2) {
                int a = (int) (s * 0.26), b = (int) (s * 0.74);
                g2.drawLine(a, a, b, b);
                g2.drawLine(b, a, a, b);
            }
        };
    }
    /** v1.61: a "market card" glyph - a card outline with an icon square + text lines. */
    public static Icon card(int s, Color c) {
        return new Vec(s, c) {
            void paintVec(Graphics2D g2) {
                int m = Math.max(1, s / 9);
                int w = s - m * 2, h = s - m * 2;
                g2.drawRoundRect(m, m, w, h, s / 5, s / 5);
                int q = Math.max(3, (int) (h * 0.34));
                g2.drawRect(m + (int) (w * 0.16), m + (int) (h * 0.16), q, q);   // the icon
                int tx = m + (int) (w * 0.16) + q + Math.max(2, s / 10);
                int ty1 = m + (int) (h * 0.26), ty2 = m + (int) (h * 0.46);
                g2.drawLine(tx, ty1, m + (int) (w * 0.86), ty1);                 // title line
                g2.drawLine(tx, ty2, m + (int) (w * 0.70), ty2);                 // meta line
                int by = m + (int) (h * 0.78);
                g2.drawLine(m + (int) (w * 0.16), by, m + (int) (w * 0.86), by); // tag row
            }
        };
    }

    /** v1.61: a die (five pips) - the card builder's "generate a random icon" button. */
    public static Icon dice(int s, Color c) {
        return new Vec(s, c) {
            void paintVec(Graphics2D g2) {
                int m = Math.max(1, s / 8);
                int w = s - m * 2;
                g2.drawRoundRect(m, m, w, w, s / 5, s / 5);
                int r = Math.max(2, s / 9);
                int a = m + (int) (w * 0.24) - r / 2, b = m + (int) (w * 0.76) - r / 2;
                int mid = m + w / 2 - r / 2;
                g2.fillOval(a, a, r, r);
                g2.fillOval(b, a, r, r);
                g2.fillOval(mid, mid, r, r);
                g2.fillOval(a, b, r, r);
                g2.fillOval(b, b, r, r);
            }
        };
    }
    /** v1.62: "insert at end" - a stack of rows with the new one landing at the bottom. */
    public static Icon insertEnd(int s, Color c) {
        return new Vec(s, c) {
            void paintVec(Graphics2D g2) {
                int lx = (int) (s * 0.22), rx = (int) (s * 0.78);
                g2.drawLine(lx, (int) (s * 0.24), rx, (int) (s * 0.24));   // existing rows
                g2.drawLine(lx, (int) (s * 0.42), rx, (int) (s * 0.42));
                float dash = Math.max(2f, s / 10f);
                g2.setStroke(new BasicStroke(Math.max(1.6f, s / 11f), BasicStroke.CAP_ROUND,
                        BasicStroke.JOIN_ROUND, 10f, new float[]{dash, dash}, 0f));
                g2.drawLine(lx, (int) (s * 0.74), rx, (int) (s * 0.74)); // the new one, at the END
                // a little down-arrow pointing to where it lands
                g2.setStroke(new BasicStroke(Math.max(1.6f, s / 11f), BasicStroke.CAP_ROUND,
                        BasicStroke.JOIN_ROUND));
                int cx = s / 2;
                g2.drawLine(cx, (int) (s * 0.52), cx, (int) (s * 0.66));
                g2.drawLine(cx - s / 10, (int) (s * 0.60), cx, (int) (s * 0.66));
                g2.drawLine(cx + s / 10, (int) (s * 0.60), cx, (int) (s * 0.66));
            }
        };
    }

    /** v1.62: "insert after selected" - the new row slots in just below a highlighted row. */
    public static Icon insertAfter(int s, Color c) {
        return new Vec(s, c) {
            void paintVec(Graphics2D g2) {
                int lx = (int) (s * 0.22), rx = (int) (s * 0.78);
                g2.drawLine(lx, (int) (s * 0.22), rx, (int) (s * 0.22));   // a row above
                // the selected row - drawn as a filled bar so it reads as "here"
                g2.fillRect(lx, (int) (s * 0.36), rx - lx, Math.max(2, (int) (s * 0.12)));
                float dash = Math.max(2f, s / 10f);
                g2.setStroke(new BasicStroke(Math.max(1.6f, s / 11f), BasicStroke.CAP_ROUND,
                        BasicStroke.JOIN_ROUND, 10f, new float[]{dash, dash}, 0f));
                g2.drawLine(lx, (int) (s * 0.62), rx, (int) (s * 0.62)); // new one, right AFTER it
                g2.setStroke(new BasicStroke(Math.max(1.6f, s / 11f), BasicStroke.CAP_ROUND,
                        BasicStroke.JOIN_ROUND));
                g2.drawLine(lx, (int) (s * 0.80), rx, (int) (s * 0.80));   // a row below
            }
        };
    }

    /** v1.62: a plus sign - the Task List's "add from library" button. */
    public static Icon plus(int s, Color c) {
        return new Vec(s, c) {
            void paintVec(Graphics2D g2) {
                int m = (int) (s * 0.24);
                g2.drawLine(s / 2, m, s / 2, s - m);
                g2.drawLine(m, s / 2, s - m, s / 2);
            }
        };
    }
    /** v1.63: a "structure/outline" glyph - a parent node with two indented child rows. */
    public static Icon structure(int s, Color c) {
        return new Vec(s, c) {
            void paintVec(Graphics2D g2) {
                int x0 = (int) (s * 0.20);
                int y0 = (int) (s * 0.22);
                // parent bar
                g2.drawLine(x0, y0, (int) (s * 0.80), y0);
                // the trunk down the left
                g2.drawLine(x0, y0, x0, (int) (s * 0.74));
                // two child branches + their bars
                int cx = (int) (s * 0.42);
                int y1 = (int) (s * 0.48), y2 = (int) (s * 0.74);
                g2.drawLine(x0, y1, cx, y1);
                g2.drawLine(cx, y1, (int) (s * 0.82), y1);
                g2.drawLine(x0, y2, cx, y2);
                g2.drawLine(cx, y2, (int) (s * 0.82), y2);
            }
        };
    }
    /** v1.63: an info "(i)" glyph - a circle with a dot and a stem. */
    public static Icon info(int s, Color c) {
        return new Vec(s, c) {
            void paintVec(Graphics2D g2) {
                int m = Math.max(1, s / 10);
                g2.drawOval(m, m, s - 2 * m, s - 2 * m);
                int cx = s / 2;
                g2.fillOval(cx - Math.max(1, s / 14), (int) (s * 0.28), Math.max(2, s / 7), Math.max(2, s / 7)); // dot
                int stemTop = (int) (s * 0.46), stemBot = (int) (s * 0.72);
                g2.drawLine(cx, stemTop, cx, stemBot);   // stem
            }
        };
    }

    /** v1.63: a "more" glyph - three horizontal dots (overflow menu). */
    public static Icon more(int s, Color c) {
        return new Vec(s, c) {
            void paintVec(Graphics2D g2) {
                int r = Math.max(2, s / 8), y = s / 2 - r / 2;
                g2.fillOval((int) (s * 0.18) - r / 2, y, r, r);
                g2.fillOval(s / 2 - r / 2, y, r, r);
                g2.fillOval((int) (s * 0.82) - r / 2, y, r, r);
            }
        };
    }

    /**
     * v1.78: a luggage-tag - angled body with a punched hole. The tag row previously used a
     * glyph the shipped fonts didn't carry, which rendered as an empty rectangle.
     */
    public static Icon tag(int s, Color c) {
        return new Vec(s, c) {
            void paintVec(Graphics2D g2) {
                g2.setStroke(new BasicStroke(Math.max(1.4f, s / 10f),
                        BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                java.awt.geom.Path2D p = new java.awt.geom.Path2D.Float();
                float a = s * 0.10f, b = s * 0.52f, m = s * 0.90f;
                p.moveTo(a, b);          // left point
                p.lineTo(b, a);          // up to the top edge
                p.lineTo(m, a);
                p.lineTo(m, s * 0.62f);
                p.lineTo(s * 0.52f, m);
                p.closePath();
                g2.draw(p);
                float r = Math.max(2f, s * 0.11f);
                g2.draw(new java.awt.geom.Ellipse2D.Float(s * 0.60f, s * 0.22f, r, r));
            }
        };
    }

    /** v1.71: a tick, for "mark this card built". */
    public static Icon check(int s, Color c) {
        return new Vec(s, c) {
            void paintVec(Graphics2D g2) {
                g2.setStroke(new BasicStroke(Math.max(1.7f, s / 7f),
                        BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.drawLine(Math.round(s * 0.22f), Math.round(s * 0.53f),
                            Math.round(s * 0.42f), Math.round(s * 0.73f));
                g2.drawLine(Math.round(s * 0.42f), Math.round(s * 0.73f),
                            Math.round(s * 0.79f), Math.round(s * 0.29f));
            }
        };
    }

    /**
     * v1.69: a plain X. Used for Close (detail view) and for the card's destructive action,
     * replacing the "..." overflow - a cross reads as "get rid of this" far faster than a
     * three-dot menu that hid a mix of unrelated commands.
     */
    public static Icon close(int s, Color c) {
        return new Vec(s, c) {
            void paintVec(Graphics2D g2) {
                g2.setStroke(new BasicStroke(Math.max(1.6f, s / 8f),
                        BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                int p = Math.round(s * 0.26f), q = s - p;
                g2.drawLine(p, p, q, q);
                g2.drawLine(q, p, p, q);
            }
        };
    }

    /**
     * v1.69: an up arrow for Download/Import - the direction that reads as "bring this into my
     * library". Stem plus a chevron head, drawn with round caps so it stays clean when small.
     */
    public static Icon arrowUp(int s, Color c) {
        return new Vec(s, c) {
            void paintVec(Graphics2D g2) {
                g2.setStroke(new BasicStroke(Math.max(1.6f, s / 8f),
                        BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                int cx = s / 2;
                int top = Math.round(s * 0.22f), bot = Math.round(s * 0.80f);
                g2.drawLine(cx, bot, cx, top);
                int w = Math.round(s * 0.24f), hy = top + Math.round(s * 0.24f);
                g2.drawLine(cx - w, hy, cx, top);
                g2.drawLine(cx + w, hy, cx, top);
            }
        };
    }

    /**
     * v1.86: a globe (circle + centre meridian ellipse + equator) for the minimap's world-map
     * button - the drawn stand-in for the in-game world-map orb.
     */
    public static Icon globe(int s, Color c) {
        return new Vec(s, c) {
            void paintVec(Graphics2D g2) {
                g2.setStroke(new BasicStroke(Math.max(1.3f, s / 13f),
                        BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                int p = Math.round(s * 0.14f), d = s - 2 * p;
                g2.drawOval(p, p, d, d);                                   // the sphere
                g2.drawOval(p + Math.round(d * 0.30f), p, Math.round(d * 0.40f), d); // meridian
                g2.drawLine(p, s / 2, p + d, s / 2);                       // equator
            }
        };
    }

    /** v1.86: a map flag (pole + banner) for the world-map view's flagged coordinates. */
    public static Icon flag(int s, Color c) {
        return new Vec(s, c) {
            void paintVec(Graphics2D g2) {
                g2.setStroke(new BasicStroke(Math.max(1.4f, s / 11f),
                        BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                int px = Math.round(s * 0.30f);
                g2.drawLine(px, Math.round(s * 0.16f), px, Math.round(s * 0.86f));  // pole
                Path2D b = new Path2D.Float();                                       // banner
                b.moveTo(px, s * 0.18f);
                b.lineTo(s * 0.78f, s * 0.30f);
                b.lineTo(px, s * 0.46f);
                b.closePath();
                g2.fill(b);
            }
        };
    }

    /**
     * v1.86: a small filled caret pointing down, for dropdown-style buttons ("Select preset").
     * Drawn, because the client font renders no reliable ▾ glyph (same lesson as v1.50's ✕).
     */
    public static Icon caretDown(int s, Color c) {
        return new Vec(s, c) {
            void paintVec(Graphics2D g2) {
                Path2D t = new Path2D.Float();
                t.moveTo(s * 0.22f, s * 0.38f);
                t.lineTo(s * 0.78f, s * 0.38f);
                t.lineTo(s * 0.50f, s * 0.68f);
                t.closePath();
                g2.fill(t);
            }
        };
    }

    /**
     * v1.87: the tier crown - three points and a band, filled, tinted per rank by the caller
     * (RankBadge owns the colour ladder). The "cute lil crown" the rank system asked for.
     */
    public static Icon crown(int s, Color c) {
        return new Vec(s, c) {
            void paintVec(Graphics2D g2) {
                Path2D p = new Path2D.Float();
                p.moveTo(s * 0.14f, s * 0.30f);        // left point base
                p.lineTo(s * 0.30f, s * 0.52f);
                p.lineTo(s * 0.50f, s * 0.22f);        // centre point (tallest)
                p.lineTo(s * 0.70f, s * 0.52f);
                p.lineTo(s * 0.86f, s * 0.30f);
                p.lineTo(s * 0.80f, s * 0.66f);        // down to the band
                p.lineTo(s * 0.20f, s * 0.66f);
                p.closePath();
                g2.fill(p);
                g2.fillRect(Math.round(s * 0.20f), Math.round(s * 0.72f),
                        Math.round(s * 0.60f), Math.max(2, Math.round(s * 0.10f)));   // band
            }
        };
    }

    /**
     * v1.87: the donor coin - a circle with a drawn $ (two strokes + bar), tinted bronze /
     * silver / gold / platinum per donor level by RankBadge. Drawn, never a font glyph.
     */
    public static Icon coin(int s, Color c) {
        return new Vec(s, c) {
            void paintVec(Graphics2D g2) {
                g2.setStroke(new BasicStroke(Math.max(1.3f, s / 12f),
                        BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                int p = Math.round(s * 0.12f), d = s - 2 * p;
                g2.drawOval(p, p, d, d);
                // the S curve of the $
                Path2D sp = new Path2D.Float();
                sp.moveTo(s * 0.62f, s * 0.36f);
                sp.curveTo(s * 0.60f, s * 0.26f, s * 0.38f, s * 0.28f, s * 0.38f, s * 0.42f);
                sp.curveTo(s * 0.38f, s * 0.54f, s * 0.62f, s * 0.48f, s * 0.62f, s * 0.60f);
                sp.curveTo(s * 0.62f, s * 0.74f, s * 0.38f, s * 0.74f, s * 0.37f, s * 0.63f);
                g2.draw(sp);
                g2.drawLine(s / 2, Math.round(s * 0.22f), s / 2, Math.round(s * 0.80f));   // bar
            }
        };
    }

    /**
     * v1.87: the scripter quill - a nib-down feather stroke, for the rank whose one power is
     * unlimited market uploads. Reads as "writes scripts" at 12px.
     */
    public static Icon quill(int s, Color c) {
        return new Vec(s, c) {
            void paintVec(Graphics2D g2) {
                Path2D f = new Path2D.Float();
                f.moveTo(s * 0.80f, s * 0.16f);                                   // feather tip
                f.curveTo(s * 0.52f, s * 0.16f, s * 0.30f, s * 0.44f, s * 0.28f, s * 0.70f);
                f.curveTo(s * 0.50f, s * 0.62f, s * 0.74f, s * 0.42f, s * 0.80f, s * 0.16f);
                f.closePath();
                g2.fill(f);
                g2.setStroke(new BasicStroke(Math.max(1.2f, s / 13f),
                        BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.drawLine(Math.round(s * 0.28f), Math.round(s * 0.70f),
                        Math.round(s * 0.16f), Math.round(s * 0.84f));            // the nib
            }
        };
    }

    /**
     * v1.87: planet earth for the world-map button - a blue disc with green landmasses, sitting
     * where the in-game world-map orb sits so the minimap corner reads like the real one. The
     * only two-colour icon in the set; the caller passes no colour because Earth has its own.
     */
    public static Icon earth(int s) {
        return new Icon() {
            @Override public int getIconWidth() { return s; }
            @Override public int getIconHeight() { return s; }
            @Override public void paintIcon(java.awt.Component comp, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.translate(x, y);
                int p = Math.round(s * 0.08f), d = s - 2 * p;
                g2.setColor(new Color(0x2E, 0x66, 0xA8));                          // ocean
                g2.fillOval(p, p, d, d);
                g2.setClip(new java.awt.geom.Ellipse2D.Float(p, p, d, d));
                g2.setColor(new Color(0x4F, 0x8F, 0x3D));                          // land
                Path2D land = new Path2D.Float();                                  // west blob
                land.moveTo(s * 0.18f, s * 0.34f);
                land.curveTo(s * 0.34f, s * 0.18f, s * 0.52f, s * 0.30f, s * 0.44f, s * 0.46f);
                land.curveTo(s * 0.40f, s * 0.60f, s * 0.24f, s * 0.58f, s * 0.18f, s * 0.34f);
                land.closePath();
                g2.fill(land);
                Path2D land2 = new Path2D.Float();                                 // east blob
                land2.moveTo(s * 0.58f, s * 0.52f);
                land2.curveTo(s * 0.76f, s * 0.42f, s * 0.86f, s * 0.58f, s * 0.72f, s * 0.72f);
                land2.curveTo(s * 0.60f, s * 0.82f, s * 0.52f, s * 0.66f, s * 0.58f, s * 0.52f);
                land2.closePath();
                g2.fill(land2);
                g2.setClip(null);
                g2.setStroke(new BasicStroke(Math.max(1.1f, s / 15f)));
                g2.setColor(new Color(0x18, 0x16, 0x12));                          // rim
                g2.drawOval(p, p, d, d);
                g2.dispose();
            }
        };
    }
}

