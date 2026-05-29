package dev.oddsystems.copeland.tools;

import dev.oddsystems.copeland.codec.CopelandCodec;
import org.apache.lucene.codecs.Codec;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.SortedNumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

/**
 * Phase 0+1 ingest helper.
 *
 * <p>Writes {@code numDocs} synthetic documents to an index directory using a
 * caller-chosen {@link CodecChoice codec}. Each document exercises a different
 * storage family so that {@link SegmentDumper} shows the full set of segment
 * file extensions:
 *
 * <ul>
 *   <li>{@code id}     - {@code StringField} stored - produces postings + stored fields</li>
 *   <li>{@code body}   - {@code TextField} analyzed - produces postings + positions</li>
 *   <li>{@code category} - {@code SortedDocValuesField} - produces sorted doc values</li>
 *   <li>{@code ts}     - {@code NumericDocValuesField} - produces numeric doc values</li>
 *   <li>{@code count}  - {@code SortedNumericDocValuesField} - produces sorted-numeric doc values</li>
 * </ul>
 */
public final class SampleIndexer {

    private static final String[] CATEGORIES = {
            "alpha", "bravo", "charlie", "delta", "echo", "foxtrot"
    };

    private static final String[] WORDS = {
            "lucene", "columnar", "segment", "codec", "docvalues", "bkd",
            "posting", "block", "encoding", "dictionary", "compression",
            "zone", "stats", "page", "merge", "directory", "input", "output"
    };

    private static final int COMMIT_INTERVAL = 2_500;

    /** Which codec to install on the {@link IndexWriterConfig}. */
    public enum CodecChoice {
        /** Lucene's built-in default codec (currently {@code Lucene104}). */
        DEFAULT,
        /** {@link CopelandCodec}, the Phase 1 pass-through codec. */
        COPELAND
    }

    private SampleIndexer() {}

    /** Backwards-compatible entry point used by older tests / scripts. */
    public static void run(Path indexDir, int numDocs, boolean useCompoundFile) throws IOException {
        run(indexDir, numDocs, useCompoundFile, CodecChoice.DEFAULT);
    }

    public static void run(Path indexDir, int numDocs, boolean useCompoundFile, CodecChoice codecChoice)
            throws IOException {
        Files.createDirectories(indexDir);

        Codec codec = switch (codecChoice) {
            case DEFAULT -> Codec.getDefault();
            case COPELAND -> new CopelandCodec();
        };

        System.out.println("Writing " + numDocs + " sample docs to " + indexDir.toAbsolutePath());
        System.out.println("Codec           : " + codec.getName() + " (" + codecChoice + ")");
        System.out.println("useCompoundFile : " + useCompoundFile);

        long start = System.nanoTime();

        try (Directory dir = FSDirectory.open(indexDir)) {
            IndexWriterConfig cfg = new IndexWriterConfig();
            cfg.setCodec(codec);
            cfg.setUseCompoundFile(useCompoundFile);
            cfg.setCommitOnClose(true);

            try (IndexWriter writer = new IndexWriter(dir, cfg)) {
                Random rng = new Random(42);
                long now = System.currentTimeMillis();

                for (int i = 0; i < numDocs; i++) {
                    Document doc = new Document();
                    doc.add(new StringField("id", "doc-" + i, Field.Store.YES));
                    doc.add(new SortedDocValuesField(
                            "category",
                            new BytesRef(CATEGORIES[rng.nextInt(CATEGORIES.length)])));
                    doc.add(new NumericDocValuesField(
                            "ts",
                            now - rng.nextInt(86_400_000)));
                    doc.add(new SortedNumericDocValuesField(
                            "count",
                            rng.nextInt(1_000)));
                    doc.add(new TextField("body", randomSentence(rng), Field.Store.NO));

                    writer.addDocument(doc);

                    if ((i + 1) % COMMIT_INTERVAL == 0) {
                        writer.commit();
                        System.out.println("Committed after " + (i + 1) + " docs");
                    }
                }

                writer.commit();
            }
        }

        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        System.out.println("Done in " + elapsedMs + " ms.");
    }

    private static String randomSentence(Random rng) {
        int len = 5 + rng.nextInt(15);
        StringBuilder sb = new StringBuilder(len * 8);
        for (int w = 0; w < len; w++) {
            if (w > 0) sb.append(' ');
            sb.append(WORDS[rng.nextInt(WORDS.length)]);
        }
        return sb.toString();
    }
}
