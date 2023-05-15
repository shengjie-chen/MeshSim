import chisel3._
import chisel3.stage.ChiselGeneratorAnnotation
import chisel3.util._

class AccMemUnit extends Module with mesh_config {
  val io = IO(new Bundle {
    val ofm  = Flipped(Valid(new ofm_data))
    val out  = Valid(new acc_data)
    val stop = Input(Bool())
  })
  val mem = Seq.fill(2)(Module(new TPRAM(pe_data_w, mesh_size, "block")))
  val en  = !(io.stop || ofm_in_stage && !io.ofm.valid)

  lazy val sNone :: sStart :: sProcess :: sEnd :: Nil = Enum(4)
  lazy val state                                      = RegInit(sNone)
  val end_read_addr                                   = RegInit(0.U(log2Ceil(mesh_size).W))

  switch(state) {
    is(sNone) {
      when(riseEdge(io.ofm.valid)) {
        state := sStart
      }
    }
    is(sStart) {
      when(en) {
        when(io.ofm.bits.finish) {
          state := sEnd
        }.elsewhen(io.ofm.bits.addr === (mesh_size - 1).U && en) {
          state := sProcess
        }
      }
    }
    is(sProcess) {
      when(io.ofm.bits.finish && en) {
        state := sEnd
      }
    }
    is(sEnd) {
      when(end_read_addr === 0.U && en) {
        state := sNone
      }
    }
  }

  when(en) {
    when(io.ofm.bits.finish || state === sEnd && end_read_addr =/= 0.U) {
      end_read_addr := Mux(end_read_addr === (mesh_size - 1).U, 0.U, end_read_addr + 1.U)
    }
  }

  // out
  io.out.valid := ShiftRegister(io.ofm.bits.acc_last, mesh_size, en) && en && state =/= sNone
  when(io.out.valid) {
    io.out.bits.data0 := mem(0).io.rdata
    io.out.bits.data1 := mem(1).io.rdata
  }.otherwise {
    io.out.bits.data0 := 0.U
    io.out.bits.data1 := 0.U
  }

  // ofm in
  lazy val ofm_in_stage = state === sStart || state === sProcess || riseEdge(io.ofm.valid)

  val add_zero = state === sStart || riseEdge(io.ofm.valid) && state === sNone || io.out.valid

  // READ
  val read_buffer = Wire(Vec(2, UInt(pe_data_w.W)))
  for (i <- 0 to 1) {
    when(io.ofm.valid) {
      mem(i).io.ren   := 1.B
      mem(i).io.raddr := Mux(io.ofm.bits.addr =/= (mesh_size - 1).U, io.ofm.bits.addr + 1.U, 0.U)
    }.elsewhen(state === sEnd) { // finish stage
      mem(i).io.ren   := 1.B
      mem(i).io.raddr := end_read_addr
    }.otherwise {
      mem(i).io.ren   := 0.B
      mem(i).io.raddr := 0.U
    }

    when(add_zero) {
      read_buffer(i) := 0.U
    }.otherwise {
      read_buffer(i) := mem(i).io.rdata
    }

    mem(i).io.clock <> clock
  }

  // WRITE
  when(io.ofm.valid) {
    mem(0).io.waddr := io.ofm.bits.addr
    mem(0).io.wdata := io.ofm.bits.data0 + read_buffer(0)
    mem(0).io.wen   := en
    mem(1).io.waddr := io.ofm.bits.addr
    mem(1).io.wdata := io.ofm.bits.data1 + read_buffer(1)
    mem(1).io.wen   := en
  }.otherwise {
    mem(0).io.waddr := 0.U
    mem(0).io.wdata := 0.U
    mem(0).io.wen   := 0.B
    mem(1).io.waddr := 0.U
    mem(1).io.wdata := 0.U
    mem(1).io.wen   := 0.B
  }
}

class AccMem extends Module with mesh_config {
  val io = IO(new Bundle {
    val ofm  = Flipped(Vec(mesh_columns, Valid(new ofm_data)))
    val stop = Input(Bool())

    val out = Vec(mesh_columns, Valid(new acc_data))
  })

  val acc_mem = Seq.fill(mesh_columns)(Module(new AccMemUnit))
  for (i <- 0 until mesh_columns) {
    acc_mem(i).io.ofm  <> io.ofm(i)
    acc_mem(i).io.stop <> io.stop
    io.out(i)          <> acc_mem(i).io.out
  }
}
