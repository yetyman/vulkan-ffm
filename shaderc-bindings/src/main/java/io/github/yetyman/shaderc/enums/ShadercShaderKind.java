package io.github.yetyman.shaderc.enums;

import java.util.*;

/**
 * Type-safe constants for ShadercShaderKind
 * Generated from jextract bindings
 */
public record ShadercShaderKind(int value) {

    public static final ShadercShaderKind shaderc_anyhit_shader = new ShadercShaderKind(15);
    public static final ShadercShaderKind shaderc_callable_shader = new ShadercShaderKind(19);
    public static final ShadercShaderKind shaderc_closesthit_shader = new ShadercShaderKind(16);
    public static final ShadercShaderKind shaderc_compute_shader = new ShadercShaderKind(2);
    public static final ShadercShaderKind shaderc_fragment_shader = new ShadercShaderKind(1);
    public static final ShadercShaderKind shaderc_geometry_shader = new ShadercShaderKind(3);
    public static final ShadercShaderKind shaderc_glsl_anyhit_shader = shaderc_anyhit_shader;
    public static final ShadercShaderKind shaderc_glsl_callable_shader = shaderc_callable_shader;
    public static final ShadercShaderKind shaderc_glsl_closesthit_shader = shaderc_closesthit_shader;
    public static final ShadercShaderKind shaderc_glsl_compute_shader = shaderc_compute_shader;
    public static final ShadercShaderKind shaderc_glsl_default_compute_shader = new ShadercShaderKind(9);
    public static final ShadercShaderKind shaderc_glsl_default_fragment_shader = new ShadercShaderKind(8);
    public static final ShadercShaderKind shaderc_glsl_default_geometry_shader = new ShadercShaderKind(10);
    public static final ShadercShaderKind shaderc_glsl_default_tess_control_shader = new ShadercShaderKind(11);
    public static final ShadercShaderKind shaderc_glsl_default_tess_evaluation_shader = new ShadercShaderKind(12);
    public static final ShadercShaderKind shaderc_glsl_default_vertex_shader = new ShadercShaderKind(7);
    public static final ShadercShaderKind shaderc_glsl_fragment_shader = shaderc_fragment_shader;
    public static final ShadercShaderKind shaderc_glsl_geometry_shader = shaderc_geometry_shader;
    public static final ShadercShaderKind shaderc_glsl_infer_from_source = new ShadercShaderKind(6);
    public static final ShadercShaderKind shaderc_glsl_intersection_shader = new ShadercShaderKind(18);
    public static final ShadercShaderKind shaderc_glsl_mesh_shader = new ShadercShaderKind(21);
    public static final ShadercShaderKind shaderc_glsl_miss_shader = new ShadercShaderKind(17);
    public static final ShadercShaderKind shaderc_glsl_raygen_shader = new ShadercShaderKind(14);
    public static final ShadercShaderKind shaderc_glsl_task_shader = new ShadercShaderKind(20);
    public static final ShadercShaderKind shaderc_glsl_tess_control_shader = new ShadercShaderKind(4);
    public static final ShadercShaderKind shaderc_glsl_tess_evaluation_shader = new ShadercShaderKind(5);
    public static final ShadercShaderKind shaderc_glsl_vertex_shader = new ShadercShaderKind(0);
    public static final ShadercShaderKind shaderc_intersection_shader = shaderc_glsl_intersection_shader;
    public static final ShadercShaderKind shaderc_mesh_shader = shaderc_glsl_mesh_shader;
    public static final ShadercShaderKind shaderc_miss_shader = shaderc_glsl_miss_shader;
    public static final ShadercShaderKind shaderc_raygen_shader = shaderc_glsl_raygen_shader;
    public static final ShadercShaderKind shaderc_spirv_assembly = new ShadercShaderKind(13);
    public static final ShadercShaderKind shaderc_task_shader = shaderc_glsl_task_shader;
    public static final ShadercShaderKind shaderc_tess_control_shader = shaderc_glsl_tess_control_shader;
    public static final ShadercShaderKind shaderc_tess_evaluation_shader = shaderc_glsl_tess_evaluation_shader;
    public static final ShadercShaderKind shaderc_vertex_shader = shaderc_glsl_vertex_shader;

    public static ShadercShaderKind fromValue(int value) {
        return switch (value) {
            case 15 -> shaderc_anyhit_shader;
            case 19 -> shaderc_callable_shader;
            case 16 -> shaderc_closesthit_shader;
            case 2 -> shaderc_compute_shader;
            case 1 -> shaderc_fragment_shader;
            case 3 -> shaderc_geometry_shader;
            case 9 -> shaderc_glsl_default_compute_shader;
            case 8 -> shaderc_glsl_default_fragment_shader;
            case 10 -> shaderc_glsl_default_geometry_shader;
            case 11 -> shaderc_glsl_default_tess_control_shader;
            case 12 -> shaderc_glsl_default_tess_evaluation_shader;
            case 7 -> shaderc_glsl_default_vertex_shader;
            case 6 -> shaderc_glsl_infer_from_source;
            case 18 -> shaderc_intersection_shader;
            case 21 -> shaderc_mesh_shader;
            case 17 -> shaderc_miss_shader;
            case 14 -> shaderc_raygen_shader;
            case 20 -> shaderc_task_shader;
            case 4 -> shaderc_tess_control_shader;
            case 5 -> shaderc_tess_evaluation_shader;
            case 0 -> shaderc_vertex_shader;
            case 13 -> shaderc_spirv_assembly;
            default -> new ShadercShaderKind(value);
        };
    }
}
