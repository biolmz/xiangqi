package chess;

import java.util.HashMap;
import java.util.Random;

/**
 * 电脑棋手：Negamax + Alpha-Beta 剪枝 + 迭代加深 + 时间上限 + 循环规避。
 *
 * 循环规避分三层：
 * 1) 真实对局中出现过的局面会传入搜索，搜索路径里再走回这些局面一律按「和棋」处理，
 *    不再继续沿循环往下算，避免反复用同一招将军刷分；
 * 2) 若重复是由某一方连续长将造成的，按规则给长将方判负分，电脑绝不会主动长将；
 * 3) 连续将军、以及把棋子原路退回上一手位置的着法会被降权，从源头减少兜圈。
 */
public final class AI {

    private static final int INF = 1_000_000;
    private static final int MATE = 900_000;
    private static final int[] VALUE = {0, 900, 400, 200, 200, 100_000, 450, 100};

    private static final class Timeout extends RuntimeException {
        Timeout() { super(null, null, false, false); }
    }

    private Board b;
    private long deadline;
    private long nodes;
    private int level;
    private int moveNumber;
    private int prevFrom = -1, prevTo = -1;
    private final HashMap<Long, Integer> seen = new HashMap<>();
    private final Random rnd = new Random();

    /** 兼容调用：无历史局面信息 */
    public int think(Board board, int level, int moveNumber) {
        return think(board, level, moveNumber, new long[]{board.hash()}, -1, -1);
    }

    /**
     * @param board          当前局面（不会被修改）
     * @param level          1 入门 / 2 普通 / 3 高手
     * @param moveNumber     已走的步数，用于开局变化
     * @param historyHashes  本局从初始局面到当前的所有局面指纹（含当前局面）
     * @param prevFrom       本方上一手的起点，-1 表示没有
     * @param prevTo         本方上一手的终点
     * @return 最佳着法，无着可走时返回 0
     */
    public int think(Board board, int level, int moveNumber,
                     long[] historyHashes, int prevFrom, int prevTo) {
        this.b = board.clone();
        this.level = level;
        this.moveNumber = moveNumber;
        this.prevFrom = prevFrom;
        this.prevTo = prevTo;
        this.nodes = 0;

        seen.clear();
        if (historyHashes != null)
            for (long h : historyHashes) {
                Integer v = seen.get(h);
                seen.put(h, v == null ? 1 : v + 1);
            }

        Board.MoveList root = new Board.MoveList();
        b.genLegal(b.side, root);
        if (root.n == 0) return 0;
        if (root.n == 1) return root.a[0];

        int maxDepth = level <= 1 ? 2 : (level == 2 ? 3 : 4);
        long budgetMs = level <= 1 ? 800L : (level == 2 ? 2500L : 7000L);
        deadline = System.nanoTime() + budgetMs * 1_000_000L;

        int best = root.a[0];
        for (int d = 1; d <= maxDepth; d++) {
            try {
                best = searchRoot(root, d, best);
            } catch (Timeout ignored) {
                break;
            }
            if (System.nanoTime() > deadline) break;
        }
        return best;
    }

    public long nodes() { return nodes; }

    private void push(long h) {
        Integer v = seen.get(h);
        seen.put(h, v == null ? 1 : v + 1);
    }

    private void pop(long h) {
        Integer v = seen.get(h);
        if (v == null) return;
        if (v <= 1) seen.remove(h); else seen.put(h, v - 1);
    }

    private int searchRoot(Board.MoveList root, int depth, int prevBest) {
        order(root, prevBest);
        int alpha = -INF;
        int best = root.a[0];
        int bestScore = -INF;
        int[] scores = new int[root.n];

        for (int i = 0; i < root.n; i++) {
            int m = root.a[i];
            int mover = b.side;
            int cap = b.applyRaw(m);
            b.side = -b.side;
            int oldQ = b.quietPly;
            b.quietPly = (cap == 0) ? oldQ + 1 : 0;

            boolean gives = b.inCheck(b.side);
            int chainSide = gives ? mover : 0;
            int chainLen = gives ? 1 : 0;

            push(b.zobrist);
            int v;
            if (Board.type(cap) == Board.KING) v = MATE - 1;
            else v = -search(depth - 1, -INF, -alpha, 1, chainSide, chainLen);
            pop(b.zobrist);

            b.quietPly = oldQ;
            b.side = -b.side;
            b.undoRaw(m, cap);

            // 原路退回上一手的位置，通常是兜圈的开始，轻微降权
            if (prevFrom >= 0 && Board.fromOf(m) == prevTo && Board.toOf(m) == prevFrom)
                v -= 45;

            scores[i] = v;
            if (v > bestScore) { bestScore = v; best = m; }
            if (v > alpha) alpha = v;
        }

        // 开局阶段在接近最优的着法里随机挑选，避免每盘棋一模一样
        int tolerance = level <= 1 ? 70 : (level == 2 ? 25 : 0);
        if (moveNumber < 10 && tolerance > 0) {
            int count = 0;
            int[] pool = new int[root.n];
            for (int i = 0; i < root.n; i++)
                if (scores[i] >= bestScore - tolerance) pool[count++] = root.a[i];
            if (count > 1) best = pool[rnd.nextInt(count)];
        }
        return best;
    }

