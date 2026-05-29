package dev.oddsystems.copeland;

import dev.oddsystems.copeland.tools.SampleIndexer;
import dev.oddsystems.copeland.tools.SegmentDumper;

import java.nio.file.Path;

/**
 * CopelandDB CLI entry point.
 *
 * <p>Phase 0+1 commands:
 * <ul>
 *   <li>{@code write-sample <indexDir> [numDocs] [--cfs] [--codec=default|copeland]}
 *       - write sample docs using either Lucene's default codec or
 *       {@code CopelandCodec} (Phase 1 pass-through), optionally packed
 *       into a compound file.</li>
 *   <li>{@code dump <indexDir>} - print segment + per-file inventory.</li>
 * </ul>
 */
public final class Main {

    private Main() {
    }

    static void main(String[] args) {
        try {
            dispatch(args);
        } catch (UsageError e) {
            System.err.println("usage: " + e.getMessage());
            printUsage(System.err);
            System.exit(2);
        } catch (Exception e) {
            System.err.println("error: " + e.getMessage());
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private static void dispatch(String[] args) throws Exception {
        if (args.length == 0) {
            printUsage(System.out);
            return;
        }
        switch (args[0]) {
            case "write-sample" -> runWriteSample(args);
            case "dump" -> runDump(args);
            case "-h", "--help", "help" -> printUsage(System.out);
            default -> throw new UsageError("unknown command: " + args[0]);
        }
    }

    private static void runWriteSample(String[] args) throws Exception {
        if (args.length < 2) {
            throw new UsageError("write-sample <indexDir> [numDocs] [--cfs] [--codec=default|copeland]");
        }
        Path indexDir = Path.of(args[1]);
        int numDocs = 10_000;
        boolean useCompoundFile = false;
        SampleIndexer.CodecChoice codec = SampleIndexer.CodecChoice.DEFAULT;
        for (int i = 2; i < args.length; i++) {
            String a = args[i];
            if (a.equals("--cfs")) {
                useCompoundFile = true;
            } else if (a.startsWith("--codec=")) {
                String value = a.substring("--codec=".length());
                codec = switch (value) {
                    case "default" -> SampleIndexer.CodecChoice.DEFAULT;
                    case "copeland" -> SampleIndexer.CodecChoice.COPELAND;
                    default -> throw new UsageError("unknown codec: " + value);
                };
            } else if (a.startsWith("--")) {
                throw new UsageError("unknown flag: " + a);
            } else {
                numDocs = Integer.parseInt(a);
            }
        }
        SampleIndexer.run(indexDir, numDocs, useCompoundFile, codec);
    }

    private static void runDump(String[] args) throws Exception {
        if (args.length < 2) {
            throw new UsageError("dump <indexDir>");
        }
        SegmentDumper.run(Path.of(args[1]));
    }

    private static void printUsage(java.io.PrintStream out) {
        out.println("""
                copeland - CopelandDB study CLI
                
                Commands:
                  write-sample <indexDir> [numDocs] [--cfs] [--codec=default|copeland]
                      Write sample docs to a Lucene index.
                      numDocs defaults to 10000. By default segments are written
                      with one file per format (no compound file) so that `dump`
                      reveals every file extension. Pass --cfs to enable Lucene's
                      compound-file packing (.cfs / .cfe).
                      --codec=default uses Lucene's built-in codec (Lucene104).
                      --codec=copeland uses CopelandCodec (Phase 1 pass-through).
                
                  dump <indexDir>
                      Print segment-level info and per-file sizes for a Lucene
                      index, including the codec used per segment.
                """);
    }

    private static final class UsageError extends RuntimeException {
        UsageError(String msg) {
            super(msg);
        }
    }
}
