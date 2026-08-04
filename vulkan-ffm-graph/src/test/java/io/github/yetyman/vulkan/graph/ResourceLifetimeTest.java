package io.github.yetyman.vulkan.graph;

import io.github.yetyman.vulkan.graph.resources.ResourceLifetime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ResourceLifetimeTest {

    @Test
    void newLifetime_isInvalid() {
        ResourceLifetime lt = new ResourceLifetime();
        assertFalse(lt.isValid());
        assertEquals(-1, lt.firstWritePass());
        assertEquals(-1, lt.lastReadPass());
    }

    @Test
    void recordWrite_setsFirstWrite() {
        ResourceLifetime lt = new ResourceLifetime();
        lt.recordWrite(3);
        assertTrue(lt.isValid());
        assertEquals(3, lt.firstWritePass());
        assertEquals(3, lt.lastReadPass()); // write extends alive range
    }

    @Test
    void recordRead_extendsLastRead() {
        ResourceLifetime lt = new ResourceLifetime();
        lt.recordWrite(1);
        lt.recordRead(5);
        assertEquals(1, lt.firstWritePass());
        assertEquals(5, lt.lastReadPass());
    }

    @Test
    void overlaps_trueForOverlapping() {
        ResourceLifetime a = new ResourceLifetime();
        a.recordWrite(0);
        a.recordRead(3);

        ResourceLifetime b = new ResourceLifetime();
        b.recordWrite(2);
        b.recordRead(5);

        assertTrue(a.overlaps(b));
        assertTrue(b.overlaps(a));
    }

    @Test
    void overlaps_falseForNonOverlapping() {
        ResourceLifetime a = new ResourceLifetime();
        a.recordWrite(0);
        a.recordRead(2);

        ResourceLifetime b = new ResourceLifetime();
        b.recordWrite(3);
        b.recordRead(5);

        assertFalse(a.overlaps(b));
        assertFalse(b.overlaps(a));
    }

    @Test
    void overlaps_adjacentDoesNotOverlap() {
        ResourceLifetime a = new ResourceLifetime();
        a.recordWrite(0);
        a.recordRead(2);

        ResourceLifetime b = new ResourceLifetime();
        b.recordWrite(3);
        b.recordRead(4);

        assertFalse(a.overlaps(b));
    }

    @Test
    void overlaps_falseWhenEitherInvalid() {
        ResourceLifetime a = new ResourceLifetime();
        ResourceLifetime b = new ResourceLifetime();
        b.recordWrite(0);
        b.recordRead(2);

        assertFalse(a.overlaps(b));
        assertFalse(b.overlaps(a));
    }
}
