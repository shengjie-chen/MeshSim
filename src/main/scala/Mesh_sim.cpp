#include "VMesh.h"
#include "svdpi.h"
#include "time.h"
#include "verilated.h"
#include "verilated_vcd_c.h"
#include <cstdlib>
#include <iostream>
#include <stdint.h>
#include <sys/time.h>
using namespace std;
#define INPUT_MAX 30
#define INPUT_NUM 2
#define MAT_SIZE 2

vluint64_t main_time = 0;        // 当前仿真时间
const vluint64_t sim_time = 200; // 最高仿真时间 可选：100

VMesh *top = new VMesh;
VerilatedVcdC *tfp = new VerilatedVcdC;

int sim_finish = 0;

uint32_t ifm0[INPUT_NUM][MAT_SIZE][MAT_SIZE];
uint32_t ifm1[INPUT_NUM][MAT_SIZE][MAT_SIZE];
uint32_t w[INPUT_NUM][MAT_SIZE][MAT_SIZE];
uint32_t ofm0[INPUT_NUM][MAT_SIZE][MAT_SIZE];
uint32_t ofm1[INPUT_NUM][MAT_SIZE][MAT_SIZE];
uint32_t hw_ofm0[INPUT_NUM][MAT_SIZE][MAT_SIZE] = {0};
uint32_t hw_ofm1[INPUT_NUM][MAT_SIZE][MAT_SIZE] = {0};

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

void MatPrint(uint32_t A[MAT_SIZE][MAT_SIZE]) {
  for (int row = 0; row < MAT_SIZE; row++) {
    for (int col = 0; col < MAT_SIZE; col++) {
      cout << A[row][col] << "  ";
    }
    cout << endl;
  }
}

int ifm_row_p = 0;
int w_row_p = MAT_SIZE - 1;
int input_index = 0;
int w_index = 0;
int output_index_0 = 0;
int output_index_1 = 0;

void InputInit() {
  for (int i = 0; i < INPUT_NUM; i++) {
    cout << "************ GOLD RESULT " << i << " ************" << endl;
    MatInit(ifm0[i]);
    cout << "ifm0:" << endl;
    MatPrint(ifm0[i]);
    MatInit(ifm1[i]);
    cout << "ifm1:" << endl;
    MatPrint(ifm1[i]);
    MatInit(w[i]);
    cout << "w:" << endl;
    MatPrint(w[i]);
    MatMul(ifm0[i], w[i], ofm0[i]);
    cout << "ofm0:" << endl;
    MatPrint(ofm0[i]);
    MatMul(ifm1[i], w[i], ofm1[i]);
    cout << "ofm1:" << endl;
    MatPrint(ofm1[i]);
  }
  //  printf("#################\n");
}

void Outputprint() {
  for (int i = 0; i < INPUT_NUM; i++) {
    cout << "************ HW RESULT " << i << " ************" << endl;
    cout << "ofm0:" << endl;
    MatPrint(hw_ofm0[i]);
    MatMul(ifm1[i], w[i], ofm1[i]);
    cout << "ofm1:" << endl;
    MatPrint(hw_ofm1[i]);
  }
}

void change_ifm() {
  if (input_index == INPUT_NUM) {
    top->io_ifm_bits_0 = 0;
    top->io_ifm_bits_1 = 0;
    top->io_ifm_valid = 0;
  } else {
    top->io_ifm_bits_0 = ifm0[input_index][ifm_row_p][0] + (ifm1[input_index][ifm_row_p][0] << 16);
    top->io_ifm_bits_1 = ifm0[input_index][ifm_row_p][1] + (ifm1[input_index][ifm_row_p][1] << 16);
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
  if (w_index == INPUT_NUM) {
    top->io_w_bits_0 = 0;
    top->io_w_bits_1 = 0;
    top->io_w_valid = 0;
  } else {
    top->io_w_bits_0 = w[w_index][w_row_p][0];
    top->io_w_bits_1 = w[w_index][w_row_p][1];
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

void check_ofm() {
  printf("!!!!!!!!!!!!!!!!!!!!check Begin!!!!!!!!!!!!!\n");
  for (int i = 0; i < INPUT_NUM; i++) {
    for (int r = 0; r < MAT_SIZE; r++) {
      for (int c = 0; c < MAT_SIZE; c++) {
        if (ofm0[i][r][c] != hw_ofm0[i][r][c]) {
          printf("check error: \ngold ofm0[%d]\n", i);
          MatPrint(ofm0[i]);
          printf("hw:\n");
          MatPrint(hw_ofm0[i]);
          printf("!!!!!!!!!!!!!!!!!!!!check Fail!!!!!!!!!!!!!\n");

          return;
        }
        if (ofm1[i][r][c] != hw_ofm1[i][r][c]) {
          printf("check error: ofm1[%d]\n", i);
          MatPrint(ofm1[i]);
          printf("hw:\n");
          MatPrint(hw_ofm1[i]);
          printf("!!!!!!!!!!!!!!!!!!!!check Fail!!!!!!!!!!!!!\n");

          return;
        }
      }
    }
  }
  printf("!!!!!!!!!!!!!!!!!!!!check pass!!!!!!!!!!!!!\n");
}

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

  // output0 save
  if (top->io_ofm_0_valid) {
    if (output_index_0 != INPUT_NUM) {
      hw_ofm0[output_index_0][top->io_ofm_0_bits_addr][0] = top->io_ofm_0_bits_data0;
      hw_ofm1[output_index_0][top->io_ofm_0_bits_addr][0] = top->io_ofm_0_bits_data1;
      if (top->io_ofm_0_bits_addr == MAT_SIZE - 1) {
        output_index_0++;
      }
    }
  }

  // output1 save and check
  if (top->io_ofm_1_valid) {
    if (output_index_1 != INPUT_NUM) {
      hw_ofm0[output_index_1][top->io_ofm_1_bits_addr][1] = top->io_ofm_1_bits_data0;
      hw_ofm1[output_index_1][top->io_ofm_1_bits_addr][1] = top->io_ofm_1_bits_data1;
      if (top->io_ofm_1_bits_addr == MAT_SIZE - 1) {
        output_index_1++;
        if (output_index_1 == INPUT_NUM) {
          check_ofm();
          sim_finish = 1;
        }
      }
    }
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
  tfp->open("./build/Mesh/Mesh.wave");

  clock_t start, end;
  start = clock();

  // input init
  InputInit();

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

  Outputprint();
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