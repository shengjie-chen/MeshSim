import chisel3._
import chisel3.util._

import scala.language.postfixOps

class FP32_Multiplier(parameters: Map[String, Any] = Map()) extends Module with Convert {

  val default_config = Map(
    "flag" -> false,
  )

  val config = default_config ++ parameters

  val io = IO(new Bundle {
    val x = Input(UInt(32.W))
    val y = Input(UInt(32.W))
    val z = Output(UInt(32.W))
    /* flag[3:0] = {overflow(8), underflow(4), NaN(2), Inf(1)} */
    val flag = Output(if(config("flag")) UInt(4.W) else UInt(0.W))
  })

  val xe = io.x(30, 23)
  val ye = io.y(30, 23)
  val xf = io.x(22, 0)
  val yf = io.y(22, 0)

  val ze_is_ff = xe === 0xff.U || ye === 0xff.U
  val is_NaN = (xe === 0xff.U && xf =/= 0.U) || (ye === 0xff.U && yf =/= 0.U)
  val is_zero = xe === 0.U || ye === 0.U

  val zf_0 = Cat(1.U, io.x(22, 0)) * Cat(1.U, io.y(22, 0))

  /* ---------------------- PIPELINE A ---------------------- */

  val carry = zf_0(47) || (zf_0(46, 22) === (~0.U(25.W)).asUInt)
  val zf = Mux(carry, zf_0(46, 24) + zf_0(23), zf_0(45, 23) + zf_0(22))
  val ze = Cat(0.U(2.W), xe) + ye - Mux(carry, 126.U, 127.U)

  io.z := Cat(io.x(31) ^ io.y(31), PriorityMux(Seq(
    ze_is_ff -> Mux(is_NaN, ~0.U(31.W), 0x7f800000.U(31.W)), // NaN, Inf
    is_zero -> 0.U(31.W), // zero
    (ze(9, 8) =/= 0.U) -> Mux(ze(9), 0.U(31.W), 0x7f800000.U(31.W)), // underflow, overflow
    true.B -> Cat(ze(7, 0), zf)
  )))

  if(config("flag")) {
    io.flag := PriorityMux(Seq(
      ze_is_ff -> Mux(is_NaN, 2.U, 1.U),
      is_zero -> 0.U,
      (ze(9, 8) =/= 0.U) -> Mux(ze(9), 4.U, 8.U),
      true.B -> 0.U
    ))
  } else {
    io.flag := DontCare
  }

}