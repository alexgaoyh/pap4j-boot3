package cn.net.pap.common.file;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.sf.sevenzipjbinding.ExtractOperationResult;
import net.sf.sevenzipjbinding.IInArchive;
import net.sf.sevenzipjbinding.SevenZip;
import net.sf.sevenzipjbinding.SevenZipException;
import net.sf.sevenzipjbinding.impl.RandomAccessFileInStream;
import net.sf.sevenzipjbinding.simple.ISimpleInArchive;
import net.sf.sevenzipjbinding.simple.ISimpleInArchiveItem;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * ISO Extract
 */
public class ISOExtractor {
    private static final Logger log = LoggerFactory.getLogger(ISOExtractor.class);

    /**
     * ISO Extract Test
     */
    // @Test
    public void extractISOTest() {
        String isoFilePath = "1999pic.iso";
        String outputDir = "1999pic";

        try {
            long startTime = System.currentTimeMillis();
            extractISO(isoFilePath, outputDir);
            long endTime = System.currentTimeMillis();
            log.info("{}", "Extraction complete. Time taken: " + (endTime - startTime) + " ms");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void extractISO(String isoFilePath, String outputDir) throws IOException {
        RandomAccessFile isoFile = new RandomAccessFile(isoFilePath, "r");
        IInArchive inArchive = null;
        try {
            inArchive = SevenZip.openInArchive(null, new RandomAccessFileInStream(isoFile));
            ISimpleInArchive simpleInArchive = inArchive.getSimpleInterface();

            for (ISimpleInArchiveItem item : simpleInArchive.getArchiveItems()) {
                final int[] hash = new int[]{0};
                if (!item.isFolder()) {
                    ExtractOperationResult result;
                    final File outputFile = new File(outputDir, item.getPath());
                    outputFile.getParentFile().mkdirs();

                    try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                        result = item.extractSlow(data -> {
                            try {
                                fos.write(data);
                            } catch (IOException e) {
                                e.printStackTrace();
                                return -1;
                            }
                            return data.length;
                        });

                        if (result != ExtractOperationResult.OK) {
                            System.err.println("Error extracting item: " + item.getPath());
                        }
                    }
                }
            }
        } catch (SevenZipException e) {
            e.printStackTrace();
        } finally {
            if (inArchive != null) {
                try {
                    inArchive.close();
                } catch (SevenZipException e) {
                    e.printStackTrace();
                }
            }
            isoFile.close();
        }
    }

}
