#ifndef __MESH_MACRO__
#define __MESH_MARCO__

#define INPUT_MAX 30
#define INPUT_NUM 2
#define MAT_SIZE 3

#if (MAT_SIZE == 2)
#define LOOP(f) f(0) f(1)
#define val_output_index_max output_index_##1
#elif (MAT_SIZE == 3)
#define LOOP(f) f(0) f(1) f(2)
#define val_output_index_max output_index_##2
#endif

#endif