#include "VMeshTop.h"
#include "svdpi.h"
#include "time.h"
#include "verilated.h"
#include "verilated_fst_c.h"
#include <cstdlib>
#include <iostream>
#include <stdint.h>
#include <sys/time.h>

using namespace std;

#define INPUT_MAX 30

// #define MAT_SIZE 32
// #define INPUT_NUM 20
// #define EACH_NUM 10
// #define OUTPUT_NUM INPUT_NUM / EACH_NUM * 2

#define MAT_SIZE 3
#define INPUT_NUM 4
#define EACH_NUM 2
#define OUTPUT_NUM INPUT_NUM / EACH_NUM * 2

// TOP IO PORT
#define TOP_IFM_BITS_ROW(a) *(((IData *)&top->io_ifm_bits_0) + a)
#define TOP_W_BITS_COL(a) *(((IData *)&top->io_w_bits_0) + a)
#define OUT_ADDR_D_GAP ((uint64_t)&top->io_out_bits_data0_1 - (uint64_t)&top->io_out_bits_data0_0)
#define TOP_OUT_DATA0_COL(a) *(IData *)((uint64_t)&top->io_out_bits_data0_0 + OUT_ADDR_D_GAP * a)
#define TOP_OUT_DATA1_COL(a) *(IData *)((uint64_t)&top->io_out_bits_data1_0 + OUT_ADDR_D_GAP * a)

vluint64_t main_time = 0;                                         // 当前仿真时间
const vluint64_t sim_time = 2 * (5 + INPUT_NUM) * MAT_SIZE + 100; // 最高仿真时间 可选：100

VMeshTop *top = new VMeshTop;
VerilatedFstC *tfp = new VerilatedFstC;

int sim_finish = 0;

// input
uint32_t ifm0[INPUT_NUM][MAT_SIZE][MAT_SIZE];
uint32_t ifm1[INPUT_NUM][MAT_SIZE][MAT_SIZE];
uint32_t w[INPUT_NUM][MAT_SIZE][MAT_SIZE];
// gold
uint32_t ofm0[INPUT_NUM][MAT_SIZE][MAT_SIZE];
uint32_t ofm1[INPUT_NUM][MAT_SIZE][MAT_SIZE];
uint32_t out[OUTPUT_NUM][MAT_SIZE][MAT_SIZE] = {0};

// dut
uint32_t hw_ofm0[INPUT_NUM][MAT_SIZE][MAT_SIZE] = {0};
uint32_t hw_ofm1[INPUT_NUM][MAT_SIZE][MAT_SIZE] = {0};
uint32_t hw_out[OUTPUT_NUM][MAT_SIZE][MAT_SIZE] = {0};

// print
// void MatPrint(uint32_t A[MAT_SIZE][MAT_SIZE]) {
//   for (int row = 0; row < MAT_SIZE; row++) {
//     for (int col = 0; col < MAT_SIZE; col++) {
//       cout << A[row][col] << "  ";
//     }
//     cout << endl;
//   }
// }

// ################ stimulator ################
int ifm_row_p = 0;
int w_row_p = MAT_SIZE - 1;
int input_index = 0;
int w_index = 0;
int output_index = 0;

void MatMul(uint32_t A[MAT_SIZE][MAT_SIZE], uint32_t B[MAT_SIZE][MAT_SIZE], uint32_t C[MAT_SIZE][MAT_SIZE]) {
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
      A[row][col] = rand() % INPUT_MAX;
    }
  }
}

void InputInit() {
  for (int i = 0; i < INPUT_NUM; i++) {
    cout << "************ GOLD RESULT " << i << " ************" << endl;
    MatInit(ifm0[i]);
    MatInit(ifm1[i]);
    MatInit(w[i]);
    MatMul(ifm0[i], w[i], ofm0[i]);
    MatMul(ifm1[i], w[i], ofm1[i]);

    // cout << "ifm0:" << endl;
    // MatPrint(ifm0[i]);
    // cout << "ifm1:" << endl;
    // MatPrint(ifm1[i]);
    // cout << "w:" << endl;
    // MatPrint(w[i]);
    // cout << "ofm0:" << endl;
    // MatPrint(ofm0[i]);
    // cout << "ofm1:" << endl;
    // MatPrint(ofm1[i]);
  }

  cout << "************ GOLD OUT ************" << endl;
  for (int i = 0; i < OUTPUT_NUM; i++) {
    for (int j = 0; j < MAT_SIZE; j++) {
      for (int k = 0; k < MAT_SIZE; k++) {
        if (i % 2 == 0) {
          for (int m = 0; m < EACH_NUM; m++) {
            out[i][j][k] = ofm0[i / 2 * EACH_NUM + m][j][k] + out[i][j][k];
          }
        } else {
          for (int m = 0; m < EACH_NUM; m++) {
            out[i][j][k] = ofm1[i / 2 * EACH_NUM + m][j][k] + out[i][j][k];
          }
        }
      }
    }
    cout << "out" << i << endl;
    // MatPrint(out[i]);
  }
}

