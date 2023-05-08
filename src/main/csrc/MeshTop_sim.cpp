#include "VMeshTop.h"
#include "config.h"
#include "svdpi.h"
#include "time.h"
#include "verilated.h"
#include "verilated_fst_c.h"
#include <cstdlib>
#include <iostream>
#include <stdint.h>
#include <sys/time.h>

using namespace std;

#define MAT_SIZE ACCEL_mesh_size

// TOP IO PORT
#define TOP_IFM_BITS_ROW(a) *(((IData *)&top->io_ifm_bits_0) + a)
#define TOP_W_BITS_COL(a) *(((IData *)&top->io_w_bits_0) + a)
#define OUT_ADDR_D_GAP ((uint64_t)&top->io_out_bits_data0_1 - (uint64_t)&top->io_out_bits_data0_0)
#define TOP_OUT_DATA0_COL(a) *(IData *)((uint64_t)&top->io_out_bits_data0_0 + OUT_ADDR_D_GAP * a)
#define TOP_OUT_DATA1_COL(a) *(IData *)((uint64_t)&top->io_out_bits_data1_0 + OUT_ADDR_D_GAP * a)

vluint64_t main_time = 0; // 当前仿真时间
const vluint64_t sim_time = 2 * (5 + ACCEL_ifm_block_num_div2 * ACCEL_ofm_x_block_num) * MAT_SIZE +
                            1000; // 最高仿真时间 可选：100

VMeshTop *top = new VMeshTop;
VerilatedFstC *tfp = new VerilatedFstC;

int sim_finish = 0;

// input
uint32_t ifm0[ACCEL_ifm_block_num_div2][MAT_SIZE][MAT_SIZE];
uint32_t ifm1[ACCEL_ifm_block_num_div2][MAT_SIZE][MAT_SIZE];
uint32_t w[ACCEL_w_block_num][MAT_SIZE][MAT_SIZE];
// gold
uint32_t ofm[ACCEL_ofm_x_block_num][ACCEL_ofm_y_block_num][ACCEL_ifm_x_block_num][MAT_SIZE]
            [MAT_SIZE] = {0};
uint32_t out[ACCEL_ofm_block_num][MAT_SIZE][MAT_SIZE] = {0};
// dut
uint32_t hw_out[ACCEL_ofm_block_num][MAT_SIZE][MAT_SIZE] = {0};

// print
void MatPrint(uint32_t A[MAT_SIZE][MAT_SIZE]) {
  for (int row = 0; row < MAT_SIZE; row++) {
    for (int col = 0; col < MAT_SIZE; col++) {
      // cout << A[row][col] << "  \t";
      printf("%05x\t", A[row][col]);
    }
    cout << endl;
  }
}

// ################ stimulator ################

void MatMul(uint32_t A[MAT_SIZE][MAT_SIZE], uint32_t B[MAT_SIZE][MAT_SIZE],
            uint32_t C[MAT_SIZE][MAT_SIZE]) {
  for (int row = 0; row < MAT_SIZE; row++) {
    for (int col = 0; col < MAT_SIZE; col++) {
      uint32_t Cvalue = 0;
      for (int e = 0; e < MAT_SIZE; ++e) {
        Cvalue += A[row][e] * B[e][col];
      }
      C[row][col] = Cvalue;
    }
  }
}

void MatInit(uint32_t A[MAT_SIZE][MAT_SIZE]) {
  for (int row = 0; row < MAT_SIZE; row++) {
    for (int col = 0; col < MAT_SIZE; col++) {
      A[row][col] = rand() % ACCEL_data_max;
    }
  }
}

