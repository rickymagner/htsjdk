package htsjdk.beta.codecs.hapref.fasta;

import htsjdk.beta.plugin.bundle.Bundle;
import htsjdk.beta.plugin.bundle.BundleResource;
import htsjdk.beta.plugin.bundle.BundleResourceType;
import htsjdk.beta.plugin.HtsVersion;
import htsjdk.beta.plugin.hapref.HaploidReferenceDecoder;
import htsjdk.beta.plugin.hapref.HaploidReferenceFormat;
import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.reference.ReferenceSequence;
import htsjdk.samtools.reference.ReferenceSequenceFile;
import htsjdk.samtools.reference.ReferenceSequenceFileFactory;
import htsjdk.samtools.seekablestream.SeekableStream;
import htsjdk.samtools.util.CloseableIterator;
import htsjdk.samtools.util.RuntimeIOException;

import java.io.IOException;

/**
 * A FASTA file decoder.
 */
public class FASTADecoderV1_0 implements HaploidReferenceDecoder {
    protected Bundle haprefBundle;
    private final String displayName;

    @Override
    public String getDisplayName() { return displayName; }

    private final ReferenceSequenceFile referenceSequenceFile;

    public FASTADecoderV1_0(final Bundle inputBundle) {
        this.haprefBundle = inputBundle;
        this.displayName = inputBundle.getPrimaryResource().getDisplayName();
        final BundleResource referenceResource = inputBundle.getOrThrow(BundleResourceType.HAPLOID_REFERENCE);
        if (referenceResource.getIOPath().isPresent()) {
            referenceSequenceFile = ReferenceSequenceFileFactory.getReferenceSequenceFile(
                    referenceResource.getIOPath().get().toPath(), true);
        } else {
            final SeekableStream seekableStream = referenceResource.getSeekableStream().orElseThrow(
                    () -> new IllegalArgumentException(
                            String.format("The reference resource %s is not able to supply the required seekable stream",
                                    referenceResource.getDisplayName())));
            referenceSequenceFile = ReferenceSequenceFileFactory.getReferenceSequenceFile(
                    referenceResource.getDisplayName(),
                    seekableStream,
                    null
            );
        }
    }

    @Override
    final public HaploidReferenceFormat getFormat() { return HaploidReferenceFormat.FASTA; }

    @Override
    public SAMSequenceDictionary getHeader() {
        throw new IllegalStateException("Not implemented");
    }

    @Override
    public HtsVersion getVersion() {
        return FASTACodecV1_0.VERSION_1;
    }

    @Override
    public CloseableIterator<ReferenceSequence> iterator() {
        referenceSequenceFile.reset();
        return new CloseableIterator<ReferenceSequence>() {
            ReferenceSequence nextSeq = referenceSequenceFile.nextSequence();

            @Override
            public boolean hasNext() {
                return nextSeq != null;
            }

            @Override
            public ReferenceSequence next() {
                final ReferenceSequence tmpSeq = nextSeq;
                nextSeq = referenceSequenceFile.nextSequence();
                return tmpSeq;
            }

            @Override
            public void close() {
                try {
                    referenceSequenceFile.close();
                } catch(final IOException e) {
                    throw new RuntimeIOException(e);
                }
            }
        };
    }

    @Override
    public boolean isQueryable() {
        throw new IllegalStateException("Not implemented");
    }

    @Override
    public boolean hasIndex() {
        throw new IllegalStateException("Not implemented");
    }

    //TODO: can we remove the need to have this getter; it may require porting CRAMFileReader
    // over so that it uses the new API
    public ReferenceSequenceFile getReferenceSequenceFile() {
        return referenceSequenceFile;
    }

    @Override
    public void close() {
        if (referenceSequenceFile != null) {
            try {
                referenceSequenceFile.close();
            } catch (IOException e) {
                throw new RuntimeIOException(e);
            }
        }
    }

}