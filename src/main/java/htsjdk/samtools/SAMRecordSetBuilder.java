/*
 * The MIT License
 *
 * Copyright (c) 2009 The Broad Institute
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package htsjdk.samtools;

import htsjdk.samtools.DuplicateScoringStrategy.ScoringStrategy;
import htsjdk.samtools.SAMReadGroupRecord.PlatformValue;
import htsjdk.samtools.reference.FastaReferenceWriter;
import htsjdk.samtools.reference.FastaReferenceWriterBuilder;
import htsjdk.samtools.util.CloseableIterator;
import htsjdk.samtools.util.RuntimeIOException;
import htsjdk.samtools.util.SequenceUtil;
import htsjdk.samtools.util.TestUtil;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;


/**
 * Factory class for creating SAMRecords for testing purposes. Various methods can be called
 * to add new SAM records (or pairs of records) to a list which can then be returned at
 * any point. The records must reference human chromosomes (excluding randoms etc.).
 * <p/>
 * Although this is a class for testing, it is in the src tree because it is included in the sam jarfile.
 *
 * @author Tim Fennell
 */
public class SAMRecordSetBuilder implements Iterable<SAMRecord> {
    private static final String[] chroms = {
            "chr1", "chr2", "chr3", "chr4", "chr5", "chr6", "chr7", "chr8", "chr9", "chr10",
            "chr11", "chr12", "chr13", "chr14", "chr15", "chr16", "chr17", "chr18", "chr19", "chr20",
            "chr21", "chr22", "chrX", "chrY", "chrM"
    };

    private static final String READ_GROUP_ID = "1";
    private static final String SAMPLE = "FREE_SAMPLE";
    private final Random random = new Random(TestUtil.RANDOM_SEED);

    private SAMFileHeader header;
    private final Collection<SAMRecord> records;

    private int readLength = 36;

    private boolean useBamFile = true;

    private SAMProgramRecord programRecord = null;
    private SAMReadGroupRecord readGroup = null;
    private boolean useNmFlag = false;

    private boolean unmappedHasBasesAndQualities = true;

    public static final int DEFAULT_CHROMOSOME_LENGTH = 200_000_000;

    public static final ScoringStrategy DEFAULT_DUPLICATE_SCORING_STRATEGY = ScoringStrategy.TOTAL_MAPPED_REFERENCE_LENGTH;

    /**
     * Constructs a new SAMRecordSetBuilder with all the data needed to keep the records
     * sorted in coordinate order.
     */
    public SAMRecordSetBuilder() {
        this(true, SAMFileHeader.SortOrder.coordinate);
    }

    /**
     * Construct a new SAMRecordSetBuilder.
     *
     * @param sortOrder If sortForMe, defines the sort order.
     * @param sortForMe If true, keep the records created in sorted order.
     */
    public SAMRecordSetBuilder(final boolean sortForMe, final SAMFileHeader.SortOrder sortOrder) {
        this(sortForMe, sortOrder, true);
    }

    public SAMRecordSetBuilder(final boolean sortForMe, final SAMFileHeader.SortOrder sortOrder, final boolean addReadGroup) {
        this(sortForMe, sortOrder, addReadGroup, DEFAULT_CHROMOSOME_LENGTH);
    }

    public SAMRecordSetBuilder(final boolean sortForMe, final SAMFileHeader.SortOrder sortOrder, final boolean addReadGroup, final int defaultChromosomeLength) {
        this(sortForMe, sortOrder, addReadGroup, defaultChromosomeLength, DEFAULT_DUPLICATE_SCORING_STRATEGY);
    }

    public SAMRecordSetBuilder(final boolean sortForMe, final SAMFileHeader.SortOrder sortOrder, final boolean addReadGroup,
                               final int defaultChromosomeLength, final ScoringStrategy duplicateScoringStrategy) {

        this.header = makeDefaultHeader(sortOrder, defaultChromosomeLength, addReadGroup);

        final SAMRecordComparator comparator = sortOrder.getComparatorInstance();

        if (sortForMe && comparator != null) {
            this.records = new TreeSet<>(comparator);
        } else {
            this.records = new ArrayList<>();
        }
    }

