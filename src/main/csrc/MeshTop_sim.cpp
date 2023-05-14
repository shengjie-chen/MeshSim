#include "VMeshTop.h"
#include "common.h"

// TOP IO PORT
#define OUT_VALID_ADDRGAP ((uint64_t)&top->io_out_1_valid - (uint64_t)&top->io_out_0_valid)
#define OUT_DATA_ADDRGAP ((uint64_t)&top->io_out_1_bits_data0 - (uint64_t)&top->io_out_0_bits_data0)

#define TOP_IFM_BITS_ROW(a) *(((IData *)&top->io_ifm_bits_0) + a)
#define TOP_W_BITS_COL(a) *(((IData *)&top->io_w_bits_0) + a)

#define TOP_OUT_VALID_COL(a) *(CData *)((uint64_t)&top->io_out_0_valid + OUT_VALID_ADDRGAP * a)
#define TOP_OUT_DATA0_COL(a) *(IData *)((uint64_t)&top->io_out_0_bits_data0 + OUT_DATA_ADDRGAP * a)
#define TOP_OUT_DATA1_COL(a) *(IData *)((uint64_t)&top->io_out_0_bits_data1 + OUT_DATA_ADDRGAP * a)

vluint64_t main_time = 0; // 当前仿真时间

int sim_finish = 0;

// gold
int32_t out[ACCEL_ofm_block_num][MAT_SIZE][MAT_SIZE] = {0};
// dut
int32_t hw_out[ACCEL_ofm_block_num][MAT_SIZE][MAT_SIZE] = {0};

void gen_out() {
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
}
// ################ checker ################

void Outputprint() {
  for (int i = 0; i < ACCEL_ofm_x_block_num; i++) {
    for (int j = 0; j < ACCEL_ofm_y_block_num; j++) {
      cout << "************ HW RESULT ROW " << j << " COL " << i << " ************" << endl;
      MatPrint(hw_out[i * ACCEL_ofm_y_block_num + j]);
    }
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
void change_input() {
  static int output_index[MAT_SIZE] = {0}; // output mat row index
  static int ifm_start = 0;                // ifm hs first clk delay one clk data valid
  // static int w_start = 1;                  // w first valid w_data must change
  static int w_onecol_finish_switch = 0;

  // change ifm
  if (top->io_ifm_ready && top->io_ifm_valid) {
    if (ifm_start) {
      change_ifm();
    }
    ifm_start = 1;
  } 

  // change w
  if (top->io_w_ready && top->io_w_valid || w_onecol_finish_switch) {
    // change_w(w_onecol_finish_switch);
  change_w();
  // }

  for (int i = 0; i < MAT_SIZE; i++) {
    if (TOP_OUT_VALID_COL(i)) {
      hw_out[output_index[i] / MAT_SIZE * 2 + 0][output_index[i] % MAT_SIZE][i] =
          TOP_OUT_DATA0_COL(i);
      hw_out[output_index[i] / MAT_SIZE * 2 + 1][output_index[i] % MAT_SIZE][i] =
          TOP_OUT_DATA1_COL(i);

#ifdef DEBUG_MODE
      if (i == MAT_SIZE - 1)
        printf("output_index[%d]:%d\n", i, output_index[i]);
#endif
      if (output_index[i] != MAT_SIZE * ACCEL_ofm_block_num / 2) {
        output_index[i]++;
      }
    }
  }

  if (output_index[MAT_SIZE - 1] == MAT_SIZE * ACCEL_ofm_block_num / 2) {
    // Outputprint();
    check_out();
    sim_finish = 1;
  }

  update_reg();
}

void one_clock() {
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
  srand((unsigned)time(NULL));

  Verilated::commandArgs(argc, argv);
  Verilated::traceEverOn(true);
  top->trace(tfp, 99);
  tfp->open("./build/MeshTop/MeshTop.wave");

  clock_t start, end;
  start = clock();

  // input init
  InputInit();
  gen_out();
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
  top->eval();
  // change_ifm();
  // change_w();

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