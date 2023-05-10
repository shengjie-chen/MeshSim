//import chisel3.{BlackBox, _}
//import chisel3.stage.ChiselGeneratorAnnotation
//import chisel3.util.{HasBlackBoxPath, _}
//
//trait Convert {
//    implicit def AnyToInt(a: Any): Int = Integer.parseInt(a.toString)
//
//    implicit def AnyToBoolean(a: Any): Boolean = a == true
//
//    implicit def AnyToString(a: Any): String = a.toString
//
//    implicit def FP32ToUInt(f: FP32): UInt = f.asUInt()
//
//    implicit def UIntToFP32(f: UInt): FP32 = FP32(f)
//
//    implicit def BooleanToInt(b: Boolean): Int = if (b) 1 else 0
//
//    implicit def BoolToUInt(b: Bool): UInt = b.asUInt
//
//    implicit def UIntToBool(u: UInt): Bool = u.asBool
//}
//
//class FP32 {
//    var bits: Bits = _
//
//    def this(bits: Bits) {
//        this()
//        this.bits = bits
//    }
//
//    def this(fp: FP32) {
//        this()
//        this.bits = fp.bits
//    }
//
//    def asUInt(): UInt = {
//        this.bits.asUInt
//    }
//
//    def +(that: FP32)(implicit config: Map[String, Any] = Map()): FP32 = {
//        val adder = Module(new FP32_FastAdder(config))
//        adder.io.x := this.bits.asUInt
//        adder.io.y := that.bits.asUInt
//        new FP32(adder.io.z)
//    }
//
//    def -(that: FP32)(implicit config: Map[String, Any] = Map()): FP32 = {
//        this + new FP32(Cat(~that.bits(31), that.bits(30, 0)))
//    }
//
//    def *(that: FP32)(implicit config: Map[String, Any] = Map()): FP32 = {
//        val mult = Module(new FP32_Mult(config))
//        mult.io.x := this.bits.asUInt
//        mult.io.y := that.bits.asUInt
//        new FP32(mult.io.z)
//    }
//
//    /* xs != ys -> ys
//     * xs == ys -> ((xe,xf) > (ys,yf)) ^ xs - */
//    def >(that: FP32): Bool = {
//        Mux(this.bits(31) ^ that.bits(31), that.bits(31), (this.bits.asUInt(30, 0) > that.bits.asUInt(30, 0)) ^ this.bits(31))
//    }
//
//    def <(that: FP32): Bool = {
//        Mux(this.bits(31) ^ that.bits(31), this.bits(31), (this.bits.asUInt(30, 0) < that.bits.asUInt(30, 0)) ^ this.bits(31))
//    }
//
//}
//
//object FP32 {
//    def apply(bits: Bits): FP32 = {
//        new FP32(bits)
//    }
//}
//
//class FP32_Adder(parameters: Map[String, Any]) extends Module with Convert {
//
//    val default_config = Map(
//        "input_buffer" -> false, // add regs between (input) and (pipeline stage 1)
//        "output_buffer" -> true, // add regs before output
//        "valid" -> true, // valid signal
//        "only_positive" -> false, // if x,y is guaranteed > 0, set true to reduce hardware resources
//        "strict" -> false, // compatible with C/C++ standard library
//        "shift_right" -> 0, // shift right when adding
//        "rounding" -> "even", // even/zero/floor/ceil
//        "overflow_flag" -> false,
//        "underflow_flag" -> false,
//        "Inf_flag" -> false,
//        "NaN_flag" -> false
//    )
//
//    val config = default_config ++ parameters
//    val rm = config("rounding").toLowerCase + "" match {
//        case "0" | "zero" | "none" => 0
//        case "1" | "even" | "nearest" => 1
//        case "2" | "floor" | "-inf" => 2
//        case "3" | "ceil" | "inf" => 3
//    }
//    val suflen = if (rm == 0) 0 else 2
//
//    /* z = x + y */
//    val io = IO(new Bundle {
//        val x = Input(UInt(32.W))
//        val y = Input(UInt(32.W))
//        val z = Output(UInt(32.W))
//        val valid_in = if (config("valid")) Some(Input(Bool())) else None
//        val valid_out = if (config("valid")) Some(Output(Bool())) else None
//        val overflow_flag = if (config("overflow_flag")) Some(Output(Bool())) else None
//        val underflow_flag = if (config("underflow_flag")) Some(Output(Bool())) else None
//        val Inf_flag = if (config("Inf_flag")) Some(Output(Bool())) else None
//        val NaN_flag = if (config("NaN_flag")) Some(Output(Bool())) else None
//    })
//    if (config("valid")) io.valid_out.get := ShiftRegister(io.valid_in.get, 1 + (if (config("input_buffer")) 1 else 0) + (if (config("output_buffer")) 1 else 0))
//
//    /*--- pipeline stage 0 ---*/
//    /*---- start ----*/
//    val (xs, ys) = if (config("only_positive")) {
//        (0.U(1.W), 0.U(1.W))
//    } else {
//        if (config("input_buffer")) {
//            (RegNext(io.x(31)), RegNext(io.y(31)))
//        } else {
//            (io.x(31), io.y(31))
//        }
//    }
//
//    val (xe, xf, ye, yf) = if (config("input_buffer")) {
//        (RegNext(io.x(30, 23)), RegNext(io.x(22, 0)), RegNext(io.y(30, 23)), RegNext(io.y(22, 0)))
//    } else {
//        (io.x(30, 23), io.x(22, 0), io.y(30, 23), io.y(22, 0))
//    }
//
//    val xe_is_ff = xe === 0xff.U
//    val ye_is_ff = ye === 0xff.U
//    val ze_is_ff = if (config("strict")) xe_is_ff || ye_is_ff else false.B
//
//    val is_NaN = if (config("strict")) {
//        (xe_is_ff && xf =/= 0.U) || (ye_is_ff && yf =/= 0.U) || (xe_is_ff && ye_is_ff && (xs ^ ys).asBool)
//    } else {
//        (xe_is_ff && xf =/= 0.U) || (ye_is_ff && yf =/= 0.U)
//    }
//    val y_gt_x = Cat(ye, yf) > Cat(xe, xf) // if |y|>|x|
//    val zs = Mux(y_gt_x, ys, xs)
//    val ze = Mux(y_gt_x, ye, xe)
//    val xf_1 = Mux(y_gt_x, Cat(ye =/= 0.U, yf), Cat(xe =/= 0.U, xf))
//    /*---- align ----*/
//    val xe_ye = ze - Mux(y_gt_x, xe, ye)
//    val yf_1 = Mux(ze_is_ff, 0.U,
//        if (rm == 0) {
//            (Mux(y_gt_x, Cat(xe =/= 0.U, xf), Cat(ye =/= 0.U, yf)) >> xe_ye)(23, 0)
//        } else {
//            (Mux(y_gt_x, Cat(xe =/= 0.U, xf, 0.U(2.W)), Cat(ye =/= 0.U, yf, 0.U(2.W))) >> xe_ye)(25, 0)
//        }
//    )
//    val xs_ys = xs === ys
//    val nz = RegNext((Mux(y_gt_x, Cat(xf, 0.U(23.W)), Cat(yf, 0.U(23.W))) >> xe_ye)(20, 0) =/= 0.U)
//    /*----- add -----*/
//    val zf = if (rm == 0) {
//        Mux(xs_ys, xf_1 +& yf_1, xf_1 - yf_1)(24, 0)
//    } else {
//        Mux(xs_ys, Cat(xf_1 +& yf_1(25, 2), yf_1(1, 0)), Cat(xf_1, 0.U(2.W)) - yf_1)(26, 0)
//    }
//    /* ---------------------- PIPELINE A ---------------------- */
//    /*-- normalize --*/
//    val zf_1 = RegNext(zf)
//    // val offset = Mux(ze_2 === 0.U, 0.U, 22.U) - Log2(zf_1(23 + suflen, 1 + suflen)) // why?
//    val offset = 22.U - Log2(zf_1(23 + suflen, 1 + suflen))
//
//    val zs_1 = RegNext(zs)
//    val shr: Int = config("shift_right")
//    val ze_1 = RegNext(if (shr > 0) ze - shr.U else ze)
//    val ze_is_ff_1 = RegNext(ze_is_ff)
//    val is_NaN_1 = RegNext(is_NaN)
//
//    /*-------- rounding ------*/
//    val zf_10 = (zf_1 << offset)(24, 0)
//    val overflow = Mux(ze_1 === 0xfe.U && zf_1(24 + suflen), true.B, false.B)
//    val out_z = Cat(zs_1, rm match {
//        case 0 => PriorityMux(Seq(
//            (ze_is_ff_1 || overflow) -> Mux(is_NaN_1, ~0.U(31.W), 0x7f800000.U(31.W)), // NaN, Inf
//            (ze_1 < offset) -> 0.U(31.W), // underflow
//            zf_1(24) -> Cat(ze_1 + 1.U, zf_1(23, 1)), // shift right
//            true.B -> Cat(ze_1 - offset, zf_10(22, 0)), // shift left (offset can be 0)
//        ))
//        case 1 => PriorityMux(Seq(
//            (ze_is_ff_1 || overflow) -> Mux(is_NaN_1, ~0.U(31.W), 0x7f800000.U(31.W)), // NaN, Inf
//            (ze_1 < offset) -> 0.U(31.W), // underflow
//            zf_1(26) -> Cat(ze_1 + 1.U, zf_1(25, 3) + (zf_1(2) & (zf_1(3) | nz | zf_1(1) | zf_1(0)))), // shift right (never shift right when xs != ys)
//            true.B -> Cat(ze_1 - offset, zf_10(24, 2) + (zf_10(1) & Mux(RegNext(xs_ys), zf_10(2) | nz | zf_10(0), zf_10(0) | (zf_10(2) & !nz)))), // shift left (offset can be 0)
//        ))
//        case 2 | 3 => PriorityMux(Seq(
//            (ze_is_ff_1 || overflow) -> Mux(is_NaN_1, ~0.U(31.W), 0x7f800000.U(31.W)), // NaN, Inf
//            (ze_1 < offset) -> 0.U(31.W), // underflow
//            zf_1(26) -> Cat(ze_1 + 1.U, zf_1(25, 3) + (zf_1(2, 0) =/= 0.U & (zs_1 ^ (rm & 1).U))), // shift right
//            true.B -> Cat(ze_1 - offset, zf_10(24, 2) + (zf_10(1, 0) =/= 0.U & (zs_1 ^ (rm & 1).U))), // shift left (offset can be 0)
//        ))
//    })
//
//    io.z := (if (config("output_buffer")) RegNext(out_z) else out_z)
//
//    if (config("overflow_flag")) io.overflow_flag.get := (if (config("output_buffer")) RegNext(overflow) else overflow)
//    if (config("overflow_flag")) io.underflow_flag.get := (if (config("output_buffer")) RegNext(ze_1 < offset) else ze_1 < offset)
//    if (config("overflow_flag")) io.Inf_flag.get := (if (config("output_buffer")) RegNext(ze_is_ff_1 && !is_NaN_1) else ze_is_ff_1 && !is_NaN_1)
//    if (config("overflow_flag")) io.NaN_flag.get := (if (config("output_buffer")) RegNext(ze_is_ff_1 && is_NaN_1) else ze_is_ff_1 && is_NaN_1)
//}
//
//object FP32_Adder {
//    def apply(parameters: Map[String, Any] = Map()) = Module(new FP32_Adder(parameters))
//
//    def get_stages: Int = 3
//}
//
//class FP32_FastAdder(parameters: Map[String, Any]) extends Module with Convert {
//
//    val default_config = Map(
//        "strict" -> false, // check if input is NaN or Inf
//    )
//
//    val config = default_config ++ parameters
//
//    val io = IO(new Bundle {
//        val x = Input(UInt(32.W))
//        val y = Input(UInt(32.W))
//        val z = Output(UInt(32.W))
//    })
//
//    val (xs, xe, xf, ys, ye, yf) = (io.x(31), io.x(30, 23), io.x(22, 0), io.y(31), io.y(30, 23), io.y(22, 0))
//    /*-------- start --------*/
//    val ze_is_ff = xe === 0xff.U || ye === 0xff.U
//    val y_gt_x = io.y(30, 0) > io.x(30, 0)
//    val is_NaN = if (config("strict")) (xe === 0xff.U && xf =/= 0.U) || (ye === 0xff.U && yf =/= 0.U) else false.B
//    val ze = Mux(y_gt_x, ye, xe)
//    val xf1 = Mux(y_gt_x, Cat(ye =/= 0.U, yf), Cat(xe =/= 0.U, xf))
//    /*-------- align --------*/
//    val yf0 = Mux(y_gt_x, Cat(xe =/= 0.U, xf), Cat(ye =/= 0.U, yf))
//    val xe_ye = Mux(y_gt_x, ye, xe) - Mux(y_gt_x, xe, ye)
//    val yf1 = Mux(ze_is_ff, 0.U(24.W), (yf0 >> xe_ye)(23, 0))
//    /*--------- add ---------*/
//    val zf = Mux(xs === ys, xf1 +& yf1, xf1 - yf1)
//    /*------ normalize ------*/
//    val offset = 22.U - Log2(zf(23, 1))
//    io.z := Cat(Mux(y_gt_x, ys, xs), PriorityMux(Seq(
//        ze_is_ff -> Mux(is_NaN, ~0.U(31.W), 0x7f800000.U(31.W)), // NaN, Inf
//        (ze < offset) -> 0.U(31.W), // underflow
//        zf(24) -> Cat(ze + 1.U, zf(23, 1)), // shift right
//        true.B -> Cat(ze - offset, (zf << offset)(22, 0)), // shift left (offset can be 0)
//    )))
//}
//
//object FP32_FastAdder {
//    def apply(parameters: Map[String, Any] = Map()) = Module(new FP32_FastAdder(parameters))
//}
//
//class FP32_Mult(parameters: Map[String, Any]) extends Module with Convert {
//
//    val default_config = Map(
//        "flag" -> false,
//        "input_buffer" -> false, // add regs between (input) and (pipeline stage 1)
//        "output_buffer" -> true // add regs before output
//    )
//
//    val config = default_config ++ parameters
//
//    val io = IO(new Bundle {
//        val x = Input(UInt(32.W))
//        val y = Input(UInt(32.W))
//        val z = Output(UInt(32.W))
//        /* flag[3:0] = {overflow(8), underflow(4), NaN(2), Inf(1)} */
//        val flag = Output(if (config("flag")) UInt(4.W) else UInt(0.W))
//    })
//
//    val xe = io.x(30, 23)
//    val ye = io.y(30, 23)
//    val xf = io.x(22, 0)
//    val yf = io.y(22, 0)
//
//    val ze_is_ff = RegNext(xe === 0xff.U || ye === 0xff.U)
//    val is_NaN = RegNext((xe === 0xff.U && xf =/= 0.U) || (ye === 0xff.U && yf =/= 0.U))
//    val is_zero = RegNext(xe === 0.U || ye === 0.U)
//
//    val zf_0 = Cat(1.U, io.x(22, 0)) * Cat(1.U, io.y(22, 0))
//    val zf_1 = RegNext(zf_0(46, 22))
//
//    val carry = RegNext(zf_0(47)) // || (zf_0(46, 22) === 0x1ffffff.U)
//    val zf = Mux(carry, zf_1(24, 2) + zf_1(1), zf_1(23, 1) + zf_1(0))
//    val ze = RegNext(xe +& ye) - Mux(carry, 126.U, 127.U)
//
//    val out_z = Cat(RegNext(io.x(31) ^ io.y(31)), PriorityMux(Seq(
//        ze_is_ff -> Mux(is_NaN, ~0.U(31.W), 0x7f800000.U(31.W)), // NaN, Inf
//        is_zero -> 0.U(31.W), // zero
//        // (ze(8) =/= 0.U) -> Mux(ze(9), 0.U(31.W), 0x7f800000.U(31.W)), // underflow, overflow
//        true.B -> Cat(ze(7, 0), zf)
//    )))
//
//    io.z := (if (config("output_buffer")) RegNext(out_z) else out_z)
//
//    if (config("flag")) {
//        io.flag := PriorityMux(Seq(
//            ze_is_ff -> Mux(is_NaN, 2.U, 1.U),
//            is_zero -> 0.U,
//            (ze(9, 8) =/= 0.U) -> Mux(ze(9), 4.U, 8.U),
//            true.B -> 0.U
//        ))
//    } else {
//        io.flag := DontCare
//    }
//
//}
//
//object FP32_Mult {
//    def apply(parameters: Map[String, Any] = Map()) = Module(new FP32_Mult(parameters))
//}
//
//object TestMain2 extends App {
//    (new chisel3.stage.ChiselStage).execute(Array("--target-dir", "generated"), Seq(ChiselGeneratorAnnotation(() => new FP32_Mult(Map()))))
//    (new chisel3.stage.ChiselStage).execute(Array("--target-dir", "generated"), Seq(ChiselGeneratorAnnotation(() => new FP32_Adder(Map()))))
//}