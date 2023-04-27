import chisel3._
import chisel3.util._

/* *************************************************************
 *  function: read 8bit data from DDR
 *            modify ncnn can keep the 8bit weight continuous
               all = kernel_w * kernel_h * ic * oc
               single = 32*32
 * *************************************************************/
class wgt_8bit_dma extends Module with dma_config with buffer_config {
  val io = IO(new Bundle() {
    //ctrl
    val start = Input(Bool())  //edge
    val start_one_task = Input(Bool()) //start one task
    val clr = Input(Bool())
    val oc_align = Input(UInt(12.W))
    val k2ic_align = Input(UInt(log2Ceil(wgt_buffer_size).W))
    val wgt_baseaddr = Input(UInt(dmaAddrWidth.W))
    //dma
    val dma_rareq = new dmaCtrl_io(dmaSizeWidth, dmaAddrWidth)
    val dma_rbusy = Input(Bool())
    val dma_rid = Input(UInt(log2Ceil(id.values.toList.max + 1).W))
    //buffer
    val task_one_done = Output(Bool())
    val task_all_done = Output(Bool())
  })

  val start_t0 = RegNext(io.start)
  val en = RegInit(false.B)
  en := Mux(io.start_one_task, true.B, Mux(io.task_one_done, false.B, en))

  val oc_align_div32 = io.oc_align(11, 5)

  val dma_en = RegInit(false.B)
  val dma_addr = RegInit(0.U(dmaAddrWidth.W))
  val dma_areq = RegInit(false.B)
  val dma_burst_size = Cat(io.k2ic_align,0.U(1.W))  //k2ic*32*8/128
  val dma_busy_down = fallEdge(io.dma_rbusy) && en && io.dma_rid === id("gemm").U
  val dma_areq_down = fallEdge(dma_areq)
  val start_one_task_keep = RegInit(false.B)
  start_one_task_keep := Mux(io.start_one_task, true.B, Mux(dma_areq, false.B, start_one_task_keep))
  dma_en := Mux(dma_busy_down, false.B, Mux(start_one_task_keep, true.B, dma_en))
  dma_addr := Mux(io.start, io.wgt_baseaddr, Mux(dma_areq_down, dma_addr + Cat(dma_burst_size,0.U(4.W)), dma_addr))
  dma_areq := Mux(dma_en && !io.dma_rbusy && io.dma_rid === id("gemm").U && start_one_task_keep, true.B, Mux(io.dma_rbusy && dma_areq, false.B, dma_areq))
  io.dma_rareq.dmaEn := dma_en
  io.dma_rareq.dmaSize := dma_burst_size
  io.dma_rareq.dmaAddr := dma_addr
  io.dma_rareq.dmaAreq := dma_areq

  val oc_align_div32_cnt = RegInit(0.U(7.W))
  oc_align_div32_cnt := Mux(start_t0, 0.U, Mux(dma_areq_down, oc_align_div32_cnt+1.U, oc_align_div32_cnt))
  val task_done = RegInit(false.B)
  task_done := Mux(io.clr|io.start, false.B, Mux(oc_align_div32_cnt === oc_align_div32 && dma_busy_down, true.B, task_done))

  io.task_one_done := dma_busy_down & en
  io.task_all_done := task_done
}

/* *************************************************************
 *  function: read 32bit data from DDR
 *            mat_w % 32 == 0 and mat_h % 32 == 0
               all = mat_w * mat_h
               single = 32*32
 * *************************************************************/
//class wgt_32bit_dma extends Module with dma_config with gemm_config {
//  val io = IO(new Bundle() {
//
//  })
//
//}

/* *************************************************************
 *  function: Read data from DDR
 *  if format is 8bit(IFM)
 *  if format is 32bit(MAT)
 * *************************************************************/
class weightBuffer extends Module with dma_config with buffer_config {
  val io = IO(new Bundle() {
    val wgt_en = Input(Bool())
    val ofm_whc = new whc
    val ic = Input(UInt(12.W))
    val kernel = Input(UInt(3.W))
    val wgt_baseaddr = Input(UInt(dmaAddrWidth.W))
    //dma
    val dma_rareq = new dmaCtrl_io(dmaSizeWidth, dmaAddrWidth)
    val dma_rdata = new dmaRData_io(dmaDataWidth)
    val dma_rbusy = Input(Bool())
    val dma_rid = Input(UInt(log2Ceil(id.values.toList.max + 1).W))
    //gemm inference
    val o_data = Decoupled(Vec(32,UInt(32.W)))
    val last = Output(Bool())
  })

  val wgt_buffer = TPRAM(wgt_buffer_width,2*wgt_buffer_size,"block")
  val wgt_8bit_dma_cell = Module(new wgt_8bit_dma).io

  val start_t0 = riseEdge(io.wgt_en)
  val start_t1 = RegNext(start_t0)
  val start_t2 = RegNext(start_t1)
  val en = RegInit(false.B)
  en := Mux(start_t1, true.B, Mux(wgt_8bit_dma_cell.task_all_done, false.B, en))
  val k2 = RegEnable(io.kernel * io.kernel, 0.U, start_t0)
  val k2ic = RegInit(0.U(log2Ceil(wgt_buffer_size).W))
  k2ic := Mux(start_t1, k2 * io.ic, k2ic)
  val k2ic_align = RegEnable(align(k2ic,32), 0.U, start_t2)
  val wgt_baseaddr = RegEnable(io.wgt_baseaddr, 0.U, start_t0)
  val owh = RegInit(0.U(24.W))
  owh := Mux(start_t0, io.ofm_whc.w * io.ofm_whc.h, owh)
  val owh_align = RegNext(align(owh, 64))
  val owh_align_div64 = owh_align(23,6)
  val oc_align = RegInit(0.U(12.W))
  oc_align := RegEnable(align(io.ofm_whc.c, 32), 0.U, start_t0)
  val oc_align_div32 = oc_align(11,5)

