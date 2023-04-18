import Chisel.Mux
import chisel3._
import chisel3.util._


class ifm_w_io(ifm_buffer_size:Int,ifm_buffer_width:Int) extends Bundle{
  val wen = Output(Bool())
  val waddr = Output(UInt(log2Ceil(ifm_buffer_size).W))
  val wdata = Output(UInt(ifm_buffer_width.W))
}

class ifm_r_io(ifm_buffer_size:Int,ifm_buffer_width:Int) extends Bundle{
  val ren = Input(Bool())
  val raddr = Input(UInt(log2Ceil(ifm_buffer_size).W))
  val rdata = Output(UInt((ifm_buffer_width).W))
}

class whc extends Bundle{
  val w = Input(UInt(16.W))
  val h = Input(UInt(16.W))
  val c = Input(UInt(16.W))
}

/* *************************************************************
 *  mode    : mode == 0/1 -> mat32       mode == 3 -> ifm_int8
 *  int8_ifm: (ic,iw,ih)    ic%32==0
 *  mat32   : (iw,1,ih)     iw%8==0
 * *************************************************************/
class int8_ifm_and_mat32_unit extends Module with dma_config with buffer_config with gemm_config {
  val io = IO(new Bundle() {
    //ctrl signal
    val start = Input(Bool())  //edge
    val clr = Input(Bool())
    val mode = Input(UInt(2.W))
    val ifm_dma_addr = Input(UInt(dmaAddrWidth.W))
    val whc = new whc
    val cstep = Input(UInt(32.W))
    //dma read
    val dma_rdata = new dmaRData_io(dmaDataWidth)
    val dma_rareq = new dmaCtrl_io(dmaSizeWidth, dmaAddrWidth)
    val dma_rbusy = Input(Bool())
    val dma_rid = Input(UInt(log2Ceil(id.values.toList.max+1).W))
    //ifm mem write
    val ifm_w_port = new ifm_w_io(ifm_buffer_size, ifm_buffer_width)
    val task_done = Output(Bool())
  })

  val start_t1 = RegNext(io.start)
  val start_t2 = RegNext(start_t1)
  val en = RegInit(false.B)
  en := Mux(start_t2,true.B,Mux(io.task_done,false.B,en))
  val mode = RegEnable(io.mode,0.U,io.start)

  val dma_size_mat32 = RegEnable(io.whc.w >> 2.U,0.U,start_t1)
  val ifm_c_align = RegEnable(align(io.whc.c,32),0.U,io.start)
  val dma_size_int8 = RegEnable((ifm_c_align*io.whc.w)>>4.U,0.U,start_t1)
  val dma_burst_num = RegEnable(Mux(mode===IFM_INT8, io.whc.h, io.whc.c),0.U,start_t1)
  val cstep = RegInit(0.U(32.W))
  cstep := Mux(start_t2, Mux(mode=== IFM_INT8, io.cstep, Cat(io.cstep,0.U(2.W))), cstep)

  val dma_finish = Wire(UInt(1.W))
  val dma_en = RegInit(false.B)
  val dma_areq = RegInit(false.B)
  val dma_addr = RegInit(0.U(dmaAddrWidth.W))
  val dma_size = RegInit(0.U(32.W))
  io.dma_rareq.dmaEn := dma_en
  io.dma_rareq.dmaAreq := dma_areq
  io.dma_rareq.dmaAddr := dma_addr
  io.dma_rareq.dmaSize := dma_size
  val dma_rbusy_down = fallEdge(io.dma_rbusy)
  val dma_rareq_down = fallEdge(dma_areq)
  dma_en := Mux(start_t2,true.B,Mux(io.task_done,false.B,dma_en))
  dma_areq := Mux(dma_en && !dma_finish && !io.task_done && !io.dma_rbusy && io.dma_rid === id("gemm").U,true.B,Mux(io.dma_rbusy && dma_areq,false.B,dma_areq))
  dma_addr := Mux(start_t2,io.ifm_dma_addr,Mux(dma_rareq_down,dma_addr+cstep,dma_addr))
  dma_size := Mux(mode === IFM_INT8,dma_size_int8,dma_size_mat32)

