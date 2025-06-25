package com.perforce.p4java.diff;

import java.util.concurrent.atomic.AtomicInteger;

public class Snake {
    public Snake next = null;
    // u-x == v-y 'cause they match
    public AtomicInteger x = new AtomicInteger(0), u = new AtomicInteger(0);    // matching part of first file
    public AtomicInteger y = new AtomicInteger(0), v = new AtomicInteger(0);    // matching part of second file
}