    /**
     * Determine whether the class will use a bam (default) or a sam file to hold the
     * records when providing a reader to them.
     *
     * @param useBamFile if true will use a BAM file, otherwise it will use a SAM file to hold the records.
     */
    public void setUseBamFile(final boolean useBamFile) {
        this.useBamFile = useBamFile;
    }

    public void setUnmappedHasBasesAndQualities(final boolean value) {
        this.unmappedHasBasesAndQualities = value;
    }

    public int size() {
        return this.records.size();
    }

    /**
     * Set the seed of the random number generator for cases in which repeatable result is desired.
     */
    public void setRandomSeed(final long seed) {
        random.setSeed(seed);
    }

    /**
     * Adds the given program record to the header, and assigns the PG tag to any SAMRecords
     * created after it has been added. May be called multiple times in order to assign different
     * PG IDs to different SAMRecords.  programRecord may be null to stop assignment of PG tag.
     * It is up to the caller to ensure that program record IDs do not collide.
     */
    public void setProgramRecord(final SAMProgramRecord programRecord) {
        this.programRecord = programRecord;
        if (programRecord != null) {
            this.header.addProgramRecord(programRecord);
        }
    }

    public void setUseNmFlag(final boolean useNmFlag) {
        this.useNmFlag = useNmFlag;
    }

    public void setReadGroup(final SAMReadGroupRecord readGroup) {
        this.readGroup = readGroup;
        if (readGroup != null) {
            this.header.addReadGroup(readGroup);
        }
    }

    /**
     * Returns the accumulated list of sam records.
     */
    public Collection<SAMRecord> getRecords() {
        return this.records;
    }

    public void setHeader(final SAMFileHeader header) {
        this.header = header.clone();
    }

    /**
     * The record should already have the DS and MC tags computed
     */
    public void addRecord(final SAMRecord record) {
        if (record.getReadPairedFlag() && !record.getMateUnmappedFlag() &&
                null == record.getAttribute(SAMTag.MC.getBinaryTag())) {
            throw new SAMException("Mate Cigar tag (MC) not found in: " + record.getReadName());
        }
        this.records.add(record);
    }

    /**
     * Returns a CloseableIterator over the collection of SAMRecords.
     */
    @Override
    public CloseableIterator<SAMRecord> iterator() {
        return new CloseableIterator<SAMRecord>() {
            private final Iterator<SAMRecord> iterator = records.iterator();

            @Override
            public void close() { /** Do nothing. */}

            @Override
            public boolean hasNext() {
                return this.iterator.hasNext();
            }

            @Override
            public SAMRecord next() {
                return this.iterator.next();
            }

            @Override
            public void remove() {
                this.iterator.remove();
            }
        };
    }

    /**
     * Adds a fragment record (mapped or unmapped) to the set using the provided contig start and optionally the strand,
     * cigar string, quality string or default quality score.  This does not modify the flag field, which should be updated
     * if desired before adding the return to the list of records.
     */
    private SAMRecord createReadNoFlag(final String name, final int contig, final int start, final boolean negativeStrand,
                                       final boolean recordUnmapped, final String cigar, final String qualityString,
                                       final int defaultQuality) throws SAMException {
        final SAMRecord rec = new SAMRecord(this.header);
        rec.setReadName(name);
        if (header.getSequenceDictionary().size() <= contig) {
            throw new SAMException("Contig too big [" + header.getSequenceDictionary().size() + " < " + contig);
        }
        if (0 <= contig) {
            rec.setReferenceIndex(contig);
            rec.setAlignmentStart(start);
        }
        rec.setReadUnmappedFlag(recordUnmapped);
        if (recordUnmapped) {
            rec.setMappingQuality(SAMRecord.NO_MAPPING_QUALITY);
        } else {
            rec.setReadNegativeStrandFlag(negativeStrand);
            if (null != cigar) {
                rec.setCigarString(cigar);
            } else if (!rec.getReadUnmappedFlag()) {
                rec.setCigarString(readLength + "M");
            }
            rec.setMappingQuality(SAMRecord.UNKNOWN_MAPPING_QUALITY);
        }
        rec.setAttribute(SAMTag.RG, READ_GROUP_ID);

        if (useNmFlag) {
            rec.setAttribute(SAMTag.NM, SequenceUtil.calculateSamNmTagFromCigar(rec));
        }

        if (programRecord != null) {
            rec.setAttribute(SAMTag.PG, programRecord.getProgramGroupId());
        }

        if (readGroup != null) {
            rec.setAttribute(SAMTag.RG, readGroup.getReadGroupId());
        }

        if (!recordUnmapped || this.unmappedHasBasesAndQualities) {
            fillInBasesAndQualities(rec, qualityString, defaultQuality);
        }

        return rec;
    }

