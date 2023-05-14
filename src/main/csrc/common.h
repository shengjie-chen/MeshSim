#ifndef __COMMON_H_
#define __COMMON_H_

#include "config.h"

#define TOP_IFM_BITS_ROW(a) *(((IData *)&top->io_ifm_bits_0) + a)
#define TOP_W_BITS_COL(a) *(((IData *)&top->io_w_bits_0) + a)

const vluint64_t sim_time = 2 * (5 + ACCEL_ifm_block_num_div2 * ACCEL_ofm_x_block_num) * MAT_SIZE +
                            1000; // 最高仿真时间 可选：100

// print
void MatPrint(int32_t A[MAT_SIZE][MAT_SIZE]) {
  for (int row = 0; row < MAT_SIZE; row++) {
    for (int col = 0; col < MAT_SIZE; col++) {
      // cout << A[row][col] << "  \t";
      printf("%08x(%04d)\t", A[row][col], A[row][col]);
      // printf("%04d\t", A[row][col], A[row][col]);
    }
    cout << endl;
  }
}

void MatMul(int32_t A[MAT_SIZE][MAT_SIZE], int32_t B[MAT_SIZE][MAT_SIZE],
            int32_t C[MAT_SIZE][MAT_SIZE]) {
  for (int row = 0; row < MAT_SIZE; row++) {
    for (int col = 0; col < MAT_SIZE; col++) {
      int32_t Cvalue = 0;
      for (int e = 0; e < MAT_SIZE; ++e) {
        Cvalue += A[row][e] * B[e][col];
      }
      C[row][col] = Cvalue;
    }
  }
}

void MatInit(int32_t A[MAT_SIZE][MAT_SIZE]) {
  for (int row = 0; row < MAT_SIZE; row++) {
    for (int col = 0; col < MAT_SIZE; col++) {
      A[row][col] = RandomInt(-ACCEL_data_max, ACCEL_data_max);
    }
  }
}

// ################ stimulator ################

// input
int32_t ifm0[ACCEL_ifm_block_num_div2][MAT_SIZE][MAT_SIZE];
int32_t ifm1[ACCEL_ifm_block_num_div2][MAT_SIZE][MAT_SIZE];
int32_t w[ACCEL_w_block_num][MAT_SIZE][MAT_SIZE];
// gold
int32_t ofm[ACCEL_ofm_x_block_num][ACCEL_ofm_y_block_num][ACCEL_ifm_x_block_num][MAT_SIZE]
           [MAT_SIZE] = {0};

void InputInit() {
  cout << "ofm w:           " << ACCEL_ofm_w << endl;
  cout << "ofm h:           " << ACCEL_ofm_h << endl;
  cout << "ifm block num:   " << ACCEL_ifm_block_num << endl;
  cout << "ifm x block num: " << ACCEL_ifm_x_block_num << endl;
  cout << "ifm y block num: " << ACCEL_ifm_y_block_num << endl;
  cout << "w block num:     " << ACCEL_w_block_num << endl;
  cout << "ofm block num:   " << ACCEL_ofm_block_num << endl;
  cout << "ofm x block num: " << ACCEL_ofm_x_block_num << endl;
  cout << "ofm y block num: " << ACCEL_ofm_y_block_num << endl;

  cout << "************ GEN IFM ************" << endl;
  for (int i = 0; i < ACCEL_ifm_block_num_div2; i++) {
    MatInit(ifm0[i]);
    MatInit(ifm1[i]);
#ifdef DEBUG_MODE
    cout << "ifm0[" << i << "]" << endl;
    MatPrint(ifm0[i]);
    cout << "ifm1[" << i << "]" << endl;
    MatPrint(ifm1[i]);
#endif
  }
  for (int i = 0; i < ACCEL_w_block_num; i++) {
    MatInit(w[i]);
#ifdef DEBUG_MODE
    cout << "w[" << i << "]" << endl;
    MatPrint(w[i]);
#endif
  }

  // ofm
  cout << "************ GOLD OFM ************" << endl;
  for (int i = 0; i < ACCEL_ofm_x_block_num; i++) {
    for (int j = 0; j < ACCEL_ofm_y_block_num; j++) {
      for (int k = 0; k < ACCEL_ifm_x_block_num; k++) {
        if (j % 2 == 0) {
          MatMul(ifm0[k + j / 2 * ACCEL_ifm_x_block_num], w[i * ACCEL_ifm_x_block_num + k],
                 ofm[i][j][k]);
        } else {
          MatMul(ifm1[k + j / 2 * ACCEL_ifm_x_block_num], w[i * ACCEL_ifm_x_block_num + k],
                 ofm[i][j][k]);
        }
#ifdef DEBUG_MODE
        cout << "ofm[" << i << "][" << j << "][" << k << "]:" << endl;
        MatPrint(ofm[i][j][k]);
#endif
      }
    }
  }
}

