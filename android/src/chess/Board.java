package chess;

/**
 * 中国象棋规则引擎。
 *
 * 棋盘坐标：行 r = 0..9（0 为黑方底线，9 为红方底线），列 c = 0..8（红方视角从左至右）。
 * 棋子用整数表示：正数红方，负数黑方，绝对值 1..7 分别为 车 马 象 士 将 炮 兵。
 * 着法用一个 int 编码：(from << 8) | to，其中 from / to 均为 r*9+c。
 */
public final class Board {

    public static final int ROWS = 10, COLS = 9;
    public static final int ROOK = 1, KNIGHT = 2, ELEPHANT = 3,
            ADVISOR = 4, KING = 5, CANNON = 6, PAWN = 7;
    public static final int RED = 1, BLACK = -1;

    /** 结果码 */
    public static final int ONGOING = 0, CHECKMATE = 1, STALEMATE = 2, DRAW_LIMIT = 3;

    public static final String[] RED_NAME = {"", "车", "马", "相", "仕", "帅", "炮", "兵"};
    public static final String[] BLACK_NAME = {"", "车", "马", "象", "士", "将", "炮", "卒"};
    private static final String[] CN = {"", "一", "二", "三", "四", "五", "六", "七", "八", "九"};

    private static final int[][] ORTHO = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
    private static final int[][] DIAG = {{-1, -1}, {-1, 1}, {1, -1}, {1, 1}};
    private static final int[][] HORSE = {
            {-2, -1}, {-2, 1}, {2, -1}, {2, 1}, {-1, -2}, {-1, 2}, {1, -2}, {1, 2}
    };

    /** 循环裁决结果：0 未触发 / 1 红方长将负 / 2 黑方长将负 / 3 重复局面和棋 */
    public static final int LOOP_NONE = 0, LOOP_RED_PERPETUAL = 1,
            LOOP_BLACK_PERPETUAL = 2, LOOP_DRAW = 3;

    // Zobrist 局面指纹：走子、悔棋时增量异或，O(1) 得到当前局面哈希
    private static final long[][] ZOBRIST = new long[15][ROWS * COLS];
    private static final long Z_SIDE;
    static {
        java.util.Random r = new java.util.Random(0x58514A32303236L);
        for (int p = 0; p < ZOBRIST.length; p++)
            for (int i = 0; i < ROWS * COLS; i++) ZOBRIST[p][i] = r.nextLong();
        Z_SIDE = r.nextLong();
    }
    private static long key(int piece, int square) { return ZOBRIST[piece + 7][square]; }

    public final int[][] sq = new int[ROWS][COLS];
    public int side = RED;
    /** 连续未吃子的步数，用于自然限着判和 */
    public int quietPly = 0;
    /** 当前局面指纹，随 applyRaw / undoRaw 增量更新 */
    public long zobrist;

    public Board() { reset(); }

    public final void reset() {
        for (int r = 0; r < ROWS; r++)
            for (int c = 0; c < COLS; c++) sq[r][c] = 0;
        int[] back = {ROOK, KNIGHT, ELEPHANT, ADVISOR, KING, ADVISOR, ELEPHANT, KNIGHT, ROOK};
        for (int c = 0; c < COLS; c++) {
            sq[0][c] = -back[c];
            sq[9][c] = back[c];
        }
        sq[2][1] = -CANNON;
        sq[2][7] = -CANNON;
        sq[7][1] = CANNON;
        sq[7][7] = CANNON;
        for (int c = 0; c < COLS; c += 2) {
            sq[3][c] = -PAWN;
            sq[6][c] = PAWN;
        }
        side = RED;
        quietPly = 0;
        zobrist = recomputeHash();
    }

    @Override
    public Board clone() {
        Board b = new Board();
        for (int r = 0; r < ROWS; r++) System.arraycopy(sq[r], 0, b.sq[r], 0, COLS);
        b.side = side;
        b.quietPly = quietPly;
        b.zobrist = zobrist;
        return b;
    }

