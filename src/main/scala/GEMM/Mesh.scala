package GEMM

import chisel3._
import chisel3.util._

/**
  * A Grid is a 2D array of Tile modules with registers in between each tile and
  * registers from the bottom row and rightmost column of tiles to the Grid outputs.
  * @param width
  * @param meshRows
  * @param meshColumns
  */
class Mesh (inputWidth: Int, accWidth: Int,
                                   val meshRows: Int, val meshColumns: Int) extends Module {
  val io = IO(new Bundle {
    val in_a = Input(Vec(meshRows, SInt(inputWidth.W)))
    val in_a_valid = Input(Bool())
    val in_b = Input(Vec(meshColumns, SInt(inputWidth.W)))
    val in_b_valid = Input(Bool())
    val in_d = Input(Vec(meshColumns, SInt(accWidth.W)))
    val in_control = Input(new PEControl)

    val out_c = Output(Vec(meshColumns, SInt(accWidth.W)))
    val in_valid = Input(Bool())  ////
    val out_valid = Output(Vec(meshColumns,  Bool()))   /////
  })


  val datatype = io.in_control.datatype
  val prop = io.in_control.propagate
  // mesh(r)(c) => PE at row r, column c
  val mesh = Seq.fill(meshRows, meshColumns)(Module(new PE(inputWidth, accWidth)))
  val meshT = mesh.transpose

  def pipe[T <: Data](valid: Bool, t: T, latency: Int): T = {
    // The default "Pipe" function apparently resets the valid signals to false.B. We would like to avoid using global
    // signals in the Mesh, so over here, we make it clear that the reset signal will never be asserted
    chisel3.withReset(false.B) { Pipe(valid, t, latency).bits }
  }

  val pe_delay_cnt=RegInit(0.U(4.W))
  when(datatype =/= 2.U || (io.in_a_valid && pe_delay_cnt === 2.U)) {
    pe_delay_cnt := 0.U
  }.elsewhen(io.in_a_valid){
    pe_delay_cnt := pe_delay_cnt + 1.U
  }
  val shift_a_valid = pe_delay_cnt === 0.U

  // (pipeline a across each row)
  for (r <- 0 until meshRows) {
    mesh(r).foldLeft(ShiftRegister(io.in_a(r),r)) {
      case (in_a, pe) =>
        pe.io.in_a := pipe(shift_a_valid, in_a, 1)
        pe.io.out_a
    }
  }

  // (pipeline a_valid across each row)
  for (r <- 0 until meshRows) {
    mesh(r).foldLeft(ShiftRegister(io.in_a_valid, r)) {
      case (in_a_valid, pe) =>
        pe.io.in_a_valid := pipe(shift_a_valid, in_a_valid, 1)
        pe.io.out_a_valid
    }
  }

  // Chain pe_out_b -> pe_b_in (pipeline b across each column)
  for (c <- 0 until meshColumns) {
    meshT(c).foldLeft(ShiftRegister(io.in_b(c),1)) {
      case (in_b, pe) =>
        pe.io.in_b := in_b
        pe.io.out_b
    }
  }

  // Chain in_valid (pipeline across each column)
  for (c <- 0 until meshColumns) {
    meshT(c).foldLeft(io.in_valid) {
      case (in_v, pe) =>
        pe.io.in_d_valid := ShiftRegister(in_v, 1)
        pe.io.out_valid
    }
  }

  // Chain tile_out -> tile_propag (pipeline output across each column)
  for (c <- 0 until meshColumns) {
    meshT(c).foldLeft((io.in_d(c), io.in_valid)) {
      case ((in_d, valid), pe) =>
        pe.io.in_d := pipe(valid, in_d, 1)
        (pe.io.out_c, pe.io.out_valid)
    }
  }

  // Broadcast control signals & in_b_valid
  for (c <- 0 until meshColumns) {
    for (r <- 0 until meshRows){
      mesh(r)(c).io.in_control := ShiftRegister(io.in_control,1)
      mesh(r)(c).io.in_b_valid := ShiftRegister(io.in_b_valid,1)
    }
  }

  // Capture out_vec and out_control_vec (connect IO to bottom row of mesh) (output delay =1)
  // (The only reason we have so many zips is because Scala doesn't provide a zipped function for Tuple4)

  for (c <- 0 until meshColumns){
    io.out_c(c) := ShiftRegister(mesh(meshRows-1)(c).io.out_c,1)
    io.out_valid(c) := ShiftRegister(mesh(meshRows-1)(c).io.out_valid,1)
  }
}