#ifdef MeshTop
VMeshTop *top = new VMeshTop;
#else
VMesh *top = new VMesh;
#endif
VerilatedFstC *tfp = new VerilatedFstC;

void change_ifm() {
  static int ifm_row_p = 0;                 // row pointer in one block
  static int input_index = 0;               // NO. of (64x32) ifm block
  static int ifm_epoch = 0;                 // max ACCEL_ofm_x_block_num , ifm epoch nums
  if (ifm_epoch == ACCEL_ofm_x_block_num) { // finish
    for (int i = 0; i < MAT_SIZE; i++) {
      TOP_IFM_BITS_ROW(i) = 0;
    }
    top->io_ifm_valid = 0;
    top->io_last_in = 0;
  } else {
    // drive ifm port
    for (int i = 0; i < MAT_SIZE; i++) {
      TOP_IFM_BITS_ROW(i) =
          (ifm0[input_index][ifm_row_p][i] & 0xffff) + (ifm1[input_index][ifm_row_p][i] << 16);
      // if (i = 0 && input_index == 0 && ifm_row_p == 0) {
      //   printf("ifm0[0][0][0] = %d\n", ifm0[input_index][ifm_row_p][i]);
      //   printf("ifm1[0][0][0] = %d\n", ifm1[input_index][ifm_row_p][i]);
      // }
    }
    if (input_index % ACCEL_ifm_x_block_num == (ACCEL_ifm_x_block_num - 1)) {
      top->io_last_in = 1;
    } else {
      top->io_last_in = 0;
    }

    // change pointer
    if (ifm_row_p == MAT_SIZE - 1) {
      ifm_row_p = 0;
      if (input_index == ACCEL_ifm_block_num_div2 - 1) {
        input_index = 0;
        ifm_epoch++;
      } else {
        input_index++;
      }
    } else {
      ifm_row_p++;
    }
  }
}

void change_w(int &w_onecol_finish_switch) {// block when w col change
  static int w_row_p = MAT_SIZE - 1; // row pointer in one w block
  static int w_index = 0;            // NO. of (32x32) w block, col0row0, col0row1, col1row0, ...
  static int w_onecol_epoch = 0;     // max ACCEL_ifm_y_block_num/2 , w one col epoch nums

  // if (w_index == ACCEL_ifm_x_block_num * ACCEL_ofm_x_block_num && w_onecol_epoch ==
  // ACCEL_ofm_x_block_num)
  // {
  //   for (int i = 0; i < MAT_SIZE; i++) {
  //     TOP_W_BITS_COL(i) = 0;
  //   }
  //   top->io_w_valid = 0;
  // } else {

  if (w_index == ACCEL_w_y_block_num * ACCEL_w_x_block_num) { // finish
    top->io_w_valid = 0;
    top->io_w_finish = 0;
    for (int i = 0; i < MAT_SIZE; i++) {
      TOP_W_BITS_COL(i) = 0;
    }
  } else {
    // drive w port
    for (int i = 0; i < MAT_SIZE; i++) {
      TOP_W_BITS_COL(i) = w[w_index][w_row_p][i];
    }
    if (w_row_p == MAT_SIZE - 1) {
      if ((w_index % ACCEL_w_y_block_num) == 0 && w_index != 0) { // w this col this time start
        if (w_onecol_epoch == 0) {                                // w this col start
          if (!w_onecol_finish_switch) {
            top->io_w_valid = 0;
            w_onecol_finish_switch = 1;
            return;
          } else {
            top->io_w_valid = 1;
            w_onecol_finish_switch = 0;
          }
        }
      }
    }

    // change pointer
    if (w_row_p == 0) {
      w_row_p = MAT_SIZE - 1;
      // printf("%d\n", top->io_w_valid);
      // printf("w_index:%d\n", w_index);
      // printf("w_onecol_epoch:  %d\n", w_onecol_epoch);
      // printf("w_row_p:%d\n", w_row_p);
      if ((w_index % ACCEL_w_y_block_num) ==
          (ACCEL_w_y_block_num - 1)) {                         // w this col this time finish
        if (w_onecol_epoch == ACCEL_ifm_y_block_num / 2 - 1) { // w this col finish
          w_index++;
          w_onecol_epoch = 0;
        } else {
          w_index = w_index - ACCEL_w_y_block_num + 1;
          w_onecol_epoch++;
        }
      } else {
        w_index++;
      }
    } else {
      w_row_p--;
    }

    if (w_index == ACCEL_w_y_block_num * ACCEL_w_x_block_num) { // w finish one block
      top->io_w_finish = 1;
    }
  }
}

