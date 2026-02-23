#ifndef SPIRV_REFLECT_WRAPPER_H
#define SPIRV_REFLECT_WRAPPER_H

// SPIRV-Reflect C API wrapper for jextract
// Download spirv_reflect.h from: https://github.com/KhronosGroup/SPIRV-Reflect

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

// Result codes
typedef enum SpvReflectResult {
    SPV_REFLECT_RESULT_SUCCESS = 0,
    SPV_REFLECT_RESULT_NOT_READY = 1,
    SPV_REFLECT_RESULT_ERROR_PARSE_FAILED = -1,
    SPV_REFLECT_RESULT_ERROR_ALLOC_FAILED = -2,
    SPV_REFLECT_RESULT_ERROR_RANGE_EXCEEDED = -3,
    SPV_REFLECT_RESULT_ERROR_NULL_POINTER = -4,
    SPV_REFLECT_RESULT_ERROR_INTERNAL_ERROR = -5,
    SPV_REFLECT_RESULT_ERROR_COUNT_MISMATCH = -6,
    SPV_REFLECT_RESULT_ERROR_ELEMENT_NOT_FOUND = -7,
    SPV_REFLECT_RESULT_ERROR_SPIRV_INVALID_CODE_SIZE = -8,
    SPV_REFLECT_RESULT_ERROR_SPIRV_INVALID_MAGIC_NUMBER = -9,
    SPV_REFLECT_RESULT_ERROR_SPIRV_UNEXPECTED_EOF = -10,
    SPV_REFLECT_RESULT_ERROR_SPIRV_INVALID_ID_REFERENCE = -11,
    SPV_REFLECT_RESULT_ERROR_SPIRV_SET_NUMBER_DUPLICATE = -12,
    SPV_REFLECT_RESULT_ERROR_SPIRV_INVALID_STORAGE_CLASS = -13,
    SPV_REFLECT_RESULT_ERROR_SPIRV_RECURSION = -14,
    SPV_REFLECT_RESULT_ERROR_SPIRV_INVALID_INSTRUCTION = -15,
    SPV_REFLECT_RESULT_ERROR_SPIRV_UNEXPECTED_BLOCK_DATA = -16,
    SPV_REFLECT_RESULT_ERROR_SPIRV_INVALID_BLOCK_MEMBER_REFERENCE = -17,
    SPV_REFLECT_RESULT_ERROR_SPIRV_INVALID_ENTRY_POINT = -18,
    SPV_REFLECT_RESULT_ERROR_SPIRV_INVALID_EXECUTION_MODE = -19,
} SpvReflectResult;

// Shader stage flags
typedef enum SpvReflectShaderStageFlagBits {
    SPV_REFLECT_SHADER_STAGE_VERTEX_BIT = 0x00000001,
    SPV_REFLECT_SHADER_STAGE_TESSELLATION_CONTROL_BIT = 0x00000002,
    SPV_REFLECT_SHADER_STAGE_TESSELLATION_EVALUATION_BIT = 0x00000004,
    SPV_REFLECT_SHADER_STAGE_GEOMETRY_BIT = 0x00000008,
    SPV_REFLECT_SHADER_STAGE_FRAGMENT_BIT = 0x00000010,
    SPV_REFLECT_SHADER_STAGE_COMPUTE_BIT = 0x00000020,
} SpvReflectShaderStageFlagBits;

// Descriptor types
typedef enum SpvReflectDescriptorType {
    SPV_REFLECT_DESCRIPTOR_TYPE_SAMPLER = 0,
    SPV_REFLECT_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER = 1,
    SPV_REFLECT_DESCRIPTOR_TYPE_SAMPLED_IMAGE = 2,
    SPV_REFLECT_DESCRIPTOR_TYPE_STORAGE_IMAGE = 3,
    SPV_REFLECT_DESCRIPTOR_TYPE_UNIFORM_TEXEL_BUFFER = 4,
    SPV_REFLECT_DESCRIPTOR_TYPE_STORAGE_TEXEL_BUFFER = 5,
    SPV_REFLECT_DESCRIPTOR_TYPE_UNIFORM_BUFFER = 6,
    SPV_REFLECT_DESCRIPTOR_TYPE_STORAGE_BUFFER = 7,
    SPV_REFLECT_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC = 8,
    SPV_REFLECT_DESCRIPTOR_TYPE_STORAGE_BUFFER_DYNAMIC = 9,
    SPV_REFLECT_DESCRIPTOR_TYPE_INPUT_ATTACHMENT = 10,
    SPV_REFLECT_DESCRIPTOR_TYPE_ACCELERATION_STRUCTURE_KHR = 1000150000,
} SpvReflectDescriptorType;

// Forward declarations
typedef struct SpvReflectShaderModule SpvReflectShaderModule;
typedef struct SpvReflectDescriptorSet SpvReflectDescriptorSet;
typedef struct SpvReflectDescriptorBinding SpvReflectDescriptorBinding;
typedef struct SpvReflectInterfaceVariable SpvReflectInterfaceVariable;
typedef struct SpvReflectBlockVariable SpvReflectBlockVariable;
typedef struct SpvReflectTypeDescription SpvReflectTypeDescription;

