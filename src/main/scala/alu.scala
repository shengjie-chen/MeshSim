import chisel3._
import chisel3.util._

/* *************************************************************
 *  alu module, only for data
 * *************************************************************/
class alu_data_cell[T <: Data](InputType : T, OutputType : T)(implicit arithmetic: Arithmetic[T]) extends Module with alu_mat_config{
  import arithmetic._
  val io = IO(new Bundle() {
    val dataIn0 = Input(InputType)
    val dataIn1 = Input(InputType)
    val op = Input(UInt(6.W))
    val dataOut = Output(OutputType)
  })
//  io.dataOut := (0.U).asTypeOf(OutputType)
  io.dataOut := DontCare
  switch(io.op){
    is(alu_add_id)  {if(alu_add_en) io.dataOut := io.dataIn0 + io.dataIn1 else io.dataOut := DontCare}
    is(alu_mul_id)  {if(alu_mul_en) io.dataOut := io.dataIn0 * io.dataIn1 else io.dataOut := DontCare}
    is(alu_abs_id)  {if(alu_abs_en) io.dataOut := io.dataIn0.abs else io.dataOut := DontCare}
  }
}

/* *************************************************************
 *  alu stream : if change int32 and fp32 unit,
                 the valid signal must be modified!
 * *************************************************************/
class alu_stream extends Module with alu_mat_config {
  val io = IO(new Bundle() {
    val data_i_0 = Input(UInt(ALU_DATA_WIDTH.W))
    val data_i_1 = Input(UInt(ALU_DATA_WIDTH.W))
    val valid_i = Input(Bool())
    val last_i = Input(Bool())
    val op = Input(UInt(6.W))
    val data_type = Input(UInt(3.W))
    val data_o = Output(UInt(ALU_DATA_WIDTH.W))
    val valid_o = Output(Bool())
    val last_o = Output(Bool())
  })
  val alu_int32 = Seq.fill(ALU_DATA_WIDTH/32)(Module(new alu_data_cell(SInt(32.W),SInt(32.W))).io)
  for(i <- 0 until ALU_DATA_WIDTH/32){
    alu_int32(i).op := io.op
    alu_int32(i).dataIn0 := io.data_i_0(32*(i+1)-1,32*i).asSInt
    alu_int32(i).dataIn1 := io.data_i_1(32*(i+1)-1,32*i).asSInt
  }

  val alu_float32 = Seq.fill(ALU_DATA_WIDTH/32)(Module(new alu_data_cell(Float(exp_width=8,sig_width=24),Float(exp_width=8,sig_width=24))).io)
  for (i <- 0 until ALU_DATA_WIDTH / 32) {
    alu_float32(i).op := io.op
    val dataIn0 = Wire(Float(exp_width=8,sig_width=24))
    val dataIn1 = Wire(Float(exp_width=8,sig_width=24))
    dataIn0.bits := io.data_i_0(32 * (i + 1) - 1, 32 * i)
    dataIn1.bits := io.data_i_1(32 * (i + 1) - 1, 32 * i)
    alu_float32(i).dataIn0 := dataIn0
    alu_float32(i).dataIn1 := dataIn1
  }

  io.data_o := 0.U
  io.valid_o := false.B
  val last_o = RegInit(false.B)
  io.last_o := last_o
  switch(io.data_type){
    is(alu_format_int32) {
      io.data_o := (for(i <- 0 until ALU_DATA_WIDTH / 32) yield {
        alu_int32(i).dataOut.asUInt
      }).reverse.reduce(Cat(_,_))
      io.valid_o := RegNext(io.valid_i)
      io.last_o := RegNext(io.last_i)
    }
    is(alu_format_float32) {
      io.data_o := (for(i <- 0 until ALU_DATA_WIDTH / 32) yield {
        alu_float32(i).dataOut.bits
      }).reverse.reduce(Cat(_,_))
      io.valid_o := Mux(io.op === alu_abs_id,RegNext(io.valid_i),ShiftRegister(io.valid_i,2))
      io.last_o := Mux(io.op === alu_abs_id,RegNext(io.last_i),ShiftRegister(io.last_i,2))
    }
  }
}

/* *************************************************************
 *  support one src or two src stream cal
 *  one alu_cell is only use one dma r & w channel
 *  veclen : the number of elements
 * *************************************************************/
