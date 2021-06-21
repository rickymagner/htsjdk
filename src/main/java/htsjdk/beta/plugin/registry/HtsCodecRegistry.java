package htsjdk.beta.plugin.registry;

import htsjdk.beta.plugin.HtsCodec;
import htsjdk.beta.plugin.bundle.BundleResourceType;
import htsjdk.beta.plugin.hapref.HaploidReferenceCodec;
import htsjdk.beta.plugin.hapref.HaploidReferenceFormat;

import htsjdk.beta.plugin.reads.ReadsCodec;
import htsjdk.beta.plugin.reads.ReadsFormat;

import htsjdk.beta.plugin.variants.VariantsCodec;
import htsjdk.beta.plugin.variants.VariantsFormat;

import java.util.*;

//TODO: Master TODO list:
// - fix CRAM reference leak issue
// - implement index creation on decoders (existing inputs), on buffered stream
// - unify/clarify exception types
// - resolve/clarify/rename/document the canDecodeURI/canDecodeSignature protocol
//      document how to implement codecs that need to see the stream (can't deterministically tell from the extension)
// - rename the packages classes for codecs to reflect the interfaces they provide (i.e., a "READS" codec
//      is a codec that exposes SAMFileHeader/SAMRecord). Someday when we replace those, we'll need a new
//      name that contrasts with the current name i.e. READS2
// clarify ownership of stream (ie, streams that are passed in are closed ? but we can't close
//      output streams that are passed in ??)
// - add a "PublicAPI" opt-in annotation to exempt internal methods that need to be public because
//      they're shared from being part of the public API
// - encryption/decryption key files, etc.
// - implement a built-in cloud channel wrapper and replace the lambdas currently exposed as options
// - Incomplete:
//      VCF, FASTA codecs
// - Missing:
//      SAM codec
//      CRAM 2.1 codec
//      BCF codec
// javadoc
// tests
// - fix CRAM codec access to the eliminate FastaDecoder getReferenceSequenceFile accessor
// - prevent the decoders that delegate to SamReaderFactory from attempting to automatically
//      resolve index files so we don't introduce incompatibilities when the SamReaderFactory
//      implementation dependency is removed
// - respect presorted in Reads encoders
// - publish the JSON Bundle JSON schema
// - upgrade API
// - support/test stdin/stdout

/**
 * Registry/cache for binding to encoders/decoders.
 */
@SuppressWarnings("rawtypes")
public class HtsCodecRegistry {
    private static final HtsCodecRegistry htsCodecRegistry = new HtsCodecRegistry();

    // for each codec type, keep a map of codec instances, by format and version
    private static HtsCodecResolver<HaploidReferenceFormat, HaploidReferenceCodec> haprefCodecResolver =
            new HtsCodecResolver<>(
                    BundleResourceType.HAPLOID_REFERENCE,
                    HaploidReferenceFormat::contentSubTypeToFormat);
    private static HtsCodecResolver<ReadsFormat, ReadsCodec> readsCodecResolver =
            new HtsCodecResolver<>(
                    BundleResourceType.READS,
                    ReadsFormat::contentSubTypeToFormat);
    private static HtsCodecResolver<VariantsFormat, VariantsCodec> variantsCodecResolver =
            new HtsCodecResolver<>(
                    BundleResourceType.VARIANTS,
                    VariantsFormat::contentSubTypeToFormat);

    //discover any codecs on the classpath
    static { ServiceLoader.load(HtsCodec.class).forEach(htsCodecRegistry::registerCodec); }

    private HtsCodecRegistry() {}

    /**
     * Add a codec to the registry
     */
    public HtsCodec<?, ?, ?> registerCodec(final HtsCodec<?, ?, ?> codec) {
        switch (codec.getCodecType()) {
            case ALIGNED_READS:
                return readsCodecResolver.registerCodec((ReadsCodec) codec);

            case HAPLOID_REFERENCE:
                return haprefCodecResolver.registerCodec((HaploidReferenceCodec) codec);

            case VARIANTS:
                return variantsCodecResolver.registerCodec((VariantsCodec) codec);

            case FEATURES:
                throw new RuntimeException("Features codec type not yet implemented");

            default:
                throw new IllegalArgumentException("Unknown codec type");
        }
    }

    public static HtsCodecResolver<ReadsFormat, ReadsCodec> getReadsCodecResolver() {
        return readsCodecResolver;
    }

    public static HtsCodecResolver<VariantsFormat, VariantsCodec> getVariantsCodecResolver() {
        return variantsCodecResolver;
    }

    public static HtsCodecResolver<HaploidReferenceFormat, HaploidReferenceCodec> getHapRefCodecResolver() {
        return haprefCodecResolver;
    }

}
