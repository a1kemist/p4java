package com.perforce.p4java.diff;

import com.perforce.p4java.exception.FileEncoderException;
import com.perforce.p4java.impl.mapbased.rpc.sys.RpcPerforceFile;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.util.concurrent.atomic.AtomicInteger;

public class Diff {

    public void SetInput(RpcPerforceFile f1, Charset c1, RpcPerforceFile f2, Charset c2, DiffFlags flags) throws FileEncoderException, IOException {
        // diff -h does it by word; see DiffWithFlags below.

        spx = new Sequence(f1, c1, flags);
        this.flags = flags;
        spy = new Sequence(f2, c2, flags);

        diff = new DiffAnalyze(spx, spy, fastMaxD);
    }

    public void SetOutput(OutputStream o) {
        this.out = new PrintWriter(o);
        closeOut = true;
    }

    public void CloseOutput() throws IOException {
        if (closeOut) out.close();
        closeOut = false;
    }

    public void DiffWithFlags(DiffFlags flags) throws IOException, FileEncoderException {
        // allow 'c' and 'u' (as well as 'C' and 'U') to take a count.
        // if it breaks, it defaults to 0 (which is then treated as 3).

        switch (flags.type) {
            case Normal:
                DiffNorm();
                break;
            case Context:
                DiffContext(flags.contextCount);
                break;
            case Unified:
                DiffUnified(flags.contextCount);
                break;
            case Summary:
                DiffSummary();
                break;
            case HTML:
                DiffHTML();
                break;
            case Rcs:
                DiffRcs();
                break;
        }
    }

    public void DiffContext(int c) throws IOException, FileEncoderException {
        // s,t bound the diffs being displayed.
        // First we move t forward until the snake is bigger than
        // 2 * CONTEXT, then we display [s,t], then iterate for [t,...].

        // Remember: a snake is a matching chunk.  Diffs are inbetween
        // snakes, before the first snake, or after the last snake.

        if (c < 0) c = 3;
        Snake s = diff.GetSnake();
        Snake t;

        for (; (t = s.next) != null; s = t) {
            // Look for snake > 2 * CONTEXT

            while (t.next != null && t.x.get() + 2 * c >= t.u.get())
                t = t.next;

            // Compute first/last lines of diff block

            int sx = s.u.get() - c > 0 ? s.u.get() - c : 0;
            int sy = s.v.get() - c > 0 ? s.v.get() - c : 0;
            int ex = t.x.get() + c < spx.Lines() ? t.x.get() + c : spx.Lines();
            int ey = t.y.get() + c < spy.Lines() ? t.y.get() + c : spy.Lines();

            // Display [s,t]

            out.printf("***************%s", newLines);

            // File 1

            out.printf("*** %d,%d ****%s", sx + 1, ex, newLines);

            // Now walk the snake between s,t, displaying the diffs

            Snake ss, tt;

            for (ss = s; ss != t; ss = tt) {
                tt = ss.next;

                if (ss.u.get() < tt.x.get()) {
                    Walker("  ", spx, sx, ss.u.get());
                    String mark = ss.v.get() < tt.y.get() ? "! " : "- ";
                    Walker(mark, spx, ss.u.get(), tt.x.get());
                    sx = tt.x.get();
                }
            }

            if (s.u.get() < sx)
                Walker("  ", spx, sx, ex);

            // File 2

            out.printf("--- %d,%d ----%s", sy + 1, ey, newLines);

            // Now walk the snake between s,t, displaying the diffs

            for (ss = s; ss != t; ss = tt) {
                tt = ss.next;

                if (ss.v.get() < tt.y.get()) {
                    Walker("  ", spy, sy, ss.v.get());
                    String mark = ss.u.get() < tt.x.get() ? "! " : "+ ";
                    Walker(mark, spy, ss.v.get(), tt.y.get());
                    sy = tt.y.get();
                }
            }

            if (s.v.get() < sy)
                Walker("  ", spy, sy, ey);
        }
    }

    public void DiffUnified(int c) throws IOException, FileEncoderException {
        // s,t bound the diffs being displayed.
        // First we move t forward until the snake is bigger than
        // 2 * CONTEXT, then we display [s,t], then iterate for [t,...].

        // Remember: a snake is a matching chunk.  Diffs are inbetween
        // snakes, before the first snake, or after the last snake.

        if (c < 0) c = 3;
        Snake s = diff.GetSnake();
        Snake t;

        while ((t = s.next) != null) {
            // Look for snake > 2 * CONTEXT

            while (t.next != null && t.x.get() + 2 * c >= t.u.get())
                t = t.next;

            // Compute first/last lines of diff block

            int sx = s.u.get() - c > 0 ? s.u.get() - c : 0;
            int sy = s.v.get() - c > 0 ? s.v.get() - c : 0;
            int ex = t.x.get() + c < spx.Lines() ? t.x.get() + c : spx.Lines();
            int ey = t.y.get() + c < spy.Lines() ? t.y.get() + c : spy.Lines();

            // Display [s,t]

            out.printf("@@ -%d,%d +%d,%d @@%s",
                    sx + 1, ex - sx, sy + 1, ey - sy, newLines);

            // Now walk the snake between s,t, displaying the diffs

            do {
                int nx = s.u.get();
                int ny = s.v.get();

                Walker(" ", spx, sx, nx);

                s = s.next;
                sx = s.x.get();
                sy = s.y.get();

                Walker("-", spx, nx, sx);
                Walker("+", spy, ny, sy);
            }
            while (s != t);

            // Display the tail of the chunk

            Walker(" ", spx, sx, ex);
        }
    }

