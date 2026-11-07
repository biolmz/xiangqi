package chess;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.ButtonGroup;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Toolkit;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;

/** 主窗口：菜单、状态、棋谱、人机调度、悔棋与终局处理。 */
public final class GameFrame extends JFrame {

    private static final Color BG = new Color(38, 32, 27);
    private static final Color CARD = new Color(56, 47, 39);
    private static final Color INK = new Color(238, 223, 197);
    private static final Color MUTED = new Color(170, 155, 134);
    private static final Color GOLD = new Color(226, 170, 60);
    private static final Color DANGER = new Color(232, 92, 78);

    private static final class Step {
        int move, cap, quiet, side;
        boolean givesCheck;
        long hash;
        String text;
    }

    private final Board board = new Board();
    private final Board.MoveList legal = new Board.MoveList();
    private final ArrayList<Step> history = new ArrayList<>();
    /** 局面指纹序列：positions[0] 为初始局面，positions[i+1] 为第 i 步之后 */
    private final ArrayList<Long> positions = new ArrayList<>();
    private final BoardPanel boardPanel = new BoardPanel(board);

    private boolean vsComputer = true;
    private int humanSide = Board.RED;
    private int level = 2;
    private boolean gameOver = false;
    private boolean thinking = false;
    private long epoch = 0;
    private SwingWorker<Integer, Void> worker;

    private final JLabel turnLabel = new JLabel(" ");
    private final JLabel subLabel = new JLabel(" ");
    private final JLabel checkLabel = new JLabel(" ");
    private final JLabel lostLabel = new JLabel(" ");
    private final JTextArea recordArea = new JTextArea();
    private final JLabel statusBar = new JLabel(" ");
    private final JButton btnNew = button("新的一局");
    private final JButton btnUndo = button("悔  棋");
    private final JButton btnHint = button("支  招");
    private final JButton btnResign = button("认  输");

    private JRadioButtonMenuItem miPVC, miPVP, miRed, miBlack, miL1, miL2, miL3;
    private JCheckBoxMenuItem miHints, miSound;

    public GameFrame() {
        super("中国象棋 · Xiangqi");
        setIconImage(IconFactory.frameIcon());
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        setBackground(BG);

        setJMenuBar(buildMenu());
        add(boardPanel, BorderLayout.CENTER);
        add(buildSide(), BorderLayout.EAST);
        add(buildStatusBar(), BorderLayout.SOUTH);

        boardPanel.setClickListener(new BoardPanel.ClickListener() {
            @Override public void onClick(int sq) { handleClick(sq); }
            @Override public void onRightClick() { clearSelection(); }
        });

        btnNew.addActionListener(e -> newGame());
        btnUndo.addActionListener(e -> undo());
        btnHint.addActionListener(e -> hint());
        btnResign.addActionListener(e -> resign());

        new Timer(110, e -> boardPanel.pulse()).start();
        setSize(new Dimension(950, 760));
        setMinimumSize(new Dimension(790, 610));
        setLocationRelativeTo(null);
        newGame();
    }

    // ------------------------------------------------------------------ 界面

