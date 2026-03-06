#ifndef SPIRV_REFLECT_WRAPPER_H
#define SPIRV_REFLECT_WRAPPER_H

// Self-contained jextract wrapper — no system header includes.
// All types and structs copied verbatim from spirv_reflect.h (KhronosGroup/SPIRV-Reflect).

#ifdef __cplusplus
extern "C" {
#endif

typedef unsigned char      uint8_t;
typedef unsigned int       uint32_t;
typedef unsigned long long uint64_t;
typedef unsigned long long size_t;

// ---- Constants ----
#define SPV_REFLECT_MAX_ARRAY_DIMS     32
#define SPV_REFLECT_MAX_DESCRIPTOR_SETS 64

// ---- Enums ----

typedef enum SpvReflectResult {
    SPV_REFLECT_RESULT_SUCCESS                                  =  0,
    SPV_REFLECT_RESULT_NOT_READY                                =  1,
    SPV_REFLECT_RESULT_ERROR_PARSE_FAILED                       = -1,
    SPV_REFLECT_RESULT_ERROR_ALLOC_FAILED                       = -2,
    SPV_REFLECT_RESULT_ERROR_RANGE_EXCEEDED                     = -3,
    SPV_REFLECT_RESULT_ERROR_NULL_POINTER                       = -4,
    SPV_REFLECT_RESULT_ERROR_INTERNAL_ERROR                     = -5,
    SPV_REFLECT_RESULT_ERROR_COUNT_MISMATCH                     = -6,
    SPV_REFLECT_RESULT_ERROR_ELEMENT_NOT_FOUND                  = -7,
    SPV_REFLECT_RESULT_ERROR_SPIRV_INVALID_CODE_SIZE            = -8,
    SPV_REFLECT_RESULT_ERROR_SPIRV_INVALID_MAGIC_NUMBER         = -9,
    SPV_REFLECT_RESULT_ERROR_SPIRV_UNEXPECTED_EOF               = -10,
    SPV_REFLECT_RESULT_ERROR_SPIRV_INVALID_ID_REFERENCE         = -11,
    SPV_REFLECT_RESULT_ERROR_SPIRV_SET_NUMBER_OVERFLOW          = -12,
    SPV_REFLECT_RESULT_ERROR_SPIRV_INVALID_STORAGE_CLASS        = -13,
    SPV_REFLECT_RESULT_ERROR_SPIRV_RECURSION                    = -14,
    SPV_REFLECT_RESULT_ERROR_SPIRV_INVALID_INSTRUCTION          = -15,
    SPV_REFLECT_RESULT_ERROR_SPIRV_UNEXPECTED_BLOCK_DATA        = -16,
    SPV_REFLECT_RESULT_ERROR_SPIRV_INVALID_BLOCK_MEMBER_REFERENCE = -17,
    SPV_REFLECT_RESULT_ERROR_SPIRV_INVALID_ENTRY_POINT          = -18,
    SPV_REFLECT_RESULT_ERROR_SPIRV_INVALID_EXECUTION_MODE       = -19,
    SPV_REFLECT_RESULT_ERROR_SPIRV_MAX_RECURSIVE_EXCEEDED       = -20,
} SpvReflectResult;

typedef enum SpvReflectModuleFlagBits {
    SPV_REFLECT_MODULE_FLAG_NONE    = 0x00000000,
    SPV_REFLECT_MODULE_FLAG_NO_COPY = 0x00000001,
} SpvReflectModuleFlagBits;
typedef uint32_t SpvReflectModuleFlags;

typedef enum SpvReflectTypeFlagBits {
    SPV_REFLECT_TYPE_FLAG_UNDEFINED                       = 0x00000000,
    SPV_REFLECT_TYPE_FLAG_VOID                            = 0x00000001,
    SPV_REFLECT_TYPE_FLAG_BOOL                            = 0x00000002,
    SPV_REFLECT_TYPE_FLAG_INT                             = 0x00000004,
    SPV_REFLECT_TYPE_FLAG_FLOAT                           = 0x00000008,
    SPV_REFLECT_TYPE_FLAG_VECTOR                          = 0x00000100,
    SPV_REFLECT_TYPE_FLAG_MATRIX                          = 0x00000200,
    SPV_REFLECT_TYPE_FLAG_EXTERNAL_IMAGE                  = 0x00010000,
    SPV_REFLECT_TYPE_FLAG_EXTERNAL_SAMPLER                = 0x00020000,
    SPV_REFLECT_TYPE_FLAG_EXTERNAL_SAMPLED_IMAGE          = 0x00040000,
    SPV_REFLECT_TYPE_FLAG_EXTERNAL_BLOCK                  = 0x00080000,
    SPV_REFLECT_TYPE_FLAG_EXTERNAL_ACCELERATION_STRUCTURE = 0x00100000,
    SPV_REFLECT_TYPE_FLAG_EXTERNAL_MASK                   = 0x00FF0000,
    SPV_REFLECT_TYPE_FLAG_STRUCT                          = 0x10000000,
    SPV_REFLECT_TYPE_FLAG_ARRAY                           = 0x20000000,
    SPV_REFLECT_TYPE_FLAG_REF                             = 0x40000000,
} SpvReflectTypeFlagBits;
typedef uint32_t SpvReflectTypeFlags;

typedef uint32_t SpvReflectDecorationFlags;

typedef enum SpvReflectResourceType {
    SPV_REFLECT_RESOURCE_FLAG_UNDEFINED = 0x00000000,
    SPV_REFLECT_RESOURCE_FLAG_SAMPLER   = 0x00000001,
    SPV_REFLECT_RESOURCE_FLAG_CBV       = 0x00000002,
    SPV_REFLECT_RESOURCE_FLAG_SRV       = 0x00000004,
    SPV_REFLECT_RESOURCE_FLAG_UAV       = 0x00000008,
} SpvReflectResourceType;

typedef enum SpvReflectFormat {
    SPV_REFLECT_FORMAT_UNDEFINED           =   0,
    SPV_REFLECT_FORMAT_R32_UINT            =  98,
    SPV_REFLECT_FORMAT_R32_SINT            =  99,
    SPV_REFLECT_FORMAT_R32_SFLOAT          = 100,
    SPV_REFLECT_FORMAT_R32G32_UINT         = 101,
    SPV_REFLECT_FORMAT_R32G32_SINT         = 102,
    SPV_REFLECT_FORMAT_R32G32_SFLOAT       = 103,
    SPV_REFLECT_FORMAT_R32G32B32_UINT      = 104,
    SPV_REFLECT_FORMAT_R32G32B32_SINT      = 105,
    SPV_REFLECT_FORMAT_R32G32B32_SFLOAT    = 106,
    SPV_REFLECT_FORMAT_R32G32B32A32_UINT   = 107,
    SPV_REFLECT_FORMAT_R32G32B32A32_SINT   = 108,
    SPV_REFLECT_FORMAT_R32G32B32A32_SFLOAT = 109,
} SpvReflectFormat;

typedef enum SpvReflectVariableFlagBits {
    SPV_REFLECT_VARIABLE_FLAGS_NONE                  = 0x00000000,
    SPV_REFLECT_VARIABLE_FLAGS_UNUSED                = 0x00000001,
    SPV_REFLECT_VARIABLE_FLAGS_PHYSICAL_POINTER_COPY = 0x00000002,
} SpvReflectVariableFlagBits;
typedef uint32_t SpvReflectVariableFlags;

typedef enum SpvReflectDescriptorType {
    SPV_REFLECT_DESCRIPTOR_TYPE_SAMPLER                    =  0,
    SPV_REFLECT_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER     =  1,
    SPV_REFLECT_DESCRIPTOR_TYPE_SAMPLED_IMAGE              =  2,
    SPV_REFLECT_DESCRIPTOR_TYPE_STORAGE_IMAGE              =  3,
    SPV_REFLECT_DESCRIPTOR_TYPE_UNIFORM_TEXEL_BUFFER       =  4,
    SPV_REFLECT_DESCRIPTOR_TYPE_STORAGE_TEXEL_BUFFER       =  5,
    SPV_REFLECT_DESCRIPTOR_TYPE_UNIFORM_BUFFER             =  6,
    SPV_REFLECT_DESCRIPTOR_TYPE_STORAGE_BUFFER             =  7,
    SPV_REFLECT_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC     =  8,
    SPV_REFLECT_DESCRIPTOR_TYPE_STORAGE_BUFFER_DYNAMIC     =  9,
    SPV_REFLECT_DESCRIPTOR_TYPE_INPUT_ATTACHMENT           = 10,
    SPV_REFLECT_DESCRIPTOR_TYPE_ACCELERATION_STRUCTURE_KHR = 1000150000,
} SpvReflectDescriptorType;

typedef enum SpvReflectShaderStageFlagBits {
    SPV_REFLECT_SHADER_STAGE_VERTEX_BIT                  = 0x00000001,
    SPV_REFLECT_SHADER_STAGE_TESSELLATION_CONTROL_BIT    = 0x00000002,
    SPV_REFLECT_SHADER_STAGE_TESSELLATION_EVALUATION_BIT = 0x00000004,
    SPV_REFLECT_SHADER_STAGE_GEOMETRY_BIT                = 0x00000008,
    SPV_REFLECT_SHADER_STAGE_FRAGMENT_BIT                = 0x00000010,
    SPV_REFLECT_SHADER_STAGE_COMPUTE_BIT                 = 0x00000020,
    SPV_REFLECT_SHADER_STAGE_TASK_BIT_NV                 = 0x00000040,
    SPV_REFLECT_SHADER_STAGE_MESH_BIT_NV                 = 0x00000080,
    SPV_REFLECT_SHADER_STAGE_RAYGEN_BIT_KHR              = 0x00000100,
    SPV_REFLECT_SHADER_STAGE_ANY_HIT_BIT_KHR             = 0x00000200,
    SPV_REFLECT_SHADER_STAGE_CLOSEST_HIT_BIT_KHR         = 0x00000400,
    SPV_REFLECT_SHADER_STAGE_MISS_BIT_KHR                = 0x00000800,
    SPV_REFLECT_SHADER_STAGE_INTERSECTION_BIT_KHR        = 0x00001000,
    SPV_REFLECT_SHADER_STAGE_CALLABLE_BIT_KHR            = 0x00002000,
} SpvReflectShaderStageFlagBits;

typedef enum SpvReflectGenerator {
    SPV_REFLECT_GENERATOR_KHRONOS_LLVM_SPIRV_TRANSLATOR         = 6,
    SPV_REFLECT_GENERATOR_KHRONOS_SPIRV_TOOLS_ASSEMBLER         = 7,
    SPV_REFLECT_GENERATOR_KHRONOS_GLSLANG_REFERENCE_FRONT_END   = 8,
    SPV_REFLECT_GENERATOR_GOOGLE_SHADERC_OVER_GLSLANG           = 13,
    SPV_REFLECT_GENERATOR_GOOGLE_SPIREGG                        = 14,
    SPV_REFLECT_GENERATOR_GOOGLE_RSPIRV                         = 15,
    SPV_REFLECT_GENERATOR_X_LEGEND_MESA_MESAIR_SPIRV_TRANSLATOR = 16,
    SPV_REFLECT_GENERATOR_KHRONOS_SPIRV_TOOLS_LINKER            = 17,
    SPV_REFLECT_GENERATOR_WINE_VKD3D_SHADER_COMPILER            = 18,
    SPV_REFLECT_GENERATOR_CLAY_CLAY_SHADER_COMPILER             = 19,
    SPV_REFLECT_GENERATOR_SLANG_SHADER_COMPILER                 = 40,
} SpvReflectGenerator;

typedef enum SpvReflectUserType {
    SPV_REFLECT_USER_TYPE_INVALID = 0,
} SpvReflectUserType;

// ---- Structs ----

typedef struct SpvReflectNumericTraits {
    struct { uint32_t width; uint32_t signedness; } scalar;
    struct { uint32_t component_count; } vector;
    struct { uint32_t column_count; uint32_t row_count; uint32_t stride; } matrix;
} SpvReflectNumericTraits;

typedef struct SpvReflectImageTraits {
    uint32_t dim;
    uint32_t depth;
    uint32_t arrayed;
    uint32_t ms;
    uint32_t sampled;
    uint32_t image_format;
} SpvReflectImageTraits;

typedef struct SpvReflectArrayTraits {
    uint32_t dims_count;
    uint32_t dims[SPV_REFLECT_MAX_ARRAY_DIMS];
    uint32_t spec_constant_op_ids[SPV_REFLECT_MAX_ARRAY_DIMS];
    uint32_t stride;
} SpvReflectArrayTraits;

typedef struct SpvReflectBindingArrayTraits {
    uint32_t dims_count;
    uint32_t dims[SPV_REFLECT_MAX_ARRAY_DIMS];
} SpvReflectBindingArrayTraits;

typedef struct SpvReflectTypeDescription {
    uint32_t                           id;
    uint32_t                           op;
    const char*                        type_name;
    const char*                        struct_member_name;
    SpvReflectTypeFlags                type_flags;
    SpvReflectDecorationFlags          decoration_flags;
    struct {
        SpvReflectNumericTraits        numeric;
        SpvReflectImageTraits          image;
        SpvReflectArrayTraits          array;
    } traits;
    struct SpvReflectTypeDescription*  struct_type_description;
    uint32_t                           member_count;
    struct SpvReflectTypeDescription*  members;
} SpvReflectTypeDescription;

typedef struct SpvReflectInterfaceVariable {
    uint32_t                            spirv_id;
    const char*                         name;
    uint32_t                            location;
    uint32_t                            component;
    uint32_t                            storage_class;
    const char*                         semantic;
    SpvReflectDecorationFlags           decoration_flags;
    uint32_t                            built_in;
    SpvReflectNumericTraits             numeric;
    SpvReflectArrayTraits               array;
    uint32_t                            member_count;
    struct SpvReflectInterfaceVariable* members;
    SpvReflectFormat                    format;
    SpvReflectTypeDescription*          type_description;
    struct { uint32_t location; }       word_offset;
    SpvReflectVariableFlags             flags;
} SpvReflectInterfaceVariable;

typedef struct SpvReflectBlockVariable {
    uint32_t                          spirv_id;
    const char*                       name;
    uint32_t                          offset;
    uint32_t                          absolute_offset;
    uint32_t                          size;
    uint32_t                          padded_size;
    SpvReflectDecorationFlags         decoration_flags;
    SpvReflectNumericTraits           numeric;
    SpvReflectArrayTraits             array;
    SpvReflectVariableFlags           flags;
    uint32_t                          member_count;
    struct SpvReflectBlockVariable*   members;
    SpvReflectTypeDescription*        type_description;
    struct { uint32_t offset; }       word_offset;
} SpvReflectBlockVariable;

typedef struct SpvReflectDescriptorBinding {
    uint32_t                            spirv_id;
    const char*                         name;
    uint32_t                            binding;
    uint32_t                            input_attachment_index;
    uint32_t                            set;
    SpvReflectDescriptorType            descriptor_type;
    SpvReflectResourceType              resource_type;
    SpvReflectImageTraits               image;
    SpvReflectBlockVariable             block;
    SpvReflectBindingArrayTraits        array;
    uint32_t                            count;
    uint32_t                            accessed;
    uint32_t                            uav_counter_id;
    struct SpvReflectDescriptorBinding* uav_counter_binding;
    SpvReflectTypeDescription*          type_description;
    struct { uint32_t binding; uint32_t set; } word_offset;
    SpvReflectDecorationFlags           decoration_flags;
    SpvReflectUserType                  user_type;
} SpvReflectDescriptorBinding;

typedef struct SpvReflectDescriptorSet {
    uint32_t                      set;
    uint32_t                      binding_count;
    SpvReflectDescriptorBinding** bindings;
} SpvReflectDescriptorSet;

typedef struct SpvReflectSpecializationConstant {
    uint32_t                   spirv_id;
    uint32_t                   constant_id;
    const char*                name;
    uint32_t                   default_value;
    SpvReflectTypeDescription* type_description;
} SpvReflectSpecializationConstant;

typedef struct SpvReflectEntryPoint {
    const char*                   name;
    uint32_t                      id;
    uint32_t                      spirv_execution_model;
    SpvReflectShaderStageFlagBits shader_stage;
    uint32_t                      input_variable_count;
    SpvReflectInterfaceVariable** input_variables;
    uint32_t                      output_variable_count;
    SpvReflectInterfaceVariable** output_variables;
    uint32_t                      interface_variable_count;
    SpvReflectInterfaceVariable*  interface_variables;
    uint32_t                      descriptor_set_count;
    SpvReflectDescriptorSet*      descriptor_sets;
    uint32_t                      used_uniform_count;
    uint32_t*                     used_uniforms;
    uint32_t                      used_push_constant_count;
    uint32_t*                     used_push_constants;
    uint32_t                      execution_mode_count;
    uint32_t*                     execution_modes;
    struct {
        uint32_t                  x;
        uint32_t                  y;
        uint32_t                  z;
    } local_size;
    uint32_t                      invocations;
    uint32_t                      output_vertices;
} SpvReflectEntryPoint;

typedef struct SpvReflectCapability {
    uint32_t value;
    uint32_t word_offset;
} SpvReflectCapability;

typedef struct SpvReflectShaderModule {
    SpvReflectGenerator               generator;
    const char*                       entry_point_name;
    uint32_t                          entry_point_id;
    uint32_t                          entry_point_count;
    SpvReflectEntryPoint*             entry_points;
    uint32_t                          source_language;
    uint32_t                          source_language_version;
    const char*                       source_file;
    const char*                       source_source;
    uint32_t                          capability_count;
    SpvReflectCapability*             capabilities;
    SpvReflectShaderStageFlagBits     shader_stage;
    uint32_t                          descriptor_binding_count;
    SpvReflectDescriptorBinding*      descriptor_bindings;
    uint32_t                          descriptor_set_count;
    SpvReflectDescriptorSet           descriptor_sets[SPV_REFLECT_MAX_DESCRIPTOR_SETS];
    uint32_t                          input_variable_count;
    SpvReflectInterfaceVariable**     input_variables;
    uint32_t                          output_variable_count;
    SpvReflectInterfaceVariable**     output_variables;
    uint32_t                          interface_variable_count;
    SpvReflectInterfaceVariable*      interface_variables;
    uint32_t                          push_constant_block_count;
    SpvReflectBlockVariable*          push_constant_blocks;
    uint32_t                          spec_constant_count;
    SpvReflectSpecializationConstant* spec_constants;
    struct {
        SpvReflectModuleFlags         module_flags;
        size_t                        spirv_size;
        uint32_t*                     spirv_code;
        uint32_t                      spirv_word_count;
        size_t                        type_description_count;
        SpvReflectTypeDescription*    type_descriptions;
    } _internal;
} SpvReflectShaderModule;

// ---- API ----

SpvReflectResult spvReflectCreateShaderModule(size_t size, const void* pCode, SpvReflectShaderModule* pModule);
void             spvReflectDestroyShaderModule(SpvReflectShaderModule* pModule);
uint32_t         spvReflectGetCodeSize(const SpvReflectShaderModule* pModule);
const uint32_t*  spvReflectGetCode(const SpvReflectShaderModule* pModule);

SpvReflectResult spvReflectEnumerateDescriptorBindings(const SpvReflectShaderModule* pModule, uint32_t* pCount, SpvReflectDescriptorBinding** ppBindings);
SpvReflectResult spvReflectEnumerateDescriptorSets(const SpvReflectShaderModule* pModule, uint32_t* pCount, SpvReflectDescriptorSet** ppSets);
SpvReflectResult spvReflectEnumerateInputVariables(const SpvReflectShaderModule* pModule, uint32_t* pCount, SpvReflectInterfaceVariable** ppVariables);
SpvReflectResult spvReflectEnumerateOutputVariables(const SpvReflectShaderModule* pModule, uint32_t* pCount, SpvReflectInterfaceVariable** ppVariables);
SpvReflectResult spvReflectEnumeratePushConstantBlocks(const SpvReflectShaderModule* pModule, uint32_t* pCount, SpvReflectBlockVariable** ppBlocks);
SpvReflectResult spvReflectEnumerateSpecializationConstants(const SpvReflectShaderModule* pModule, uint32_t* pCount, SpvReflectSpecializationConstant** ppConstants);

const SpvReflectDescriptorBinding* spvReflectGetDescriptorBinding(const SpvReflectShaderModule* pModule, uint32_t binding_number, uint32_t set_number, SpvReflectResult* pResult);
const SpvReflectDescriptorSet*     spvReflectGetDescriptorSet(const SpvReflectShaderModule* pModule, uint32_t set_number, SpvReflectResult* pResult);
const SpvReflectInterfaceVariable* spvReflectGetInputVariableByLocation(const SpvReflectShaderModule* pModule, uint32_t location, SpvReflectResult* pResult);
const SpvReflectInterfaceVariable* spvReflectGetInputVariable(const SpvReflectShaderModule* pModule, uint32_t location, SpvReflectResult* pResult);
const SpvReflectInterfaceVariable* spvReflectGetOutputVariableByLocation(const SpvReflectShaderModule* pModule, uint32_t location, SpvReflectResult* pResult);
const SpvReflectInterfaceVariable* spvReflectGetOutputVariable(const SpvReflectShaderModule* pModule, uint32_t location, SpvReflectResult* pResult);
const SpvReflectBlockVariable*     spvReflectGetPushConstantBlock(const SpvReflectShaderModule* pModule, uint32_t index, SpvReflectResult* pResult);

SpvReflectResult spvReflectChangeDescriptorBindingNumbers(SpvReflectShaderModule* pModule, const SpvReflectDescriptorBinding* pBinding, uint32_t new_binding_number, uint32_t new_set_number);
SpvReflectResult spvReflectChangeDescriptorSetNumber(SpvReflectShaderModule* pModule, const SpvReflectDescriptorSet* pSet, uint32_t new_set_number);

const char* spvReflectResultToString(SpvReflectResult result);

const char* spvReflectGetEntryPointName(const SpvReflectShaderModule* pModule, uint32_t index);
uint32_t    spvReflectGetEntryPointId(const SpvReflectShaderModule* pModule, const char* entry_point);
SpvReflectShaderStageFlagBits spvReflectGetShaderStage(const SpvReflectShaderModule* pModule);

#ifdef __cplusplus
}
#endif

#endif // SPIRV_REFLECT_WRAPPER_H