class alu_cell extends Module with alu_mat_config{
  val io = IO(new Bundle() {
    //ctrl reg
    val start = Input(Bool())
    val op = Input(UInt(6.W))
    val data_type = Input(UInt(3.W))
    val veclen = Input(UInt(32.W))
    val src0_addr = Input(UInt(dmaAddrWidth.W))
    val src1_addr = Input(UInt(dmaAddrWidth.W))
    val dst_addr = Input(UInt(dmaAddrWidth.W))
    //dma read channel
    val rid = Input(UInt(log2Ceil(id.values.toList.max+1).W))
    val dmaRbusy = Input(Bool())
    val rareq = new dmaCtrl_io(dmaSizeWidth, dmaAddrWidth)
    val rdata = new dmaRData_io(dmaDataWidth)
    //dma write channel
    val wid = Input(UInt(log2Ceil(id.values.toList.max+1).W))
    val dmaWBusy = Input(Bool())
    val wareq = new dmaCtrl_io(dmaSizeWidth, dmaAddrWidth)
    val wdata = new dmaWData_io(dmaDataWidth)
    //ctrl signal
    val task_done = Output(Bool())
  })
  val start_t1 = riseEdge(io.start)
  val op = RegEnable(io.op,0.U,start_t1)
  val data_type = RegEnable(io.data_type,0.U,start_t1)
  //io.veclen align 4
  val dmalen = RegEnable(((io.veclen+(dmaDataWidth/32-1).U)&(-(dmaDataWidth/32)).S(32.W).asUInt)(31,log2Ceil(dmaDataWidth/32)),0.U(30.W),start_t1)
  val src_num = op =/= alu_abs_id  //0 -> only one  1-> two src

  val src2_mem = TPRAM(dmaDataWidth,ALU_BURST_LEN,"block")
  val dst_mem = TPRAM(dmaDataWidth,ALU_BURST_LEN,"block")
  src2_mem.clock := clock
  dst_mem.clock := clock
  val src2_mem_full = RegInit(false.B)
  val src2_mem_empty = RegInit(true.B)
  val dst_mem_full = RegInit(false.B)
  val dst_mem_empty = RegInit(true.B)

  val task_done = RegInit(false.B)
  val en = RegInit(false.B)
  en := Mux(start_t1,true.B,Mux(task_done,false.B,en))

  //generate dma the last data flag
  val dma_r_count = RegInit(0.U(32.W))
  val dma_r_last = dma_r_count === io.rareq.dmaSize - 1.U & io.rdata.valid
  dma_r_count := Mux((io.rareq.dmaAreq & io.rareq.dmaEn)|dma_r_last,0.U,Mux(io.rdata.valid & io.rareq.dmaEn,dma_r_count+1.U,dma_r_count))
  val dma_w_count = RegInit(0.U(32.W))
  val dma_w_last = dma_w_count === io.wareq.dmaSize - 1.U & io.wdata.valid
  dma_w_count := Mux((io.wareq.dmaAreq & io.wareq.dmaEn)|dma_w_last,0.U,Mux(io.wdata.valid & io.wareq.dmaEn,dma_w_count+1.U,dma_w_count))

  val alu_stream = Module(new alu_stream)

