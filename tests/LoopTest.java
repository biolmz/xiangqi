import chess.AI;
import chess.Board;

import java.util.ArrayList;
import java.util.Random;

/** 循环规避自检：Zobrist 指纹、三次重复 / 长将裁决、真实棋盘长将序列、AI 集成。 */
public class LoopTest {

    private static int failed = 0;

    private static void check(String name, long got, long expect) {
        boolean ok = got == expect;
        if (!ok) failed++;
        System.out.printf("%-34s %s  got=%d expect=%d%n", name, ok ? "PASS" : "FAIL", got, expect);
    }

    private static void clear(Board b) {
        for (int r = 0; r < 10; r++) for (int c = 0; c < 9; c++) b.sq[r][c] = 0;
        b.side = Board.RED;
        b.quietPly = 0;
        b.zobrist = b.recomputeHash();
    }

    /** 1. 走子 / 悔棋过程中增量指纹始终等于全量重算 */
    private static void testZobrist() {
        Board b = new Board();
        long h0 = b.hash();
        check("初始指纹一致", b.hash(), b.recomputeHash());

        Random rnd = new Random(20261107L);
        ArrayList<Integer> moves = new ArrayList<>(), caps = new ArrayList<>(), quiet = new ArrayList<>();
        boolean ok = true;
        for (int step = 0; step < 200; step++) {
            Board.MoveList ml = new Board.MoveList();
            b.genLegal(b.side, ml);
            if (ml.n == 0) break;
            int m = ml.a[rnd.nextInt(ml.n)];
            // 不选择「吃掉对方将」的着法，保持局面可继续推演
            int cap = b.sq[Board.toOf(m) / 9][Board.toOf(m) % 9];
            if (Board.type(cap) == Board.KING) continue;
            int applied = b.applyRaw(m);
            int q = b.quietPly;
            b.quietPly = applied == 0 ? q + 1 : 0;
            b.side = -b.side;
            moves.add(m); caps.add(applied); quiet.add(q);
            if (b.hash() != b.recomputeHash()) { ok = false; break; }
        }
        check("随机走子中指纹始终正确", ok ? 1 : 0, 1);

        for (int i = moves.size() - 1; i >= 0; i--) {
            b.side = -b.side;
            b.quietPly = quiet.get(i);
            b.undoRaw(moves.get(i), caps.get(i));
        }
        check("全部悔棋后回到初始指纹", b.hash(), h0);
        check("全部悔棋后重算一致", b.hash(), b.recomputeHash());
        System.out.println("  (随机推演 " + moves.size() + " 步)");
    }

    /** 2. 裁决函数的各类场景 */
    private static void testJudge() {
        long A = 11, B = 22, C = 33, D = 44;
        check("无重复不裁决",
                Board.judgeLoop(new long[]{A, B, C, D}, new boolean[]{false, false, false},
                        new int[]{1, -1, 1}, new boolean[]{false, false, false}),
                Board.LOOP_NONE);

        // pos: A B C A B C A -> 当前局面 A 出现 3 次；循环内只有红方将军
        boolean[] gives = {true, false, true, false, true, false};
        int[] sides = {1, -1, 1, -1, 1, -1};
        boolean[] caps = new boolean[6];
        check("红方长将判负",
                Board.judgeLoop(new long[]{A, B, C, A, B, C, A}, gives, sides, caps),
                Board.LOOP_RED_PERPETUAL);

        int[] sidesB = {-1, 1, -1, 1, -1, 1};
        check("黑方长将判负",
                Board.judgeLoop(new long[]{A, B, C, A, B, C, A}, gives, sidesB, caps),
                Board.LOOP_BLACK_PERPETUAL);

        boolean[] noCheck = new boolean[6];
        check("无人将军则判和",
                Board.judgeLoop(new long[]{A, B, C, A, B, C, A}, noCheck, sides, caps),
                Board.LOOP_DRAW);

        boolean[] allCheck = new boolean[8];
        int[] oneSide = new int[8];
        boolean[] noCap = new boolean[8];
        java.util.Arrays.fill(allCheck, true);
        java.util.Arrays.fill(oneSide, -1);
        check("连续 8 步同方将军提前判负",
                Board.judgeLoop(new long[]{A, B, C, D, B, C, D, B, C}, allCheck, oneSide, noCap),
                Board.LOOP_BLACK_PERPETUAL);
        System.out.println("  (含长将兜底分支)");
    }