  val dma_burst_num_cnt = RegInit(0.U(16.W))
  dma_burst_num_cnt := Mux(io.start|io.clr,0.U,Mux(en&dma_rbusy_down,dma_burst_num_cnt+1.U,dma_burst_num_cnt))
  dma_finish := dma_burst_num_cnt===dma_burst_num-1.U && dma_rbusy_down
  val task_done = RegInit(false.B)
  task_done := Mux(io.start|io.clr, false.B, Mux(dma_finish.asBool, true.B, task_done))
  io.task_done := task_done

  val dma_data_t = RegNext(io.dma_rdata.data)
  val ifm_waddr = RegInit(0.U(log2Ceil(ifm_buffer_size).W))
  val ifm_wcnt = RegInit(false.B)
  val dma_data_valid = RegNext(io.dma_rdata.valid)
  ifm_wcnt := Mux(dma_data_valid, !ifm_wcnt, ifm_wcnt)
  ifm_waddr := Mux(start_t1, 0.U, Mux(io.ifm_w_port.wen & ifm_wcnt, ifm_waddr + 1.U, ifm_waddr))
  val ifm_wdata = RegNext(Cat(io.dma_rdata.data,dma_data_t))
  io.ifm_w_port.wdata := ifm_wdata
  io.ifm_w_port.wen := dma_data_valid & ifm_wcnt
  io.ifm_w_port.waddr := ifm_waddr
}

/* *************************************************************
 *  function: mul(fp32) = a(fp32) * b(fp32)
 *            if(mul > 127) mul = 127
 *            else if(mul < -128) mul = -128
 * *************************************************************/
class quant_cell(implicit env:Arithmetic[Float]) extends Module{
  import env._
  val io = IO(new Bundle() {
    val i_data = Input(UInt(32.W))
    val scale = Input(UInt(32.W))
    val o_data = Output(UInt(8.W))
  })
  io.o_data := Float.FloatToSInt(Float(io.i_data)*Float(io.scale),8).asUInt
}

class quant_unit extends Module with dma_config with cal_cell_params{
  val io = IO(new Bundle() {
    val i_data = Input(UInt(dmaDataWidth.W))
    val i_valid = Input(Bool())
    val i_scale = Input(UInt(32.W))
    val o_data = Output(UInt((dmaDataWidth/32*8).W))
    val o_valid = Output(Bool())
  })
  val quant = Seq.fill(dmaDataWidth/32)(Module(new quant_cell))
  for(i <- 0 until dmaDataWidth/32){
    quant(i).io.i_data := io.i_data(i*32+31,i*32)
    quant(i).io.scale := io.i_scale
  }
  io.o_data := (for(i <- 0 until dmaDataWidth/32) yield {quant(i).io.o_data}).reverse.reduce(Cat(_,_))
  io.o_valid := ShiftRegister(io.i_valid,fp32_to_sint_cycles+fp32_mul_cycles)
}

/* *************************************************************
 *  function:  im2col core code
 * *************************************************************/
class fp32_ifm_unit extends Module with dma_config with buffer_config with cal_cell_params {
  val io = IO(new Bundle() {
    //ctrl signal
    val start = Input(Bool())  //edge
    val clr = Input(Bool())
    //parameter
    val ifm_whc = new whc
    val ifm_cstep = Input(UInt(32.W))
    val ifm_dma_addr = Input(UInt(dmaAddrWidth.W))
    val ifm_quant_scale = Input(UInt(32.W))
    //dma read
    val dma_rdata = new dmaRData_io(dmaDataWidth)
    val dma_rareq = new dmaCtrl_io(dmaSizeWidth, dmaAddrWidth)
    val dma_rbusy = Input(Bool())
    val dma_rid = Input(UInt(log2Ceil(id.values.toList.max+1).W))
    //ifm mem write
    val ifm_w_port = new ifm_w_io(ifm_buffer_size, ifm_buffer_width)
    val task_done = Output(Bool())
  })

