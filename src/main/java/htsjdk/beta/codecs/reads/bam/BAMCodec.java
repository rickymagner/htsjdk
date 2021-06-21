package htsjdk.beta.codecs.reads.bam;

import htsjdk.io.IOPath;
import htsjdk.beta.plugin.HtsVersion;
import htsjdk.beta.plugin.reads.ReadsCodec;
import htsjdk.beta.plugin.reads.ReadsFormat;
import htsjdk.samtools.util.FileExtensions;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Base class for BAM codecs.
 */
public abstract class BAMCodec implements ReadsCodec {
    public static final HtsVersion BAM_DEFAULT_VERSION = new HtsVersion(1, 0,0);

    private static final Set<String> extensionMap = new HashSet(Arrays.asList(FileExtensions.BAM));

    @Override
    public ReadsFormat getFileFormat() { return ReadsFormat.BAM; }

    @Override
    public boolean canDecodeURI(final IOPath ioPath) {
        return extensionMap.stream().anyMatch(ext-> ioPath.hasExtension(ext));
    }

}