import chisel3._
import chisel3.stage.ChiselGeneratorAnnotation
import chisel3.util._

// need 2
class AccMem extends Module with mesh_config {
  val io = IO(new Bundle {
    val ofm  = Flipped(Valid(new ofm_data))
    val last = Input(Bool())
    val out  = Valid(new ofm_data)
    val stop = Input(Bool())
  })
  val mem = Seq.fill(2, mesh_size)(Module(new TPRAM(pe_data_w, mesh_size, "block")))
  val en  = !io.stop
  // out
  io.out.valid     := ShiftRegister(io.last, mesh_size, en)
  io.out.bits.addr := DontCare
  for (c <- 0 until mesh_columns) {
    when(io.out.valid) {
      io.out.bits.data0(c) := mem(0)(c).io.rdata
      io.out.bits.data1(c) := mem(1)(c).io.rdata
    }.otherwise {
      io.out.bits.data0(c) := 0.U
      io.out.bits.data1(c) := 0.U
    }
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
  val read_buffer = Wire(Vec(2, Vec(mesh_size, UInt(pe_data_w.W))))
  for (i <- 0 to 1) {
    for (c <- 0 until mesh_columns) {
      when(io.ofm.valid) {
        mem(i)(c).io.ren   := 1.B
        mem(i)(c).io.raddr := Mux(io.ofm.bits.addr =/= (mesh_size - 1).U, io.ofm.bits.addr + 1.U, 0.U)
      }.elsewhen(ShiftRegister(io.last, mesh_size - 1, en)) {
        mem(i)(c).io.ren   := 1.B
        mem(i)(c).io.raddr := ShiftRegister(io.ofm.bits.addr, mesh_size - 1, en)
      }.otherwise {
        mem(i)(c).io.ren   := 0.B
        mem(i)(c).io.raddr := 0.U
      }

      when(add_zero) {
        read_buffer(i)(c) := 0.U
      }.otherwise {
        read_buffer(i)(c) := mem(i)(c).io.rdata
      }

      mem(i)(c).io.clock <> clock
    }
  }

  // WRITE
  for (c <- 0 until mesh_columns) {
    when(io.ofm.valid) {
      mem(0)(c).io.waddr := io.ofm.bits.addr
      mem(0)(c).io.wdata := io.ofm.bits.data0(c) + read_buffer(0)(c)
      mem(0)(c).io.wen   := en
      mem(1)(c).io.waddr := io.ofm.bits.addr
      mem(1)(c).io.wdata := io.ofm.bits.data1(c) + read_buffer(1)(c)
      mem(1)(c).io.wen   := 1.B
    }.otherwise {
      mem(0)(c).io.waddr := 0.U
      mem(0)(c).io.wdata := 0.U
      mem(0)(c).io.wen   := 0.B
      mem(1)(c).io.waddr := 0.U
      mem(1)(c).io.wdata := 0.U
      mem(1)(c).io.wen   := 0.B
    }
  }
}