  //cache the input ctrl signal to register
  val start_t = RegNext(io.start)
  val ifm_wh = RegInit(0.U(32.W))
  ifm_wh := Mux(io.start, io.ifm_whc.w*io.ifm_whc.h, ifm_wh)
  val ifm_c = RegEnable(io.ifm_whc.c, 0.U, io.start)
  val ifm_cstep = RegEnable(io.ifm_cstep, 0.U, io.start)
  val ifm_dma_addr = RegEnable(io.ifm_dma_addr, 0.U, io.start)
  val ifm_quant_scale = RegEnable(io.ifm_quant_scale, 0.U, io.start)
  val ifm_c_align = RegEnable(align(io.ifm_whc.c,32), 0.U, io.start)
  val ifm_c_align_div32 = ifm_c_align(15,5)

  val en = RegInit(false.B)
  en := Mux(io.start, true.B, Mux(io.task_done, false.B, en))
  val ifm_wen = RegInit(false.B)
  val load_done = RegInit(false.B)
  val store_done = fallEdge(ifm_wen)

  //dma_ctrl
  val dma_trans_done = RegInit(false.B)
  val dma_trans_stop = RegInit(false.B)
  val dma_rbusy_down = fallEdge(io.dma_rbusy)
  val dma_rareq_down = fallEdge(io.dma_rareq.dmaAreq)
  val dma_wh_cnt = RegInit(0.U(32.W))
  val dma_c_cnt = RegInit(0.U(16.W))
  val dma_c_cnt_equal = ifm_c === dma_c_cnt & ifm_c =/= 0.U
  val dma_c_cnt_equal_rise = riseEdge(dma_c_cnt_equal)
  dma_wh_cnt := Mux(io.start, 0.U, Mux(dma_c_cnt_equal_rise, dma_wh_cnt+64.U, dma_wh_cnt))
  dma_c_cnt := Mux(io.start|dma_c_cnt_equal_rise, 0.U, Mux(dma_rareq_down, Mux(dma_c_cnt_equal, 0.U, dma_c_cnt+1.U), dma_c_cnt))

  val dma_addr_base = RegInit(0.U(dmaAddrWidth.W))
  val dma_addr_offset = RegInit(0.U(dmaAddrWidth.W))
  dma_addr_base := ifm_dma_addr + Cat(dma_wh_cnt,0.U(2.W))
  dma_addr_offset := Cat(dma_c_cnt*ifm_cstep,0.U(2.W))
  val dma_areq = RegInit(false.B)
  val dma_addr = RegInit(0.U(dmaAddrWidth.W))
  io.dma_rareq.dmaEn := en
  io.dma_rareq.dmaAreq := dma_areq
  io.dma_rareq.dmaAddr := dma_addr
  io.dma_rareq.dmaSize := 16.U
  dma_areq := Mux(en & !io.dma_rbusy & !dma_trans_stop & io.dma_rid===id("gemm").U, true.B, Mux(io.dma_rbusy & dma_areq, false.B, dma_areq))
  dma_addr := dma_addr_offset + dma_addr_base

