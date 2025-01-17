/*
 * The MIT License
 *
 * Copyright (c) 2014 The Broad Institute
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

import htsjdk.HtsjdkTest;
import htsjdk.samtools.util.BinaryCodec;
import htsjdk.samtools.util.FileExtensions;
import htsjdk.samtools.util.IOUtil;
import htsjdk.samtools.util.TestUtil;
import htsjdk.utils.TestNGUtils;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.*;
import java.lang.reflect.Array;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class SAMRecordUnitTest extends HtsjdkTest {

    private static final String ARRAY_TAG = "xa";

    @DataProvider(name = "serializationTestData")
    public Object[][] getSerializationTestData() {
        return new Object[][] {
                { new File("src/test/resources/htsjdk/samtools/serialization_test.sam") },
                { new File("src/test/resources/htsjdk/samtools/serialization_test.bam") }
        };
    }

    @Test(dataProvider = "serializationTestData")
    public void testSAMRecordSerialization( final File inputFile ) throws Exception {
        final SamReader reader = SamReaderFactory.makeDefault().open(inputFile);
        final SAMRecord initialSAMRecord = reader.iterator().next();
        reader.close();

        final SAMRecord deserializedSAMRecord = TestUtil.serializeAndDeserialize(initialSAMRecord);

        Assert.assertEquals(deserializedSAMRecord, initialSAMRecord, "Deserialized SAMRecord not equal to original SAMRecord");
    }

    @DataProvider
    public Object [][] offsetAtReferenceData() {
        return new Object[][]{
                {"3S9M",   7, 10, false},
                {"3S9M",   0,  0, false},
                {"3S9M",  -1,  0, false},
                {"3S9M",  13,  0, false},
                {"4M1D6M", 4,  4, false},
                {"4M1D6M", 4,  4, true},
                {"4M1D6M", 5,  0, false},
                {"4M1D6M", 5,  4, true},
                {"4M1I6M", 5,  6, false},
                {"4M1I6M", 11, 0, false},
        };
    }

    @Test(dataProvider = "offsetAtReferenceData")
    public void testOffsetAtReference(String cigar, int posInReference, int expectedPosInRead, boolean returnLastBaseIfDeleted) {

            SAMRecord sam = new SAMRecordSetBuilder().addFrag("test", 0, 1, false, false, cigar, null, 2);
            Assert.assertEquals(SAMRecord.getReadPositionAtReferencePosition(sam, posInReference, returnLastBaseIfDeleted), expectedPosInRead);
    }

    @DataProvider
    public Object [][] referenceAtReadData() {
        return new Object[][]{
                {"3S9M", 7, 10},
                {"3S9M", 0, 0},
                {"3S9M", 0, 13},
                {"4M1D6M", 4, 4},
                {"4M1D6M", 6, 5},
                {"4M1I6M", 0, 5},
                {"4M1I6M", 5, 6},
        };
    }

    @Test(dataProvider = "referenceAtReadData")
    public void testOffsetAtRead(String cigar, int expectedReferencePos, int posInRead) {

            SAMRecord sam = new SAMRecordSetBuilder().addFrag("test", 0, 1, false, false, cigar, null, 2);
            Assert.assertEquals(sam.getReferencePositionAtReadPosition(posInRead), expectedReferencePos);
    }

    @DataProvider(name = "deepCopyTestData")
    public Object [][] deepCopyTestData() {
        return new Object[][]{
                { new SAMRecordSetBuilder().addFrag("test", 0, 1, false, false, "3S9M", null, 2) },
                { new SAMRecordSetBuilder().addFrag("test", 0, 1, false, false, "4M1I6M", null, 2) }
        };
    }

    @Test(dataProvider = "deepCopyTestData")
    public void testDeepCopyBasic(final SAMRecord sam) {
        testDeepCopy(sam);
    }

    @Test(dataProvider = "deepCopyTestData")
    public void testDeepCopyCigar(SAMRecord sam) {
        sam.setCigar(sam.getCigar());
        final SAMRecord deepCopy = sam.deepCopy();
        Assert.assertTrue(sam.equals(deepCopy));
    }

    @Test(dataProvider = "deepCopyTestData")
    public void testDeepCopyGetCigarString(SAMRecord sam) {
        sam.setCigarString(sam.getCigarString());
        final SAMRecord deepCopy = sam.deepCopy();
        Assert.assertTrue(sam.equals(deepCopy));
    }

    @Test(dataProvider = "deepCopyTestData")
    public void testDeepCopyGetCigar(final SAMRecord sam)
    {
        testDeepCopy(sam);
        sam.setCigarString(sam.getCigarString());
        sam.getCigar(); // force cigar elements to be resolved for equals
        testDeepCopy(sam);
    }

    @Test(dataProvider = "deepCopyTestData")
    public void testDeepCopyMutate(final SAMRecord sam) {
        final byte[] initialBaseQualityCopy = Arrays.copyOf(sam.getBaseQualities(), sam.getBaseQualities().length);
        final int initialStart = sam.getAlignmentStart();

        final SAMRecord deepCopy = testDeepCopy(sam);
        Assert.assertTrue(Arrays.equals(sam.getBaseQualities(), deepCopy.getBaseQualities()));
        Assert.assertTrue(sam.getAlignmentStart() == deepCopy.getAlignmentStart());

        // mutate copy and make sure original remains unchanged
        final byte[] copyBaseQuals = deepCopy.getBaseQualities();
        for (int i = 0; i < copyBaseQuals.length; i++) {
            copyBaseQuals[i]++;
        }
        deepCopy.setBaseQualities(copyBaseQuals);
        deepCopy.setAlignmentStart(initialStart + 1);
        Assert.assertTrue(Arrays.equals(sam.getBaseQualities(), initialBaseQualityCopy));
        Assert.assertTrue(sam.getAlignmentStart() == initialStart);
    }

    @Test(dataProvider = "deepCopyTestData")
    public void testDeepByteAttributes( final SAMRecord sam ) throws Exception {
        SAMRecord deepCopy = testDeepCopy(sam);

        final byte bytes[] = { -2, -1, 0, 1, 2 };
        sam.setAttribute("BY", bytes);
        deepCopy = sam.deepCopy();
        Assert.assertEquals(sam, deepCopy);

        // validate reference inequality and content equality
        final byte samBytes[] = sam.getByteArrayAttribute("BY");
        final byte copyBytes[] = deepCopy.getByteArrayAttribute("BY");
        Assert.assertFalse(copyBytes == samBytes);
        Assert.assertTrue(Arrays.equals(copyBytes, samBytes));

        // validate mutation independence
        final byte testByte = -1;
        Assert.assertTrue(samBytes[2] != testByte);  // ensure initial test condition
        Assert.assertTrue(copyBytes[2] != testByte); // ensure initial test condition
        samBytes[2] = testByte;                      // mutate original
        Assert.assertTrue(samBytes[2] == testByte);
        Assert.assertTrue(copyBytes[2] != testByte);
        sam.setAttribute("BY", samBytes);
        Assert.assertTrue(sam.getByteArrayAttribute("BY")[2] != deepCopy.getByteArrayAttribute("BY")[2]);

        // now unsigned...
        sam.setUnsignedArrayAttribute("BY", bytes);
        deepCopy = sam.deepCopy();
        Assert.assertEquals(sam, deepCopy);
        final byte samUBytes[] = sam.getUnsignedByteArrayAttribute("BY");
        final byte copyUBytes[] = deepCopy.getUnsignedByteArrayAttribute("BY");
        Assert.assertFalse(copyUBytes == bytes);
        Assert.assertTrue(Arrays.equals(copyUBytes, samUBytes));

        // validate mutation independence
        final byte uByte = 1;
        Assert.assertTrue(samUBytes[2] != uByte); //  ensure initial test condition
        Assert.assertTrue(samUBytes[2] != uByte); //  ensure initial test condition
        samUBytes[2] = uByte;  // mutate original
        Assert.assertTrue(samUBytes[2] == uByte);
        Assert.assertTrue(copyUBytes[2] != uByte);
        sam.setUnsignedArrayAttribute("BY", samBytes);
        Assert.assertTrue(sam.getUnsignedByteArrayAttribute("BY")[2] != deepCopy.getUnsignedByteArrayAttribute("BY")[2]);
    }

    @Test(dataProvider = "deepCopyTestData")
    public void testDeepShortAttributes( final SAMRecord sam ) throws Exception {
        SAMRecord deepCopy = testDeepCopy(sam);

        final short shorts[] = { -20, -10, 0, 10, 20 };
        sam.setAttribute("SH", shorts);
        deepCopy = sam.deepCopy();
        Assert.assertEquals(sam, deepCopy);

        // validate reference inequality, content equality
        final short samShorts[] = sam.getSignedShortArrayAttribute("SH");
        final short copyShorts[] = deepCopy.getSignedShortArrayAttribute("SH");
        Assert.assertFalse(copyShorts == samShorts);
        Assert.assertTrue(Arrays.equals(copyShorts, samShorts));

        // validate mutation independence
        final short testShort = -1;
        Assert.assertTrue(samShorts[2] != testShort); //  ensure initial test condition
        Assert.assertTrue(samShorts[2] != testShort); //  ensure initial test condition
        samShorts[2] = testShort;  // mutate original
        Assert.assertTrue(samShorts[2] == testShort);
        Assert.assertTrue(copyShorts[2] != testShort);
        sam.setAttribute("SH", samShorts);
        Assert.assertTrue(sam.getSignedShortArrayAttribute("SH")[2] != deepCopy.getSignedShortArrayAttribute("SH")[2]);

        // now unsigned...
        sam.setUnsignedArrayAttribute("SH", shorts);
        deepCopy = sam.deepCopy();
        Assert.assertEquals(sam, deepCopy);

        final short samUShorts[] = sam.getUnsignedShortArrayAttribute("SH");
        final short copyUShorts[] = deepCopy.getUnsignedShortArrayAttribute("SH");
        Assert.assertFalse(copyUShorts == shorts);
        Assert.assertTrue(Arrays.equals(copyUShorts, samUShorts));

        // validate mutation independence
        final byte uShort = 1;
        Assert.assertTrue(samUShorts[2] != uShort); //  ensure initial test condition
        Assert.assertTrue(samUShorts[2] != uShort); //  ensure initial test condition
        samUShorts[2] = uShort;  // mutate original
        Assert.assertTrue(samUShorts[2] == uShort);
        Assert.assertTrue(copyUShorts[2] != uShort);
        sam.setUnsignedArrayAttribute("SH", samShorts);
        Assert.assertTrue(sam.getUnsignedShortArrayAttribute("SH")[2] != deepCopy.getUnsignedShortArrayAttribute("SH")[2]);
    }

    @Test(dataProvider = "deepCopyTestData")
    public void testDeepIntAttributes( final SAMRecord sam ) throws Exception {
        SAMRecord deepCopy = testDeepCopy(sam);

        final int ints[] = { -200, -100, 0, 100, 200 };
        sam.setAttribute("IN", ints);
        deepCopy = sam.deepCopy();
        Assert.assertEquals(sam, deepCopy);

        // validate reference inequality and content equality
        final  int samInts[] = sam.getSignedIntArrayAttribute("IN");
        final  int copyInts[] = deepCopy.getSignedIntArrayAttribute("IN");
        Assert.assertFalse(copyInts == ints);
        Assert.assertTrue(Arrays.equals(copyInts, samInts));

        // validate mutation independence
        final short testInt = -1;
        Assert.assertTrue(samInts[2] != testInt); //  ensure initial test condition
        Assert.assertTrue(samInts[2] != testInt); //  ensure initial test condition
        samInts[2] = testInt;  // mutate original
        Assert.assertTrue(samInts[2] == testInt);
        Assert.assertTrue(copyInts[2] != testInt);
        sam.setAttribute("IN", samInts);
        Assert.assertTrue(sam.getSignedIntArrayAttribute("IN")[2] != deepCopy.getSignedIntArrayAttribute("IN")[2]);

        // now unsigned...
        sam.setUnsignedArrayAttribute("IN", ints);
        deepCopy = sam.deepCopy();
        Assert.assertEquals(sam, deepCopy);

        final int samUInts[] = sam.getUnsignedIntArrayAttribute("IN");
        final int copyUInts[] = deepCopy.getUnsignedIntArrayAttribute("IN");
        Assert.assertFalse(copyUInts == ints);
        Assert.assertTrue(Arrays.equals(copyUInts, samUInts));

        // validate mutation independence
        byte uInt = 1;
        Assert.assertTrue(samUInts[2] != uInt); //  ensure initial test condition
        Assert.assertTrue(samUInts[2] != uInt); //  ensure initial test condition
        samInts[2] = uInt;  // mutate original
        Assert.assertTrue(samUInts[2] == uInt);
        Assert.assertTrue(copyUInts[2] != uInt);
        sam.setUnsignedArrayAttribute("IN", samInts);
        Assert.assertTrue(sam.getUnsignedIntArrayAttribute("IN")[2] != deepCopy.getUnsignedIntArrayAttribute("IN")[2]);
    }

    @Test(dataProvider = "deepCopyTestData")
    public void testDeepFloatAttributes( final SAMRecord sam ) throws Exception {
        SAMRecord deepCopy = testDeepCopy(sam);

        final float floats[] = { -2.4f, -1.2f, 0, 2.3f, 4.6f };
        sam.setAttribute("FL", floats);
        deepCopy = sam.deepCopy();
        Assert.assertEquals(sam, deepCopy);

        // validate reference inequality and content equality
        final float samFloats[] = sam.getFloatArrayAttribute("FL");
        final float copyFloats[] = deepCopy.getFloatArrayAttribute("FL");
        Assert.assertFalse(copyFloats == floats);
        Assert.assertFalse(copyFloats == samFloats);
        Assert.assertTrue(Arrays.equals(copyFloats, samFloats));

        // validate mutation independence
        final float testFloat = -1.0f;
        Assert.assertTrue(samFloats[2] != testFloat); //  ensure initial test condition
        Assert.assertTrue(samFloats[2] != testFloat); //  ensure initial test condition
        samFloats[2] = testFloat;  // mutate original
        Assert.assertTrue(samFloats[2] == testFloat);
        Assert.assertTrue(copyFloats[2] != testFloat);
        sam.setAttribute("FL", samFloats);
        Assert.assertTrue(sam.getFloatArrayAttribute("FL")[2] != deepCopy.getFloatArrayAttribute("FL")[2]);
    }

    private SAMRecord testDeepCopy(SAMRecord sam) {
        final SAMRecord deepCopy = sam.deepCopy();
        Assert.assertTrue(sam.equals(deepCopy));
        return deepCopy;
    }

    @Test
    public void test_getUnsignedIntegerAttribute_valid() {
        final String stringTag = "UI";
        final short binaryTag = SAMTag.makeBinaryTag(stringTag);
        SAMFileHeader header = new SAMFileHeader();
        SAMRecord record = new SAMRecord(header);
        Assert.assertNull(record.getUnsignedIntegerAttribute(stringTag));
        Assert.assertNull(record.getUnsignedIntegerAttribute(binaryTag));

        record.setAttribute("UI", (long) 0L);
        Assert.assertEquals(0L, record.getUnsignedIntegerAttribute(stringTag));
        Assert.assertEquals(0L, record.getUnsignedIntegerAttribute(binaryTag));

        record.setAttribute("UI", BinaryCodec.MAX_UINT);
        Assert.assertEquals(BinaryCodec.MAX_UINT, record.getUnsignedIntegerAttribute(stringTag));
        Assert.assertEquals(BinaryCodec.MAX_UINT, record.getUnsignedIntegerAttribute(binaryTag));

        final SAMBinaryTagAndValue tv_zero = new SAMBinaryTagAndValue(binaryTag, 0L);
        record = new SAMRecord(header){
            {
                setAttributes(tv_zero);
            }
        };
        Assert.assertEquals(0L, record.getUnsignedIntegerAttribute(stringTag));
        Assert.assertEquals(0L, record.getUnsignedIntegerAttribute(binaryTag));

        final SAMBinaryTagAndValue tv_max = new SAMBinaryTagAndValue(binaryTag, BinaryCodec.MAX_UINT);
        record = new SAMRecord(header){
            {

                setAttributes(tv_max);
            }
        };
        Assert.assertEquals(BinaryCodec.MAX_UINT, record.getUnsignedIntegerAttribute(stringTag));
        Assert.assertEquals(BinaryCodec.MAX_UINT, record.getUnsignedIntegerAttribute(binaryTag));
    }

    /**
     * This is an alternative to test_getUnsignedIntegerAttribute_valid().
     * This is required for testing invalid (out of range) unsigned integer value.
     */
    @Test
    public void test_getUnsignedIntegerAttribute_valid_alternative() {
        final short tag = SAMTag.makeBinaryTag("UI");
        SAMFileHeader header = new SAMFileHeader();
        SAMRecord record;

        record = new SAMRecord(header);
        record.setAttribute("UI", 0L);
        Assert.assertEquals(0L, record.getUnsignedIntegerAttribute(tag));

        record = new SAMRecord(header);
        record.setAttribute("UI", BinaryCodec.MAX_UINT);
        Assert.assertEquals(BinaryCodec.MAX_UINT, record.getUnsignedIntegerAttribute("UI"));
    }

    @Test(expectedExceptions = SAMException.class)
    public void test_getUnsignedIntegerAttribute_negative() {
        SAMFileHeader header = new SAMFileHeader();
        SAMRecord record = new SAMRecord(header);
        record.setAttribute("UI", -1L);
        record.getUnsignedIntegerAttribute("UI");
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void test_setUnsignedIntegerAttributeTooLarge() {
        SAMFileHeader header = new SAMFileHeader();
        SAMRecord record = new SAMRecord(header);
        record.setAttribute("UI", BinaryCodec.MAX_UINT + 1);
    }

    // NOTE: SAMRecord.asAllowedAttribute is deprecated, as it has been moved into
    // SAMBinaryTagAndValue, but we'll leave this test here until the code is removed.
    @Test
    public void test_isAllowedAttributeDataType() {
        Assert.assertTrue(SAMBinaryTagAndValue.isAllowedAttributeValue((byte) 0));
        Assert.assertTrue(SAMBinaryTagAndValue.isAllowedAttributeValue((short) 0));
        Assert.assertTrue(SAMBinaryTagAndValue.isAllowedAttributeValue(0));
        Assert.assertTrue(SAMBinaryTagAndValue.isAllowedAttributeValue("a string"));
        Assert.assertTrue(SAMBinaryTagAndValue.isAllowedAttributeValue('C'));
        Assert.assertTrue(SAMBinaryTagAndValue.isAllowedAttributeValue(0.1F));
        Assert.assertTrue(SAMBinaryTagAndValue.isAllowedAttributeValue(new byte[]{0}));
        Assert.assertTrue(SAMBinaryTagAndValue.isAllowedAttributeValue(new short[]{0}));
        Assert.assertTrue(SAMBinaryTagAndValue.isAllowedAttributeValue(new int[]{0}));
        Assert.assertTrue(SAMBinaryTagAndValue.isAllowedAttributeValue(new float[]{0.1F}));

        // unsigned integers:
        Assert.assertTrue(SAMBinaryTagAndValue.isAllowedAttributeValue(0L));
        Assert.assertTrue(SAMBinaryTagAndValue.isAllowedAttributeValue(BinaryCodec.MAX_UINT));
        Assert.assertTrue(SAMBinaryTagAndValue.isAllowedAttributeValue(-1L));
        Assert.assertFalse(SAMBinaryTagAndValue.isAllowedAttributeValue(BinaryCodec.MAX_UINT + 1L));
        Assert.assertFalse(SAMBinaryTagAndValue.isAllowedAttributeValue(Integer.MIN_VALUE - 1L));
    }

    @Test()
    public void test_setAttribute_empty_string() {
        final SAMFileHeader header = new SAMFileHeader();
        final SAMRecord record = new SAMRecord(header);
        Assert.assertNull(record.getStringAttribute(SAMTag.MD));
        record.setAttribute(SAMTag.MD, "");
        Assert.assertNotNull(record.getStringAttribute(SAMTag.MD));
        Assert.assertEquals(record.getStringAttribute(SAMTag.MD),"");
        record.setAttribute(SAMTag.MD, null);
        Assert.assertNull(record.getStringAttribute(SAMTag.MD));
    }


    @Test(expectedExceptions = IllegalArgumentException.class)
    public void test_setAttribute_unsigned_int_negative() {
        SAMFileHeader header = new SAMFileHeader();
        SAMRecord record = null;
        record = new SAMRecord(header);
        Assert.assertNull(record.getUnsignedIntegerAttribute("UI"));
        record.setAttribute("UI", (long) Integer.MIN_VALUE - 1L);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void test_setAttribute_unsigned_int_tooLarge() {
        SAMFileHeader header = new SAMFileHeader();
        SAMRecord record = new SAMRecord(header);
        Assert.assertNull(record.getUnsignedIntegerAttribute("UI"));
        record.setAttribute("UI", (long) BinaryCodec.MAX_UINT + 1L);
    }

    @Test
    public void test_setAttribute_null_removes_tag() {
        final short tag = SAMTag.makeBinaryTag("UI");
        SAMFileHeader header = new SAMFileHeader();
        SAMRecord record = new SAMRecord(header);
        Assert.assertNull(record.getUnsignedIntegerAttribute(tag));

        record.setAttribute(tag, BinaryCodec.MAX_UINT);
        Assert.assertEquals(BinaryCodec.MAX_UINT, record.getUnsignedIntegerAttribute(tag));

        record.setAttribute(tag, null);
        Assert.assertNull(record.getUnsignedIntegerAttribute(tag));
    }

    private SAMRecord createTestRecordHelper() {
        return new SAMRecordSetBuilder().addFrag("test", 0, 1, false, false, "3S33M", null, 2);
    }

    @Test
    public void testReferenceName() {
        SAMRecord sam = createTestRecordHelper();

        // NO_ALIGNMENT_NAME
        sam.setReferenceName(SAMRecord.NO_ALIGNMENT_REFERENCE_NAME);
        Assert.assertTrue(sam.getReferenceName().equals(SAMRecord.NO_ALIGNMENT_REFERENCE_NAME));
        Assert.assertTrue(sam.getReferenceIndex().equals(SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX));

        // valid reference name
        sam = createTestRecordHelper();
        sam.setReferenceName("chr4");
        Assert.assertTrue(sam.getReferenceName().equals("chr4"));
        Assert.assertTrue(sam.getReferenceIndex().equals(3));

        // invalid reference name sets name but leaves ref index invalid
        sam = createTestRecordHelper();
        sam.setReferenceName("unresolvableName");
        Assert.assertTrue(sam.getReferenceName().equals("unresolvableName"));
        Assert.assertTrue(sam.getReferenceIndex().equals(SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX));
    }

    @Test
    public void testReferenceIndex() {
        // NO_ALIGNMENT_REFERENCE
        SAMRecord sam = createTestRecordHelper();
        sam.setReferenceIndex(SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX);
        Assert.assertTrue(sam.getReferenceIndex().equals(SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX));
        Assert.assertTrue(sam.getReferenceName().equals(SAMRecord.NO_ALIGNMENT_REFERENCE_NAME));

        // valid reference
        sam = createTestRecordHelper();
        sam.setReferenceIndex(3);
        Assert.assertTrue(sam.getReferenceIndex().equals(3));
        Assert.assertTrue(sam.getReferenceName().equals("chr4"));
    }

    @Test(expectedExceptions=IllegalArgumentException.class)
    public void testInvalidReferenceIndex() {
        // unresolvable reference
        final SAMRecord sam = createTestRecordHelper();
        sam.setReferenceIndex(9999);
    }

    @Test
    public void testMateReferenceName() {
        // NO_ALIGNMENT_NAME
        SAMRecord sam = createTestRecordHelper();
        sam.setMateReferenceName(SAMRecord.NO_ALIGNMENT_REFERENCE_NAME);
        Assert.assertTrue(sam.getMateReferenceName().equals(SAMRecord.NO_ALIGNMENT_REFERENCE_NAME));
        Assert.assertTrue(sam.getMateReferenceIndex().equals(SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX));

        // valid reference
        sam = createTestRecordHelper();
        sam.setMateReferenceName("chr4");
        Assert.assertTrue(sam.getMateReferenceName().equals("chr4"));
        Assert.assertTrue(sam.getMateReferenceIndex().equals(3));

        // unresolvable reference
        sam = createTestRecordHelper();
        sam.setMateReferenceName("unresolvableName");
        Assert.assertTrue(sam.getMateReferenceName().equals("unresolvableName"));
        Assert.assertTrue(sam.getMateReferenceIndex().equals(SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX));
    }

    @Test
    public void testMateReferenceIndex() {
        // NO_ALIGNMENT_REFERENCE
        SAMRecord sam = createTestRecordHelper();
        sam.setMateReferenceIndex(SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX);
        Assert.assertTrue(sam.getMateReferenceIndex().equals(SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX));
        Assert.assertTrue(sam.getMateReferenceName().equals(SAMRecord.NO_ALIGNMENT_REFERENCE_NAME));

        // valid reference
        sam = createTestRecordHelper();
        sam.setMateReferenceIndex(3);
        Assert.assertTrue(sam.getMateReferenceIndex().equals(3));
        Assert.assertTrue(sam.getMateReferenceName().equals("chr4"));
    }

    @Test(expectedExceptions=IllegalArgumentException.class)
    public void testInvalidMateReferenceIndex() {
        // unresolvable reference
        final SAMRecord sam = createTestRecordHelper();
        sam.setMateReferenceIndex(9999);
    }

    @Test
    public void testRecordValidation() {
        final SAMRecord sam = createTestRecordHelper();
        List<SAMValidationError> validationErrors = sam.isValid(false);
        Assert.assertTrue(validationErrors == null);
    }

    @Test
    public void testInvalidAlignmentStartValidation() {
        final SAMRecord sam = createTestRecordHelper();
        sam.setAlignmentStart(0);
        List<SAMValidationError> validationErrors = sam.isValid(false);
        Assert.assertTrue(validationErrors != null && validationErrors.size() == 1);
    }

    // ----------------- NULL header tests ---------------------

    @Test
    public void testNullHeaderReferenceName() {
        final SAMRecord sam = createTestRecordHelper();
        final SAMFileHeader samHeader = sam.getHeader();
        Assert.assertTrue(null != samHeader);
        final String originalRefName = sam.getReferenceName();

        // setting header to null retains the previously assigned ref name
        sam.setHeader(null);
        Assert.assertTrue(originalRefName.equals(sam.getReferenceName()));

        // null header allows reference name to be set to NO_ALIGNMENT_REFERENCE_NAME
        sam.setReferenceName(SAMRecord.NO_ALIGNMENT_REFERENCE_NAME);
        Assert.assertTrue(sam.getReferenceName().equals(SAMRecord.NO_ALIGNMENT_REFERENCE_NAME));
        Assert.assertTrue(sam.getReferenceIndex().equals(SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX));

        // null header allows reference name to be reset to a valid namw
        sam.setReferenceName(originalRefName);
        Assert.assertTrue(sam.getReferenceName().equals(originalRefName));
    }

    @Test
    public void testNullHeaderReferenceIndex() {
        SAMRecord sam = createTestRecordHelper();
        final SAMFileHeader samHeader = sam.getHeader();
        int originalRefIndex = sam.getReferenceIndex();
        Assert.assertTrue(SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX != originalRefIndex);

        // setting header to null resets the reference index to null
        sam.setHeader(null);
        Assert.assertTrue(null == sam.mReferenceIndex);
        // restoring the header to restores the reference index back to the original
        sam.setHeader(samHeader);
        Assert.assertTrue(sam.getReferenceIndex().equals(originalRefIndex));

        // setting the header to null allows setting the reference index to NO_ALIGNMENT_REFERENCE_INDEX
        sam.setHeader(null);
        sam.setReferenceIndex(SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX);
        Assert.assertTrue(sam.getReferenceIndex().equals(SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX));
        Assert.assertTrue(sam.getReferenceName().equals(SAMRecord.NO_ALIGNMENT_REFERENCE_NAME));

        // force the internal SAMRecord reference index value to (null) initial state
        sam = new SAMRecord(null);
        Assert.assertTrue(null == sam.mReferenceIndex);
        Assert.assertTrue(sam.getReferenceIndex() == SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX);

        // an unresolvable reference name doesn't throw
        final String unresolvableRefName = "unresolvable";
        sam.setReferenceName(unresolvableRefName);
        // now force the SAMRecord to try to resolve the unresolvable name
        sam.setHeader(samHeader);
        Assert.assertTrue(null == sam.mReferenceIndex);
        Assert.assertTrue(sam.getReferenceIndex() == SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX);
    }

    @Test(expectedExceptions=IllegalStateException.class)
    public void testNullHeaderSetReferenceIndex() {
        final SAMRecord sam = createTestRecordHelper();
        sam.setHeader(null);
        // setReferenceIndex with null header throws
        sam.setReferenceIndex(3);
    }

    @Test(expectedExceptions=IllegalStateException.class)
    public void testNullHeaderGetReferenceIndex() {
        final SAMRecord sam = createTestRecordHelper();
        sam.setHeader(null);
        // getReferenceIndex with null header throws
        sam.getReferenceIndex();
    }

    @Test(expectedExceptions=IllegalStateException.class)
    public void testNullHeaderForceIndexResolutionFailure() {
        // force the internal SAMRecord reference index value to null initial state
        final SAMRecord sam = new SAMRecord(null);
        sam.setReferenceName("unresolvable");
        sam.getReferenceIndex();
    }

    @Test
    public void testNullHeaderMateReferenceName() {
        final SAMRecord sam = createTestRecordHelper();
        final SAMFileHeader samHeader = sam.getHeader();
        Assert.assertTrue(null != samHeader);
        final String originalMateRefName = sam.getMateReferenceName();

        // setting header to null retains the previously assigned mate ref name
        sam.setHeader(null);
        Assert.assertTrue(originalMateRefName.equals(sam.getMateReferenceName()));

        // null header allows mate reference name to be set to NO_ALIGNMENT_REFERENCE_NAME
        sam.setMateReferenceName(SAMRecord.NO_ALIGNMENT_REFERENCE_NAME);
        Assert.assertTrue(sam.getMateReferenceName().equals(SAMRecord.NO_ALIGNMENT_REFERENCE_NAME));
        Assert.assertTrue(sam.getMateReferenceIndex().equals(SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX));

        // null header allows reference name to be reset to a valid namw
        sam.setMateReferenceName(originalMateRefName);
        Assert.assertTrue(sam.getMateReferenceName().equals(originalMateRefName));
    }

    @Test
    public void testNullHeaderMateReferenceIndex() {
        SAMRecord sam = createTestRecordHelper();
        final SAMFileHeader samHeader = sam.getHeader();
        sam.setMateReferenceName("chr1");
        int originalMateRefIndex = sam.getMateReferenceIndex();
        Assert.assertTrue(SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX != originalMateRefIndex);

        // setting header to null resets the mate reference index to null
        sam.setHeader(null);
        Assert.assertTrue(null == sam.mMateReferenceIndex);
        // restoring the header to restores the reference index back to the original
        sam.setHeader(samHeader);
        Assert.assertTrue(sam.getMateReferenceIndex().equals(originalMateRefIndex));

        // setting the header to null allows setting the mate reference index to NO_ALIGNMENT_REFERENCE_INDEX
        sam.setHeader(null);
        sam.setMateReferenceIndex(SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX);
        Assert.assertTrue(sam.getMateReferenceIndex().equals(SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX));
        Assert.assertTrue(sam.getMateReferenceName().equals(SAMRecord.NO_ALIGNMENT_REFERENCE_NAME));

        // force the internal SAMRecord mate reference index value to (null) initial state
        sam = new SAMRecord(null);
        Assert.assertTrue(null == sam.mMateReferenceIndex);
        Assert.assertTrue(sam.getMateReferenceIndex() == SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX);

        // an unresolvable mate reference name doesn't throw
        final String unresolvableRefName = "unresolvable";
        sam.setMateReferenceName(unresolvableRefName);
        // now force the SAMRecord to try to resolve the unresolvable mate reference name
        sam.setHeader(samHeader);
        Assert.assertTrue(null == sam.mMateReferenceIndex);
        Assert.assertTrue(sam.getMateReferenceIndex() == SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX);
    }

    @Test(expectedExceptions=IllegalStateException.class)
    public void testNullHeaderSetMateReferenceIndex() {
        final SAMRecord sam = createTestRecordHelper();
        sam.setHeader(null);
        sam.setMateReferenceIndex(3);
    }

    @Test(expectedExceptions=IllegalStateException.class)
    public void testNullHeaderGetMateReferenceIndex() {
        final SAMRecord sam = createTestRecordHelper();
        sam.setMateReferenceName("chr1");
        sam.setHeader(null);
        // getMateReferenceIndex with null header throws
        sam.getMateReferenceIndex();
    }

    @Test(expectedExceptions=IllegalStateException.class)
    public void testNullHeaderForceMateIndexResolutionFailure() {
        // force the internal SAMRecord reference index value to null initial state
        final SAMRecord sam = new SAMRecord(null);
        sam.setMateReferenceName("unresolvable");
        sam.getMateReferenceIndex();
    }

    @Test
    public void testNullHeaderGetReadGroup() {
        final SAMRecord sam = createTestRecordHelper();
        Assert.assertTrue(null != sam.getHeader());

        Assert.assertTrue(null != sam.getReadGroup() && sam.getReadGroup().getId().equals("1"));
        sam.setHeader(null);
        Assert.assertNull(sam.getReadGroup());
    }

    @Test(dataProvider = "serializationTestData")
    public void testNullHeaderSerialization(final File inputFile) throws Exception {
        final SamReader reader = SamReaderFactory.makeDefault().open(inputFile);
        final SAMRecord initialSAMRecord = reader.iterator().next();
        reader.close();

        initialSAMRecord.setHeader(null);
        final SAMRecord deserializedSAMRecord = TestUtil.serializeAndDeserialize(initialSAMRecord);
        Assert.assertEquals(deserializedSAMRecord, initialSAMRecord, "Deserialized SAMRecord not equal to original SAMRecord");
    }


    @Test
    public void testValidateNonsenseCigar(){
        // Create nonsense record
        SAMRecord rec = createTestRecordHelper();
        rec.setCigarString("nonsense");

        //The default validationStringency of a sam record is SILENT.
        rec.setValidationStringency(ValidationStringency.STRICT);
        // Validate record
        List<SAMValidationError> err = rec.validateCigar(-1);

        Assert.assertNotNull(err);
        Assert.assertEquals(err.size(), 1);
        Assert.assertEquals(err.get(0).getType(), SAMValidationError.Type.INVALID_CIGAR);
    }

    @Test
    public void testNullHeaderRecordValidation() {
        final SAMRecord sam = createTestRecordHelper();
        sam.setHeader(null);
        List<SAMValidationError> validationErrors = sam.isValid(false);
        Assert.assertTrue(validationErrors == null);
    }

    @Test
    public void testNullHeaderDeepCopy() {
        SAMRecord sam = createTestRecordHelper();
        sam.setHeader(null);
        final SAMRecord deepCopy = sam.deepCopy();

        Assert.assertTrue(sam.equals(deepCopy));
    }

    private void testNullHeaderCigar(SAMRecord rec) {
        Cigar origCigar = rec.getCigar();
        Assert.assertNotNull(origCigar);
        String originalCigarString = rec.getCigarString();

        // set the cigar to null and then reset the cigar string in order to force getCigar to decode it
        rec.setCigar(null);
        Assert.assertNull(rec.getCigar());
        rec.setCigarString(originalCigarString);
        rec.setValidationStringency(ValidationStringency.STRICT);
        rec.setHeader(null);
        Assert.assertTrue(rec.getValidationStringency() == ValidationStringency.STRICT);

        // force getCigar to decode the cigar string, validate that SAMRecord doesn't try to validate the cigar
        Cigar cig = rec.getCigar();
        Assert.assertNotNull(cig);
        String cigString = TextCigarCodec.encode(cig);
        Assert.assertEquals(cigString, originalCigarString);
    }

    @Test
    public void testNullHeadGetCigarSAM() {
        final SAMRecord sam = createTestRecordHelper();
        testNullHeaderCigar(sam);
    }

    @Test
    public void testNullHeadGetCigarBAM() {
        SAMRecord sam = createTestRecordHelper();
        SAMRecordFactory factory = new DefaultSAMRecordFactory();
        BAMRecord bamRec = factory.createBAMRecord(
                sam.getHeader(),
                sam.getReferenceIndex(),
                sam.getAlignmentStart(),
                (short) sam.getReadNameLength(),
                (short) sam.getMappingQuality(),
                0,
                sam.getCigarLength(),
                sam.getFlags(),
                sam.getReadLength(),
                sam.getMateReferenceIndex(),
                sam.getMateAlignmentStart(),
                0, null);

        bamRec.setCigarString(sam.getCigarString());

        testNullHeaderCigar(bamRec);
    }

    @Test
    public void testSetHeaderStrictValid() {
        SAMRecord sam = createTestRecordHelper();
        final SAMFileHeader samHeader = sam.getHeader();
        Integer originalRefIndex = sam.getReferenceIndex();
        Assert.assertTrue(SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX != originalRefIndex);

        // force re-resolution of the reference name
        sam.setHeaderStrict(samHeader);
        Assert.assertEquals(sam.getReferenceIndex(), originalRefIndex);
    }

    @Test
    public void testSetHeaderStrictValidHeaderless() {
        SAMRecord sam = createTestRecordHelper();
        final SAMFileHeader samHeader = sam.getHeader();
        Integer originalRefIndex = sam.getReferenceIndex();
        Assert.assertTrue(SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX != originalRefIndex);

        sam.setHeader(null);
        // force re-resolution of the reference name
        sam.setHeaderStrict(samHeader);
        Assert.assertEquals(sam.getReferenceIndex(), originalRefIndex);
    }

    @Test
    public void testSetHeaderStrictValidNewHeader() {
        final SAMRecord sam = createTestRecordHelper();
        final String origSequenceName = sam.getContig();

        final SAMFileHeader origSamHeader = sam.getHeader();
        final int origSequenceLength = origSamHeader.getSequence(origSequenceName).getSequenceLength();
        final SAMFileHeader newHeader = new SAMFileHeader();
        newHeader.addSequence(new SAMSequenceRecord(origSequenceName, origSequenceLength));

        // force re-resolution of the reference name against the new header
        sam.setHeaderStrict(newHeader);
        Assert.assertEquals(sam.getReferenceIndex(), 0);
    }

    @Test(expectedExceptions=IllegalArgumentException.class)
    public void testSetHeaderStrictInvalidReference() {
        SAMRecord sam = createTestRecordHelper();
        final SAMFileHeader samHeader = sam.getHeader();

        sam.setReferenceName("unresolvable");
        Assert.assertEquals(SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX, sam.getReferenceIndex());

        // throw on force re-resolution of the unresolvable reference name
        sam.setHeaderStrict(samHeader);
    }

    @Test(expectedExceptions=IllegalArgumentException.class)
    public void testSetHeaderStrictInvalidMateReference() {
        SAMRecord sam = createTestRecordHelper();
        final SAMFileHeader samHeader = sam.getHeader();

        sam.setMateReferenceName("unresolvable");
        Assert.assertEquals(SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX, sam.getMateReferenceIndex());

        // throw on force re-resolution of the unresolvable mate reference name
        sam.setHeaderStrict(samHeader);
    }

    @Test
    public void testSetHeaderStrictNull() {
        SAMRecord sam = createTestRecordHelper();
        Assert.assertNotNull(sam.getHeader());
        sam.setHeaderStrict(null);
        Assert.assertNull(sam.getHeader());
        Assert.assertNull(sam.mReferenceIndex);
    }

    // resolveIndexFromName

    @Test
    public void testResolveIndexResolvable() {
        final SAMRecord sam = createTestRecordHelper();
        final SAMFileHeader samHeader = sam.getHeader();
        final String contigName = sam.getContig();
        Assert.assertEquals(SAMRecord.resolveIndexFromName(contigName, samHeader, true), samHeader.getSequenceIndex(contigName));
    }

    @Test(expectedExceptions=IllegalStateException.class)
    public void testResolveIndexUnresolvableNullHeader() {
        SAMRecord.resolveIndexFromName("unresolvable", null, false);
    }

    @Test(expectedExceptions=IllegalArgumentException.class)
    public void testResolveIndexUnresolvableStrict() {
        final SAMFileHeader samHeader = new SAMFileHeader();
        SAMRecord.resolveIndexFromName("unresolvable", samHeader, true);
    }

    @Test
    public void testResolveIndexUnresolvableNotStrict() {
        final SAMFileHeader samHeader = new SAMFileHeader();
        Assert.assertEquals(SAMRecord.resolveIndexFromName("unresolvable", samHeader, false), null);
    }

    @Test
    public void testResolveIndexNoAlignment() {
        final SAMFileHeader samHeader = new SAMFileHeader();
        Assert.assertEquals(SAMRecord.resolveIndexFromName(
                SAMRecord.NO_ALIGNMENT_REFERENCE_NAME, samHeader, true), SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX);
    }

    @Test(expectedExceptions=IllegalStateException.class)
    public void testResolveIndexNullHeader() {
        SAMRecord.resolveIndexFromName("unresolvable", null, true);
    }

    // resolveNameFromIndex

    @Test
    public void testResolveNameResolvable() {
        final SAMRecord sam = createTestRecordHelper();
        final SAMFileHeader samHeader = sam.getHeader();
        final String contigName = sam.getContig();
        final Integer contigIndex = samHeader.getSequenceIndex(contigName);
        Assert.assertEquals(SAMRecord.resolveNameFromIndex(contigIndex, samHeader), contigName);
    }

    @Test(expectedExceptions=IllegalArgumentException.class)
    public void testResolveNameUnresolvable() {
        final SAMFileHeader samHeader = new SAMFileHeader();
        SAMRecord.resolveNameFromIndex(99, samHeader);
    }

    @Test
    public void testResolveNameNoAlignment() {
        final SAMFileHeader samHeader = new SAMFileHeader();
        Assert.assertEquals(SAMRecord.resolveNameFromIndex(
                SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX, samHeader), SAMRecord.NO_ALIGNMENT_REFERENCE_NAME);
    }

    @Test(expectedExceptions=IllegalStateException.class)
    public void testResolveNameNullHeader() {
        SAMRecord.resolveNameFromIndex(1, null);
    }

    @Test
    public void testReverseComplement() {
        final SAMRecord rec = createTestSamRec();

        rec.reverseComplement(Arrays.asList("Y1"), Arrays.asList("X1", "X2", "X3", "X4", "X5"), false);
        Assert.assertEquals(rec.getReadString(), "GTGTGTGTGT");
        Assert.assertEquals(rec.getBaseQualityString(), "IIIIIHHHHH");
        Assert.assertEquals(rec.getByteArrayAttribute("X1"), new byte[] {5,4,3,2,1});
        Assert.assertEquals(rec.getSignedShortArrayAttribute("X2"), new short[] {5,4,3,2,1});
        Assert.assertEquals(rec.getSignedIntArrayAttribute("X3"), new int[] {5,4,3,2,1});
        Assert.assertEquals(rec.getFloatArrayAttribute("X4"), new float[] {5.0f,4.0f,3.0f,2.0f,1.0f});
        Assert.assertEquals(rec.getStringAttribute("Y1"), "GTTTTCTTTT");
    }

    /**
     * Note that since strings are immutable the Y1 attribute, which is a String, is not reversed in the original even
     * if an in-place reverse complement occurred. The bases and qualities are byte[] so they are reversed if in-place
     * is true.
     */
    @DataProvider
    public Object [][] reverseComplementData() {
        return new Object[][]{
                {false, "ACACACACAC", "HHHHHIIIII", "AAAAGAAAAC", new byte[] {1,2,3,4,5}, new short[] {1,2,3,4,5}, new int[] {1,2,3,4,5}, new float[] {1,2,3,4,5}},
                {true, "GTGTGTGTGT", "IIIIIHHHHH", "AAAAGAAAAC", new byte[] {5,4,3,2,1}, new short[] {5,4,3,2,1}, new int[] {5,4,3,2,1}, new float[] {5,4,3,2,1}},
        };
    }

    @Test(dataProvider = "reverseComplementData")
    public void testSafeReverseComplement(boolean inplace, String bases, String quals, String y1, byte[] x1, short[] x2, int[] x3, float[] x4) throws CloneNotSupportedException {
        final SAMRecord original = createTestSamRec();
        final SAMRecord cloneOfOriginal = (SAMRecord) original.clone();
        //Runs a copy (rather than in-place) reverseComplement
        cloneOfOriginal.reverseComplement(Arrays.asList("Y1"), Arrays.asList("X1", "X2", "X3", "X4", "X5"), inplace);

        Assert.assertEquals(original.getReadString(), bases);
        Assert.assertEquals(original.getBaseQualityString(), quals);
        Assert.assertEquals(original.getByteArrayAttribute("X1"), x1);
        Assert.assertEquals(original.getSignedShortArrayAttribute("X2"), x2);
        Assert.assertEquals(original.getSignedIntArrayAttribute("X3"), x3);
        Assert.assertEquals(original.getFloatArrayAttribute("X4"), x4);
        Assert.assertEquals(original.getStringAttribute("Y1"), y1);

        Assert.assertEquals(cloneOfOriginal.getReadString(), "GTGTGTGTGT");
        Assert.assertEquals(cloneOfOriginal.getBaseQualityString(), "IIIIIHHHHH");
        Assert.assertEquals(cloneOfOriginal.getByteArrayAttribute("X1"), new byte[] {5,4,3,2,1});
        Assert.assertEquals(cloneOfOriginal.getSignedShortArrayAttribute("X2"), new short[] {5,4,3,2,1});
        Assert.assertEquals(cloneOfOriginal.getSignedIntArrayAttribute("X3"), new int[] {5,4,3,2,1});
        Assert.assertEquals(cloneOfOriginal.getFloatArrayAttribute("X4"), new float[] {5.0f,4.0f,3.0f,2.0f,1.0f});
        Assert.assertEquals(cloneOfOriginal.getStringAttribute("Y1"), "GTTTTCTTTT");

    }

    public SAMRecord createTestSamRec() {
        final SAMFileHeader header = new SAMFileHeader();
        final SAMRecord rec = new SAMRecord(header);
        rec.setReadString("ACACACACAC");
        rec.setBaseQualityString("HHHHHIIIII");
        rec.setAttribute("X1", new byte[] {1,2,3,4,5});
        rec.setAttribute("X2", new short[] {1,2,3,4,5});
        rec.setAttribute("X3", new int[] {1,2,3,4,5});
        rec.setAttribute("X4", new float[] {1.0f,2.0f,3.0f,4.0f,5.0f});
        rec.setAttribute("Y1", "AAAAGAAAAC");

        return(rec);
    }

    @DataProvider
    public Object [][] readBasesArrayGetReadLengthData() {
        return new Object[][]{
                { null, 0 },
                { SAMRecord.NULL_SEQUENCE, 0 },
                { new byte[] {'A', 'C'}, 2 }
        };
    }

    @Test(dataProvider = "readBasesArrayGetReadLengthData")
    public void testReadBasesGetReadLength(final byte[] readBases, final int readLength) {
        final SAMRecord sam = createTestRecordHelper();
        sam.setReadBases(readBases);
        Assert.assertEquals(sam.getReadLength(), readLength);
    }

    @DataProvider
    public Object [][] readBasesStringGetReadLengthData() {
        return new Object[][]{
                { null, 0 },
                { SAMRecord.NULL_SEQUENCE_STRING, 0 },
                { "AC", 2 }
        };
    }

    @Test(dataProvider = "readBasesStringGetReadLengthData")
    public void testReadStringGetReadLength(final String readBases, final int readLength) {
        final SAMRecord sam = createTestRecordHelper();
        sam.setReadString(readBases);
        Assert.assertEquals(sam.getReadLength(), readLength);
    }

    @DataProvider(name = "attributeAccessTestData")
    private Object[][] hasAttributeTestData() throws IOException {
        final SamReader reader = SamReaderFactory.makeDefault().open(new File("src/test/resources/htsjdk/samtools/SAMIntegerTagTest/variousAttributes.sam"));
        final SAMRecord samRecordWithAttributes = reader.iterator().next();
        final SAMRecord samRecordWithoutAnyAttributes = new SAMRecord(reader.getFileHeader());
        reader.close();

        return new Object[][] {
                {samRecordWithAttributes, "MF", true},
                {samRecordWithAttributes, "Nm", true},
                {samRecordWithAttributes, "H0", true},
                {samRecordWithAttributes, "H1", true},
                {samRecordWithAttributes, "SB", true},
                {samRecordWithAttributes, "UB", true},
                {samRecordWithAttributes, "SS", true},
                {samRecordWithAttributes, "US", true},
                {samRecordWithAttributes, "SI", true},
                {samRecordWithAttributes, "I2", true},
                {samRecordWithAttributes, "UI", true},

                {samRecordWithAttributes, "AS", false},

                {samRecordWithoutAnyAttributes, "RG", false}
        };
    }

    @Test(dataProvider = "attributeAccessTestData")
    public void testHasAttribute(final SAMRecord samRecord, final String tag, final boolean expectedHasAttribute) {
        Assert.assertEquals(samRecord.hasAttribute(tag), expectedHasAttribute);
    }

    @Test
    public void test_setAttribute_empty_array() {
        final SAMFileHeader header = new SAMFileHeader();
        final SAMRecord record = new SAMRecord(header);
        Assert.assertNull(record.getStringAttribute(ARRAY_TAG));
        record.setAttribute(ARRAY_TAG, new int[0]);
        Assert.assertNotNull(record.getSignedIntArrayAttribute(ARRAY_TAG));
        Assert.assertEquals(record.getSignedIntArrayAttribute(ARRAY_TAG), new int[0]);
        Assert.assertEquals(record.getAttribute(ARRAY_TAG), new char[0]);
        record.setAttribute(ARRAY_TAG, null);
        Assert.assertNull(record.getStringAttribute(ARRAY_TAG));
    }

    @Test
    public void test_setUnsignedArrayAttribute_empty_array() {
        final SAMFileHeader header = new SAMFileHeader();
        final SAMRecord record = new SAMRecord(header);
        Assert.assertNull(record.getStringAttribute(ARRAY_TAG));
        record.setUnsignedArrayAttribute(ARRAY_TAG, new int[0]);
        Assert.assertNotNull(record.getUnsignedIntArrayAttribute(ARRAY_TAG));
        Assert.assertEquals(record.getUnsignedIntArrayAttribute(ARRAY_TAG), new int[0]);
        Assert.assertEquals(record.getAttribute(ARRAY_TAG), new char[0]);
        record.setAttribute(ARRAY_TAG, null);
        Assert.assertNull(record.getStringAttribute(ARRAY_TAG));
    }

    private static Object[][] getEmptyArrays() {
        return new Object[][]{
                {new int[0], int[].class},
                {new short[0], short[].class},
                {new byte[0], byte[].class},
                {new float[0], float[].class},
        };
    }

    private static Object[][] getFileExtensions(){
        return new Object[][]{
                {FileExtensions.BAM}, {FileExtensions.SAM}, {FileExtensions.CRAM}
        };
    }

    @DataProvider
    public Object[][] getEmptyArraysAndExtensions(){
        return TestNGUtils.cartesianProduct(getEmptyArrays(), getFileExtensions());
    }

    @Test(dataProvider = "getEmptyArraysAndExtensions")
    public void testWriteSamWithEmptyArray(Object emptyArray, Class<?> arrayClass, String fileExtension) throws IOException {
        Assert.assertEquals(emptyArray.getClass(), arrayClass);
        Assert.assertEquals(Array.getLength(emptyArray), 0);

        final SAMRecordSetBuilder samRecords = new SAMRecordSetBuilder();
        samRecords.addFrag("Read", 0, 100, false);
        final SAMRecord record = samRecords.getRecords().iterator().next();
        record.setAttribute(ARRAY_TAG, emptyArray);
        checkArrayIsEmpty(ARRAY_TAG, record, arrayClass);

        final Path tmp = Files.createTempFile("tmp", fileExtension);
        IOUtil.deleteOnExit(tmp);

        final SAMFileWriterFactory writerFactory = new SAMFileWriterFactory()
                .setCreateMd5File(false)
                .setCreateIndex(false);
        final Path reference = IOUtil.getPath("src/test/resources/htsjdk/samtools/one-contig.fasta");
        try (final SAMFileWriter samFileWriter = writerFactory.makeWriter(samRecords.getHeader(), false, tmp, reference)) {
            samFileWriter.addAlignment(record);
        }

        try (final SamReader reader = SamReaderFactory.makeDefault()
                .referenceSequence(reference)
                .open(tmp)) {
            final SAMRecordIterator iterator = reader.iterator();
            Assert.assertTrue(iterator.hasNext());
            final SAMRecord recordFromDisk = iterator.next();
            checkArrayIsEmpty(ARRAY_TAG, recordFromDisk, arrayClass);
        }
    }

    private static void checkArrayIsEmpty(String arrayTag, SAMRecord recordFromDisk, Class<?> expectedClass) {
        final Object attribute = recordFromDisk.getAttribute(arrayTag);
        Assert.assertNotNull(attribute);
        Assert.assertEquals(attribute.getClass(), expectedClass);
        Assert.assertEquals(Array.getLength(attribute), 0);
    }

    @Test
    public void testAlignmentBlockCacheInvalidation() {
        final SAMRecord rec = createTestRecordHelper();

        rec.getAlignmentBlocks();
        Assert.assertEquals(1, rec.getAlignmentBlocks().get(0).getReferenceStart());
        rec.setAlignmentStart(100);
        Assert.assertEquals(100, rec.getAlignmentBlocks().get(0).getReferenceStart());
    }
}