void change_w() {                    // dont block when w col change
  static int w_row_p = MAT_SIZE - 1; // row pointer in one w block
  static int w_index = 0;            // NO. of (32x32) w block, col0row0, col0row1, col1row0, ...
  static int w_onecol_epoch = 0;     // max ACCEL_ifm_y_block_num/2 , w one col epoch nums

  // if (w_index == ACCEL_ifm_x_block_num * ACCEL_ofm_x_block_num && w_onecol_epoch ==
  // ACCEL_ofm_x_block_num)
  // {
  //   for (int i = 0; i < MAT_SIZE; i++) {
  //     TOP_W_BITS_COL(i) = 0;
  //   }
  //   top->io_w_valid = 0;
  // } else {

  if (w_index == ACCEL_w_y_block_num * ACCEL_w_x_block_num) { // finish
    top->io_w_valid = 0;
    top->io_w_finish = 0;
    for (int i = 0; i < MAT_SIZE; i++) {
      TOP_W_BITS_COL(i) = 0;
    }
  } else {
    // drive w port
    for (int i = 0; i < MAT_SIZE; i++) {
      TOP_W_BITS_COL(i) = w[w_index][w_row_p][i];
    }

    // change pointer
    if (w_row_p == 0) {
      w_row_p = MAT_SIZE - 1;
      // printf("%d\n", top->io_w_valid);
      // printf("w_index:%d\n", w_index);
      // printf("w_onecol_epoch:  %d\n", w_onecol_epoch);
      // printf("w_row_p:%d\n", w_row_p);
      if ((w_index % ACCEL_w_y_block_num) ==
          (ACCEL_w_y_block_num - 1)) {                         // w this col this time finish
        if (w_onecol_epoch == ACCEL_ifm_y_block_num / 2 - 1) { // w this col finish
          w_index++;
          w_onecol_epoch = 0;
        } else {
          w_index = w_index - ACCEL_w_y_block_num + 1;
          w_onecol_epoch++;
        }
      } else {
        w_index++;
      }
    } else {
      w_row_p--;
    }

    if (w_index == ACCEL_w_y_block_num * ACCEL_w_x_block_num) { // w finish one block
      top->io_w_finish = 1;
    }
  }
}

void one_clock();

void w_before_2c() {
  top->io_w_valid = 1;
  top->eval();
  one_clock();
  one_clock();
  top->io_ifm_valid = 1;
  top->eval();
}

void ifm_before_2c() {
  top->io_ifm_valid = 1;
  top->eval();
  one_clock();
  one_clock();
  top->io_w_valid = 1;
  top->eval();
}

void w_ifm_sametime() {
  top->io_w_valid = 1;
  top->io_ifm_valid = 1;
  top->eval();
}

#endif