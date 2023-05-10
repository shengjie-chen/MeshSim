import chisel3._
import chisel3.util._


class opfusion_data_cell(implicit arithmetic: Arithmetic[Float]) extends  Module with activation_config with gemm_config with cal_cell_params{
  import arithmetic._
  val io  = IO(new Bundle{
    val i_mode = Input(UInt(2.W))
    val i_data = Input(SInt(32.W))
    val i_oscale = Input(Float(exp_width = 8, sig_width = 24))
    val i_bias = Input(Float(exp_width = 8, sig_width = 24))
    val i_bias_en = Input(Bool())
    val i_act_mode = Input(UInt(3.W))
    val i_leakyrelu_param = Input(Float(exp_width = 8, sig_width = 24))
    val i_rescale = Input(Float(exp_width = 8, sig_width = 24))
    val i_rescale_en = Input(Bool())
    val o_ifm_int8 = Output(UInt(8.W))
    val o_ifm_fp32 = Output(UInt(32.W))
    val o_mat32 = Output(UInt(32.W))
  })

  val convert0_data = Float.SIntToFloat(io.i_data,32)
  val oscale_data = convert0_data * io.i_oscale
  val bias_data = Mux(io.i_bias_en, io.i_bias + oscale_data, oscale_data)
  val bias_data_t = ShiftRegister(bias_data,fp32_mul_cycles)
  val act_relu_data = Mux(bias_data.bits(bias_data.exp_width+bias_data.sig_width-1),Float(0.U(32.W)),bias_data)
  val act_leakyrelu_data = Mux(bias_data_t.bits(bias_data.exp_width+bias_data.sig_width-1), bias_data*io.i_leakyrelu_param, bias_data_t)
  val act_data = Wire(Float(exp_width = 8, sig_width = 24))
  when(io.i_act_mode === ReLU){
    act_data := act_relu_data
  }.elsewhen(io.i_act_mode === LeakyReLU){
    act_data := act_leakyrelu_data
  }.otherwise{
    act_data := bias_data
  }
  val rescale_data = Mux(io.i_rescale_en, act_data*io.i_rescale, Float(0.U(32.W)))
  val convert1_data = Float.FloatToSInt(rescale_data,8)

  io.o_ifm_int8 := Mux(io.i_mode === IFM_INT8, convert1_data.asUInt, 0.U)
  io.o_ifm_fp32 := Mux(io.i_mode === IFM_FP32, act_data.bits, 0.U)
  io.o_mat32 := Mux(io.i_mode === Mat32_FP32 | io.i_mode === Mat32_INT32, io.i_data.asUInt, 0.U)
}


class opfusion_valid_cell extends Module with cal_cell_params with activation_config with gemm_config {
  val io = IO(new Bundle() {
    val i_valid = Input(Bool())
    val i_mode = Input(UInt(2.W))
    val i_bias_en = Input(Bool())
    val i_act_mode = Input(UInt(3.W))
    val i_rescale_en = Input(Bool())
    val o_valid = Output(Bool())
  })

  val o_oscale_valid = ShiftRegister(io.i_valid, sint_to_fp32_cycles+fp32_mul_cycles)
  val o_bias_valid = Mux(io.i_bias_en, ShiftRegister(o_oscale_valid, fp32_add_cycles), o_oscale_valid)
  val o_act_valid = Mux(io.i_act_mode === LeakyReLU, ShiftRegister(o_bias_valid, leakyrelu_cycles),o_bias_valid)
  val o_rescale_valid = Mux(io.i_rescale_en, ShiftRegister(o_act_valid,fp32_mul_cycles+fp32_to_sint_cycles),o_act_valid)

  io.o_valid := MuxCase(false.B, Array(
    (io.i_mode === Mat32_INT32) -> io.i_valid,
    (io.i_mode === Mat32_FP32) -> io.i_valid,
    (io.i_mode === IFM_FP32) -> o_act_valid,
    (io.i_mode === IFM_INT8) -> o_rescale_valid
  ))
}

