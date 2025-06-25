package com.perforce.p4java.diff;

import com.perforce.p4java.exception.FileEncoderException;
import com.perforce.p4java.impl.mapbased.rpc.sys.RpcInputStream;
import com.perforce.p4java.impl.mapbased.rpc.sys.RpcPerforceFile;

import java.io.Closeable;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class Sequence implements ISequence, Closeable {

    public static int P4TUNE_FILESYS_BUFSIZE = 64 * 1024 * 1024;

    class ReadFile {
        private Charset charset = null;
        private RpcPerforceFile file = null;

        public void Open(RpcPerforceFile f, Charset charset) throws IOException, FileEncoderException {
            this.file = f;
            this.charset = charset;
            in = new RpcInputStream(f, charset);
            buf = new byte[P4TUNE_FILESYS_BUFSIZE];
        }

        public void Close() throws IOException {
            in.close();
        }

        private void deleteFile() throws IOException {
            file.delete();
        }

        public char Char() {
            return (char) buf[pos];
        }

        public char Get() throws IOException {
            return Eof() ? '\0' : (char) buf[pos++];
        }

        public void Prev() throws IOException, FileEncoderException {
            if (--pos < 0) Seek(Tell());
        }

        public void Next() {
            ++pos;
        }

        public long Size() {
            return size;
        }

        public long Tell() {
            return offset - len + pos;
        }

        public boolean Eof() throws IOException {
            return InMem() == 0;
        }

        public void Seek(long o) throws IOException, FileEncoderException {
            long windowStart = offset - len;

            if (o > offset) {
                // o > offset
                // Read forward then seek again.

                if (Read() != 0)
                    Seek(o);
            } else if (o < windowStart) {
                // Seek before current buffer.

                // The in-memory offset and file offset
                // are different due to the line-ending
                // conversion on Windows. Rewind to the
                // beginning of the file then try again.

                in.close();
                in = new RpcInputStream(file, charset);
                offset = 0;
                if (Read() != 0)
                    Seek(o);
            } else {
                pos = (int)(o - windowStart);
            }
        }

        public long Memcmp(ReadFile other, long length) throws IOException {
            long l1, l2;

            while (length != 0 && (l1 = InMem()) != 0 && (l2 = other.InMem()) != 0) {
                if (l1 > length)
                    l1 = length;
                if (l1 > l2)
                    l1 = l2;

                int x = 0;
                for (int i = 0; x == 0 && i < l1; i++)
                    x = this.buf[pos++] - other.buf[other.pos++];
                if (x != 0)
                    return x;

                length -= l1;
            }

            return 0;
        }

        public int Memcpy(byte[] buf, int length) throws IOException {
            int l;
            int olen = length;
            int bpos = 0;

            while (length != 0 && (l = InMem()) != 0) {
                if (l > length)
                    l = length;

                for (int i = 0; i < l; i++)
                    buf[bpos++] = this.buf[pos++];

                length -= l;
            }

            return olen - length;
        }

        public int Memccpy(byte[] buf, char c, int length) throws IOException {
            int l;
            int olen = length;
            int bpos = 0;
            boolean hit = false;

            while (!hit && length != 0 && (l = InMem()) != 0) {
                if (l > length)
                    l = length;

                for (int i = 0; !hit && i < l; i++) {
                    hit = this.buf[pos] == c;
                    buf[bpos++] = this.buf[pos++];
                }

                length -= l;
            }

            return olen - length;
        }

        public int Memchr(char c, int length) throws IOException {
            // Memchr( c, -1 ) means to Eof()

            if (length == -1)
                length = (int) (Size() - Tell());

            int l;
            int olen = length;
            boolean hit = false;

            while (!hit && length != 0 && (l = InMem()) != 0) {
                if (l > length)
                    l = length;

                for (int i = 0; !hit && i < l; i++) {
                    hit = this.buf[pos++] == c;
                }
                length -= l;
            }

            return olen - length;
        }

        public int Textcpy(byte[] dst, int dstlen,
                           long srclen, LineType type) throws IOException {
            switch (type) {
                default:
                case LineTypeRaw: {
                    // Just Memcpy the minimum.

                    return Memcpy(dst, (int) (dstlen < srclen ? dstlen : srclen));
                }

                case LineTypeCr: {
                    // Memcpy the minimum, translating \r to \n

                    if (dstlen > srclen) dstlen = (int) srclen;
                    int l = 0;
                    while (dstlen != 0) {
                        l = Memccpy(dst, '\r', dstlen);
                        if (l == 0) break;
                        dstlen -= l;
                        if (dst[l - 1] == '\r') dst[l - 1] = '\n';
                    }

                    return l;
                }

                case LineTypeCrLf:
                case LineTypeLfcrlf: {
                    // Memcpy, stopping at each \r and, if it is followed
                    // by a \n, translating the \r to \n and dropping the
                    // \n from the source.  This can cause dstlen and srclen
                    // to move out of step.  If we hit a \r at the exact end
                    // of srclen, and a \n follows, srclen reaches -1.
                    // LFCRLF reads CRLF.


                    int l = 0;
                    while (dstlen != 0 && srclen > 0) {
                        l = Memccpy(dst, '\r', (int) (dstlen < srclen ? dstlen : srclen));
                        if (l == 0) break;

                        dstlen -= l;
                        srclen -= l;

                        if (dst[l - 1] == '\r' && !Eof() && Char() == '\n') {
                            Next();
                            dst[l - 1] = '\n';
                            --srclen;
                        }
                    }

                    return l;
                }
            }
        }

        private int Read() throws IOException {

            int l = in.read(buf);

            if (l <= 0) {
                // say what? file got short?
                size = offset;
                return 0;
            }

            pos = 0;
            offset += l;
            len = l;
            if( offset > size )
                size = offset;

            /* return if more */

            return l;
        }

        private int InMem() throws IOException {
            return len - pos > 0 ? len - pos : Read();
        }

        private int pos;        // current char in memory window
        private int len;        // current bytes in memory window
        private byte[] buf;        // memory window

        private long size;        // length of file
        private long offset;        // file offset of end of buf

        private RpcInputStream in; // underlying file for Read()

    }

    public enum LineType {LineTypeRaw, LineTypeCr, LineTypeCrLf, LineTypeLfcrlf}

    class VarInfo {
        public int hash;
        public long offset;
    }

    abstract class ISequencer {
        abstract boolean Equal(int lineA, Sequence b, int lineB) throws IOException, FileEncoderException;

        abstract void Load() throws IOException;

        Sequence a;
        ReadFile src;
    }

    class LineReader extends ISequencer {

        boolean Equal(int lineA, Sequence b, int lineB) throws IOException, FileEncoderException {
            // hashes lready checked by Sequence::Equal()
            // length unequal . lines unequal

            if (a.Length(lineA) != b.Length(lineB))
                return false;

            // same hash, same length . we have to check the actual file contents

            a.SeekLine(lineA);
            b.SeekLine(lineB);

            return src.Memcmp(b.sequencer.src, a.Length(lineA)) == 0;
        }

        void Load() throws IOException {

            int h = 0;

            if (!src.Eof())
                while (true) {
                    char c = src.Char();
                    src.Next();

                    h = CHARHASH(h, c);

                    if (src.Eof()) {
                        a.StoreLine(h);
                        break;
                    } else if (c == '\n') {
                        a.StoreLine(h);
                        h = 0;
                    }
                }
        }
    }

    class WordReader extends LineReader {

        void Load() throws IOException {

            int h = 0;

            if (!src.Eof())
                while (true) {
                    char c = src.Char();
                    src.Next();

                    h = CHARHASH(h, c);

                    if (src.Eof()) {
                        a.StoreLine(h);
                        break;
                    } else if (isspace(c)) {
                        a.StoreLine(h);
                        h = 0;
                    }
                }
        }
    }

    private boolean isspace(char c) {
        return c == ' ';
    }

    class WClassReader extends LineReader {

        void Load() throws IOException {

            int h = 0;

            int lastcharclass = 0;
            char c = 0;

            if (src.Eof())
                return;

            do {
                c = src.Char();

                int charclass;

                if (c == '\r') {
                    charclass = 1;
                } else if (c == '\n') {
                    charclass = 5;
                } else if (Character.isAlphabetic(c) || (c & 0x80) != 0) {
                    charclass = 2;
                } else if (isspace(c)) {
                    charclass = 3;
                } else {
                    charclass = 4;
                }

                if (charclass != lastcharclass) {
                    if (charclass == 5) {
                        charclass = 6;
                        if (lastcharclass == 1)
                            lastcharclass = 0;
                    }
                    if (lastcharclass != 0) {
                        a.StoreLine(h);
                        h = 0;
                    }
                    lastcharclass = charclass;
                }

                h = CHARHASH(h, c);

                src.Next();

            } while (!src.Eof());

            a.StoreLine(h);
        }
    }

    class DifflReader extends ISequencer {

        boolean Equal(int lineA, Sequence b, int lineB) throws IOException, FileEncoderException {

            ISequencer bs = b.sequencer;

            long la = a.Length(lineA);
            long lb = b.Length(lineB);

            // hashes already checked by Sequence::Equal()
            // length can be out by a maximum of 1 character \r\n <> \n
            // quick optimization (modified to allow for unsigned)

            if (la > (lb + 1) || (la + 1) < lb)
                return false;

            // same hash, we have to check the actual file contents

            a.SeekLine(lineA);
            b.SeekLine(lineB);

            char ca = '\0', cb = '\0';

            while (la != 0 && lb != 0) {
                // Load next char

                ca = src.Get();
                cb = bs.src.Get();

                if (ca != cb)
                    break;

                // used ca, cb

                --la;
                --lb;
            }

            // Last line might have no newline (with -dl)
            if (testEndEOL)
                if ((la == 0 && lb == 1 && IsNewLine(bs.src.Get())) ||
                        (lb == 0 && la == 1 && IsNewLine(src.Get())))
                    return true;

            return !((la != 0 || lb != 0) && !IsNewLine(ca) && !IsNewLine(cb));
        }

        void Load() throws IOException {

            int h = 0;

            while (!src.Eof()) {
                char c = src.Char();
                src.Next();

                if (IsNewLine(c)) {
                    if (!src.Eof() && c == '\r' && src.Char() == '\n')
                        src.Next();

                    c = '\n';
                }

                h = CHARHASH(h, c);

                // Add hash newline if last line didn't have one

                if (src.Eof() && c != '\n')
                    h = CHARHASH(h, '\n');

                if (src.Eof() || c == '\n') {
                    a.StoreLine(h);
                    h = 0;
                }
            }
        }

        // any newline character

        boolean IsNewLine(char c) {
            return c == '\r' || c == '\n';
        }

        boolean testEndEOL = true;
    }

    class DiffbReader extends DifflReader {

        boolean Equal(int lineA, Sequence b, int lineB) throws IOException, FileEncoderException {

            ISequencer bs = b.sequencer;

            // Start at line beginning

            a.SeekLine(lineA);
            b.SeekLine(lineB);

            long la = a.Length(lineA);
            long lb = b.Length(lineB);

            char ca = la != 0 ? src.Get() : 0, cb = lb != 0 ? bs.src.Get() : 0;

            // While more lines

            while (la != 0 && lb != 0) {
                // If we're looking at Whitespace() or newline in BOTH
                // then eat up Whitespace() (but not newline) in both.
                // This handles change of whitespace amount and change
                // of whitespace presence at EOL.

                if ((IsWhitespace(ca) || IsNewLine(ca))
                        && (IsWhitespace(cb) || IsNewLine(cb))) {
                    while (IsWhitespace(ca) && --la != 0)
                        ca = src.Get();
                    while (IsWhitespace(cb) && --lb != 0)
                        cb = bs.src.Get();
                    if (la == 0 || lb == 0)
                        break;
                }

                // Whitespace gone; now safe to check chars.

                if (ca != cb)
                    break;

                // Load next char

                if (--la != 0)
                    ca = src.Get();
                if (--lb != 0)
                    cb = bs.src.Get();
            }

            // Any mismatching chars? (whitespace/newline characters don't count)

            while (la != 0 && (IsWhitespace(ca) || IsNewLine(ca)) && --la != 0)
                ca = src.Get();

            while (lb != 0 && (IsWhitespace(cb) || IsNewLine(cb)) && --lb != 0)
                cb = bs.src.Get();

            return la == 0 && lb == 0;
        }

        void Load() throws IOException {

            int h = 0;

            while (!src.Eof()) {
                char c = src.Char();
                src.Next();

                // Absorb whitespace into a single space

                if (IsWhitespace(c)) {
                    c = ' ';

                    while (!src.Eof() && IsWhitespace(src.Char()))
                        src.Next();

                    // hash in the single space, unless eof or eol

                    if (src.Eof()) {
                        a.StoreLine(h);
                        break;
                    }

                    if (!IsNewLine(src.Char()))
                        h = CHARHASH(h, c);

                    c = src.Char();
                    src.Next();
                }

                // skip the '\r' otherwise the next stored line
                // will begin with '\n'

                if (!src.Eof() && c == '\r' && src.Char() == '\n')
                    src.Next();

                // don't hash the newline

                if (!IsNewLine(c))
                    h = CHARHASH(h, c);

                if (src.Eof() || IsNewLine(c)) {
                    a.StoreLine(h);
                    h = 0;
                }
            }
        }

        // for purposes of diff -b: what is whitespace?

        boolean IsWhitespace(char c) {
            return c == ' ' || c == '\t';
        }
    }

    class DiffwReader extends DiffbReader {

        boolean Equal(int lineA, Sequence b, int lineB) throws IOException, FileEncoderException {

            ISequencer bs = b.sequencer;

            // Start at line beginning

            a.SeekLine(lineA);
            b.SeekLine(lineB);

            long la = a.Length(lineA);
            long lb = b.Length(lineB);

            char ca = la != 0 ? src.Get() : 0, cb = lb != 0 ? bs.src.Get() : 0;

            // While more lines

            while (la != 0 && lb != 0) {
                // Eliminate whitespace

                while (IsWhitespace(ca) && --la != 0)
                    ca = src.Get();
                while (IsWhitespace(cb) && --lb != 0)
                    cb = bs.src.Get();

                if (la == 0 || lb == 0)
                    break;

                // Whitespace gone; now safe to check chars.

                if (ca != cb)
                    break;

                // Load next char

                if (--la != 0)
                    ca = src.Get();
                if (--lb != 0)
                    cb = bs.src.Get();
            }

            // Any mismatching chars? (whitespace/newline characters don't count)

            while (la != 0 && (IsWhitespace(ca) || IsNewLine(ca)) && --la != 0)
                ca = src.Get();

            while (lb != 0 && (IsWhitespace(cb) || IsNewLine(cb)) && --lb != 0)
                cb = bs.src.Get();

            return la == 0 && lb == 0;
        }

        void Load() throws IOException {

            int h = 0;

            while (!src.Eof()) {
                char c = src.Char();
                src.Next();

                // Eliminate whitespace

                while (IsWhitespace(c) && !src.Eof()) {
                    c = src.Char();
                    src.Next();
                }

                // skip the '\r' otherwise the next stored line
                // will begin with '\n'

                if (!src.Eof() && c == '\r' && src.Char() == '\n')
                    src.Next();

                // don't hash the newline, nor any whitespace at EOF

                if (!IsNewLine(c) && !IsWhitespace(c))
                    h = CHARHASH(h, c);

                if (src.Eof() || IsNewLine(c)) {
                    a.StoreLine(h);
                    h = 0;
                }
            }
        }
    }

    private static int CHARHASH(int h, char c) {
        return (293 * (h) + (c));
    }

    public Sequence(final Sequence other, final DiffFlags flags) {
        lines.addAll(other.lines);

        sequencer = null;

        readfile = new ReadFile();

        switch (flags.sequence) {
            case Line:
                sequencer = new LineReader();
                break;
            case Word:
                sequencer = new WordReader();
                break;
            case WClass:
                sequencer = new WClassReader();
                break;
            case DashL:
                sequencer = new DifflReader();
                break;
            case DashB:
                sequencer = new DiffbReader();
                break;
            case DashW:
                sequencer = new DiffwReader();
                break;
        }

        sequencer.a = this;
        sequencer.src = readfile;
        // Must call Reuse() before using this instance
    }

    public Sequence(RpcPerforceFile f, Charset charset, final DiffFlags flags) throws IOException, FileEncoderException {
        sequencer = null;

        readfile = new ReadFile();

        // Build list of line hashes.

        switch (flags.sequence) {
            case Line:
                sequencer = new LineReader();
                break;
            case Word:
                sequencer = new WordReader();
                break;
            case WClass:
                sequencer = new WClassReader();
                break;
            case DashL:
                sequencer = new DifflReader();
                break;
            case DashB:
                sequencer = new DiffbReader();
                break;
            case DashW:
                sequencer = new DiffwReader();
                break;
        }

        // We open, ~Sequence() closes.

        sequencer.a = this;
        sequencer.src = readfile;
        readfile.Open(f, charset);

        // allocate initial space

        lines.clear();
        VarInfo vi =new VarInfo();
        vi.offset=0;
        vi.hash=0;
        lines.add(vi);

        // Load lines

        sequencer.Load();
    }

    public int Lines() {
        return lines.size() -1;
    }

    public void SeekLine(int l) throws IOException, FileEncoderException {
        readfile.Seek(Off(l));
    }

    public int CopyLines(AtomicInteger l, int m,
                         byte[] buf, int length, LineType lineType) throws IOException {
        // Don't go past the end of the file!

        if (Lines() < m)
            m = Lines();

        // Copy what we can

        length = readfile.Textcpy(buf, length, LengthLeft(m - 1), lineType);

        // Did we finish?

        if (LengthLeft(m - 1) == 0)
            l.set(m);

        return length;
    }

    public boolean Dump(PrintWriter out, AtomicInteger l, int m, LineType lineType) throws IOException {
        int len;
        int llen = 0;
        byte[] buf = new byte[1024];

        while ((len = CopyLines(l, m, buf, 1024, lineType)) != 0) {
            for (int i = 0; i < len; i++)
                out.write(buf[i]);
            llen = len;
        }

        return llen <= 0 || buf[llen - 1] == '\n';


    }

    public long Length(int l) {
        return Off(l + 1) - Off(l);
    }

    public long Length(int l, int m) {
        return Off(m) - Off(l);
    }

    public long LengthLeft(int l) {
        return Off(l + 1) - readfile.Tell();
    }

    public boolean Equal(int lA, ISequence b, int lB) throws IOException, FileEncoderException {
        return ProbablyEqual(lA, b, lB) &&
                sequencer.Equal(lA, ((Sequence) b), lB);
    }

    public boolean ProbablyEqual(int lA, ISequence b, int lB) {
        return Hash(lA) == ((Sequence) b).Hash(lB);
    }

    public void StoreLine(int intue) {
        lines.get(Lines()).hash = intue;
        VarInfo varInfo = new VarInfo();
        varInfo.hash = 0;
        varInfo.offset = readfile.Tell();
        lines.add(varInfo);
    }


    private int Hash(int l) {
        return lines.get(l).hash;
    }

    private long Off(int l) {
        return lines.get(l).offset;
    }

    /* Variable length list of lines */

    private final List<VarInfo> lines = new ArrayList<>();

    public void close() {
        try {
            readfile.Close();
        } catch (IOException e) {
            //throw new RuntimeException(e);
        }
    }

    public void deleteFile() {
		try {
			readfile.deleteFile();
		} catch (IOException e) {
			//throw new RuntimeException(e);
		}
	}

    public void Reuse(RpcPerforceFile f, Charset charset) throws FileEncoderException, IOException {
        readfile.Open(f, charset);
    }

    /* Actual underlying file reader */

    private ISequencer sequencer;
    private final ReadFile readfile;
}
