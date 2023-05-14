import chisel3._
import chisel3.stage.ChiselGeneratorAnnotation
import chisel3.util._

class acc_data extends Bundle with mesh_config {
  val data0 = UInt(pe_data_w.W)
  val data1 = UInt(pe_data_w.W)
}

class ofm_data extends acc_data {
  val addr     = UInt(ofm_buffer_addr_w.W)
  val acc_last = Bool()
}

class Mesh() extends Module with mesh_config {
  val io = IO(new Bundle {
    val w           = Flipped(Decoupled(Vec(mesh_columns, UInt(pe_data_w.W))))
    val ifm         = Flipped(Decoupled(Vec(mesh_rows, UInt(pe_data_w.W)))) // each has 2 int8 or 1 int32/fp32
    val ofm         = Vec(mesh_columns, Valid(new ofm_data))
    val ofmbuf_stop = Input(Bool())
    val w_finish    = Input(Bool())
    val last_in     = Input(Bool())
  })

  val mesh       = Seq.fill(mesh_rows, mesh_columns)(Module(new PE))
  val meshT      = mesh.transpose
  val w_block    = w_state_r && !io.w.valid
  val en         = !(io.ofmbuf_stop || w_block)
  val w_finish   = io.w_finish
  val ifm_finish = ShiftRegister(w_finish, mesh_size, en)
//  assert(ifm_finish && io.last_in || !ifm_finish)

  //  val cnt = RegInit(0.U(32.W))
  //  when(io.w.valid || cnt =/= 0.U) {
  //    cnt := cnt + 1.U
  //  }
  //  dontTouch(cnt)

  val start_cnt    = RegInit(0.U(log2Up(mesh_rows).W))
  lazy val start_r = RegInit(0.B)
  val start        = io.w.valid && io.w.ready && io.ifm.valid && en && !start_r || start_r
  when(en) {
    when(ifm_finish) {
      start_r := 0.B
    }.elsewhen(io.w.valid && io.w.ready && io.ifm.valid && !start_r) {
      start_r := 1.B
    }
  }

  when(start && en) {
    start_cnt := start_cnt + 1.U
    when(start_cnt === (mesh_rows - 1).U) {
      start_cnt := 0.U
    }
  }

  lazy val w_state_r = RegInit(0.B)
  when(w_finish) {
    w_state_r := 0.B
  }.elsewhen((io.w.valid && io.w.ready && io.ifm.valid) && en && !w_state_r) {
    w_state_r := 1.B
  }

  val ifm_ready = RegInit(0.B)
  io.w.ready   := en && io.ifm.valid
  io.ifm.ready := ifm_ready && en
  when(start_cnt === (mesh_rows - 2).U) {
    ifm_ready := 1.B
  }.elsewhen(ifm_finish) {
    ifm_ready := 0.B
  }
  val ifm_ready_inside = RegInit(0.B)
  when(en) {
    when(ifm_finish) {
      ifm_ready_inside := 0.B
    }.elsewhen(start_cnt === (mesh_rows - 1).U) {
      ifm_ready_inside := 1.B
    }
  }
  val ifm_handshake = io.ifm.valid && (ifm_ready_inside && en)

  val propagate = RegInit(0.B)
  when(start_cnt === (mesh_rows - 1).U && en) {
    propagate := !propagate
  }

  // TODO: maybe optimize this
  // pipeline propagate across each row
  for (r <- 0 until mesh_rows) {
    for (c <- 0 until mesh_columns) {
      mesh(r)(c).io.ctl.propagate := ShiftRegister(propagate, c, en)
    }
  }

  // pipeline sel across each row
  for (c <- 0 until mesh_columns) {
    meshT(c).foldLeft(!mesh(0)(c).io.ctl.propagate) {
      case (w, pe) =>
        pe.io.ctl.sel := w
        RegEnable(pe.io.ctl.sel, 0.B, en)
    }
  }

  // broadcast en & datatype
  for (r <- 0 until mesh_rows) {
    for (c <- 0 until mesh_columns) {
      mesh(r)(c).io.en           := en
      mesh(r)(c).io.ctl.datatype := 1.B
    }
  }

  // pipeline ifm across each row
  for (r <- 0 until mesh_rows) {
    mesh(r).foldLeft(ShiftRegister(io.ifm.bits(r), r, en)) {
      case (ifm, pe) =>
        pe.io.in_a := ifm
        pe.io.out_a
    }
  }

  // pipeline w across each column
  for (c <- 0 until mesh_columns) {
    meshT(c).foldLeft(ShiftRegister(io.w.bits(c), c, en)) {
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
      } else {
        mesh(r)(c).io.in_c0 := 0.U
        mesh(r)(c).io.in_c1 := 0.U
      }
    }
  }

  // ofm valid
  val ofm_valid = RegInit(0.U((mesh_rows * 2).W))
  when(en) {
    ofm_valid := ofm_valid ## ifm_handshake
  }
  for (c <- 0 until mesh_columns) {
    io.ofm(c).valid := ofm_valid(mesh_rows + c - 1) && en
  }

  // ofm acc_last
  val ofm_acc_last = RegInit(0.U((mesh_rows * 2).W))
  when(en) {
    ofm_acc_last := ofm_acc_last ## io.last_in
  }
  for (c <- 0 until mesh_columns) {
    io.ofm(c).bits.acc_last := ofm_acc_last(mesh_rows + c - 1)
  }

  // ofm addr
  val addr_cnt_sr = RegInit(
    Vec(mesh_rows, UInt(ofm_buffer_addr_w.W)),
    0.B.asTypeOf(Vec(mesh_rows, UInt(ofm_buffer_addr_w.W)))
  ) // shift reg
  when(ofm_valid(mesh_rows - 1) && en) {
    addr_cnt_sr(0) := addr_cnt_sr(0) + 1.U
    when(addr_cnt_sr(0) === (mesh_rows - 1).U) {
      addr_cnt_sr(0) := 0.U
    }
  }
  when(en) {
    for (i <- 1 until mesh_rows) {
      addr_cnt_sr(i) := addr_cnt_sr(i - 1)
    }
  }
  for (c <- 0 until mesh_columns) {
    io.ofm(c).bits.addr := addr_cnt_sr(c)
  }

  // ofm bits
  for (c <- 0 until mesh_columns) {
    io.ofm(c).bits.data0 := mesh(mesh_rows - 1)(c).io.out_d0
    io.ofm(c).bits.data1 := mesh(mesh_rows - 1)(c).io.out_d1
  }

}

object mesh_gen extends App {
  new (chisel3.stage.ChiselStage)
    .execute(Array("--target-dir", "./verilog/mesh"), Seq(ChiselGeneratorAnnotation(() => new Mesh)))
}

object MeshGen extends App {
  (new chisel3.stage.ChiselStage)
    .execute(args, Seq(chisel3.stage.ChiselGeneratorAnnotation(() => new Mesh)))
}
