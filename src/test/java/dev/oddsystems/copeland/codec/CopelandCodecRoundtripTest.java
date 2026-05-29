package dev.oddsystems.copeland.codec;

import org.apache.lucene.codecs.Codec;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.SortedNumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.index.SegmentReader;
import org.apache.lucene.index.SortedDocValues;
import org.apache.lucene.index.SortedNumericDocValues;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.BytesRef;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 1 deliverable: full write/read roundtrip with the Copeland codec.
 *
 * <p>Verifies four things at once:
 * <ol>
 *   <li>SPI registration works: {@code Codec.forName("Copeland001")} returns
 *       an instance of {@link CopelandCodec}. This proves the
 *       {@code META-INF/services/org.apache.lucene.codecs.Codec} file is
 *       picked up by Lucene's {@code NamedSPILoader}.</li>
 *   <li>The codec name flows end-to-end: a segment written with
 *       {@code CopelandCodec} reports its codec name on read via
 *       {@code SegmentReader.getSegmentInfo().info.getCodec().getName()}.</li>
 *   <li>The pass-through is real: every doc-values family (numeric, sorted,
 *       sorted-numeric), stored fields, and inverted-index lookup roundtrip
 *       correctly through {@code CopelandCodec}.</li>
 *   <li>Lucene can find {@code CopelandCodec} purely from the SPI registry
 *       when opening a segment that recorded "Copeland001" in its
 *       {@code .si} - i.e. we never hand the reader a codec instance.</li>
 * </ol>
 */
class CopelandCodecRoundtripTest {

    @Test
    void copelandCodec_is_discoverable_via_spi() {
        Codec codec = Codec.forName(CopelandCodec.NAME);
        assertNotNull(codec, "Codec.forName(" + CopelandCodec.NAME + ") returned null");
        assertInstanceOf(CopelandCodec.class, codec,
                "expected CopelandCodec, got " + codec.getClass().getName());
        assertEquals(CopelandCodec.NAME, codec.getName());
    }

    @Test
    void available_codecs_listing_contains_copeland() {
        assertTrue(Codec.availableCodecs().contains(CopelandCodec.NAME),
                "Codec.availableCodecs() did not include " + CopelandCodec.NAME
                        + ". Actual: " + Codec.availableCodecs());
    }

    @Test
    void write_then_read_roundtrip_through_copeland() throws Exception {
        try (Directory dir = new ByteBuffersDirectory()) {
            writeSampleSegment(dir, 25);
            assertRoundtrip(dir, 25);
        }
    }

    private static void writeSampleSegment(Directory dir, int numDocs) throws Exception {
        IndexWriterConfig cfg = new IndexWriterConfig();
        cfg.setCodec(new CopelandCodec());
        cfg.setUseCompoundFile(false);
        cfg.setCommitOnClose(true);

        try (IndexWriter writer = new IndexWriter(dir, cfg)) {
            for (int i = 0; i < numDocs; i++) {
                Document doc = new Document();
                doc.add(new StringField("id", "doc-" + i, Field.Store.YES));
                doc.add(new TextField("body", "alpha bravo " + i, Field.Store.NO));
                doc.add(new SortedDocValuesField("category", new BytesRef("cat-" + (i % 3))));
                doc.add(new NumericDocValuesField("ts", 1_000L + i));
                doc.add(new SortedNumericDocValuesField("count", i));
                doc.add(new SortedNumericDocValuesField("count", i * 2L));
                writer.addDocument(doc);
            }
            writer.commit();
        }
    }

    private static void assertRoundtrip(Directory dir, int expectedDocs) throws Exception {
        try (DirectoryReader reader = DirectoryReader.open(dir)) {
            assertEquals(expectedDocs, reader.numDocs(), "numDocs");
            assertEquals(1, reader.leaves().size(), "expected a single segment");

            LeafReaderContext ctx = reader.leaves().get(0);
            SegmentReader sr = assertInstanceOf(SegmentReader.class, ctx.reader(),
                    "expected SegmentReader");

            String codecName = sr.getSegmentInfo().info.getCodec().getName();
            assertEquals(CopelandCodec.NAME, codecName, "segment codec name");

            StoredFields storedFields = ctx.reader().storedFields();
            NumericDocValues ts = ctx.reader().getNumericDocValues("ts");
            SortedDocValues cat = ctx.reader().getSortedDocValues("category");
            SortedNumericDocValues counts = ctx.reader().getSortedNumericDocValues("count");

            assertNotNull(ts);
            assertNotNull(cat);
            assertNotNull(counts);

            for (int i = 0; i < expectedDocs; i++) {
                Document stored = storedFields.document(i);
                assertEquals("doc-" + i, stored.get("id"), "stored id roundtrip at doc " + i);

                assertTrue(ts.advanceExact(i), "ts dv missing at doc " + i);
                assertEquals(1_000L + i, ts.longValue(), "ts dv value at doc " + i);

                assertTrue(cat.advanceExact(i), "category dv missing at doc " + i);
                assertEquals("cat-" + (i % 3), cat.lookupOrd(cat.ordValue()).utf8ToString(),
                        "category dv value at doc " + i);

                assertTrue(counts.advanceExact(i), "count dv missing at doc " + i);
                assertEquals(2, counts.docValueCount(), "count dv arity at doc " + i);
                long first = counts.nextValue();
                long second = counts.nextValue();
                assertEquals(i, first, "count[0] at doc " + i);
                assertEquals(i * 2L, second, "count[1] at doc " + i);
            }

            IndexSearcher searcher = new IndexSearcher(reader);
            TopDocs hits = searcher.search(new TermQuery(new Term("body", "alpha")), 10);
            assertEquals(expectedDocs, hits.totalHits.value(),
                    "term 'alpha' should match every doc");
        }
    }

    @Test
    void read_only_via_spi_after_write() throws Exception {
        try (Directory dir = new ByteBuffersDirectory()) {
            writeSampleSegment(dir, 5);

            try (DirectoryReader reader = DirectoryReader.open(dir)) {
                LeafReaderContext ctx = reader.leaves().get(0);
                SegmentReader sr = (SegmentReader) ctx.reader();
                Codec resolved = sr.getSegmentInfo().info.getCodec();

                assertEquals(CopelandCodec.NAME, resolved.getName());
                assertAll("codec resolved via SPI from .si",
                        () -> assertInstanceOf(CopelandCodec.class, resolved),
                        () -> assertNotNull(resolved.docValuesFormat()),
                        () -> assertNotNull(resolved.postingsFormat()),
                        () -> assertNotNull(resolved.storedFieldsFormat()),
                        () -> assertNotNull(resolved.segmentInfoFormat()),
                        () -> assertNotNull(resolved.liveDocsFormat()),
                        () -> assertNotNull(resolved.pointsFormat()),
                        () -> assertNotNull(resolved.knnVectorsFormat()),
                        () -> assertNotNull(resolved.normsFormat()),
                        () -> assertNotNull(resolved.compoundFormat()),
                        () -> assertNotNull(resolved.termVectorsFormat()),
                        () -> assertNotNull(resolved.fieldInfosFormat()));
            }
        }
    }
}
