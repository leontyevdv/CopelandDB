# Notes — 01 Codec Skeleton

> Phase 1 study log. Run the CLI commands below and record your observations
> here. This file is the human-facing counterpart to the Phase 1 deliverable
> ([`CopelandCodecRoundtripTest`](../../src/test/java/dev/oddsystems/copeland/codec/CopelandCodecRoundtripTest.java))
> referenced by [the plan](../PLAN.md#phase-1--copelandcodec-skeleton-filtercodec-pass-through).
>
> Pair this with [learning.md](learning.md), which explains the concepts.

## 0. Tools

Same setup as Phase 0. Everything goes through the `application`-plugin task:

```bash
./gradlew --quiet run --args="<command and args>"
```

Scratch directory for this phase:

```bash
mkdir -p build/tmp/phase1
```

## 1. Setup

Write the same 10k-doc workload twice, once with each codec:

```bash
./gradlew --quiet run --args="write-sample build/tmp/phase1/default  10000"
./gradlew --quiet run --args="write-sample build/tmp/phase1/copeland 10000 --codec=copeland"
```

Then dump both:

```bash
./gradlew --quiet run --args="dump build/tmp/phase1/default"
./gradlew --quiet run --args="dump build/tmp/phase1/copeland"
```

## 2. The single-line diff

Diff the dump outputs (sizes will match modulo random data; the *structure*
should be identical):

```bash
diff <(./gradlew --quiet run --args="dump build/tmp/phase1/default")  \
     <(./gradlew --quiet run --args="dump build/tmp/phase1/copeland")
```

- The codec name on each segment changes from `Lucene104` to `Copeland001`.
  Anything else? `TBD`
- Per-field filenames (`_0_Lucene104_0.doc`, `_0_Lucene90_0.dvd`) are
  identical between the two runs. Why? `TBD`
  (Hint: who actually wrote those files? The codec name lives in `.si`,
  the per-field format names live inside `PerField*Format` wrappers.)

## 3. The `.si` byte

Find the codec name in the segment-info file:

```bash
hexdump -C build/tmp/phase1/copeland/_0.si | head -20
```

- Offset where `Copeland001` first appears: `TBD`
- Compare with `build/tmp/phase1/default/_0.si`. Where does `Lucene104`
  appear?  `TBD`
- How many bytes are spent on the codec name in each case?  `TBD`

(Bonus: the codec name is written by `Lucene99SegmentInfoFormat` using
`CodecUtil.writeHeader(...)` + the codec name as a `writeString`. Locate
that in `lucene-core-10.4.0-sources.jar` and confirm the offsets you
saw match the layout.)

## 4. SPI lookup, programmatically

The roundtrip test exercises this for you. Run just the SPI-related tests:

```bash
./gradlew test --tests \
  dev.oddsystems.copeland.codec.CopelandCodecRoundtripTest.copelandCodec_is_discoverable_via_spi
./gradlew test --tests \
  dev.oddsystems.copeland.codec.CopelandCodecRoundtripTest.available_codecs_listing_contains_copeland
```

Both pass: `TBD` (yes/no, and any observations).

## 5. Break SPI on purpose

Temporarily disable the SPI file:

```bash
mv src/main/resources/META-INF/services/org.apache.lucene.codecs.Codec{,.disabled}
./gradlew test --tests dev.oddsystems.copeland.codec.CopelandCodecRoundtripTest
```

- Which test fails first? `TBD`
- Which exception type does Lucene throw? `TBD`
- Does the test that uses an explicit `new CopelandCodec()` write
  succeed but read fail, or both fail? `TBD`

Restore:

```bash
mv src/main/resources/META-INF/services/org.apache.lucene.codecs.Codec{.disabled,}
```

## 6. Break the codec name

Change `Copeland001` to `copeland001` (lowercase) in
[CopelandCodec.java](../../src/main/java/dev/oddsystems/copeland/codec/CopelandCodec.java),
re-run the tests:

- What fails? `TBD`
- Does Lucene complain at SPI registration time, or only when something
  asks for the old name? `TBD`

Revert the change.

## 7. Per-field routing

Open a sources jar viewer on
`org/apache/lucene/codecs/lucene104/Lucene104Codec.java`. Confirm:

- `docValuesFormat()` returns a `PerFieldDocValuesFormat` (not a
  `Lucene90DocValuesFormat` directly).
- `postingsFormat()` returns a `PerFieldPostingsFormat`.
- The wrappers delegate to `getDocValuesFormatForField` /
  `getPostingsFormatForField`, which by default return the codec's
  single configured format.

How does Lucene know *which* format wrote a given field in a segment we
later open?
- Field-info file `.fnm` carries a per-field attribute:
  `PerFieldDocValuesFormat.format` -> the chosen format's name.
- Per-field segment filenames embed the format name (the middle token).

`TBD` — verify by reading the relevant code paths in
`Lucene94FieldInfosFormat` and `PerFieldDocValuesFormat$FieldsReader`.

## 8. Sizing observations

Re-run [Phase 0's sizing exercise](../phase0/00-segment-anatomy.md#3-sizing-observations)
side-by-side for both codecs. Expectation: **identical**, byte for byte,
because we delegate everything.

| Group                                       | Default bytes | Copeland bytes | Delta |
|---------------------------------------------|---------------|----------------|-------|
| Postings (`.tim/.tip/.tmd/.psm/.doc/.pos/.pay`) |               |                |       |
| Stored fields (`.fdt/.fdx/.fdm`)                |               |                |       |
| Doc values (`.dvd/.dvm`)                        |               |                |       |
| Norms (`.nvd/.nvm`)                              |               |                |       |
| Schema + manifest (`.fnm`, `.si`)               |               |                |       |
| **Total**                                       |               |                |       |

If the totals differ by even a single byte, something is off — likely a
buffered RNG state difference in `SampleIndexer` between the two runs.
(Both use seed `42` and the same field ordering, so this should not
happen in practice.)

## 9. Decisions log

Decisions made during Phase 1, kept here for cross-reference:

- **Codec name:** `Copeland001`. Bumped on any incompatible on-disk
  change.
- **Base class:** `FilterCodec` (not `Lucene104Codec`). Switch only when
  Phase 2's per-field routing arrives.
- **No-arg constructor:** required by `ServiceLoader`. Delegate
  instantiated directly with `new Lucene104Codec()` per Lucene's
  documented constructor caveat.
- **No `module-info.java`** in Phase 1. Classpath + `META-INF/services`
  is enough; we revisit if/when we adopt the module path.

## 10. Open questions

Backlog for later phases:

- TBD
- TBD
- TBD

## 11. Phase 1 checklist

- [ ] `Codec.forName("Copeland001")` returns a `CopelandCodec` instance.
- [ ] `Codec.availableCodecs()` contains `Copeland001`.
- [ ] `write-sample build/tmp/phase1/copeland --codec=copeland` produces
      a segment with `.si` codec `Copeland001`.
- [ ] `dump` of that segment confirms `codec : Copeland001` while per-field
      filenames are unchanged.
- [ ] `CopelandCodecRoundtripTest` is green (4 tests).
- [ ] Hex-peek into `.si` finds the literal byte string `Copeland001`.
- [ ] Multi-segment, multi-commit ingest (from Phase 0 exercise) still
      works under `--codec=copeland`.
- [ ] "Break SPI" exercise produces the expected failure mode.
- [ ] "Break codec name" exercise produces the expected failure mode.
