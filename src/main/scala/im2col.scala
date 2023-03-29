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
 *  int8_ifm: mat_w = ic (ic%32==0)     mat_h = iw * ih
 *  mat32   : mat_w = w (w%32==0)     mat_h = h
 *  mat_wh  : mat_w * mat_h
 * *************************************************************/
class int8_ifm_and_mat32_unit extends Module with dma_config with buffer_config{
  val io = IO(new Bundle() {
    //ctrl signal
    val start = Input(Bool())
    val clr = Input(Bool())
    val mode = Input(UInt(2.W))
    val ifm_dma_addr = Input(UInt(dmaAddrWidth.W))
    val ifm_whc = new whc
    //dma read
    val dma_rdata = new dmaRData_io(dmaDataWidth)
    val dma_rareq = new dmaCtrl_io(dmaSizeWidth, dmaAddrWidth)
    val dma_rbusy = Input(Bool())
    val dma_rid = Input(UInt(log2Ceil(id.values.toList.max+1).W))
    //ifm mem write
    val ifm_w_port = new ifm_w_io(ifm_buffer_size*2, ifm_buffer_width)
    //place flag
    val cache_whc = Flipped(new whc)
    val cache_valid = Output(Bool())
    val task_done = Output(Bool())
  })


  val start = riseEdge(io.start)
  val start_t1 = RegNext(start)
  val start_t2 = RegNext(start_t1)
  val en = RegInit(false.B)
  en := Mux(start_t2,true.B,Mux(io.task_done,false.B,en))
  val mode = RegEnable(io.mode,0.U,start)

  val matWAlignMat = (io.ifm_whc.w+3.U) & -4.S(16.W).asUInt
  val matWAlignInt8 = (io.ifm_whc.w+15.U) & -16.S(16.W).asUInt
  val dmaSizeAlignMat = RegEnable(matWAlignMat*io.ifm_whc.c, 0.U, start)(31,2)
  val dmaSizeAlignInt8_t = RegEnable(matWAlignInt8*io.ifm_whc.h,0.U,start)(31,4)
  val dmaSizeAlignInt8 = RegEnable(dmaSizeAlignInt8_t*io.ifm_whc.c,0.U,start_t1)(31,0)

  val dmaAreq = RegInit(false.B)
  val dmaAddr = RegInit(0.U(dmaAddrWidth.W))
  val dmaSize = RegInit(0.U(32.W))
  io.dma_rareq.dmaEn := RegNext(!reset.asBool && en && !io.task_done)
  io.dma_rareq.dmaAreq := dmaAreq
  io.dma_rareq.dmaAddr := dmaAddr
  io.dma_rareq.dmaSize := dmaSize
  dmaAreq := Mux(io.dma_rareq.dmaEn && !io.dma_rbusy && io.dma_rid === id("im2col").U && !io.ifm_w_port.wen && !io.task_done,true.B,Mux(io.dma_rbusy && dmaAreq,false.B,dmaAreq))
  dmaAddr := Mux(start_t2,io.ifm_dma_addr,dmaAddr)
  dmaSize := Mux(start_t2,Mux(mode(1),Mux(mode === 3.U,dmaSizeAlignInt8,0.U),dmaSizeAlignMat),dmaSize)
  //int8_ifm (mode = 0) : w * h * 8 /128
  //mat32    (mode = 1) : w * h * 32 / 128

  val ifm_waddr = RegInit(0.U(log2Ceil(ifm_buffer_size*2).W))

  val ifm_wcnt = RegInit(false.B)
  ifm_wcnt := Mux(io.ifm_w_port.wen, !ifm_wcnt, ifm_wcnt)
  ifm_waddr := Mux(start, 0.U, Mux(io.ifm_w_port.wen & ifm_wcnt, ifm_waddr + 1.U, ifm_waddr))
  val ifm_wdata = RegNext(Cat(io.dma_rdata.data,RegNext(io.dma_rdata.data)))
  io.ifm_w_port.wdata := ifm_wdata
  io.ifm_w_port.wen := RegNext(io.dma_rdata.valid)
  io.ifm_w_port.waddr := ifm_waddr
  val ifm_wen_down = fallEdge(io.ifm_w_port.wen)

