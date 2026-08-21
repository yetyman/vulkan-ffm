package io.github.yetyman.vulkan.buffers;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DirtyStrategy implementations: AlwaysDirtyStrategy,
 * RangeCoalescingDirtyStrategy, and BitSetDirtyStrategy.
 */
class DirtyStrategyTest {

    // Helper to collect all regions from an iterator into a list of [offset, size] pairs
    private static List<long[]> collectRegions(DirtyStrategy strategy) {
        List<long[]> result = new ArrayList<>();
        DirtyRegionIterator it = strategy.dirtyRegions();
        while (it.hasNext()) {
            it.next();
            result.add(new long[]{it.offset(), it.size()});
        }
        return result;
    }

    // =========================================================================
    // AlwaysDirtyStrategy
    // =========================================================================

    @Nested
    class AlwaysDirtyTests {

        @Test
        void alwaysReportsDirty() {
            AlwaysDirtyStrategy s = new AlwaysDirtyStrategy(1024);
            assertTrue(s.isDirty());
        }

        @Test
        void alwaysReturnsSingleFullRegion() {
            AlwaysDirtyStrategy s = new AlwaysDirtyStrategy(4096);
            assertEquals(1, s.dirtyRegionCount());

            List<long[]> regions = collectRegions(s);
            assertEquals(1, regions.size());
            assertEquals(0, regions.get(0)[0]);
            assertEquals(4096, regions.get(0)[1]);
        }

        @Test
        void markDirtyIsNoOp() {
            AlwaysDirtyStrategy s = new AlwaysDirtyStrategy(512);
            s.markDirty(100, 50);
            // Still reports full buffer
            List<long[]> regions = collectRegions(s);
            assertEquals(1, regions.size());
            assertEquals(0, regions.get(0)[0]);
            assertEquals(512, regions.get(0)[1]);
        }

        @Test
        void clearIsNoOp() {
            AlwaysDirtyStrategy s = new AlwaysDirtyStrategy(256);
            s.clear();
            assertTrue(s.isDirty()); // still always dirty
        }
    }

    // =========================================================================
    // RangeCoalescingDirtyStrategy
    // =========================================================================

    @Nested
    class RangeCoalescingTests {

        @Test
        void startsClean() {
            RangeCoalescingDirtyStrategy s = new RangeCoalescingDirtyStrategy();
            assertFalse(s.isDirty());
            assertEquals(0, s.dirtyRegionCount());
        }

        @Test
        void singleDirtyRegion() {
            RangeCoalescingDirtyStrategy s = new RangeCoalescingDirtyStrategy();
            s.markDirty(100, 50);

            assertTrue(s.isDirty());
            assertEquals(1, s.dirtyRegionCount());

            List<long[]> regions = collectRegions(s);
            assertEquals(100, regions.get(0)[0]);
            assertEquals(50, regions.get(0)[1]);
        }

        @Test
        void nonOverlappingRegionsStaySeparate() {
            RangeCoalescingDirtyStrategy s = new RangeCoalescingDirtyStrategy(0); // no gap merging
            s.markDirty(0, 100);
            s.markDirty(500, 100);

            assertEquals(2, s.dirtyRegionCount());

            List<long[]> regions = collectRegions(s);
            assertEquals(0, regions.get(0)[0]);
            assertEquals(100, regions.get(0)[1]);
            assertEquals(500, regions.get(1)[0]);
            assertEquals(100, regions.get(1)[1]);
        }

        @Test
        void overlappingRegionsMerge() {
            RangeCoalescingDirtyStrategy s = new RangeCoalescingDirtyStrategy(0);
            s.markDirty(0, 100);
            s.markDirty(50, 100); // overlaps [0, 100) by 50 bytes

            assertEquals(1, s.dirtyRegionCount());

            List<long[]> regions = collectRegions(s);
            assertEquals(0, regions.get(0)[0]);
            assertEquals(150, regions.get(0)[1]); // merged to [0, 150)
        }

        @Test
        void adjacentRegionsMerge() {
            RangeCoalescingDirtyStrategy s = new RangeCoalescingDirtyStrategy(0);
            s.markDirty(0, 100);
            s.markDirty(100, 100); // starts exactly where previous ends

            assertEquals(1, s.dirtyRegionCount());

            List<long[]> regions = collectRegions(s);
            assertEquals(0, regions.get(0)[0]);
            assertEquals(200, regions.get(0)[1]);
        }

