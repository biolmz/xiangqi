package chess;

import javax.swing.JPanel;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/** 棋盘绘制与鼠标交互。所有坐标都经过「翻转视角」映射，执黑时己方永远在下方。 */
public final class BoardPanel extends JPanel {

    public interface ClickListener {
        void onClick(int square);
        void onRightClick();
    }

    private static final Color WOOD_LIGHT = new Color(244, 214, 160);
    private static final Color WOOD_DARK = new Color(222, 180, 116);
    private static final Color LINE = new Color(103, 68, 36);
    private static final Color RED_PIECE = new Color(176, 38, 30);
    private static final Color BLACK_PIECE = new Color(38, 39, 44);
    private static final Color GOLD = new Color(232, 165, 32);
    private static final Color GREEN = new Color(46, 140, 86);

    private static final String PIECE_FONT = pickFont("KaiTi", "楷体", "STKaiti", "SimSun",
            "宋体", "Microsoft YaHei", "微软雅黑", "Noto Serif CJK SC", "Serif", "Dialog");
    private static final String UI_FONT = pickFont("Microsoft YaHei", "微软雅黑",
            "SimSun", "宋体", "Noto Sans CJK SC", "Dialog");

    private static String pickFont(String... candidates) {
        java.util.Set<String> available = new java.util.HashSet<>();
        for (String s : GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames())
            available.add(s.toLowerCase(java.util.Locale.ROOT));
        for (String c : candidates)
            if (available.contains(c.toLowerCase(java.util.Locale.ROOT))) return c;
        return "Dialog";
    }

    public static Font uiFont(int style, int size) { return new Font(UI_FONT, style, size); }

    private final Board board;
    private ClickListener listener;

    private int selected = -1;
    private final Board.MoveList hints = new Board.MoveList();
    private int lastFrom = -1, lastTo = -1;
    private int hintFrom = -1, hintTo = -1;
    private int checkedKing = -1;
    private boolean flipped = false;
    private boolean showHints = true;
    private String banner = "";
    private int tick = 0;

    private double u, ox, oy;

