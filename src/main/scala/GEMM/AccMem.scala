package GEMM

//import Chisel3.Output
import chisel3.util.{RegEnable, ShiftRegister, log2Up}
import chisel3._

class AccMem(accMemRow: Int = 64, accWidth: Int = 64, outWidth:Int = 32,
                val meshRows: Int = 16, val meshColumns: Int = 16)  extends Module {
  val io = IO(new Bundle {
    val in_c          = Input(Vec(meshColumns, SInt(accWidth.W)))
    val in_c_valid    = Input(Vec(meshColumns, Bool()))
    val in_c_height   = Input(UInt(8.W)) //icg max255 <accMemRow
    val in_c_group    = Input(UInt(8.W)) //ocg    c.h = in_height * in_group
    val in_acc_mode   = Input(UInt(2.W)) //0.U -- normal add, 1.U -- cover(no add), 2.U -- add2 (fir 2way), 3.U -- add4
    val in_datatype   = Input(UInt(2.W))// 0.U -- INT8, 1.U -- INT32, 2.U -- FL32

    val in_accmem_ren = Input(Bool())

    val out_r = Output(Vec(meshColumns, SInt(outWidth.W)))
    val out_r_valid = Output(Bool())
    val out_accmem_rready = Output(Bool()) //calculate finish
    val out_accmem_full = Output(Bool())
  })

  val c_int = Wire(Vec(meshColumns, SInt(outWidth.W)))
  for (c <- 0 until meshColumns) {
    c_int(c) := ShiftRegister(Mux(io.in_datatype === 0.U, io.in_c(c)(63,32).asSInt +& io.in_c(c)(31,0).asSInt , io.in_c(c)) , meshColumns-c)
  }
  val c_int_valid = RegNext(io.in_c_valid(meshColumns-1))

  val fir_2w_col = meshColumns/2
  val c_fir_2w = Wire(Vec(fir_2w_col, SInt(32.W)))
  for (c <- 0 until fir_2w_col) {
    c_fir_2w(c) := io.in_c(2 * c) +& io.in_c(2 * c + 1)
  }

  val fir_4w_col = meshColumns / 4
  val c_fir_4w = Wire(Vec(fir_4w_col, SInt(32.W)))
  for (c <- 0 until fir_4w_col){
    c_fir_4w(c) := io.in_c(2 * c) +& io.in_c(2 * c + 1) +& io.in_c(2 * c + 2) +& io.in_c(2 * c + 3)
  }

  val acc_mem = RegInit(VecInit.fill(accMemRow)(VecInit.fill(meshColumns)(0.S(outWidth.W))))
  val w_ptr = RegInit(0.U(log2Up(accMemRow).W))
  val r_ptr = RegInit(0.U(log2Up(accMemRow).W))
  val c_group_cnt = RegInit(0.U(8.W))

  when (c_int_valid){
    when(w_ptr === io.in_c_height-1.U){
      w_ptr := 0.U
      when(c_group_cnt === io.in_c_group-1.U){
        c_group_cnt := 0.U
      }.otherwise{
        c_group_cnt := c_group_cnt + 1.U
      }
    }.otherwise{
      w_ptr := w_ptr + 1.U
    }
  }

  when (c_int_valid){
    when (c_group_cnt===0.U){
      acc_mem(w_ptr) := c_int
    }.otherwise{
      for (i <- 0 until meshColumns){
        acc_mem(w_ptr)(i) := c_int(i) +& acc_mem(w_ptr)(i)
      }
    }
  }
  val c_last_group = c_group_cnt === io.in_c_group-1.U
//  val c_last_group_s1 = RegNext(c_last_group)
//  val c_last_group_s2 = ShiftRegister(c_last_group,2)
//  val c_last_group_s3 = ShiftRegister(c_last_group,3)
//  val c_int_valid_s1 = RegNext(c_int_valid)
//  val c_int_valid_s2 = ShiftRegister(c_int_valid,2)

  //val r_accmem_enable = c_int_valid_s1 && c_last_group_s1

  val accmem_rready = RegInit(0.U(1.W))
  when(c_int_valid && c_last_group &&  w_ptr === 0.U) { //first number in last group is about to written to accmem
    accmem_rready := 1.U
  }.elsewhen(io.in_accmem_ren) {
    accmem_rready := 0.U
  }
  io.out_accmem_rready := accmem_rready

  val r_accmem_enable = io.in_accmem_ren||r_ptr>0.U
  when (r_accmem_enable) { //burst read
    when(r_ptr === io.in_c_height - 1.U) {
      r_ptr := 0.U
    }
    r_ptr := r_ptr + 1.U
  }
  io.out_r := RegEnable(acc_mem(r_ptr),VecInit.fill(meshColumns)(0.S(32.W)),r_accmem_enable)
  io.out_r_valid := RegNext(r_accmem_enable)

  val accmem_full_flag = RegInit(0.U(1.W))
  when(c_group_cnt === io.in_c_group-1.U && w_ptr === io.in_c_height-1.U && accmem_rready === 1.U){
    accmem_full_flag := 1.U
  }.elsewhen(io.in_accmem_ren){
    accmem_full_flag := 0.U
  }
  io.out_accmem_full := accmem_full_flag===1.U
}