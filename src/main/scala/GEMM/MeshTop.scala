package GEMM

//import Chisel3.Output
import chisel3.{Bool, Bundle, Input, Module, Output, SInt, Vec}
import chisel3._
import chisel3.util.{Log2, ShiftRegister, log2Up}

class MeshTop(inputWidth: Int, accWidth: Int,
              val meshRows: Int, val meshColumns: Int, val accMemRow: Int = 64) extends Module {
  val in_a_width = inputWidth*meshRows
  val in_b_width = inputWidth*meshColumns
  val io = IO(new Bundle {
    val in_a = Input(UInt(in_a_width.W))
    val in_a_valid = Input(Bool())
    val out_a_ready = Output(Bool())
    val in_b = Input(UInt(in_b_width.W))
    val in_b_valid = Input(Bool())
    val out_b_ready = Output(Bool())
    val in_d = Input(Vec(meshColumns, SInt(accWidth.W)))
    val in_control = Input(new PEControl)
    val in_valid = Input(Bool())

    val in_c_height = Input(UInt(8.W)) //icg max255
    val in_c_group = Input(UInt(8.W)) //ocg    c.h = in_height * in_group
    val in_acc_mode = Input(UInt(2.W)) //0.U -- normal add, 1.U -- cover(no add), 2.U -- add2 (fir 2way), 3.U -- add4

    val in_accmem_ren = Input(Bool())

    val out_r = Output(Vec(meshColumns, SInt(accWidth.W)))
    val out_r_valid = Output(Bool())
    val out_accmem_rready = Output(Bool())
  })
  val accmem_full = Wire(Bool())
  val mesh=Module(new Mesh(inputWidth,accWidth,meshRows,meshColumns))
  mesh.io.in_a       := io.in_a
  mesh.io.in_a_valid := io.in_a_valid
  mesh.io.in_b       := io.in_b
  mesh.io.in_b_valid := io.in_b_valid
  mesh.io.in_d       := io.in_d

  mesh.io.in_control := io.in_control
  mesh.io.in_valid   := io.in_valid

  val acc_mem = Module(new AccMem(accMemRow,accWidth, meshRows, meshColumns))
  acc_mem.io.in_c         := mesh.io.out_c
  acc_mem.io.in_c_valid   := mesh.io.out_valid
  acc_mem.io.in_c_height  := io.in_c_height
  acc_mem.io.in_c_group   := io.in_c_group
  acc_mem.io.in_acc_mode  := io.in_acc_mode
  acc_mem.io.in_datatype  := io.in_control.datatype
  acc_mem.io.in_accmem_ren:= io.in_accmem_ren

  io.out_r := acc_mem.io.out_r
  io.out_r_valid := acc_mem.io.out_r_valid
  io.out_accmem_rready := acc_mem.io.out_accmem_rready
  accmem_full := acc_mem.io.out_accmem_full

  val b_cnt = RegInit(0.U(8.W)) //unused b in mesh
  val c_height_cnt = RegInit(0.U(8.W))//from mesh output,change b
  //val c_height_cnt = RegInit(0.U(8.W))

  when(mesh.io.out_valid(0)){
    when(c_height_cnt === io.in_c_height-1.U){
      c_height_cnt := 0.U
    }.otherwise{
      c_height_cnt := c_height_cnt + 1.U
    }
  }

  when(io.in_b_valid) {
    when(c_height_cnt === io.in_c_height - 1.U) {
      b_cnt := b_cnt - meshRows.U + 1.U
    }.otherwise {
      b_cnt := b_cnt + 1.U
    }
  }.otherwise{
    when(c_height_cnt === io.in_c_height - 1.U) {
      b_cnt := b_cnt - meshRows.U
    }
  }

  io.out_b_ready := (b_cnt <= meshRows.U)
  io.out_a_ready := (b_cnt >= meshRows.U) && !accmem_full
}