// Simplified structures for essential reflection data
struct SpvReflectDescriptorBinding {
    uint32_t spirv_id;
    const char* name;
    uint32_t binding;
    uint32_t set;
    SpvReflectDescriptorType descriptor_type;
    uint32_t count;
    uint32_t accessed;
    SpvReflectTypeDescription* type_description;
};

struct SpvReflectDescriptorSet {
    uint32_t set;
    uint32_t binding_count;
    SpvReflectDescriptorBinding** bindings;
};

struct SpvReflectShaderModule {
    uint32_t generator;
    const char* entry_point_name;
    uint32_t entry_point_id;
    SpvReflectShaderStageFlagBits shader_stage;
    uint32_t descriptor_set_count;
    SpvReflectDescriptorSet* descriptor_sets;
    uint32_t input_variable_count;
    SpvReflectInterfaceVariable** input_variables;
    uint32_t output_variable_count;
    SpvReflectInterfaceVariable** output_variables;
    uint32_t push_constant_block_count;
    SpvReflectBlockVariable** push_constant_blocks;
    // Internal data follows...
};

// Core API functions
SpvReflectResult spvReflectCreateShaderModule(size_t size,
                                              const void* pCode,
                                              SpvReflectShaderModule* pModule);

void spvReflectDestroyShaderModule(SpvReflectShaderModule* pModule);

uint32_t spvReflectGetCodeSize(const SpvReflectShaderModule* pModule);

const uint32_t* spvReflectGetCode(const SpvReflectShaderModule* pModule);

const char* spvReflectGetEntryPointName(const SpvReflectShaderModule* pModule,
                                        uint32_t index);

uint32_t spvReflectGetEntryPointId(const SpvReflectShaderModule* pModule,
                                   const char* entry_point);

SpvReflectShaderStageFlagBits spvReflectGetShaderStage(const SpvReflectShaderModule* pModule);

SpvReflectResult spvReflectEnumerateDescriptorBindings(const SpvReflectShaderModule* pModule,
                                                       uint32_t* pCount,
                                                       SpvReflectDescriptorBinding** ppBindings);

SpvReflectResult spvReflectEnumerateDescriptorSets(const SpvReflectShaderModule* pModule,
                                                   uint32_t* pCount,
                                                   SpvReflectDescriptorSet** ppSets);

SpvReflectResult spvReflectEnumerateInputVariables(const SpvReflectShaderModule* pModule,
                                                   uint32_t* pCount,
                                                   SpvReflectInterfaceVariable** ppVariables);

SpvReflectResult spvReflectEnumerateOutputVariables(const SpvReflectShaderModule* pModule,
                                                    uint32_t* pCount,
                                                    SpvReflectInterfaceVariable** ppVariables);

SpvReflectResult spvReflectEnumeratePushConstantBlocks(const SpvReflectShaderModule* pModule,
                                                       uint32_t* pCount,
                                                       SpvReflectBlockVariable** ppBlocks);

SpvReflectDescriptorBinding* spvReflectGetDescriptorBinding(const SpvReflectShaderModule* pModule,
                                                            uint32_t binding_number,
                                                            uint32_t set_number,
                                                            SpvReflectResult* pResult);

SpvReflectDescriptorSet* spvReflectGetDescriptorSet(const SpvReflectShaderModule* pModule,
                                                    uint32_t set_number,
                                                    SpvReflectResult* pResult);

SpvReflectInterfaceVariable* spvReflectGetInputVariableByLocation(const SpvReflectShaderModule* pModule,
                                                                  uint32_t location,
                                                                  SpvReflectResult* pResult);

SpvReflectInterfaceVariable* spvReflectGetInputVariable(const SpvReflectShaderModule* pModule,
                                                        uint32_t location,
                                                        SpvReflectResult* pResult);

SpvReflectInterfaceVariable* spvReflectGetOutputVariableByLocation(const SpvReflectShaderModule* pModule,
                                                                   uint32_t location,
                                                                   SpvReflectResult* pResult);

SpvReflectInterfaceVariable* spvReflectGetOutputVariable(const SpvReflectShaderModule* pModule,
                                                         uint32_t location,
                                                         SpvReflectResult* pResult);

SpvReflectBlockVariable* spvReflectGetPushConstantBlock(const SpvReflectShaderModule* pModule,
                                                        uint32_t index,
                                                        SpvReflectResult* pResult);

SpvReflectResult spvReflectChangeDescriptorBindingNumbers(SpvReflectShaderModule* pModule,
                                                          const SpvReflectDescriptorBinding* pBinding,
                                                          uint32_t new_binding_number,
                                                          uint32_t new_set_number);

SpvReflectResult spvReflectChangeDescriptorSetNumber(SpvReflectShaderModule* pModule,
                                                     const SpvReflectDescriptorSet* pSet,
                                                     uint32_t new_set_number);

const char* spvReflectResultToString(SpvReflectResult result);

#ifdef __cplusplus
}
#endif

#endif // SPIRV_REFLECT_WRAPPER_H