    public static int move(int from, int to) { return (from << 8) | to; }
    public static int fromOf(int m) { return (m >>> 8) & 0xFF; }
    public static int toOf(int m) { return m & 0xFF; }
    public static int sign(int p) { return p > 0 ? RED : BLACK; }
    public static int type(int p) { return p > 0 ? p : -p; }
    public static boolean inBoard(int r, int c) { return r >= 0 && r < ROWS && c >= 0 && c < COLS; }
    public static boolean inPalace(int r, int c, int s) {
        if (c < 3 || c > 5) return false;
        return s == RED ? (r >= 7 && r <= 9) : (r >= 0 && r <= 2);
    }

    /** 着法容器，避免搜索过程中的对象分配 */
    public static final class MoveList {
        public final int[] a = new int[160];
        public int n = 0;
        public void clear() { n = 0; }
        public void add(int m) { if (n < a.length) a[n++] = m; }
    }

    private void tryAdd(int from, int r, int c, int s, MoveList out) {
        if (!inBoard(r, c)) return;
        int q = sq[r][c];
        if (q == 0 || sign(q) != s) out.add(move(from, r * COLS + c));
    }

    /** 车 / 炮 的滑动着法 */
    private void slide(int from, int r, int c, int s, boolean cannon, MoveList out) {
        for (int[] d : ORTHO) {
            int rr = r + d[0], cc = c + d[1];
            boolean jumped = false;
            while (inBoard(rr, cc)) {
                int q = sq[rr][cc];
                int to = rr * COLS + cc;
                if (!cannon) {
                    if (q == 0) out.add(move(from, to));
                    else {
                        if (sign(q) != s) out.add(move(from, to));
                        break;
                    }
                } else if (!jumped) {
                    if (q == 0) out.add(move(from, to));
                    else jumped = true;
                } else if (q != 0) {
                    if (sign(q) != s) out.add(move(from, to));
                    break;
                }
                rr += d[0];
                cc += d[1];
            }
        }
    }

    /** 生成某一个格子上棋子的所有「伪合法」着法（不检查己方是否被将军） */
    public void genFrom(int from, MoveList out) {
        int r = from / COLS, c = from % COLS;
        int p = sq[r][c];
        if (p == 0) return;
        int s = sign(p), t = type(p);
        switch (t) {
            case ROOK:
                slide(from, r, c, s, false, out);
                break;
            case CANNON:
                slide(from, r, c, s, true, out);
                break;
            case KNIGHT:
                for (int[] d : HORSE) {
                    int nr = r + d[0], nc = c + d[1];
                    if (!inBoard(nr, nc)) continue;
                    // 蹩马腿
                    int lr = r + (Math.abs(d[0]) == 2 ? d[0] / 2 : 0);
                    int lc = c + (Math.abs(d[1]) == 2 ? d[1] / 2 : 0);
                    if (sq[lr][lc] != 0) continue;
                    tryAdd(from, nr, nc, s, out);
                }
                break;
            case ELEPHANT:
                for (int[] d : DIAG) {
                    int nr = r + d[0] * 2, nc = c + d[1] * 2;
                    if (!inBoard(nr, nc)) continue;
                    // 象 / 相 不可过河
                    if (s == RED ? nr < 5 : nr > 4) continue;
                    // 塞象眼
                    if (sq[r + d[0]][c + d[1]] != 0) continue;
                    tryAdd(from, nr, nc, s, out);
                }
                break;
            case ADVISOR:
                for (int[] d : DIAG) {
                    int nr = r + d[0], nc = c + d[1];
                    if (inPalace(nr, nc, s)) tryAdd(from, nr, nc, s, out);
                }
                break;
            case KING:
                for (int[] d : ORTHO) {
                    int nr = r + d[0], nc = c + d[1];
                    if (inPalace(nr, nc, s)) tryAdd(from, nr, nc, s, out);
                }
                // 将帅对面（白脸将）：同列且中间无子时视为可直接吃掉对方将，
                // 于是「走成将帅对面」会因为自陷被将而被 genLegal 自动禁止。
                for (int dir = -1; dir <= 1; dir += 2) {
                    for (int rr = r + dir; rr >= 0 && rr < ROWS; rr += dir) {
                        int q = sq[rr][c];
                        if (q != 0) {
                            if (type(q) == KING && sign(q) != s)
                                out.add(move(from, rr * COLS + c));
                            break;
                        }
                    }
                }
                break;
            case PAWN:
                int dr = (s == RED) ? -1 : 1;
                tryAdd(from, r + dr, c, s, out);
                boolean crossed = (s == RED) ? (r <= 4) : (r >= 5);
                if (crossed) {
                    tryAdd(from, r, c - 1, s, out);
                    tryAdd(from, r, c + 1, s, out);
                }
                break;
            default:
                break;
        }
    }

