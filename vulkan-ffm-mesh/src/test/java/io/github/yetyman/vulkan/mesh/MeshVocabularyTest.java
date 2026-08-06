package io.github.yetyman.vulkan.mesh;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Layer 0 is pure description with no Vulkan calls and no GPU resources, so it is fully testable
 * without a device. These tests pin the properties the rest of the module depends on.
 */
class MeshVocabularyTest {

    // --- AttributeSemantic ---

    @Test
    void semanticsAreInternedByIdentity() {
        assertSame(AttributeSemantic.POSITION, AttributeSemantic.of("position"));
        assertSame(AttributeSemantic.POSITION, AttributeSemantic.of("Position"));
        assertSame(AttributeSemantic.POSITION, AttributeSemantic.of("  POSITION  "));
    }

    @Test
    void indexedSemanticsInternPerIndex() {
        assertSame(AttributeSemantic.TEXCOORD(0), AttributeSemantic.TEXCOORD(0));
        assertSame(AttributeSemantic.TEXCOORD(1), AttributeSemantic.TEXCOORD(1));
        assertFalse(AttributeSemantic.TEXCOORD(0) == AttributeSemantic.TEXCOORD(1));
    }

    @Test
    void arbitrarySemanticsAreAllowed() {
        AttributeSemantic custom = AttributeSemantic.of("sdfGradient", 3);
        assertSame(custom, AttributeSemantic.of("sdfgradient"));
        assertEquals("sdfGradient", custom.name());
        assertEquals(3, custom.componentCountHint());
    }

    @Test
    void blankSemanticRejected() {
        assertThrows(IllegalArgumentException.class, () -> AttributeSemantic.of("  "));
    }

    // --- AttributeFormat ---

    @Test
    void formatSizesAreComponentTimesCount() {
        assertEquals(12, AttributeFormat.F32x3.byteSize());
        assertEquals(16, AttributeFormat.F32x4.byteSize());
        assertEquals(4, AttributeFormat.U8x4_NORM.byteSize());
        assertEquals(8, AttributeFormat.S16x4_NORM.byteSize());
        assertEquals(64, AttributeFormat.F32x16.byteSize());
    }

    @Test
    void scalarFormatsMapToVkFormats() {
        assertTrue(AttributeFormat.F32x3.isVertexInputCapable());
        assertTrue(AttributeFormat.F32x3.vertexInputFormat().isPresent());
        assertTrue(AttributeFormat.U8x4_NORM.isVertexInputCapable());
        assertTrue(AttributeFormat.U32x2.isVertexInputCapable());
    }

    @Test
    void packedFormatsNeedNotHaveVkFormats() {
        // The whole point: a shader-decoded encoding is a first-class format.
        assertFalse(AttributeFormat.OCT16.isVertexInputCapable());
        assertTrue(AttributeFormat.OCT16.vertexInputFormat().isEmpty());
        assertEquals(4, AttributeFormat.OCT16.byteSize());
        assertEquals(ComponentType.PACKED, AttributeFormat.OCT16.componentType());
    }

    @Test
    void packedFormatsMayStillCarryAVkFormat() {
        assertTrue(AttributeFormat.R10G10B10A2_NORM.isVertexInputCapable());
        assertEquals(4, AttributeFormat.R10G10B10A2_NORM.byteSize());
    }

    @Test
    void sixteenComponentFloatHasNoVkFormat() {
        // A mat4 attribute exceeds four components, so there is no single VkFormat for it.
        assertFalse(AttributeFormat.F32x16.isVertexInputCapable());
    }

    @Test
    void normalizedRejectedForFloatComponents() {
        assertThrows(IllegalArgumentException.class, () -> AttributeFormat.of(ComponentType.F32, 3, true));
    }

    @Test
    void packedFactoryRejectedForScalarPath() {
        assertThrows(IllegalArgumentException.class, () -> AttributeFormat.of(ComponentType.PACKED, 1, false));
    }

    // --- MeshLayout: arrangements ---

    @Test
    void interleavedPacksAttributesIntoOneStream() {
        MeshLayout layout = MeshLayout.builder()
                .stream(0)
                .attribute(AttributeSemantic.POSITION, AttributeFormat.F32x3)
                .attribute(AttributeSemantic.NORMAL, AttributeFormat.F32x3)
                .attribute(AttributeSemantic.TEXCOORD(0), AttributeFormat.F32x2)
                .build();

        assertEquals(1, layout.streamCount());
        assertEquals(32, layout.strideOf(0));
        assertEquals(0, layout.offsetOf(AttributeSemantic.POSITION));
        assertEquals(12, layout.offsetOf(AttributeSemantic.NORMAL));
        assertEquals(24, layout.offsetOf(AttributeSemantic.TEXCOORD(0)));
    }