    /** 3. 真实棋盘：红车用同一招反复照将，黑将左右躲避，必须被裁决终止 */
    private static int simulatePerpetual() {
        Board b = new Board();
        clear(b);
        b.sq[9][3] = Board.KING;     // 红帅
        b.sq[0][4] = -Board.KING;    // 黑将
        b.sq[5][0] = Board.ROOK;     // 红车，将在第 4 / 5 列之间来回照将
        b.side = Board.RED;
        b.zobrist = b.recomputeHash();

        ArrayList<Long> pos = new ArrayList<>();
        ArrayList<Boolean> gives = new ArrayList<>(), caps = new ArrayList<>();
        ArrayList<Integer> sides = new ArrayList<>();
        pos.add(b.hash());

        // 红车横移到黑将所在列照将 -> 黑将平移躲开，如此往复
        for (int i = 0; i < 24; i++) {
            boolean redTurn = b.side == Board.RED;
            int rookCol = -1, kingCol = -1;
            for (int c = 0; c < 9; c++) {
                if (b.sq[5][c] == Board.ROOK) rookCol = c;
                if (b.sq[0][c] == -Board.KING) kingCol = c;
            }
            if (rookCol < 0 || kingCol < 0) break;
            int from, to;
            if (redTurn) {
                from = 5 * 9 + rookCol;
                to = 5 * 9 + kingCol;
            } else {
                from = kingCol;
                to = (kingCol == 4) ? 5 : 4;
            }
            int m = Board.move(from, to);
            int cap = b.applyRaw(m);
            b.side = -b.side;
            b.quietPly = cap == 0 ? b.quietPly + 1 : 0;
            gives.add(b.inCheck(b.side));
            sides.add(-b.side);
            caps.add(cap != 0);
            pos.add(b.hash());

            long[] pa = new long[pos.size()];
            boolean[] ga = new boolean[gives.size()], ca = new boolean[caps.size()];
            int[] sa = new int[sides.size()];
            for (int k = 0; k < pa.length; k++) pa[k] = pos.get(k);
            for (int k = 0; k < ga.length; k++) {
                ga[k] = gives.get(k); ca[k] = caps.get(k); sa[k] = sides.get(k);
            }
            int verdict = Board.judgeLoop(pa, ga, sa, ca);
            if (verdict != Board.LOOP_NONE) {
                System.out.println("  真实长将在第 " + (i + 1) + " 步被裁决终止，result=" + verdict);
                return verdict;
            }
        }
        return Board.LOOP_NONE;
    }

    /** 4. AI 集成：带历史局面思考时仍返回合法着法，且不崩溃 */
    private static void testAI() {
        Board b = new Board();
        ArrayList<Long> pos = new ArrayList<>();
        pos.add(b.hash());
        AI ai = new AI();
        int okMoves = 0;
        for (int i = 0; i < 8; i++) {
            long[] h = new long[pos.size()];
            for (int k = 0; k < h.length; k++) h[k] = pos.get(k);
            int m = ai.think(b, 1, i, h, -1, -1);
            if (m == 0) break;
            Board.MoveList ml = new Board.MoveList();
            b.genLegal(b.side, ml);
            boolean legal = false;
            for (int k = 0; k < ml.n; k++) if (ml.a[k] == m) legal = true;
            if (!legal) break;
            okMoves++;
            int cap = b.applyRaw(m);
            b.side = -b.side;
            b.quietPly = cap == 0 ? b.quietPly + 1 : 0;
            pos.add(b.hash());
        }
        check("AI 自对弈 8 步均合法", okMoves, 8);
    }

    public static void main(String[] args) {
        testZobrist();
        testJudge();
        check("真实棋盘循环长将判红负", simulatePerpetual(), Board.LOOP_RED_PERPETUAL);
        testAI();
        System.out.println(failed == 0 ? "=== ALL LOOP TESTS PASSED ===" : "=== " + failed + " FAILED ===");
        if (failed != 0) System.exit(1);
    }
}
