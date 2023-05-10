
import chisel3._
import chisel3.util._

//----------------------Get Rise Edge----------------------//
class riseEdge extends Module{
  val io = IO(new Bundle() {
    val in = Input(Bool())
    val edge = Output(Bool())
  })
  io.edge := !RegNext(io.in) && io.in
}
object riseEdge{
  def apply(in:Bool):Bool = {
    val inst = Module(new riseEdge)
    inst.io.in := in
    inst.io.edge
  }
}

//----------------------Get Fall Edge----------------------//
class fallEdge extends Module{
  val io = IO(new Bundle() {
    val in = Input(Bool())
    val edge = Output(Bool())
  })
  io.edge := RegNext(io.in) && !io.in
}
object fallEdge{
  def apply(in:Bool):Bool = {
    val inst = Module(new fallEdge)
    inst.io.in := in
    inst.io.edge
  }
}

//----------------------Get Dual Edge----------------------//
class dualEdge extends Module{
  val io = IO(new Bundle() {
    val in = Input(Bool())
    val edge = Output(Bool())
  })
  io.edge := RegNext(io.in) ^ io.in
}
object dualEdge{
  def apply(in:Bool):Bool = {
    val inst = Module(new dualEdge)
    inst.io.in := in
    inst.io.edge
  }
}

class align(width:Int, n:Int) extends Module{
  val io = IO(new Bundle() {
    val in = Input(UInt(width.W))
    val out = Output(UInt(width.W))
  })
  io.out := (io.in + (n-1).U) & -n.S(width.W).asUInt
}
object align{
  def apply(x:UInt,n:Int):UInt = {
    val inst = Module(new align(x.getWidth,n))
    inst.io.in := x
    inst.io.out
  }

  def apply(x:UInt, wdith:Int, n:Int):UInt = {
    val inst = Module(new align(wdith, n))
    inst.io.in := x
    inst.io.out
  }
}

//----------------------Single Port Ram----------------------//
//ram_style: distributed / block / ultra
class SPRAM(data_width:Int, depth:Int, ram_style:String) extends BlackBox(
  Map("DATA_WIDTH"->data_width, "DEPTH"->depth, "RAM_STYLE_VAL"->ram_style)) with HasBlackBoxPath{
  val io = IO(new Bundle{
    val clock = Input(Clock())
    val en = Input(Bool())
    val wr = Input(Bool())   //0:W 1:R
    val addr = Input(UInt(log2Ceil(depth).W))
    val wdata = Input(UInt(data_width.W))
    val rdata = Output(UInt(data_width.W))
  })
  addPath("./src/main/hdl/SPRAM.v")
}

object SPRAM{
  def apply(data_width:Int,depth:Int,ram_style:String) = Module(new SPRAM(data_width, depth, ram_style)).io
}

//----------------------Pseudo Dual Port Ram----------------------//
//ram_style: distributed / block / ultra
class TPRAM(data_width:Int,depth:Int,ram_style:String) extends BlackBox(
  Map("DATA_WIDTH"->data_width,"DEPTH"->depth,"RAM_STYLE_VAL"->ram_style)) with HasBlackBoxPath{
  val io = IO(new Bundle{
    val clock = Input(Clock())
    val wen = Input(Bool())
    val ren = Input(Bool())
    val waddr = Input(UInt(log2Ceil(depth).W))
    val raddr = Input(UInt(log2Ceil(depth).W))
    val wdata = Input(UInt(data_width.W))
    val rdata = Output(UInt(data_width.W))
  })
  addPath("./src/main/hdl/TPRAM.v")
}

object TPRAM{
  def apply(data_width:Int,depth:Int,ram_style:String) = Module(new TPRAM(data_width, depth, ram_style)).io
}

//----------------------True Dual Port Ram----------------------//
//ram_style: distributed / block / ultra
class DPRAM(data_width:Int,depth:Int,ram_style:String) extends BlackBox(
  Map("DATA_WIDTH"->data_width,"DEPTH"->depth,"RAM_STYLE_VAL"->ram_style)) with HasBlackBoxPath{
  val io = IO(new Bundle() {
    val clock = Input(Clock())
    val wr_a = Input(Bool())
    val wr_b = Input(Bool())
    val en_a = Input(Bool())
    val en_b = Input(Bool())
    val addr_a = Input(UInt(log2Ceil(depth).W))
    val addr_b = Input(UInt(log2Ceil(depth).W))
    val wdata_a = Input(UInt(data_width.W))
    val wdata_b = Input(UInt(data_width.W))
    val rdata_a = Output(UInt(data_width.W))
    val rdata_b = Output(UInt(data_width.W))
  })
  addPath("./src/main/hdl/DPRAM.v")
}

object DPRAM{
  def apply(data_width:Int,depth:Int,ram_style:String) = Module(new DPRAM(data_width, depth, ram_style)).io
}