  io.task_done := ifm_wen_down | RegNext(ifm_wen_down)

  val w = RegInit(0.U(16.W))
  val h = RegInit(0.U(16.W))
  val c = RegInit(0.U(16.W))
  val iw = RegEnable(Mux(mode(1), io.ifm_whc.h, matWAlignMat), 0.U, io.start)
  val ic = RegEnable(Mux(mode(1), matWAlignInt8, 0.U), 0.U, io.start)

  c := Mux(start | mode =/= 3.U, 0.U, Mux(io.ifm_w_port.wen, Mux(c === ic-16.U, 0.U, c+16.U), c))
  when(start | io.clr){
    w := 0.U
    h := 0.U
  }.elsewhen(!mode(1) && io.ifm_w_port.wen){
    when(w === iw - 4.U){
      w := 0.U
      h := h+1.U
    }.otherwise{
      w := w+4.U
    }
  }.elsewhen(mode === 3.U && io.ifm_w_port.wen && c === ic-16.U){
    when(w === iw - 1.U) {
      w := 0.U
      h := h + 1.U
    }.otherwise {
      w := w + 1.U
    }
  }
  io.cache_whc.w := w
  io.cache_whc.h := h
  io.cache_whc.c := c
  io.cache_valid := (w | h | c) =/= 0.U

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

class quant_unit extends Module with dma_config {
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
  io.o_valid := ShiftRegister(io.i_valid,3)
}


/* *************************************************************
 *  function:  im2col core code
 * *************************************************************/
class fp32_ifm_unit extends Module with dma_config with buffer_config{
  val io = IO(new Bundle() {
    //ctrl signal
    val start = Input(Bool())
    val clr = Input(Bool())
    //parameter
    val ifm_whc = new whc
    val ifm_cstep = Input(UInt(32.W))
    val ifm_dma_addr = Input(UInt(dmaAddrWidth.W))
    val ifm_quant_scale = Input(UInt(32.W))
    //place info
    val cache_whc = Flipped(new whc)
    val cache_valid = Output(Bool())
    val task_done = Output(Bool())
    //dma read
    val dma_rdata = new dmaRData_io(dmaDataWidth)
    val dma_rareq = new dmaCtrl_io(dmaSizeWidth, dmaAddrWidth)
    val dma_rbusy = Input(Bool())
    val dma_rid = Input(UInt(log2Ceil(id.values.toList.max+1).W))
    //ifm mem write
    val ifm_w_port = new ifm_w_io(ifm_buffer_size*2, ifm_buffer_width)
  })

  //cache the input ctrl signal to register
  val start = riseEdge(io.start)
  val ifm_w = RegEnable(io.ifm_whc.w, 0.U, start)
  val ifm_h = RegEnable(io.ifm_whc.h, 0.U, start)
  val ifm_c = RegEnable(io.ifm_whc.c, 0.U, start)
  val ifm_cstep = RegEnable(io.ifm_cstep, 0.U, start)
  val ifm_dma_addr = RegEnable(io.ifm_dma_addr, 0.U, start)
  val ifm_quant_scale = RegEnable(io.ifm_quant_scale, 0.U, start)
  val ifm_c_align = RegEnable((io.ifm_whc.c+31.U) & -32.S(16.W).asUInt, 0.U, start)