    /** 生成某方全部伪合法着法 */
    public void genPseudo(int s, MoveList out) {
        out.clear();
        for (int i = 0; i < ROWS * COLS; i++) {
            int p = sq[i / COLS][i % COLS];
            if (p != 0 && sign(p) == s) genFrom(i, out);
        }
    }

    /** target 格是否受 bySide 攻击 */
    public boolean isAttacked(int target, int bySide) {
        MoveList tmp = new MoveList();
        for (int i = 0; i < ROWS * COLS; i++) {
            int p = sq[i / COLS][i % COLS];
            if (p == 0 || sign(p) != bySide) continue;
            tmp.clear();
            genFrom(i, tmp);
            for (int k = 0; k < tmp.n; k++)
                if (toOf(tmp.a[k]) == target) return true;
        }
        return false;
    }

    public int kingSquare(int s) {
        int k = s == RED ? KING : -KING;
        for (int i = 0; i < ROWS * COLS; i++)
            if (sq[i / COLS][i % COLS] == k) return i;
        return -1;
    }

    public boolean inCheck(int s) {
        int k = kingSquare(s);
        return k < 0 || isAttacked(k, -s);
    }

    /** 生成全部合法着法（已排除送将 / 将帅对面） */
    public void genLegal(int s, MoveList out) {
        MoveList pseudo = new MoveList();
        genPseudo(s, pseudo);
        out.clear();
        for (int i = 0; i < pseudo.n; i++) {
            int m = pseudo.a[i];
            int cap = applyRaw(m);
            boolean bad = inCheck(s);
            undoRaw(m, cap);
            if (!bad) out.add(m);
        }
    }

    /** 仅搬动棋子，不切换行棋方；返回被吃的棋子 */
    public int applyRaw(int m) {
        int from = fromOf(m), to = toOf(m);
        int p = sq[from / COLS][from % COLS];
        int cap = sq[to / COLS][to % COLS];
        zobrist ^= key(p, from) ^ key(p, to)
                ^ (cap != 0 ? key(cap, to) : 0L) ^ Z_SIDE;
        sq[to / COLS][to % COLS] = p;
        sq[from / COLS][from % COLS] = 0;
        return cap;
    }

    public void undoRaw(int m, int cap) {
        int from = fromOf(m), to = toOf(m);
        int p = sq[to / COLS][to % COLS];
        sq[from / COLS][from % COLS] = p;
        sq[to / COLS][to % COLS] = cap;
        // 异或自反，把指纹还原到走子之前
        zobrist ^= key(p, from) ^ key(p, to)
                ^ (cap != 0 ? key(cap, to) : 0L) ^ Z_SIDE;
    }

    /** 当前局面指纹（用于重复局面检测） */
    public long hash() { return zobrist; }

    /** 全量重算指纹，用于自检与 reset */
    public long recomputeHash() {
        long h = 0;
        for (int i = 0; i < ROWS * COLS; i++) {
            int p = sq[i / COLS][i % COLS];
            if (p != 0) h ^= key(p, i);
        }
        if (side == BLACK) h ^= Z_SIDE;
        return h;
    }

