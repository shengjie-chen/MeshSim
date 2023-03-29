import chisel3.{Output, _}
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

class opfusion extends Module with dma_config with gemm_config {
  val io = IO(new Bundle() {
    //data
    val i_valid = Input(Bool())
    val i_data = Input(UInt(1024.W))
    val o_valid = Output(Bool())
    val o_ifm_int8 = Output(UInt(256.W))
    val o_ifm_fp32 = Output(UInt(1024.W))
    val o_mat32 = Output(UInt(1024.W))
    //parameters
    val i_start = Input(Bool())
    val i_mode = Input(UInt(2.W))
    val i_oscale = Input(UInt(dmaAddrWidth.W))
    val i_bias = Input(UInt(dmaAddrWidth.W))
    val i_bias_en = Input(Bool())
    val i_act_mode = Input(UInt(3.W))
    val i_leakyrelu_param = Input(UInt(32.W))
    val i_rescale = Input(UInt(32.W))
    val i_rescale_en = Input(Bool())
    val i_oc = Input(UInt(12.W))
    //dma
    val dma_rdata = new dmaRData_io(dmaDataWidth)
    val dma_rareq = new dmaCtrl_io(dmaSizeWidth, dmaAddrWidth)
    val dma_rbusy = Input(Bool())
    val dma_rid = Input(UInt(log2Ceil(id.values.toList.max + 1).W))
    //ctrl
    val cur_c_index = Input(UInt(6.W))
    val o_ready = Output(Bool())
  })

  //reg the input parameters
  val start = riseEdge(io.i_start)
  val i_mode = RegEnable(io.i_mode, 0.U, start)
  val i_oscale = RegEnable(io.i_oscale, 0.U, start)
  val i_bias = RegEnable(io.i_bias, 0.U, start)
  val i_bias_en = RegEnable(io.i_bias_en, false.B, start)
  val i_act_mode = RegEnable(io.i_act_mode, 0.U, start)
  val i_leakyrelu_param = RegEnable(io.i_leakyrelu_param, 0.U, start)
  val i_rescale = RegEnable(io.i_rescale, 0.U, start)
  val i_rescale_en = RegEnable(io.i_rescale_en, false.B, start)
  val i_oc = RegEnable(io.i_oc, 0.U, start)
  val is_ifm = i_mode === IFM_FP32 | i_mode === IFM_INT8

  //module ctrl
  val en = RegInit(false.B)
  en := Mux(start, true.B, Mux(io.o_ready, false.B, en))
  val dma_rbusy_down = fallEdge(io.dma_rbusy) & io.dma_rid === id("opfusion").U
  val mem_wcnt = RegInit(0.U(9.W))
  mem_wcnt := Mux(dma_rbusy_down,0.U,Mux(en & io.dma_rdata.valid, mem_wcnt + 1.U, mem_wcnt))
  val start_t = RegNext(start)

  //ready bias / oscale mem
  val bias_mem = Seq.fill(8)(SPRAM(128,64,"block"))
  val bias_load_en = RegInit(false.B)
  val bias_load_finish = RegInit(false.B)
  bias_load_finish := Mux(start_t, !i_bias_en, Mux(i_bias_en & bias_load_en & dma_rbusy_down, true.B, bias_load_finish))
  bias_load_en := i_bias_en & en & !bias_load_finish & is_ifm
  val oscale_mem = Seq.fill(8)(SPRAM(128,64,"block"))
  val oscale_load_en = RegInit(false.B)
  val oscale_load_finish = RegInit(false.B)
  oscale_load_finish := Mux(start, false.B, Mux(oscale_load_en & dma_rbusy_down, true.B, oscale_load_finish))
  oscale_load_en := is_ifm & bias_load_finish & en
  io.dma_rareq.dmaEn := bias_load_en | oscale_load_en
  io.dma_rareq.dmaAddr := Mux(bias_load_en.asBool, i_bias, i_oscale)
  io.dma_rareq.dmaSize := i_oc(11, 2)
  io.o_ready := Mux(is_ifm, oscale_load_finish, bias_load_finish)
  val dma_rareq_bias = RegInit(false.B)
  val dma_rareq_oscale = RegInit(false.B)
  io.dma_rareq.dmaAreq := Mux(bias_load_en, dma_rareq_bias, dma_rareq_oscale)

  //bias mem dma
  dma_rareq_bias := Mux(bias_load_en & io.dma_rid === id("opfusion").U & !io.dma_rbusy & !dma_rbusy_down & !bias_load_finish, true.B, Mux(dma_rareq_bias & io.dma_rbusy, false.B, dma_rareq_bias))
  for(i<-0 until 8){
    bias_mem(i).clock := clock
    bias_mem(i).en := true.B
    bias_mem(i).wr := ~(bias_load_en & io.dma_rdata.valid & mem_wcnt(2,0) === i.U)
    bias_mem(i).wdata := io.dma_rdata.data
    bias_mem(i).addr := Mux(bias_load_en,mem_wcnt(8,3),io.cur_c_index)
  }

  //oscale mem dma
  dma_rareq_oscale := Mux(oscale_load_en & io.dma_rid === id("opfusion").U & !io.dma_rbusy & !dma_rbusy_down & !oscale_load_finish & bias_load_finish, true.B, Mux(dma_rareq_oscale & io.dma_rbusy, false.B, dma_rareq_oscale))
  for (i <- 0 until 8) {
    oscale_mem(i).clock := clock
    oscale_mem(i).en := true.B
    oscale_mem(i).wr := ~(oscale_load_en & io.dma_rdata.valid & mem_wcnt(2, 0) === i.U)
    oscale_mem(i).wdata := io.dma_rdata.data
    oscale_mem(i).addr := Mux(oscale_load_en, mem_wcnt(8, 3), io.cur_c_index)
  }



  // data x32
  val opfusion_data = Seq.fill(32)(Module(new opfusion_data_cell))
  for(i <- 0 until 32){
    opfusion_data(i).io.i_mode := i_mode
    opfusion_data(i).io.i_oscale := Float(oscale_mem(i/4).rdata((i%4)*32+31,(i%4)*32))
    opfusion_data(i).io.i_bias := Float(bias_mem(i/4).rdata((i%4)*32+31,(i%4)*32))
    opfusion_data(i).io.i_bias_en := i_bias_en
    opfusion_data(i).io.i_act_mode := i_act_mode
    opfusion_data(i).io.i_leakyrelu_param := Float(i_leakyrelu_param)
    opfusion_data(i).io.i_rescale := Float(i_rescale)
    opfusion_data(i).io.i_rescale_en := i_rescale_en

    opfusion_data(i).io.i_data := io.i_data(i*32+31,i*32).asSInt
  }

  io.o_ifm_int8 := (for(i <- 0 until 32) yield {opfusion_data(i).io.o_ifm_int8}).reverse.reduce(Cat(_,_))
  io.o_ifm_fp32 := (for(i <- 0 until 32) yield {opfusion_data(i).io.o_ifm_fp32}).reverse.reduce(Cat(_,_))
  io.o_mat32 := (for(i <- 0 until 32) yield {opfusion_data(i).io.o_mat32}).reverse.reduce(Cat(_,_))

  // valid x1
  val opfusion_valid = Module(new opfusion_valid_cell)
  opfusion_valid.io.i_valid := io.i_valid
  opfusion_valid.io.i_mode := i_mode
  opfusion_valid.io.i_bias_en := i_bias_en
  opfusion_valid.io.i_act_mode := i_act_mode
  opfusion_valid.io.i_rescale_en := i_rescale_en
  io.o_valid := opfusion_valid.io.o_valid
}

