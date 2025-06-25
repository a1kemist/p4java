package com.perforce.p4java.diff;

public class DiffFlags {

    public enum Type {Normal, Context, Unified, Rcs, HTML, Summary}

    public enum Sequence {Line, Word, DashL, DashB, DashW, WClass}

    public enum Grid {Optimal, Guarded, TwoWay, Diff3, GuardedDiff3}

    public Type type;
    public Sequence sequence;
    public Grid grid;
    public int contextCount;

    public DiffFlags(String flags) {
        type = Type.Normal;
        sequence = Sequence.Line;
        grid = Grid.Optimal;
        contextCount = 0;
        boolean someDigit = false;

        if (flags == null) {
            return;
        }

        for (char c : flags.toCharArray()) {
            switch (c) {

                // P4MERGE, reserved flag (don't use!)

                case 'a':
                    break;

                // mods -b, -w, -l

                case 'l':
                    sequence = Sequence.DashL;
                    break;
                case 'b':
                    sequence = Sequence.DashB;
                    break;
                case 'w':
                    sequence = Sequence.DashW;
                    break;

                // types

                case 'c':
                case 'C':
                    type = Type.Context;
                    break;
                case 'h':
                case 'H':
                    type = Type.HTML;
                    sequence = Sequence.Word;
                    break;
                case 'v':
                    type = Type.HTML;
                    sequence = Sequence.WClass;
                    break;
                case 'n':
                    type = Type.Rcs;
                    break;
                case 's':
                    type = Type.Summary;
                    break;
                case 'u':
                case 'U':
                    type = Type.Unified;
                    break;

                // grid

                case 'g':
                case 'G':
                    if (grid == Grid.Diff3) grid = Grid.GuardedDiff3;
                    else grid = Grid.Guarded;
                    break;

                case 'x':
                case 'X':
                    if (grid == Grid.Guarded) grid = Grid.GuardedDiff3;
                    else grid = Grid.Diff3;
                    break;

                case 't':
                case 'T':
                    grid = Grid.TwoWay;
                    break;

                // Simple atoi()

                case '0':
                case '1':
                case '2':
                case '3':
                case '4':
                case '5':
                case '6':
                case '7':
                case '8':
                case '9':
                    contextCount = contextCount * 10 + c - '0';
                    someDigit = true;
                    break;
            }
            if (!someDigit)
                contextCount = -1;
        }
    }
}