    public void DiffNorm() throws IOException, FileEncoderException {
        Snake s = diff.GetSnake();
        Snake t;

        for (; (t = s.next) != null; s = t) {
            /* Print edit operator */

            char c;
            int nx = s.u.get(), ny = s.v.get();

            if (s.u.get() < t.x.get() && s.v.get() < t.y.get()) {
                c = 'c';
                ++nx;
                ++ny;
            } else if (s.u.get() < t.x.get()) {
                c = 'd';
                ++nx;
            } else if (s.v.get() < t.y.get()) {
                c = 'a';
                ++ny;
            } else continue;

            out.printf("%d", nx);
            if (t.x.get() > nx) out.printf(",%d", t.x.get());
            out.printf("%c%d", c, ny);
            if (t.y.get() > ny) out.printf(",%d", t.y.get());
            out.printf("%s", newLines);

            /* Line lines that differ */

            Walker("< ", spx, s.u.get(), t.x.get());

            if (c == 'c') out.printf("---%s", newLines);

            Walker("> ", spy, s.v.get(), t.y.get());
        }
    }

    public void DiffRcs() throws IOException, FileEncoderException {
        Snake s = diff.GetSnake();
        Snake t;

        for (; (t = s.next) != null; s = t) {
            if (s.u.get() < t.x.get()) {
                out.printf("d%d %d%s", s.u.get() + 1, t.x.get() - s.u.get(), newLines);
                chunkCnt++;
            }
            if (s.v.get() < t.y.get()) {
                out.printf("a%d %d%s", t.x.get(), t.y.get() - s.v.get(), newLines);
                chunkCnt++;
                spy.SeekLine(s.v.get());
                spy.Dump(out, s.v, t.y.get(), lineType);
            }
        }
    }

    public void DiffHTML() throws IOException, FileEncoderException {
        Snake s = diff.GetSnake();
        Snake t;

        for (; (t = s.next) != null; s = t) {
            // Dump the common stuff

            spx.SeekLine(s.x.get());
            spy.SeekLine(s.v.get());
            spx.Dump(out, s.x, s.u.get(), lineType);

            // Dump spx

            out.printf("<font color=red>");
            spx.Dump(out, s.u, t.x.get(), lineType);

            // dump spy

            out.printf("</font><font color=blue>");
            spy.Dump(out, s.v, t.y.get(), lineType);

            // Done

            out.printf("</font>");
        }
    }

    public void DiffSummary() {
        Snake s = diff.GetSnake();
        Snake t;

        int l_deleted = 0;
        int l_added = 0;
        int l_edited_in = 0;
        int l_edited_out = 0;

        int c_deleted = 0;
        int c_added = 0;
        int c_edited = 0;

        for (; (t = s.next) != null; s = t) {
            /* Print edit operator */

            if (s.u.get() < t.x.get() && s.v.get() < t.y.get()) {
                l_edited_in += (t.x.get() - s.u.get());
                l_edited_out += (t.y.get() - s.v.get());
                ++c_edited;
            } else if (s.v.get() < t.y.get()) {
                l_added += (t.y.get() - s.v.get());
                ++c_added;
            } else if (s.u.get() < t.x.get()) {
                l_deleted += (t.x.get() - s.u.get());
                ++c_deleted;
            }
        }

        out.printf(
                "add %d chunks %d lines%s" +
                        "deleted %d chunks %d lines%s" +
                        "changed %d chunks %d / %d lines%s",
                c_added, l_added, newLines,
                c_deleted, l_deleted, newLines,
                c_edited, l_edited_in, l_edited_out, newLines);
    }

    public void DiffFast() {
        fastMaxD = 1;
    }

    public int GetChunkCnt() {
        return (chunkCnt);
    }

    public boolean IsIdentical() {
        boolean retval = diff != null && diff.GetSnake() != null && diff.GetSnake().next == null;

        return retval;
    }

    private void Walker(String mark, Sequence s,
                        int sx, int ex) throws IOException, FileEncoderException {
        s.SeekLine(sx);

        boolean lineEnd = true;

        for (; sx < ex; ++sx) {
            out.print(mark);
            lineEnd = s.Dump(out, new AtomicInteger(sx), sx + 1, lineType);
        }

        if (!lineEnd && this.flags.type == DiffFlags.Type.Unified)
            out.printf("%s\\ No newline at end of file%s", newLines, newLines);
    }

    private Sequence spx = null;
    private Sequence spy = null;
    private PrintWriter out = null;
    private DiffAnalyze diff = null;
    private DiffFlags flags = null;
    private boolean closeOut = false;
    private final Sequence.LineType lineType = Sequence.LineType.LineTypeRaw;
    private final String newLines = System.lineSeparator();
    private int fastMaxD = 0;
    private int chunkCnt = 0;
}
