import chess.Board;

/** 规则引擎自检：perft 着法数 + 关键规则断言。仅用于开发验证，不打进交付包。 */
public class SelfTest {

    private static int failed = 0;

    private static void check(String name, long got, long expect) {
        boolean ok = got == expect;
        if (!ok) failed++;
        System.out.printf("%-26s %s  got=%d expect=%d%n", name, ok ? "PASS" : "FAIL", got, expect);
    }

    private static long perft(Board b, int depth) {
        Board.MoveList ml = new Board.MoveList();
        b.genLegal(b.side, ml);
        if (depth == 1) return ml.n;
        long n = 0;
        for (int i = 0; i < ml.n; i++) {
            int m = ml.a[i];
            int cap = b.applyRaw(m);
            b.side = -b.side;
            int q = b.quietPly;
            b.quietPly = cap == 0 ? q + 1 : 0;
            n += perft(b, depth - 1);
            b.quietPly = q;
            b.side = -b.side;
            b.undoRaw(m, cap);
        }
        return n;
    }

    private static void clear(Board b) {
        for (int r = 0; r < 10; r++) for (int c = 0; c < 9; c++) b.sq[r][c] = 0;
        b.side = Board.RED;
        b.quietPly = 0;
    }

    private static int fromCount(Board b, int from) {
        Board.MoveList ml = new Board.MoveList();
        b.genFrom(from, ml);
        return ml.n;
    }

    public static void main(String[] args) {
        Board b = new Board();
        System.out.println(b);
        long t0 = System.currentTimeMillis();
        check("perft(1) 初始局面", perft(b, 1), 44);
        check("perft(2) 初始局面", perft(b, 2), 1920);
        long t1 = System.currentTimeMillis();
        check("perft(3) 初始局面", perft(b, 3), 79666);
        System.out.println("perft(1..2) " + (t1 - t0) + " ms, perft(3) " + (System.currentTimeMillis() - t1) + " ms");

        Board t = new Board();
        clear(t);
        t.sq[5][4] = Board.KNIGHT;
        check("马·空盘 8 着", fromCount(t, 5 * 9 + 4), 8);
        t.sq[4][4] = Board.PAWN;
        check("马·蹩腿少 2 着", fromCount(t, 5 * 9 + 4), 6);

        clear(t);
        t.sq[9][2] = Board.ELEPHANT;
        check("相·不过河 2 着", fromCount(t, 9 * 9 + 2), 2);
        t.sq[8][3] = Board.PAWN;
        check("相·塞象眼少 1 着", fromCount(t, 9 * 9 + 2), 1);

        clear(t);
        t.sq[7][1] = Board.CANNON;
        check("炮·空盘 17 着", fromCount(t, 7 * 9 + 1), 17);
        t.sq[5][1] = Board.PAWN;
        check("炮·隔己方子不吃", fromCount(t, 7 * 9 + 1), 11);
        t.sq[3][1] = -Board.ROOK;
        check("炮·翻山吃车", fromCount(t, 7 * 9 + 1), 12);

        clear(t);
        t.sq[6][4] = Board.PAWN;
        check("兵·未过河 1 着", fromCount(t, 6 * 9 + 4), 1);
        clear(t);
        t.sq[4][4] = Board.PAWN;
        check("兵·过河 3 着", fromCount(t, 4 * 9 + 4), 3);

        clear(t);
        t.sq[9][4] = Board.ADVISOR;
        check("仕·九宫斜走 2 着", fromCount(t, 9 * 9 + 4), 2);

        clear(t);
        t.sq[9][4] = Board.KING;
        t.sq[0][3] = -Board.KING;
        Board.MoveList ml = new Board.MoveList();
        t.genLegal(Board.RED, ml);
        boolean facing = false;
        for (int i = 0; i < ml.n; i++)
            if (ml.a[i] == Board.move(9 * 9 + 4, 9 * 9 + 3)) facing = true;
        check("禁止走成将帅对面", facing ? 1 : 0, 0);

        clear(t);
        t.sq[9][4] = Board.KING;
        t.sq[0][3] = -Board.KING;
        t.sq[5][4] = -Board.ROOK;
        t.genLegal(Board.RED, ml);
        check("被将军时只 1 个应手", ml.n, 1);

        System.out.println(failed == 0 ? "=== ALL TESTS PASSED ===" : "=== " + failed + " TEST(S) FAILED ===");
        if (failed != 0) System.exit(1);
    }
}
