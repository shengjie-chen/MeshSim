import chisel3._
import chisel3.util._

class ofmBuffer_cell(ofm_block_offset:Int) extends Module with buffer_config with dma_config {
  val io = IO(new Bundle(){
    //ctrl
    val start = Input(Bool())
    val en = Input(Bool())
    val ofm_c_align = Input(UInt(12.W))
    val ofm_whc = new whc
    val ofm_wh = Input(UInt(24.W))
    val ofm_cstep = Input(UInt(32.W))
    val ofm_dma_addr = Input(UInt(dmaAddrWidth.W))
    val data_in = Flipped(Vec(32, Valid(UInt(32.W))))
    val task_done = Output(Bool())
    val gemm_stop = Output(Bool())
    //dma
    val dma_wid = Input(UInt(log2Ceil(id.values.toList.max + 1).W))
    val dma_wbusy = Input(Bool())
    val dma_wareq = new dmaCtrl_io(dmaSizeWidth, dmaAddrWidth)
    val dma_wdata = new dmaWData_io(dmaDataWidth)
  })

  //ofm dara transpose
  val regs = RegInit(VecInit(Seq.fill(32)(0.U(1024.W))))
  for(i <- 0 until 32){
    regs(i) := Mux(io.data_in(i).valid, Cat(io.data_in(i).bits,regs(i)(1023,32)), regs(i))
  }
  val r_valid = ShiftRegister(io.data_in(0).valid,32)
  val r_sel = RegInit(0.U(5.W))
  r_sel := Mux(r_valid, r_sel+1.U, r_sel)

  //generate dma ofm address
  val fifo_in_ofm_wh_cnt = RegInit(0.U(24.W))
  val fifo_in_ofm_c_low_cnt = RegInit(0.U(5.W))
  val fifo_in_ofm_c_high_cnt = RegInit(0.U(7.W))
  val fifo_in_ofm_c_low_cnt_equal = fifo_in_ofm_c_low_cnt === 31.U
  //val fifo_in_ofm_wh_cnt_equal = fifo_in_ofm_wh_cnt + 64.U  >= io.ofm_wh - 1.U
  val fifo_in_ofm_wh_cnt_equal = fifo_in_ofm_wh_cnt + 65.U  >= io.ofm_wh && io.ofm_wh =/= 0.U
  fifo_in_ofm_wh_cnt := Mux(io.start, 0.U, Mux(r_valid & fifo_in_ofm_c_low_cnt_equal, Mux(fifo_in_ofm_wh_cnt_equal, 0.U, fifo_in_ofm_wh_cnt+64.U), fifo_in_ofm_wh_cnt))
  fifo_in_ofm_c_low_cnt := Mux(io.start, 0.U, Mux(r_valid, fifo_in_ofm_c_low_cnt+1.U, fifo_in_ofm_c_low_cnt))
  fifo_in_ofm_c_high_cnt :=Mux(io.start, 0.U, Mux(r_valid & fifo_in_ofm_c_low_cnt_equal & fifo_in_ofm_wh_cnt_equal, fifo_in_ofm_c_high_cnt+1.U, fifo_in_ofm_c_high_cnt))
  val fifo_in_ofm_c_cnt = Cat(fifo_in_ofm_c_high_cnt, fifo_in_ofm_c_low_cnt)
  val dma_addr_offset_t = RegInit(0.U((dmaAddrWidth-2).W))
  dma_addr_offset_t := fifo_in_ofm_c_cnt * io.ofm_cstep + fifo_in_ofm_wh_cnt
  val dma_addr_offset = Cat(dma_addr_offset_t, 0.U(2.W))
  val ofm_addr = RegInit(0.U(dmaAddrWidth.W))
  val dma_addr_base = RegEnable(io.ofm_dma_addr+Cat(ofm_block_offset.U(6.W),0.U(2.W)),0.U,io.start)
  ofm_addr := dma_addr_offset + dma_addr_base
  val oc_valid = fifo_in_ofm_c_cnt <= io.ofm_whc.c - 1.U
  val owh_valid = (fifo_in_ofm_wh_cnt + ofm_block_offset.U) < io.ofm_wh

  //write to data fifo
  val data_fifo = Module(new Queue(UInt(1024.W), entries = ofm_buffer_size))
  data_fifo.io.enq.valid := r_valid & oc_valid & owh_valid
  data_fifo.io.enq.bits := regs(r_sel)

