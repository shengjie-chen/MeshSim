//import chisel3._
//import chisel3.util._
//
//
///* *************************************************************
// *  function: read 8bit data from DDR
// *            modify ncnn can keep the 8bit weight continuous
//               all = kernel_w * kernel_h * ic * oc
//               single = 32*32
// * *************************************************************/
//class wgt_8bit_dma extends Module with dma_config with gemm_config {
//  val io = IO(new Bundle() {
//    val start = Input(Bool())
//    val en = Input(Bool())
//    val kernel = Input(UInt(3.W))
//    val ch0_whc = new whc
//    val ofm_whc = new whc
//    val dma_baseaddr = Input(UInt(dmaAddrWidth.W))
//    val fifo_ready = Input(Bool())
//    val dma_rareq = new dmaCtrl_io(dmaSizeWidth, dmaAddrWidth)
//    val dma_rbusy = Input(Bool())
//    val dma_rid = Input(UInt(log2Ceil(id.values.toList.max+1).W))
//    val task_done = Output(Bool())
//  })
//  val start = RegNext(io.start)
//  val start_t1 = RegNext(start)
//  val start_t2 = RegNext(start_t1)
//
//  val ofm_wh = RegInit(0.U(32.W))
//  ofm_wh := Mux(io.start, io.ofm_whc.w*io.ofm_whc.h, ofm_wh)
//  val wgt_load_num = RegEnable(align(ofm_wh,gemm_ch0_pipeline_width)(31,log2Ceil(gemm_ch0_pipeline_width)), 0.U, start)
//  val wgt_load_num_cnt = RegInit(0.U((32-log2Ceil(gemm_ch0_pipeline_width)).W))
//
//  val ic = RegEnable(align(io.ch0_whc.c, 64)(io.ch0_whc.c.getWidth - 1, 6), 0.U, io.start)
//  val oc = RegEnable(align(io.ofm_whc.c,32)(io.ofm_whc.c.getWidth - 1, 5), 0.U, io.start)
//  val kernel_pow2 = RegEnable(io.kernel * io.kernel, 0.U, io.start)
//  val total_num_t = RegEnable(ic * oc, 0.U, start)
//  val total_num = RegEnable(total_num_t * kernel_pow2, 0.U, start_t1)
//
//  val dma_baseaddr = RegEnable(io.dma_baseaddr, 0.U, io.start)
//  val dma_trans_cnt = RegInit(0.U(24.W))
//  val dma_done = fallEdge(io.dma_rbusy) && io.dma_rid === id("wgtBuf").U && io.en
//  val dma_flag = dma_trans_cnt === total_num - 1.U & dma_done
//  val dma_offset = RegInit(0.U(dmaAddrWidth.W))
//  dma_offset := Mux(io.start | dma_flag, 0.U, Mux(dma_done, dma_offset+2048.U, dma_offset))
//  dma_trans_cnt := Mux(start | dma_flag, 0.U, Mux(dma_done, dma_trans_cnt + 1.U, dma_trans_cnt))
//  wgt_load_num_cnt := Mux(dma_flag, wgt_load_num_cnt+1.U, wgt_load_num_cnt)
//  io.task_done := wgt_load_num_cnt === wgt_load_num && wgt_load_num =/= 0.U
//
//  io.dma_rareq.dmaEn := io.en
//  io.dma_rareq.dmaSize := 128.U
//  io.dma_rareq.dmaAddr := dma_baseaddr + dma_offset
//  val dma_rareq = RegInit(false.B)
//  dma_rareq := Mux(!io.dma_rbusy && io.dma_rid === id("wgtBuf").U && io.fifo_ready, true.B, Mux(io.start | (io.dma_rbusy&dma_rareq), false.B, dma_rareq))
//  io.dma_rareq.dmaAreq := dma_rareq
//}
//
///* *************************************************************
// *  function: read 32bit data from DDR
// *            mat_w % 32 == 0 and mat_h % 32 == 0
//               all = mat_w * mat_h
//               single = 32*32
// * *************************************************************/
//class wgt_32bit_dma extends Module with dma_config with gemm_config {
//  val io = IO(new Bundle() {
//    val start = Input(Bool())
//    val en = Input(Bool())
//    val ch0_c = Input(UInt(16.W))  //mat_a -> h
//    val ch1_w = Input(UInt(16.W)) //mat_b -> w
//    val ch1_c = Input(UInt(16.W))  //mat_b -> h
//    val ch1_cstep = Input(UInt(32.W))
//    val dma_baseaddr = Input(UInt(dmaAddrWidth.W))
//    val fifo_ready = Input(Bool())
//    val dma_rareq = new dmaCtrl_io(dmaSizeWidth, dmaAddrWidth)
//    val dma_rbusy = Input(Bool())
//    val dma_rid = Input(UInt(log2Ceil(id.values.toList.max+1).W))
//    val task_done = Output(Bool())
//  })
//
//  val start = RegNext(io.start)
//  val start_t1 = RegNext(start)
//  val start_t2 = RegNext(start_t1)
//
//  val mat_b_load_num = RegEnable(align(io.ch0_c,32), 0.U, io.start)
//  val mat_b_load_num_cnt = RegInit(0.U(16.W))
//
//  val ch1_w = RegEnable(align(io.ch1_w,32)(15,5), 0.U, io.start)
//  val ch1_w_cnt = RegInit(0.U(11.W))
//  val ch1_c = RegEnable(align(io.ch1_c,32), 0.U, io.start)
//  val ch1_c_cnt = RegInit(0.U(16.W))
//  val ch1_cstep = RegEnable(io.ch1_cstep, 0.U, io.start)
//
//  val dma_done = fallEdge(io.dma_rbusy) && io.dma_rid === id("wgtBuf").U && io.en
//  val dma_flag = dma_done && ch1_c === ch1_c_cnt - 1.U
//  ch1_c_cnt := Mux(start | dma_flag, 0.U, Mux(dma_done, ch1_c_cnt+1.U, ch1_c_cnt))
//  ch1_w_cnt := Mux(start | ch1_w_cnt === ch1_w , 0.U, Mux(dma_flag, ch1_w_cnt+1.U, ch1_w_cnt))
//
//  val dma_baseaddr_t = RegEnable(io.dma_baseaddr, 0.U, io.start)
//  val dma_baseaddr = RegInit(0.U(dmaAddrWidth.W))
//  dma_baseaddr := Mux((dma_done && ch1_w === ch1_w_cnt) | start, dma_baseaddr_t, Mux(dma_flag, dma_baseaddr+96.U, dma_baseaddr))
//
//  io.dma_rareq.dmaSize := 8.U
//  io.dma_rareq.dmaEn := io.en
//  val dmaAddr = RegInit(0.U(dmaAddrWidth.W))
//  dmaAddr := dma_baseaddr + ch1_cstep*ch1_c_cnt
//  io.dma_rareq.dmaAddr := dmaAddr
//  val dma_rareq = RegInit(false.B)
//  dma_rareq := Mux(!io.dma_rbusy && io.dma_rid === id("wgtBuf").U && io.fifo_ready, true.B, Mux(io.start | (io.dma_rbusy & dma_rareq), false.B, dma_rareq))
//  io.dma_rareq.dmaAreq := dma_rareq
//
//  mat_b_load_num_cnt := Mux(io.start, 0.U, Mux(ch1_w_cnt === ch1_w - 1.U && ch1_c_cnt === ch1_c, mat_b_load_num_cnt+1.U, mat_b_load_num_cnt))
//  io.task_done := mat_b_load_num_cnt === mat_b_load_num
//}
//
///* *************************************************************
// *  function: Read data from DDR
// *  if format is 8bit(IFM)      dmasize  =64*32
// *  if format is 32bit(MAT)   32*8
// * *************************************************************/
//class weightBuffer extends Module with dma_config with gemm_config {
//  val io = IO(new Bundle() {
//    //ctrl_signal
//    val start = Input(Bool())
//    val format = Input(UInt(2.W))
//    val kernel = Input(UInt(3.W))
//    val ch0_whc = new whc
//    val ch1_whc = new whc
//    val ch1_cstep = Input(UInt(32.W))
//    val ofm_whc = new whc
//    val src_addr = Input(UInt(dmaAddrWidth.W))
//    //dma
//    val dma_rdata = new dmaRData_io(dmaDataWidth)
//    val dma_rareq = new dmaCtrl_io(dmaSizeWidth, dmaAddrWidth)
//    val dma_rbusy = Input(Bool())
//    val dma_rid = Input(UInt(log2Ceil(id.values.toList.max+1).W))
//    //fifo_out
//    val o_fifo = Decoupled(UInt(1024.W))
//    val task_done = Output(Bool())
//  })
//
//  val start = RegNext(riseEdge(io.start))
//  val format = RegEnable(io.format, 0.U, io.start)
//  val en = RegInit(false.B)
//  en := Mux(start, true.B, Mux(io.task_done, false.B, en))
//
//  val fifo = Seq.fill(4)(Module(new Queue(UInt(256.W),128)))
//  val wgt_8bit_dma = Module(new wgt_8bit_dma)
////  val wgt_32bit_dma = Module(new wgt_32bit_dma)
//
//  val is_8bit = format === IFM_INT8 | format === IFM_FP32
////  val is_32bit = format === Mat32_INT32 | format === Mat32_FP32
//
//  //deq
//  io.o_fifo.valid := fifo.head.io.deq.valid
//  for(i <- 0 until 4){
//    fifo(i).io.deq.ready := io.o_fifo.ready
//  }
//  io.o_fifo.bits := (for(i <- 0 until 4) yield {fifo(i).io.deq.bits}).reverse.reduce(Cat(_,_))
//
//  //enq
//  val cnt_8bit = RegInit(0.U(2.W))
////  val cnt_32_bit = RegInit(0.U(3.W))
//  val dma_valid_t = RegNext(io.dma_rdata.valid)
//  cnt_8bit := Mux(is_8bit & dma_valid_t, cnt_8bit+1.U, cnt_8bit)
////  cnt_32_bit := Mux(is_32bit & dma_valid_t, cnt_32_bit+1.U, cnt_32_bit)
//  val fifo_ready = fifo.head.io.count <= 96.U && en
////  fifo(0).io.enq.valid := (is_8bit & cnt_8bit === 0.U & dma_valid_t) | (is_32bit & cnt_32_bit(2,1) === 0.U & cnt_32_bit(0) & dma_valid_t)
////  fifo(1).io.enq.valid := (is_8bit & cnt_8bit === 1.U & dma_valid_t) | (is_32bit & cnt_32_bit(2,1) === 1.U & cnt_32_bit(0) & dma_valid_t)
////  fifo(2).io.enq.valid := (is_8bit & cnt_8bit === 2.U & dma_valid_t) | (is_32bit & cnt_32_bit(2,1) === 2.U & cnt_32_bit(0) & dma_valid_t)
////  fifo(3).io.enq.valid := (is_8bit & cnt_8bit === 3.U & dma_valid_t) | (is_32bit & cnt_32_bit(2,1) === 3.U & cnt_32_bit(0) & dma_valid_t)
//  fifo(0).io.enq.valid := is_8bit & cnt_8bit === 0.U & dma_valid_t
//  fifo(1).io.enq.valid := is_8bit & cnt_8bit === 1.U & dma_valid_t
//  fifo(2).io.enq.valid := is_8bit & cnt_8bit === 2.U & dma_valid_t
//  fifo(3).io.enq.valid := is_8bit & cnt_8bit === 3.U & dma_valid_t
////  val data_32bit_t = RegNext(io.dma_rdata.data)
////  val data_32bit = Cat(io.dma_rdata.data, data_32bit_t)
//  val data_8bit = (for(i <- 0 until 16) yield {Cat(0.U(8.W), io.dma_rdata.data(8*i+7,8*i))}).reverse.reduce(Cat(_,_))
//  val fifo_data = RegInit(0.U(256.W))
////  fifo_data := Mux(io.dma_rdata.valid,Mux(is_32bit, data_32bit, data_8bit), 0.U)
//  fifo_data := Mux(io.dma_rdata.valid,data_8bit,0.U)
//  for(i <- 0 until 4){
//    fifo(i).io.enq.bits := fifo_data
//  }
//
//  wgt_8bit_dma.io.start := start && is_8bit
//  wgt_8bit_dma.io.en := en
//  wgt_8bit_dma.io.kernel := io.kernel
//  wgt_8bit_dma.io.ch0_whc := io.ch0_whc
//  wgt_8bit_dma.io.ofm_whc := io.ofm_whc
//  wgt_8bit_dma.io.dma_baseaddr := io.src_addr
//  wgt_8bit_dma.io.dma_rid <> io.dma_rid
//  wgt_8bit_dma.io.dma_rbusy <> io.dma_rbusy
//  wgt_8bit_dma.io.fifo_ready := fifo_ready
//
////  wgt_32bit_dma.io.start := start && is_32bit
////  wgt_32bit_dma.io.en := en
////  wgt_32bit_dma.io.ch0_c := io.ch0_whc.c
////  wgt_32bit_dma.io.ch1_w := io.ch1_whc.w
////  wgt_32bit_dma.io.ch1_c := io.ch1_whc.c
////  wgt_32bit_dma.io.ch1_cstep := io.ch1_cstep
////  wgt_32bit_dma.io.dma_baseaddr := io.src_addr
////  wgt_32bit_dma.io.dma_rid <> io.dma_rid
////  wgt_32bit_dma.io.dma_rbusy <> io.dma_rbusy
////  wgt_32bit_dma.io.fifo_ready := fifo_ready
//
//  when(is_8bit){
//    io.dma_rareq <> wgt_8bit_dma.io.dma_rareq
//  }.otherwise{
////    io.dma_rareq <> wgt_32bit_dma.io.dma_rareq
//    io.dma_rareq <> 0.U.asTypeOf(new dmaCtrl_io(dmaSizeWidth, dmaAddrWidth))
//  }
//
////  io.task_done := Mux(is_32bit, wgt_32bit_dma.io.task_done, wgt_8bit_dma.io.task_done)
//  io.task_done := Mux(is_8bit, wgt_8bit_dma.io.task_done, 0.U)
//}
//