  //-------------------------src2_mem write (src2 read)-------------------------------
  val src1_baseaddr = RegEnable(io.src0_addr,0.U,start_t1)
  val src2_baseaddr = RegEnable(io.src1_addr,0.U,start_t1)
  val dma_recv_cnt = RegInit(0.U((30+1-log2Ceil(ALU_BURST_LEN)).W)) // Cat(cnt,sel)
  dma_recv_cnt := Mux(start_t1|task_done,0.U,Mux(dma_r_last,dma_recv_cnt+1.U,dma_recv_cnt))
  //dma_recv_cnt_t : every src_data trans times
  val dma_recv_cnt_t = Mux(src_num,Cat(0.U(1.W),dma_recv_cnt(dma_recv_cnt.getWidth-1,1)),dma_recv_cnt)
  val recv_done_cnt = Cat(dma_recv_cnt_t,0.U(log2Ceil(ALU_BURST_LEN).W))
  val recv_rest_cnt = dmalen - recv_done_cnt
  val dma_src_trans_finish = recv_rest_cnt.asSInt < 0.S
  //src_sel : 0 -> src1_data  1 -> src2_data
  val src_sel = Mux(src_num,dma_recv_cnt(0).asBool,true.B)
  val src_sel_t = RegNext(src_sel)
  //generate src2_data  dma require
  io.rareq.dmaSize := Mux(recv_rest_cnt(recv_rest_cnt.getWidth-1,log2Ceil(ALU_BURST_LEN)) > 0.U,ALU_BURST_LEN.U,recv_rest_cnt)
  io.rareq.dmaAddr := Mux(src_sel,src2_baseaddr+Cat(recv_done_cnt,0.U(log2Ceil(dmaDataWidth/8).W)),src1_baseaddr+Cat(recv_done_cnt,0.U(log2Ceil(dmaDataWidth/8).W)))
  val dma_r_src2_en = RegInit(false.B)
  dma_r_src2_en := Mux(src2_mem_empty & src_num & en & !dma_src_trans_finish,true.B,Mux(dma_r_last,false.B,dma_r_src2_en))
  val dma_r_src2_req = RegInit(false.B)
  dma_r_src2_req := Mux(!io.dmaRbusy & dma_r_src2_en & src2_mem_empty,true.B,Mux(io.dmaRbusy,false.B,dma_r_src2_req))
  //cache src2_data to src2_mem
  val src2_mem_waddr = RegInit(0.U(log2Ceil(ALU_BURST_LEN).W))
  src2_mem_waddr := Mux(start_t1, 0.U, Mux(src2_mem.wen, src2_mem_waddr + 1.U, src2_mem_waddr))
  src2_mem.wen := io.rdata.valid && !src2_mem_full && src_num
  src2_mem.waddr := src2_mem_waddr
  src2_mem.wdata := io.rdata.data
  val src2_cal_finish = src_sel & dma_r_last
  src2_mem_full := Mux(dma_r_last,true.B,Mux(start_t1|alu_stream.io.last_o,false.B,src2_mem_full))
  src2_mem_empty := Mux(start_t1|alu_stream.io.last_o,true.B,Mux(io.rid === id("alu").U & !src_sel & dma_r_src2_req,false.B,src2_mem_empty))

  //-------------------------------------alu_stream-------------------------------------
  val src_ready = !src_num | (src_num & src2_mem_full)
  alu_stream.io.op := op
  alu_stream.io.data_type := data_type
  alu_stream.io.valid_i := src_ready && RegNext(io.rdata.valid) & src_sel_t
  alu_stream.io.data_i_0 := RegNext(io.rdata.data)
  alu_stream.io.data_i_1 := src2_mem.rdata
  alu_stream.io.last_i := RegNext(dma_r_last) & src_sel_t
  //from src2_mem to read src2_data
  src2_mem.ren := src_ready & io.rdata.valid & src2_mem_full
  val src2_mem_raddr = RegInit(0.U(log2Ceil(ALU_BURST_LEN).W))
  src2_mem_raddr := Mux(src2_cal_finish | start_t1 | task_done, 0.U, Mux(src2_mem.ren, src2_mem_raddr + 1.U, src2_mem_raddr))
  src2_mem.raddr := src2_mem_raddr
  //from dma to read src1_data
  val dma_r_src1_en = RegInit(false.B)
  dma_r_src1_en := Mux(dst_mem_empty & src_ready & en & !dma_src_trans_finish,true.B,Mux(dst_mem_full,false.B,dma_r_src1_en))
  val dma_r_src1_req = RegInit(false.B)
  dma_r_src1_req := Mux(!io.dmaRbusy && dma_r_src1_en && dst_mem_empty,true.B,Mux(io.dmaRbusy,false.B,dma_r_src1_req))
  io.rareq.dmaEn := Mux(src_sel,dma_r_src1_en,dma_r_src2_en)
  io.rareq.dmaAreq := Mux(src_sel,dma_r_src1_req,dma_r_src2_req)

  //-------------------------------------dst_mem write-------------------------------------
  dst_mem.wen := !dst_mem_full && alu_stream.io.valid_o
  val dst_mem_waddr = RegInit(0.U(log2Ceil(ALU_BURST_LEN).W))
  dst_mem_waddr := Mux(start_t1|task_done,0.U,Mux(dst_mem.wen,dst_mem_waddr+1.U,dst_mem_waddr))
  dst_mem.waddr := dst_mem_waddr
  dst_mem.wdata := alu_stream.io.data_o