  val dma_trans_num = RegInit(0.U(6.W))
  val dma_c_res = RegInit(0.U(16.W))
  val dma_trans_cnt = RegInit(0.U(6.W))
  val dma_c_cnt_equal_keep = RegInit(false.B)
  dma_c_cnt_equal_keep := Mux(io.start|store_done, false.B, Mux(dma_c_cnt_equal, true.B, dma_c_cnt_equal_keep))
  dma_c_res := Mux(start_t|(dma_c_cnt_equal_keep&store_done), ifm_c, Mux(store_done & dma_c_res>32.U, dma_c_res-32.U, dma_c_res))
  dma_trans_num := Mux(dma_c_res > 32.U, 32.U, dma_c_res)
  dma_trans_cnt := Mux(io.start|store_done, 0.U, Mux(dma_rareq_down,  dma_trans_cnt+1.U, dma_trans_cnt))
  dma_trans_done := dma_trans_cnt===dma_trans_num & dma_rbusy_down
  dma_trans_stop :=  Mux(dma_trans_cnt===dma_trans_num & riseEdge(io.dma_rdata.valid), true.B, Mux(store_done, false.B, dma_trans_stop))

  val quant = Module(new quant_unit)
  quant.io.i_data := io.dma_rdata.data
  quant.io.i_valid := io.dma_rdata.valid
  quant.io.i_scale := ifm_quant_scale
  val quant_data = quant.io.o_data
  val quant_valid = quant.io.o_valid

  //add zero
  val add_zero_len = RegInit(0.U(10.W))
  add_zero_len := Cat(ifm_c_align - ifm_c, 0.U(4.W))
  val add_zero_cnt = RegInit(0.U(10.W))
  val add_zero_done = add_zero_cnt === add_zero_len-1.U
  val add_zero_valid = RegInit(false.B)
  val add_zero_ready = RegInit(false.B)
  add_zero_ready := Mux(RegNext(dma_rareq_down) & dma_c_cnt_equal & ifm_c < ifm_c_align, true.B, Mux(add_zero_valid, false.B, add_zero_ready))
  add_zero_valid := Mux(fallEdge(quant_valid) & add_zero_ready, true.B, Mux(add_zero_done, false.B, add_zero_valid))
  add_zero_cnt := Mux(add_zero_valid, add_zero_cnt+1.U, 0.U)

  val dma_trans_done_keep = RegInit(false.B)
  val dma_trans_done_t = RegInit(false.B)
  val add_zero_done_t = RegInit(false.B)
  dma_trans_done_keep := Mux(dma_trans_done, true.B, Mux(store_done, false.B, dma_trans_done_keep))
  dma_trans_done_t := Mux(ifm_wen,false.B,Mux(ShiftRegister(dma_trans_done,fp32_to_sint_cycles+fp32_mul_cycles),true.B,dma_trans_done_t))
  add_zero_done_t := Mux(ifm_wen,false.B,Mux(add_zero_done,true.B,add_zero_done_t))
  load_done := Mux(ifm_c < ifm_c_align & dma_trans_done_keep & dma_c_res < 32.U, add_zero_done_t, dma_trans_done_t)

  //write to ifm mem
  val layout_array = Seq.fill(32)(SPRAM(32,16,"distribute"))
  val layout_array_waddr = RegInit(0.U(4.W))
  val layout_array_cnt = RegInit(0.U(5.W))
  layout_array_waddr := Mux(store_done,0.U,Mux(quant_valid|add_zero_valid,layout_array_waddr+1.U,layout_array_waddr))
  layout_array_cnt := Mux(store_done,0.U,Mux(layout_array_waddr===15.U && (quant_valid|add_zero_valid),layout_array_cnt+1.U,layout_array_cnt))
  val layout_array_raddr = RegInit(0.U(4.W))
  val layout_array_sel = RegInit(0.U(2.W))
  for(i <- 0 until 32){
    layout_array(i).clock := clock
    layout_array(i).en := en
    layout_array(i).wr := ~((quant_valid | add_zero_valid) & layout_array_cnt === i.U)
    layout_array(i).wdata := Mux(add_zero_valid,0.U,quant_data)
    layout_array(i).addr := Mux(layout_array(i).wr,layout_array_raddr,layout_array_waddr)
  }

