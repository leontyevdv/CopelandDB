# CopelandDB

Study-oriented columnar analytics store built as a custom [Apache Lucene](https://lucene.apache.org/) 10.4 codec — learning page layout, encodings, pruning, and vectorized reads hands-on.

CopelandDB is a phased learning project, not a production database. Instead of wrapping an existing format (Parquet, ORC), it implements storage layer-by-layer on top of Lucene's segment and codec machinery: custom `Codec` and `DocValuesFormat`, page-level encodings, statistics and pruning, BKD range indexes, vectorized batch readers, and a small filter / project / aggregate query layer. The goal is deep understanding of tradeoffs — not benchmark wins.

See [docs/PLAN.md](docs/PLAN.md) for the full roadmap.

## Stack

- Java 26 (toolchain)
- Apache Lucene 10.4.0 (`lucene-core`, `lucene-codecs`, `lucene-backward-codecs`)

## Status

| Phase | Topic | Status |
|-------|-------|--------|
| 0 | Wire-up, segment anatomy, CLI | Done |
| 1 | `CopelandCodec` skeleton (FilterCodec pass-through) | Done |
| 2+ | Numeric DocValues, dictionary encoding, stats, vectorized reads, query layer | Planned |

## Quick start

```bash
./gradlew --quiet run --args="write-sample build/tmp/sample 10000 --codec=copeland"
./gradlew --quiet run --args="dump build/tmp/sample"
```

Run tests:

```bash
./gradlew test
```

## CLI

```
copeland write-sample <indexDir> [numDocs] [--cfs] [--codec=default|copeland]
copeland dump <indexDir>
```

- `write-sample` — index sample documents (default 10,000). Use `--codec=copeland` for the custom codec; `--codec=default` for Lucene's built-in `Lucene104` codec.
- `dump` — print segment info and per-file sizes for a Lucene index.

## Topics

`lucene` · `columnar-storage` · `analytics` · `java` · `database-internals` · `codec` · `learning-project`