        @Test
        void gapThresholdMergesCloseRegions() {
            RangeCoalescingDirtyStrategy s = new RangeCoalescingDirtyStrategy(256);
            s.markDirty(0, 100);
            s.markDirty(300, 100); // gap is 200 bytes, within 256 threshold

            assertEquals(1, s.dirtyRegionCount());

            List<long[]> regions = collectRegions(s);
            assertEquals(0, regions.get(0)[0]);
            assertEquals(400, regions.get(0)[1]); // merged including gap
        }

        @Test
        void gapThresholdKeepsDistantRegionsSeparate() {
            RangeCoalescingDirtyStrategy s = new RangeCoalescingDirtyStrategy(256);
            s.markDirty(0, 100);
            s.markDirty(1000, 100); // gap is 900 bytes, exceeds 256 threshold

            assertEquals(2, s.dirtyRegionCount());
        }

        @Test
        void clearResetsState() {
            RangeCoalescingDirtyStrategy s = new RangeCoalescingDirtyStrategy();
            s.markDirty(0, 100);
            s.markDirty(200, 100);
            assertTrue(s.isDirty());

            s.clear();
            assertFalse(s.isDirty());
            assertEquals(0, s.dirtyRegionCount());
        }

        @Test
        void multipleOverlappingMarksMergeCorrectly() {
            RangeCoalescingDirtyStrategy s = new RangeCoalescingDirtyStrategy(0);
            s.markDirty(100, 50);  // [100, 150)
            s.markDirty(120, 80);  // [120, 200) - overlaps, extends
            s.markDirty(180, 50);  // [180, 230) - overlaps extended region

            assertEquals(1, s.dirtyRegionCount());

            List<long[]> regions = collectRegions(s);
            assertEquals(100, regions.get(0)[0]);
            assertEquals(130, regions.get(0)[1]); // [100, 230)
        }

        @Test
        void insertionOrderDoesNotMatter() {
            RangeCoalescingDirtyStrategy s = new RangeCoalescingDirtyStrategy(0);
            s.markDirty(500, 100); // [500, 600)
            s.markDirty(100, 100); // [100, 200)
            s.markDirty(300, 100); // [300, 400)

            assertEquals(3, s.dirtyRegionCount());

            List<long[]> regions = collectRegions(s);
            assertEquals(100, regions.get(0)[0]);
            assertEquals(300, regions.get(1)[0]);
            assertEquals(500, regions.get(2)[0]);
        }

        @Test
        void zeroSizeMarkIsIgnored() {
            RangeCoalescingDirtyStrategy s = new RangeCoalescingDirtyStrategy();
            s.markDirty(100, 0);
            assertFalse(s.isDirty());
        }

        @Test
        void threadSafetyBasic() throws InterruptedException {
            RangeCoalescingDirtyStrategy s = new RangeCoalescingDirtyStrategy(0);
            int threadCount = 8;
            int marksPerThread = 1000;
            Thread[] threads = new Thread[threadCount];

            for (int t = 0; t < threadCount; t++) {
                int threadId = t;
                threads[t] = Thread.ofVirtual().start(() -> {
                    for (int i = 0; i < marksPerThread; i++) {
                        long offset = (long) threadId * 10000 + (long) i * 8;
                        s.markDirty(offset, 8);
                    }
                });
            }
            for (Thread thread : threads) thread.join();

            assertTrue(s.isDirty());
            // Each thread writes to non-overlapping regions, so we should have some regions
            assertTrue(s.dirtyRegionCount() > 0);
        }
    }

    // =========================================================================
    // BitSetDirtyStrategy
    // =========================================================================

    @Nested
    class BitSetTests {

        @Test
        void startsClean() {
            BitSetDirtyStrategy s = new BitSetDirtyStrategy(65536);
            assertFalse(s.isDirty());
            assertEquals(0, s.dirtyRegionCount());
        }

        @Test
        void singlePageDirty() {
            BitSetDirtyStrategy s = new BitSetDirtyStrategy(65536, 4096);
            s.markDirty(0, 1); // touches first page only

            assertTrue(s.isDirty());
            assertEquals(1, s.dirtyRegionCount());

            List<long[]> regions = collectRegions(s);
            assertEquals(0, regions.get(0)[0]);
            assertEquals(4096, regions.get(0)[1]); // entire first page
        }

        @Test
        void writeSpanningTwoPages() {
            BitSetDirtyStrategy s = new BitSetDirtyStrategy(65536, 4096);
            s.markDirty(4000, 200); // starts in page 0, ends in page 1

            assertEquals(1, s.dirtyRegionCount()); // contiguous pages merge

            List<long[]> regions = collectRegions(s);
            assertEquals(0, regions.get(0)[0]);
            assertEquals(8192, regions.get(0)[1]); // pages 0 and 1
        }

