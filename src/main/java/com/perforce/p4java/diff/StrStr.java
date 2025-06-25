/*
 * Copyright 2025 Perforce Software Inc., All Rights Reserved.
 */
package com.perforce.p4java.diff;

public class StrStr {
    private String fileName;
    private String digest;

    public StrStr(String fileName, String digest) {
        this.fileName = fileName;
        this.digest = digest;
    }

    public String getFileName() {
        return this.fileName;
    }

    public String getDigest() {
        return this.digest;
    }
}
