package io.github.yetyman.vulkan.graph;

import io.github.yetyman.vulkan.graph.nodes.RenderNode;
import io.github.yetyman.vulkan.graph.resources.GraphResource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * A reusable subgraph template that can be stamped multiple times with different parameters.
 * No recursion allowed. Each instantiation produces unique pass names.
 *
 * Usage:
 * <pre>
 *   SubgraphTemplate shadowCascade = SubgraphTemplate.define("shadow_cascade")
 *       .input("scene_geometry")
 *       .output("shadow_map")
 *       .body((params, builder) -> {
 *           builder.addPass(ComputePassNode.builder()
 *               .name("shadow_" + params.get("index"))
 *               .reads(builder.input("scene_geometry"))
 *               ...build());
 *           builder.mapOutput("shadow_map", depthTarget);
 *       });
 *
 *   // Stamp N times
 *   for (int i = 0; i < cascadeCount; i++) {
 *       SubgraphInstance inst = graph.instantiate(shadowCascade)
 *           .param("index", i)
 *           .connectInput("scene_geometry", sceneBuffer)
 *           .build();
 *       lightingPass.reads(inst.output("shadow_map"));
 *   }
 * </pre>
 */
public class SubgraphTemplate {

    private final String name;
    private final List<String> inputs;
    private final List<String> outputs;
    private final BiConsumer<Map<String, Object>, SubgraphBuilder> body;

    private SubgraphTemplate(String name, List<String> inputs, List<String> outputs,
                             BiConsumer<Map<String, Object>, SubgraphBuilder> body) {
        this.name = name;
        this.inputs = Collections.unmodifiableList(inputs);
        this.outputs = Collections.unmodifiableList(outputs);
        this.body = body;
    }

    /** @return template name */
    public String name() { return name; }

    /** @return declared input slot names */
    public List<String> inputs() { return inputs; }

    /** @return declared output slot names */
    public List<String> outputs() { return outputs; }

    /**
     * Instantiates this template with the given parameters and input bindings.
     *
     * @param params template parameters
     * @param inputBindings input slot name -> actual resource
     * @return the instantiated subgraph
     */
    public SubgraphInstance instantiate(Map<String, Object> params, Map<String, GraphResource> inputBindings) {
        SubgraphBuilder builder = new SubgraphBuilder(inputBindings);
        body.accept(params, builder);
        return new SubgraphInstance(builder.passes, builder.outputBindings);
    }

    /** Starts defining a new template */
    public static TemplateBuilder define(String name) {
        return new TemplateBuilder(name);
    }

    /** Builder for constructing subgraph instances within the body */
    public static class SubgraphBuilder {
        private final Map<String, GraphResource> inputBindings;
        private final List<RenderNode> passes = new ArrayList<>();
        private final Map<String, GraphResource> outputBindings = new HashMap<>();

        SubgraphBuilder(Map<String, GraphResource> inputBindings) {
            this.inputBindings = inputBindings;
        }

        /** @return the resource bound to the given input slot */
        public GraphResource input(String slotName) {
            GraphResource res = inputBindings.get(slotName);
            if (res == null) throw new RenderGraphException("Input '" + slotName + "' not connected");
            return res;
        }

        /** Adds a pass to this subgraph instance */
        public void addPass(RenderNode node) { passes.add(node); }

        /** Maps a subgraph output slot to a resource produced by a pass in this instance */
        public void mapOutput(String slotName, GraphResource resource) {
            outputBindings.put(slotName, resource);
        }
    }

    /** Result of instantiating a template */
    public static class SubgraphInstance {
        private final List<RenderNode> passes;
        private final Map<String, GraphResource> outputs;

        SubgraphInstance(List<RenderNode> passes, Map<String, GraphResource> outputs) {
            this.passes = Collections.unmodifiableList(passes);
            this.outputs = Collections.unmodifiableMap(outputs);
        }

        /** @return all passes produced by this instantiation */
        public List<RenderNode> passes() { return passes; }

        /** @return the resource mapped to the given output slot */
        public GraphResource output(String slotName) {
            GraphResource res = outputs.get(slotName);
            if (res == null) throw new RenderGraphException("Output '" + slotName + "' not mapped");
            return res;
        }
    }

    /** Fluent builder for instantiation */
    public static class InstantiationBuilder {
        private final SubgraphTemplate template;
        private final Map<String, Object> params = new HashMap<>();
        private final Map<String, GraphResource> inputs = new HashMap<>();

        InstantiationBuilder(SubgraphTemplate template) { this.template = template; }

        public InstantiationBuilder param(String key, Object value) { params.put(key, value); return this; }
        public InstantiationBuilder connectInput(String slot, GraphResource resource) { inputs.put(slot, resource); return this; }

        public SubgraphInstance build() { return template.instantiate(params, inputs); }
    }

    public static class TemplateBuilder {
        private final String name;
        private final List<String> inputs = new ArrayList<>();
        private final List<String> outputs = new ArrayList<>();
        private BiConsumer<Map<String, Object>, SubgraphBuilder> body;

        TemplateBuilder(String name) { this.name = name; }

        public TemplateBuilder input(String slotName) { inputs.add(slotName); return this; }
        public TemplateBuilder output(String slotName) { outputs.add(slotName); return this; }
        public TemplateBuilder body(BiConsumer<Map<String, Object>, SubgraphBuilder> body) { this.body = body; return this; }

        public SubgraphTemplate build() {
            if (body == null) throw new IllegalStateException("body not set");
            return new SubgraphTemplate(name, inputs, outputs, body);
        }
    }
}
