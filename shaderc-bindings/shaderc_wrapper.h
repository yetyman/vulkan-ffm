#ifndef SHADERC_WRAPPER_H
#define SHADERC_WRAPPER_H

// Shaderc C API wrapper for jextract
// Download shaderc headers from: https://github.com/google/shaderc

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

// Core shaderc types
typedef struct shaderc_compiler* shaderc_compiler_t;
typedef struct shaderc_compile_options* shaderc_compile_options_t;
typedef struct shaderc_compilation_result* shaderc_compilation_result_t;

// Shader kinds
typedef enum {
    shaderc_vertex_shader,
    shaderc_fragment_shader,
    shaderc_compute_shader,
    shaderc_geometry_shader,
    shaderc_tess_control_shader,
    shaderc_tess_evaluation_shader,
    shaderc_glsl_vertex_shader = shaderc_vertex_shader,
    shaderc_glsl_fragment_shader = shaderc_fragment_shader,
    shaderc_glsl_compute_shader = shaderc_compute_shader,
    shaderc_glsl_geometry_shader = shaderc_geometry_shader,
    shaderc_glsl_tess_control_shader = shaderc_tess_control_shader,
    shaderc_glsl_tess_evaluation_shader = shaderc_tess_evaluation_shader,
    shaderc_glsl_infer_from_source,
    shaderc_glsl_default_vertex_shader,
    shaderc_glsl_default_fragment_shader,
    shaderc_glsl_default_compute_shader,
    shaderc_glsl_default_geometry_shader,
    shaderc_glsl_default_tess_control_shader,
    shaderc_glsl_default_tess_evaluation_shader,
    shaderc_spirv_assembly,
    shaderc_raygen_shader,
    shaderc_anyhit_shader,
    shaderc_closesthit_shader,
    shaderc_miss_shader,
    shaderc_intersection_shader,
    shaderc_callable_shader,
    shaderc_glsl_raygen_shader = shaderc_raygen_shader,
    shaderc_glsl_anyhit_shader = shaderc_anyhit_shader,
    shaderc_glsl_closesthit_shader = shaderc_closesthit_shader,
    shaderc_glsl_miss_shader = shaderc_miss_shader,
    shaderc_glsl_intersection_shader = shaderc_intersection_shader,
    shaderc_glsl_callable_shader = shaderc_callable_shader,
    shaderc_task_shader,
    shaderc_mesh_shader,
    shaderc_glsl_task_shader = shaderc_task_shader,
    shaderc_glsl_mesh_shader = shaderc_mesh_shader,
} shaderc_shader_kind;

// Compilation status
typedef enum {
    shaderc_compilation_status_success = 0,
    shaderc_compilation_status_invalid_stage = 1,
    shaderc_compilation_status_compilation_error = 2,
    shaderc_compilation_status_internal_error = 3,
    shaderc_compilation_status_null_result_object = 4,
    shaderc_compilation_status_invalid_assembly = 5,
    shaderc_compilation_status_validation_error = 6,
    shaderc_compilation_status_transformation_error = 7,
    shaderc_compilation_status_configuration_error = 8,
} shaderc_compilation_status;

// Source language
typedef enum {
    shaderc_source_language_glsl,
    shaderc_source_language_hlsl,
} shaderc_source_language;

// Optimization level
typedef enum {
    shaderc_optimization_level_zero = 0,
    shaderc_optimization_level_size = 1,
    shaderc_optimization_level_performance = 2,
} shaderc_optimization_level;

// Target environment
typedef enum {
    shaderc_target_env_vulkan = 0,
    shaderc_target_env_opengl = 1,
    shaderc_target_env_opengl_compat = 2,
    shaderc_target_env_webgpu = 3,
} shaderc_target_env;

// Environment version
typedef enum {
    shaderc_env_version_vulkan_1_0 = ((1u << 22)),
    shaderc_env_version_vulkan_1_1 = ((1u << 22) | (1 << 12)),
    shaderc_env_version_vulkan_1_2 = ((1u << 22) | (2 << 12)),
    shaderc_env_version_vulkan_1_3 = ((1u << 22) | (3 << 12)),
    shaderc_env_version_opengl_4_5 = 450,
    shaderc_env_version_webgpu = ((1u << 22)),
} shaderc_env_version;

// SPIR-V version
typedef enum {
    shaderc_spirv_version_1_0 = 0x010000,
    shaderc_spirv_version_1_1 = 0x010100,
    shaderc_spirv_version_1_2 = 0x010200,
    shaderc_spirv_version_1_3 = 0x010300,
    shaderc_spirv_version_1_4 = 0x010400,
    shaderc_spirv_version_1_5 = 0x010500,
    shaderc_spirv_version_1_6 = 0x010600,
} shaderc_spirv_version;