  //ifm_mem write abrit
  ifm_wen := Mux(load_done,true.B,Mux((layout_array_raddr === 15.U && layout_array_sel === 3.U) | io.task_done,false.B,ifm_wen))
  val ifm_wen_t = RegNext(ifm_wen)
  layout_array_raddr := Mux(store_done,0.U,Mux(ifm_wen && layout_array_sel === 3.U, layout_array_raddr+1.U, layout_array_raddr))
  layout_array_sel := Mux(store_done, 0.U, Mux(ifm_wen, layout_array_sel+1.U, layout_array_sel))
  val ifm_wh_cnt = RegInit(0.U(6.W))
  val ifm_wh_cnt_base = RegInit(0.U(32.W))
  val ifm_c_cnt_div32 = RegInit(0.U(11.W))
  ifm_wh_cnt := Mux(io.clr|io.start, 0.U, Mux(ifm_wen, ifm_wh_cnt+1.U, ifm_wh_cnt))
  ifm_wh_cnt_base := Mux(io.start, 0.U, Mux(store_done & (ifm_c_cnt_div32===ifm_c_align_div32), ifm_wh_cnt_base+64.U, ifm_wh_cnt_base))
  when(ifm_wen & ifm_wh_cnt === 63.U){
    ifm_c_cnt_div32 := ifm_c_cnt_div32 + 1.U
  }.elsewhen(io.start | ifm_c_cnt_div32===ifm_c_align_div32){
    ifm_c_cnt_div32 := 0.U
  }

  val ifm_mem_wh = ifm_wh_cnt_base + ifm_wh_cnt
  val ifm_mem_waddr = RegInit(0.U(log2Ceil(ifm_buffer_size).W))
  val ifm_mem_waddr_base = Wire(UInt(log2Ceil(ifm_buffer_size).W))
  ifm_mem_waddr_base := ifm_mem_wh * ifm_c_align_div32
  ifm_mem_waddr := ifm_mem_waddr_base + ifm_c_cnt_div32

  io.ifm_w_port.wen := ifm_wen_t
  io.ifm_w_port.waddr := ifm_mem_waddr
  val layout_array_sel_t = RegNext(layout_array_sel)
  val ifm_wdata = Wire(UInt(ifm_buffer_width.W))
  dontTouch(ifm_wdata)
  ifm_wdata := 0.U
  for (j <- 0 until 4) {
    when(layout_array_sel_t === j.U) {
      ifm_wdata := (for (i <- 0 until 32) yield {layout_array(i).rdata(8 * j + 7, 8 * j)}).reverse.reduce(Cat(_, _))
      //ifm_mem: high bit cached high channel data
    }
  }
  io.ifm_w_port.wdata := ifm_wdata

  val task_done = RegInit(false.B)
  task_done := Mux(io.clr | io.start, false.B, Mux(ifm_mem_wh===ifm_wh-1.U && ifm_c_cnt_div32===ifm_c_align_div32-1.U, true.B, task_done))
  io.task_done := task_done
}


/* *************************************************************
 *  function 1 : fp32_int32_mat -> 0
 *  function 2 : int8_ifm       -> 1
 *  function 3 : fp32_ifm       -> 2
 * *************************************************************/
class im2col extends Module with dma_config with buffer_config{
  val io = IO(new Bundle() {
    //axi-lite reg
    val ch0_whc = new whc
    val ch0_cstep = Input(UInt(32.W))
    val im2col_en = Input(Bool())
    val quant_scale = Input(UInt(32.W))
    val ch0_src0_addr = Input(UInt(dmaAddrWidth.W))
    val im2col_format = Input(UInt(2.W))
    //dma
    val dma_ch0_rdata = new dmaRData_io(dmaDataWidth)
    val dma_ch0_rareq = new dmaCtrl_io(dmaSizeWidth, dmaAddrWidth)
    val dma_ch0_rbusy = Input(Bool())
    val dma_ch0_rid = Input(UInt(log2Ceil(id.values.toList.max+1).W))
    //ifm
    val ifm_read_port0 = new ifm_r_io(ifm_buffer_size, ifm_buffer_width)
    val ifm_read_port1 = new ifm_r_io(ifm_buffer_size, ifm_buffer_width)
    val task_done = Output(Bool())
  })