    public BoardPanel(Board board) {
        this.board = board;
        setBackground(new Color(44, 38, 32));
        setDoubleBuffered(true);
        MouseAdapter ma = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger() || e.getButton() == MouseEvent.BUTTON3) {
                    if (listener != null) listener.onRightClick();
                    return;
                }
                if (e.getButton() != MouseEvent.BUTTON1) return;
                int[] rc = hitTest(e.getX(), e.getY());
                if (rc != null && listener != null) listener.onClick(rc[0] * 9 + rc[1]);
            }
        };
        addMouseListener(ma);
    }

    public void setClickListener(ClickListener l) { this.listener = l; }
    public void setFlipped(boolean f) { this.flipped = f; repaint(); }
    public boolean isFlipped() { return flipped; }
    public void setShowHints(boolean s) { this.showHints = s; repaint(); }
    public void setSelected(int sq) { this.selected = sq; repaint(); }
    public void setHints(Board.MoveList h) { hints.clear(); if (h != null) for (int i = 0; i < h.n; i++) hints.add(h.a[i]); repaint(); }
    public void setLastMove(int from, int to) { lastFrom = from; lastTo = to; repaint(); }
    public void setHintMove(int from, int to) { hintFrom = from; hintTo = to; repaint(); }
    public void setCheckedKing(int sq) { checkedKing = sq; repaint(); }
    public void setBanner(String s) { banner = s == null ? "" : s; repaint(); }
    public void pulse() { tick++; repaint(); }

    private int vr(int r) { return flipped ? 9 - r : r; }
    private int vc(int c) { return flipped ? 8 - c : c; }
    private int X(int c) { return (int) Math.round(ox + vc(c) * u); }
    private int Y(int r) { return (int) Math.round(oy + vr(r) * u); }

    /** 屏幕坐标 → 棋盘（行, 列），点在线外或格子外返回 null */
    private int[] hitTest(int mx, int my) {
        int sc = (int) Math.round((mx - ox) / u);
        int sr = (int) Math.round((my - oy) / u);
        if (sr < 0 || sr > 9 || sc < 0 || sc > 8) return null;
        if (Math.abs(mx - (ox + sc * u)) > u * 0.52) return null;
        if (Math.abs(my - (oy + sr * u)) > u * 0.52) return null;
        return new int[]{flipped ? 9 - sr : sr, flipped ? 8 - sc : sc};
    }

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

            int w = getWidth(), h = getHeight();
            u = Math.min(w / 9.35, h / 10.35);
            double margin = u * 0.62;
            ox = (w - 8 * u) / 2.0;
            oy = (h - 9 * u) / 2.0;
            if (u <= 1) return;

            drawBackdrop(g, w, h);
            drawWood(g, margin);
            drawGrid(g);
            drawRiverText(g);
            drawLastMove(g);
            if (hintFrom >= 0 && hintTo >= 0) drawHintArrow(g);
            if (showHints) drawHints(g);
            drawPieces(g);
            drawOverlays(g);
            if (!banner.isEmpty()) drawBanner(g);
        } finally {
            g.dispose();
        }
    }

    private void drawBackdrop(Graphics2D g, int w, int h) {
        g.setPaint(new GradientPaint(0, 0, new Color(58, 49, 40), w, h, new Color(30, 26, 22)));
        g.fillRect(0, 0, w, h);
        g.setColor(new Color(255, 255, 255, 7));
        for (int y = 0; y < h; y += 6) g.drawLine(0, y, w, y);
    }

    private void drawWood(Graphics2D g, double margin) {
        int x = (int) (ox - margin), y = (int) (oy - margin);
        int bw = (int) (8 * u + margin * 2), bh = (int) (9 * u + margin * 2);
        g.setColor(new Color(0, 0, 0, 70));
        g.fillRoundRect(x + 6, y + 8, bw, bh, (int) (u * .12), (int) (u * .12));
        g.setPaint(new GradientPaint(x, y, WOOD_LIGHT, x + bw, y + bh, WOOD_DARK));
        g.fillRoundRect(x, y, bw, bh, (int) (u * .12), (int) (u * .12));
        // 木纹
        g.setColor(new Color(150, 105, 55, 18));
        for (int i = 1; i < 26; i++) {
            int yy = y + (int) (bh * i / 26.0);
            g.drawLine(x + 6, yy, x + bw - 6, yy);
        }
        g.setColor(new Color(92, 60, 30));
        g.setStroke(new BasicStroke(Math.max(1.6f, (float) (u * .045f))));
        g.drawRoundRect(x, y, bw, bh, (int) (u * .12), (int) (u * .12));
        g.setColor(new Color(120, 82, 42));
        g.setStroke(new BasicStroke(Math.max(1f, (float) (u * .022f))));
        double inset = u * .13;
        g.drawRect((int) (ox - inset), (int) (oy - inset),
                (int) (8 * u + inset * 2), (int) (9 * u + inset * 2));
    }

    private void drawGrid(Graphics2D g) {
        float thin = Math.max(1.1f, (float) (u * .022f));
        float bold = Math.max(1.8f, (float) (u * .042f));
        g.setColor(LINE);
        g.setStroke(new BasicStroke(thin));
        for (int r = 0; r < 10; r++)
            g.drawLine(X(0), Y(r), X(8), Y(r));
        // 竖线在河界处断开，左右边框贯通
        for (int c = 0; c < 9; c++) {
            if (c == 0 || c == 8) {
                g.drawLine(X(c), Y(0), X(c), Y(9));
            } else {
                g.drawLine(X(c), Y(0), X(c), Y(4));
                g.drawLine(X(c), Y(5), X(c), Y(9));
            }
        }
        // 外框加粗
        g.setStroke(new BasicStroke(bold));
        g.drawRect(X(0), Y(0), X(8) - X(0), Y(9) - Y(0));
        g.setStroke(new BasicStroke(thin));
        // 九宫斜线
        line(g, 0, 3, 2, 5);
        line(g, 0, 5, 2, 3);
        line(g, 7, 3, 9, 5);
        line(g, 7, 5, 9, 3);
        // 兵、炮定位十字
        int[][] marks = {
                {2, 1}, {2, 7}, {7, 1}, {7, 7},
                {3, 0}, {3, 2}, {3, 4}, {3, 6}, {3, 8},
                {6, 0}, {6, 2}, {6, 4}, {6, 6}, {6, 8}
        };
        for (int[] m : marks) drawMark(g, m[0], m[1]);
    }

    private void line(Graphics2D g, int r1, int c1, int r2, int c2) {
        g.drawLine(X(c1), Y(r1), X(c2), Y(r2));
    }

    private void drawMark(Graphics2D g, int r, int c) {
        int x = X(c), y = Y(r);
        int gap = (int) (u * .085), len = (int) (u * .19);
        if (gap < 2) return;
        g.setStroke(new BasicStroke(Math.max(1f, (float) (u * .022f))));
        for (int sx : new int[]{-1, 1})
            for (int sy : new int[]{-1, 1}) {
                if (c + sx < 0 || c + sx > 8) continue;
                int x1 = x + sx * gap, y1 = y + sy * gap;
                g.drawLine(x1, y1, x1 + sx * len, y1);
                g.drawLine(x1, y1, x1, y1 + sy * len);
            }
    }

    private void drawRiverText(Graphics2D g) {
        String a = "楚  河", b = "漢  界";
        if (flipped) { String t = a; a = b; b = t; }
        Font f = new Font(PIECE_FONT, Font.BOLD, (int) (u * .58));
        g.setFont(f);
        g.setColor(new Color(96, 62, 30, 205));
        FontMetrics fm = g.getFontMetrics();
        double cy = oy + 4.5 * u + (fm.getAscent() - fm.getDescent()) / 2.0;
        int y = (int) cy;
        int x1 = (int) (ox + 1.05 * u), x2 = (int) (ox + 5.05 * u);
        if (flipped) {
            drawRotated(g, a, x1 + fm.stringWidth(a) / 2, y);
            drawRotated(g, b, x2 + fm.stringWidth(b) / 2, y);
        } else {
            g.drawString(a, x1, y);
            g.drawString(b, x2, y);
        }
    }

    private void drawRotated(Graphics2D g, String s, int x, int y) {
        java.awt.geom.AffineTransform old = g.getTransform();
        g.rotate(Math.PI, x, y - g.getFontMetrics().getAscent() / 2.0 + g.getFontMetrics().getDescent() / 2.0);
        g.drawString(s, x - g.getFontMetrics().stringWidth(s) / 2, y);
        g.setTransform(old);
    }

    private void drawLastMove(Graphics2D g) {
        if (lastFrom < 0 || lastTo < 0) return;
        g.setColor(new Color(214, 158, 44, 190));
        g.setStroke(new BasicStroke(Math.max(2f, (float) (u * .045f))));
        corner(g, lastFrom);
        corner(g, lastTo);
        g.setColor(new Color(214, 158, 44, 28));
        g.fillOval(X(lastTo % 9) - (int) (u * .44), Y(lastTo / 9) - (int) (u * .44),
                (int) (u * .88), (int) (u * .88));
    }

    private void corner(Graphics2D g, int sq) {
        int x = X(sq % 9), y = Y(sq / 9);
        int d = (int) (u * .46), l = (int) (u * .16);
        for (int sx : new int[]{-1, 1})
            for (int sy : new int[]{-1, 1}) {
                int px = x + sx * d, py = y + sy * d;
                g.drawLine(px, py, px - sx * l, py);
                g.drawLine(px, py, px, py - sy * l);
            }
    }

    private void drawHintArrow(Graphics2D g) {
        int x1 = X(hintFrom % 9), y1 = Y(hintFrom / 9);
        int x2 = X(hintTo % 9), y2 = Y(hintTo / 9);
        Stroke old = g.getStroke();
        g.setColor(new Color(30, 120, 210, 210));
        g.setStroke(new BasicStroke(Math.max(2.5f, (float) (u * .055f)), BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND, 0, new float[]{(float) (u * .16f), (float) (u * .12f)}, 0));
        g.drawLine(x1, y1, x2, y2);
        g.setStroke(old);
        double ang = Math.atan2(y2 - y1, x2 - x1);
        int size = (int) (u * .20);
        int[] xs = {x2, (int) (x2 - size * Math.cos(ang - .45)), (int) (x2 - size * Math.cos(ang + .45))};
        int[] ys = {y2, (int) (y2 - size * Math.sin(ang - .45)), (int) (y2 - size * Math.sin(ang + .45))};
        g.fillPolygon(xs, ys, 3);
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(Math.max(2f, (float) (u * .045f))));
        g.drawOval(x1 - (int) (u * .47), y1 - (int) (u * .47), (int) (u * .94), (int) (u * .94));
    }

    private void drawHints(Graphics2D g) {
        if (selected < 0) return;
        for (int i = 0; i < hints.n; i++) {
            int to = Board.toOf(hints.a[i]);
            int x = X(to % 9), y = Y(to / 9);
            boolean capture = board.sq[to / 9][to % 9] != 0;
            if (capture) {
                g.setColor(new Color(200, 45, 35, 210));
                g.setStroke(new BasicStroke(Math.max(2.5f, (float) (u * .055f))));
                int d = (int) (u * .90);
                g.drawOval(x - d / 2, y - d / 2, d, d);
            } else {
                int d = (int) (u * .20);
                g.setColor(new Color(40, 135, 85, 165));
                g.fillOval(x - d / 2, y - d / 2, d, d);
            }
        }
    }

    private void drawPieces(Graphics2D g) {
        int rad = (int) (u * .435);
        Font pf = new Font(PIECE_FONT, Font.BOLD, Math.max(10, (int) (u * .52)));
        for (int i = 0; i < 90; i++) {
            int p = board.sq[i / 9][i % 9];
            if (p == 0) continue;
            int x = X(i % 9), y = Y(i / 9);
            // 阴影
            g.setColor(new Color(0, 0, 0, 65));
            g.fillOval(x - rad + (int) (u * .045), y - rad + (int) (u * .065), rad * 2, rad * 2);
            // 外缘
            g.setColor(new Color(74, 50, 27));
            g.fillOval(x - rad, y - rad, rad * 2, rad * 2);
            // 木色盘面
            g.setPaint(new GradientPaint(x - rad, y - rad, new Color(255, 241, 211),
                    x + rad, y + rad, new Color(228, 198, 145)));
            g.fillOval(x - rad + 2, y - rad + 2, rad * 2 - 4, rad * 2 - 4);
            Color ink = p > 0 ? RED_PIECE : BLACK_PIECE;
            g.setColor(ink);
            g.setStroke(new BasicStroke(Math.max(1.4f, (float) (u * .032f))));
            int ring = (int) (u * .105);
            g.drawOval(x - rad + ring, y - rad + ring, rad * 2 - ring * 2, rad * 2 - ring * 2);
            String s = (p > 0 ? Board.RED_NAME : Board.BLACK_NAME)[Board.type(p)];
            g.setFont(pf);
            g.setColor(ink);
            FontMetrics fm = g.getFontMetrics();
            g.drawString(s, x - fm.stringWidth(s) / 2f,
                    y + (fm.getAscent() - fm.getDescent()) / 2f - 1);
        }
    }

    private void drawOverlays(Graphics2D g) {
        int rad = (int) (u * .47);
        // 被将军的将：脉动的红色光环
        if (checkedKing >= 0) {
            int x = X(checkedKing % 9), y = Y(checkedKing / 9);
            float alpha = .35f + .35f * (float) Math.abs(Math.sin(tick * .18));
            g.setColor(new Color(225, 40, 35, (int) (255 * alpha)));
            g.setStroke(new BasicStroke(Math.max(3f, (float) (u * .07f))));
            g.drawOval(x - rad, y - rad, rad * 2, rad * 2);
        }
        // 选中框
        if (selected >= 0) {
            int x = X(selected % 9), y = Y(selected / 9);
            g.setColor(GOLD);
            g.setStroke(new BasicStroke(Math.max(2.5f, (float) (u * .055f))));
            g.drawOval(x - rad, y - rad, rad * 2, rad * 2);
            g.setColor(new Color(232, 165, 32, 45));
            g.fillOval(x - rad, y - rad, rad * 2, rad * 2);
        }
    }

    private void drawBanner(Graphics2D g) {
        Font f = uiFont(Font.BOLD, Math.max(18, (int) (u * .52)));
        g.setFont(f);
        FontMetrics fm = g.getFontMetrics();
        int tw = fm.stringWidth(banner);
        int bw = tw + (int) (u * 1.6), bh = (int) (u * 1.25);
        int x = (getWidth() - bw) / 2, y = (getHeight() - bh) / 2;
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, .94f));
        g.setColor(new Color(38, 32, 26));
        g.fillRoundRect(x, y, bw, bh, 14, 14);
        g.setColor(GOLD);
        g.setStroke(new BasicStroke(2.5f));
        g.drawRoundRect(x, y, bw, bh, 14, 14);
        g.setColor(new Color(255, 240, 205));
        g.drawString(banner, x + (bw - tw) / 2f, y + bh / 2f + (fm.getAscent() - fm.getDescent()) / 2f);
        g.setComposite(AlphaComposite.SrcOver);
    }
}
