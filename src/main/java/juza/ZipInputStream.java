package juza;

import static java.util.zip.ZipEntry.*;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.nio.charset.Charset;
import java.util.zip.CRC32;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;

import com.google.common.base.Charsets;

/**
 * java.util.zip.ZipInputStream 파일명 패치
 * 
 * <p>기존코드 수정을 최소화하기 위해 클래스명은 동일하게 하고, 패키지명만 다르게 만들어 import문만 변경하면 적용되도록 하였다.
 * jazzlib가 유니코드 환경에서 정상적으로 한글파일명을 처리하지 못하는 현상이 있어서 작성함.</p>
 *
 * 유니코드환경에서 기본 문자셋으로 작성된 파일명이 손상되는 이유는, 시간날때 다시 정리하는 걸로..^^;
 *
 * <p>이 클래스는 java.util.zip.ZipInputStream의 소소코드를 그대로 가져와서, 파일이름 처리 부분만을 패치한 겁니다.</p>
 * 
 * This class implements an input stream filter for reading files in the ZIP
 * file format. Includes support for both compressed and uncompressed entries.
 * 
 * @author David Connelly
 * @version %I%, %G%
 */
public class ZipInputStream extends InflaterInputStream {

	// java.util.zip.ZipConstants 에 정의되어 있는 상수를 가져왔습니다.
	
    /*
     * Header signatures
     */
    private static long LOCSIG = 0x04034b50L;	// "PK\003\004"
    private static long EXTSIG = 0x08074b50L;	// "PK\007\008"
//    private static long CENSIG = 0x02014b50L;	// "PK\001\002"
//    private static long ENDSIG = 0x06054b50L;	// "PK\005\006"

    /*
     * Header sizes in bytes (including signatures)
     */
    private static final int LOCHDR = 30;	// LOC header size
    private static final int EXTHDR = 16;	// EXT header size
//    private static final int CENHDR = 46;	// CEN header size
//    private static final int ENDHDR = 22;	// END header size

    /*
     * Local file (LOC) header field offsets
     */
//    private static final int LOCVER = 4;	// version needed to extract
    private static final int LOCFLG = 6;	// general purpose bit flag
    private static final int LOCHOW = 8;	// compression method
    private static final int LOCTIM = 10;	// modification time
    private static final int LOCCRC = 14;	// uncompressed file crc-32 value
    private static final int LOCSIZ = 18;	// compressed size
    private static final int LOCLEN = 22;	// uncompressed size
    private static final int LOCNAM = 26;	// filename length
    private static final int LOCEXT = 28;	// extra field length

    /*
     * Extra local (EXT) header field offsets
     */
    private static final int EXTCRC = 4;	// uncompressed file crc-32 value
    private static final int EXTSIZ = 8;	// compressed size
    private static final int EXTLEN = 12;	// uncompressed size

    /*
     * Central directory (CEN) header field offsets
     */
//    private static final int CENVEM = 4;	// version made by
//    private static final int CENVER = 6;	// version needed to extract
//    private static final int CENFLG = 8;	// encrypt, decrypt flags
//    private static final int CENHOW = 10;	// compression method
//    private static final int CENTIM = 12;	// modification time
//    private static final int CENCRC = 16;	// uncompressed file crc-32 value
//    private static final int CENSIZ = 20;	// compressed size
//    private static final int CENLEN = 24;	// uncompressed size
//    private static final int CENNAM = 28;	// filename length
//    private static final int CENEXT = 30;	// extra field length
//    private static final int CENCOM = 32;	// comment length
//    private static final int CENDSK = 34;	// disk number start
//    private static final int CENATT = 36;	// internal file attributes
//    private static final int CENATX = 38;	// external file attributes
//    private static final int CENOFF = 42;	// LOC header offset

