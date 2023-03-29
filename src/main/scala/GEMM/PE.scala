// See README.md for license details.
package GEMM

import chisel3._
import chisel3.util._

class PEControl extends Bundle {
  val datatype = UInt(2.W) // 0.U -- INT8, 1.U -- INT32, 2.U -- FL32
  val propagate = UInt(1.W) // Which register should be propagated (and which should be accumulated)?

}

// TODO update documentation
/**
  * A PE implementing a MAC operation. Configured as fully combinational when integrated into a Mesh.
  * @param width Data width of operands
  */
class PE (inputWidth: Int, accWidth: Int)
                   extends Module {

  val io = IO(new Bundle {
    val in_a  = Input(SInt(inputWidth.W))
    val in_a_valid = Input(Bool())
    val in_b  = Input(SInt(inputWidth.W))
    val in_b_valid = Input(Bool())
    val in_d  = Input(SInt(accWidth.W))
    val in_d_valid = Input(Bool())

    val in_control = Input(new PEControl)

    val out_a = Output(SInt(inputWidth.W))
    val out_a_valid = Output(Bool())
    val out_b = Output(SInt(inputWidth.W))
    val out_c = Output(SInt(accWidth.W))
    val out_valid = Output(Bool())

  })


  val a  = io.in_a
  //val a0 = a(7,0)
  //val a1 = a(23,16)
  //val b  = io.in_b
  val d  = io.in_d
  val b1 = Reg(SInt(inputWidth.W))
  val b2 = Reg(SInt(inputWidth.W))
  val datatype = io.in_control.datatype
  val prop  = io.in_control.propagate
  val prop_s1 = RegNext(prop)


  val b_compute  = Wire(SInt(inputWidth.W))
  val b_downward = Wire(SInt(inputWidth.W))

  when(io.in_b_valid){
    when(prop === 1.U) {
      b1 := io.in_b
    }.otherwise{
      b2 := io.in_b
    }
  }
  when(prop_s1 === 1.U) {
    b_downward := b1
    b_compute  := b2
  }.otherwise{
    b_downward := b2
    b_compute  := b1
  }

  val c0 = Wire(SInt(32.W))
  val c1 = Wire(SInt(32.W))
  c0 := a(7,0).asSInt * b_compute(7,0) + d(31,0).asSInt
  c1 := a(23,16).asSInt * b_compute(23,16) + d(63,32).asSInt

  when(datatype === 0.U) { // INT8
    io.out_a := a
    io.out_a_valid := io.in_a_valid
    io.out_b := b_downward
    //io.out_c(15,0)  := a(7,0) * b_compute(7,0) + d(15,0)
    //io.out_c(31,16) := a(23,16) * b_compute(23,16) + d(31,16)
    io.out_c := Cat(c1,c0).asSInt
    io.out_valid := io.in_a_valid && io.in_d_valid
  }.elsewhen(datatype === 1.U) { // INT32  1cycle?
    io.out_a := a
    io.out_a_valid := io.in_a_valid
    io.out_b := b_downward
    io.out_c := a * b_compute + d
    io.out_valid := io.in_a_valid && io.in_d_valid
  }.elsewhen(datatype === 2.U) { // FL32 3cycle?
    io.out_a := ShiftRegister(a,2)
    io.out_a_valid := ShiftRegister(io.in_a_valid,2)
    io.out_b := b_downward
    io.out_c := ShiftRegister(a * b_compute + d,2)
    io.out_valid := ShiftRegister(io.in_a_valid && io.in_d_valid,2)
  }.otherwise{
    io.out_a := a
    io.out_a_valid := io.in_a_valid
    io.out_b := b_downward
    io.out_c := a * b_compute + d
    io.out_valid := io.in_a_valid && io.in_d_valid
  }
}