  //-------------------------------------dst_mem read-------------------------------------
  //dst_data write to dma
  val dst_baseaddr = RegEnable(io.dst_addr,0.U,start_t1)
  val dma_wbusy_down = fallEdge(io.dmaWBusy)
  val send_cnt = RegInit(0.U((30-log2Ceil(ALU_BURST_LEN)).W))
  send_cnt := Mux(task_done|start_t1,0.U,Mux(dma_w_last,send_cnt+1.U,send_cnt))
  val send_done_cnt = Cat(send_cnt,0.U(log2Ceil(ALU_BURST_LEN).W))
  val send_rest_cnt = dmalen - send_done_cnt
  io.wareq.dmaSize := Mux(send_rest_cnt(send_rest_cnt.getWidth-1,log2Ceil(ALU_BURST_LEN))>0.U,ALU_BURST_LEN.U,send_rest_cnt)
  io.wareq.dmaAddr := dst_baseaddr + Cat(send_done_cnt,0.U(log2Ceil(dmaDataWidth/8).W))
  io.wareq.dmaEn := dst_mem_full & en
  val dmawAreq = RegInit(false.B)
  dmawAreq := Mux(io.wareq.dmaEn & !io.dmaWBusy & io.wid === id("alu").U,true.B,Mux(dmawAreq&io.dmaWBusy,false.B,dmawAreq))
  io.wareq.dmaAreq := dmawAreq
  //dst_mem flag generate
  val dst_mem_trans_finish = dma_w_last & en
  val dst_mem_cal_finish = alu_stream.io.last_o & en
  val dst_mem_cal_finish_t2 = ShiftRegister(dst_mem_cal_finish,2)
  dst_mem_empty := Mux(start_t1 | dst_mem_trans_finish, true.B, Mux(dst_mem.wen, false.B, dst_mem_empty))
  dst_mem_full := Mux(start_t1 | dst_mem_trans_finish, false.B, Mux(dst_mem_cal_finish, true.B, dst_mem_full))
  //dst_mem read
  dst_mem.ren := dst_mem_full
  val dst_mem_raddr = RegInit(0.U(log2Ceil(ALU_BURST_LEN).W))
  dst_mem_raddr := Mux(start_t1|task_done|dma_w_last,0.U,Mux(io.wdata.valid | dst_mem_cal_finish_t2,dst_mem_raddr+1.U,dst_mem_raddr))
  dst_mem.raddr := dst_mem_raddr
  val dst_mem_first_data = RegEnable(dst_mem.rdata, 0.U, dst_mem_cal_finish_t2)
  io.wdata.data := Mux(dst_mem_raddr===1.U,dst_mem_first_data,dst_mem.rdata)

  task_done := send_rest_cnt.asSInt <= 0.S && RegNext(dma_wbusy_down)
  io.task_done := task_done
}

/* *************************************************************
 *  support one channel or two channel,which can be setted
    with channel_en
    and when use one channel, which channl can be chosed
 * *************************************************************/