    /*
     * End of central directory (END) header field offsets
     */
//    private static final int ENDSUB = 8;	// number of entries on this disk
//    private static final int ENDTOT = 10;	// total number of entries
//    private static final int ENDSIZ = 12;	// central directory size in bytes
//    private static final int ENDOFF = 16;	// offset of first CEN header
//    private static final int ENDCOM = 20;	// zip file comment length	
	
	private ZipEntry entry;
	private int flag;
	private CRC32 crc = new CRC32();
	private long remaining;
	private byte[] tmpbuf = new byte[512];

//	private static final int STORED = ZipEntry.STORED;
//	private static final int DEFLATED = ZipEntry.DEFLATED;

	private boolean closed = false;
	// this flag is set to true after EOF has reached for
	// one entry
	private boolean entryEOF = false;
	private Charset charset;

	/**
	 * Check to make sure that this stream has not been closed
	 */
	private void ensureOpen() throws IOException {
		if (closed) {
			throw new IOException("Stream closed");
		}
	}

	/**
	 * Creates a new ZIP input stream.
	 * 
	 * @param in
	 *            the actual input stream
	 * @param charset
	 *            파일명이 UTF-8이 아닌경우에 사용할 문자셋을 지정한다. 시스템의 인코딩(file.encoding)이
	 *            유니코드인 경우, EUC-KR과 같은 로컬 문자셋으로 작성된 파일명이 잘못 인식되는것을 교정하기 위한 용도로
	 *            사용한다.(기본값은 EUC-KR)
	 */
	public ZipInputStream(InputStream in, Charset charset) {
		super(new PushbackInputStream(in, 512), new Inflater(true), 512);
		if (in == null) {
			throw new NullPointerException("in is null");
		}
		this.charset = charset;
	}
	
	public ZipInputStream(InputStream in) {
		this(in, Charset.forName("EUC-KR"));
	}

	/**
	 * Reads the next ZIP file entry and positions the stream at the beginning
	 * of the entry data.
	 * 
	 * @return the next ZIP file entry, or null if there are no more entries
	 * @exception ZipException
	 *                if a ZIP file error has occurred
	 * @exception IOException
	 *                if an I/O error has occurred
	 */
	public ZipEntry getNextEntry() throws IOException {
		ensureOpen();
		if (entry != null) {
			closeEntry();
		}
		crc.reset();
		inf.reset();
		if ((entry = readLOC()) == null) {
			return null;
		}
		if (entry.getMethod() == STORED) {
			remaining = entry.getSize();
		}
		entryEOF = false;
		return entry;
	}

	/**
	 * Closes the current ZIP entry and positions the stream for reading the
	 * next entry.
	 * 
	 * @exception ZipException
	 *                if a ZIP file error has occurred
	 * @exception IOException
	 *                if an I/O error has occurred
	 */
	public void closeEntry() throws IOException {
		ensureOpen();
		while (read(tmpbuf, 0, tmpbuf.length) != -1)
			;
		entryEOF = true;
	}

	/**
	 * Returns 0 after EOF has reached for the current entry data, otherwise
	 * always return 1.
	 * <p>
	 * Programs should not count on this method to return the actual number of
	 * bytes that could be read without blocking.
	 * 
	 * @return 1 before EOF and 0 after EOF has reached for current entry.
	 * @exception IOException
	 *                if an I/O error occurs.
	 * 
	 */
	public int available() throws IOException {
		ensureOpen();
		if (entryEOF) {
			return 0;
		} else {
			return 1;
		}
	}

