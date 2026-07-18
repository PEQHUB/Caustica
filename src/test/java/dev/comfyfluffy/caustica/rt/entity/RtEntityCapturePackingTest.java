package dev.comfyfluffy.caustica.rt.entity;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class RtEntityCapturePackingTest {
    @Test
    void stablePartitionPreservesTriangleAndPrimitiveBits() {
        RtEntityCapture capture = new RtEntityCapture();
        int[] buckets = {1, 0, 1, 0};
        for (int tri = 0; tri < buckets.length; tri++) {
            capture.idx.add(tri * 3);
            capture.idx.add(tri * 3 + 1);
            capture.idx.add(tri * 3 + 2);
            capture.alphaBuckets.add(buckets[tri]);
            for (int lane = 0; lane < 12; lane++) {
                capture.prim.add(Float.intBitsToFloat(0x3f000000 + tri * 16 + lane));
            }
        }

        RtEntityCapture.PackedGeometry packed = capture.packGeometry();
        assertFalse(packed.orderedFastPath());
        assertArrayEquals(new int[] {3, 4, 5, 9, 10, 11, 0, 1, 2, 6, 7, 8},
                packed.indices().toIntArray());
        assertArrayEquals(new int[] {2, 2}, packed.copyBucketTris());
        int[] expectedTriangleOrder = {1, 3, 0, 2};
        for (int output = 0; output < expectedTriangleOrder.length; output++) {
            int source = expectedTriangleOrder[output];
            for (int lane = 0; lane < 12; lane++) {
                int expected = 0x3f000000 + source * 16 + lane;
                int actual = Float.floatToRawIntBits(packed.primitives().getFloat(output * 12 + lane));
                assertEquals(expected, actual, "primitive bit mismatch at triangle " + output + ", lane " + lane);
            }
        }
    }

    @Test
    void orderedCapturesReturnSourceListsWithExactBits() {
        assertOrderedIdentity(new int[] {0, 0, 0, 0});
        assertOrderedIdentity(new int[] {0, 0, 1, 1});
        assertOrderedIdentity(new int[] {1, 1, 1});
        assertOrderedIdentity(new int[0]);
    }

    @Test
    void randomizedCapturesMatchReferenceStablePartitionBitwise() {
        Random random = new Random(0xCA0571CAL);
        for (int run = 0; run < 256; run++) {
            int triangleCount = random.nextInt(65);
            int[] buckets = new int[triangleCount];
            for (int tri = 0; tri < triangleCount; tri++) {
                buckets[tri] = random.nextInt(2);
            }
            RtEntityCapture capture = capture(buckets, random);
            RtEntityCapture.PackedGeometry packed = capture.packGeometry();
            List<Integer> order = new ArrayList<>(triangleCount);
            for (int bucket = 0; bucket < 2; bucket++) {
                for (int tri = 0; tri < triangleCount; tri++) {
                    if (buckets[tri] == bucket) order.add(tri);
                }
            }
            assertArrayEquals(order.stream().flatMapToInt(tri ->
                    java.util.stream.IntStream.of(tri * 3, tri * 3 + 1, tri * 3 + 2)).toArray(),
                    packed.indices().toIntArray());
            for (int output = 0; output < order.size(); output++) {
                int source = order.get(output);
                for (int lane = 0; lane < 12; lane++) {
                    assertEquals(Float.floatToRawIntBits(capture.prim.getFloat(source * 12 + lane)),
                            Float.floatToRawIntBits(packed.primitives().getFloat(output * 12 + lane)));
                }
            }
        }
    }

    private static void assertOrderedIdentity(int[] buckets) {
        RtEntityCapture capture = capture(buckets, new Random(buckets.length));
        RtEntityCapture.PackedGeometry packed = capture.packGeometry();
        assertTrue(packed.orderedFastPath());
        assertSame(capture.idx, packed.indices());
        assertSame(capture.prim, packed.primitives());
        assertArrayEquals(capture.idx.toIntArray(), packed.indices().toIntArray());
        assertArrayEquals(capture.prim.toFloatArray(), packed.primitives().toFloatArray());
    }

    private static RtEntityCapture capture(int[] buckets, Random random) {
        RtEntityCapture capture = new RtEntityCapture();
        for (int tri = 0; tri < buckets.length; tri++) {
            capture.idx.add(tri * 3);
            capture.idx.add(tri * 3 + 1);
            capture.idx.add(tri * 3 + 2);
            capture.alphaBuckets.add(buckets[tri]);
            for (int lane = 0; lane < 12; lane++) {
                capture.prim.add(Float.intBitsToFloat(random.nextInt()));
            }
        }
        return capture;
    }
}
