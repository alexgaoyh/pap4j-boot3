package cn.net.pap.common.opencv;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.KnnVectorField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.KnnVectorQuery;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.MMapDirectory;
import org.junit.jupiter.api.Test;

/**
 * 将图像的特征向量放入 lucene 中，后续通过 KNN 进行相似度查询，类似以图搜图的功能实现。
 */
public class KnnVectorQueryTest {
    private static final Logger log = LoggerFactory.getLogger(KnnVectorQueryTest.class);

    public static final Path indexPath = Paths.get("target/index");

    @Test
    public void testQuery() throws IOException {

        float[] firstVector = null;

        try (MMapDirectory dir = new MMapDirectory(indexPath)) {
            try (IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig())) {
                List<String> imageAbsPathList = new ArrayList<>();
                imageAbsPathList.add(TestResourceUtil.getFile("0.jpg").getAbsolutePath().toString());
                imageAbsPathList.add(TestResourceUtil.getFile("0.jpg").getAbsolutePath().toString());
                imageAbsPathList.add(TestResourceUtil.getFile("0.jpg").getAbsolutePath().toString());
                for(String imageAbsPath : imageAbsPathList) {
                    byte[] bytes = OpenCVUtils.matOfKeyPointImage(imageAbsPath, true, 500, 500);
                    // added PCA to reduce dimensions
                    float[] floats = OpenCVUtils.normalize(OpenCVUtils.convertArray(OpenCVUtils.byteArrayToFloatList(bytes), Integer.MAX_VALUE));
                    if(firstVector == null) {
                        firstVector = floats;
                    }
                    Document doc = new Document();
                    doc.add(new StoredField("id", imageAbsPath));
                    doc.add(new KnnVectorField("field", floats));
                    writer.addDocument(doc);
                }
            }
            log.info("");
            try (IndexReader reader = DirectoryReader.open(dir)) {
                IndexSearcher searcher = new IndexSearcher(reader);

                TopDocs results = searcher.search(new KnnVectorQuery("field", firstVector, 3), 10);
                log.info("{}", "Hits: " + results.totalHits);
                for (ScoreDoc sdoc : results.scoreDocs) {
                    Document doc = reader.document(sdoc.doc);
                    StoredField idField = (StoredField) doc.getField("id");
                    log.info("{}", "Found: " + idField.toString() + " = " + String.format("%.1f", sdoc.score));
                }
            }
        }

    }
}
