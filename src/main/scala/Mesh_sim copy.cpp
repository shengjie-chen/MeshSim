// #include "VMesh.h"
// #include "svdpi.h"
// #include "time.h"
// #include "verilated.h"
// #include "verilated_vcd_c.h"
// #include <cstdlib>
// #include <iostream>
// #include <stdint.h>
// #include <sys/time.h>
// using namespace std;
// #define INPUT_MAX 127
// #define INPUT_NUM 3
// #define IFM_W 2
// #define IFM_H 2
// #define W_W 2
// #define W_H IFM_W

// vluint64_t main_time = 0;        // 当前仿真时间
// const vluint64_t sim_time = 200; // 最高仿真时间 可选：100

// VMesh *top = new VMesh;
// VerilatedVcdC *tfp = new VerilatedVcdC;

// typedef struct {
//   int width;
//   int height;
//   uint32_t *elements;
// } Matrix;

// void MatMul(Matrix A, Matrix B, Matrix C) {
//   assert(A.width == B.height);
//   for (int row = 0; row < C.height; row++) {
//     for (int col = 0; col < C.width; col++) {
//       uint32_t Cvalue = 0;
//       for (int e = 0; e < A.width; ++e) {
//         Cvalue += A.elements[row * A.width + e] // 所有点到点的元素乘积求和
//                   * B.elements[e * B.width + col];
//       }
//       C.elements[row * C.width + col] = Cvalue;
//     }
//   }
// }

// void MatInit(Matrix &A) {
//   for (int row = 0; row < A.height; row++) {
//     for (int col = 0; col < A.width; col++) {
//       A.elements[row * A.width + col] = rand() % INPUT_MAX;
//     }
//   }
// }

// void MatPrint(Matrix A) {
//   for (int row = 0; row < A.height; row++) {
//     for (int col = 0; col < A.width; col++) {
//       cout << A.elements[row * A.width + col] << "  ";
//     }
//     cout << endl;
//   }
// }

// static uint32_t a0[IFM_H * IFM_W], a1[IFM_H * IFM_W];
// static uint32_t b[W_H * W_W];
// static uint32_t c0[IFM_H * W_W], c1[IFM_H * W_W];
// static Matrix ifm0{IFM_W, IFM_H, a0} [INPUT_NUM];
// static Matrix ifm1{IFM_W, IFM_H, a1};
// static Matrix w{W_W, W_H, b};
// static Matrix ofm0{W_W, IFM_H, c0};
// static Matrix ofm1{W_W, IFM_H, c1};

// int ifm_row_p = 0;
// int w_addr = 0;

// void InputInit() {
//   cout << "************ GOLD RESULT ************" << endl;
//   MatInit(ifm0);
//   cout << "ifm0:" << endl;
//   MatPrint(ifm0);
//   MatInit(ifm1);
//   cout << "ifm1:" << endl;
//   MatPrint(ifm1);
//   MatInit(w);
//   cout << "w:" << endl;
//   MatPrint(w);
//   MatMul(ifm0, w, ofm0);
//   cout << "ofm0:" << endl;
//   MatPrint(ofm0);
//   MatMul(ifm1, w, ofm1);
//   cout << "ofm1:" << endl;
//   MatPrint(ofm1);
// }

// void change_ifm() {
//   top->io_ifm_bits_0 = ifm0.elements[]
// }

// void one_clock() {
//   top->clock = 0;
//   top->eval();
//   tfp->dump(main_time);
//   main_time++;

//   top->clock = 1;
//   top->eval();
//   tfp->dump(main_time);
//   main_time++;
// }

// int main(int argc, char **argv, char **env) {
//   srand((unsigned)time(NULL));

//   Verilated::commandArgs(argc, argv);
//   Verilated::traceEverOn(true);
//   top->trace(tfp, 99);
//   tfp->open("./build/Mesh/Mesh.vcd");

//   clock_t start, end;
//   start = clock();

//   InputInit();

//   int n = 10;
//   top->reset = 1;
//   while (n-- > 0) {
//     top->clock = 0;
//     top->eval();
//     tfp->dump(main_time);
//     main_time++;

//     top->clock = 1;
//     top->eval();
//     tfp->dump(main_time);
//     main_time++;
//   }
//   top->reset = 0;
//   // prepare data for 5 clock
//   n = 5;
//   while (n-- > 0) {
//     top->clock = 0;
//     top->eval();
//     tfp->dump(main_time);
//     main_time++;

//     top->clock = 1;
//     top->eval();
//     tfp->dump(main_time);
//     main_time++;
//   }
//   top->io_w_valid = 1;
//   top->io_ifm_valid = 1;

//   // parse_args
//   while (!Verilated::gotFinish() && main_time < sim_time) {
//     one_clock();
//   }

//   end = clock();
//   int time = double(end - start) / CLOCKS_PER_SEC;
//   vluint64_t clock_cnt = main_time / 2;
//   printf("************ SIM SUMMARY ************\n");
//   printf("sim clock num            : %ld\n", clock_cnt);
//   printf("simulation time          : %d min %d s\n", time / 60, time % 60);
//   printf("average simulation speed : %ld clock/s\n", clock_cnt / time);

//   tfp->close();
//   delete tfp;
//   delete top;
//   exit(0);
//   return 0;
// }