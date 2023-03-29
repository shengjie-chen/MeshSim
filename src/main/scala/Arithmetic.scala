import chisel3._
import chisel3.util._
import hardfloat._

case class Float(val exp_width : Int, val sig_width : Int) extends Bundle{
  val bits=UInt((exp_width+sig_width).W)
}

object Float{
  def apply(a:UInt,exp_width:Int = 8,sig_width:Int = 24):Float = {
    val result = Wire(Float(exp_width,sig_width))
    result.bits := a
    result
  }
  def FloatToSInt(a:Float,intWidth:Int):SInt = {
    val convert = Module(new RecFNToIN(a.exp_width,a.sig_width,intWidth))
    convert.io.in := recFNFromFN(a.exp_width,a.sig_width,a.bits)
    convert.io.roundingMode := consts.round_near_maxMag
    convert.io.signedOut := true.B
    RegNext(convert.io.out.asSInt)
  }
  def SIntToFloat(a:SInt,intWidth:Int,exp_width:Int = 8,sig_width:Int = 24):Float = {
    val convert = Module(new INToRecFN(intWidth,exp_width,sig_width))
    convert.io.signedIn := true.B
    convert.io.in := a.asUInt
    convert.io.roundingMode := consts.round_near_even
    convert.io.detectTininess := consts.tininess_afterRounding
    Float(RegNext(fNFromRecFN(exp_width, sig_width, convert.io.out)))
  }
  implicit def Float2Bits(x:Float):UInt = x.bits

}

abstract class Arithmetic[ T <: Data]{
  implicit def cast(a : T) : ArithmeticOps[T]
}

abstract class ArithmeticOps[T <: Data](a : T){
  def +(b : T) : T
  def *(b : T) : T
  def abs:T
}

object Arithmetic{
  implicit object ArithmeticSInt extends Arithmetic[SInt]{
    implicit def cast(a: SInt) = new ArithmeticOps(a) {
      override def +(b: SInt) = RegNext(a+b)
      override def *(b: SInt) = RegNext(a*b)
      override def abs = RegNext(Mux(a(a.getWidth-1).asBool,-a,a))
    }
  }
  implicit object ArithmeticUInt extends Arithmetic[UInt]{
    implicit def cast(a: UInt) = new ArithmeticOps[UInt](a) {
      override def +(b: UInt) = RegNext(a+b)
      override def *(b: UInt) = RegNext(a*b)
      override def abs = RegNext(a)
    }
  }
  implicit object ArithmeticFloat extends Arithmetic[Float]{
    implicit def cast(a: Float) = new ArithmeticOps[Float](a) {
      override def +(b: Float) = {
        val a_rec = recFNFromFN(a.exp_width,a.sig_width,a.bits)
        val b_rec = recFNFromFN(b.exp_width,b.sig_width,b.bits)
        val adder = Module(new AddRecFN(a.exp_width,a.sig_width)).io
        adder.subOp := false.B
        adder.a := RegNext(a_rec)
        adder.b := RegNext(b_rec)
        adder.roundingMode := consts.round_near_even
        adder.detectTininess := consts.tininess_afterRounding
        Float(RegNext(fNFromRecFN(a.exp_width,a.sig_width,adder.out)))
//        val adder = Module(new FP32_Adder)
//        adder.io.x := a.bits
//        adder.io.y := b.bits
//        adder.io.valid_in := 1.U
//        Float(adder.io.z,32)
      }
      override def *(b: Float) = {
        val a_rec = recFNFromFN(a.exp_width, a.sig_width, a.bits)
        val b_rec = recFNFromFN(b.exp_width, b.sig_width, b.bits)
        val muler = Module(new MulRecFN(a.exp_width, a.sig_width)).io
        muler.a := RegNext(a_rec)
        muler.b := RegNext(b_rec)
        muler.roundingMode := consts.round_near_even
        muler.detectTininess := consts.tininess_afterRounding
        Float(RegNext(fNFromRecFN(a.exp_width, a.sig_width, muler.out)))
      }
      override def abs = Float(RegNext(Cat(0.U(1.W),a.bits(a.bits.getWidth-2,0))))
    }
  }
}
/*-----------------------------------------------------------------*/
//FP32 ADD MUL TEST
/*-----------------------------------------------------------------*/
//class tcp(implicit env:Arithmetic[Float]) extends Module{
//  import env._
//  val io = IO(new Bundle() {
//    val a = Input(UInt(32.W))
//    val b = Input(UInt(32.W))
//    val i_valid = Input(Bool())
//    val op = Input(UInt(2.W))
//    val c = Output(UInt(32.W))
//    val o_valid = Output(Bool())
//  })
//
//  //when you are running the simulation, must reg on cycle!
//  val a = RegNext(io.a)
//  val b = RegNext(io.b)
//  val op = RegNext(io.op)
//  val i_valid = RegNext(io.i_valid)
//
//  io.c := MuxCase(0.U,Array(
//    (op === 0.U) -> (Float(a) + Float(b)).bits,
//    (op === 1.U) -> (Float(a) * Float(b)).bits,
//  ))
//
//  io.o_valid := ShiftRegister(i_valid,2)
//}

/*-----------------------------------------------------------------*/
//FP32 TO INT8 TEST
/*-----------------------------------------------------------------*/
//class tcp extends Module{
//  val io = IO(new Bundle() {
//    val a = Input(UInt(32.W))
//    val i_valid = Input(Bool())
//    val c = Output(UInt(8.W))
//    val o_valid = Output(Bool())
//  })
//  //when you are running the simulation, must reg on cycle!
//  val a = RegNext(io.a)
//  val i_valid = RegNext(io.i_valid)
//
//  io.c := Float.FloatToSInt(Float(a),8).asUInt
//  io.o_valid := RegNext(i_valid)
//}

/*-----------------------------------------------------------------*/
//INT32 TO FP32 TEST
/*-----------------------------------------------------------------*/
//class tcp extends Module{
//  val io = IO(new Bundle() {
//    val a = Input(UInt(32.W))
//    val i_valid = Input(Bool())
//    val c = Output(UInt(32.W))
//    val o_valid = Output(Bool())
//  })
//  //when you are running the simulation, must reg on cycle!
//  val a = RegNext(io.a)
//  val i_valid = RegNext(io.i_valid)
//
//  io.c := Float.SIntToFloat(a.asSInt,32).bits
//  io.o_valid := RegNext(i_valid)
//}

//import chisel3.stage.ChiselGeneratorAnnotation
//object tcp_gen extends App{
//  new (chisel3.stage.ChiselStage).execute(Array("--target-dir","./verilog/test/fp32"),Seq(ChiselGeneratorAnnotation(()=>new tcp)))
//}


