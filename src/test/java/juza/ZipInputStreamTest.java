package juza;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.zip.ZipEntry;

import org.junit.Test;

import com.google.common.io.ByteStreams;

public class ZipInputStreamTest {

	/**
	 * http://bugs.java.com/view_bug.do?bug_id=4532049
	 * JDK-4532049 : IllegalArgumentException in ZipInputStream while reading unicode file
	 * java.lang.IllegalArgumentException
	 */
	@Test
	public void juz() throws Exception {
		InputStream is = new FileInputStream("src/test/resources/한글.zip");
		try {
			java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(is);
			ZipEntry entry = null;
			while ((entry = zis.getNextEntry()) != null) {
				if (!entry.isDirectory()) {
					System.out.println(entry.getName());
					byte[] content = ByteStreams.toByteArray(zis);
					System.out.println(content.length);
				}
			}
			zis.close();
		}
		finally {
			is.close();
		}
	}

	@Test
	public void juza() throws Exception {
		InputStream is = new FileInputStream("src/test/resources/한글.zip");
		try {
			ZipInputStream zis = new ZipInputStream(is);
			ZipEntry entry = null;
			while ((entry = zis.getNextEntry()) != null) {
				if (!entry.isDirectory()) {
					System.out.println(entry.getName());
					byte[] content = ByteStreams.toByteArray(zis);
					System.out.println(content.length);
				}
			}
			zis.close();
		}
		finally {
			is.close();
		}
	}
}