    /**
     * Adds a skeletal fragment (non-PE) record to the set using the provided
     * contig start and strand information.
     */
    public SAMRecord addFrag(final String name, final int contig, final int start, final boolean negativeStrand) {
        return addFrag(name, contig, start, negativeStrand, false, null, null, -1);
    }

    /**
     * Adds a fragment record (mapped or unmapped) to the set using the provided contig start and optionally the strand,
     * cigar string, quality string or default quality score.
     */
    public SAMRecord addFrag(final String name, final int contig, final int start, final boolean negativeStrand,
                             final boolean recordUnmapped, final String cigar, final String qualityString,
                             final int defaultQuality) throws SAMException {
        return addFrag(name, contig, start, negativeStrand, recordUnmapped, cigar, qualityString, defaultQuality, false);
    }

    /**
     * Adds a fragment record (mapped or unmapped) to the set using the provided contig start and optionally the strand,
     * cigar string, quality string or default quality score.
     */
    public SAMRecord addFrag(final String name, final int contig, final int start, final boolean negativeStrand,
                             final boolean recordUnmapped, final String cigar, final String qualityString,
                             final int defaultQuality, final boolean isSecondary) throws SAMException {
        final htsjdk.samtools.SAMRecord rec = createReadNoFlag(name, contig, start, negativeStrand, recordUnmapped, cigar, qualityString, defaultQuality);
        if (isSecondary) {
            rec.setSecondaryAlignment(true);
        }
        this.records.add(rec);
        return rec;
    }

    /**
     * Adds a fragment record (mapped or unmapped) to the set using the provided contig start and optionally the strand,
     * cigar string, quality string or default quality score.
     */
    public SAMRecord addFrag(final String name, final int contig, final int start, final boolean negativeStrand,
                             final boolean recordUnmapped, final String cigar, final String qualityString,
                             final int defaultQuality, final boolean isSecondary, final boolean isSupplementary) throws SAMException {
        final htsjdk.samtools.SAMRecord rec = createReadNoFlag(name, contig, start, negativeStrand, recordUnmapped, cigar, qualityString, defaultQuality);
        if (isSecondary) {
            rec.setSecondaryAlignment(true);
        }
        if (isSupplementary) {
            rec.setSupplementaryAlignmentFlag(true);
        }
        this.records.add(rec);
        return rec;
    }

    /**
     * Fills in the bases and qualities for the given record. Quality data is randomly generated if the defaultQuality
     * is set to -1. Otherwise all qualities will be set to defaultQuality. If a quality string is provided that string
     * will be used instead of the defaultQuality.
     */
    private void fillInBasesAndQualities(final SAMRecord rec, final String qualityString, final int defaultQuality) {

        if (null == qualityString) {
            fillInBasesAndQualities(rec, defaultQuality);
        } else {
            fillInBases(rec);
            rec.setBaseQualityString(qualityString);
        }
    }

    /**
     * If the record contains a cigar with non-zero read length, return that length, otherwise, return readLength
     */
    private int getReadLengthFromCigar(final SAMRecord rec) {
        return (rec.getCigar() != null &&
                rec.getCigar().getReadLength() != 0) ? rec.getCigar().getReadLength() : readLength;
    }

    /**
     * Randomly fills in the bases for the given record.
     * <p>
     * If there's a cigar with read-length >0, will use that length for reads. Otherwise will use length = 36
     */
    private void fillInBases(final SAMRecord rec) {
        rec.setReadBases(SequenceUtil.getRandomBases(random, getReadLengthFromCigar(rec)));
    }

    /**
     * Adds an unmapped fragment read to the builder.
     */
    public void addUnmappedFragment(final String name) {
        addFrag(name, -1, -1, false, true, null, null, -1, false);
    }

