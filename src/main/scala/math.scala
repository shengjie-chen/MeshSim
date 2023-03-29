import chisel3._
import chisel3.util._

class alu_math extends Module with alu_mathfunc_config{
  val io = IO(new Bundle() {
    val data_i = Input(UInt(32.W))
    val math_ctrl_reg = Input(UInt(32.W))
    val data_o = Output(UInt(32.W))
    val valid_o = Output(Bool())
    val task_done = Output(Bool())
  })
  val valid_i = io.math_ctrl_reg(0) & !RegNext(io.math_ctrl_reg(0))
  val op = io.math_ctrl_reg(6,1)

  val task_done = RegInit(false.B)
  val valid_o = RegInit(false.B)
  val data_o = RegInit(0.U(32.W))
  io.task_done := task_done
  io.valid_o := valid_o
  io.data_o := data_o

  task_done := Mux(io.math_ctrl_reg === 0.U,false.B,Mux(valid_o,true.B,task_done))

  val sin_cos_cell = if(alu_sin_cos_en) Some(Module(new math_sin_cos)) else None
  val atan_cell = if(alu_atan_en) Some(Module(new math_atanpuf32)) else None
  val sqrt_cell = if(alu_sqrt_en) Some(Module(new math_sqrtf32)) else None
  val exp_cell = if(alu_iexp_en) Some(Module(new math_expf32)) else None
  val log_cell = if(alu_log_en) Some(Module(new math_logf32)) else None

  if(alu_sin_cos_en){
    sin_cos_cell.get.io.clk := clock
    sin_cos_cell.get.io.rst_n := !reset.asBool
    sin_cos_cell.get.io.sin_rb := io.data_i
    sin_cos_cell.get.io.cos_rb := io.data_i
    sin_cos_cell.get.io.sin_op_en := op === alu_sin_id && valid_i
    sin_cos_cell.get.io.cos_op_en := op === alu_cos_id && valid_i
  }

  if(alu_atan_en){
    atan_cell.get.io.clk := clock
    atan_cell.get.io.rst_n := !reset.asBool
    atan_cell.get.io.atan_rb := io.data_i
    atan_cell.get.io.atan_op_en := op === alu_atan_id && valid_i
  }

  if(alu_sqrt_en){
    sqrt_cell.get.io.clk := clock
    sqrt_cell.get.io.rst_n := !reset.asBool
    sqrt_cell.get.io.sqrt_rb := io.data_i
    sqrt_cell.get.io.sqrt_op_en := op === alu_sqrt_id && valid_i
  }

  if(alu_iexp_en){
    exp_cell.get.io.clk := clock
    exp_cell.get.io.rst_n := !reset.asBool
    exp_cell.get.io.iexp2_rb := io.data_i
    exp_cell.get.io.iexp2_op_en := op === alu_iexp_id && valid_i
  }

  if(alu_log_en){
    log_cell.get.io.clk := clock
    log_cell.get.io.rst_n := !reset.asBool
    log_cell.get.io.log2_rb := io.data_i
    log_cell.get.io.log2_op_en := op === alu_log_id && valid_i
  }

  data_o := 0.U
  valid_o := false.B
  switch(op){
    is(alu_sin_id) {
      data_o := (if(alu_sin_cos_en) sin_cos_cell.get.io.sin_ra else data_o)
      valid_o := (if(alu_sin_cos_en) sin_cos_cell.get.io.sin_ra_en else valid_o)
    }
    is(alu_cos_id){
      data_o := (if(alu_sin_cos_en) sin_cos_cell.get.io.cos_ra else data_o)
      valid_o := (if(alu_sin_cos_en) sin_cos_cell.get.io.cos_ra_en else valid_o)
    }
    is(alu_atan_id) {
      data_o := (if(alu_atan_en) atan_cell.get.io.atan_ra else data_o)
      valid_o := (if(alu_atan_en) atan_cell.get.io.atan_ra_en else valid_o)
    }
    is(alu_sqrt_id) {
      data_o := (if(alu_sqrt_en) sqrt_cell.get.io.sqrt_ra else data_o)
      valid_o := (if(alu_sqrt_en) sqrt_cell.get.io.sqrt_ra_en else valid_o)
    }
    is(alu_iexp_id){
      data_o := (if(alu_iexp_en) exp_cell.get.io.iexp2_ra else data_o)
      valid_o := (if(alu_iexp_en) exp_cell.get.io.iexp2_ra_en else valid_o)
    }
    is(alu_log_id){
      data_o := (if(alu_log_en) log_cell.get.io.log_ra else data_o)
      valid_o := (if(alu_log_en) log_cell.get.io.log2_ra_en else valid_o)
    }
  }

}

class math_sin_cos extends BlackBox with HasBlackBoxPath{
  val io = IO(new Bundle() {
    val clk = Input(Clock())
    val rst_n = Input(Bool())
    val sin_rb = Input(UInt(32.W))
    val cos_rb = Input(UInt(32.W))
    val sin_op_en = Input(Bool())
    val cos_op_en = Input(Bool())
    val sin_ra = Output(UInt(32.W))
    val cos_ra = Output(UInt(32.W))
    val sin_ra_en = Output(Bool())
    val cos_ra_en = Output(Bool())
  })

  addPath("./src/main/hdl/math_sin_cos.sv")
}

class math_atanpuf32 extends BlackBox with HasBlackBoxPath{
  val io = IO(new Bundle() {
    val clk = Input(Clock())
    val rst_n = Input(Bool())
    val atan_rb = Input(UInt(32.W))
    val atan_op_en = Input(Bool())
    val atan_ra = Output(UInt(32.W))
    val atan_ra_en = Output(Bool())
    val atan_lvf_flag = Output(Bool())
  })

  addPath("./src/main/hdl/math_atanpuf32.sv")
}

class math_sqrtf32 extends BlackBox with HasBlackBoxPath{
  val io = IO(new Bundle() {
    val clk = Input(Clock())
    val rst_n = Input(Bool())
    val sqrt_rb = Input(UInt(32.W))
    val sqrt_op_en = Input(Bool())
    val sqrt_ra = Output(UInt(32.W))
    val sqrt_ra_en = Output(Bool())
    val sqrt_lvf_flag = Output(Bool())
  })

  addPath("./src/main/hdl/math_sqrtf32.sv")
}

class math_expf32 extends BlackBox with HasBlackBoxPath{
  val io = IO(new Bundle() {
    val clk = Input(Clock())
    val rst_n = Input(Bool())
    val iexp2_rb = Input(UInt(32.W))
    val iexp2_op_en = Input(Bool())
    val iexp2_ra = Output(UInt(32.W))
    val iexp2_ra_en = Output(Bool())
    val iexp2_luf_flag = Output(Bool())
  })

  addPath("./src/main/hdl/math_expf32.v")
}

class math_logf32 extends BlackBox with HasBlackBoxPath{
  val io = IO(new Bundle() {
    val clk = Input(Clock())
    val rst_n = Input(Bool())
    val log2_rb = Input(UInt(32.W))
    val log2_op_en = Input(Bool())
    val log_ra = Output(UInt(32.W))
    val log2_ra_en = Output(Bool())
    val log2_luf_flag = Output(Bool())
    val log2_lvf_flag = Output(Bool())
  })

  addPath("./src/main/hdl/math_logf32.v")
}





