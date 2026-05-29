package dev.oddsystems.copeland.tools;

import org.apache.lucene.index.SegmentCommitInfo;
import org.apache.lucene.index.SegmentInfo;
import org.apache.lucene.index.SegmentInfos;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeSet;

/**
 * Phase 0 inspector.
 *
 * <p>Reads {@code segments_N} (the index manifest) for the latest commit and
 * prints:
 * <ul>
 *   <li>Index-wide metadata: generation, Lucene version, segment count, total docs.</li>
 *   <li>Per-segment metadata: codec, maxDoc, deletes, version, id.</li>
 *   <li>Every file in the segment with its size on disk and a human-readable
 *       hint about which sub-format wrote it.</li>
 * </ul>
 *
 * <p>This is the canonical Phase 0 deliverable: it makes the segment + codec
 * machinery visible without us implementing any of it yet.
 */
public final class SegmentDumper {

    private SegmentDumper() {}

    public static void run(Path indexDir) throws IOException {
        try (Directory dir = FSDirectory.open(indexDir)) {
            SegmentInfos infos = SegmentInfos.readLatestCommit(dir);

            System.out.println("Index dir              : " + indexDir.toAbsolutePath());
            System.out.println("Directory impl         : " + dir.getClass().getSimpleName());
            System.out.println("Generation             : " + infos.getGeneration());
            System.out.println("Commit Lucene version  : " + infos.getCommitLuceneVersion());
            System.out.println("Min segment version    : " + infos.getMinSegmentLuceneVersion());
            System.out.println("Segments               : " + infos.size());
            System.out.println("Total maxDoc           : " + infos.totalMaxDoc());
            System.out.println("Index id               : " + HexFormat.of().formatHex(infos.getId()));

            Map<String, String> userData = infos.getUserData();
            if (userData != null && !userData.isEmpty()) {
                System.out.println("User data              : " + userData);
            }
            System.out.println();

            long indexTotal = 0;
            int segIdx = 0;
            for (SegmentCommitInfo sci : infos) {
                SegmentInfo si = sci.info;
                System.out.println("[" + segIdx + "] Segment " + si.name);
                System.out.println("    codec           : " + si.getCodec().getName());
                System.out.println("    maxDoc          : " + si.maxDoc());
                System.out.println("    delCount        : " + sci.getDelCount());
                System.out.println("    softDelCount    : " + sci.getSoftDelCount());
                System.out.println("    hasDeletions    : " + sci.hasDeletions());
                System.out.println("    delGen          : " + sci.getDelGen());
                System.out.println("    fieldInfosGen   : " + sci.getFieldInfosGen());
                System.out.println("    docValuesGen    : " + sci.getDocValuesGen());
                System.out.println("    sizeInBytes     : " + sci.sizeInBytes());
                System.out.println("    luceneVersion   : "
                        + si.getVersion() + " (min: " + si.getMinVersion() + ")");
                System.out.println("    id              : " + HexFormat.of().formatHex(si.getId()));
                System.out.println("    files           :");

                TreeSet<String> files = new TreeSet<>(sci.files());
                int maxNameLen = files.stream().mapToInt(String::length).max().orElse(20);

                long segTotal = 0;
                for (String f : files) {
                    long sz = dir.fileLength(f);
                    segTotal += sz;
                    System.out.printf("      %-" + maxNameLen + "s  %12d bytes  (%s)%n",
                            f, sz, classify(f));
                }
                System.out.printf("    total segment   : %d bytes (%.2f MB)%n",
                        segTotal, segTotal / 1024.0 / 1024.0);

                indexTotal += segTotal;
                segIdx++;
                System.out.println();
            }

            System.out.printf("Index total            : %d bytes (%.2f MB)%n",
                    indexTotal, indexTotal / 1024.0 / 1024.0);
        }
    }

    /**
     * Maps Lucene's file extensions to a short, human-readable description.
     * Useful for Phase 0 learning; not authoritative across all codec versions.
     */
    private static String classify(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0) {
            return "?";
        }
        String ext = fileName.substring(dot + 1).toLowerCase();
        return switch (ext) {
            case "si"   -> "segment info";
            case "cfs"  -> "compound file data";
            case "cfe"  -> "compound file entries";
            case "fnm"  -> "field infos";
            case "fdt"  -> "stored fields data";
            case "fdx"  -> "stored fields index";
            case "fdm"  -> "stored fields metadata";
            case "tim"  -> "term dictionary";
            case "tip"  -> "term index";
            case "tmd"  -> "term metadata";
            case "psm"  -> "postings metadata (Lucene104)";
            case "doc"  -> "doc/freq postings";
            case "pos"  -> "positions";
            case "pay"  -> "payloads + offsets";
            case "nvd"  -> "norms data";
            case "nvm"  -> "norms metadata";
            case "dvd"  -> "doc values data";
            case "dvm"  -> "doc values metadata";
            case "kdd"  -> "BKD point data";
            case "kdi"  -> "BKD point index";
            case "kdm"  -> "BKD point metadata";
            case "liv"  -> "live docs (delete bitmap)";
            case "vec", "vex", "vem", "vemf", "vemq", "veq", "veb" -> "kNN vectors";
            case "tvd"  -> "term vectors data";
            case "tvx"  -> "term vectors index";
            case "tvm"  -> "term vectors metadata";
            default     -> "?";
        };
    }
}
