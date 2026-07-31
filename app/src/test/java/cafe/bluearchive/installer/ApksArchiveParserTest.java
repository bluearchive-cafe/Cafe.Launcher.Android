package cafe.bluearchive.installer;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertEquals;

public class ApksArchiveParserTest {
    @Test
    public void parseAcceptsBaseAndSplit() throws Exception {
        File file = File.createTempFile("archive", ".apks");
        writeZip(file,
                new Entry("base.apk", 3),
                new Entry("config.arm64_v8a.apk", 2));

        ApksArchive archive = new ApksArchiveParser(new DownloadLimits(1024, 1024, 1024, 4))
                .parse(file);

        assertEquals(2, archive.splitCount());
        assertEquals(5, archive.totalBytes());
    }

    @Test(expected = java.util.zip.ZipException.class)
    public void parseRejectsArchiveWithoutBaseApk() throws Exception {
        File file = File.createTempFile("archive", ".apks");
        writeZip(file, new Entry("config.en.apk", 1));

        new ApksArchiveParser(new DownloadLimits(1024, 1024, 1024, 4)).parse(file);
    }

    @Test(expected = java.util.zip.ZipException.class)
    public void parseRejectsDuplicateDisplayNames() throws Exception {
        File file = File.createTempFile("archive", ".apks");
        writeZip(file,
                new Entry("base.apk", 1),
                new Entry("nested/base.apk", 1));

        new ApksArchiveParser(new DownloadLimits(1024, 1024, 1024, 4)).parse(file);
    }

    @Test(expected = java.util.zip.ZipException.class)
    public void parseRejectsTooManySplits() throws Exception {
        File file = File.createTempFile("archive", ".apks");
        writeZip(file,
                new Entry("base.apk", 1),
                new Entry("config.one.apk", 1));

        new ApksArchiveParser(new DownloadLimits(1024, 1024, 1024, 1)).parse(file);
    }

    private static void writeZip(File file, Entry... entries) throws Exception {
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(file))) {
            for (Entry entry : entries) {
                zip.putNextEntry(new ZipEntry(entry.name));
                for (int i = 0; i < entry.size; i++) {
                    zip.write('a');
                }
                zip.closeEntry();
            }
        }
    }

    private static final class Entry {
        final String name;
        final int size;

        Entry(String name, int size) {
            this.name = name;
            this.size = size;
        }
    }
}
