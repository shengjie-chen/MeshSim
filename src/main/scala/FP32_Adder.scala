
import chisel3._
import chisel3.util._

trait Convert {
  implicit def AnyToInt(a: Any): Int = Integer.parseInt(a.toString)

  implicit def AnyToBoolean(a: Any): Boolean = a == true

  implicit def AnyToString(a: Any): String = a.toString

  //implicit def FP32ToUInt(f: FP32): UInt = f.asUInt()

  implicit def BooleanToInt(b: Boolean): Int = if(b) 1 else 0
}

class FP32_Adder(parameters: Map[String, Any] = Map()) extends Module with Convert {

  val default_config = Map(
    "input_buffer" -> false, // add regs between (input) and (pipeline stage 1)
    "output_buffer" -> true, // add regs before output
    "valid" -> true, // valid signal
    "only_positive" -> false, // if x,y is guaranteed > 0, set true to reduce hardware resources
    "check_NaN_Inf" -> true, // check if input is NaN or Inf
    "-Inf+Inf=NaN" -> true, // x + y = (this) ? NaN : x ,when (x,y) or (y,x) is (Inf,-Inf)
    "shift_right" -> 0, // shift right when adding.
    "rounding" -> "even", // even/zero/floor/ceil
    "overflow_flag" -> false,
    "underflow_flag" -> false,
    "Inf_flag" -> false,
    "NaN_flag" -> false
  )

  val config = default_config ++ parameters
  val rm = config("rounding").toLowerCase + "" match {
    case "0" | "zero" | "none" => 0
    case "1" | "even" | "nearest" => 1
    case "2" | "floor" | "-inf" => 2
    case "3" | "ceil" | "inf" => 3
  }
  val suflen = if (rm == 0) 0 else 2

  /* z = x + y */
  val io = IO(new Bundle {
    val x = Input(UInt(32.W))
    val y = Input(UInt(32.W))
    val z = Output(UInt(32.W))
    val valid_in = Input(if (config("valid")) Bool() else UInt(0.W))
    val valid_out = Output(if (config("valid")) Bool() else UInt(0.W))
    val overflow_flag = Output(if (config("overflow_flag")) Bool() else UInt(0.W))
    val underflow_flag = Output(if (config("underflow_flag")) Bool() else UInt(0.W))
    val Inf_flag = Output(if (config("Inf_flag")) Bool() else UInt(0.W))
    val NaN_flag = Output(if (config("NaN_flag")) Bool() else UInt(0.W))
  })
  io.valid_out := ShiftRegister(io.valid_in, 1 + (if (config("input_buffer")) 1 else 0) + (if (config("output_buffer")) 1 else 0))

  /*--- pipeline stage 0 ---*/
  /*---- start ----*/
  val (xs, ys) = if (config("only_positive")) {
    (0.U(1.W), 0.U(1.W))
  } else {
    if (config("input_buffer")) {
      (RegNext(io.x(31)), RegNext(io.y(31)))
    } else {
      (io.x(31), io.y(31))
    }
  }

  val (xe, xf, ye, yf) = if (config("input_buffer")) {
    (RegNext(io.x(30, 23)), RegNext(io.x(22, 0)), RegNext(io.y(30, 23)), RegNext(io.y(22, 0)))
  } else {
    (io.x(30, 23), io.x(22, 0), io.y(30, 23), io.y(22, 0))
  }

  val xe_is_ff = xe === 0xff.U
  val ye_is_ff = ye === 0xff.U
  val ze_is_ff = if (config("check_NaN_Inf")) xe_is_ff || ye_is_ff else false.B