	/**
	 * Reads from the current ZIP entry into an array of bytes. If
	 * <code>len</code> is not zero, the method blocks until some input is
	 * available; otherwise, no bytes are read and <code>0</code> is returned.
	 * 
	 * @param b
	 *            the buffer into which the data is read
	 * @param off
	 *            the start offset in the destination array <code>b</code>
	 * @param len
	 *            the maximum number of bytes read
	 * @return the actual number of bytes read, or -1 if the end of the entry is
	 *         reached
	 * @exception NullPointerException
	 *                If <code>b</code> is <code>null</code>.
	 * @exception IndexOutOfBoundsException
	 *                If <code>off</code> is negative, <code>len</code> is
	 *                negative, or <code>len</code> is greater than
	 *                <code>b.length - off</code>
	 * @exception ZipException
	 *                if a ZIP file error has occurred
	 * @exception IOException
	 *                if an I/O error has occurred
	 */
	public int read(byte[] b, int off, int len) throws IOException {
		ensureOpen();
		if (off < 0 || len < 0 || off > b.length - len) {
			throw new IndexOutOfBoundsException();
		} else if (len == 0) {
			return 0;
		}

		if (entry == null) {
			return -1;
		}
		switch (entry.getMethod()) {
		case DEFLATED:
			len = super.read(b, off, len);
			if (len == -1) {
				readEnd(entry);
				entryEOF = true;
				entry = null;
			} else {
				crc.update(b, off, len);
			}
			return len;
		case STORED:
			if (remaining <= 0) {
				entryEOF = true;
				entry = null;
				return -1;
			}
			if (len > remaining) {
				len = (int) remaining;
			}
			len = in.read(b, off, len);
			if (len == -1) {
				throw new ZipException("unexpected EOF");
			}
			crc.update(b, off, len);
			remaining -= len;
			if (remaining == 0 && entry.getCrc() != crc.getValue()) {
				throw new ZipException("invalid entry CRC (expected 0x"
						+ Long.toHexString(entry.getCrc()) + " but got 0x"
						+ Long.toHexString(crc.getValue()) + ")");
			}
			return len;
		default:
			throw new ZipException("invalid compression method");
		}
	}

	/**
	 * Skips specified number of bytes in the current ZIP entry.
	 * 
	 * @param n
	 *            the number of bytes to skip
	 * @return the actual number of bytes skipped
	 * @exception ZipException
	 *                if a ZIP file error has occurred
	 * @exception IOException
	 *                if an I/O error has occurred
	 * @exception IllegalArgumentException
	 *                if n < 0
	 */
	public long skip(long n) throws IOException {
		if (n < 0) {
			throw new IllegalArgumentException("negative skip length");
		}
		ensureOpen();
		int max = (int) Math.min(n, Integer.MAX_VALUE);
		int total = 0;
		while (total < max) {
			int len = max - total;
			if (len > tmpbuf.length) {
				len = tmpbuf.length;
			}
			len = read(tmpbuf, 0, len);
			if (len == -1) {
				entryEOF = true;
				break;
			}
			total += len;
		}
		return total;
	}

	/**
	 * Closes this input stream and releases any system resources associated
	 * with the stream.
	 * 
	 * @exception IOException
	 *                if an I/O error has occurred
	 */
	public void close() throws IOException {
		if (!closed) {
			super.close();
			closed = true;
		}
	}

	private byte[] b = new byte[256];

	/*
	 * Reads local file (LOC) header for next entry.
	 */
	private ZipEntry readLOC() throws IOException {
		try {
			readFully(tmpbuf, 0, LOCHDR);
		} catch (EOFException e) {
			return null;
		}
		if (get32(tmpbuf, 0) != LOCSIG) {
			return null;
		}
		// get the entry name and create the ZipEntry first
		int len = get16(tmpbuf, LOCNAM);
		int blen = b.length;
		if (len > blen) {
			do
				blen = blen * 2;
			while (len > blen);
			b = new byte[blen];
		}
		readFully(b, 0, len);
		String name = getFileName(b, len);

		ZipEntry e = createZipEntry(name);
		// now get the remaining fields for the entry
		flag = get16(tmpbuf, LOCFLG);
		if ((flag & 1) == 1) {
			throw new ZipException("encrypted ZIP entry not supported");
		}
		e.setMethod(get16(tmpbuf, LOCHOW));
		e.setTime(get32(tmpbuf, LOCTIM));
		if ((flag & 8) == 8) {
			/* "Data Descriptor" present */
			if (e.getMethod() != DEFLATED) {
				throw new ZipException(
						"only DEFLATED entries can have EXT descriptor");
			}
		} else {
			e.setCrc(get32(tmpbuf, LOCCRC));
			e.setCompressedSize(get32(tmpbuf, LOCSIZ));
			e.setSize(get32(tmpbuf, LOCLEN));
		}
		if ((flag & (1 << 11)) == 1) {
			charset = Charsets.UTF_8;
		}

		len = get16(tmpbuf, LOCEXT);
		if (len > 0) {
			byte[] bb = new byte[len];
			readFully(bb, 0, len);
			e.setExtra(bb);
		}
		return e;
	}