  //global ctrl signal
  val task_done = RegInit(false.B)
  io.task_done := task_done
  val en = RegInit(false.B)
  en := Mux(start, true.B, Mux(task_done, false.B, en))
  val trans_done = RegInit(false.B)
  val load_done = RegInit(false.B)
  val cur_w = RegInit(0.U(16.W))
  val cur_h = RegInit(0.U(16.W))
  val cur_c = RegInit(0.U(16.W))
  val cur_w_cnt = RegInit(0.U(16.W))
  val cur_h_cnt = RegInit(0.U(16.W))
  val cur_c_cnt = RegInit(0.U(16.W))
  val cur_c_cnt_t0 = RegInit(0.U(16.W))
  cur_c_cnt_t0 := Mux(start | io.clr | io.task_done | cur_c_cnt === ifm_c_align, 0.U, cur_c_cnt)


  //single trans ctrl signal
  val im2col_begin = (RegNext(start)|RegNext(load_done)) && en
  val im2col_begin_t1 = RegNext(im2col_begin)
  val im2col_begin_t2 = RegNext(im2col_begin_t1)
  val gap_c_t = ifm_c - cur_c_cnt_t0
  val gap_c = RegEnable(Mux(gap_c_t < 32.U, gap_c_t, 32.U),0.U(6.W),im2col_begin)
  val add_zero_valid = RegInit(false.B)
  val add_zero_len = RegInit(0.U(9.W))
  val add_zero_cnt = RegInit(0.U(9.W))
  val im2col_mem_cnt = RegInit(0.U(5.W))

  val ifm_wh_info = RegEnable(ifm_w * cur_h_cnt + cur_w_cnt, 0.U, im2col_begin)(31,0)
  //cal dma baseaddr and ifm mem write baseaddr
  val dma_baseaddr = RegInit(0.U(dmaAddrWidth.W))
  val dma_offset = RegInit(0.U(32.W))
  //dma_addr = baseaddr + (c*cstep + h*map_w + w)*4
  dma_baseaddr := Mux(im2col_begin_t1, ifm_dma_addr + Cat(ifm_wh_info, 0.U(2.W)), dma_baseaddr)
  dma_offset := Mux(im2col_begin_t1, Cat(cur_c_cnt_t0 * ifm_cstep, 0.U(2.W)), dma_offset)
  val dma_addr = RegInit(0.U(dmaAddrWidth.W))
  dma_addr := Mux(im2col_begin_t2, dma_offset + dma_baseaddr, dma_addr)

  val cur_c_cnt_t = RegInit(0.U(16.W))

  //generate dma require
  io.dma_rareq.dmaSize := 8.U
  io.dma_rareq.dmaEn := en
  val dmaAddr = RegInit(0.U(dmaAddrWidth.W))
  val dmaAreq = RegInit(false.B)
  val dma_done = RegInit(false.B)
  val dma_rbusy_down = fallEdge(io.dma_rbusy)
  val dma_rbusy_down_t = RegNext(dma_rbusy_down)
  val dma_trans_cnt = RegInit(0.U(6.W))
  trans_done := Mux(add_zero_len === 0.U,riseEdge(dma_done),fallEdge(add_zero_valid))
  dma_done := Mux(im2col_begin, false.B, Mux(dma_trans_cnt === gap_c && dma_rbusy_down,true.B,dma_done) && en)
  val first_trans = RegInit(false.B)
  first_trans := Mux(im2col_begin_t2,true.B,Mux(dmaAreq,false.B,first_trans))
  val next_trans = dma_trans_cnt < gap_c && dma_rbusy_down_t
  dmaAreq := Mux((first_trans | next_trans) && en && io.dma_rid === id("im2col").U,true.B,Mux(io.dma_rbusy && dmaAreq,false.B,dmaAreq))
  dmaAddr := Mux(first_trans,dma_addr,Mux(next_trans,dmaAddr+Cat(io.ifm_cstep,0.U(2.W)),dmaAddr))
  io.dma_rareq.dmaAddr := dmaAddr
  io.dma_rareq.dmaAreq := dmaAreq
  dma_trans_cnt := Mux(im2col_begin, 0.U, Mux(riseEdge(io.dma_rbusy) && io.dma_rid === id("im2col").U, dma_trans_cnt+1.U, dma_trans_cnt))
  //quant
  val quant = Module(new quant_unit)
  quant.io.i_data := io.dma_rdata.data
  quant.io.i_valid := io.dma_rdata.valid
  quant.io.i_scale := ifm_quant_scale
  val quant_data = quant.io.o_data
  val quant_valid = quant.io.o_valid