  val is_NaN = if (config("-Inf+Inf=NaN")) {
    (xe_is_ff && xf =/= 0.U) || (ye_is_ff && yf =/= 0.U) || (xe_is_ff && ye_is_ff && (xs ^ ys).asBool)
  } else {
    (xe_is_ff && xf =/= 0.U) || (ye_is_ff && yf =/= 0.U)
  }
  val y_gt_x = Cat(ye, yf) > Cat(xe, xf) // if |y|>|x|
  val zs = Mux(y_gt_x, ys, xs)
  val ze = Mux(y_gt_x, ye, xe)
  val xf_1 = Mux(y_gt_x, Cat(ye =/= 0.U, yf), Cat(xe =/= 0.U, xf))
  /*---- align ----*/
  val xe_ye = ze - Mux(y_gt_x, xe, ye)
  val yf_1 = Mux(ze_is_ff, 0.U,
    if (rm == 0) {
      (Mux(y_gt_x, Cat(xe =/= 0.U, xf), Cat(ye =/= 0.U, yf)) >> xe_ye) (23, 0)
    } else {
      (Mux(y_gt_x, Cat(xe =/= 0.U, xf, 0.U(2.W)), Cat(ye =/= 0.U, yf, 0.U(2.W))) >> xe_ye) (25, 0)
    }
  )
  val xs_ys = xs === ys
  /*----- add -----*/
  val zf = if (rm == 0) {
    Mux(xs_ys, xf_1 +& yf_1, xf_1 - yf_1)(24, 0)
  } else {
    Mux(xs_ys, Cat(xf_1 +& yf_1(25, 2), yf_1(1, 0)), Cat(xf_1, 0.U(2.W)) - yf_1)(26, 0)
  }
  /* ---------------------- PIPELINE A ---------------------- */
  /*-- normalize --*/
  val zf_1 = RegNext(zf)
  // val offset = Mux(ze_2 === 0.U, 0.U, 22.U) - Log2(zf_1(23 + suflen, 1 + suflen)) // why?
  val offset = 22.U - Log2(zf_1(23 + suflen, 1 + suflen))

  val zs_1 = RegNext(zs)
  val shr: Int = config("shift_right")
  val ze_1 = RegNext(if (shr > 0) ze - shr.U else ze)
  val ze_is_ff_1 = RegNext(ze_is_ff)
  val is_NaN_1 = RegNext(is_NaN)

  /*-------- rounding ------*/
  val zf_10 = (zf_1 << offset) (24, 0)
  val overflow = Mux(ze_1 === 0xfe.U && zf_1(24 + suflen), true.B, false.B)
  val out_z = Cat(zs_1, rm match {
    case 0 => PriorityMux(Seq(
      (ze_is_ff_1 || overflow) -> Mux(is_NaN_1, ~0.U(31.W), 0x7f800000.U(31.W)), // NaN, Inf
      (ze_1 < offset) -> 0.U(31.W), // underflow
      zf_1(24) -> Cat(ze_1 + 1.U, zf_1(23, 1)), // shift right
      true.B -> Cat(ze_1 - offset, zf_10(22, 0)), // shift left (offset can be 0)
    ))
    case 1 => PriorityMux(Seq(
      (ze_is_ff_1 || overflow) -> Mux(is_NaN_1, ~0.U(31.W), 0x7f800000.U(31.W)), // NaN, Inf
      (ze_1 < offset) -> 0.U(31.W), // underflow
      zf_1(26) -> Cat(ze_1 + 1.U, zf_1(25, 3) + (zf_1(2) & (zf_1(1) | zf_1(3) | zf_1(0)))), // shift right
      true.B -> Cat(ze_1 - offset, zf_10(24, 2) + (zf_10(1) & (zf_10(2) | zf_10(0)))), // shift left (offset can be 0)
    ))
    case 2 | 3 => PriorityMux(Seq(
      (ze_is_ff_1 || overflow) -> Mux(is_NaN_1, ~0.U(31.W), 0x7f800000.U(31.W)), // NaN, Inf
      (ze_1 < offset) -> 0.U(31.W), // underflow
      zf_1(26) -> Cat(ze_1 + 1.U, zf_1(25, 3) + (zf_1(2, 0) =/= 0.U & (zs_1 ^ (rm & 1).U))), // shift right
      true.B -> Cat(ze_1 - offset, zf_10(24, 2) + (zf_10(1, 0) =/= 0.U & (zs_1 ^ (rm & 1).U))), // shift left (offset can be 0)
    ))
  })

  io.z := (if (config("output_buffer")) RegNext(out_z) else out_z)

  io.overflow_flag := (if (config("overflow_flag")) if (config("output_buffer")) RegNext(overflow) else overflow else DontCare)
  io.underflow_flag := (if (config("underflow_flag")) if (config("output_buffer")) RegNext(ze_1 < offset) else ze_1 < offset else DontCare)
  io.Inf_flag := (if (config("Inf_flag")) if (config("output_buffer")) RegNext(ze_is_ff_1 && !is_NaN_1) else ze_is_ff_1 && !is_NaN_1 else DontCare)
  io.NaN_flag := (if (config("NaN_flag")) if (config("output_buffer")) RegNext(ze_is_ff_1 && is_NaN_1) else ze_is_ff_1 && is_NaN_1 else DontCare)
}