void InputInit() {
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

  // for (int i = 0; i < ACCEL_ifm_block_num_div2; i++) {
  //   cout << "************ GOLD RESULT " << i << " ************" << endl;
  //   MatInit(ifm0[i]);
  //   MatInit(ifm1[i]);
  //   MatInit(w[i]);
  //   MatMul(ifm0[i], w[i], ofm0[i]);
  //   MatMul(ifm1[i], w[i], ofm1[i]);

  //   cout << "ifm0:" << endl;
  //   MatPrint(ifm0[i]);
  //   cout << "ifm1:" << endl;
  //   MatPrint(ifm1[i]);
  //   cout << "w:" << endl;
  //   MatPrint(w[i]);
  //   cout << "ofm0:" << endl;
  //   MatPrint(ofm0[i]);
  //   cout << "ofm1:" << endl;
  //   MatPrint(ofm1[i]);
  // }

  cout << "************ GOLD OUT ************" << endl;
  for (int i = 0; i < ACCEL_ofm_x_block_num; i++) {
    for (int j = 0; j < ACCEL_ifm_y_block_num; j++) {
      for (int k = 0; k < ACCEL_ifm_x_block_num; k++) {
        for (int m = 0; m < MAT_SIZE; m++) {
          for (int n = 0; n < MAT_SIZE; n++) {
            out[i * ACCEL_ofm_y_block_num + j][m][n] =
                ofm[i][j][k][m][n] + out[i * ACCEL_ofm_y_block_num + j][m][n];
          }
        }
      }
    }
  }
#ifdef DEBUG_MODE
  for (int i = 0; i < ACCEL_ofm_x_block_num; i++) {
    for (int j = 0; j < ACCEL_ofm_y_block_num; j++) {
      cout << "out[" << i << "][" << j << "]:" << endl;
      MatPrint(out[i * ACCEL_ofm_y_block_num + j]);
    }
  }
#endif
  cout << "************ GOLD OUT FINISH ************" << endl;

  // for (int i = 0; i < ACCEL_ofm_block_num; i++) {
  //   for (int j = 0; j < MAT_SIZE; j++) {
  //     for (int k = 0; k < MAT_SIZE; k++) {
  //       if (i % 2 == 0) {
  //         for (int m = 0; m < ACCEL_ifm_x_block_num; m++) {
  //           out[i][j][k] = ofm0[i / 2 * ACCEL_ifm_x_block_num + m][j][k] + out[i][j][k];
  //         }
  //       } else {
  //         for (int m = 0; m < ACCEL_ifm_x_block_num; m++) {
  //           out[i][j][k] = ofm1[i / 2 * ACCEL_ifm_x_block_num + m][j][k] + out[i][j][k];
  //         }
  //       }
  //     }
  //   }
  //   cout << "out" << i << endl;
  //   MatPrint(out[i]);
  // }
}

void change_ifm() {
  static int ifm_row_p = 0;
  static int input_index = 0;
  static int ifm_cnt = 0; // max ACCEL_ofm_x_block_num , ergodic ifm nums
  if (ifm_cnt == ACCEL_ofm_x_block_num) {
    for (int i = 0; i < MAT_SIZE; i++) {
      TOP_IFM_BITS_ROW(i) = 0;
    }
    top->io_ifm_valid = 0;
    top->io_last_in = 0;
  } else {
    for (int i = 0; i < MAT_SIZE; i++) {
      TOP_IFM_BITS_ROW(i) =
          ifm0[input_index][ifm_row_p][i] + (ifm1[input_index][ifm_row_p][i] << 16);
    }
    if (input_index % ACCEL_ifm_x_block_num == (ACCEL_ifm_x_block_num - 1)) {
      top->io_last_in = 1;
    } else {
      top->io_last_in = 0;
    }

    if (ifm_row_p == MAT_SIZE - 1) {
      ifm_row_p = 0;
      if (input_index == ACCEL_ifm_block_num_div2 - 1) {
        input_index = 0;
        ifm_cnt++;
      } else {
        input_index++;
      }
    } else {
      ifm_row_p++;
    }
  }
}

void change_w() {
  static int w_row_p = MAT_SIZE - 1;
  static int w_index = 0;
  static int w_cnt = 0; // max ACCEL_ifm_y_block_num/2 , ergodic w one col nums
  // if (w_index == ACCEL_ifm_x_block_num * ACCEL_ofm_x_block_num && w_cnt == ACCEL_ofm_x_block_num)
  // {
  //   for (int i = 0; i < MAT_SIZE; i++) {
  //     TOP_W_BITS_COL(i) = 0;
  //   }
  //   top->io_w_valid = 0;
  // } else {

  if (top->io_w_valid == 1) {
    if (w_index == ACCEL_ifm_x_block_num * ACCEL_ofm_x_block_num) {
      top->io_w_valid = 0;
      for (int i = 0; i < MAT_SIZE; i++) {
        TOP_W_BITS_COL(i) = 0;
      }
    } else {

      for (int i = 0; i < MAT_SIZE; i++) {
        TOP_W_BITS_COL(i) = w[w_index][w_row_p][i];
      }

      if (w_row_p == 0) {
        // printf("%d\n", top->io_w_valid);
        // printf("w_index:%d\n", w_index);
        // printf("w_cnt:  %d\n", w_cnt);
        // printf("w_row_p:%d\n", w_row_p);
        if ((w_index % ACCEL_ifm_x_block_num) == (ACCEL_ifm_x_block_num - 1)) {
          // w this col this time finish
          if (w_cnt == ACCEL_ifm_y_block_num / 2 - 1) { // w this col finish
            w_index++;
            w_cnt = 0;
          } else {
            w_index = w_index - ACCEL_ifm_x_block_num + 1;
            w_cnt++;
          }
        } else {
          w_index++;
        }
        w_row_p = MAT_SIZE - 1;
      } else {
        w_row_p--;
      }
    }
  }
}

