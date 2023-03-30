// See README.md for license details.

import chisel3._
import chisel3.util._

class PEControl extends Bundle {
  val datatype = UInt(3.W) // 001 -- INT8, 010 -- INT32, 100 -- FL32
  val propagate = Bool() // propagate: Which register should be propagated (and which should be accumulated)?
  val sel = Bool() // which b to compute
}

// TODO update documentation

/**
 * A PE implementing a MAC operation. Configured as fully combinational when integrated into a Mesh.
 *
 * @param width Data width of operands
 */
class PE extends Module with pe_config {

  val io = IO(new Bundle {
    val in_a = Input(UInt(pe_data_w.W)) // ifm
    val in_b = Input(UInt(pe_data_w.W)) // w
    val in_c0 = Input(UInt(pe_data_w.W)) // part sum
    val in_c1 = Input(UInt(pe_data_w.W)) // part sum

    val ctl = Input(new PEControl)

    val out_a = Output(UInt(pe_data_w.W)) // ifm
    val out_b = Output(UInt(pe_data_w.W)) // w output
    val out_d0 = Output(UInt(pe_data_w.W)) // part sum
    val out_d1 = Output(UInt(pe_data_w.W)) // part sum
  })
  assert(io.ctl.datatype === 1.U)

  val b = RegInit(Vec(2, SInt(pe_data_w.W)), 0.B.asTypeOf(Vec(2, SInt(pe_data_w.W))))

  val a0 = io.in_a(7, 0).asSInt
  val a1 = io.in_a(23, 16).asSInt
  val p = io.ctl.propagate
//  val use_start = RegInit(0.B)
//  val use_index = ShiftRegister(~p, delay, use_start)
  val sel = io.ctl.sel
//  when(p) {
//    use_start := 1.B
//  }
//  when(use_start) {
//    use_index := ~p
//  }
  val use_b = Wire(SInt(pe_data_w.W))
  use_b := b(sel)
  dontTouch(use_b)

  b(p) := io.in_b.asSInt
  io.out_b := b(p).asUInt

  io.out_a := RegNext(io.in_a)
  io.out_d0 := RegNext(io.in_c0.asSInt + a0 * use_b).asUInt
  io.out_d1 := RegNext(io.in_c1.asSInt + a1 * use_b).asUInt
}