	/**
	 * Creates a new <code>ZipEntry</code> object for the specified entry name.
	 * 
	 * @param name
	 *            the ZIP file entry name
	 * @return the ZipEntry just created
	 */
	protected ZipEntry createZipEntry(String name) {
		return new ZipEntry(name);
	}

	/*
	 * Reads end of deflated entry as well as EXT descriptor if present.
	 */
	private void readEnd(ZipEntry e) throws IOException {
		int n = inf.getRemaining();
		if (n > 0) {
			((PushbackInputStream) in).unread(buf, len - n, n);
		}
		if ((flag & 8) == 8) {
			/* "Data Descriptor" present */
			readFully(tmpbuf, 0, EXTHDR);
			long sig = get32(tmpbuf, 0);
			if (sig != EXTSIG) { // no EXTSIG present
				e.setCrc(sig);
				e.setCompressedSize(get32(tmpbuf, EXTSIZ - EXTCRC));
				e.setSize(get32(tmpbuf, EXTLEN - EXTCRC));
				((PushbackInputStream) in).unread(tmpbuf, EXTHDR - EXTCRC - 1,
						EXTCRC);
			} else {
				e.setCrc(get32(tmpbuf, EXTCRC));
				e.setCompressedSize(get32(tmpbuf, EXTSIZ));
				e.setSize(get32(tmpbuf, EXTLEN));
			}
		}
		if (e.getSize() != inf.getBytesWritten()) {
			throw new ZipException("invalid entry size (expected "
					+ e.getSize() + " but got " + inf.getBytesWritten()
					+ " bytes)");
		}
		if (e.getCompressedSize() != inf.getBytesRead()) {
			throw new ZipException("invalid entry compressed size (expected "
					+ e.getCompressedSize() + " but got " + inf.getBytesRead()
					+ " bytes)");
		}
		if (e.getCrc() != crc.getValue()) {
			throw new ZipException("invalid entry CRC (expected 0x"
					+ Long.toHexString(e.getCrc()) + " but got 0x"
					+ Long.toHexString(crc.getValue()) + ")");
		}
	}

	/*
	 * Reads bytes, blocking until all bytes are read.
	 */
	private void readFully(byte[] b, int off, int len) throws IOException {
		while (len > 0) {
			int n = in.read(b, off, len);
			if (n == -1) {
				throw new EOFException();
			}
			off += n;
			len -= n;
		}
	}

	/*
	 * Fetches unsigned 16-bit value from byte array at specified offset. The
	 * bytes are assumed to be in Intel (little-endian) byte order.
	 */
	private static final int get16(byte b[], int off) {
		return (b[off] & 0xff) | ((b[off + 1] & 0xff) << 8);
	}

	/*
	 * Fetches unsigned 32-bit value from byte array at specified offset. The
	 * bytes are assumed to be in Intel (little-endian) byte order.
	 */
	private static final long get32(byte b[], int off) {
		return get16(b, off) | ((long) get16(b, off + 2) << 16);
	}

	private String getFileName(byte[] b, int len) throws IOException {
		return new String(b, 0, len, charset);
	}
}