    @Test
    void planarGivesEachAttributeItsOwnTightStream() {
        LinkedHashMap<AttributeSemantic, AttributeFormat> attrs = new LinkedHashMap<>();
        attrs.put(AttributeSemantic.POSITION, AttributeFormat.F32x3);
        attrs.put(AttributeSemantic.NORMAL, AttributeFormat.F32x3);
        MeshLayout layout = MeshLayout.planar(attrs);

        assertEquals(2, layout.streamCount());
        assertEquals(12, layout.strideOf(0));
        assertEquals(12, layout.strideOf(1));
        assertEquals(0, layout.streamOf(AttributeSemantic.POSITION));
        assertEquals(1, layout.streamOf(AttributeSemantic.NORMAL));
        assertEquals(0, layout.offsetOf(AttributeSemantic.NORMAL));
    }

    @Test
    void hybridKeepsPositionDenseInItsOwnStream() {
        // The depth-prepass and meshlet-culling arrangement: position alone, rest interleaved.
        MeshLayout layout = MeshLayout.builder()
                .stream(0).attribute(AttributeSemantic.POSITION, AttributeFormat.F32x3)
                .stream(1)
                .attribute(AttributeSemantic.NORMAL, AttributeFormat.OCT16)
                .attribute(AttributeSemantic.TEXCOORD(0), AttributeFormat.S16x2_NORM)
                .build();

        assertEquals(2, layout.streamCount());
        assertEquals(12, layout.strideOf(0));
        assertEquals(8, layout.strideOf(1));
        assertEquals(0, layout.offsetOf(AttributeSemantic.NORMAL));
        assertEquals(4, layout.offsetOf(AttributeSemantic.TEXCOORD(0)));
    }

    @Test
    void instanceStreamsCarryTheirRate() {
        AttributeSemantic instanceTransform = AttributeSemantic.of("instanceTransform");
        MeshLayout layout = MeshLayout.builder()
                .stream(0).attribute(AttributeSemantic.POSITION, AttributeFormat.F32x3)
                .instanceStream(1).attribute(instanceTransform, AttributeFormat.F32x16)
                .build();

        assertEquals(InputRate.VERTEX, layout.inputRateOf(0));
        assertEquals(InputRate.INSTANCE, layout.inputRateOf(1));
        assertEquals(64, layout.strideOf(1));
    }

    @Test
    void explicitStrideAllowsPaddingButNotOverlap() {
        MeshLayout layout = MeshLayout.builder()
                .stream(0)
                .attribute(AttributeSemantic.POSITION, AttributeFormat.F32x3)
                .stride(0, 16)
                .build();
        assertEquals(16, layout.strideOf(0));

        assertThrows(IllegalStateException.class, () -> MeshLayout.builder()
                .stream(0)
                .attribute(AttributeSemantic.POSITION, AttributeFormat.F32x3)
                .stride(0, 8)
                .build());
    }

    @Test
    void explicitOffsetsMatchExternalLayouts() {
        MeshLayout layout = MeshLayout.builder()
                .stream(0)
                .attributeAt(AttributeSemantic.POSITION, AttributeFormat.F32x3, 0)
                .attributeAt(AttributeSemantic.NORMAL, AttributeFormat.F32x3, 16)
                .build();
        assertEquals(16, layout.offsetOf(AttributeSemantic.NORMAL));
        assertEquals(28, layout.strideOf(0));
    }

    @Test
    void duplicateAttributeRejected() {
        assertThrows(IllegalArgumentException.class, () -> MeshLayout.builder()
                .stream(0)
                .attribute(AttributeSemantic.POSITION, AttributeFormat.F32x3)
                .attribute(AttributeSemantic.POSITION, AttributeFormat.F32x2)
                .build());
    }

    @Test
    void emptyLayoutRejected() {
        assertThrows(IllegalStateException.class, () -> MeshLayout.builder().build());
    }

    // --- MeshLayout: element addressing ---

    @Test
    void elementOffsetWalksByStride() {
        MeshLayout layout = MeshLayout.builder()
                .stream(0)
                .attribute(AttributeSemantic.POSITION, AttributeFormat.F32x3)
                .attribute(AttributeSemantic.NORMAL, AttributeFormat.F32x3)
                .build();

        assertEquals(0, layout.elementOffset(AttributeSemantic.POSITION, 0));
        assertEquals(24, layout.elementOffset(AttributeSemantic.POSITION, 1));
        assertEquals(12, layout.elementOffset(AttributeSemantic.NORMAL, 0));
        assertEquals(36, layout.elementOffset(AttributeSemantic.NORMAL, 1));
        assertEquals(240, layout.streamByteSize(0, 10));
    }

    @Test
    void missingAttributeReportsClearly() {
        MeshLayout layout = MeshLayout.builder()
                .stream(0).attribute(AttributeSemantic.POSITION, AttributeFormat.F32x3)
                .build();
        assertFalse(layout.has(AttributeSemantic.NORMAL));
        assertThrows(IllegalArgumentException.class, () -> layout.offsetOf(AttributeSemantic.NORMAL));
    }

