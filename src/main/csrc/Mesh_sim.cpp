#include "VMesh.h"
#include "common.h"

// TOP IO PORT
#define OFM_VALID_ADDRGAP ((uint64_t)&top->io_ofm_1_valid - (uint64_t)&top->io_ofm_0_valid)
#define OFM_ADDR_ADDRGAP ((uint64_t)&top->io_ofm_1_bits_addr - (uint64_t)&top->io_ofm_0_bits_addr)
#define OFM_DATA_ADDRGAP ((uint64_t)&top->io_ofm_1_bits_data0 - (uint64_t)&top->io_ofm_0_bits_data0)

#define TOP_OFM_VALID_COL(a) *(CData *)((uint64_t)&top->io_ofm_0_valid + OFM_VALID_ADDRGAP * a)
#define TOP_OFM_ADDR_COL(a) *(CData *)((uint64_t)&top->io_ofm_0_bits_addr + OFM_ADDR_ADDRGAP * a)
#define TOP_OFM_DATA0_COL(a) *(IData *)((uint64_t)&top->io_ofm_0_bits_data0 + OFM_DATA_ADDRGAP * a)
#define TOP_OFM_DATA1_COL(a) *(IData *)((uint64_t)&top->io_ofm_0_bits_data1 + OFM_DATA_ADDRGAP * a)

vluint64_t main_time = 0; // 当前仿真时间

int sim_finish = 0;

// dut
int32_t hw_ofm[ACCEL_ofm_x_block_num][ACCEL_ofm_y_block_num][ACCEL_ifm_x_block_num][MAT_SIZE]
              [MAT_SIZE] = {0};

// ################ checker ################
void Outputprint() {
  for (int i = 0; i < ACCEL_ofm_x_block_num; i++) {
    for (int j = 0; j < ACCEL_ofm_y_block_num; j++) {
      cout << "************ HW RESULT ROW " << j << " COL " << i << " ************" << endl;
      for (int k = 0; k < ACCEL_ifm_x_block_num; k++) {
        cout << "NO. " << k << "OFM BLOCK" << endl;
        MatPrint(hw_ofm[i][j][k]);
      }
    }
  }
}

void check_ofm() {
  printf("!!!!!!!!!!!!!!!!!!!!check Begin!!!!!!!!!!!!!\n");
  for (int i = 0; i < ACCEL_ofm_x_block_num; i++) {
    for (int j = 0; j < ACCEL_ofm_y_block_num; j++) {
      for (int k = 0; k < ACCEL_ifm_x_block_num; k++) {
        for (int r = 0; r < MAT_SIZE; r++) {
          for (int c = 0; c < MAT_SIZE; c++) {
            if (ofm[i][j][k][r][c] != hw_ofm[i][j][k][r][c]) {
              printf("check error: \ngold ofm col[%d] row[%d] NO.[%d]\n", i, j, k);
              MatPrint(ofm[i][j][k]);
              printf("hw:\n");
              MatPrint(hw_ofm[i][j][k]);
              printf("!!!!!!!!!!!!!!!!!!!!check Fail!!!!!!!!!!!!!\n");
              return;
            }
          }
        }
      }
    }
  }
  printf("!!!!!!!!!!!!!!!!!!!!check pass!!!!!!!!!!!!!\n");
}

// ################ SIM ################
void change_input() {
  static int output_index[MAT_SIZE] = {0}; // out block index (64x32)
  static int ofm_index[MAT_SIZE] = {0};    // ofm block index in one out block
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
  // else if (ifm_start) {
  //   top->io_ifm_valid = 0;
  //   top->io_last_in = 0;
  // }

  // change w
  if (top->io_w_ready && top->io_w_valid || w_onecol_finish_switch) {
#ifdef W_SWITCH_BLOCK
    change_w(w_onecol_finish_switch);
#else
    change_w();
#endif
  }

  for (int i = 0; i < MAT_SIZE; i++) {
    if (TOP_OFM_VALID_COL(i)) {
      // save
      if (output_index[i] != ACCEL_ofm_block_num / 2) {
        // if (i == 1 && ((output_index[i] / (ACCEL_ofm_y_block_num / 2)) == 0) &&
        //     (output_index[i] % (ACCEL_ofm_y_block_num / 2) * 2) == 2 && ofm_index[i] == 0) {
        //   printf("time : %d\n", main_time);
        //   printf("output_index[%d] = %d\n", i, output_index[i]);
        //   printf("ofm_index[%d] = %d\n", i, ofm_index[i]);
        //   printf("hw_ofm=%d\n", TOP_OFM_DATA0_COL(i));
        // }

        hw_ofm[output_index[i] / (ACCEL_ofm_y_block_num / 2)]
              [output_index[i] % (ACCEL_ofm_y_block_num / 2) * 2][ofm_index[i]][TOP_OFM_ADDR_COL(i)]
              [i] = TOP_OFM_DATA0_COL(i);
        hw_ofm[output_index[i] / (ACCEL_ofm_y_block_num / 2)]
              [output_index[i] % (ACCEL_ofm_y_block_num / 2) * 2 + 1][ofm_index[i]]
              [TOP_OFM_ADDR_COL(i)][i] = TOP_OFM_DATA1_COL(i);
      }
      // change printer
      if (TOP_OFM_ADDR_COL(i) == MAT_SIZE - 1) {
        if (ofm_index[i] == ACCEL_ifm_x_block_num - 1) {
          output_index[i]++;
          ofm_index[i] = 0;
        } else {
          ofm_index[i]++;
        }
      }
    }
  }

  if (output_index[MAT_SIZE - 1] == ACCEL_ofm_block_num / 2) {
#ifdef DEBUG_MODE
    Outputprint();
#endif
    check_ofm();
    sim_finish = 1;
  }
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
  // srand((unsigned)time(NULL));

  Verilated::commandArgs(argc, argv);
  Verilated::traceEverOn(true);
  top->trace(tfp, 99);
  tfp->open("./build/Mesh/Mesh.wave");

  clock_t start, end;
  start = clock();

  // input init
  InputInit();
  top->io_ofmbuf_stop = 0;
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
  // w_ifm_sametime();
  // ifm_before_2c();
  w_before_2c();

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