package main.menu.components;

import main.market.DonorRanks;
import main.market.Tier;
import main.menu.Theme;

import javax.swing.*;
import java.awt.*;

/**
 * The rank badges (v1.87): the little crown-and-coin chips shown beside a name wherever names
 * appear - the Account tab, the Dev Console, public profiles, and (once the server sends author
 * tier/donation fields on listings) market cards and comments. Purely cosmetic, deliberately
 * small, and built from drawn {@link UIIcons} glyphs so they render identically everywhere.
 *
 * <p>Three independent chips can appear, in this order:
 * <ol>
 *   <li><b>Tier crown</b> - tinted up the ladder: silver (free), gold (VIP), blue (subscriber),
 *       purple (lifetime), green (moderator), crimson (admin), white-gold (owner). Guests and
 *       free users show no crown unless {@code showFree} asks for one.</li>
 *   <li><b>Scripter quill</b> - the earned mark for unlimited market uploads.</li>
 *   <li><b>Donor coin</b> - bronze → silver → gold → platinum by donor level, labelled with the
 *       ladder's cosmetic name ("The Quiet Donor"). Shown REGARDLESS of tier - a donor's coin
 *       never hides behind their crown.</li>
 * </ol>
 */
public final class RankBadge {

    private RankBadge() {}

    /** The crown tint for a tier name (the ladder made visible). */
    public static Color tierColor(String tier) {
        switch (tier == null ? "" : tier.toLowerCase(java.util.Locale.ROOT)) {
            case Tier.OWNER:      return new Color(0xF5, 0xE6, 0xB0);   // white-gold
            case Tier.ADMIN:      return new Color(0xD8, 0x5A, 0x46);   // crimson
            case Tier.MODERATOR:  return Theme.GREEN;
            case Tier.LIFETIME:   return new Color(0xB0, 0x7F, 0xE0);   // purple
            case Tier.SUBSCRIBER: return Theme.BLUE;
            case Tier.VIP:        return Theme.ACCENT;                  // OSRS gold
            default:              return new Color(0xA8, 0xA8, 0xA8);   // silver (free)
        }
    }

    /** The coin tint for a donor level (1-based; level 4+ shares platinum). */
    public static Color donorColor(int level) {
        switch (Math.max(1, Math.min(level, 4))) {
            case 1:  return new Color(0xC8, 0x8A, 0x4A);   // bronze
            case 2:  return new Color(0xC0, 0xC6, 0xCE);   // silver
            case 3:  return new Color(0xE8, 0xC2, 0x4A);   // gold
            default: return new Color(0xD6, 0xE4, 0xF0);   // platinum
        }
    }

    /** The signed-in account's own badge strip. */
    public static JComponent mine(int iconSize) {
        return of(Tier.current(), Tier.isScripter(), Tier.donatedCents(), iconSize);
    }

    /**
     * A badge strip for any person. {@code tier} may be null/empty (no crown), {@code scripter}
     * adds the quill, and any {@code donatedCents} at or above the ladder's first rung adds the
     * coin with its cosmetic name. Returns an empty (invisible) panel when nothing applies, so
     * callers can add it unconditionally.
     */
    public static JComponent of(String tier, boolean scripter, long donatedCents, int iconSize) {
        return of(tier, scripter, donatedCents, iconSize, false);
    }

    /** As {@link #of}, with {@code showFree} forcing the silver crown for free/guest tiers. */
    public static JComponent of(String tier, boolean scripter, long donatedCents,
                                int iconSize, boolean showFree) {
        JPanel strip = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        strip.setOpaque(false);

        int w = Tier.weight(tier);
        if (w >= 2 || (showFree && tier != null)) {
            Color c = tierColor(tier);
            strip.add(chip(UIIcons.crown(iconSize, c), Tier.labelFor(tier), c,
                    "Account tier: " + Tier.labelFor(tier)));
        }
        if (scripter) {
            Color c = new Color(0x8F, 0xD4, 0xA8);
            strip.add(chip(UIIcons.quill(iconSize, c), "Scripter", c,
                    "Scripter - earned rank: no market upload limit"));
        }
        int lvl = DonorRanks.levelFor(donatedCents);
        if (lvl > 0) {
            Color c = donorColor(lvl);
            String name = DonorRanks.nameFor(donatedCents);
            strip.add(chip(UIIcons.coin(iconSize, c), name, c,
                    name + " - donor level " + lvl + " ("
                            + DonorRanks.dollars(donatedCents) + " donated). Thank you."));
        }
        return strip;
    }

    /** One icon+label pill on a faint tinted wash. */
    private static JComponent chip(Icon icon, String text, Color color, String tooltip) {
        JLabel l = new JLabel(text, icon, SwingConstants.LEFT);
        l.setIconTextGap(4);
        l.setFont(new Font("Segoe UI", Font.BOLD, Math.max(10, 11)));
        l.setForeground(color);
        l.setToolTipText(tooltip);
        l.setOpaque(true);
        l.setBackground(new Color(color.getRed(), color.getGreen(), color.getBlue(), 26));
        l.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(
                        new Color(color.getRed(), color.getGreen(), color.getBlue(), 90)),
                BorderFactory.createEmptyBorder(1, 6, 1, 6)));
        return l;
    }
}
