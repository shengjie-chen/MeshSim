import chisel3._
import chisel3.stage.ChiselGeneratorAnnotation
import chisel3.util._

class ofm_data extends Bundle with mesh_config {
  val data0 = UInt(pe_data_w.W)
  val data1 = UInt(pe_data_w.W)
  val addr = UInt(ofm_buffer_addr_w.W)
}

class Mesh() extends Module with mesh_config {
  val io = IO(new Bundle {
    val w = Flipped(Decoupled(Vec(mesh_columns, UInt(pe_data_w.W))))
    //    val w_valid = Input(Bool())
    val ifm = Flipped(Decoupled(Vec(mesh_rows, UInt(pe_data_w.W))))
    //    val ifm_valid = Input(Bool()) // then transfer 1 block

    val ofm = Vec(mesh_columns, Valid(new ofm_data))
    //    val ofm_addr =Output(Vec(mesh_columns, UInt(ofm_buffer_addr_w.W)))
    //    val ofm_valid = Output(Bool())
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
  //  when(io.w.valid){
  //    w_ready := 1.B
  //  }
  io.w.ready := w_ready
  io.ifm.ready := ifm_ready
  when(start_cnt === (mesh_rows-1).U) {
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

  val ofm_valid = RegInit(0.U((mesh_rows * 2).W))
  ofm_valid := ofm_valid ## ifm_handshake
  for (c <- 0 until mesh_columns) {
    io.ofm(c).valid := ofm_valid(mesh_rows + c - 1)
  }

  val addr_cnt_sr = RegInit(
    Vec(mesh_rows, UInt(ofm_buffer_addr_w.W)),
    0.B.asTypeOf(Vec(mesh_rows, UInt(ofm_buffer_addr_w.W)))) // shift reg
  when(ofm_valid(mesh_rows - 1)) {
    addr_cnt_sr(0) := addr_cnt_sr(0) + 1.U
    when(addr_cnt_sr(0) === mesh_rows.U) {
      addr_cnt_sr(0) := 0.U
    }
  }
  for (i <- 1 until mesh_rows) {
    addr_cnt_sr(i) := addr_cnt_sr(i - 1)
  }

  for (c <- 0 until mesh_columns) {
    io.ofm(c).bits.addr := addr_cnt_sr(c)
    io.ofm(c).bits.data0 := mesh(mesh_rows - 1)(c).io.out_d0
    io.ofm(c).bits.data1 := mesh(mesh_rows - 1)(c).io.out_d1
  }


}

object mesh_gen extends App {
  new (chisel3.stage.ChiselStage).execute(Array("--target-dir", "./verilog/mesh"), Seq(ChiselGeneratorAnnotation(() => new Mesh)))
}

object MeshGen extends App {
  (new chisel3.stage.ChiselStage)
    .execute(args, Seq(chisel3.stage.ChiselGeneratorAnnotation(() => new Mesh)))
}