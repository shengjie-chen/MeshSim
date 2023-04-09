import chisel3._
import chisel3.stage.ChiselGeneratorAnnotation
import chisel3.util._

class ofm_data extends Bundle with mesh_config {
  val data0 = Vec(mesh_columns, UInt(pe_data_w.W))
  val data1 = Vec(mesh_columns, UInt(pe_data_w.W))
  val addr = UInt(ofm_buffer_addr_w.W)
}

class Mesh() extends Module with mesh_config {
  val io = IO(new Bundle {
    val w = Flipped(Decoupled(Vec(mesh_columns, UInt(pe_data_w.W))))
    val ifm = Flipped(Decoupled(Vec(mesh_rows, UInt(pe_data_w.W))))
    val ofm = Valid(new ofm_data)
  })

  val mesh = Seq.fill(mesh_rows, mesh_columns)(Module(new PE))
  val meshT = mesh.transpose

  val cnt = RegInit(0.U(32.W))
  when(io.w.valid || cnt =/= 0.U) {
    cnt := cnt + 1.U
  }
  dontTouch(cnt)

  val start_cnt = RegInit(0.U(log2Up(mesh_rows).W))
  val start = RegInit(0.B)
  when(io.w.valid && io.w.ready && io.ifm.valid || start) {
    start := 1.B
    start_cnt := start_cnt + 1.U
    when(start_cnt === (mesh_rows - 1).U) {
      start_cnt := 0.U
    }
  }

  val w_ready = RegInit(1.B)
  val ifm_ready = RegInit(0.B)

  io.w.ready := w_ready
  io.ifm.ready := ifm_ready
  when(start_cnt === (mesh_rows - 1).U) {
    ifm_ready := 1.B
  }
  val ifm_handshake = io.ifm.valid && io.ifm.ready

  val propagate = RegInit(0.B)
  when(start_cnt === (mesh_rows - 1).U) {
    propagate := !propagate
  }

  // (pipeline propagate across each row)
  for (r <- 0 until mesh_rows) {
    for (c <- 0 until mesh_columns) {
      mesh(r)(c).io.ctl.propagate := ShiftRegister(propagate, c)
    }
  }

  // (pipeline sel across each row)
  for (c <- 0 until mesh_columns) {
    meshT(c).foldLeft(!mesh(0)(c).io.ctl.propagate) {
      case (w, pe) =>
        pe.io.ctl.sel := w
        RegNext(pe.io.ctl.sel)
    }
  }

  // broadcast update & datatype
  for (r <- 0 until mesh_rows) {
    for (c <- 0 until mesh_columns) {
      //      mesh(r)(c).io.ctl.update := 1.B
      mesh(r)(c).io.ctl.datatype := 1.B
    }
  }

  // (pipeline ifm across each row)
  for (r <- 0 until mesh_rows) {
    mesh(r).foldLeft(ShiftRegister(io.ifm.bits(r), r)) {
      case (ifm, pe) =>
        pe.io.in_a := ifm
        pe.io.out_a
    }
  }

  // pipeline w across each column
  for (c <- 0 until mesh_columns) {
    meshT(c).foldLeft(ShiftRegister(io.w.bits(c), c)) {
      case (w, pe) =>
        pe.io.in_b := w
        pe.io.out_b
    }
    meshT(c).foldLeft(1.B) {
      case (in, pe) =>
        pe.io.in_b_valid := in
        pe.io.out_b_valid
    }
  }

  // pipeline part sum across each column
  for (r <- 0 until mesh_rows) {
    for (c <- 0 until mesh_columns) {
      if (r != 0) {
        mesh(r)(c).io.in_c0 := mesh(r - 1)(c).io.out_d0
        mesh(r)(c).io.in_c1 := mesh(r - 1)(c).io.out_d1
      }
      else {
        mesh(r)(c).io.in_c0 := 0.U
        mesh(r)(c).io.in_c1 := 0.U
      }
    }
  }

  io.ofm.valid := ShiftRegister(ifm_handshake, mesh_rows * 2)
  val addr_cnt = RegInit(0.U(ofm_buffer_addr_w.W))
  when(io.ofm.valid) {
    addr_cnt := addr_cnt + 1.U
    when(addr_cnt === (mesh_rows - 1).U) {
      addr_cnt := 0.U
    }
  }
  io.ofm.bits.addr := addr_cnt
  for (c <- 0 until mesh_columns) {
    io.ofm.bits.data0(c) := ShiftRegister(mesh(mesh_rows - 1)(c).io.out_d0, mesh_rows - c)
    io.ofm.bits.data1(c) := ShiftRegister(mesh(mesh_rows - 1)(c).io.out_d1, mesh_rows - c)
  }

}

object mesh_gen extends App {
  new (chisel3.stage.ChiselStage).execute(Array("--target-dir", "./verilog/mesh"), Seq(ChiselGeneratorAnnotation(() => new Mesh)))
}

object MeshGen extends App {
  (new chisel3.stage.ChiselStage)
    .execute(args, Seq(chisel3.stage.ChiselGeneratorAnnotation(() => new Mesh)))
}