        @Test
        void nonContiguousPagesDirty() {
            BitSetDirtyStrategy s = new BitSetDirtyStrategy(65536, 4096);
            s.markDirty(0, 1);     // page 0
            s.markDirty(8192, 1);  // page 2

            assertEquals(2, s.dirtyRegionCount());

            List<long[]> regions = collectRegions(s);
            assertEquals(0, regions.get(0)[0]);
            assertEquals(4096, regions.get(0)[1]);
            assertEquals(8192, regions.get(1)[0]);
            assertEquals(4096, regions.get(1)[1]);
        }

        @Test
        void lastPageClampedToBufferSize() {
            BitSetDirtyStrategy s = new BitSetDirtyStrategy(10000, 4096);
            // Buffer is 10000 bytes, last page starts at 8192, extends to 10000 (not 12288)
            s.markDirty(9000, 100); // touches last page

            List<long[]> regions = collectRegions(s);
            assertEquals(1, regions.size());
            assertEquals(8192, regions.get(0)[0]);
            assertEquals(10000 - 8192, regions.get(0)[1]); // clamped to buffer end
        }

        @Test
        void clearResetsState() {
            BitSetDirtyStrategy s = new BitSetDirtyStrategy(65536, 4096);
            s.markDirty(0, 65536); // all pages dirty
            assertTrue(s.isDirty());

            s.clear();
            assertFalse(s.isDirty());
            assertEquals(0, s.dirtyRegionCount());
        }

        @Test
        void smallPageGranularity() {
            BitSetDirtyStrategy s = new BitSetDirtyStrategy(1024, 64);
            s.markDirty(128, 64); // page 2

            List<long[]> regions = collectRegions(s);
            assertEquals(1, regions.size());
            assertEquals(128, regions.get(0)[0]);
            assertEquals(64, regions.get(0)[1]);
        }

        @Test
        void zeroSizeMarkIsIgnored() {
            BitSetDirtyStrategy s = new BitSetDirtyStrategy(65536);
            s.markDirty(100, 0);
            assertFalse(s.isDirty());
        }

        @Test
        void threadSafetyBasic() throws InterruptedException {
            BitSetDirtyStrategy s = new BitSetDirtyStrategy(1024 * 1024, 4096);
            int threadCount = 8;
            int marksPerThread = 1000;
            Thread[] threads = new Thread[threadCount];

            for (int t = 0; t < threadCount; t++) {
                int threadId = t;
                threads[t] = Thread.ofVirtual().start(() -> {
                    for (int i = 0; i < marksPerThread; i++) {
                        long offset = (long) threadId * 128 * 1024 + (long) i * 64;
                        s.markDirty(offset, 64);
                    }
                });
            }
            for (Thread thread : threads) thread.join();

            assertTrue(s.isDirty());
            assertTrue(s.dirtyRegionCount() > 0);
        }
    }

    // =========================================================================
    // DirtyStrategy.forSize auto-selection
    // =========================================================================

    @Nested
    class AutoSelectionTests {

        @Test
        void smallBufferGetsAlwaysDirty() {
            DirtyStrategy s = DirtyStrategy.forSize(2048);
            assertInstanceOf(AlwaysDirtyStrategy.class, s);
        }

        @Test
        void mediumBufferGetsRangeCoalescing() {
            DirtyStrategy s = DirtyStrategy.forSize(100_000);
            assertInstanceOf(RangeCoalescingDirtyStrategy.class, s);
        }

        @Test
        void largeBufferGetsBitSet() {
            DirtyStrategy s = DirtyStrategy.forSize(2 * 1024 * 1024);
            assertInstanceOf(BitSetDirtyStrategy.class, s);
        }

        @Test
        void boundaryAt4KB() {
            // < 4096 -> AlwaysDirty
            assertInstanceOf(AlwaysDirtyStrategy.class, DirtyStrategy.forSize(4095));
            // >= 4096 -> RangeCoalescing
            assertInstanceOf(RangeCoalescingDirtyStrategy.class, DirtyStrategy.forSize(4096));
        }

        @Test
        void boundaryAt1MB() {
            // < 1MB -> RangeCoalescing
            assertInstanceOf(RangeCoalescingDirtyStrategy.class, DirtyStrategy.forSize(1024 * 1024 - 1));
            // >= 1MB -> BitSet
            assertInstanceOf(BitSetDirtyStrategy.class, DirtyStrategy.forSize(1024 * 1024));
        }
    }
}
