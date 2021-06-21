package htsjdk.beta.codecs.reads.cram;

import htsjdk.beta.plugin.bundle.Bundle;
import htsjdk.beta.plugin.bundle.BundleResourceType;
import htsjdk.beta.plugin.reads.ReadsEncoderOptions;
import htsjdk.beta.plugin.reads.ReadsFormat;
import htsjdk.beta.plugin.reads.ReadsEncoder;
import htsjdk.samtools.cram.ref.CRAMReferenceSource;
import htsjdk.samtools.cram.ref.ReferenceSource;

import java.util.Optional;

/**
 * Base class for CRAM encoders.
 */
public abstract class CRAMEncoder implements ReadsEncoder {
    // TODO: presorted
    protected final Bundle outputBundle;
    protected final ReadsEncoderOptions readsEncoderOptions;
    private final String displayName;

    public CRAMEncoder(final Bundle outputBundle, final ReadsEncoderOptions readsEncoderOptions) {
        this.outputBundle = outputBundle;
        this.readsEncoderOptions = readsEncoderOptions;
        this.displayName = outputBundle.getOrThrow(BundleResourceType.READS).getDisplayName();
    }

    @Override
    final public ReadsFormat getFormat() { return ReadsFormat.CRAM; }

    @Override
    final public String getDisplayName() { return displayName; }

    protected CRAMReferenceSource getCRAMReferenceSource() {
        final Optional<CRAMEncoderOptions> optCRAMEncoderOptions = readsEncoderOptions.getCRAMEncoderOptions();
        if (optCRAMEncoderOptions.isPresent()) {
            final CRAMEncoderOptions cramEncoderOptions = optCRAMEncoderOptions.get();
            if (cramEncoderOptions.getReferenceSource().isPresent()) {
                return cramEncoderOptions.getReferenceSource().get();
            } else if (cramEncoderOptions.getReferencePath().isPresent()) {
                return CRAMCodec.getCRAMReferenceSource(cramEncoderOptions.getReferencePath().get());
            }
        }
        // if none is specified, get the default "lazy" reference source that throws when queried, to allow
        // operations that don't require a reference
        return ReferenceSource.getDefaultCRAMReferenceSource();
    }
}