import chisel3._
import chisel3.util._

class PEControl extends Bundle {
  val datatype  = UInt(3.W) // 001 -- INT8, 010 -- INT32, 100 -- FL32
  val propagate = Bool() // propagate: Which register should be propagated (and which should be accumulated)?
  val sel       = Bool() // which b to compute
}

class PE extends Module with pe_config {

  val io = IO(new Bundle {
    val in_a       = Input(UInt(pe_data_w.W)) // ifm
    val in_b       = Input(UInt(pe_data_w.W)) // w
    val in_c0      = Input(UInt(pe_data_w.W)) // part sum
    val in_c1      = Input(UInt(pe_data_w.W)) // part sum
    val in_b_valid = Input(Bool())
    val en         = Input(Bool())

    val ctl = Input(new PEControl)

    val out_a       = Output(UInt(pe_data_w.W)) // ifm
    val out_b       = Output(UInt(pe_data_w.W)) // w output
    val out_d0      = Output(UInt(pe_data_w.W)) // part sum
    val out_d1      = Output(UInt(pe_data_w.W)) // part sum
    val out_b_valid = Output(Bool())

  })
  assert(io.ctl.datatype === 1.U)

  val b = RegInit(Vec(2, SInt(pe_data_w.W)), 0.B.asTypeOf(Vec(2, SInt(pe_data_w.W))))

  val a0  = io.in_a(7, 0).asSInt
  val a1  = io.in_a(23, 16).asSInt
  val p   = io.ctl.propagate
  val sel = io.ctl.sel

  val use_b = Wire(SInt(pe_data_w.W))
  use_b := b(sel)
  dontTouch(use_b)
  when(io.in_b_valid && io.en) {
    b(p) := io.in_b.asSInt
  }
  io.out_b := b(p).asUInt

  val b_invalid = Wire(Vec(2, Bool()))
  b_invalid(0)   := riseEdge(io.ctl.sel) // sel posedge
  b_invalid(1)   := fallEdge(io.ctl.sel) // sel negedge
  val out_b_valid = !b_invalid(p) && !(io.ctl.propagate === io.ctl.sel)
  io.out_b_valid := out_b_valid || RegEnable(out_b_valid, dualEdge(io.en))

  io.out_a  := RegEnable(io.in_a, io.en)
  io.out_d0 := RegEnable(io.in_c0.asSInt + a0 * use_b, io.en).asUInt
  io.out_d1 := RegEnable(io.in_c1.asSInt + a1 * use_b, io.en).asUInt
}