    /**
     * Adds a skeletal pair of records to the set using the provided
     * contig starts.  The pair is assumed to be a well
     * formed pair sitting on a single contig.
     */
    public void addPair(final String name, final int contig, final int start1, final int start2) {
        final SAMRecord end1 = new SAMRecord(this.header);
        final SAMRecord end2 = new SAMRecord(this.header);
        final boolean end1IsFirstOfPair = this.random.nextBoolean();

        end1.setReadName(name);
        end1.setReferenceIndex(contig);
        end1.setAlignmentStart(start1);
        end1.setReadNegativeStrandFlag(false);
        end1.setCigarString(readLength + "M");
        if (useNmFlag) {
            end1.setAttribute(ReservedTagConstants.NM, 0);
        }
        end1.setMappingQuality(SAMRecord.UNKNOWN_MAPPING_QUALITY);
        end1.setReadPairedFlag(true);
        end1.setProperPairFlag(true);
        end1.setFirstOfPairFlag(end1IsFirstOfPair);
        end1.setSecondOfPairFlag(!end1IsFirstOfPair);
        end1.setAttribute(SAMTag.RG, READ_GROUP_ID);
        if (programRecord != null) {
            end1.setAttribute(SAMTag.PG, programRecord.getProgramGroupId());
        }
        if (readGroup != null) {
            end1.setAttribute(SAMTag.RG, readGroup.getReadGroupId());
        }
        fillInBasesAndQualities(end1);

        end2.setReadName(name);
        end2.setReferenceIndex(contig);
        end2.setAlignmentStart(start2);
        end2.setReadNegativeStrandFlag(true);
        end2.setCigarString(readLength + "M");
        if (useNmFlag) {
            end2.setAttribute(ReservedTagConstants.NM, 0);
        }
        end2.setMappingQuality(SAMRecord.UNKNOWN_MAPPING_QUALITY);
        end2.setReadPairedFlag(true);
        end2.setProperPairFlag(true);
        end2.setFirstOfPairFlag(!end1IsFirstOfPair);
        end2.setSecondOfPairFlag(end1IsFirstOfPair);
        end2.setAttribute(SAMTag.RG, READ_GROUP_ID);
        if (programRecord != null) {
            end2.setAttribute(SAMTag.PG, programRecord.getProgramGroupId());
        }
        if (readGroup != null) {
            end2.setAttribute(SAMTag.RG, readGroup.getReadGroupId());
        }
        fillInBasesAndQualities(end2);

        // set mate info
        SamPairUtil.setMateInfo(end1, end2, true);

        this.records.add(end1);
        this.records.add(end2);
    }

    /**
     * Adds a pair of records (mapped or unmmapped) to the set using the provided contig starts.
     * The pair is assumed to be a well formed pair sitting on a single contig.
     */
    public List<SAMRecord> addPair(final String name, final int contig, final int start1, final int start2,
                                   final boolean record1Unmapped, final boolean record2Unmapped, final String cigar1,
                                   final String cigar2, final boolean strand1, final boolean strand2, final int defaultQuality) {
        return this.addPair(name, contig, contig, start1, start2, record1Unmapped, record2Unmapped, cigar1, cigar2, strand1, strand2, false, false, defaultQuality);
    }

    /**
     * Adds a pair of records (mapped or unmmapped) to the set using the provided contig starts.
     * The pair is assumed to be a well formed pair sitting on a single contig.
     */
    public List<SAMRecord> addPair(final String name, final int contig1, final int contig2, final int start1, final int start2,
                                   final boolean record1Unmapped, final boolean record2Unmapped, final String cigar1,
                                   final String cigar2, final boolean strand1, final boolean strand2, final boolean record1NonPrimary,
                                   final boolean record2NonPrimary, final int defaultQuality) {
        final List<SAMRecord> recordsList = new LinkedList<>();

        final SAMRecord end1 = createReadNoFlag(name, contig1, start1, strand1, record1Unmapped, cigar1, null, defaultQuality);
        final SAMRecord end2 = createReadNoFlag(name, contig2, start2, strand2, record2Unmapped, cigar2, null, defaultQuality);

        end1.setReadPairedFlag(true);
        end1.setFirstOfPairFlag(true);

        if (!record1Unmapped && !record2Unmapped) {
            end1.setProperPairFlag(true);
            end2.setProperPairFlag(true);
        }
        end2.setReadPairedFlag(true);
        end2.setSecondOfPairFlag(true);

        if (record1NonPrimary) {
            end1.setSecondaryAlignment(true);
        }
        if (record2NonPrimary) {
            end2.setSecondaryAlignment(true);
        }

        // set mate info
        SamPairUtil.setMateInfo(end1, end2, true);

        recordsList.add(end1);
        recordsList.add(end2);

        records.add(end1);
        records.add(end2);

        return recordsList;
    }

