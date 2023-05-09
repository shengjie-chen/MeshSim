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
  val en  = !io.stop
  // out
  io.out.valid := ShiftRegister(io.ofm.bits.acc_last, mesh_size, en)
  when(io.out.valid) {
    io.out.bits.data0 := mem(0).io.rdata
    io.out.bits.data1 := mem(1).io.rdata
  }.otherwise {
    io.out.bits.data0 := 0.U
    io.out.bits.data1 := 0.U
  }

  // start stage
  val start_reg = RegInit(0.B)
  when(riseEdge(io.ofm.valid)) {
    start_reg := 1.B
  }.elsewhen(io.ofm.bits.addr === (mesh_size - 1).U) {
    start_reg := 0.B
  }
  val start_stage = start_reg || riseEdge(io.ofm.valid)
  val add_zero    = io.out.valid || start_stage

  // READ
  val read_buffer = Wire(Vec(2, UInt(pe_data_w.W)))
  for (i <- 0 to 1) {
    when(io.ofm.valid) {
      mem(i).io.ren   := 1.B
      mem(i).io.raddr := Mux(io.ofm.bits.addr =/= (mesh_size - 1).U, io.ofm.bits.addr + 1.U, 0.U)
    }.elsewhen(ShiftRegister(io.ofm.bits.acc_last, mesh_size - 1, en)) { // finish stage
      mem(i).io.ren   := 1.B
      mem(i).io.raddr := ShiftRegister(io.ofm.bits.addr, mesh_size - 1, en)
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

    val out  = Vec(mesh_columns, Valid(new acc_data))
  })

  val acc_mem = Seq.fill(mesh_columns)(Module(new AccMemUnit))
  for (i <- 0 until mesh_columns) {
    acc_mem(i).io.ofm  <> io.ofm(i)
    acc_mem(i).io.stop <> io.stop
    io.out(i)          <> acc_mem(i).io.out
  }
}