  //write to data fifo
  val addr_fifo = Module(new Queue(UInt(dmaAddrWidth.W), entries = ofm_buffer_size))
  addr_fifo.io.enq.valid := ShiftRegister(data_fifo.io.enq.valid, 2)
  addr_fifo.io.enq.bits := ofm_addr

  //read fifo
  val dma_wbusy_fall_edge = fallEdge(io.dma_wbusy)
  val dma_areq_fall_edge = fallEdge(io.dma_wareq.dmaAreq)
  data_fifo.io.deq.ready := dma_wbusy_fall_edge
  addr_fifo.io.deq.ready := dma_wbusy_fall_edge
  val data_fifo_o_r = RegInit(0.U(1024.W))
  data_fifo_o_r := Mux(data_fifo.io.deq.ready & data_fifo.io.deq.valid, data_fifo.io.deq.bits, data_fifo_o_r)
  val fifo_not_empty = data_fifo.io.count > 0.U && addr_fifo.io.count > 0.U
  val data_fifo_top_limit = data_fifo.io.count > (ofm_buffer_size-65).U
  val data_fifo_down_limit = data_fifo.io.count < 33.U
  //test
  val data_fifo_count_max = RegInit(0.U(log2Ceil(ofm_buffer_size).W))
  data_fifo_count_max := Mux(io.start, 0.U, Mux(data_fifo_count_max < data_fifo.io.count, data_fifo.io.count, data_fifo_count_max))
  dontTouch(data_fifo_count_max)

  val gemm_stop = RegInit(false.B)
  gemm_stop := Mux(!io.en, false.B, Mux(data_fifo_top_limit, true.B, Mux(data_fifo_down_limit, false.B, gemm_stop)))
  io.gemm_stop := gemm_stop

  val ofm_wh_cnt = RegInit(0.U(24.W))
  val ofm_c_low_cnt = RegInit(0.U(5.W))
  val ofm_c_high_cnt = RegInit(0.U(7.W))
  val ofm_c_low_equal = ofm_c_low_cnt === 31.U
  val ofm_c_cnt = Cat(ofm_c_high_cnt,ofm_c_low_cnt)
  val ofm_c_equal = ofm_c_low_equal | ofm_c_cnt === io.ofm_whc.c - 1.U
  val ofm_wh_cnt_equal = ofm_wh_cnt + 65.U  >= io.ofm_wh && io.ofm_wh =/= 0.U
  ofm_c_low_cnt:= Mux(dma_areq_fall_edge, Mux(ofm_c_equal, 0.U, ofm_c_low_cnt+1.U), ofm_c_low_cnt)
  ofm_wh_cnt := Mux(io.start, ofm_block_offset.U, Mux(ofm_c_equal & dma_areq_fall_edge, Mux(ofm_wh_cnt_equal, ofm_block_offset.U, ofm_wh_cnt+64.U), ofm_wh_cnt))
  ofm_c_high_cnt := Mux(io.start, 0.U, Mux(ofm_c_low_equal & dma_areq_fall_edge & ofm_wh_cnt_equal, ofm_c_high_cnt+1.U, ofm_c_high_cnt))
  val ofm_c_cnt_equal = ofm_c_cnt === io.ofm_whc.c - 1.U
  val ofm_wh_rest = io.ofm_wh - ofm_wh_cnt

  val load_finish = RegInit(false.B)
  load_finish := Mux(io.task_done, false.B, Mux(ofm_c_cnt_equal && ofm_wh_cnt_equal & dma_areq_fall_edge, true.B, load_finish))
  val task_done = RegInit(false.B)
  task_done := Mux(!io.en, false.B, Mux(load_finish && dma_wbusy_fall_edge, true.B, task_done))
  io.task_done := task_done

  val dma_en = RegInit(false.B)
  val dma_areq = RegInit(false.B)
  val dma_size = RegInit(0.U(32.W))
  dma_en := Mux(io.start, true.B, Mux(task_done, false.B, dma_en))
  dma_areq := Mux(dma_en & !io.dma_wbusy & io.dma_wid === id("gemm").U & !dma_areq & fifo_not_empty & !load_finish, true.B, Mux(dma_areq & io.dma_wbusy, false.B, dma_areq))
  dma_size := Mux(ofm_wh_rest > 31.U, 8.U, align(ofm_wh_rest,5,4)(4,2))
  io.dma_wareq.dmaSize := dma_size
  io.dma_wareq.dmaAddr := addr_fifo.io.deq.bits
  io.dma_wareq.dmaEn := dma_en
  io.dma_wareq.dmaAreq := dma_areq

