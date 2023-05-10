//import chisel3._
//import chisel3.stage.ChiselGeneratorAnnotation
//import chisel3.util._
//
//trait pool_config {
//
//  /*-------------*/
//  val pool_out_buffer_size = 1024 //  16 KiB
//  // val pool_out_buffer_width = 128  // 16 Bytes
//  val pool_out_buffer_ram_style = "block"
//  val pool_out_buffer_fdma_size = 256
//  val pool_h_ram_dep = 256 // at least need max(ifm_w)/4
//}
//
//class pool extends Module with dma_config with pool_config {
//  val io = IO(new Bundle {
//    /*---- General Registers ----*/
//    val shape0 = new whc
//    val shape1 = new whc
//    val src0_addr0 = Input(UInt(dmaAddrWidth.W))
//    val dst_addr0 = Input(UInt(dmaAddrWidth.W))
//    val src0_addr1 = Input(UInt(dmaAddrWidth.W))
//    val dst_addr1 = Input(UInt(dmaAddrWidth.W))
//
//    /*---- Pool Registers ----*/
//    val en = Input(Bool())
//    val pool_type = Input(UInt(2.W))
//    val channels = Input(UInt(2.W))
//    val kernel_w = Input(UInt(2.W))
//    val kernel_h = Input(UInt(2.W))
//    val stride_w = Input(UInt(2.W))
//    val stride_h = Input(UInt(2.W))
//    val pad_mode = Input(UInt(1.W))
//    val pad_left = Input(UInt(2.W))
//    val pad_right = Input(UInt(2.W))
//    val pad_top = Input(UInt(2.W))
//    val pad_bottom = Input(UInt(2.W))
//
//    /*-- FDMA interface --*/
//    //dma read channel
//    val ch0_rid = Input(UInt(id.values.toList.max.W))
//    val ch0_rbusy = Input(Bool())
//    val ch0_rareq = new dmaCtrl_io(dmaSizeWidth, dmaAddrWidth)
//    val ch0_rdata = new dmaRData_io(dmaDataWidth)
//    val ch1_rid = Input(UInt(id.values.toList.max.W))
//    val ch1_rbusy = Input(Bool())
//    val ch1_rareq = new dmaCtrl_io(dmaSizeWidth, dmaAddrWidth)
//    val ch1_rdata = new dmaRData_io(dmaDataWidth)
//    //dma write channel
//    val ch0_wid = Input(UInt(id.values.toList.max.W))
//    val ch0_wbusy = Input(Bool())
//    val ch0_wareq = new dmaCtrl_io(dmaSizeWidth, dmaAddrWidth)
//    val ch0_wdata = new dmaWData_io(dmaDataWidth)
//    val ch1_wid = Input(UInt(id.values.toList.max.W))
//    val ch1_wbusy = Input(Bool())
//    val ch1_wareq = new dmaCtrl_io(dmaSizeWidth, dmaAddrWidth)
//    val ch1_wdata = new dmaWData_io(dmaDataWidth)
//
//    /*-- control signal --*/
//    val task_done = Output(Bool())
//  })
//
//  val ofm_w = (io.shape0.w + io.pad_left + io.pad_right - io.kernel_w) / io.stride_w + 1.U
//  val ofm_h = (io.shape0.h + io.pad_top + io.pad_bottom - io.kernel_h) / io.stride_h + 1.U
//
//  val pool_unit_0 = Module(new Pool_Unit)
//  val pool_unit_1 = Module(new Pool_Unit)
//
//  pool_unit_0.io.ofm_w := ofm_w
//  pool_unit_0.io.ofm_h := ofm_h
//  pool_unit_1.io.ofm_w := ofm_w
//  pool_unit_1.io.ofm_h := ofm_h
//
//  /*-- Pool 0 --*/
//  /*---- Pool Registers ----*/
//  pool_unit_0.io.pool_start := riseEdge(io.en) && io.channels(0).asBool
//  pool_unit_0.io.pool_type := io.pool_type(0)
//  pool_unit_0.io.pool_kernel_w := io.kernel_w(0)
//  pool_unit_0.io.pool_kernel_h := io.kernel_h(0)
//  pool_unit_0.io.stride_w := io.stride_w
//  pool_unit_0.io.stride_h := io.stride_h
//  pool_unit_0.io.pad_mode := io.pad_mode
//  pool_unit_0.io.pad_left := io.pad_left
//  pool_unit_0.io.pad_right := io.pad_right
//  pool_unit_0.io.pad_top := io.pad_top
//  pool_unit_0.io.pad_bottom := io.pad_bottom
//  /*---- General Registers ----*/
//  pool_unit_0.io.ifm_shape := io.shape0
//  pool_unit_0.io.src_addr := io.src0_addr0
//  pool_unit_0.io.dst_addr := io.dst_addr0
//  /*---- FDMA interface ----*/
//  pool_unit_0.io.rid <> io.ch0_rid
//  pool_unit_0.io.rbusy <> io.ch0_rbusy
//  pool_unit_0.io.rareq <> io.ch0_rareq
//  pool_unit_0.io.rdata <> io.ch0_rdata
//  pool_unit_0.io.wid <> io.ch0_wid
//  pool_unit_0.io.wbusy <> io.ch0_wbusy
//  pool_unit_0.io.wareq <> io.ch0_wareq
//  pool_unit_0.io.wdata <> io.ch0_wdata
//
//  /*-- Pool 1 --*/
//  /*---- Pool Registers ----*/
//  pool_unit_1.io.pool_start := riseEdge(io.en) && io.channels(1).asBool
//  pool_unit_1.io.pool_type := io.pool_type
//  pool_unit_1.io.pool_kernel_w := io.kernel_w(0)
//  pool_unit_1.io.pool_kernel_h := io.kernel_h(0)
//  pool_unit_1.io.stride_w := io.stride_w
//  pool_unit_1.io.stride_h := io.stride_h
//  pool_unit_1.io.pad_mode := io.pad_mode
//  pool_unit_1.io.pad_left := io.pad_left
//  pool_unit_1.io.pad_right := io.pad_right
//  pool_unit_1.io.pad_top := io.pad_top
//  pool_unit_1.io.pad_bottom := io.pad_bottom
//  /*---- General Registers ----*/
//  pool_unit_1.io.ifm_shape := io.shape1
//  pool_unit_1.io.src_addr := io.src0_addr1
//  pool_unit_1.io.dst_addr := io.dst_addr1
//  /*---- FDMA interface ----*/
//  pool_unit_1.io.rid <> io.ch1_rid
//  pool_unit_1.io.rbusy <> io.ch1_rbusy
//  pool_unit_1.io.rareq <> io.ch1_rareq
//  pool_unit_1.io.rdata <> io.ch1_rdata
//  pool_unit_1.io.wid <> io.ch1_wid
//  pool_unit_1.io.wbusy <> io.ch1_wbusy
//  pool_unit_1.io.wareq <> io.ch1_wareq
//  pool_unit_1.io.wdata <> io.ch1_wdata
//
//  io.task_done := io.en && riseEdge((pool_unit_0.io.task_done || !io.channels(0).asBool) && (pool_unit_1.io.task_done || !io.channels(1).asBool))
//}
//
//class Pool_Unit extends Module with dma_config with pool_config {
//  val io = IO(new Bundle {
//    /*---- Pool Registers ----*/
//    val pool_start = Input(Bool())
//    val pool_type = Input(UInt(1.W))
//    val pool_kernel_w = Input(UInt(1.W))
//    val pool_kernel_h = Input(UInt(1.W))
//
//    val stride_w = Input(UInt(2.W))
//    val stride_h = Input(UInt(2.W))
//    val pad_mode = Input(UInt(1.W))
//    val pad_left = Input(UInt(2.W))
//    val pad_right = Input(UInt(2.W))
//    val pad_top = Input(UInt(2.W))
//    val pad_bottom = Input(UInt(2.W))
//    /*---- General Registers ----*/
//    val ifm_shape = new whc
//    val ofm_w = Input(UInt(16.W))
//    val ofm_h = Input(UInt(16.W))
//    val src_addr = Input(UInt(dmaAddrWidth.W))
//    val dst_addr = Input(UInt(dmaAddrWidth.W))
//    /*---- FDMA interface ----*/
//    //dma read channel
//    val rid = Input(UInt(id.values.toList.max.W))
//    val rbusy = Input(Bool())
//    val rareq = new dmaCtrl_io(dmaSizeWidth, dmaAddrWidth)
//    val rdata = new dmaRData_io(dmaDataWidth)
//    //dma write channel
//    val wid = Input(UInt(id.values.toList.max.W))
//    val wbusy = Input(Bool())
//    val wareq = new dmaCtrl_io(dmaSizeWidth, dmaAddrWidth)
//    val wdata = new dmaWData_io(dmaDataWidth)
//
//    /*---- control signal ----*/
//    val task_done = Output(Bool())
//    val test_out = Output(Vec(4, UInt(32.W)))
//    val test_valid_out = Output(Bool())
//  })
//
//  val pool_h = Module(new Pool_H)
//  val prb_h = Module(new Pool_RAM_Buf(16, false))
//  val pool_v = Module(new Pool_V)
//  val prb_v = Module(new Pool_RAM_Buf(32, true))
//
//  val raddr = RegInit(0.U(dmaAddrWidth.W))
//  val rareq = RegInit(0.U(dmaAddrWidth.W))
//
//  val pob = Module(new Pool_Out_Buf)
//
//  val pool_busy = RegInit(false.B)
//  val task_done = RegInit(false.B)
//
//  val waddr = RegInit(0.U(dmaAddrWidth.W))
//
//  val ifm_wh = io.ifm_shape.w * io.ifm_shape.h
//
//  val offset = RegInit(0.U(2.W))
//
//  val rsize_ceil = ((io.ifm_shape.w + 3.U + offset) >> 2).asUInt
//  // val rsize_ceil = ((io.ifm_shape.w + 3.U) >> 2).asUInt
//
//  /*test*/
//  io.task_done := task_done
//  pool_h.io.data_in := 0.U
//  io.rareq.dmaSize := rsize_ceil
//  io.rareq.dmaAddr := Cat(raddr(raddr.getWidth - 1, 4), 0.U(4.W))
//  io.rareq.dmaAreq := rareq
//  io.rareq.dmaEn := pool_busy
//
//  /*test*/
//  prb_h.io.data_in := pool_h.io.out
//  prb_h.io.valid_in := pool_h.io.valid_out
//  prb_h.io.ofm_w := io.ofm_w
//
//  /*test*/
//  pool_v.io.data_in := prb_h.io.data_out
//  pool_v.io.valid_in := prb_h.io.valid_out
//  pool_v.io.ofm_w := io.ofm_w
//  pool_v.io.pad_mode := io.pad_mode
//  pool_v.io.pad_top := io.pad_top
//  pool_v.io.pad_bottom := io.pad_bottom
//  pool_v.io.pool_kernel_h := io.pool_kernel_h
//  pool_v.io.pool_type := io.pool_type
//  pool_v.io.stride_h := io.stride_h
//  pool_v.io.ifm_h := io.ifm_shape.h
//  io.test_valid_out := pool_v.io.valid_out
//
//  /*test*/
//  prb_v.io.data_in := pool_v.io.out
//  prb_v.io.valid_in := pool_v.io.valid_out
//  prb_v.io.ofm_w := io.ofm_w * io.ofm_h
//
//  val pmb = Module(new Pool_Mult_Buf)
//
//  pmb.io.valid_in := prb_v.io.valid_out
//  pmb.io.data_in := prb_v.io.data_out
//  pmb.io.input_end_in := prb_v.io.line_end.get
//  pmb.io.factor := Cat(io.pool_kernel_w, io.pool_kernel_h)
//  pmb.io.pool_type := io.pool_type
//
//  pob.io.valid_in := pmb.io.valid_out
//  pob.io.data_in := pmb.io.data_out
//  pob.io.input_end := pmb.io.input_end_out
//
//  io.wdata <> pob.io.wdata
//  io.wareq.dmaSize := pob.io.wsize
//  io.wareq.dmaEn := pob.io.wen
//  io.wareq.dmaAddr := waddr
//  pob.io.wbusy := io.wbusy
//  io.wareq.dmaAreq := pob.io.wareq
//
//  val c_count = RegInit(0.U(16.W))
//
//  io.test_out(0) := Mux(prb_v.io.valid_out, prb_v.io.data_out(31, 0), 0.U)
//  io.test_out(1) := Mux(prb_v.io.valid_out, prb_v.io.data_out(63, 32), 0.U)
//  io.test_out(2) := Mux(prb_v.io.valid_out, prb_v.io.data_out(95, 64), 0.U)
//  io.test_out(3) := Mux(prb_v.io.valid_out, prb_v.io.data_out(127, 96), 0.U)
//  io.test_valid_out := prb_v.io.valid_out
//  dontTouch(io.test_out)
//  dontTouch(io.test_valid_out)
//
//  when(io.pool_start) {
//    pool_busy := true.B
//    raddr := io.src_addr
//    c_count := 0.U
//    waddr := io.dst_addr
//  }
//
//  val r0 :: r1 :: r2 :: r3 :: nil = Enum(4)
//  val state = RegInit(r0)
//
//  val line_start = RegInit(false.B)
//  pool_h.io.line_start := line_start
//  val ph_valid_in = RegInit(0.U(3.W))
//
//  val rcount = RegInit(0.U(32.W))
//  val h_count = RegInit(0.U(16.W))
//  val mat_pad_wh = -ifm_wh(1, 0)
//
//  val pib = Module(new Pool_in_buf)
//  pib.io.offset := offset
//  pib.io.ifm_w := io.ifm_shape.w
//  pib.io.data_in := 0.U
//
//  pib.io.valid_in := io.rdata.valid
//  pib.io.data_in := RegNext(io.rdata.data)
//
//  pool_h.io.pool_type := io.pool_type
//  pool_h.io.pool_kernel_w := io.pool_kernel_w
//  pool_h.io.stride_w := io.stride_w
//  pool_h.io.pad_mode := io.pad_mode
//  pool_h.io.pad_left := io.pad_left
//  pool_h.io.pad_right := io.pad_right
//  pool_h.io.valid_in := pib.io.valid_out
//  pool_h.io.data_in := pib.io.data_out
//
//  dontTouch(pib.io.valid_out)
//  dontTouch(pib.io.data_out)
//
//  when(pool_busy) {
//    when(c_count < io.ifm_shape.c) {
//      io.rareq.dmaEn := true.B
//      switch(state) {
//        is(r0) {
//          when(!io.rbusy && io.rid === id("pool").U) {
//            rareq := true.B
//            offset := raddr(3, 2)
//            state := r1
//            rcount := 0.U
//          }
//        }
//        is(r1) {
//          when(io.rbusy && io.rid === id("pool").U) {
//            rareq := false.B
//            state := r2
//            line_start := true.B
//          }
//        }
//        is(r2) {
//          // pool_h.io.data_in := RegNext(io.rdata.data)
//          when(rcount === rsize_ceil - 1.U) { // last
//            ph_valid_in := Mux(io.rdata.valid, Mux(io.ifm_shape.w(1, 0) === 0.U, 4.U, io.ifm_shape.w(1, 0)), 0.U)
//            line_start := ~io.rdata.valid
//            raddr := raddr + (io.ifm_shape.w << 2)
//          }.otherwise {
//            ph_valid_in := Mux(io.rdata.valid, 4.U, 0.U)
//          }
//          rcount := rcount + io.rdata.valid.asUInt
//          when(rcount === rsize_ceil) {
//            when(h_count === io.ifm_shape.h - 1.U) {
//              state := r3 // wait of pob end
//              h_count := 0.U
//              c_count := c_count + 1.U
//              raddr := raddr + (mat_pad_wh << 2)
//            }.otherwise {
//              state := r0 // continue
//              h_count := h_count + 1.U
//            }
//          }
//        }
//        is(r3) {
//          when(pob.io.output_end) {
//            state := r0
//          }
//        }
//      }
//    }.otherwise {
//      io.rareq.dmaEn := false.B
//      when(pob.io.output_end || io.ifm_shape.c === 0.U) {
//        pool_busy := false.B
//        task_done := true.B
//        c_count := 0.U
//        rareq := false.B
//      }
//    }
//  }
//  when(fallEdge(pob.io.wareq)) {
//    waddr := waddr + (pob.io.wsize << 4).asUInt
//  }
//}
//
//class Pool_in_buf extends Module {
//  val io = IO(new Bundle {
//    val ifm_w = Input(UInt(16.W))
//    val offset = Input(UInt(2.W))
//    val data_in = Input(UInt(128.W))
//    val valid_in = Input(Bool())
//    val data_out = Output(UInt(128.W))
//    val valid_out = Output(UInt(3.W))
//    val line_end = Output(Bool())
//  })
//
//  val data_in = RegNext(io.data_in)
//  val valid_in = RegNext(io.valid_in)
//  val valid_in_r = RegNext(valid_in)
//  val valid_in_r2 = RegNext(valid_in_r)
//
//  val wceil = ((io.ifm_w + 3.U) >> 2).asUInt
//
//  val buf_reg = RegInit(0.U(96.W))
//  val last_valid = Mux(io.ifm_w(1, 0) === 0.U, 4.U, io.ifm_w(1, 0))
//
//  val w_count = RegInit(0.U(16.W))
//  val w_count_now = Mux(valid_in, w_count + Mux(w_count === 0.U, 4.U - io.offset, 4.U), w_count)
//
//  val end = w_count >= io.ifm_w
//
//  val offset_pre = RegNext(io.offset)
//
//  val test_in_0 = data_in(31, 0)
//  val test_in_1 = data_in(63, 32)
//  val test_in_2 = data_in(95, 64)
//  val test_in_3 = data_in(127, 96)
//  val out = Wire(UInt(128.W))
//  out := 0.U
//  val test_out_0 = out(31, 0)
//  val test_out_1 = out(63, 32)
//  val test_out_2 = out(95, 64)
//  val test_out_3 = out(127, 96)
//  dontTouch(test_in_0)
//  dontTouch(test_in_1)
//  dontTouch(test_in_2)
//  dontTouch(test_in_3)
//  dontTouch(test_out_0)
//  dontTouch(test_out_1)
//  dontTouch(test_out_2)
//  dontTouch(test_out_3)
//
//  when(end) {
//    w_count := 0.U
//    io.line_end := true.B
//  }.otherwise {
//    w_count := w_count_now
//    io.line_end := false.B
//  }
//
//  val valid_out = RegInit(0.U(3.W))
//  io.data_out := 0.U
//  io.valid_out := valid_out
//
//  val out_count = RegInit(0.U(16.W))
//
//  switch(offset_pre) {
//    is(0.U) {
//      io.data_out := data_in
//      out := data_in
//      when(valid_in && out_count < wceil) {
//        when(out_count === wceil - 1.U) {
//          valid_out := last_valid
//        }.otherwise {
//          valid_out := 4.U
//        }
//        out_count := out_count + 1.U
//      }.otherwise {
//        valid_out := 0.U
//      }
//      when(out_count >= wceil) {
//        out_count := 0.U
//      }
//    }
//    is(1.U) {
//      buf_reg := data_in(127, 32)
//      io.data_out := Cat(data_in(31, 0), buf_reg)
//      out := Cat(data_in(31, 0), buf_reg)
//      when(valid_in_r && out_count < wceil) {
//        when(out_count === wceil - 1.U) {
//          valid_out := last_valid
//        }.otherwise {
//          valid_out := 4.U
//        }
//        out_count := out_count + 1.U
//      }.otherwise {
//        valid_out := 0.U
//      }
//      when(out_count >= wceil) {
//        out_count := 0.U
//      }
//    }
//    is(2.U) {
//      buf_reg := Cat(data_in(127, 64), 0.U(32.W))
//      io.data_out := Cat(data_in(63, 0), buf_reg(95, 32))
//      out := Cat(data_in(63, 0), buf_reg(95, 32))
//      when(valid_in_r && out_count < wceil) {
//        when(out_count === wceil - 1.U) {
//          valid_out := last_valid
//        }.otherwise {
//          valid_out := 4.U
//        }
//        out_count := out_count + 1.U
//      }.otherwise {
//        valid_out := 0.U
//      }
//      when(out_count >= wceil) {
//        out_count := 0.U
//      }
//    }
//    is(3.U) {
//      buf_reg := Cat(data_in(127, 96), 0.U(64.W))
//      io.data_out := Cat(data_in(95, 0), buf_reg(95, 64))
//      out := Cat(data_in(95, 0), buf_reg(95, 64))
//      when(valid_in_r && out_count < wceil) {
//        when(out_count === wceil - 1.U) {
//          valid_out := last_valid
//        }.otherwise {
//          valid_out := 4.U
//        }
//        out_count := out_count + 1.U
//      }.otherwise {
//        valid_out := 0.U
//      }
//      when(out_count >= wceil) {
//        out_count := 0.U
//      }
//    }
//  }
//}
//
//class Pool_H extends Module with pool_config with cal_cell_params {
//  val io = IO(new Bundle {
//    val data_in = Input(UInt(128.W))
//    val valid_in = Input(UInt(3.W))
//    val pool_type = Input(UInt(1.W))
//    val pool_kernel_w = Input(UInt(1.W))
//    val stride_w = Input(UInt(2.W))
//    val pad_mode = Input(UInt(1.W))
//    val pad_left = Input(UInt(2.W))
//    val pad_right = Input(UInt(2.W))
//    val line_start = Input(Bool())
//    val out = Output(Vec(4, UInt(32.W)))
//    val valid_out = Output(UInt(3.W))
//  })
//
//  io.out(0) := 0.U
//  io.out(1) := 0.U
//  io.out(2) := 0.U
//  io.out(3) := 0.U
//
//  val pad_l_en = RegInit(false.B)
//  val pad_r_en = RegInit(false.B)
//  when(riseEdge(io.line_start)) {
//    pad_l_en := true.B
//  }
//  when(fallEdge(io.line_start)) {
//    pad_r_en := true.B
//  }
//
//  val (in0, in1, in2, in3) = (io.data_in(31, 0), io.data_in(63, 32), io.data_in(95, 64), io.data_in(127, 96))
//  val last = RegInit(0.U(32.W))
//  last := Mux(io.pad_mode.asBool, MuxLookup(io.valid_in, last, Array(1.U -> in0, 2.U -> in1, 3.U -> in2, 4.U -> in3)), 0.U)
//
//  val regs = Array.fill(8)(Reg(UInt(32.W)))
//  val start_index = RegInit(0.U(3.W))
//  val end_index = RegInit(0.U(3.W))
//  val count = end_index - start_index
//  val count_shift = Mux(io.pool_type.asBool,
//    Mux(io.pool_kernel_w.asBool, ShiftRegister(count, 2), ShiftRegister(count, 1)),
//    Mux(io.pool_kernel_w.asBool, ShiftRegister(count, fp32_add_cycles * 2), ShiftRegister(count, fp32_add_cycles))
//  )
//
//  // regs in
//  when(io.valid_in =/= 0.U) {
//    // pad when line start
//    when(pad_l_en) {
//      pad_l_en := false.B
//      val pad_data_l = Mux(io.pad_mode === 1.U, in0, 0.U)
//      start_index := 4.U - io.pad_left
//      regs(0) := pad_data_l
//      regs(1) := pad_data_l
//      regs(2) := pad_data_l
//      regs(3) := pad_data_l
//      regs(4) := in0
//      regs(5) := in1
//      regs(6) := in2
//      regs(7) := in3
//    }.otherwise { // input
//      end_index := end_index + io.valid_in
//      when(end_index(2) === 0.U) {
//        regs(0) := in0
//        regs(1) := in1
//        regs(2) := in2
//        regs(3) := in3
//      }.otherwise {
//        regs(4) := in0
//        regs(5) := in1
//        regs(6) := in2
//        regs(7) := in3
//      }
//    }
//  }.otherwise {
//    when(pad_r_en) {
//      end_index := end_index + io.pad_right
//      pad_r_en := false.B
//      switch(end_index) {
//        is(0.U) {
//          regs(0) := last
//          regs(1) := last
//        }
//        is(1.U) {
//          regs(1) := last
//          regs(2) := last
//        }
//        is(2.U) {
//          regs(2) := last
//          regs(3) := last
//        }
//        is(3.U) {
//          regs(3) := last
//          regs(4) := last
//        }
//        is(4.U) {
//          regs(4) := last
//          regs(5) := last
//        }
//        is(5.U) {
//          regs(5) := last
//          regs(6) := last
//        }
//        is(6.U) {
//          regs(6) := last
//          regs(7) := last
//        }
//        is(7.U) {
//          regs(7) := last
//          regs(0) := last
//        }
//      }
//    }.otherwise {
//      when(count < Cat(1.U(1.W), io.pool_kernel_w)) { // line finish // don't touch!
//        start_index := 0.U
//        end_index := 0.U
//      }
//    }
//  }
//  // regs out
//  val adder_array = Module(new pool_H_add_max_array)
//  adder_array.io.pool_type := io.pool_type
//  // pool_h
//  for (i <- 0 until 6) {
//    adder_array.io.in(i) := DontCare // default
//    switch(start_index) {
//      is(0.U) {
//        adder_array.io.in(i) := regs(i % 8)
//      }
//      is(1.U) {
//        adder_array.io.in(i) := regs((i + 1) % 8)
//      }
//      is(2.U) {
//        adder_array.io.in(i) := regs((i + 2) % 8)
//      }
//      is(3.U) {
//        adder_array.io.in(i) := regs((i + 3) % 8)
//      }
//      is(4.U) {
//        adder_array.io.in(i) := regs((i + 4) % 8)
//      }
//      is(5.U) {
//        adder_array.io.in(i) := regs((i + 5) % 8)
//      }
//      is(6.U) {
//        adder_array.io.in(i) := regs((i + 6) % 8)
//      }
//      is(7.U) {
//        adder_array.io.in(i) := regs((i + 7) % 8)
//      }
//    }
//  }
//  io.valid_out := 0.U
//  when(io.pool_kernel_w.asBool) { // kernel_w = 3
//    when(io.stride_w === 3.U) {
//      /*---- count = 3,4,5 ----*/
//      when(count === 3.U || count === 4.U || count === 5.U) {
//        start_index := start_index + 3.U
//      }
//      when(count_shift === 3.U || count_shift === 4.U || count_shift === 5.U) {
//        io.out(0) := adder_array.io.out(4)
//        io.valid_out := 1.U
//      }
//      /*---- count = 6 ----*/
//      when(count === 6.U) {
//        start_index := start_index + 6.U
//      }
//      when(count_shift === 6.U) {
//        io.out(0) := adder_array.io.out(4)
//        io.out(1) := adder_array.io.out(7)
//        io.valid_out := 2.U
//      }
//    }.elsewhen(io.stride_w === 2.U) {
//      /*---- count = 3 or 4 ----*/
//      when(count === 3.U || count === 4.U) {
//        start_index := start_index + 2.U
//      }
//      when(count_shift === 3.U || count_shift === 4.U) {
//        io.out(0) := adder_array.io.out(4)
//        io.valid_out := 1.U
//      }
//      /*---- count = 5 or 6 ----*/
//      when(count >= 5.U) {
//        start_index := start_index + 4.U
//      }
//      when(count_shift >= 5.U) {
//        io.out(0) := adder_array.io.out(4)
//        io.out(1) := adder_array.io.out(6)
//        io.valid_out := 2.U
//      }
//    }.otherwise { // stride = 1
//      /*---- count = 3 ----*/
//      when(count === 3.U) {
//        start_index := start_index + 1.U
//      }
//      when(count_shift === 3.U) {
//        io.out(0) := adder_array.io.out(4)
//        io.valid_out := 1.U
//      }
//      /*---- count = 4 ----*/
//      when(count === 4.U) {
//        start_index := start_index + 2.U
//      }
//      when(count_shift === 4.U) {
//        io.out(0) := adder_array.io.out(4)
//        io.out(1) := adder_array.io.out(5)
//        io.valid_out := 2.U
//      } /*---- count = 5 ----*/
//      when(count === 5.U) {
//        start_index := start_index + 3.U
//      }
//      when(count_shift === 5.U) {
//        io.out(0) := adder_array.io.out(4)
//        io.out(1) := adder_array.io.out(5)
//        io.out(2) := adder_array.io.out(6)
//        io.valid_out := 3.U
//      }
//      /*---- count = 6 ----*/
//      when(count >= 6.U) {
//        start_index := start_index + 4.U
//      }
//      when(count_shift >= 6.U) {
//        io.out(0) := adder_array.io.out(4)
//        io.out(1) := adder_array.io.out(5)
//        io.out(2) := adder_array.io.out(6)
//        io.out(3) := adder_array.io.out(7)
//        io.valid_out := 4.U
//      }
//    }
//  }.otherwise { // kernel_w = 2
//    when(io.stride_w === 2.U) {
//      /*---- count = 2 or 3 ----*/
//      when(count(2, 1) === 1.U) {
//        start_index := start_index + 2.U
//      }
//      when(count_shift(2, 1) === 1.U) {
//        io.out(0) := adder_array.io.out(0)
//        io.valid_out := 1.U
//      }
//      /*---- count >= 4 ----*/
//      when(count(2) === 1.U) {
//        start_index := start_index + 4.U
//      }
//      when(count_shift === 4.U || count_shift === 5.U) {
//        io.out(0) := adder_array.io.out(0)
//        io.out(1) := adder_array.io.out(2)
//        io.valid_out := 2.U
//      }
//    }.otherwise { // stride = 1
//      /*---- count = 2 ----*/
//      when(count === 2.U) {
//        start_index := start_index + 1.U
//      }
//      when(count_shift === 2.U) {
//        io.out(0) := adder_array.io.out(0)
//        io.valid_out := 1.U
//      }
//      /*---- count = 3 ----*/
//      when(count === 3.U) {
//        start_index := start_index + 2.U
//      }
//      when(count_shift === 3.U) {
//        io.out(0) := adder_array.io.out(0)
//        io.out(1) := adder_array.io.out(1)
//        io.valid_out := 2.U
//      }
//      /*---- count = 4 ----*/
//      when(count === 4.U) {
//        start_index := start_index + 3.U
//      }
//      when(count_shift === 4.U) {
//        io.out(0) := adder_array.io.out(0)
//        io.out(1) := adder_array.io.out(1)
//        io.out(2) := adder_array.io.out(2)
//        io.valid_out := 3.U
//      }
//      /*---- count > 4 ----*/
//      when(count > 4.U) {
//        start_index := start_index + 4.U
//      }
//      when(count_shift > 4.U) {
//        io.out(0) := adder_array.io.out(0)
//        io.out(1) := adder_array.io.out(1)
//        io.out(2) := adder_array.io.out(2)
//        io.out(3) := adder_array.io.out(3)
//        io.valid_out := 4.U
//      }
//    }
//  }
//}
//
///* in:(0)(1)(2)(3)(4)(5) ---
//*                          |
//*                          V
//* out:(0)(4) <---- (0+1)(0+1+2)
//*     (1)(5) <---- (1+2)(1+2+3)
//*     (2)(6) <---- (2+3)(2+3+4)
//*     (3)(7) <---- (3+4)(3+4+5) */
//class pool_H_add_max_array extends Module with pool_config with cal_cell_params {
//  val io = IO(new Bundle {
//    val in = Input(Vec(6, UInt(32.W)))
//    val out = Output(Vec(8, UInt(32.W)))
//    val pool_type = Input(UInt(1.W)) // '1' for max_pool, '0' for avg_pool
//  })
//
//  val adder_array = Array.fill(8)(Module(new add_max(false)))
//
//  for (i <- 0 until 4) {
//    adder_array(i).io.in_0 := io.in(i)
//    adder_array(i).io.in_1 := io.in(i + 1)
//    adder_array(i).io.pool_type := io.pool_type
//    io.out(i) := adder_array(i).io.out
//
//    adder_array(i + 4).io.in_0 := adder_array(i).io.out
//    adder_array(i + 4).io.in_1 := Mux(io.pool_type.asBool, ShiftRegister(io.in(i + 2), 1), ShiftRegister(io.in(i + 2), fp32_add_cycles))
//    adder_array(i + 4).io.pool_type := io.pool_type
//    io.out(i + 4) := adder_array(i + 4).io.out
//  }
//}
//
//class add_max(valid: Boolean = true) extends Module with pool_config with Convert with cal_cell_params {
//  val io = IO(new Bundle {
//    val valid_in = if (valid) Some(Input(Bool())) else None
//    val valid_out = if (valid) Some(Output(Bool())) else None
//    val in_0 = Input(UInt(32.W))
//    val in_1 = Input(UInt(32.W))
//    val out = Output(UInt(32.W))
//    val pool_type = Input(UInt(1.W)) // '1' for max_pool, '0' for avg_pool
//  })
//
//  val adder = FP32_Adder(Map("valid" -> valid))
//  val (a, b) = (FP32(io.in_0), FP32(io.in_1))
//  val max_ab: UInt = RegNext(Mux(a > b, a, b))
//  adder.io.x := a
//  adder.io.y := b
//  val add_ab = adder.io.z
//  io.out := Mux(io.pool_type, max_ab, add_ab)
//  if (valid) {
//    adder.io.valid_in.get := io.valid_in.get
//    io.valid_out.get := Mux(io.pool_type, RegNext(io.valid_in.get), adder.io.valid_out.get)
//  }
//}
//
//class Pool_RAM_Buf(max_width_size: Int = 16, end_sign: Boolean) extends Module with pool_config {
//  val io = IO(new Bundle {
//    val ofm_w = Input(UInt(max_width_size.W))
//    val data_in = Input(Vec(4, UInt(32.W)))
//    val valid_in = Input(UInt(3.W))
//    val data_out = Output(UInt(128.W))
//    val valid_out = Output(Bool())
//    val line_end = if (end_sign) Some(Output(Bool())) else None
//  })
//
//
//  val w_count = RegInit(0.U(max_width_size.W))
//  val data_count = w_count(1, 0)
//  val w_count_now = w_count + io.valid_in
//
//  val (b0, b1, b2) = (RegInit(0.U(32.W)), RegInit(0.U(32.W)), RegInit(0.U(32.W)))
//  val (v1, v2, v3, v4, end) = (io.valid_in === 1.U, io.valid_in === 2.U, io.valid_in === 3.U, io.valid_in === 4.U, Mux(io.ofm_w === 0.U, false.B, w_count === io.ofm_w))
//  val (in0, in1, in2, in3) = (io.data_in(0), io.data_in(1), io.data_in(2), io.data_in(3))
//  w_count := Mux(end, 0.U, w_count_now)
//  io.valid_out := (data_count + io.valid_in >= 4.U || (data_count =/= 0.U && end))
//  io.data_out := 0.U // by default
//  if (end_sign) io.line_end.get := RegNext(end)
//  switch(data_count) {
//    is(0.U) {
//      b0 := Mux(v1 || v2 || v3, in0, b0)
//      b1 := Mux(v2 || v3, in1, b1)
//      b2 := Mux(v3, in2, b2)
//      io.data_out := Cat(in3, in2, in1, in0)
//    }
//    is(1.U) {
//      b1 := Mux(v1 || v2, in0, b1)
//      b2 := Mux(v2, in1, b2)
//      io.data_out := Cat(in2, in1, in0, b0)
//      b0 := Mux(v4, in3, b0)
//    }
//    is(2.U) {
//      b2 := Mux(v1, in0, b2)
//      io.data_out := Cat(b0, b1, in0, in1)
//      io.data_out := Cat(in1, in0, b1, b0)
//      b0 := Mux(v3 || v4, in2, b0)
//      b1 := Mux(v4, in3, b1)
//    }
//    is(3.U) {
//      io.data_out := Cat(in0, b2, b1, b0)
//      b0 := Mux(v2 || v3 || v4, in1, b0)
//      b1 := Mux(v3 || v4, in2, b1)
//      b2 := Mux(v4, in3, b2)
//    }
//  }
//}
//
//class Pool_V extends Module with pool_config with cal_cell_params {
//  val io = IO(new Bundle {
//    val ofm_w = Input(UInt(16.W))
//    val ifm_h = Input(UInt(16.W))
//    val data_in = Input(UInt(128.W))
//    val valid_in = Input(Bool())
//    val pool_type = Input(UInt(1.W))
//    val pool_kernel_h = Input(UInt(1.W))
//    val stride_h = Input(UInt(2.W))
//    val pad_mode = Input(UInt(1.W))
//    val pad_top = Input(UInt(2.W))
//    val pad_bottom = Input(UInt(2.W))
//    val out = Output(Vec(4, UInt(32.W)))
//    val valid_out = Output(UInt(3.W))
//    //val test_out = Output(UInt(128.W))
//  })
//  io.out := DontCare
//  val kh = Cat(1.U(1.W), io.pool_kernel_h)
//
//  val (r0, r1, r2) = (TPRAM(128, pool_h_ram_dep, "block"), TPRAM(128, pool_h_ram_dep, "block"), TPRAM(128, pool_h_ram_dep, "block"))
//  r0.clock := clock
//  r1.clock := clock
//  r2.clock := clock
//
//  val adder_array = Module(new Fp32add8v(true))
//  adder_array.io.pool_type := io.pool_type
//  adder_array.io.valid_in.get := false.B
//  adder_array.io.in_0 := 0.U
//  adder_array.io.in_1 := 0.U
//  adder_array.io.in_2 := 0.U
//
//  val current_line = RegInit(2.U(2.W))
//  val in_ptr = RegInit(0.U(log2Ceil(pool_h_ram_dep).W))
//  val ow_num_1 = ((io.ofm_w - 1.U) >> 2).asUInt
//  val v_count = RegInit(0.U(16.W))
//  val valid_line = RegInit(0.U(16.W))
//  val w_count = RegInit(0.U(16.W))
//
//  val valid = Mux(io.pool_kernel_h.asBool, adder_array.io.valid_out_3.get, adder_array.io.valid_out_2.get)
//  when(valid) {
//    when(w_count + 4.U >= io.ofm_w) {
//      w_count := 0.U
//      io.valid_out := io.ofm_w - w_count
//    }.otherwise {
//      w_count := w_count + 4.U
//      io.valid_out := 4.U
//    }
//  }.otherwise {
//    io.valid_out := 0.U
//  }
//
//  r0.wen := false.B
//  r1.wen := false.B
//  r2.wen := false.B
//  r0.wdata := io.data_in
//  r1.wdata := io.data_in
//  r2.wdata := io.data_in
//
//  val valid_prev = RegNext(io.valid_in)
//  val data_prev = RegNext(io.data_in)
//  val v_count_prev = RegInit(0.U(16.W))
//  v_count_prev := v_count
//  val current_line_prev = RegNext(current_line)
//  // output
//  when(valid_prev && v_count_prev =/= 0.U) {
//    adder_array.io.valid_in.get := RegNext(valid_line) >= kh
//    adder_array.io.in_0 := data_prev
//    switch(RegNext(current_line)) {
//      is(0.U) {
//        adder_array.io.in_1 := r2.rdata
//        adder_array.io.in_2 := r1.rdata
//      }
//      is(1.U) {
//        adder_array.io.in_1 := r0.rdata
//        adder_array.io.in_2 := r2.rdata
//      }
//      is(2.U) {
//        adder_array.io.in_1 := r1.rdata
//        adder_array.io.in_2 := r0.rdata
//      }
//    }
//  }
//
//  // input
//  when(io.valid_in) {
//    in_ptr := Mux(in_ptr === ow_num_1, 0.U, in_ptr + 1.U)
//    // pad top
//    when(v_count === 0.U) {
//      r0.wen := true.B
//      r1.wen := true.B
//      r2.wen := true.B
//      r0.wdata := Mux(io.pad_mode.asBool, io.data_in, 0.U)
//      r1.wdata := Mux(io.pad_mode.asBool, io.data_in, 0.U)
//      r2.wdata := io.data_in
//      when(in_ptr === ow_num_1) {
//        current_line := 0.U
//        in_ptr := 0.U
//        v_count := io.pad_top + 1.U
//        valid_line := Mux(io.pad_top + 1.U >= kh, io.pad_top + 2.U - io.stride_h, io.pad_top + 2.U)
//      }
//      adder_array.io.in_0 := io.data_in
//      adder_array.io.in_1 := Mux(io.pad_mode.asBool, io.data_in, 0.U)
//      adder_array.io.in_2 := Mux(io.pad_mode.asBool, io.data_in, 0.U)
//      adder_array.io.valid_in.get := io.pad_top + 1.U >= kh
//    }.otherwise {
//      when(in_ptr === ow_num_1) {
//        v_count := v_count + 1.U
//        valid_line := Mux(valid_line >= kh, valid_line + 1.U - io.stride_h, valid_line + 1.U)
//      }
//      switch(current_line) {
//        is(0.U) {
//          r0.wen := true.B
//          when(in_ptr === ow_num_1) {
//            current_line := 1.U
//          }
//        }
//        is(1.U) {
//          r1.wen := true.B
//          when(in_ptr === ow_num_1) {
//            current_line := 2.U
//          }
//        }
//        is(2.U) {
//          r2.wen := true.B
//          when(in_ptr === ow_num_1) {
//            current_line := 0.U
//          }
//        }
//      }
//    }
//  }
//  // no pad bottom
//  when(v_count_prev === io.ifm_h + io.pad_top && io.pad_bottom === 0.U) {
//    v_count := 0.U
//    v_count_prev := 0.U
//    valid_line := 0.U
//    current_line := 2.U
//    in_ptr := 0.U
//  }
//
//  // pad bottom
//  when(v_count_prev >= io.ifm_h + io.pad_top && v_count_prev < io.ifm_h + io.pad_top + io.pad_bottom) {
//    //        in_ptr := Mux(in_ptr === ow_num_1, 0.U, in_ptr + 1.U)
//    //        adder_array.io.valid_in.get := in_ptr < ow_num_1
//    in_ptr := in_ptr + 1.U
//    adder_array.io.valid_in.get := in_ptr > 0.U
//
//    switch(current_line_prev) {
//      is(0.U) {
//        val pad_data = Mux(io.pad_mode.asBool, r2.rdata, 0.U)
//        when(io.pad_bottom === 1.U) {
//          adder_array.io.in_0 := pad_data
//          adder_array.io.in_1 := r2.rdata
//          adder_array.io.in_2 := r1.rdata
//        }.otherwise {
//          adder_array.io.in_0 := pad_data
//          adder_array.io.in_1 := pad_data
//          adder_array.io.in_2 := r2.rdata
//        }
//      }
//      is(1.U) {
//        val pad_data = Mux(io.pad_mode.asBool, r0.rdata, 0.U)
//        when(io.pad_bottom === 1.U) {
//          adder_array.io.in_0 := pad_data
//          adder_array.io.in_1 := r0.rdata
//          adder_array.io.in_2 := r2.rdata
//        }.otherwise {
//          adder_array.io.in_0 := pad_data
//          adder_array.io.in_1 := pad_data
//          adder_array.io.in_2 := r0.rdata
//        }
//      }
//      is(2.U) {
//        val pad_data = Mux(io.pad_mode.asBool, r1.rdata, 0.U)
//        when(io.pad_bottom === 1.U) {
//          adder_array.io.in_0 := pad_data
//          adder_array.io.in_1 := r1.rdata
//          adder_array.io.in_2 := r0.rdata
//        }.otherwise {
//          adder_array.io.in_0 := pad_data
//          adder_array.io.in_1 := pad_data
//          adder_array.io.in_2 := r1.rdata
//        }
//      }
//    }
//
//    when(in_ptr > ow_num_1) {
//      v_count := 0.U
//      valid_line := 0.U
//      current_line := 2.U
//      in_ptr := 0.U
//      w_count := 0.U
//      v_count_prev := 0.U
//    }
//  }
//
//  io.out(0) := Mux(io.pool_kernel_h.asBool, adder_array.io.out_3(0), adder_array.io.out_2(0))
//  io.out(1) := Mux(io.pool_kernel_h.asBool, adder_array.io.out_3(1), adder_array.io.out_2(1))
//  io.out(2) := Mux(io.pool_kernel_h.asBool, adder_array.io.out_3(2), adder_array.io.out_2(2))
//  io.out(3) := Mux(io.pool_kernel_h.asBool, adder_array.io.out_3(3), adder_array.io.out_2(3))
//  dontTouch(io.out)
//  dontTouch(io.valid_out)
//
//  r0.ren := true.B
//  r1.ren := true.B
//  r2.ren := true.B
//  r0.waddr := in_ptr
//  r1.waddr := in_ptr
//  r2.waddr := in_ptr
//  r0.raddr := in_ptr
//  r1.raddr := in_ptr
//  r2.raddr := in_ptr
//}
//
///* in_2 ()()()()
//*  in_1 ()()()()
//*  in_0 ()()()()
//*
//* out_3 ()()()() = in_2 + in_1 + in_0
//* out_2 ()()()() = in_1 + in_0
//* */
//class Fp32add8v(valid: Boolean = true) extends Module with pool_config with cal_cell_params {
//  val io = IO(new Bundle {
//    val in_0 = Input(UInt(128.W))
//    val in_1 = Input(UInt(128.W))
//    val in_2 = Input(UInt(128.W))
//    val out_2 = Output(Vec(4, UInt(32.W)))
//    val out_3 = Output(Vec(4, UInt(32.W)))
//    val pool_type = Input(UInt(1.W)) // '1' for max_pool, '0' for avg_pool
//    val valid_in = if (valid) Some(Input(Bool())) else None
//    val valid_out_2 = if (valid) Some(Output(Bool())) else None
//    val valid_out_3 = if (valid) Some(Output(Bool())) else None
//  })
//
//  val a0 = Module(new add_max(valid))
//  val a1 = Module(new add_max(false))
//  val a2 = Module(new add_max(false))
//  val a3 = Module(new add_max(false))
//  a0.io.pool_type := io.pool_type
//  a1.io.pool_type := io.pool_type
//  a2.io.pool_type := io.pool_type
//  a3.io.pool_type := io.pool_type
//  a0.io.in_0 := io.in_0(31, 0)
//  a1.io.in_0 := io.in_0(63, 32)
//  a2.io.in_0 := io.in_0(95, 64)
//  a3.io.in_0 := io.in_0(127, 96)
//  a0.io.in_1 := io.in_1(31, 0)
//  a1.io.in_1 := io.in_1(63, 32)
//  a2.io.in_1 := io.in_1(95, 64)
//  a3.io.in_1 := io.in_1(127, 96)
//  io.out_2(0) := a0.io.out
//  io.out_2(1) := a1.io.out
//  io.out_2(2) := a2.io.out
//  io.out_2(3) := a3.io.out
//
//  val a4 = Module(new add_max(valid))
//  val a5 = Module(new add_max(false))
//  val a6 = Module(new add_max(false))
//  val a7 = Module(new add_max(false))
//  a4.io.pool_type := io.pool_type
//  a5.io.pool_type := io.pool_type
//  a6.io.pool_type := io.pool_type
//  a7.io.pool_type := io.pool_type
//  a4.io.in_0 := a0.io.out
//  a5.io.in_0 := a1.io.out
//  a6.io.in_0 := a2.io.out
//  a7.io.in_0 := a3.io.out
//  a4.io.in_1 := Mux(io.pool_type.asBool, ShiftRegister(io.in_2(31, 0), 1), ShiftRegister(io.in_2(31, 0), fp32_add_cycles))
//  a5.io.in_1 := Mux(io.pool_type.asBool, ShiftRegister(io.in_2(63, 32), 1), ShiftRegister(io.in_2(63, 32), fp32_add_cycles))
//  a6.io.in_1 := Mux(io.pool_type.asBool, ShiftRegister(io.in_2(95, 64), 1), ShiftRegister(io.in_2(95, 64), fp32_add_cycles))
//  a7.io.in_1 := Mux(io.pool_type.asBool, ShiftRegister(io.in_2(127, 96), 1), ShiftRegister(io.in_2(127, 96), fp32_add_cycles))
//  io.out_3(0) := a4.io.out
//  io.out_3(1) := a5.io.out
//  io.out_3(2) := a6.io.out
//  io.out_3(3) := a7.io.out
//
//  if (valid) {
//    a0.io.valid_in.get := io.valid_in.get
//    a4.io.valid_in.get := a0.io.valid_out.get
//    io.valid_out_2.get := a0.io.valid_out.get
//    io.valid_out_3.get := a4.io.valid_out.get
//  }
//}
//
//class Pool_Mult_Buf extends Module with pool_config with cal_cell_params {
//  val io = IO(new Bundle {
//    /*--- input ---*/
//    val data_in = Input(UInt(128.W))
//    val valid_in = Input(Bool())
//    val input_end_in = Input(Bool())
//    val factor = Input(UInt(2.W))
//    val pool_type = Input(UInt(1.W))
//
//    /*--- output ---*/
//    val data_out = Output(UInt(128.W))
//    val valid_out = Output(Bool())
//    val input_end_out = Output(Bool())
//  })
//
//  //    val mult = Array.fill(4)(FP32_Mult(Map("valid" -> false)))
//  val mult = Array.fill(4)(FP32_Mult(Map("valid" -> false)))
//  mult(0).io.x := io.data_in(31, 0)
//  mult(1).io.x := io.data_in(63, 32)
//  mult(2).io.x := io.data_in(95, 64)
//  mult(3).io.x := io.data_in(127, 96)
//
//  val f1_4 = ("h" + java.lang.Float.floatToIntBits(1.0f / 4).toHexString).U
//  val f1_6 = ("h" + java.lang.Float.floatToIntBits(1.0f / 6).toHexString).U
//  val f1_9 = ("h" + java.lang.Float.floatToIntBits(1.0f / 9).toHexString).U
//
//  when(io.factor === 0.U) { // 0.25
//    mult(0).io.y := f1_4
//    mult(1).io.y := f1_4
//    mult(2).io.y := f1_4
//    mult(3).io.y := f1_4
//  }.elsewhen(io.factor === 3.U) { // 0.11
//    mult(0).io.y := f1_9
//    mult(1).io.y := f1_9
//    mult(2).io.y := f1_9
//    mult(3).io.y := f1_9
//  }.otherwise { // 0.16
//    mult(0).io.y := f1_6
//    mult(1).io.y := f1_6
//    mult(2).io.y := f1_6
//    mult(3).io.y := f1_6
//  }
//
//  io.data_out := Mux(io.pool_type.asBool, io.data_in, Cat(mult(3).io.z, mult(2).io.z, mult(1).io.z, mult(0).io.z))
//
//  io.valid_out := Mux(io.pool_type.asBool, io.valid_in, ShiftRegister(io.valid_in, fp32_mul_cycles))
//  io.input_end_out := Mux(io.pool_type.asBool, io.input_end_in, ShiftRegister(io.input_end_in, fp32_mul_cycles))
//}
//
//class Pool_Out_Buf extends Module with dma_config with pool_config {
//  val io = IO(new Bundle {
//    /*--- input ---*/
//    val data_in = Input(UInt(128.W))
//    val valid_in = Input(Bool())
//    val input_end = Input(Bool())
//
//    /*--- output ---*/
//    val wen = Output(Bool())
//    val wbusy = Input(Bool())
//    val wareq = Output(Bool())
//    val wsize = Output(UInt(32.W))
//    val wdata = new dmaWData_io(dmaDataWidth)
//    val output_end = Output(Bool())
//  })
//  io.output_end := false.B
//  io.wareq := false.B
//  io.wdata.data := 0.U
//
//  val buf_size = pool_out_buffer_size
//  val out_size = pool_out_buffer_fdma_size
//  val addr_width = log2Ceil(buf_size)
//  val end = RegInit(false.B)
//  val curren_end = RegInit(false.B)
//
//  val ram = TPRAM(128, buf_size, pool_out_buffer_ram_style)
//  ram.clock := clock
//  val in_ptr = RegInit(0.U(addr_width.W))
//  val out_ptr = RegInit(0.U(addr_width.W))
//  val count = RegInit(0.U(addr_width.W))
//  val current_out_size = RegInit(0.U(addr_width.W))
//  val out_count = RegInit(0.U(log2Ceil(out_size + 1).W))
//
//  ram.waddr := in_ptr
//  ram.raddr := out_ptr
//
//  val out_buf = RegInit(0.U(128.W))
//
//  val full = count >= out_size.U
//
//  ram.wen := io.valid_in
//  ram.wdata := io.data_in
//  when(io.valid_in) {
//    count := count + 1.U
//    in_ptr := in_ptr + 1.U
//  }
//
//  when(riseEdge(io.input_end)) {
//    end := true.B
//  }
//
//  val w0 :: w1 :: w2 :: nil = Enum(3)
//  val state = RegInit(w0)
//  io.wsize := Mux(end && !full, count, out_size.U)
//  io.wen := full | end
//  when(full | end) {
//    switch(state) {
//      is(w0) {
//        current_out_size := Mux(end && !full, count, out_size.U)
//        io.wdata.data := 0.U
//        when(count =/= 0.U) {
//          curren_end := end
//          when(!io.wbusy) {
//            io.wareq := true.B
//            state := w1
//            ram.ren := true.B
//          }.otherwise {
//            io.wareq := false.B
//          }
//        }.otherwise {
//          end := false.B
//          curren_end := false.B
//          io.output_end := true.B
//        }
//      }
//      is(w1) {
//        ram.ren := true.B
//        io.wdata.data := 0.U
//        when(io.wbusy) {
//          io.wareq := false.B
//          state := w2
//          out_buf := ram.rdata
//          out_ptr := out_ptr + 1.U
//        }.otherwise {
//          io.wareq := true.B
//        }
//      }
//      is(w2) {
//        io.wareq := false.B
//        when(out_count < current_out_size) {
//          ram.ren := true.B
//          when(io.wdata.valid) {
//            when(!RegNext(io.wdata.valid)) {
//              io.wdata.data := out_buf
//            }.otherwise {
//              io.wdata.data := ram.rdata
//              // out_buf := ram.rdata
//              // @TODO not solved
//            }
//            out_ptr := out_ptr + 1.U
//            out_count := out_count + 1.U
//          }.otherwise {
//            io.wdata.data := ram.rdata
//          }
//        }.otherwise {
//          when(io.valid_in) {
//            count := count - (current_out_size - 1.U)
//          }.otherwise {
//            count := count - current_out_size
//          }
//          when(curren_end) {
//            io.output_end := true.B
//            end := false.B
//            curren_end := false.B
//          }
//          state := w0
//          out_count := 0.U
//          ram.ren := false.B
//          out_ptr := out_ptr - 1.U
//          io.wdata.data := 0.U
//        }
//      }
//    }
//  }.otherwise {
//    io.wareq := false.B
//    io.wdata.data := 0.U
//  }
//}
//
//object test_pool extends App {
//  new(chisel3.stage.ChiselStage).execute(Array("--target-dir", "./verilog/test"), Seq(ChiselGeneratorAnnotation(() => new Pool_Unit)))
//}