    private JMenuBar buildMenu() {
        JMenuBar mb = new JMenuBar();
        mb.setBackground(new Color(48, 40, 33));
        mb.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(90, 74, 56)));

        JMenu game = menu("游戏");
        game.add(item("新的一局", "control N", e -> newGame()));
        game.add(item("悔棋", "control Z", e -> undo()));
        game.add(item("支招 / 提示", "H", e -> hint()));
        game.add(item("认输", null, e -> resign()));
        game.addSeparator();
        game.add(item("导出棋谱…", "control S", e -> exportRecord()));
        game.addSeparator();
        game.add(item("退出", "alt F4", e -> dispose()));

        JMenu play = menu("对局");
        ButtonGroup g1 = new ButtonGroup();
        miPVC = radio("人机对战", g1, true, e -> setVsComputer(true));
        miPVP = radio("双人对弈", g1, false, e -> setVsComputer(false));
        play.add(miPVC);
        play.add(miPVP);
        play.addSeparator();
        ButtonGroup g2 = new ButtonGroup();
        miRed = radio("我执红（先手）", g2, true, e -> setHumanSide(Board.RED));
        miBlack = radio("我执黑（后手）", g2, false, e -> setHumanSide(Board.BLACK));
        play.add(miRed);
        play.add(miBlack);
        play.addSeparator();
        ButtonGroup g3 = new ButtonGroup();
        miL1 = radio("入门", g3, false, e -> setLevel(1));
        miL2 = radio("普通", g3, true, e -> setLevel(2));
        miL3 = radio("高手", g3, false, e -> setLevel(3));
        play.add(miL1); play.add(miL2); play.add(miL3);

        JMenu opt = menu("选项");
        miHints = check("显示可走位置", true, e -> boardPanel.setShowHints(miHints.isSelected()));
        miSound = check("落子音效", true, e -> Sound.enabled = miSound.isSelected());
        opt.add(miHints);
        opt.add(miSound);

        JMenu help = menu("帮助");
        help.add(item("规则速览", "F1", e -> showRules()));
        help.add(item("快捷键", null, e -> showKeys()));
        help.addSeparator();
        help.add(item("关于", null, e -> showAbout()));

        mb.add(game); mb.add(play); mb.add(opt); mb.add(help);
        return mb;
    }

    private JMenu menu(String s) {
        JMenu m = new JMenu(s);
        m.setFont(BoardPanel.uiFont(Font.PLAIN, 13));
        darkItem(m);
        return m;
    }

    private static void darkItem(javax.swing.JComponent c) {
        c.setBackground(new Color(48, 40, 33));
        c.setForeground(INK);
        c.setOpaque(true);
    }

    private JMenuItem item(String name, String accel, java.awt.event.ActionListener a) {
        JMenuItem it = new JMenuItem(name);
        it.setFont(BoardPanel.uiFont(Font.PLAIN, 13));
        darkItem(it);
        if (accel != null && !accel.equals("alt F4")) it.setAccelerator(KeyStroke.getKeyStroke(accel));
        it.addActionListener(a);
        return it;
    }

    private JRadioButtonMenuItem radio(String name, ButtonGroup g, boolean sel,
                                       java.awt.event.ActionListener a) {
        JRadioButtonMenuItem it = new JRadioButtonMenuItem(name, sel);
        it.setFont(BoardPanel.uiFont(Font.PLAIN, 13));
        darkItem(it);
        it.addActionListener(a);
        g.add(it);
        return it;
    }

    private JCheckBoxMenuItem check(String name, boolean sel, java.awt.event.ActionListener a) {
        JCheckBoxMenuItem it = new JCheckBoxMenuItem(name, sel);
        it.setFont(BoardPanel.uiFont(Font.PLAIN, 13));
        darkItem(it);
        it.addActionListener(a);
        return it;
    }

    private JPanel buildSide() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(BG);
        p.setBorder(BorderFactory.createEmptyBorder(14, 12, 12, 14));
        p.setPreferredSize(new Dimension(272, 0));
        p.setMinimumSize(new Dimension(240, 0));

        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(CARD);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(94, 76, 56), 1),
                BorderFactory.createEmptyBorder(14, 14, 14, 14)));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 132));
        JLabel cap = new JLabel("对局状态");
        cap.setFont(BoardPanel.uiFont(Font.PLAIN, 12));
        cap.setForeground(MUTED);
        turnLabel.setFont(BoardPanel.uiFont(Font.BOLD, 20));
        turnLabel.setForeground(GOLD);
        subLabel.setFont(BoardPanel.uiFont(Font.PLAIN, 12));
        subLabel.setForeground(MUTED);
        checkLabel.setFont(BoardPanel.uiFont(Font.BOLD, 13));
        checkLabel.setForeground(MUTED);
        card.add(cap);
        card.add(Box.createVerticalStrut(8));
        card.add(turnLabel);
        card.add(Box.createVerticalStrut(4));
        card.add(subLabel);
        card.add(Box.createVerticalStrut(6));
        card.add(checkLabel);

        JPanel buttons = new JPanel(new java.awt.GridLayout(2, 2, 8, 8));
        buttons.setBackground(BG);
        buttons.setMaximumSize(new Dimension(Integer.MAX_VALUE, 82));
        buttons.setBorder(BorderFactory.createEmptyBorder(12, 0, 6, 0));
        buttons.add(btnNew); buttons.add(btnUndo);
        buttons.add(btnHint); buttons.add(btnResign);

        JLabel recCap = section("棋  谱");
        recordArea.setEditable(false);
        recordArea.setFont(BoardPanel.uiFont(Font.PLAIN, 12));
        recordArea.setBackground(new Color(29, 25, 21));
        recordArea.setForeground(new Color(224, 207, 178));
        recordArea.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        recordArea.setLineWrap(false);
        JScrollPane scroll = new JScrollPane(recordArea);
        scroll.setBorder(BorderFactory.createLineBorder(new Color(88, 72, 54), 1));
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        scroll.getVerticalScrollBar().setBackground(new Color(56, 47, 39));

        JLabel lostCap = section("战  况");
        lostLabel.setFont(BoardPanel.uiFont(Font.PLAIN, 12));
        lostLabel.setForeground(MUTED);
        lostLabel.setVerticalAlignment(SwingConstants.TOP);

        JLabel tip = new JLabel("<html><div style='line-height:150%'>菜单「对局」可切换人机 / 双人、<br>执红 / 执黑与电脑难度。<br>右键棋盘取消选中。</div></html>");
        tip.setFont(BoardPanel.uiFont(Font.PLAIN, 11));
        tip.setForeground(new Color(140, 126, 108));
        tip.setBorder(BorderFactory.createEmptyBorder(8, 2, 0, 2));

        p.add(card);
        p.add(buttons);
        p.add(recCap);
        p.add(scroll);
        p.add(Box.createVerticalStrut(10));
        p.add(lostCap);
        p.add(lostLabel);
        p.add(Box.createVerticalStrut(6));
        p.add(tip);
        p.add(Box.createVerticalGlue());
        return p;
    }

    private JLabel section(String s) {
        JLabel l = new JLabel(s);
        l.setFont(BoardPanel.uiFont(Font.BOLD, 13));
        l.setForeground(GOLD);
        l.setBorder(BorderFactory.createEmptyBorder(8, 2, 5, 2));
        l.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        return l;
    }

    private JLabel buildStatusBar() {
        statusBar.setFont(BoardPanel.uiFont(Font.PLAIN, 12));
        statusBar.setForeground(MUTED);
        statusBar.setBackground(new Color(30, 25, 21));
        statusBar.setOpaque(true);
        statusBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(84, 68, 50)),
                BorderFactory.createEmptyBorder(5, 12, 5, 12)));
        return statusBar;
    }

    private static JButton button(String text) {
        JButton b = new JButton(text);
        // 系统外观会忽略自定义背景，改用 Basic UI 才能保持深色主题与清晰对比度
        b.setUI(new javax.swing.plaf.basic.BasicButtonUI());
        b.setFont(BoardPanel.uiFont(Font.BOLD, 13));
        b.setForeground(new Color(245, 228, 198));
        b.setBackground(new Color(88, 70, 50));
        b.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(140, 112, 74), 1),
                BorderFactory.createEmptyBorder(6, 4, 6, 4)));
        b.setFocusPainted(false);
        b.setRolloverEnabled(false);
        b.setOpaque(true);
        b.setContentAreaFilled(true);
        return b;
    }

    // ------------------------------------------------------------------ 交互

    private boolean humanTurn() {
        return !gameOver && !thinking && (!vsComputer || board.side == humanSide);
    }

    private void handleClick(int sq) {
        if (!humanTurn()) {
            if (thinking) statusBar.setText("电脑正在思考，请稍候…");
            return;
        }
        int p = board.sq[sq / 9][sq % 9];

        if (p != 0 && Board.sign(p) == board.side) {
            boardPanel.setSelected(sq);
            Board.MoveList mine = new Board.MoveList();
            board.genLegal(board.side, legal);
            for (int i = 0; i < legal.n; i++)
                if (Board.fromOf(legal.a[i]) == sq) mine.add(legal.a[i]);
            boardPanel.setHints(mine);
            remember(sq, mine);
            Sound.play("select");
            statusBar.setText(mine.n == 0 ? "这个棋子当前没有合法走法" : "已选中，绿色圆点为可走位置");
            return;
        }

        BoardPanelSelection sel = currentSelection();
        if (sel != null) {
            for (int i = 0; i < sel.hints.n; i++) {
                if (Board.toOf(sel.hints.a[i]) == sq) {
                    performMove(sel.hints.a[i]);
                    return;
                }
            }
        }
        if (p != 0) {
            statusBar.setText("那是对方的棋子，或该走法不合法");
            Sound.play("illegal");
        } else {
            clearSelection();
        }
    }

    /** 选中状态由 BoardPanel 维护，这里通过反射式保存的快照取回（见 BoardPanelSnapshot） */
    private static final class BoardPanelSelection {
        int square;
        Board.MoveList hints;
    }

    private BoardPanelSnapshot snapshot = new BoardPanelSnapshot();

    private static final class BoardPanelSnapshot {
        int square = -1;
        Board.MoveList hints = new Board.MoveList();
    }

    private BoardPanelSelection currentSelection() {
        if (snapshot.square < 0) return null;
        BoardPanelSelection s = new BoardPanelSelection();
        s.square = snapshot.square;
        s.hints = snapshot.hints;
        return s;
    }

    private void clearSelection() {
        snapshot.square = -1;
        snapshot.hints.clear();
        boardPanel.setSelected(-1);
        boardPanel.setHints(null);
    }

    /** 记录选中态（供 handleClick 使用） */
    private void remember(int square, Board.MoveList hints) {
        snapshot.square = square;
        snapshot.hints.clear();
        for (int i = 0; i < hints.n; i++) snapshot.hints.add(hints.a[i]);
    }

    /** 把本局历史交给规则引擎，裁决三次重复局面与单方长将 */
    private int adjudicateLoop() {
        int n = history.size();
        long[] pos = new long[n + 1];
        boolean[] gives = new boolean[n];
        int[] sides = new int[n];
        boolean[] caps = new boolean[n];
        for (int i = 0; i <= n; i++) pos[i] = positions.get(i);
        for (int i = 0; i < n; i++) {
            Step s = history.get(i);
            gives[i] = s.givesCheck;
            sides[i] = s.side;
            caps[i] = s.cap != 0;
        }
        return Board.judgeLoop(pos, gives, sides, caps);
    }

    private long[] positionHashes() {
        long[] a = new long[positions.size()];
        for (int i = 0; i < a.length; i++) a[i] = positions.get(i);
        return a;
    }

    /** 当前行棋方最近一次走的着法，用于抑制「原路退回」式兜圈 */
    private int[] lastMoveBy(int side) {
        for (int i = history.size() - 1; i >= 0 && i >= history.size() - 4; i--) {
            Step s = history.get(i);
            if (s.side == side) return new int[]{Board.fromOf(s.move), Board.toOf(s.move)};
        }
        return new int[]{-1, -1};
    }

    private int repetitionCount() {
        long cur = board.hash();
        int c = 0;
        for (long h : positions) if (h == cur) c++;
        return c;
    }

    private void performMove(int m) {
        int from = Board.fromOf(m), to = Board.toOf(m);
        int cap = board.sq[to / 9][to % 9];
        Step st = new Step();
        st.move = m;
        st.cap = cap;
        st.quiet = board.quietPly;
        st.side = board.side;
        st.text = board.toChinese(m);

        board.applyRaw(m);
        board.quietPly = (cap == 0) ? board.quietPly + 1 : 0;
        board.side = -st.side;
        st.givesCheck = board.inCheck(board.side);
        st.hash = board.hash();
        history.add(st);
        positions.add(st.hash);

        clearSelection();
        boardPanel.setLastMove(from, to);
        boardPanel.setHintMove(-1, -1);
        boardPanel.setCheckedKing(-1);

        boolean inCheck = st.givesCheck;
        int res = board.result();
        int loop = (res == Board.ONGOING) ? adjudicateLoop() : Board.LOOP_NONE;

        if (res != Board.ONGOING) {
            gameOver = true;
            String title;
            if (res == Board.DRAW_LIMIT) {
                title = "和  棋";
            } else {
                int winner = -board.side;
                title = (winner == Board.RED ? "红方胜" : "黑方胜")
                        + (res == Board.CHECKMATE ? " · 将死" : " · 困毙");
            }
            boardPanel.setBanner(title);
            statusBar.setText("对局结束：" + title + "　点击「新的一局」再来一盘");
            if (vsComputer) {
                boolean humanWon = (res != Board.DRAW_LIMIT) && (-board.side == humanSide);
                Sound.play(humanWon ? "win" : "lose");
            } else {
                Sound.play("win");
            }
        } else if (loop != Board.LOOP_NONE) {
            gameOver = true;
            String title = (loop == Board.LOOP_DRAW)
                    ? "和  棋"
                    : (loop == Board.LOOP_RED_PERPETUAL ? "黑方胜" : "红方胜") + " · 长将判负";
            boardPanel.setBanner(title);
            statusBar.setText("对局结束：" + title
                    + (loop == Board.LOOP_DRAW ? "（三次重复局面）" : "（连续长将，规则判负）")
                    + "　点击「新的一局」再来一盘");
            if (loop == Board.LOOP_DRAW) {
                Sound.play("select");
            } else {
                int loser = (loop == Board.LOOP_RED_PERPETUAL) ? Board.RED : Board.BLACK;
                Sound.play(vsComputer && loser == humanSide ? "lose" : "win");
            }
        } else {
            int rep = repetitionCount();
            String repTip = rep >= 2 ? "　·　局面重复 " + rep + " 次，再重复将判和" : "";
            if (inCheck) {
                boardPanel.setCheckedKing(board.kingSquare(board.side));
                Sound.play("check");
                statusBar.setText("将军！必须应将" + repTip);
            } else {
                Sound.play(cap != 0 ? "capture" : "move");
                statusBar.setText(st.text + (cap != 0 ? "　（吃子）" : "") + repTip);
            }
        }
        board.genLegal(board.side, legal);
        refresh();
        maybeAI();
    }

    private void maybeAI() {
        if (!vsComputer || gameOver || board.side == humanSide || thinking) return;
        final long gen = ++epoch;
        thinking = true;
        refresh();
        // 在 EDT 上先做好快照，后台线程只读，避免与悔棋等改动竞争
        final long[] hashes = positionHashes();
        final int[] last = lastMoveBy(board.side);
        final int steps = history.size();
        worker = new SwingWorker<Integer, Void>() {
            @Override protected Integer doInBackground() {
                return new AI().think(board, level, steps, hashes, last[0], last[1]);
            }
            @Override protected void done() {
                thinking = false;
                if (gen != epoch) { refresh(); return; }
                try {
                    int m = get();
                    if (m != 0 && !gameOver && !isCancelled() && gen == epoch
                            && board.side != humanSide) {
                        performMove(m);
                        return;
                    }
                } catch (Exception ignored) {
                    // 取消 / 中断时忽略
                }
                refresh();
            }
        };
        worker.execute();
    }

    private void undo() {
        stopThinking();
        if (history.isEmpty()) {
            statusBar.setText("还没有可以悔的棋");
            return;
        }
        int n = 1;
        if (vsComputer) {
            Step last = history.get(history.size() - 1);
            n = (last.side != humanSide) ? 2 : 1;
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
        clearSelection();
        boardPanel.setBanner("");
        boardPanel.setHintMove(-1, -1);
        boardPanel.setCheckedKing(-1);
        if (history.isEmpty()) boardPanel.setLastMove(-1, -1);
        else {
            Step last = history.get(history.size() - 1);
            boardPanel.setLastMove(Board.fromOf(last.move), Board.toOf(last.move));
        }
        board.genLegal(board.side, legal);
        if (board.inCheck(board.side)) boardPanel.setCheckedKing(board.kingSquare(board.side));
        statusBar.setText("已悔 " + n + " 步");
        Sound.play("select");
        refresh();
        maybeAI();
    }

    private void stopThinking() {
        epoch++;
        if (worker != null) worker.cancel(true);
        thinking = false;
    }

    private void newGame() {
        stopThinking();
        board.reset();
        history.clear();
        positions.clear();
        positions.add(board.hash());
        gameOver = false;
        clearSelection();
        boardPanel.setBanner("");
        boardPanel.setLastMove(-1, -1);
        boardPanel.setHintMove(-1, -1);
        boardPanel.setCheckedKing(-1);
        boardPanel.setFlipped(vsComputer && humanSide == Board.BLACK);
        board.genLegal(board.side, legal);
        statusBar.setText("新局开始，红方先行　·　点击己方棋子选中，再点目标格落子");
        refresh();
        maybeAI();
    }

    private void hint() {
        if (!humanTurn()) { statusBar.setText("现在不是你落子"); return; }
        final long gen = ++epoch;
        statusBar.setText("正在计算推荐走法…");
        final long[] hashes = positionHashes();
        final int[] last = lastMoveBy(board.side);
        final int steps = history.size();
        SwingWorker<Integer, Void> w = new SwingWorker<Integer, Void>() {
            @Override protected Integer doInBackground() {
                return new AI().think(board, level, steps, hashes, last[0], last[1]);
            }
            @Override protected void done() {
                if (gen != epoch) return;
                try {
                    int m = get();
                    if (m != 0) {
                        boardPanel.setHintMove(Board.fromOf(m), Board.toOf(m));
                        statusBar.setText("推荐：" + board.toChinese(m) + "　（蓝色虚线箭头）");
                    }
                } catch (Exception ignored) { }
            }
        };
        w.execute();
    }

    private void resign() {
        stopThinking();
        if (gameOver) return;
        int side = vsComputer ? humanSide : board.side;
        int r = JOptionPane.showConfirmDialog(this,
                "确定由" + (side == Board.RED ? "红方" : "黑方") + "认输吗？",
                "认输", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (r != JOptionPane.YES_OPTION) return;
        gameOver = true;
        String title = (side == Board.RED ? "黑方胜" : "红方胜") + " · 认输";
        boardPanel.setBanner(title);
        statusBar.setText("对局结束：" + title);
        Sound.play("lose");
        refresh();
    }

    private void setVsComputer(boolean v) {
        if (vsComputer == v) return;
        vsComputer = v;
        miPVC.setSelected(v);
        miPVP.setSelected(!v);
        newGame();
    }

    private void setHumanSide(int s) {
        if (humanSide == s) return;
        humanSide = s;
        miRed.setSelected(s == Board.RED);
        miBlack.setSelected(s == Board.BLACK);
        newGame();
    }

    private void setLevel(int l) {
        level = l;
        miL1.setSelected(l == 1);
        miL2.setSelected(l == 2);
        miL3.setSelected(l == 3);
        statusBar.setText("电脑难度已设为：" + (l == 1 ? "入门" : l == 2 ? "普通" : "高手"));
    }

    // ------------------------------------------------------------------ 刷新

    private void refresh() {
        String sideName = board.side == Board.RED ? "红方" : "黑方";
        if (gameOver) {
            turnLabel.setText("对局结束");
            turnLabel.setForeground(MUTED);
        } else if (thinking) {
            turnLabel.setText(sideName + " · 思考中");
            turnLabel.setForeground(GOLD);
        } else {
            turnLabel.setText(sideName + " 行棋");
            turnLabel.setForeground(board.side == Board.RED ? new Color(232, 108, 88) : new Color(206, 200, 186));
        }
        subLabel.setText("第 " + (history.size() / 2 + 1) + " 回合 · 共 "
                + history.size() + " 步 · " + (vsComputer ? "人机" : "双人"));
        boolean chk = !gameOver && board.inCheck(board.side);
        checkLabel.setText(chk ? "⚠ 将军！必须应将" : (gameOver ? "点击「新的一局」再来一盘" : "局势正常"));
        checkLabel.setForeground(chk ? DANGER : MUTED);
        if (!chk && !gameOver) boardPanel.setCheckedKing(-1);

        btnUndo.setEnabled(!history.isEmpty());
        btnHint.setEnabled(humanTurn());
        btnResign.setEnabled(!gameOver);

        StringBuilder redLost = new StringBuilder("红方损失："), blackLost = new StringBuilder("黑方损失：");
        int rc = 0, bc = 0;
        for (Step s : history) {
            if (s.cap == 0) continue;
            if (s.cap > 0) { redLost.append(Board.RED_NAME[Board.type(s.cap)]).append(' '); rc++; }
            else { blackLost.append(Board.BLACK_NAME[Board.type(s.cap)]).append(' '); bc++; }
        }
        if (rc == 0) redLost.append("无");
        if (bc == 0) blackLost.append("无");
        lostLabel.setText("<html><div style='line-height:165%'>" + redLost + "<br>" + blackLost + "</div></html>");
        updateRecord();
    }

    private void updateRecord() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < history.size(); i += 2) {
            sb.append(String.format("%3d. ", i / 2 + 1));
            Step red = history.get(i);
            sb.append(pad(red.text));
            if (i + 1 < history.size()) sb.append(pad(history.get(i + 1).text));
            sb.append('\n');
        }
        if (history.isEmpty()) sb.append("　（尚无记录）\n");
        recordArea.setText(sb.toString());
        recordArea.setCaretPosition(0);
    }

    private static String pad(String s) {
        StringBuilder b = new StringBuilder(s);
        while (b.length() < 8) b.append('　');
        return b.toString();
    }

    private void exportRecord() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("导出棋谱");
        fc.setSelectedFile(new File("中国象棋棋谱.txt"));
        fc.setFileFilter(new FileNameExtensionFilter("文本文件 (*.txt)", "txt"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File f = fc.getSelectedFile();
        if (!f.getName().toLowerCase().endsWith(".txt")) f = new File(f.getAbsolutePath() + ".txt");
        StringBuilder sb = new StringBuilder();
        sb.append("中国象棋 · 对局棋谱\n");
        sb.append("模式：").append(vsComputer ? "人机对战（难度" + level + "）" : "双人对弈").append('\n');
        sb.append("步数：").append(history.size()).append('\n');
        sb.append("--------------------------------\n").append(recordArea.getText());
        sb.append("--------------------------------\n\n").append(board);
        try {
            Files.write(f.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
            statusBar.setText("棋谱已导出：" + f.getAbsolutePath());
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "导出失败：" + ex.getMessage(),
                    "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void showRules() {
        String text = "【棋盘与棋子】\n"
                + "棋盘 9 列 × 10 行，中间为楚河汉界。红方在下、先行；黑方在上。每方 16 子。\n\n"
                + "【走法】\n"
                + "· 车：沿横线或竖线任意步，不能越子。\n"
                + "· 炮：不吃子时同车；吃子时必须隔且仅隔一个棋子（炮架）。\n"
                + "· 马：走「日」字；紧邻的直线格有子时不能往该方向跳（蹩马腿）。\n"
                + "· 象/相：走「田」字，不可过河；田字中心有子则不能走（塞象眼）。\n"
                + "· 士/仕：九宫内斜走一步。\n"
                + "· 将/帅：九宫内直走一步；两将不可在同一列直接照面（白脸将）。\n"
                + "· 兵/卒：过河前只能直进一步；过河后可直进或横走一步，永不后退。\n\n"
                + "【胜负】\n"
                + "将死对方将（帅）或使对方无子可动（困毙）即胜；"
                + "同一局面出现三次判和，单方连续长将判负；"
                + "连续 60 回合无吃子判和。走子后己方被将军、或走成将帅对面均属禁手。\n\n"
                + "【防循环】\n"
                + "程序会记录每一步的局面指纹。局面重复两次时状态栏会提示；"
                + "第三次重复自动判和，若循环由单方连续将军造成则判该方负。"
                + "电脑在搜索时同样会回避重复局面与长将，不会用同一招反复兜圈。\n\n"
                + "【操作】\n"
                + "点击己方棋子选中，绿色圆点是可走位置，红圈是可吃子；再点目标格落子。"
                + "右键取消选中。菜单「对局」可切换人机 / 双人、执子与难度。";
        JTextArea ta = new JTextArea(text, 18, 46);
        ta.setEditable(false);
        ta.setLineWrap(true);
        ta.setWrapStyleWord(true);
        ta.setFont(BoardPanel.uiFont(Font.PLAIN, 13));
        ta.setCaretPosition(0);
        JOptionPane.showMessageDialog(this, new JScrollPane(ta), "规则速览",
                JOptionPane.INFORMATION_MESSAGE);
    }

    private void showKeys() {
        JOptionPane.showMessageDialog(this,
                "Ctrl + N　新的一局\nCtrl + Z　悔棋\nH　支招\n"
                        + "Ctrl + S　导出棋谱\nF1　规则速览\n右键棋盘　取消选中",
                "快捷键", JOptionPane.INFORMATION_MESSAGE);
    }

    private void showAbout() {
        JOptionPane.showMessageDialog(this,
                "<html><div style='text-align:center;font-size:13px'>"
                        + "<b style='font-size:16px'>中国象棋 · Xiangqi</b><br><br>"
                        + "版本 1.0.0　|　Java Swing 单机版<br><br>"
                        + "完整规则校验 · 将死 / 困毙判定<br>"
                        + "Alpha-Beta 电脑对手（三档难度）<br>"
                        + "悔棋 · 中文棋谱 · 落子音效 · 棋谱导出<br><br>"
                        + "无需联网，无需安装 Java 运行环境"
                        + "</div></html>",
                "关于", JOptionPane.INFORMATION_MESSAGE,
                new javax.swing.ImageIcon(IconFactory.render(64)));
    }
}