  val wgt_buffer0_full = RegInit(false.B)
  val wgt_buffer1_full = RegInit(false.B)
  val wgt_buffer_sel = RegInit(false.B)
  val wgt_one_dma_done = wgt_8bit_dma_cell.task_one_done
  val wgt_one_output_done = RegInit(false.B)
  val wgt_start_one_task = RegInit(false.B)
  wgt_buffer0_full := Mux(start_t0, false.B, Mux(!wgt_buffer_sel & wgt_one_dma_done, true.B, Mux(wgt_buffer_sel & wgt_one_output_done, false.B, wgt_buffer0_full)))
  wgt_buffer1_full := Mux(start_t0, false.B, Mux(wgt_buffer_sel & wgt_one_dma_done, true.B, Mux(!wgt_buffer_sel & wgt_one_output_done, false.B, wgt_buffer1_full)))
  wgt_buffer_sel := Mux(start_t0, false.B, Mux(riseEdge(wgt_buffer0_full ^ wgt_buffer1_full), !wgt_buffer_sel, wgt_buffer_sel))
  wgt_8bit_dma_cell.start_one_task := wgt_start_one_task
  wgt_start_one_task := (!wgt_buffer0_full || !wgt_buffer1_full) & !io.dma_rareq.dmaEn & en & !wgt_8bit_dma_cell.task_all_done

  wgt_8bit_dma_cell.start := start_t1
  wgt_8bit_dma_cell.clr := ~io.wgt_en
  wgt_8bit_dma_cell.oc_align := oc_align
  wgt_8bit_dma_cell.k2ic_align := k2ic_align
  wgt_8bit_dma_cell.wgt_baseaddr := wgt_baseaddr
  wgt_8bit_dma_cell.dma_rareq <> io.dma_rareq
  wgt_8bit_dma_cell.dma_rid <> io.dma_rid
  wgt_8bit_dma_cell.dma_rbusy <> io.dma_rbusy

  val wgt_buffer_waddr = RegInit(0.U(log2Ceil(wgt_buffer_size).W))
  val dma_valid_cnt = RegInit(false.B)
  dma_valid_cnt := Mux(wgt_start_one_task, false.B, Mux(io.dma_rdata.valid, ~dma_valid_cnt, dma_valid_cnt))
  wgt_buffer.clock := clock
  wgt_buffer.wen := io.dma_rdata.valid & dma_valid_cnt
  wgt_buffer.waddr := Cat(wgt_buffer_sel, wgt_buffer_waddr)
  wgt_buffer.wdata := Cat(io.dma_rdata.data, RegNext(io.dma_rdata.data))
  wgt_buffer_waddr := Mux(wgt_start_one_task, 0.U, Mux(wgt_buffer.wen, wgt_buffer_waddr+1.U, wgt_buffer_waddr))

  val wgt_buffer_raddr = RegInit(0.U(log2Ceil(wgt_buffer_size).W))
  val wgt_buffer_repeat_cnt = RegInit(0.U(18.W))
  val wgt_buffer_oc_cnt = RegInit(0.U(7.W))
  val wgt_buffer_raddr_equal = wgt_buffer_raddr === k2ic_align - 1.U
  val wgt_buffer_repeat_equal = wgt_buffer_repeat_cnt === owh_align_div64 - 1.U
  val wgt_buffer_oc_equal = wgt_buffer_oc_cnt === oc_align_div32 - 1.U
  wgt_buffer_raddr := Mux(wgt_buffer.ren, Mux(wgt_buffer_raddr_equal, 0.U, wgt_buffer_raddr + 1.U), wgt_buffer_raddr)
  wgt_buffer_repeat_cnt := Mux(wgt_buffer.ren & wgt_buffer_raddr_equal, Mux(wgt_buffer_repeat_equal, 0.U, wgt_buffer_repeat_cnt + 1.U), wgt_buffer_repeat_cnt)
  wgt_buffer_oc_cnt := Mux(wgt_buffer.ren & wgt_buffer_raddr_equal & wgt_buffer_repeat_equal, Mux(wgt_buffer_oc_equal, 0.U, wgt_buffer_oc_cnt+1.U), wgt_buffer_oc_cnt)
  io.last := wgt_buffer_raddr_equal & wgt_buffer_repeat_equal & wgt_buffer_oc_equal & io.o_data.valid

  val wgt_buffer0_r_valid = wgt_buffer_sel & wgt_buffer0_full
  val wgt_buffer1_r_valid = !wgt_buffer_sel & wgt_buffer1_full

  wgt_one_output_done := Mux(start_t0, false.B, wgt_buffer_repeat_equal & wgt_buffer_raddr === k2ic_align - 2.U)
  wgt_buffer.ren := io.wgt_en & (wgt_buffer0_r_valid | wgt_buffer1_r_valid) & io.o_data.ready
  wgt_buffer.raddr := Cat(~wgt_buffer_sel,wgt_buffer_raddr)
  io.o_data.valid := RegNext(wgt_buffer.ren)
  for (i <- 0 until 32) {
    io.o_data.bits(i) := Cat(0.U(24.W), wgt_buffer.rdata(8 * i + 7, 8 * i))
  }
}

