package cn.biolmz.xiangqi;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.widget.Toast;

import java.util.ArrayList;

import chess.AI;
import chess.Board;

/** 安卓版中国象棋：复用桌面版纯 Java 规则引擎与 AI，界面用 Canvas 自绘。 */
public class MainActivity extends Activity {

    private static final class Step {
        int move, cap, quiet, side;
        boolean givesCheck;
        long hash;
    }

    private static final String[] BUTTONS = {"新局", "悔棋", "难度", "模式", "认输"};

    private final Board board = new Board();
    private final ArrayList<Step> history = new ArrayList<>();
    private final ArrayList<Long> positions = new ArrayList<>();
    private final Board.MoveList legal = new Board.MoveList();
    private final Board.MoveList hints = new Board.MoveList();

    private int selected = -1, lastFrom = -1, lastTo = -1, checkedKing = -1;
    private boolean gameOver = false, thinking = false, vsComputer = true;
    private int level = 2;
    private long epoch = 0;
    private String banner = "";
    private String status = "";
    private ChessView view;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        view = new ChessView(this);
        setContentView(view);
        newGame();
    }

    // ---------------------------------------------------------------- 对局逻辑

    private void newGame() {
        epoch++;
        thinking = false;
        board.reset();
        history.clear();
        positions.clear();
        positions.add(board.hash());
        gameOver = false;
        selected = -1;
        hints.clear();
        lastFrom = lastTo = checkedKing = -1;
        banner = "";
        board.genLegal(board.side, legal);
        status = "红方行棋 · 点击己方棋子";
        view.postInvalidate();
        maybeAI();
    }

    private void performMove(int m) {
        int from = Board.fromOf(m), to = Board.toOf(m);
        String cn = board.toChinese(m);
        int cap = board.sq[to / 9][to % 9];
        Step st = new Step();
        st.move = m;
        st.cap = cap;
        st.quiet = board.quietPly;
        st.side = board.side;

        board.applyRaw(m);
        board.quietPly = (cap == 0) ? board.quietPly + 1 : 0;
        board.side = -st.side;
        st.givesCheck = board.inCheck(board.side);
        st.hash = board.hash();
        history.add(st);
        positions.add(st.hash);

        selected = -1;
        hints.clear();
        lastFrom = from;
        lastTo = to;
        checkedKing = -1;
        board.genLegal(board.side, legal);

        int res = board.result();
        int loop = (res == Board.ONGOING) ? adjudicateLoop() : Board.LOOP_NONE;
        if (res != Board.ONGOING) {
            gameOver = true;
            if (res == Board.DRAW_LIMIT) banner = "和  棋";
            else banner = ((-board.side == Board.RED) ? "红方胜" : "黑方胜")
                    + (res == Board.CHECKMATE ? " · 将死" : " · 困毙");
        } else if (loop != Board.LOOP_NONE) {
            gameOver = true;
            if (loop == Board.LOOP_DRAW) banner = "和  棋 · 三次重复";
            else banner = (loop == Board.LOOP_RED_PERPETUAL ? "黑方胜" : "红方胜") + " · 长将判负";
        } else {
            status = cn + (st.givesCheck ? " · 将军！" : " · 第 " + (history.size() / 2 + 1) + " 回合");
            if (st.givesCheck) checkedKing = board.kingSquare(board.side);
            int rep = repetitionCount();
            if (rep >= 2) status += " · 重复 " + rep + " 次";
        }
        view.postInvalidate();
        maybeAI();
    }

    private void maybeAI() {
        if (!vsComputer || gameOver || thinking || board.side != Board.BLACK) return;
        thinking = true;
        final long gen = ++epoch;
        final long[] hashes = new long[positions.size()];
        for (int i = 0; i < hashes.length; i++) hashes[i] = positions.get(i);
        final int steps = history.size();
        int[] p = {-1, -1};
        for (int i = history.size() - 1; i >= 0 && i >= history.size() - 4; i--) {
            Step s = history.get(i);
            if (s.side == board.side) {
                p[0] = Board.fromOf(s.move);
                p[1] = Board.toOf(s.move);
                break;
            }
        }
        final int[] prev = p;
        status = "黑方（电脑）思考中…";
        view.postInvalidate();

        new Thread(new Runnable() {
            @Override public void run() {
                final int m = new AI().think(board, level, steps, hashes, prev[0], prev[1]);
                view.post(new Runnable() {
                    @Override public void run() {
                        thinking = false;
                        if (gen != epoch) return;
                        if (m != 0 && !gameOver && board.side == Board.BLACK) performMove(m);
                        else view.postInvalidate();
                    }
                });
            }
        }, "xiangqi-ai").start();
    }

    private void undo() {
        epoch++;
        thinking = false;
        if (history.isEmpty()) return;
        int n = 1;
        if (vsComputer) {
            Step last = history.get(history.size() - 1);
            n = (last.side == Board.BLACK) ? 2 : 1;
        }
        n = Math.min(n, history.size());
        for (int i = 0; i < n; i++) {
            Step st = history.remove(history.size() - 1);
            positions.remove(positions.size() - 1);
            board.undoRaw(st.move, st.cap);
            board.quietPly = st.quiet;
            board.side = st.side;
        }
        gameOver = false;
        banner = "";
        selected = -1;
        hints.clear();
        if (history.isEmpty()) {
            lastFrom = lastTo = -1;
        } else {
            Step l = history.get(history.size() - 1);
            lastFrom = Board.fromOf(l.move);
            lastTo = Board.toOf(l.move);
        }
        board.genLegal(board.side, legal);
        checkedKing = board.inCheck(board.side) ? board.kingSquare(board.side) : -1;
        status = "已悔 " + n + " 步";
        view.postInvalidate();
        maybeAI();
    }

    private int adjudicateLoop() {
        int n = history.size();
        long[] pos = new long[n + 1];
        boolean[] gives = new boolean[n], caps = new boolean[n];
        int[] sides = new int[n];
        for (int i = 0; i <= n; i++) pos[i] = positions.get(i);
        for (int i = 0; i < n; i++) {
            Step s = history.get(i);
            gives[i] = s.givesCheck;
            sides[i] = s.side;
            caps[i] = s.cap != 0;
        }
        return Board.judgeLoop(pos, gives, sides, caps);
    }

    private int repetitionCount() {
        long cur = board.hash();
        int c = 0;
        for (long h : positions) if (h == cur) c++;
        return c;
    }

    private void handleClick(int sq) {
        if (gameOver || thinking) return;
        if (vsComputer && board.side == Board.BLACK) return;
        int p = board.sq[sq / 9][sq % 9];
        if (p != 0 && Board.sign(p) == board.side) {
            selected = sq;
            hints.clear();
            board.genLegal(board.side, legal);
            for (int i = 0; i < legal.n; i++)
                if (Board.fromOf(legal.a[i]) == sq) hints.add(legal.a[i]);
            view.postInvalidate();
            return;
        }
        for (int i = 0; i < hints.n; i++) {
            if (Board.toOf(hints.a[i]) == sq) {
                performMove(hints.a[i]);
                return;
            }
        }
        selected = -1;
        hints.clear();
        view.postInvalidate();
    }

    private void onButton(int index) {
        switch (index) {
            case 0: newGame(); break;
            case 1: undo(); break;
            case 2: showLevelDialog(); break;
            case 3:
                vsComputer = !vsComputer;
                Toast.makeText(this, vsComputer ? "人机对战" : "双人对弈", Toast.LENGTH_SHORT).show();
                newGame();
                break;
            case 4:
                new AlertDialog.Builder(this).setTitle("认输")
                        .setMessage("确定由当前行棋方认输吗？")
                        .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface d, int w) {
                                gameOver = true;
                                epoch++;
                                banner = (board.side == Board.RED ? "黑方胜" : "红方胜") + " · 认输";
                                view.postInvalidate();
                            }
                        }).setNegativeButton("取消", null).show();
                break;
            default: break;
        }
    }

    private void showLevelDialog() {
        final String[] items = {"入门", "普通", "高手"};
        new AlertDialog.Builder(this).setTitle("电脑难度")
                .setSingleChoiceItems(items, level - 1, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        level = which + 1;
                        d.dismiss();
                        Toast.makeText(MainActivity.this, "难度：" + items[which], Toast.LENGTH_SHORT).show();
                        view.postInvalidate();
                    }
                }).setNegativeButton("取消", null).show();
    }

    // ---------------------------------------------------------------- 界面绘制

    private final class ChessView extends View {

        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float u, ox, oy, topH, btnH;

        ChessView(android.content.Context ctx) { super(ctx); }

        private float dp(float v) { return v * getResources().getDisplayMetrics().density; }

        @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
            topH = dp(48);
            btnH = dp(60);
            u = Math.min(w / 9.5f, (h - topH - btnH) / 10.2f);
            if (u < 10f) u = 10f;
            ox = (w - 8 * u) / 2f;
            oy = topH + (h - topH - btnH - 9 * u) / 2f;
        }

        private float X(int c) { return ox + c * u; }
        private float Y(int r) { return oy + r * u; }

        @Override protected void onDraw(Canvas c) {
            int w = getWidth(), h = getHeight();
            fill.setStyle(Paint.Style.FILL);
            fill.setColor(Color.rgb(38, 32, 27));
            c.drawRect(0, 0, w, h, fill);

            drawStatus(c, w);
            drawBoard(c);
            drawMoves(c);
            drawPieces(c);
            if (!banner.isEmpty()) drawBanner(c, w, h);
            drawButtons(c, w, h);
        }

        private void drawStatus(Canvas c, int w) {
            label.setStyle(Paint.Style.FILL);
            label.setTextAlign(Paint.Align.CENTER);
            label.setTypeface(Typeface.DEFAULT_BOLD);
            label.setTextSize(dp(15));
            label.setColor(Color.rgb(226, 170, 60));
            c.drawText(status, w / 2f, topH * 0.62f, label);
            fill.setColor(Color.rgb(88, 72, 54));
            c.drawRect(0, topH - dp(1), w, topH, fill);
        }

        private void drawBoard(Canvas c) {
            float m = u * 0.5f;
            fill.setColor(Color.rgb(238, 208, 154));
            c.drawRoundRect(new RectF(ox - m, oy - m, X(8) + m, Y(9) + m),
                    u * .10f, u * .10f, fill);

            stroke.setStyle(Paint.Style.STROKE);
            stroke.setColor(Color.rgb(103, 68, 36));
            stroke.setStrokeWidth(Math.max(1f, u * .020f));
            for (int r = 0; r < 10; r++) c.drawLine(X(0), Y(r), X(8), Y(r), stroke);
            for (int col = 0; col < 9; col++) {
                if (col == 0 || col == 8) c.drawLine(X(col), Y(0), X(col), Y(9), stroke);
                else {
                    c.drawLine(X(col), Y(0), X(col), Y(4), stroke);
                    c.drawLine(X(col), Y(5), X(col), Y(9), stroke);
                }
            }
            stroke.setStrokeWidth(Math.max(1.6f, u * .038f));
            c.drawRect(X(0), Y(0), X(8), Y(9), stroke);
            stroke.setStrokeWidth(Math.max(1f, u * .020f));
            c.drawLine(X(3), Y(0), X(5), Y(2), stroke);
            c.drawLine(X(5), Y(0), X(3), Y(2), stroke);
            c.drawLine(X(3), Y(7), X(5), Y(9), stroke);
            c.drawLine(X(5), Y(7), X(3), Y(9), stroke);

            int[][] marks = {{2, 1}, {2, 7}, {7, 1}, {7, 7},
                    {3, 0}, {3, 2}, {3, 4}, {3, 6}, {3, 8},
                    {6, 0}, {6, 2}, {6, 4}, {6, 6}, {6, 8}};
            float gap = u * .085f, len = u * .18f;
            for (int[] mk : marks) {
                float x = X(mk[1]), y = Y(mk[0]);
                for (int sx = -1; sx <= 1; sx += 2)
                    for (int sy = -1; sy <= 1; sy += 2) {
                        if (mk[1] + sx < 0 || mk[1] + sx > 8) continue;
                        float px = x + sx * gap, py = y + sy * gap;
                        c.drawLine(px, py, px + sx * len, py, stroke);
                        c.drawLine(px, py, px, py + sy * len, stroke);
                    }
            }
            label.setTextAlign(Paint.Align.LEFT);
            label.setTypeface(Typeface.DEFAULT_BOLD);
            label.setTextSize(u * .52f);
            label.setColor(Color.rgb(96, 62, 30));
            c.drawText("楚  河", X(0) + u * .7f, Y(4) + u * .68f, label);
            c.drawText("漢  界", X(4) + u * .7f, Y(4) + u * .68f, label);
        }

        private void drawMoves(Canvas c) {
            if (lastFrom >= 0) {
                stroke.setStyle(Paint.Style.STROKE);
                stroke.setColor(Color.rgb(214, 158, 44));
                stroke.setStrokeWidth(Math.max(2f, u * .04f));
                corner(c, lastFrom);
                corner(c, lastTo);
            }
            for (int i = 0; i < hints.n; i++) {
                int to = Board.toOf(hints.a[i]);
                float x = X(to % 9), y = Y(to / 9);
                boolean cap = board.sq[to / 9][to % 9] != 0;
                if (cap) {
                    stroke.setStyle(Paint.Style.STROKE);
                    stroke.setColor(Color.rgb(200, 45, 35));
                    stroke.setStrokeWidth(Math.max(2f, u * .05f));
                    c.drawCircle(x, y, u * .45f, stroke);
                } else {
                    fill.setStyle(Paint.Style.FILL);
                    fill.setColor(Color.rgb(40, 135, 85));
                    c.drawCircle(x, y, u * .10f, fill);
                }
            }
        }

        private void corner(Canvas c, int sq) {
            float x = X(sq % 9), y = Y(sq / 9), d = u * .44f, l = u * .16f;
            for (int sx = -1; sx <= 1; sx += 2)
                for (int sy = -1; sy <= 1; sy += 2) {
                    float px = x + sx * d, py = y + sy * d;
                    c.drawLine(px, py, px - sx * l, py, stroke);
                    c.drawLine(px, py, px, py - sy * l, stroke);
                }
        }

        private void drawPieces(Canvas c) {
            float rad = u * .43f;
            label.setTextAlign(Paint.Align.CENTER);
            label.setTypeface(Typeface.DEFAULT_BOLD);
            label.setTextSize(u * .50f);
            Paint.FontMetrics fm = label.getFontMetrics();
            for (int i = 0; i < 90; i++) {
                int p = board.sq[i / 9][i % 9];
                if (p == 0) continue;
                float x = X(i % 9), y = Y(i / 9);
                fill.setStyle(Paint.Style.FILL);
                fill.setColor(Color.argb(60, 0, 0, 0));
                c.drawCircle(x + u * .04f, y + u * .06f, rad, fill);
                fill.setColor(Color.rgb(74, 50, 27));
                c.drawCircle(x, y, rad, fill);
                fill.setColor(Color.rgb(250, 233, 197));
                c.drawCircle(x, y, rad - u * .025f, fill);
                int ink = p > 0 ? Color.rgb(176, 38, 30) : Color.rgb(38, 39, 44);
                stroke.setStyle(Paint.Style.STROKE);
                stroke.setColor(ink);
                stroke.setStrokeWidth(Math.max(1.2f, u * .028f));
                c.drawCircle(x, y, rad - u * .10f, stroke);
                String s = (p > 0 ? Board.RED_NAME : Board.BLACK_NAME)[Board.type(p)];
                label.setColor(ink);
                c.drawText(s, x, y - (fm.ascent + fm.descent) / 2f, label);
            }
            if (selected >= 0 || checkedKing >= 0) {
                int sq = checkedKing >= 0 ? checkedKing : selected;
                float x = X(sq % 9), y = Y(sq / 9);
                stroke.setStyle(Paint.Style.STROKE);
                stroke.setColor(checkedKing >= 0 ? Color.rgb(228, 60, 50) : Color.rgb(232, 165, 32));
                stroke.setStrokeWidth(Math.max(2.5f, u * .055f));
                c.drawCircle(x, y, u * .47f, stroke);
            }
        }

        private void drawBanner(Canvas c, int w, int h) {
            float bw = u * 6.2f, bh = u * 1.1f;
            float x = (w - bw) / 2f, y = (h - btnH) / 2f - bh / 2f;
            fill.setStyle(Paint.Style.FILL);
            fill.setColor(Color.argb(238, 38, 32, 26));
            c.drawRoundRect(new RectF(x, y, x + bw, y + bh), dp(10), dp(10), fill);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setColor(Color.rgb(226, 170, 60));
            stroke.setStrokeWidth(dp(2));
            c.drawRoundRect(new RectF(x, y, x + bw, y + bh), dp(10), dp(10), stroke);
            label.setTextAlign(Paint.Align.CENTER);
            label.setTypeface(Typeface.DEFAULT_BOLD);
            label.setTextSize(u * .42f);
            label.setColor(Color.rgb(255, 240, 205));
            c.drawText(banner, w / 2f, y + bh / 2f - (label.getFontMetrics().ascent
                    + label.getFontMetrics().descent) / 2f, label);
        }

        private void drawButtons(Canvas c, int w, int h) {
            float top = h - btnH;
            fill.setStyle(Paint.Style.FILL);
            fill.setColor(Color.rgb(30, 25, 21));
            c.drawRect(0, top, w, h, fill);
            float bw = w / (float) BUTTONS.length;
            label.setTextAlign(Paint.Align.CENTER);
            label.setTypeface(Typeface.DEFAULT_BOLD);
            label.setTextSize(dp(13));
            Paint.FontMetrics fm = label.getFontMetrics();
            float cy = top + btnH / 2f - (fm.ascent + fm.descent) / 2f;
            for (int i = 0; i < BUTTONS.length; i++) {
                float l = i * bw + dp(4), r = (i + 1) * bw - dp(4);
                fill.setColor(Color.rgb(88, 70, 50));
                c.drawRoundRect(new RectF(l, top + dp(8), r, h - dp(8)), dp(6), dp(6), fill);
                label.setColor(Color.rgb(245, 228, 198));
                String t = (i == 3) ? (vsComputer ? "人机" : "双人") : BUTTONS[i];
                c.drawText(t, (l + r) / 2f, cy, label);
            }
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            if (e.getAction() != MotionEvent.ACTION_DOWN) return true;
            float x = e.getX(), y = e.getY();
            if (y >= getHeight() - btnH) {
                int idx = (int) (x / (getWidth() / (float) BUTTONS.length));
                if (idx >= 0 && idx < BUTTONS.length) onButton(idx);
                return true;
            }
            int col = Math.round((x - ox) / u), row = Math.round((y - oy) / u);
            if (row < 0 || row > 9 || col < 0 || col > 8) return true;
            if (Math.abs(x - (ox + col * u)) > u * .55f
                    || Math.abs(y - (oy + row * u)) > u * .55f) return true;
            handleClick(row * 9 + col);
            return true;
        }
    }
}