    /**
     * Adds a pair of records (mapped or unmmapped) to the set using the provided contig starts.
     * The pair is assumed to be a well formed pair sitting on a single contig.
     */
    public List<SAMRecord> addPair(final String name, final int contig, final int start1, final int start2,
                                   final boolean record1Unmapped, final boolean record2Unmapped, final String cigar1,
                                   final String cigar2, final boolean strand1, final boolean strand2, final boolean record1NonPrimary,
                                   final boolean record2NonPrimary, final int defaultQuality) {
        return addPair(name, contig, contig, start1, start2, record1Unmapped, record2Unmapped, cigar1, cigar2, strand1, strand2,
                record1NonPrimary, record2NonPrimary, defaultQuality);
    }

    /**
     * Adds a pair with both ends unmapped to the builder.
     */
    public void addUnmappedPair(final String name) {
        final SAMRecord end1 = new SAMRecord(this.header);
        final SAMRecord end2 = new SAMRecord(this.header);
        final boolean end1IsFirstOfPair = this.random.nextBoolean();

        end1.setReadName(name);
        end1.setReadPairedFlag(true);
        end1.setReadUnmappedFlag(true);
        end1.setAttribute(SAMTag.MC, null);
        end1.setProperPairFlag(false);
        end1.setFirstOfPairFlag(end1IsFirstOfPair);
        end1.setSecondOfPairFlag(!end1IsFirstOfPair);
        end1.setMateUnmappedFlag(true);
        end1.setAttribute(SAMTag.RG, READ_GROUP_ID);
        if (programRecord != null) {
            end1.setAttribute(SAMTag.PG, programRecord.getProgramGroupId());
        }
        if (this.unmappedHasBasesAndQualities) {
            fillInBasesAndQualities(end1);
        }

        end2.setReadName(name);
        end2.setReadPairedFlag(true);
        end2.setReadUnmappedFlag(true);
        end2.setAttribute(SAMTag.MC, null);
        end2.setProperPairFlag(false);
        end2.setFirstOfPairFlag(!end1IsFirstOfPair);
        end2.setSecondOfPairFlag(end1IsFirstOfPair);
        end2.setMateUnmappedFlag(true);
        end2.setAttribute(SAMTag.RG, READ_GROUP_ID);
        if (programRecord != null) {
            end2.setAttribute(SAMTag.PG, programRecord.getProgramGroupId());
        }
        if (this.unmappedHasBasesAndQualities) {
            fillInBasesAndQualities(end2);
        }

        this.records.add(end1);
        this.records.add(end2);
    }

    /**
     * Fills in bases and qualities with randomly generated data.
     * Relies on the alignment start and end having been set to get read length.
     */
    private void fillInBasesAndQualities(final SAMRecord rec) {
        fillInBasesAndQualities(rec, -1);
    }

    /**
     * Fills in bases and qualities with a set default quality. If the defaultQuality is set to -1 quality scores will
     * be randomly generated.
     * If there's a cigar with read-length >0, will use that length for reads. Otherwise will use length = 36
     */
    private void fillInBasesAndQualities(final SAMRecord rec, final int defaultQuality) {
        final int length = getReadLengthFromCigar(rec);
        final byte[] quals = new byte[length];

        if (-1 != defaultQuality) {
            Arrays.fill(quals, (byte) defaultQuality);
        } else {
            for (int i = 0; i < length; ++i) {
                quals[i] = (byte) this.random.nextInt(50);
            }
        }
        rec.setBaseQualities(quals);
        fillInBases(rec);
    }