  val int8_ifm_and_mat32_unit = Module(new int8_ifm_and_mat32_unit)
  val fp32_ifm_unit = Module(new fp32_ifm_unit)

  val ifm_mem = DPRAM(ifm_buffer_width,ifm_buffer_size,"ultra")

  val is_Int8_ifm_mat32 = io.im2col_format =/= 2.U
  val is_fp32_ifm = io.im2col_format === 2.U
  val ifm_mem_w_port = Mux(is_Int8_ifm_mat32,int8_ifm_and_mat32_unit.io.ifm_w_port,fp32_ifm_unit.io.ifm_w_port)
  val im2col_en_rise_edge  = riseEdge(io.im2col_en)

  //ifm_mem connect
  ifm_mem.clock := clock
  ifm_mem.en_a := io.im2col_en
  ifm_mem.en_b := io.im2col_en
  ifm_mem.wr_a := ~ifm_mem_w_port.wen
  ifm_mem.wr_b := true.B
  ifm_mem.addr_a := Mux(ifm_mem.wr_a , io.ifm_read_port0.raddr, ifm_mem_w_port.waddr)
  ifm_mem.addr_b := io.ifm_read_port1.raddr
  io.ifm_read_port0.rdata := ifm_mem.rdata_a
  io.ifm_read_port1.rdata := ifm_mem.rdata_b
  ifm_mem.wdata_a := ifm_mem_w_port.wdata
  ifm_mem.wdata_b := 0.U

  //int8_ifm_and_mat32_unit connect
  int8_ifm_and_mat32_unit.io.dma_rdata := io.dma_ch0_rdata
  int8_ifm_and_mat32_unit.io.dma_rbusy := io.dma_ch0_rbusy
  int8_ifm_and_mat32_unit.io.dma_rid := io.dma_ch0_rid
  int8_ifm_and_mat32_unit.io.start := im2col_en_rise_edge && is_Int8_ifm_mat32
  int8_ifm_and_mat32_unit.io.mode := io.im2col_format
  int8_ifm_and_mat32_unit.io.ifm_dma_addr := io.ch0_src0_addr
  int8_ifm_and_mat32_unit.io.whc := io.ch0_whc
  int8_ifm_and_mat32_unit.io.cstep := io.ch0_cstep
  int8_ifm_and_mat32_unit.io.clr := ~io.im2col_en

  //fp32_ifm_unit connect
  fp32_ifm_unit.io.dma_rdata := io.dma_ch0_rdata
  fp32_ifm_unit.io.dma_rbusy := io.dma_ch0_rbusy
  fp32_ifm_unit.io.dma_rid := io.dma_ch0_rid
  fp32_ifm_unit.io.start := im2col_en_rise_edge && is_fp32_ifm
  fp32_ifm_unit.io.ifm_whc := io.ch0_whc
  fp32_ifm_unit.io.ifm_cstep := io.ch0_cstep
  fp32_ifm_unit.io.ifm_dma_addr := io.ch0_src0_addr
  fp32_ifm_unit.io.ifm_quant_scale := io.quant_scale
  fp32_ifm_unit.io.clr := ~io.im2col_en

  when(is_Int8_ifm_mat32){
    io.dma_ch0_rareq <> int8_ifm_and_mat32_unit.io.dma_rareq
    io.task_done := int8_ifm_and_mat32_unit.io.task_done
  }.otherwise{
    io.dma_ch0_rareq <> fp32_ifm_unit.io.dma_rareq
    io.task_done := fp32_ifm_unit.io.task_done
  }
}
