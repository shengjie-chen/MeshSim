#ifndef __CONFIG_H_
#define __CONFIG_H_
#include "svdpi.h"
#include "time.h"
#include "verilated.h"
#include "verilated_fst_c.h"
#include <cstdlib>
#include <iostream>
#include <stdint.h>
#include <sys/time.h>

// #define u_align(x, n) ((x + n - 1) & -n)
#define u_align(x, n) (((x - 1) / n + 1) * n)

#define ACCEL_mesh_size 32
#define MAT_SIZE ACCEL_mesh_size

#define ACCEL_ifm_w 17
#define ACCEL_ifm_h 125
#define ACCEL_ifm_c (ACCEL_mesh_size * 9)

#define ACCEL_data_max 30

#define ACCEL_kernel 1
#define ACCEL_stride 1

#define ACCEL_padding_left 0
#define ACCEL_padding_right ACCEL_padding_left
#define ACCEL_padding_top 0
#define ACCEL_padding_down ACCEL_padding_top

#define ACCEL_ofm_c (ACCEL_mesh_size * 8)
#define ACCEL_ofm_w ((ACCEL_ifm_w - ACCEL_kernel + 2 * ACCEL_padding_left) / ACCEL_stride + 1)
#define ACCEL_ofm_h ((ACCEL_ifm_h - ACCEL_kernel + 2 * ACCEL_padding_top) / ACCEL_stride + 1)

#define ACCEL_ifm_x_block_num (ACCEL_kernel * ACCEL_kernel * ACCEL_ifm_c / ACCEL_mesh_size)
#define ACCEL_ifm_y_block_num                                                                      \
  (u_align((ACCEL_ofm_w * ACCEL_ofm_h), (2 * ACCEL_mesh_size)) / ACCEL_mesh_size)
#define ACCEL_ifm_block_num (ACCEL_ifm_y_block_num * ACCEL_ifm_x_block_num)
#define ACCEL_ifm_block_num_div2 (ACCEL_ifm_block_num / 2)

#define ACCEL_ofm_x_block_num (ACCEL_ofm_c / ACCEL_mesh_size)
#define ACCEL_ofm_y_block_num ACCEL_ifm_y_block_num
#define ACCEL_ofm_block_num (ACCEL_ofm_y_block_num * ACCEL_ofm_x_block_num)

#define ACCEL_w_x_block_num (ACCEL_ofm_c / ACCEL_mesh_size)
#define ACCEL_w_y_block_num ACCEL_ifm_x_block_num
#define ACCEL_w_block_num (ACCEL_w_y_block_num * ACCEL_w_x_block_num)

// #define DEBUG_MODE
// #define EXPORT_VCD

#define W_SWITCH_BLOCK

int RandomInt(int a = -127, int b = 127) { return (rand() % (b - a + 1)) + a; }

using namespace std;
#define INPUT_NUM ACCEL_ifm_block_num / 2

#endif
