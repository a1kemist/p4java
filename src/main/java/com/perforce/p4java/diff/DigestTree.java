/*
 * Copyright 2025 Perforce Software Inc., All Rights Reserved.
 */
package com.perforce.p4java.diff;

import java.util.HashMap;
import java.util.Map;

public class DigestTree {
    private final Map<String, StrStr> map;

    public DigestTree() {
        this.map = new HashMap<>();
    }

    /**
     * Custom putIfAbsent implementation.
     * Adds the entry only if no mapping for the fileName already exists.
     *
     * @param entry the StrStr object to insert
     * @return true if the entry was added, false if it already existed
     */
    public boolean putIfAbsent(StrStr entry) {
        String key = entry.getFileName();

        if (!map.containsKey(key)) {
            map.put(key, entry);
            return true;
        }

        return false;
    }

    public boolean contains(StrStr entry) {
        if (map.containsKey(entry.getFileName())) {
            return true;
        }
        return false;
    }

    public StrStr get(String fileName) {
        return map.get(fileName);
    }

    public void remove(String fileName) {
        map.remove(fileName);
    }

    public void clear() {
        map.clear();
    }
}