  //channel < 32, add zero
  add_zero_len := RegEnable(Cat(32.U-gap_c,0.U(3.W)), 0.U, im2col_begin_t2)
  val add_zero_flag = add_zero_len =/= 0.U && dma_done
  add_zero_valid := Mux(im2col_begin | add_zero_cnt === add_zero_len - 1.U,false.B,Mux(add_zero_flag & fallEdge(quant.io.o_valid),true.B,add_zero_valid))
  add_zero_cnt := Mux(im2col_begin, 0.U,Mux(add_zero_valid,add_zero_cnt+1.U,add_zero_cnt))

  //write to ifm mem
  val im2col_mem = Seq.fill(32)(SPRAM(32,8,"distribute"))
  val im2col_mem_waddr = RegInit(0.U(3.W))
  im2col_mem_waddr := Mux(im2col_begin,0.U,Mux(quant_valid|add_zero_valid,im2col_mem_waddr+1.U,im2col_mem_waddr))
  im2col_mem_cnt := Mux(im2col_begin,0.U,Mux(im2col_mem_waddr===7.U && (quant_valid|add_zero_valid),im2col_mem_cnt+1.U,im2col_mem_cnt))
  val im2col_mem_raddr = RegInit(0.U(3.W))
  val im2col_mem_sel = RegInit(0.U(2.W))
  for(i <- 0 until 32){
    im2col_mem(i).clock := clock
    im2col_mem(i).en := en
    im2col_mem(i).wr := ~((quant_valid | add_zero_valid) & im2col_mem_cnt === i.U)
    im2col_mem(i).wdata := Mux(add_zero_valid,0.U,quant_data)
    im2col_mem(i).addr := Mux(im2col_mem(i).wr,im2col_mem_raddr,im2col_mem_waddr)
  }

  //ifm_mem write abrit
  val ifm_wen = RegInit(false.B)
  val ifm_wen_t1 = RegNext(ifm_wen)
  ifm_wen := Mux(trans_done,true.B,Mux(im2col_mem_raddr === 7.U && im2col_mem_sel === 3.U,false.B,ifm_wen))
  im2col_mem_raddr := Mux(im2col_begin,0.U,Mux(ifm_wen && im2col_mem_sel === 3.U, im2col_mem_raddr+1.U, im2col_mem_raddr))
  im2col_mem_sel := Mux(im2col_begin, 0.U, Mux(ifm_wen, im2col_mem_sel+1.U, im2col_mem_sel))
  val ifm_waddr = RegInit(0.U(log2Ceil(ifm_buffer_size).W))
  val ifm_sel = RegInit(0.U(1.W))
  val ifm_sel_flag = ifm_waddr(4,0)===31.U & ifm_wen_t1
  ifm_sel := Mux(ifm_sel_flag,!ifm_sel,ifm_sel)

  when(start|task_done){
    ifm_waddr := 0.U
  }.elsewhen(!ifm_sel.asBool & ifm_sel_flag){
    ifm_waddr := ifm_waddr - 31.U
  }.elsewhen(ifm_wen_t1){
    ifm_waddr := ifm_waddr+1.U
  }

