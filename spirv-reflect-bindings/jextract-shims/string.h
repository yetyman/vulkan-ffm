/* Shim for jextract — spirv_reflect.h includes string.h for memcpy,
   but jextract only needs the struct/function declarations. */
#ifndef _JEXTRACT_STRING_H
#define _JEXTRACT_STRING_H
static inline void* memcpy(void* dst, const void* src, unsigned long long n) { (void)dst; (void)src; (void)n; return dst; }
#endif