class opfusion extends Module with dma_config with gemm_config with buffer_config with cal_cell_params {
  val io = IO(new Bundle() {
    //data
    val i_data = Flipped(Vec(64, Valid(UInt(32.W))))
    val o_data = Vec(64, Valid(UInt(32.W)))
    //parameters
    val en = Input(Bool())
    val i_mode = Input(UInt(2.W))
    val i_oscale = Input(UInt(dmaAddrWidth.W))
    val i_bias = Input(UInt(dmaAddrWidth.W))
    val i_bias_en = Input(Bool())
    val i_act_mode = Input(UInt(3.W))
    val i_leakyrelu_param = Input(UInt(32.W))
    val i_rescale = Input(UInt(32.W))
    val i_rescale_en = Input(Bool())
    val ofm_whc = new whc
    //dma
    val dma_rdata = new dmaRData_io(dmaDataWidth)
    val dma_rareq = new dmaCtrl_io(dmaSizeWidth, dmaAddrWidth)
    val dma_rbusy = Input(Bool())
    val dma_rid = Input(UInt(log2Ceil(id.values.toList.max + 1).W))
    //ctrl
    val o_ready = Output(Bool())
  })

  //module ctrl
  val start = riseEdge(io.en)
  val start_t = RegNext(start)
  val end = fallEdge(io.en)
  val is_ifm = io.i_mode === IFM_FP32 | io.i_mode === IFM_INT8
  val load_en = RegInit(false.B)
  load_en := Mux(start, true.B, Mux(io.o_ready, false.B, load_en))
  val c_align = RegEnable(align(io.ofm_whc.c,12,32), 0.U, start)

  //ready bias / oscale mem
  val dma_rbusy_down = fallEdge(io.dma_rbusy) & io.dma_rid === id("opfusion").U
  val bias_mem = SPRAM(128,bias_buffer_size,"block") //128/32 = 4; 4*512=2048
  val bias_load_en = RegInit(false.B)
  val bias_load_finish = RegInit(false.B)
//  bias_load_finish := Mux(!io.en, !io.i_bias_en, Mux(io.i_bias_en & bias_load_en & dma_rbusy_down, true.B, bias_load_finish))
  bias_load_finish := Mux(start_t | end, !io.i_bias_en, Mux(io.i_bias_en & bias_load_en & dma_rbusy_down, true.B, bias_load_finish))
  bias_load_en := io.i_bias_en & load_en & !bias_load_finish & is_ifm
  val oscale_mem = SPRAM(128,oscale_buffer_size,"block") //128/32 = 4; 4*512=2048
  val oscale_load_en = RegInit(false.B)
  val oscale_load_finish = RegInit(false.B)
//  oscale_load_finish := Mux(!io.en, false.B, Mux(oscale_load_en & dma_rbusy_down, true.B, oscale_load_finish))
  oscale_load_finish := Mux(start | end, false.B, Mux(oscale_load_en & dma_rbusy_down, true.B, oscale_load_finish))
  oscale_load_en := is_ifm & bias_load_finish & load_en

  io.dma_rareq.dmaEn := bias_load_en | oscale_load_en
  io.dma_rareq.dmaAddr := Mux(bias_load_en.asBool, io.i_bias, io.i_oscale)
  io.dma_rareq.dmaSize := c_align(11, 2)
  io.o_ready := Mux(is_ifm, oscale_load_finish, bias_load_finish)
  val dma_rareq_bias = RegInit(false.B)
  val dma_rareq_oscale = RegInit(false.B)
  io.dma_rareq.dmaAreq := Mux(bias_load_en, dma_rareq_bias, dma_rareq_oscale)

  //bias mem write
  val bias_mem_waddr = RegInit(0.U(log2Ceil(bias_buffer_size).W))
  val bias_mem_raddr = RegInit(0.U(log2Ceil(bias_buffer_size).W))
  val bias_mem_rsel = RegInit(0.U(2.W))
  dma_rareq_bias := Mux(bias_load_en & io.dma_rid === id("opfusion").U & !io.dma_rbusy & !dma_rbusy_down & !bias_load_finish, true.B, Mux(dma_rareq_bias & io.dma_rbusy, false.B, dma_rareq_bias))
  bias_mem.clock := clock
  bias_mem.en := io.en
  bias_mem.wr := ~(bias_load_en & io.dma_rdata.valid)
  bias_mem.wdata := io.dma_rdata.data
  bias_mem.addr := Mux(!bias_mem.wr,bias_mem_waddr,bias_mem_raddr)
  bias_mem_waddr := Mux(start, 0.U, Mux(!bias_mem.wr, bias_mem_waddr+1.U, bias_mem_waddr))

