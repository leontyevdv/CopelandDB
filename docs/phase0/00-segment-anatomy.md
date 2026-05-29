# Notes — 00 Segment Anatomy

> Phase 0 study log. Run the CLI commands below and record your observations
> here. This file is the Phase 0 deliverable from
> [the plan](../PLAN.md#phase-0--wire-up-and-reconnaissance).
>
> Pair this with [learning.md](learning.md), which explains the concepts.

## 0. Tools

The Gradle [`application` plugin](../../build.gradle.kts) generates a `run` task that wires the right JVM args (`--enable-native-access=ALL-UNNAMED` for Lucene's `MMapDirectory`). All commands below go through it.

```bash
./gradlew --quiet run --args="<command and args>"
```

For a stable build/tmp scratch dir:

```bash
mkdir -p build/tmp/phase0
```

## 1. Setup

Default (per-file, no compound file) run:

```bash
./gradlew --quiet run --args="write-sample build/tmp/phase0/nocfs 10000"
./gradlew --quiet run --args="dump build/tmp/phase0/nocfs"
```

Same data, packed into a compound file:

```bash
./gradlew --quiet run --args="write-sample build/tmp/phase0/cfs 10000 --cfs"
./gradlew --quiet run --args="dump build/tmp/phase0/cfs"
```

## 2. File inventory

### 2a. No-CFS layout (10k docs)

Paste the output of `dump build/tmp/phase0/nocfs` here:

```
Index dir              : /Users/dleontyev/Documents/workspace/lab/CopelandDB/build/tmp/phase0/nocfs
Directory impl         : MMapDirectory
Generation             : 1
Commit Lucene version  : 10.4.0
Min segment version    : 10.4.0
Segments               : 1
Total maxDoc           : 10000
Index id               : 0b1809622002162700222ecba696604e

[0] Segment _0
    codec           : Lucene104
    maxDoc          : 10000
    delCount        : 0
    softDelCount    : 0
    hasDeletions    : false
    delGen          : -1
    fieldInfosGen   : -1
    docValuesGen    : -1
    sizeInBytes     : 279046
    luceneVersion   : 10.4.0 (min: 10.4.0)
    id              : 0b1809622002162700222ecba696604b
    files           :
      _0.fdm                       158 bytes  (stored fields metadata)
      _0.fdt                     44064 bytes  (stored fields data)
      _0.fdx                        93 bytes  (stored fields index)
      _0.fnm                       540 bytes  (field infos)
      _0.nvd                     10059 bytes  (norms data)
      _0.nvm                       103 bytes  (norms metadata)
      _0.si                        501 bytes  (segment info)
      _0_Lucene104_0.doc         56070 bytes  (doc/freq postings)
      _0_Lucene104_0.pos         65150 bytes  (positions)
      _0_Lucene104_0.psm           112 bytes  (postings metadata (Lucene104))
      _0_Lucene104_0.tim         45272 bytes  (term dictionary)
      _0_Lucene104_0.tip          1164 bytes  (term index)
      _0_Lucene104_0.tmd           204 bytes  (term metadata)
      _0_Lucene90_0.dvd          55117 bytes  (doc values data)
      _0_Lucene90_0.dvm            439 bytes  (doc values metadata)
    total segment   : 279046 bytes (0.27 MB)

Index total            : 279046 bytes (0.27 MB)
```

Annotate each row with the sub-format you expect produced it (cross-reference [the file extension table](learning.md#common-file-extensions-in-lucene-10x)). Note which fields in [SampleIndexer](../../src/main/java/dev/oddsystems/copeland/tools/SampleIndexer.java) feed each family.

### 2b. CFS layout (10k docs)

Paste the output of `dump build/tmp/phase0/cfs` here:

```
Index dir              : /Users/dleontyev/Documents/workspace/lab/CopelandDB/build/tmp/phase0/cfs
Directory impl         : MMapDirectory
Generation             : 1
Commit Lucene version  : 10.4.0
Min segment version    : 10.4.0
Segments               : 1
Total maxDoc           : 10000
Index id               : 9c34fc3549e5751741a5f544cb26f698

[0] Segment _0
    codec           : Lucene104
    maxDoc          : 10000
    delCount        : 0
    softDelCount    : 0
    hasDeletions    : false
    delGen          : -1
    fieldInfosGen   : -1
    docValuesGen    : -1
    sizeInBytes     : 279895
    luceneVersion   : 10.4.0 (min: 10.4.0)
    id              : 9c34fc3549e5751741a5f544cb26f695
    files           :
      _0.cfe           454 bytes  (compound file entries)
      _0.cfs        279118 bytes  (compound file data)
      _0.si            323 bytes  (segment info)
    total segment   : 279895 bytes (0.27 MB)

Index total            : 279895 bytes (0.27 MB)
```

Questions to answer:

- How many distinct files now appear in the segment directory listing? Why the difference?
- Compare `total segment` size to the no-CFS run. Are bytes saved, lost, or about the same?
- Was the `.si` file packed into the compound file or left alongside?

## 3. Sizing observations

Fill in actual numbers from your run.

| Group                           | No-CFS bytes | CFS bytes | Comment |
|---------------------------------|--------------|-----------|---------|
| Postings (`.tim/.tip/.tmd/.doc/.pos/.pay`) |              |           |         |
| Stored fields (`.fdt/.fdx/.fdm`)           |              |           |         |
| Doc values (`.dvd/.dvm`)                   |              |           |         |
| Norms (`.nvd/.nvm`)                         |              |           |         |
| Schema + manifest (`.fnm`, `.si`)          |              |           |         |
| Compound packing (`.cfs/.cfe`)             |              |           |         |
| **Total**                                  |              |           |         |

Reflection prompts:

- Which group is the biggest in our workload? Why does that make sense given how `SampleIndexer` shapes the data (a TextField with ~10 short random words plus a few small DV / stored fields)?
- The dictionary `.tim` is much smaller than `.doc`. What does that tell you about the term-vs-doc cardinality?

## 4. Codec composition

What was reported in the dump output as the codec name? (Expected: `Lucene104` in 10.4.0.)

- `codec` field per segment: Lucene104
- `Commit Lucene version` of `segments_N`: 10.4.0
- `Min segment version`: 10.4.0

Cross-check against the actual class: open `lucene-core-10.4.0-sources.jar` and read [`org.apache.lucene.codecs.lucene104.Lucene104Codec`](https://lucene.apache.org/core/10_4_0/core/org/apache/lucene/codecs/lucene104/Lucene104Codec.html). Confirm the sub-formats it composes:

| Slot                 | Class in `Lucene104Codec`                           | Files produced  |
|----------------------|-----------------------------------------------------|-----------------|
| `postingsFormat`     | `PerFieldPostingsFormat` wrapping `Lucene104PostingsFormat` | `.tim .tip .tmd .psm .doc .pos .pay` |
| `docValuesFormat`    | `PerFieldDocValuesFormat` wrapping `Lucene90DocValuesFormat` | `.dvd .dvm` |
| `storedFieldsFormat` | `Lucene90StoredFieldsFormat` (BEST_SPEED by default) | `.fdt .fdx .fdm` |
| `termVectorsFormat`  | `Lucene90TermVectorsFormat`                          | `.tvd .tvx .tvm` |
| `fieldInfosFormat`   | `Lucene94FieldInfosFormat`                           | `.fnm` |
| `segmentInfoFormat`  | `Lucene99SegmentInfoFormat`                          | `.si` |
| `liveDocsFormat`     | `Lucene90LiveDocsFormat`                             | `.liv` |
| `pointsFormat`       | `Lucene90PointsFormat`                               | `.kdd .kdi .kdm` |
| `knnVectorsFormat`   | `PerFieldKnnVectorsFormat` wrapping `Lucene99HnswVectorsFormat` | `.vec .vex .vem` (+ variants) |
| `normsFormat`        | `Lucene90NormsFormat`                                | `.nvd .nvm` |
| `compoundFormat`     | `Lucene90CompoundFormat`                             | `.cfs .cfe` |

Notice how few are actually "Lucene104"; the rest are inherited from earlier versions. This is the codec philosophy: bump only what changes.

## 5. Generations and commits

Run the multi-commit exercise (Exercise 4 in [learning.md](learning.md#10-exercises)). Then:

- How many `segments_N` files exist after the run?
- Which generations are referenced? Which are leftover candidates for `IndexDeletionPolicy`?
- How many segment families `_0`, `_1`, ... did you produce?
- Did any merges happen during ingest? How can you tell?

## 6. Force-merge

Run the force-merge exercise (Exercise 5 in [learning.md](learning.md#10-exercises)):

- File inventory before vs after:

```
TBD
```

- Total size before vs after:

```
TBD
```

- Did the `.liv` file disappear? Should it? Why?

## 7. Hex peek at `segments_N`

`hexdump -C` the `segments_N` file from your no-CFS run. Look for:

- The Lucene magic header (start of the file).
- The codec name string `Lucene104`.
- The number of segments.
- A segment's name (e.g. `_0`).

Record any specific byte offsets you find interesting. We will revisit `SegmentInfos.write` source in Phase 1.

## 8. Open questions

Keep a list as you go. These become Phase 1 / Phase 2 backlog.

- TBD
- TBD
- TBD

## 9. Phase 0 checklist

- [ ] `build.gradle.kts` builds on Java 26 with the new toolchain.
- [ ] `./gradlew run --args="write-sample ..."` produces an index.
- [ ] `./gradlew run --args="dump ..."` prints the segment + file inventory.
- [ ] No-CFS file inventory recorded above.
- [ ] CFS file inventory recorded above.
- [ ] Sizing table filled in.
- [ ] Default codec name confirmed (`Lucene104`).
- [ ] Sub-format composition mapped out by reading `Lucene104Codec` source.
- [ ] Multi-commit exercise completed.
- [ ] Force-merge exercise completed.
- [ ] `segments_N` hex peek completed.
