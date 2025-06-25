/*
 * Copyright 2025 Perforce Software Inc., All Rights Reserved.
 */
package com.perforce.p4java.impl.mapbased.rpc.func.helper;

public class DigestResult {
    private final String digest;
    private final long fileSize;

    public DigestResult(String digest, long fileSize) {
        this.digest = digest;
        this.fileSize = fileSize;
    }

    public String getDigest() {
        return digest;
    }

    public long getFileSize() {
        return fileSize;
    }
}