    /**
     * @param chainSide 到目前为止连续将军的一方，0 表示当前没有连续将军
     * @param chainLen  连续将军的步数
     */
    private int search(int depth, int alpha, int beta, int ply, int chainSide, int chainLen) {
        if ((nodes++ & 511) == 0 && System.nanoTime() > deadline) throw new Timeout();
        if (b.quietPly >= 120) return 0;

        // 局面重复：不再沿循环深入。长将方按规则判负，其余按和棋处理。
        Integer repCount = seen.get(b.zobrist);
        if (ply > 0 && b.quietPly >= 4 && repCount != null && repCount >= 2) {
            if (chainLen >= 4)
                return (chainSide == b.side) ? -MATE + ply : MATE - ply;
            return 0;
        }

        Board.MoveList ml = new Board.MoveList();
        b.genLegal(b.side, ml);
        if (ml.n == 0) return -MATE + ply;   // 将死 / 困毙，当前行棋方负

        if (depth <= 0) {
            int e = evaluate();
            if (chainLen >= 3) e += (chainSide == Board.RED ? -30 : 30); // 连续长将软惩罚
            return b.side == Board.RED ? e : -e;
        }

        order(ml, 0);
        int best = -INF;
        for (int i = 0; i < ml.n; i++) {
            int m = ml.a[i];
            int mover = b.side;
            int cap = b.applyRaw(m);
            b.side = -b.side;
            int oldQ = b.quietPly;
            b.quietPly = (cap == 0) ? oldQ + 1 : 0;

            boolean gives = b.inCheck(b.side);
            int nSide = gives ? mover : 0;
            int nLen = gives ? (chainSide == mover ? chainLen + 1 : 1) : 0;

            push(b.zobrist);
            int v;
            if (Board.type(cap) == Board.KING) v = MATE - ply - 1;
            else v = -search(depth - 1, -beta, -alpha, ply + 1, nSide, nLen);
            pop(b.zobrist);

            b.quietPly = oldQ;
            b.side = -b.side;
            b.undoRaw(m, cap);

            if (v > best) best = v;
            if (v > alpha) alpha = v;
            if (alpha >= beta) break;
        }
        return best;
    }

    /** MVV-LVA 着法排序：吃大子、用小子吃优先，另有上一轮最佳着法加权 */
    private void order(Board.MoveList ml, int hint) {
        int[] sc = new int[ml.n];
        for (int i = 0; i < ml.n; i++) {
            int m = ml.a[i];
            if (m == hint) { sc[i] = 1_000_000; continue; }
            int cap = b.sq[Board.toOf(m) / 9][Board.toOf(m) % 9];
            int mover = b.sq[Board.fromOf(m) / 9][Board.fromOf(m) % 9];
            sc[i] = (cap != 0)
                    ? 10_000 + VALUE[Board.type(cap)] * 8 - VALUE[Board.type(mover)]
                    : 0;
        }
        for (int i = 1; i < ml.n; i++) {
            int tm = ml.a[i], ts = sc[i], j = i - 1;
            while (j >= 0 && sc[j] < ts) {
                ml.a[j + 1] = ml.a[j];
                sc[j + 1] = sc[j];
                j--;
            }
            ml.a[j + 1] = tm;
            sc[j + 1] = ts;
        }
    }

    /** 静态局面评估，始终从红方视角给分 */
    private int evaluate() {
        int score = 0;
        for (int i = 0; i < 90; i++) {
            int p = b.sq[i / 9][i % 9];
            if (p == 0) continue;
            int s = Board.sign(p), t = Board.type(p);
            int r = i / 9, c = i % 9;
            int adv = (s == Board.RED) ? (9 - r) : r; // 0 己方底线 → 9 对方底线
            int v = VALUE[t];
            switch (t) {
                case Board.PAWN:
                    v += adv * 3;
                    if (adv >= 5) v += 30 + (adv - 5) * 14;   // 过河兵大幅增值
                    if (c >= 2 && c <= 6) v += 8;
                    break;
                case Board.KNIGHT:
                    v += (4 - Math.abs(4 - c)) * 7 + adv * 3;
                    if (c == 0 || c == 8) v -= 10;
                    break;
                case Board.ROOK:
                    v += adv * 3 + (c == 4 ? 8 : 0);
                    break;
                case Board.CANNON:
                    v += (c == 4 ? 14 : 0) + adv * 2;
                    break;
                default:
                    break;
            }
            score += (s == Board.RED) ? v : -v;
        }
        return score + (b.side == Board.RED ? 4 : -4);
    }
}