// Core API functions
shaderc_compiler_t shaderc_compiler_initialize(void);
void shaderc_compiler_release(shaderc_compiler_t compiler);

shaderc_compile_options_t shaderc_compile_options_initialize(void);
shaderc_compile_options_t shaderc_compile_options_clone(const shaderc_compile_options_t options);
void shaderc_compile_options_release(shaderc_compile_options_t options);

void shaderc_compile_options_add_macro_definition(shaderc_compile_options_t options,
                                                  const char* name, size_t name_length,
                                                  const char* value, size_t value_length);

void shaderc_compile_options_set_source_language(shaderc_compile_options_t options,
                                                 shaderc_source_language lang);

void shaderc_compile_options_set_generate_debug_info(shaderc_compile_options_t options);

void shaderc_compile_options_set_optimization_level(shaderc_compile_options_t options,
                                                    shaderc_optimization_level level);

void shaderc_compile_options_set_forced_version_profile(shaderc_compile_options_t options,
                                                        int version, int profile);

void shaderc_compile_options_set_include_callbacks(shaderc_compile_options_t options,
                                                   void* resolver_fn,
                                                   void* releaser_fn,
                                                   void* user_data);

void shaderc_compile_options_set_suppress_warnings(shaderc_compile_options_t options);

void shaderc_compile_options_set_target_env(shaderc_compile_options_t options,
                                            shaderc_target_env target,
                                            uint32_t version);

void shaderc_compile_options_set_target_spirv(shaderc_compile_options_t options,
                                              shaderc_spirv_version version);

void shaderc_compile_options_set_warnings_as_errors(shaderc_compile_options_t options);

void shaderc_compile_options_set_limit(shaderc_compile_options_t options,
                                       int limit, int value);

void shaderc_compile_options_set_auto_bind_uniforms(shaderc_compile_options_t options,
                                                    int auto_bind);

void shaderc_compile_options_set_auto_combined_image_sampler(shaderc_compile_options_t options,
                                                            int upgrade);

void shaderc_compile_options_set_hlsl_register_set_and_binding_for_stage(
    shaderc_compile_options_t options, shaderc_shader_kind shader_kind,
    const char* reg, const char* set, const char* binding);

void shaderc_compile_options_set_hlsl_register_set_and_binding(
    shaderc_compile_options_t options, const char* reg, const char* set, const char* binding);

void shaderc_compile_options_set_hlsl_functionality1(shaderc_compile_options_t options,
                                                     int enable);

void shaderc_compile_options_set_invert_y(shaderc_compile_options_t options,
                                          int enable);

void shaderc_compile_options_set_nan_clamp(shaderc_compile_options_t options,
                                           int enable);

// Compilation functions
shaderc_compilation_result_t shaderc_compile_into_spv(const shaderc_compiler_t compiler,
                                                      const char* source_text,
                                                      size_t source_text_size,
                                                      shaderc_shader_kind shader_kind,
                                                      const char* input_file_name,
                                                      const char* entry_point_name,
                                                      const shaderc_compile_options_t additional_options);

shaderc_compilation_result_t shaderc_compile_into_spv_assembly(const shaderc_compiler_t compiler,
                                                               const char* source_text,
                                                               size_t source_text_size,
                                                               shaderc_shader_kind shader_kind,
                                                               const char* input_file_name,
                                                               const char* entry_point_name,
                                                               const shaderc_compile_options_t additional_options);

shaderc_compilation_result_t shaderc_compile_into_preprocessed_text(const shaderc_compiler_t compiler,
                                                                    const char* source_text,
                                                                    size_t source_text_size,
                                                                    shaderc_shader_kind shader_kind,
                                                                    const char* input_file_name,
                                                                    const char* entry_point_name,
                                                                    const shaderc_compile_options_t additional_options);

// Result functions
void shaderc_result_release(shaderc_compilation_result_t result);

size_t shaderc_result_get_length(const shaderc_compilation_result_t result);
size_t shaderc_result_get_num_warnings(const shaderc_compilation_result_t result);
size_t shaderc_result_get_num_errors(const shaderc_compilation_result_t result);

shaderc_compilation_status shaderc_result_get_compilation_status(const shaderc_compilation_result_t result);

const char* shaderc_result_get_bytes(const shaderc_compilation_result_t result);
const char* shaderc_result_get_error_message(const shaderc_compilation_result_t result);

// Utility functions
void shaderc_get_spv_version(unsigned int* version, unsigned int* revision);
int shaderc_parse_version_profile(const char* str, int* version, int* profile);

#ifdef __cplusplus
}
#endif

#endif // SHADERC_WRAPPER_H