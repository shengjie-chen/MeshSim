// See README.md for license details.

import chisel3._
import chisel3.util._

class PEControl extends Bundle {
  val datatype = UInt(3.W) // 001 -- INT8, 010 -- INT32, 100 -- FL32
  val update = Bool() // Which register should be propagated (and which should be accumulated)?
//  val sel = Bool()

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
    val out_d0 = Output(UInt(pe_data_w.W)) // part sum
    val out_d1 = Output(UInt(pe_data_w.W)) // part sum
  })
  assert(io.ctl.datatype === 1.U)

  val b = Reg(SInt(pe_data_w.W))
  val a0 = io.in_a(7, 0).asSInt
  val a1 = io.in_a(23, 16).asSInt

  when(io.ctl.update) {
    b := io.in_b.asSInt
  }

  io.out_a := RegNext(io.in_a)
  io.out_d0 := RegNext(io.in_c0.asSInt + a0 * b).asUInt
  io.out_d1 := RegNext(io.in_c1.asSInt + a1 * b).asUInt
}