void change_ifm() {
  if (input_index == INPUT_NUM) {
    for (int i = 0; i < MAT_SIZE; i++) {
      TOP_IFM_BITS_ROW(i) = 0;
    }
    top->io_ifm_valid = 0;
    top->io_last_in = 0;
  } else {
    for (int i = 0; i < MAT_SIZE; i++) {
      TOP_IFM_BITS_ROW(i) = ifm0[input_index][ifm_row_p][i] + (ifm1[input_index][ifm_row_p][i] << 16);
    }
    ifm_row_p++;
    if (input_index % EACH_NUM == (EACH_NUM - 1)) {
      top->io_last_in = 1;
    } else {
      top->io_last_in = 0;
    }
    if (ifm_row_p == MAT_SIZE) {
      ifm_row_p = 0;
      input_index++;
    }
  }
}

void change_w() {
  if (w_index == INPUT_NUM) {
    for (int i = 0; i < MAT_SIZE; i++) {
      TOP_W_BITS_COL(i) = 0;
    }
    top->io_w_valid = 0;
  } else {
    for (int i = 0; i < MAT_SIZE; i++) {
      TOP_W_BITS_COL(i) = w[w_index][w_row_p][i];
    }
    if (w_row_p == 0) {
      w_index++;
      w_row_p = MAT_SIZE - 1;
    } else {
      w_row_p--;
    }
  }
}

// ################ checker ################
void Outputprint() {
  for (int i = 0; i < OUTPUT_NUM; i++) {
    cout << "************ HW RESULT " << i << " ************" << endl;
    cout << "out" << i << endl;
    // MatPrint(hw_out[i]);
  }
}

void check_out() {
  printf("!!!!!!!!!!!!!!!!!!!!check Begin!!!!!!!!!!!!!\n");
  for (int i = 0; i < OUTPUT_NUM; i++) {
    for (int r = 0; r < MAT_SIZE; r++) {
      for (int c = 0; c < MAT_SIZE; c++) {
        if (out[i][r][c] != hw_out[i][r][c]) {
          printf("check error: \ngold out[%d]\n", i);
          // MatPrint(out[i]);
          printf("hw:\n");
          // MatPrint(hw_out[i]);
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
int w_hs_reg = 0;
void update_reg() {
  ifm_hs_reg = top->io_ifm_ready && top->io_ifm_valid;
  w_hs_reg = top->io_w_ready && top->io_w_valid;
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

  for (int i = 0; i < MAT_SIZE; i++) {
    if (top->io_out_valid) {
      hw_out[output_index / MAT_SIZE * 2][output_index % MAT_SIZE][i] = TOP_OUT_DATA0_COL(i);
      hw_out[output_index / MAT_SIZE * 2 + 1][output_index % MAT_SIZE][i] = TOP_OUT_DATA1_COL(i);
    }
  }

  // cout << output_index << endl;

  if (top->io_out_valid && output_index != MAT_SIZE * OUTPUT_NUM / 2) {
    output_index++;
    // cout << output_index << endl;
  }

  if (output_index == MAT_SIZE * OUTPUT_NUM / 2) {
    Outputprint();
    check_out();
    sim_finish = 1;
  }

  update_reg();
}

void one_clock() {
  change_input();

  top->clock = 0;
  top->eval();
  tfp->dump(main_time);
  main_time++;

  top->clock = 1;
  top->eval();
  tfp->dump(main_time);
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
    tfp->dump(main_time);
    main_time++;

    top->clock = 1;
    top->eval();
    tfp->dump(main_time);
    main_time++;
  }
  top->reset = 0;

  // prepare data for 5 clock
  n = 5;
  while (n-- > 0) {
    update_reg();

    top->clock = 0;
    top->eval();
    tfp->dump(main_time);
    main_time++;

    top->clock = 1;
    top->eval();
    tfp->dump(main_time);
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