  //oscale mem write
  val oscale_mem_waddr = RegInit(0.U(log2Ceil(oscale_buffer_size).W))
  val oscale_mem_raddr = RegInit(0.U(log2Ceil(oscale_buffer_size).W))
  val oscale_mem_rsel = RegInit(0.U(2.W))
  dma_rareq_oscale := Mux(oscale_load_en & io.dma_rid === id("opfusion").U & !io.dma_rbusy & !dma_rbusy_down & !oscale_load_finish & bias_load_finish, true.B, Mux(dma_rareq_oscale & io.dma_rbusy, false.B, dma_rareq_oscale))
  oscale_mem.clock := clock
  oscale_mem.en := io.en
  oscale_mem.wr := ~(oscale_load_en & io.dma_rdata.valid)
  oscale_mem.wdata := io.dma_rdata.data
  oscale_mem.addr := Mux(!oscale_mem.wr, oscale_mem_waddr, oscale_mem_raddr)
  oscale_mem_waddr := Mux(start, 0.U, Mux(!oscale_mem.wr, oscale_mem_waddr+1.U, oscale_mem_waddr))

  //bias and oscale mem read
  val o_ready_rise_edge = riseEdge(io.o_ready)
  val owh_align64 = RegInit(0.U(24.W))
  owh_align64 := Mux(start, align(io.ofm_whc.w*io.ofm_whc.h,24,64), owh_align64)
  val owh_align64_div2 = owh_align64(23,1)
  val cnt_valid = io.i_data(0).valid  //oscale
  val owh_cnt = RegInit(0.U(24.W))
  val owh_cnt_equal = owh_cnt === owh_align64_div2 - 1.U
  owh_cnt := Mux(start, 0.U, Mux(cnt_valid, Mux(owh_cnt_equal, 0.U, owh_cnt+1.U), owh_cnt))
  val oc_cnt = RegInit(0.U(7.W))
  val oc_align32 = RegEnable(align(io.ofm_whc.c,12,32), 0.U, start)
  val oc_align32_equal = oc_cnt === oc_align32(11,5) - 1.U
  oc_cnt := Mux(start, 0.U, Mux(cnt_valid && owh_cnt_equal, Mux(oc_align32_equal, 0.U, oc_cnt+1.U), oc_cnt))

  val oscale_mem_read_start = owh_cnt_equal & cnt_valid & !oc_align32_equal
  val oscale_mem_read_valid = RegInit(false.B)
  val oscale_mem_read_cnt = RegInit(0.U(5.W))
  oscale_mem_read_valid := Mux(oscale_mem_read_start | o_ready_rise_edge, true.B, Mux(oscale_mem_read_cnt === 31.U, false.B, oscale_mem_read_valid))
  oscale_mem_read_cnt := Mux(oscale_mem_read_valid, oscale_mem_read_cnt+1.U, 0.U)
  val bias_mem_read_valid = ShiftRegister(oscale_mem_read_valid, fp32_mul_cycles)
  oscale_mem_rsel := Mux(start, 0.U, Mux(oscale_mem_read_valid, oscale_mem_rsel+1.U, oscale_mem_rsel))
  bias_mem_rsel := Mux(start, 0.U, Mux(bias_mem_read_valid, bias_mem_rsel+1.U, bias_mem_rsel))

  oscale_mem_raddr := Mux(start, 0.U, Mux(oscale_mem_read_valid && oscale_mem_rsel === 2.U, oscale_mem_raddr+1.U, oscale_mem_raddr))
  bias_mem_raddr := Mux(start, 0.U, Mux(bias_mem_read_valid && bias_mem_rsel === 2.U, bias_mem_raddr+1.U, bias_mem_raddr))

