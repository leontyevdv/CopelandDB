package dev.oddsystems.copeland.codec;

import org.apache.lucene.codecs.Codec;
import org.apache.lucene.codecs.FilterCodec;
import org.apache.lucene.codecs.lucene104.Lucene104Codec;

/**
 * Phase 1 codec.
 *
 * <p>{@code CopelandCodec} is a pure pass-through over Lucene's default
 * 10.4 codec. Every {@code *Format()} accessor inherits from {@link FilterCodec}
 * and forwards to the wrapped {@link Lucene104Codec}. The point of Phase 1 is
 * to make the SPI plumbing visible: registering a named codec, getting it
 * loaded via {@link Codec#forName(String)} / {@link java.util.ServiceLoader},
 * and seeing the codec name appear in {@code .si} segment manifests.
 *
 * <h2>SPI registration</h2>
 * <p>
 * This class is discovered through
 * {@code META-INF/services/org.apache.lucene.codecs.Codec}. The codec name
 * <strong>must</strong> match the SPI registration string and the value
 * returned by {@link Codec#getName()} (set via {@code super(name, delegate)}
 * below).
 *
 * <h2>Why a unique name?</h2>
 * <p>
 * Lucene's {@code NamedSPILoader} indexes codecs by name. Two codecs with the
 * same name on the classpath are a hard failure at SPI load time. We pick
 * {@code "Copeland001"} so we can ship a new on-disk format under
 * {@code "Copeland002"} later without breaking old segments: future readers
 * can still load {@code Copeland001} via SPI as long as the class stays on
 * the classpath, which is exactly how {@code lucene-backward-codecs} works.
 *
 * <h2>Why not extend {@link Lucene104Codec} directly?</h2>
 * <p>
 * Subclassing {@code Lucene104Codec} exposes the
 * {@code getPostingsFormatForField} / {@code getDocValuesFormatForField}
 * hooks that the codec uses internally to route per-field formats. That is
 * a fine pattern for Phase 2+ when we start swapping in our own
 * {@code DocValuesFormat}. For Phase 1, {@link FilterCodec} is the cleaner
 * "pure delegation" base since it forwards every sub-format method
 * explicitly. We will revisit this choice when we plug in Copeland-specific
 * formats.
 *
 * <h2>Constructor caveat (per Lucene)</h2>
 * <p>
 * Lucene's {@link FilterCodec} javadoc explicitly warns: do <em>not</em>
 * call {@link Codec#forName(String)} from a no-arg constructor, because
 * the SPI loader has not finished initializing when this class is
 * instantiated as a service. We construct the delegate directly via
 * {@code new Lucene104Codec()}, which is the documented pattern.
 */
public final class CopelandCodec extends FilterCodec {

    /**
     * The canonical name for the Phase 1 codec. Bumped (Copeland002,
     * Copeland003, ...) when we change anything an existing segment depends
     * on - file format, encoding, page layout, etc.
     */
    public static final String NAME = "Copeland001";

    public CopelandCodec() {
        super(NAME, new Lucene104Codec());
    }
}