    /**
     * 循环裁决：三次重复局面判和；单方长将（连续将军构成循环）判该方负。
     *
     * @param pos      局面序列，pos[0] 为初始局面，pos[i+1] 为第 i 步之后的局面
     * @param gives    每步走完后是否给对方造成将军
     * @param sides    每步的行棋方
     * @param captures 每步是否吃子
     */
    public static int judgeLoop(long[] pos, boolean[] gives, int[] sides, boolean[] captures) {
        if (pos == null || pos.length < 2) return LOOP_NONE;
        int n = gives.length;
        long cur = pos[pos.length - 1];
        int first = -1, count = 0;
        for (int i = 0; i < pos.length; i++) {
            if (pos[i] == cur) {
                if (first < 0) first = i;
                count++;
            }
        }
        if (count >= 3) {
            int red = 0, black = 0;
            for (int i = Math.max(0, first); i < n; i++) {
                if (!gives[i]) continue;
                if (sides[i] == RED) red++; else black++;
            }
            if (red > 0 && black == 0) return LOOP_RED_PERPETUAL;
            if (black > 0 && red == 0) return LOOP_BLACK_PERPETUAL;
            return LOOP_DRAW;
        }
        // 兜底：连续 8 步由同一方将军且无吃子，视为长将，不必等三次重复
        if (n >= 8) {
            int side = sides[n - 1];
            boolean all = true;
            for (int i = n - 8; i < n; i++) {
                if (captures[i] || !gives[i] || sides[i] != side) { all = false; break; }
            }
            if (all) return side == RED ? LOOP_RED_PERPETUAL : LOOP_BLACK_PERPETUAL;
        }
        return LOOP_NONE;
    }

    /**
     * 当前行棋方的局面结果。
     * ONGOING 继续；CHECKMATE / STALEMATE 当前行棋方负；DRAW_LIMIT 和棋。
     */
    public int result() {
        MoveList l = new MoveList();
        genLegal(side, l);
        if (l.n == 0) return inCheck(side) ? CHECKMATE : STALEMATE;
        if (quietPly >= 120) return DRAW_LIMIT;
        return ONGOING;
    }

    /** 中文棋谱记法，如「炮二平五」「马8进7」，须在走子之前调用 */
    public String toChinese(int m) {
        int from = fromOf(m), to = toOf(m);
        int r = from / COLS, c = from % COLS;
        int tr = to / COLS, tc = to % COLS;
        int p = sq[r][c];
        if (p == 0) return "??";
        int s = sign(p), t = type(p);
        String name = (s == RED ? RED_NAME : BLACK_NAME)[t];
        String fileNum = (s == RED) ? CN[COLS - c] : String.valueOf(c + 1);
        String destNum = (s == RED) ? CN[COLS - tc] : String.valueOf(tc + 1);

        // 同一纵线上有多个同类棋子时，用「前/后」或序号区分
        int total = 0, order = 0;
        for (int rr = 0; rr < ROWS; rr++) {
            if (sq[rr][c] == p) {
                boolean before = (s == RED) ? (rr < r) : (rr > r);
                if (before) order++;
                total++;
            }
        }
        String head;
        if (total == 1) {
            head = name + fileNum;
        } else if (total == 2) {
            head = (order == 0 ? "前" : "后") + name;
        } else {
            head = (s == RED ? CN[Math.min(9, order + 1)] : String.valueOf(order + 1)) + name;
        }

        String action, tail;
        if (tr == r) {
            action = "平";
            tail = destNum;
        } else {
            boolean forward = (s == RED) ? (tr < r) : (tr > r);
            action = forward ? "进" : "退";
            if (t == KNIGHT || t == ELEPHANT || t == ADVISOR) {
                tail = destNum;
            } else {
                int steps = Math.min(9, Math.abs(tr - r));
                tail = (s == RED) ? CN[steps] : String.valueOf(steps);
            }
        }
        return head + action + tail;
    }

    /** 便于调试的字符串棋盘 */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                int p = sq[r][c];
                sb.append(p == 0 ? "．" : (p > 0 ? RED_NAME[type(p)] : BLACK_NAME[type(p)]));
            }
            sb.append('\n');
        }
        return sb.toString();
    }
}
