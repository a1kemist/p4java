package com.perforce.p4java.diff;

import com.perforce.p4java.exception.FileEncoderException;

import java.io.IOException;

public interface ISequence {
    int Lines();

    boolean Equal(int lA, ISequence B, int lB) throws IOException, FileEncoderException;

    boolean ProbablyEqual(int lA, ISequence B, int lB);
}