  io.ifm_w_port.wen := ifm_wen_t1
  io.ifm_w_port.waddr := Cat(ifm_waddr,ifm_sel)
  val im2col_mem_sel_t1 = RegNext(im2col_mem_sel)
  val ifm_wdata = Wire(UInt(ifm_buffer_width.W))
  dontTouch(ifm_wdata)
  ifm_wdata := 0.U
  for (j <- 0 until 4) {
    when(im2col_mem_sel_t1 === j.U) {
      ifm_wdata := (for (i <- 0 until 32) yield {im2col_mem(i).rdata(8 * j + 7, 8 * j)}).reverse.reduce(Cat(_, _))
      //ifm_mem: high bit cached high channel data
    }
  }
  io.ifm_w_port.wdata := ifm_wdata

  //update cache whc
  //gemm must be clear here!!!!
  val ifm_wen_down = fallEdge(ifm_wen)
  val ifm_wen_up = riseEdge(ifm_wen)
  val cnr_w_cnt_t = cur_w_cnt === ifm_w - 1.U
  val cur_h_cnt_t = cur_h_cnt === ifm_h - 1.U
  cur_c_cnt := Mux(ifm_wen_up, cur_c_cnt+32.U, Mux(ifm_wen_down && cur_c_cnt === ifm_c_align, 0.U, cur_c_cnt))
  cur_c_cnt_t := Mux(ifm_wen_down, cur_c_cnt, cur_c_cnt_t)
  cur_c_cnt_t := Mux(ifm_wen_down, cur_c_cnt, cur_c_cnt_t)
  when(start | task_done | io.clr){
    cur_w_cnt := 0.U
    cur_h_cnt := 0.U
  }.elsewhen(cur_c_cnt === ifm_c_align && ifm_wen_t1){
    when(cnr_w_cnt_t){
      cur_w_cnt := Mux(cur_h_cnt_t, cur_w_cnt, 0.U)
      cur_h_cnt := Mux(cur_h_cnt_t, cur_h_cnt, cur_h_cnt + 1.U)
    }.otherwise{
      cur_w_cnt := cur_w_cnt + 1.U
    }
  }

  val cur_update = ifm_wen_down && cur_c_cnt === ifm_c_align  //must be clear here!!!
  cur_w := Mux(cur_update, cur_w_cnt, cur_w)
  cur_h := Mux(cur_update, cur_h_cnt, cur_h)
  cur_c := Mux(cur_update, cur_c_cnt, cur_c)

  io.cache_valid := (cur_w | cur_h | cur_c) =/= 0.U

  io.cache_whc.w := cur_w
  io.cache_whc.h := cur_h
  io.cache_whc.c := cur_c

