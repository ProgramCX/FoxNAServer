package cn.programcx.foxnaserver.util;

import java.io.IOException;
import java.io.InputStream;

public class LimitedInputStream extends InputStream {
    private final InputStream in;
    private long left;
    private long mark = -1;

    public LimitedInputStream(InputStream in, long limit) {
        this.in = in;
        this.left = limit;
    }

    @Override
    public int read() throws IOException {
        if (left == 0) return -1;
        int result = in.read();
        if (result != -1) left--;
        return result;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (left == 0) return -1;
        len = (int) Math.min(len, left);
        int result = in.read(b, off, len);
        if (result != -1) left -= result;
        return result;
    }

    @Override
    public long skip(long n) throws IOException {
        long skipped = in.skip(Math.min(n, left));
        left -= skipped;
        return skipped;
    }

    @Override
    public int available() throws IOException {
        int avail = in.available();
        return (int) Math.min(avail, left);
    }

    @Override
    public void close() throws IOException {
        in.close();
    }

    @Override
    public synchronized void mark(int readlimit) {
        in.mark(readlimit);
        mark = left;
    }

    @Override
    public synchronized void reset() throws IOException {
        in.reset();
        left = mark;
    }

    @Override
    public boolean markSupported() {
        return in.markSupported();
    }
}