    // --- MeshLayout: consumer derivation ---

    @Test
    void vertexFormatDerivationIsPartialByDesign() {
        MeshLayout layout = MeshLayout.builder()
                .stream(0)
                .attribute(AttributeSemantic.POSITION, AttributeFormat.F32x3)
                .attribute(AttributeSemantic.NORMAL, AttributeFormat.OCT16)
                .build();

        // OCT16 has no VkFormat, so it is reported as shader-decoded rather than silently dropped.
        assertEquals(1, layout.shaderDecodedSemantics().size());
        assertTrue(layout.shaderDecodedSemantics().contains(AttributeSemantic.NORMAL));

        Map<AttributeSemantic, Integer> locations = Map.of(
                AttributeSemantic.POSITION, 0,
                AttributeSemantic.NORMAL, 1);
        var vf = layout.toVertexFormat(locations);

        assertEquals(1, vf.getBindings().size());
        assertEquals(1, vf.getAttributes().size());
        assertEquals(0, vf.getAttributes().get(0).location());
        // Stride is 16, not 12: the shader-decoded normal still occupies space in the stream even
        // though it contributes no vertex input attribute. Dropping it from the stride would
        // silently corrupt every subsequent element.
        assertEquals(16, vf.getBindings().get(0).stride());
        assertEquals(16, vf.getStride(0));
    }

    @Test
    void vertexFormatSkipsUnmappedSemantics() {
        MeshLayout layout = MeshLayout.builder()
                .stream(0)
                .attribute(AttributeSemantic.POSITION, AttributeFormat.F32x3)
                .attribute(AttributeSemantic.NORMAL, AttributeFormat.F32x3)
                .build();

        var vf = layout.toVertexFormat(Map.of(AttributeSemantic.POSITION, 0));
        assertEquals(1, vf.getAttributes().size());
        assertEquals(1, vf.getBindings().size());
    }

    @Test
    void vertexFormatDeclaresOnlyContributingStreams() {
        MeshLayout layout = MeshLayout.builder()
                .stream(0).attribute(AttributeSemantic.POSITION, AttributeFormat.F32x3)
                .stream(1).attribute(AttributeSemantic.NORMAL, AttributeFormat.OCT16)
                .build();

        // Stream 1 holds only a shader-decoded attribute, so it contributes no vertex input binding.
        var vf = layout.toVertexFormat(Map.of(
                AttributeSemantic.POSITION, 0,
                AttributeSemantic.NORMAL, 1));
        assertEquals(1, vf.getBindings().size());
        assertEquals(0, vf.getBindings().get(0).binding());
    }

    // --- PrimitiveTopology ---

    @Test
    void topologiesAreInternedAndMayLackVkMapping() {
        assertSame(PrimitiveTopology.TRIANGLE_LIST, PrimitiveTopology.of("triangleList"));
        assertTrue(PrimitiveTopology.TRIANGLE_LIST.isRenderable());
        assertEquals(3, PrimitiveTopology.TRIANGLE_LIST.indicesPerPrimitive());
        assertEquals(4, PrimitiveTopology.TRIANGLE_LIST.primitiveCount(12));

        assertFalse(PrimitiveTopology.MESHLET.isRenderable());
        assertTrue(PrimitiveTopology.MESHLET.vkTopology().isEmpty());
        assertFalse(PrimitiveTopology.TETRAHEDRA.isRenderable());
        assertEquals(4, PrimitiveTopology.TETRAHEDRA.indicesPerPrimitive());
    }

    @Test
    void customTopologyCanBeRegisteredFromOutside() {
        PrimitiveTopology custom = PrimitiveTopology.of("quadStripPatch", 0);
        assertSame(custom, PrimitiveTopology.of("quadstrippatch"));
        assertFalse(custom.isRenderable());
        assertEquals(-1, custom.primitiveCount(100));
    }

    @Test
    void unknownTopologyLookupFails() {
        assertThrows(IllegalArgumentException.class, () -> PrimitiveTopology.of("neverRegisteredTopology"));
    }

    // --- IndexWidth ---

    @Test
    void indexWidthNarrowingMatchesAddressableRange() {
        assertEquals(IndexWidth.U8, IndexWidth.narrowestFor(200));
        assertEquals(IndexWidth.U16, IndexWidth.narrowestFor(300));
        assertEquals(IndexWidth.U16, IndexWidth.narrowestFor(65535));
        assertEquals(IndexWidth.U32, IndexWidth.narrowestFor(65536));
        assertEquals(2, IndexWidth.U16.byteSize());
        assertTrue(IndexWidth.U32.vkIndexType().isPresent());
    }
}