  load_done := ifm_wen_down
  task_done := io.cache_valid & !ifm_wen & (cur_h > ifm_h - 1.U |
                        (cur_h === ifm_h - 1.U & cur_w > ifm_w -1.U) | (cur_h === ifm_h - 1.U & cur_w === ifm_w - 1.U & cur_c === ifm_c_align))
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
    val clr = Input(Bool())
    //dma
    val dma_ch0_rdata = new dmaRData_io(dmaDataWidth)
    val dma_ch0_rareq = new dmaCtrl_io(dmaSizeWidth, dmaAddrWidth)
    val dma_ch0_rbusy = Input(Bool())
    val dma_ch0_rid = Input(UInt(log2Ceil(id.values.toList.max+1).W))
    //ifm
    val ifm_read_port = new ifm_r_io(ifm_buffer_size, ifm_buffer_width*2)
    val cache_whc = Flipped(new whc)
    val cache_valid = Output(Bool())
    val task_done = Output(Bool())
  })

  val int8_ifm_and_mat32_unit = Module(new int8_ifm_and_mat32_unit)
  val fp32_ifm_unit = Module(new fp32_ifm_unit)
  val ifm_mem0 = TPRAM(ifm_buffer_width,ifm_buffer_size,"ultra")
  val ifm_mem1 = TPRAM(ifm_buffer_width,ifm_buffer_size,"ultra")

  val is_Int8_ifm_mat32 = io.im2col_format =/= 2.U
  val is_fp32_ifm = io.im2col_format === 2.U
  val ifm_mem_port = Mux(is_Int8_ifm_mat32,int8_ifm_and_mat32_unit.io.ifm_w_port,fp32_ifm_unit.io.ifm_w_port)

  //ifm_mem connect
  ifm_mem0.clock := clock
  ifm_mem1.clock := clock
  ifm_mem0.ren := io.ifm_read_port.ren
  ifm_mem1.ren := io.ifm_read_port.ren
  ifm_mem0.raddr := io.ifm_read_port.raddr
  ifm_mem1.raddr := io.ifm_read_port.raddr
  io.ifm_read_port.rdata := Cat(ifm_mem1.rdata,ifm_mem0.rdata)
  ifm_mem0.wen := ifm_mem_port.wen & !ifm_mem_port.waddr(0)
  ifm_mem1.wen := ifm_mem_port.wen & ifm_mem_port.waddr(0)
  ifm_mem0.waddr := ifm_mem_port.waddr(ifm_mem_port.waddr.getWidth-1,1)
  ifm_mem1.waddr := ifm_mem_port.waddr(ifm_mem_port.waddr.getWidth-1,1)
  ifm_mem0.wdata := ifm_mem_port.wdata
  ifm_mem1.wdata := ifm_mem_port.wdata

  //int8_ifm_and_mat32_unit connect
  int8_ifm_and_mat32_unit.io.dma_rdata := io.dma_ch0_rdata
  int8_ifm_and_mat32_unit.io.dma_rbusy := io.dma_ch0_rbusy
  int8_ifm_and_mat32_unit.io.dma_rid := io.dma_ch0_rid
  int8_ifm_and_mat32_unit.io.start := io.im2col_en && is_Int8_ifm_mat32
  int8_ifm_and_mat32_unit.io.mode := io.im2col_format
  int8_ifm_and_mat32_unit.io.ifm_dma_addr := io.ch0_src0_addr
  int8_ifm_and_mat32_unit.io.ifm_whc := io.ch0_whc

  //fp32_ifm_unit connect
  fp32_ifm_unit.io.dma_rdata := io.dma_ch0_rdata
  fp32_ifm_unit.io.dma_rbusy := io.dma_ch0_rbusy
  fp32_ifm_unit.io.dma_rid := io.dma_ch0_rid
  fp32_ifm_unit.io.start := io.im2col_en && is_fp32_ifm
  fp32_ifm_unit.io.ifm_whc := io.ch0_whc
  fp32_ifm_unit.io.ifm_cstep := io.ch0_cstep
  fp32_ifm_unit.io.ifm_dma_addr := io.ch0_src0_addr
  fp32_ifm_unit.io.ifm_quant_scale := io.quant_scale

  io.cache_whc.w := Mux(io.im2col_format(1),fp32_ifm_unit.io.cache_whc.w,int8_ifm_and_mat32_unit.io.cache_whc.w)
  io.cache_whc.h := Mux(io.im2col_format(1),fp32_ifm_unit.io.cache_whc.h,int8_ifm_and_mat32_unit.io.cache_whc.h)
  io.cache_whc.c := Mux(io.im2col_format(1),fp32_ifm_unit.io.cache_whc.c,int8_ifm_and_mat32_unit.io.cache_whc.c)
  io.task_done := Mux(io.im2col_format === 2.U,fp32_ifm_unit.io.task_done,int8_ifm_and_mat32_unit.io.task_done)

  when(is_Int8_ifm_mat32){
    io.dma_ch0_rareq <> int8_ifm_and_mat32_unit.io.dma_rareq
  }.otherwise{
    io.dma_ch0_rareq <> fp32_ifm_unit.io.dma_rareq
  }

  fp32_ifm_unit.io.clr := io.clr
  int8_ifm_and_mat32_unit.io.clr := io.clr
  io.cache_valid := Mux(is_Int8_ifm_mat32, int8_ifm_and_mat32_unit.io.cache_valid, fp32_ifm_unit.io.cache_valid)
}