// ################ checker ################
void Outputprint() {
  for (int i = 0; i < ACCEL_ofm_block_num; i++) {
    cout << "************ HW RESULT " << i << " ************" << endl;
    cout << "out" << i << endl;
    MatPrint(hw_out[i]);
  }
}

void check_out() {
  printf("!!!!!!!!!!!!!!!!!!!!check Begin!!!!!!!!!!!!!\n");
  for (int i = 0; i < ACCEL_ofm_block_num; i++) {
    for (int r = 0; r < MAT_SIZE; r++) {
      for (int c = 0; c < MAT_SIZE; c++) {
        if (out[i][r][c] != hw_out[i][r][c]) {
          printf("check error: \ngold out[%d]\n", i);
          MatPrint(out[i]);
          printf("hw:\n");
          MatPrint(hw_out[i]);
          printf("!!!!!!!!!!!!!!!!!!!!check Fail!!!!!!!!!!!!!\n");
          return;
        }
      }
    }
  }
  printf("!!!!!!!!!!!!!!!!!!!!check pass!!!!!!!!!!!!!\n");
}

// ################ SIM ################
int ifm_hs_reg = 0;
int ifm_hs_reg_r = 0;
int w_hs_reg = 0;
void update_reg() {
  ifm_hs_reg_r = ifm_hs_reg;
  ifm_hs_reg = top->io_ifm_ready && top->io_ifm_valid;
  w_hs_reg = top->io_w_ready && top->io_w_valid;
}

void change_input() {
  static int output_index = 0;

  // change ifm
  if (ifm_hs_reg_r) {
    change_ifm();
  }

  // change w
  if (w_hs_reg) {
    change_w();
  }

  for (int i = 0; i < MAT_SIZE; i++) {
    if (top->io_out_valid) {
      hw_out[output_index / MAT_SIZE * 2][output_index % MAT_SIZE][i] = TOP_OUT_DATA0_COL(i);
      hw_out[output_index / MAT_SIZE * 2 + 1][output_index % MAT_SIZE][i] = TOP_OUT_DATA1_COL(i);
    }
  }

  // cout << output_index << endl;

  if (top->io_out_valid && output_index != MAT_SIZE * ACCEL_ofm_block_num / 2) {
    output_index++;
    // if (output_index % ACCEL_mesh_size == 0)
    //   cout << "[ log ] output_block_index: " << output_index / ACCEL_mesh_size << endl;
  }

  if (output_index == MAT_SIZE * ACCEL_ofm_block_num / 2) {
    // Outputprint();
    check_out();
    sim_finish = 1;
  }

  update_reg();
}

void one_clock() {
  //  cout << "one_clock" << endl;
  change_input();

  top->clock = 0;
  top->eval();
#ifdef EXPORT_VCD
  tfp->dump(main_time);
#endif
  main_time++;

  top->clock = 1;
  top->eval();
#ifdef EXPORT_VCD
  tfp->dump(main_time);
#endif
  main_time++;
}

int main(int argc, char **argv, char **env) {
  // srand((unsigned)time(NULL));

  Verilated::commandArgs(argc, argv);
  Verilated::traceEverOn(true);
  top->trace(tfp, 99);
  tfp->open("./build/MeshTop/MeshTop.wave");

  clock_t start, end;
  start = clock();

  // input init
  InputInit();
  top->io_stop = 0;
  top->io_w_finish = 0;

  // reset
  int n = 10;
  top->reset = 1;
  while (n-- > 0) {
    top->clock = 0;
    top->eval();
#ifdef EXPORT_VCD
    tfp->dump(main_time);
#endif

    main_time++;

    top->clock = 1;
    top->eval();
#ifdef EXPORT_VCD
    tfp->dump(main_time);
#endif
    main_time++;
  }

  top->reset = 0;
  // prepare data for 5 clock
  n = 5;
  while (n-- > 0) {
    update_reg();

    top->clock = 0;
    top->eval();
#ifdef EXPORT_VCD
    tfp->dump(main_time);
#endif
    main_time++;

    top->clock = 1;
    top->eval();
#ifdef EXPORT_VCD
    tfp->dump(main_time);
#endif
    main_time++;
  }

  // data valid
  top->io_w_valid = 1;
  top->io_ifm_valid = 1;
  change_ifm();
  change_w();

  // parse_args
  while (!Verilated::gotFinish() && main_time < sim_time && !sim_finish) {
    one_clock();
  }

  end = clock();
  int time = double(end - start) / CLOCKS_PER_SEC;
  uint64_t clock_cnt = main_time / 2;
  printf("************ SIM SUMMARY ************\n");
  printf("sim clock num            : %ld\n", clock_cnt);
  printf("simulation time          : %d min %d s\n", time / 60, time % 60);
  if (time != 0) {
    printf("average simulation speed : %ld clock/s\n", clock_cnt / time);
  }

  tfp->close();
  delete tfp;
  delete top;
  exit(0);
  return 0;
}