    /**
     * Creates samFileReader from the data in instance of this class
     *
     * @return SamReader
     */
    public SamReader getSamReader() {

        final File tempFile;

        try {
            tempFile = File.createTempFile("temp", this.useBamFile ? ".bam" : ".sam");
            tempFile.deleteOnExit();
        } catch (final IOException e) {
            throw new RuntimeIOException("problems creating tempfile", e);
        }

        this.header.setAttribute("VN", "1.0");
        try (final SAMFileWriter w = this.useBamFile ?
                new SAMFileWriterFactory().makeBAMWriter(this.header, true, tempFile) :
                new SAMFileWriterFactory().makeSAMWriter(this.header, true, tempFile)) {
            for (final SAMRecord r : this.getRecords()) {
                w.addAlignment(r);
            }
        }

        return SamReaderFactory.makeDefault().validationStringency(ValidationStringency.SILENT).open(tempFile);
    }

    public SAMFileHeader getHeader() {
        return header;
    }

    public void setReadLength(final int readLength) {
        this.readLength = readLength;
    }

    /**
     * creates a simple header
     *
     * @param sortOrder   Use this sort order in the header
     * @param contigLength length of the other contigs
     * @return newly formed header
     */
    static public SAMFileHeader makeDefaultHeader(final SAMFileHeader.SortOrder sortOrder, final int contigLength, final boolean addReadGroup) {
        final List<SAMSequenceRecord> sequences = new ArrayList<>();
        for (final String chrom : chroms) {
            final SAMSequenceRecord sequenceRecord = new SAMSequenceRecord(chrom, contigLength);
            sequences.add(sequenceRecord);
        }

        final SAMFileHeader header = new SAMFileHeader();
        header.setSequenceDictionary(new SAMSequenceDictionary(sequences));
        header.setSortOrder(sortOrder);

        if (addReadGroup) {
            final SAMReadGroupRecord readGroupRecord = new SAMReadGroupRecord(READ_GROUP_ID);
            readGroupRecord.setSample(SAMPLE);
            readGroupRecord.setPlatform(PlatformValue.ILLUMINA.name());
            //This must be a mutable list because setReadGroups doesn't perform a copy and tests expect to be able
            //to modify it.
            final List<SAMReadGroupRecord> readGroups = new ArrayList<>();
            readGroups.add(readGroupRecord);
            header.setReadGroups(readGroups);
        }
        return header;
    }


    /**
     * Writes a random (but deterministic) reference file given a {@link SAMFileHeader}
     * output file is expected to have a non-compressed fasta suffix.
     * Method will also write .dict and .fai files next to the reference file.
     * files next to the provided. Will use the current {@link SAMFileHeader} to model
     * the reference on.
     *
     * @param fasta Path to output reference where it will be written. No checks will be
     *              performed regarding the writability, or existence of the files prior to writing.
     * @throws IOException In case of problem during writing the files.
     **/

    public void writeRandomReference(final Path fasta) throws IOException {
        writeRandomReference(getHeader(), fasta);
    }

    /**
     * Writes a random (but deterministic) reference file given a {@link SAMFileHeader}
     * output file is expected to have a non-compressed fasta suffix.
     * Method will also write .dict and .fai files next to the reference file.
     * files next to the provided
     *
     * @param header Header file to base the reference on. Length and names of sequences will be
     *               taken from here, as will the order of the contigs in the reference.
     * @param fasta  Path to output reference where it will be written. No checks will be performed
     *               regarding the writability, or existence of the files prior to writing.
     * @throws IOException In case of problem during writing the files.
     **/
    public static void writeRandomReference(final SAMFileHeader header, final Path fasta) throws IOException {
        final int MAX_WRITE_AT_A_TIME = 10_000;
        final byte[] buffer = new byte[MAX_WRITE_AT_A_TIME];
        final FastaReferenceWriterBuilder builder = new FastaReferenceWriterBuilder().setEmitMd5(true).setFastaFile(fasta);

        try (FastaReferenceWriter writer = builder.build()) {

            final Random random = new Random();
            for (final SAMSequenceRecord seq : header.getSequenceDictionary().getSequences()) {
                writer.startSequence(seq.getSequenceName());
                random.setSeed(Objects.hash(seq.getSequenceName(), seq.getSequenceLength()));
                int written = 0;
                while (written < seq.getSequenceLength()) {
                    final int writeLength = Math.min(seq.getSequenceLength() - written, MAX_WRITE_AT_A_TIME);
                    SequenceUtil.getRandomBases(random, writeLength, buffer);
                    writer.appendBases(buffer, 0, writeLength);
                    written += writeLength;
                }
            }
        }
    }
}
