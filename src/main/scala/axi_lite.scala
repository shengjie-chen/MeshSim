import chisel3._
import chisel3.util._

class axi_lite_io(C_S_AXI_DATA_WIDTH:Int, C_S_AXI_ADDR_WIDTH:Int) extends Bundle{
  val AXI_AWADDR = Input(UInt(C_S_AXI_ADDR_WIDTH.W))
  val AXI_AWPROT = Input(UInt(3.W))
  val AXI_AWVALID = Input(Bool())
  val AXI_AWREADY = Output(Bool())
  val AXI_WDATA = Input(UInt(32.W))
  val AXI_WSTRB = Input(UInt((C_S_AXI_DATA_WIDTH / 8).W))
  val AXI_WVALID = Input(Bool())
  val AXI_WREADY = Output(Bool())
  val AXI_BRESP = Output(UInt(2.W))
  val AXI_BVALID = Output(Bool())
  val AXI_BREADY = Input(Bool())
  val AXI_ARADDR = Input(UInt(C_S_AXI_ADDR_WIDTH.W))
  val AXI_ARPROT = Input(UInt(3.W))
  val AXI_ARVALID = Input(Bool())
  val AXI_ARREADY = Output(Bool())
  val AXI_RDATA = Output(UInt(C_S_AXI_DATA_WIDTH.W))
  val AXI_RRESP = Output(UInt(2.W))
  val AXI_RVALID = Output(Bool())
  val AXI_RREADY = Input(Bool())
}

class axi_lite_accel_reg_o(C_S_AXI_DATA_WIDTH :Int) extends Bundle{
  val reg0 = Output(UInt(C_S_AXI_DATA_WIDTH.W))
  val reg1 = Output(UInt(C_S_AXI_DATA_WIDTH.W))
  val reg2 = Output(UInt(C_S_AXI_DATA_WIDTH.W))
  val reg3 = Output(UInt(C_S_AXI_DATA_WIDTH.W))
  val reg4 = Output(UInt(C_S_AXI_DATA_WIDTH.W))
  val reg5 = Output(UInt(C_S_AXI_DATA_WIDTH.W))
  val reg6 = Output(UInt(C_S_AXI_DATA_WIDTH.W))
  val reg7 = Output(UInt(C_S_AXI_DATA_WIDTH.W))
  val reg8 = Output(UInt(C_S_AXI_DATA_WIDTH.W))
  val reg9 = Output(UInt(C_S_AXI_DATA_WIDTH.W))
  val reg10 = Output(UInt(C_S_AXI_DATA_WIDTH.W))
  val reg11 = Output(UInt(C_S_AXI_DATA_WIDTH.W))
  val reg12 = Output(UInt(C_S_AXI_DATA_WIDTH.W))
  val reg13 = Output(UInt(C_S_AXI_DATA_WIDTH.W))
  val reg14 = Output(UInt(C_S_AXI_DATA_WIDTH.W))
  val reg15 = Output(UInt(C_S_AXI_DATA_WIDTH.W))
  val reg16 = Output(UInt(C_S_AXI_DATA_WIDTH.W))
  val reg17 = Output(UInt(C_S_AXI_DATA_WIDTH.W))
  val reg18 = Output(UInt(C_S_AXI_DATA_WIDTH.W))
  val reg19 = Output(UInt(C_S_AXI_DATA_WIDTH.W))
  val reg20 = Output(UInt(C_S_AXI_DATA_WIDTH.W))
  val reg21 = Output(UInt(C_S_AXI_DATA_WIDTH.W))
  val reg22 = Output(UInt(C_S_AXI_DATA_WIDTH.W))
  val reg23 = Output(UInt(C_S_AXI_DATA_WIDTH.W))
  val reg24 = Output(UInt(C_S_AXI_DATA_WIDTH.W))
  val reg25 = Output(UInt(C_S_AXI_DATA_WIDTH.W))
  val reg26 = Output(UInt(C_S_AXI_DATA_WIDTH.W))
  val reg27 = Output(UInt(C_S_AXI_DATA_WIDTH.W))
  val reg28 = Output(UInt(C_S_AXI_DATA_WIDTH.W))
  val reg29 = Output(UInt(C_S_AXI_DATA_WIDTH.W))
  val reg30 = Output(UInt(C_S_AXI_DATA_WIDTH.W))
  val reg31 = Output(UInt(C_S_AXI_DATA_WIDTH.W))
}

class axi_lite_math_reg_o(C_S_AXI_DATA_WIDTH :Int) extends Bundle {
  val reg0 = Output(UInt(C_S_AXI_DATA_WIDTH.W))
  val reg1 = Output(UInt(C_S_AXI_DATA_WIDTH.W))
  val reg2 = Output(UInt(C_S_AXI_DATA_WIDTH.W))
  val reg3 = Output(UInt(C_S_AXI_DATA_WIDTH.W))
}


class axi_lite_math_reg_i(C_S_AXI_DATA_WIDTH :Int) extends Bundle{
  val reg1 = Input(UInt(C_S_AXI_DATA_WIDTH.W))
  val reg1_valid = Input(Bool())
}

class axi_lite_accel extends BlackBox with HasBlackBoxPath with hw_config {
  val io = IO(new Bundle() {
    val S_AXI_ACLK = Input(Clock())
    val S_AXI_ARESETN = Input(Bool())
    val S = new axi_lite_io(ACCEL_AXI_DATA_WIDTH,ACCEL_AXI_ADDR_WIDTH)
    val o_slv = new axi_lite_accel_reg_o(ACCEL_AXI_DATA_WIDTH)
  })
  addPath("./src/main/hdl/axi_lite_accel.v")
}

class axi_lite_math extends BlackBox with HasBlackBoxPath with hw_config {
  val io = IO(new Bundle() {
    val S_AXI_ACLK = Input(Clock())
    val S_AXI_ARESETN = Input(Bool())
    val S = new axi_lite_io(MATH_AXI_DATA_WIDTH,MATH_AXI_ADDR_WIDTH)
    val o_slv = new axi_lite_math_reg_o(MATH_AXI_DATA_WIDTH)
    val i_slv = new axi_lite_math_reg_i(MATH_AXI_DATA_WIDTH)
  })
  addPath("./src/main/hdl/axi_lite_math.v")
}