  val oscale_regs = RegInit(VecInit(Seq.fill(32)(0.U(32.W))))
  val bias_regs = RegInit(VecInit(Seq.fill(32)(0.U(32.W))))
  val oscale_mem_rdata = Wire(UInt(32.W))
  oscale_mem_rdata := 0.U
  for (i <- 0 until 4) {
    when(oscale_mem_rsel === i.U) {
      oscale_mem_rdata := oscale_mem.rdata(32*i+31,32*i)
    }
  }
  val bias_mem_rdata = Wire(UInt(32.W))
  bias_mem_rdata := 0.U
  for(i<-0 until 4){
    when(bias_mem_rsel === i.U){
      bias_mem_rdata := bias_mem.rdata(32*i+31,32*i)
    }
  }

  val oscale_regs_index = Cat(RegNext(oscale_mem_raddr(2,0)), oscale_mem_rsel)
  val bias_regs_index = Cat(RegNext(bias_mem_raddr(2,0)), bias_mem_rsel)
  oscale_regs(oscale_regs_index) := Mux(oscale_mem_read_valid, oscale_mem_rdata, oscale_regs(oscale_regs_index))
  bias_regs(bias_regs_index) := Mux(bias_mem_read_valid, bias_mem_rdata, bias_regs(bias_regs_index))

  // data x32
  val opfusion_data = Seq.fill(64)(Module(new opfusion_data_cell))
  for(i <- 0 until 32){
    opfusion_data(i).io.i_mode := io.i_mode
    opfusion_data(i).io.i_oscale := Float(oscale_regs(i))
    opfusion_data(i).io.i_bias := Float(bias_regs(i))
    opfusion_data(i).io.i_bias_en := io.i_bias_en
    opfusion_data(i).io.i_act_mode := io.i_act_mode
    opfusion_data(i).io.i_leakyrelu_param := Float(io.i_leakyrelu_param)
    opfusion_data(i).io.i_rescale := Float(io.i_rescale)
    opfusion_data(i).io.i_rescale_en := io.i_rescale_en
    opfusion_data(i).io.i_data := io.i_data(i).bits.asSInt
    io.o_data(i).bits := opfusion_data(i).io.o_ifm_fp32
  }
  for (i <- 32 until 64) {
    opfusion_data(i).io.i_mode := io.i_mode
    opfusion_data(i).io.i_oscale := Float(oscale_regs(i-32))
    opfusion_data(i).io.i_bias := Float(bias_regs(i-32))
    opfusion_data(i).io.i_bias_en := io.i_bias_en
    opfusion_data(i).io.i_act_mode := io.i_act_mode
    opfusion_data(i).io.i_leakyrelu_param := Float(io.i_leakyrelu_param)
    opfusion_data(i).io.i_rescale := Float(io.i_rescale)
    opfusion_data(i).io.i_rescale_en := io.i_rescale_en
    opfusion_data(i).io.i_data := io.i_data(i).bits.asSInt
    io.o_data(i).bits := opfusion_data(i).io.o_ifm_fp32
  }

  // valid x32
  val opfusion_valid = Seq.fill(32)(Module(new opfusion_valid_cell))
  for(i <- 0 until 32){
      opfusion_valid(i).io.i_valid := io.i_data(i).valid
      opfusion_valid(i).io.i_mode := io.i_mode
      opfusion_valid(i).io.i_bias_en := io.i_bias_en
      opfusion_valid(i).io.i_act_mode := io.i_act_mode
      opfusion_valid(i).io.i_rescale_en := io.i_rescale_en
      io.o_data(i).valid := opfusion_valid(i).io.o_valid
      io.o_data(i+32).valid := opfusion_valid(i).io.o_valid
  }

//  val test_o_cnt = RegInit(0.U(10.W))
//  test_o_cnt := Mux(start, 0.U, Mux(io.o_data(0).valid, test_o_cnt+1.U, test_o_cnt))
//  dontTouch(test_o_cnt)
//  val test_i_cnt = RegInit(0.U(10.W))
//  test_i_cnt := Mux(start, 0.U, Mux(io.i_data(0).valid, test_i_cnt + 1.U, test_i_cnt))
//  dontTouch(test_i_cnt)
}

