#include "macro.h"
#include <cstdlib>
#include <iostream>
#include <stdint.h>

#define def_output_index(a) int output_index_##a = 0;
LOOP(def_output_index)
// int output_index_0 = 0;
// int output_index_1 = 0;

void change_ifm() {
#define ifm_assign_0(a) top->io_ifm_bits_##a = 0;
#define ifm_assign_val(a) \
  top->io_ifm_bits_##a = ifm0[input_index][ifm_row_p][a] + (ifm1[input_index][ifm_row_p][a] << 16);
  if (input_index == INPUT_NUM) {
    // top->io_ifm_bits_0 = 0;
    // top->io_ifm_bits_1 = 0;
    LOOP(ifm_assign_0)
    top->io_ifm_valid = 0;
  } else {
    // top->io_ifm_bits_0 = ifm0[input_index][ifm_row_p][0] + (ifm1[input_index][ifm_row_p][0] << 16);
    // top->io_ifm_bits_1 = ifm0[input_index][ifm_row_p][1] + (ifm1[input_index][ifm_row_p][1] << 16);
    LOOP(ifm_assign_val)
    // printf("%x\n",ifm0[input_index][ifm_row_p][0] );
    // printf("%x\n",top->io_ifm_bits_0 );
    // printf("%x\n",top->io_ifm_bits_1);
    ifm_row_p++;
    if (ifm_row_p == MAT_SIZE) {
      ifm_row_p = 0;
      input_index++;
    }
  }
}

void change_w() {
#define w_assign_0(a) top->io_w_bits_##a = 0;
#define w_assign_val(a) \
  top->io_w_bits_##a = w[w_index][w_row_p][a];
  if (w_index == INPUT_NUM) {
    // top->io_w_bits_0 = 0;
    // top->io_w_bits_1 = 0;
    LOOP(w_assign_0)
    top->io_w_valid = 0;
  } else {
    // top->io_w_bits_0 = w[w_index][w_row_p][0];
    // top->io_w_bits_1 = w[w_index][w_row_p][1];
    LOOP(w_assign_val)
    // printf("w[%d][%d][0]:%d\t", w_index, w_row_p, w[w_index][w_row_p][0]);
    // printf("w[%d][%d][1]:%d\n", w_index, w_row_p, w[w_index][w_row_p][1]);
    // printf("%x %x\n", top->io_w_bits_0, top->io_w_bits_1);
    if (w_row_p == 0) {
      w_index++;
      w_row_p = MAT_SIZE - 1;
    } else {
      w_row_p--;
    }
  }
}

void change_input() {
  // change ifm
  if (ifm_hs_reg) {
    change_ifm();
  }

  // change w
  if (w_hs_reg) {
    change_w();
  }


#define output_save(a)                                                                          \
  if (top->io_ofm_##a##_valid) {                                                                \
    if (output_index_##a != INPUT_NUM) {                                                        \
      hw_ofm0[output_index_##a][top->io_ofm_##a##_bits_addr][a] = top->io_ofm_##a##_bits_data0; \
      hw_ofm1[output_index_##a][top->io_ofm_##a##_bits_addr][a] = top->io_ofm_##a##_bits_data1; \
      if (top->io_ofm_##a##_bits_addr == MAT_SIZE - 1) {                                        \
        output_index_##a++;                                                                     \
      }                                                                                         \
    }                                                                                           \
  }

  LOOP(output_save)

  // // output0 save
  // if (top->io_ofm_0_valid) {
  //   if (output_index_0 != INPUT_NUM) {
  //     hw_ofm0[output_index_0][top->io_ofm_0_bits_addr][0] = top->io_ofm_0_bits_data0;
  //     hw_ofm1[output_index_0][top->io_ofm_0_bits_addr][0] = top->io_ofm_0_bits_data1;
  //     if (top->io_ofm_0_bits_addr == MAT_SIZE - 1) {
  //       output_index_0++;
  //     }
  //   }
  // }

  // // output1 save and check
  // if (top->io_ofm_1_valid) {
  //   if (output_index_1 != INPUT_NUM) {
  //     hw_ofm0[output_index_1][top->io_ofm_1_bits_addr][1] = top->io_ofm_1_bits_data0;
  //     hw_ofm1[output_index_1][top->io_ofm_1_bits_addr][1] = top->io_ofm_1_bits_data1;
  //     if (top->io_ofm_1_bits_addr == MAT_SIZE - 1) {
  //       output_index_1++;
  //     }
  //   }
  // }

  if (val_output_index_max == INPUT_NUM){
    check_ofm();                                                     
  }

  update_reg();
}