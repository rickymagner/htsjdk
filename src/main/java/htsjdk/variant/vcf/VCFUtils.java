/*
* Copyright (c) 2012 The Broad Institute
* 
* Permission is hereby granted, free of charge, to any person
* obtaining a copy of this software and associated documentation
* files (the "Software"), to deal in the Software without
* restriction, including without limitation the rights to use,
* copy, modify, merge, publish, distribute, sublicense, and/or sell
* copies of the Software, and to permit persons to whom the
* Software is furnished to do so, subject to the following
* conditions:
* 
* The above copyright notice and this permission notice shall be
* included in all copies or substantial portions of the Software.
* 
* THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
* EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
* OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
* NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
* HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
* WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
* FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR
* THE USE OR OTHER DEALINGS IN THE SOFTWARE.
*/

package htsjdk.variant.vcf;

import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.SAMSequenceRecord;
import htsjdk.tribble.TribbleException;
import htsjdk.samtools.util.FileExtensions;
import htsjdk.variant.utils.GeneralUtils;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.variantcontext.writer.Options;
import htsjdk.variant.variantcontext.writer.VariantContextWriter;
import htsjdk.variant.variantcontext.writer.VariantContextWriterBuilder;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class VCFUtils {

    private static final Pattern INF_OR_NAN_PATTERN = Pattern.compile("^(?<sign>[-+]?)((?<inf>(INF|INFINITY))|(?<nan>NAN))$", Pattern.CASE_INSENSITIVE);

    //TODO: Once we settle on the uses for this, we should determine how it gets set. For now its static/global.
    public static boolean VCF_STRICT_VERSION_VALIDATION = true;
    public static boolean VCF_VERBOSE_LOGGING = true;

    public static boolean getStrictVCFVersionValidation() { return VCF_STRICT_VERSION_VALIDATION; }
    public static boolean getVerboseVCFLogging() { return VCF_VERBOSE_LOGGING; }

    //TODO: NOTE: The old implementation of this code had side-effects due to mutation of some VCFCompoundHeaderLines
    public static Set<VCFHeaderLine> smartMergeHeaders(
            final Collection<VCFHeader> headers,
            final boolean emitWarnings) throws IllegalStateException {
        enforceHeaderVersionMergePolicy(headerVersions, source.getVCFHeaderVersion());
        return VCFHeader.getMergedHeaderLines(headers, emitWarnings);
    }


    // Reject attempts to merge a VCFv4.3 header with any other version
    private static void enforceHeaderVersionMergePolicy(
            final Set<VCFHeaderVersion> headerVersions,
            final VCFHeaderVersion candidateVersion) {
        if (candidateVersion != null) {
            headerVersions.add(candidateVersion);
            if (headerVersions.size() > 1 && headerVersions.contains(VCFHeaderVersion.VCF4_3)) {
                throw new IllegalArgumentException(
                        String.format("Attempt to merge version %s header with incompatible header version %s",
                                VCFHeaderVersion.VCF4_3.getVersionString(),
                                headerVersions.stream()
                                        .filter(hv -> !hv.equals(VCFHeaderVersion.VCF4_3))
                                        .map(VCFHeaderVersion::getVersionString)
                                        .collect(Collectors.joining(" "))));
            }
        }
    /**
     * Add / replace the contig header lines in the VCFHeader with the in the reference file and master reference dictionary
     *
     * @param oldHeader     the header to update
     * @param referenceFile the file path to the reference sequence used to generate this vcf
     * @param refDict       the SAM formatted reference sequence dictionary
     */
    public static VCFHeader withUpdatedContigs(final VCFHeader oldHeader, final File referenceFile, final SAMSequenceDictionary refDict) {
        return new VCFHeader(withUpdatedContigsAsLines(oldHeader.getMetaDataInInputOrder(), referenceFile, refDict), oldHeader.getGenotypeSamples());
    }

    public static Set<VCFHeaderLine> withUpdatedContigsAsLines(final Set<VCFHeaderLine> oldLines, final File referenceFile, final SAMSequenceDictionary refDict) {
        return withUpdatedContigsAsLines(oldLines, referenceFile, refDict, false);
    }

    public static Set<VCFHeaderLine> withUpdatedContigsAsLines(final Set<VCFHeaderLine> oldLines, final File referenceFile, final SAMSequenceDictionary refDict, final boolean referenceNameOnly) {
        final Set<VCFHeaderLine> lines = new LinkedHashSet<>(oldLines.size());

        for ( final VCFHeaderLine line : oldLines ) {
            if ( line.isStructuredHeaderLine() && line.getKey().equals(VCFConstants.CONTIG_HEADER_KEY) )
                continue; // skip old contig lines
            if (line.getKey().equals(VCFHeader.REFERENCE_KEY))
                continue; // skip the old reference key
            lines.add(line);
        }

        for (final VCFHeaderLine contigLine : makeContigHeaderLines(refDict, referenceFile))
            lines.add(contigLine);

        final String referenceValue;
        if (referenceFile != null) {
            if (referenceNameOnly) {
                final int extensionStart = referenceFile.getName().lastIndexOf('.');
                referenceValue = extensionStart == -1 ? referenceFile.getName() : referenceFile.getName().substring(0, extensionStart);
            } else {
                referenceValue = "file://" + referenceFile.getAbsolutePath();
            }
            lines.add(new VCFHeaderLine(VCFHeader.REFERENCE_KEY, referenceValue));
        }
        return lines;
    }

    /**
     * Create VCFHeaderLines for each refDict entry, and optionally the assembly if referenceFile != null
     *
     * @param refDict       reference dictionary
     * @param referenceFile for assembly name.  May be null
     * @return list of vcf contig header lines
     */
    public static List<VCFContigHeaderLine> makeContigHeaderLines(final SAMSequenceDictionary refDict,
                                                                  final File referenceFile) {
        final List<VCFContigHeaderLine> lines = new ArrayList<>();
        final String assembly = referenceFile != null ? getReferenceAssembly(referenceFile.getName()) : null;
        for ( final SAMSequenceRecord contig : refDict.getSequences() )
            lines.add(new VCFContigHeaderLine(contig, assembly));
        return lines;
    }

    @Deprecated
    private static VCFContigHeaderLine makeContigHeaderLine(final SAMSequenceRecord contig, final String assembly) {
        return new VCFContigHeaderLine(contig, assembly);
    }

    /**
     * This method creates a temporary VCF file and its appropriately named index file, and will delete them on exit.
     *
     * @param prefix - The prefix string to be used in generating the file's name; must be at least three characters long
     * @param suffix - The suffix string to be used in generating the file's name; may be null, in which case the suffix ".tmp" will be used
     * @return A File object referencing the newly created temporary VCF file
     * @throws IOException - if a file could not be created.
     */
    public static File createTemporaryIndexedVcfFile(final String prefix, final String suffix) throws IOException {
        final File out = File.createTempFile(prefix, suffix);
        out.deleteOnExit();
        String indexFileExtension = null;
        if (suffix.endsWith(FileExtensions.COMPRESSED_VCF)) {
            indexFileExtension = FileExtensions.COMPRESSED_VCF_INDEX;
        } else if (suffix.endsWith(FileExtensions.VCF)) {
            indexFileExtension = FileExtensions.VCF_INDEX;
        }
        if (indexFileExtension != null) {
            final File indexOut = new File(out.getAbsolutePath() + indexFileExtension);
            indexOut.deleteOnExit();
        }
        return out;
    }

    /**
     * This method makes a copy of the input VCF and creates an index file for it in the same location.
     * This is done so that we don't need to store the index file in the same repo
     * The copy of the input is done so that it and its index are in the same directory which is typically required.
     *
     * @param vcfFile the vcf file to index
     * @return File a vcf file (index file is created in same path).
     */
    public static File createTemporaryIndexedVcfFromInput(final File vcfFile, final String tempFilePrefix) throws IOException {
        final String extension;

        if (vcfFile.getAbsolutePath().endsWith(FileExtensions.VCF)) extension = FileExtensions.VCF;
        else if (vcfFile.getAbsolutePath().endsWith(FileExtensions.COMPRESSED_VCF))
            extension = FileExtensions.COMPRESSED_VCF;
        else
            throw new IllegalArgumentException("couldn't find a " + FileExtensions.VCF + " or " + FileExtensions.COMPRESSED_VCF + " ending for input file " + vcfFile.getAbsolutePath());

        File output = createTemporaryIndexedVcfFile(tempFilePrefix, extension);

        try (final VCFFileReader in = new VCFFileReader(vcfFile, false);
             final VariantContextWriter out = new VariantContextWriterBuilder().
                     setReferenceDictionary(in.getFileHeader().getSequenceDictionary()).
                     setOptions(EnumSet.of(Options.INDEX_ON_THE_FLY)).
                     setOutputFile(output).build()) {
            out.writeHeader(in.getFileHeader());
            for (final VariantContext ctx : in) {
                out.add(ctx);
            }
        }

        return output;
    }

    /**
     * Parses a String as a Double, being tolerant for case-insensitive NaN and Inf/Infinity.
     */
    public static double parseVcfDouble(final String str) {
        try {
            return Double.parseDouble(str);
        } catch (NumberFormatException e) {
            final Matcher matcher = INF_OR_NAN_PATTERN.matcher(str);
            if (matcher.matches()) {
                final double ret;
                if (matcher.group("inf") == null) {
                    ret = Double.NaN;
                } else {
                    if (matcher.group("sign").equals("-")) {
                        ret = Double.NEGATIVE_INFINITY;
                    } else {
                        ret = Double.POSITIVE_INFINITY;
                    }
                }
                return ret;
            }
            throw e;
        }
    }

    private static String getReferenceAssembly(final String refPath) {
        // This doesn't need to be perfect as it's not a required VCF header line, but we might as well give it a shot
        String assembly = null;
        if (refPath.contains("b37") || refPath.contains("v37"))
            assembly = "b37";
        else if (refPath.contains("b36"))
            assembly = "b36";
        else if (refPath.contains("hg18"))
            assembly = "hg18";
        else if (refPath.contains("hg19"))
            assembly = "hg19";
        else if (refPath.contains("hg38"))
            assembly = "hg38";
        return assembly;
    }

}