class alu_mat extends Module with alu_mat_config{
  val io = IO(new Bundle() {
    //ctrl registers
    val ch0_src0_addr = Input(UInt(dmaAddrWidth.W))
    val ch0_src1_addr = Input(UInt(dmaAddrWidth.W))
    val ch0_dst_addr = Input(UInt(dmaAddrWidth.W))
    val ch1_src0_addr = Input(UInt(dmaAddrWidth.W))
    val ch1_src1_addr = Input(UInt(dmaAddrWidth.W))
    val ch1_dst_addr = Input(UInt(dmaAddrWidth.W))
    val alu_en = Input(Bool())
    val alu_op = Input(UInt(6.W))
    val alu_type = Input(UInt(3.W))
    val alu_channels = Input(UInt(2.W))
    val alu_veclen_0 = Input(UInt(32.W))
    val alu_veclen_1 = Input(UInt(32.W))

    //dma
    val dma_rid_ch0 = Input(UInt(log2Ceil(id.values.toList.max+1).W))
    val dma_rbusy_ch0 = Input(Bool())
    val dma_rareq_ch0 = new dmaCtrl_io(dmaSizeWidth, dmaAddrWidth)
    val dma_rdata_ch0 = new dmaRData_io(dmaDataWidth)
    val dma_rid_ch1 = Input(UInt(log2Ceil(id.values.toList.max+1).W))
    val dma_rbusy_ch1 = Input(Bool())
    val dma_rareq_ch1 = new dmaCtrl_io(dmaSizeWidth, dmaAddrWidth)
    val dma_rdata_ch1 = new dmaRData_io(dmaDataWidth)
    val dma_wid_ch0 = Input(UInt(log2Ceil(id.values.toList.max+1).W))
    val dma_wbusy_ch0 = Input(Bool())
    val dma_wareq_ch0 = new dmaCtrl_io(dmaSizeWidth, dmaAddrWidth)
    val dma_wdata_ch0 = new dmaWData_io(dmaDataWidth)
    val dma_wid_ch1 = Input(UInt(log2Ceil(id.values.toList.max+1).W))
    val dma_wbusy_ch1 = Input(Bool())
    val dma_wareq_ch1 = new dmaCtrl_io(dmaSizeWidth, dmaAddrWidth)
    val dma_wdata_ch1 = new dmaWData_io(dmaDataWidth)
    //intr
    val task_done = Output(Bool())
  })

  val alu_cell_0 = Module(new alu_cell)
  val alu_cell_1 = Module(new alu_cell)
  alu_cell_0.io.start := io.alu_en && io.alu_channels(0).asBool
  alu_cell_0.io.op := io.alu_op
  alu_cell_0.io.data_type := io.alu_type
  alu_cell_0.io.veclen := io.alu_veclen_0
  alu_cell_0.io.src0_addr := io.ch0_src0_addr
  alu_cell_0.io.src1_addr := io.ch0_src1_addr
  alu_cell_0.io.dst_addr := io.ch0_dst_addr
  alu_cell_0.io.rid := io.dma_rid_ch0
  alu_cell_0.io.dmaRbusy := io.dma_rbusy_ch0
  alu_cell_0.io.rdata <> io.dma_rdata_ch0
  alu_cell_0.io.rareq <> io.dma_rareq_ch0
  alu_cell_0.io.dmaWBusy := io.dma_wbusy_ch0
  alu_cell_0.io.wid := io.dma_wid_ch0
  io.dma_wdata_ch0 <> alu_cell_0.io.wdata
  io.dma_wareq_ch0 <> alu_cell_0.io.wareq

  alu_cell_1.io.start := io.alu_en && io.alu_channels(1).asBool
  alu_cell_1.io.op := io.alu_op
  alu_cell_1.io.data_type := io.alu_type
  alu_cell_1.io.veclen := io.alu_veclen_1
  alu_cell_1.io.src0_addr := io.ch1_src0_addr
  alu_cell_1.io.src1_addr := io.ch1_src1_addr
  alu_cell_1.io.dst_addr := io.ch1_dst_addr
  alu_cell_1.io.rid := io.dma_rid_ch1
  alu_cell_1.io.dmaRbusy := io.dma_rbusy_ch1
  alu_cell_1.io.rdata <> io.dma_rdata_ch1
  alu_cell_1.io.rareq <> io.dma_rareq_ch1
  alu_cell_1.io.dmaWBusy := io.dma_wbusy_ch1
  alu_cell_1.io.wid := io.dma_wid_ch1
  io.dma_wdata_ch1 <> alu_cell_1.io.wdata
  io.dma_wareq_ch1 <> alu_cell_1.io.wareq

  val task_done_cell0 = RegInit(false.B)
  val task_done_cell1 = RegInit(false.B)
  task_done_cell0 := Mux(io.alu_en === 0.U,false.B,Mux(io.alu_channels(0) && riseEdge(alu_cell_0.io.task_done),true.B,task_done_cell0))
  task_done_cell1 := Mux(io.alu_en === 0.U,false.B,Mux(io.alu_channels(1) && riseEdge(alu_cell_1.io.task_done),true.B,task_done_cell1))
  val task_done = RegInit(false.B)
  task_done := MuxLookup(io.alu_channels,false.B,Array(
    1.U -> task_done_cell0,
    2.U -> task_done_cell1,
    3.U -> (task_done_cell0 & task_done_cell1)
  ))
  io.task_done := task_done
}