  val dma_sel = RegInit(0.U(3.W))
  dma_sel := Mux(io.dma_wdata.valid, dma_sel+1.U, 0.U)
  val dma_data = Wire(UInt(128.W))
  dma_data := 0.U
  for(i <- 0 until 8){
    when(dma_sel === i.U){
      dma_data := data_fifo.io.deq.bits(128*i+127,128*i)
    }
  }
  io.dma_wdata.data := dma_data
}

class ofmbuffer extends Module with buffer_config with dma_config {
  val io = IO(new Bundle() {
    //ctrl
    val en = Input(Bool())
    val ofm_whc = new whc
    val ofm_cstep = Input(UInt(32.W))
    val ofm_dma_addr = Input(UInt(dmaAddrWidth.W))
    val data_in = Flipped(Vec(64, Valid(UInt(32.W))))
    //dma
    val dma_ch0_wid = Input(UInt(log2Ceil(id.values.toList.max + 1).W))
    val dma_ch0_wbusy = Input(Bool())
    val dma_ch0_wareq = new dmaCtrl_io(dmaSizeWidth, dmaAddrWidth)
    val dma_ch0_wdata = new dmaWData_io(dmaDataWidth)
    val dma_ch1_wid = Input(UInt(log2Ceil(id.values.toList.max + 1).W))
    val dma_ch1_wbusy = Input(Bool())
    val dma_ch1_wareq = new dmaCtrl_io(dmaSizeWidth, dmaAddrWidth)
    val dma_ch1_wdata = new dmaWData_io(dmaDataWidth)
    //intr
    val task_done = Output(Bool())
    val gemm_stop = Output(Bool())
  })
  val ofmbuf_cell0 = Module(new ofmBuffer_cell(0))
  val ofmbuf_cell1 = Module(new ofmBuffer_cell(32))

  val start = riseEdge(io.en)
  val start_t1 = RegNext(start)
  val ofm_wh = RegEnable(io.ofm_whc.w*io.ofm_whc.h, 0.U, start)
  val ofm_c_align = RegInit(0.U(12.W))
  ofm_c_align := align(io.ofm_whc.c,32)

  ofmbuf_cell0.io.en := io.en
  ofmbuf_cell0.io.start := start_t1
  ofmbuf_cell0.io.ofm_c_align := ofm_c_align
  ofmbuf_cell0.io.ofm_whc := io.ofm_whc
  ofmbuf_cell0.io.ofm_wh := ofm_wh
  ofmbuf_cell0.io.ofm_cstep := io.ofm_cstep
  ofmbuf_cell0.io.ofm_dma_addr := io.ofm_dma_addr
  for(i <- 0 until 32){
    ofmbuf_cell0.io.data_in(i) := io.data_in(i)
  }
  ofmbuf_cell0.io.dma_wid <> io.dma_ch0_wid
  ofmbuf_cell0.io.dma_wbusy <> io.dma_ch0_wbusy
  ofmbuf_cell0.io.dma_wareq <> io.dma_ch0_wareq
  ofmbuf_cell0.io.dma_wdata <> io.dma_ch0_wdata

  ofmbuf_cell1.io.en := io.en
  ofmbuf_cell1.io.start := start_t1
  ofmbuf_cell1.io.ofm_c_align := ofm_c_align
  ofmbuf_cell1.io.ofm_whc := io.ofm_whc
  ofmbuf_cell1.io.ofm_wh := ofm_wh
  ofmbuf_cell1.io.ofm_cstep := io.ofm_cstep
  ofmbuf_cell1.io.ofm_dma_addr := io.ofm_dma_addr
  for (i <- 32 until 64) {
    ofmbuf_cell1.io.data_in(i-32) := io.data_in(i)
  }
  ofmbuf_cell1.io.dma_wid <> io.dma_ch1_wid
  ofmbuf_cell1.io.dma_wbusy <> io.dma_ch1_wbusy
  ofmbuf_cell1.io.dma_wareq <> io.dma_ch1_wareq
  ofmbuf_cell1.io.dma_wdata <> io.dma_ch1_wdata

  io.gemm_stop := ofmbuf_cell0.io.gemm_stop | ofmbuf_cell1.io.gemm_stop

  val task_done = RegInit(false.B)
  task_done := Mux(!io.en | start, false.B, Mux(ofmbuf_cell0.io.task_done & ofmbuf_cell1.io.task_done, true.B, task_done))
  io.task_done := task_done
}