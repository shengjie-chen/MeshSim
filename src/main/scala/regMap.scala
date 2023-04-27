import chisel3._
import chisel3.util._

class regPort(dmaAddrWidth:Int) extends Bundle{
  val shape_bc0_reg = Input(UInt(32.W))
  val shape_wh0_reg = Input(UInt(32.W))
  val shape_cstep0_reg = Input(UInt(32.W))
  val shape_bc1_reg = Input(UInt(32.W))
  val shape_wh1_reg = Input(UInt(32.W))
  val shape_cstep1_reg = Input(UInt(32.W))
  val src0_addr0_reg = Input(UInt(dmaAddrWidth.W))
  val src1_addr0_reg = Input(UInt(dmaAddrWidth.W))
  val dst_addr0_reg = Input(UInt(dmaAddrWidth.W))
  val src0_addr1_reg = Input(UInt(dmaAddrWidth.W))
  val src1_addr1_reg = Input(UInt(dmaAddrWidth.W))
  val dst_addr1_reg = Input(UInt(dmaAddrWidth.W))
  val alu_ctrl_reg = Input(UInt(32.W))
  val alu_veclen0_reg = Input(UInt(32.W))
  val alu_veclen1_reg = Input(UInt(32.W))
  val pool_ctrl_reg = Input(UInt(32.W))
  val gemm_ctrl_reg = Input(UInt(32.W))
  val opfusion_ctrl_reg = Input(UInt(32.W))
  val quant_scale_reg = Input(UInt(32.W))
  val dequant_scale_reg = Input(UInt(dmaAddrWidth.W))
  val requant_scale_reg = Input(UInt(32.W))
  val bias_addr_reg = Input(UInt(dmaAddrWidth.W))
  val leakyrelu_param_reg = Input(UInt(32.W))
}

class regMap extends Module with dma_config {
  val io = IO(new Bundle() {
    val regPort = new regPort(dmaAddrWidth)

    //shape_reg
    val ch0_dims = Output(UInt(3.W))
    val ch0_batch = Output(UInt(13.W))
//    val ch0_channels = Output(UInt(16.W))
//    val ch0_h = Output(UInt(16.W))
//    val ch0_w = Output(UInt(16.W))
    val ch0_whc = Flipped(new whc)
    val ch0_cstep = Output(UInt(32.W))
    val ch1_dims = Output(UInt(3.W))
    val ch1_batch = Output(UInt(13.W))
//    val ch1_channels = Output(UInt(16.W))
//    val ch1_h = Output(UInt(16.W))
//    val ch1_w = Output(UInt(16.W))
    val ch1_whc = Flipped(new whc)
    val ch1_cstep = Output(UInt(32.W))

    //mat_addr_reg
    val ch0_src0_addr = Output(UInt(dmaAddrWidth.W))
    val ch0_src1_addr = Output(UInt(dmaAddrWidth.W))
    val ch0_dst_addr = Output(UInt(dmaAddrWidth.W))
    val ch1_src0_addr = Output(UInt(dmaAddrWidth.W))
    val ch1_src1_addr = Output(UInt(dmaAddrWidth.W))
    val ch1_dst_addr = Output(UInt(dmaAddrWidth.W))

    //alu_reg
    val alu_en = Output(Bool())
    val alu_op = Output(UInt(6.W))
    val alu_type = Output(UInt(3.W))
    val alu_channels = Output(UInt(2.W))
    val alu_veclen_0 = Output(UInt(32.W))
    val alu_veclen_1 = Output(UInt(32.W))

    //pool_reg
    val pool_en = Output(Bool())
    val pool_type = Output(UInt(2.W))

    //gemm_ctrl
    val gemm_en = Output(Bool())
    val gemm_format = Output(UInt(2.W))
    val kernel = Output(UInt(3.W))
    val stride = Output(UInt(3.W))
    val padding_mode = Output(UInt(2.W))
    val padding_left = Output(UInt(2.W))
    val padding_right = Output(UInt(2.W))
    val padding_top = Output(UInt(2.W))
    val padding_down = Output(UInt(2.W))
    val ofm_whc = Flipped(new whc)
    val bias_en = Output(Bool())
    val activate_type = Output(UInt(3.W))
    val requant_en = Output(Bool())
    val quant_scale = Output(UInt(32.W))
    val dequant_scale = Output(UInt(dmaAddrWidth.W))
    val requant_scale = Output(UInt(32.W))
    val bias_addr = Output(UInt(dmaAddrWidth.W))
    val leakyrelu_param = Output(UInt(32.W))
  })

  //shape_reg
  io.ch0_dims := io.regPort.shape_bc0_reg(2,0)
  io.ch0_batch := io.regPort.shape_bc0_reg(15,3)
  io.ch0_whc.c := io.regPort.shape_bc0_reg(31,16)
  io.ch0_whc.h := io.regPort.shape_wh0_reg(15,0)
  io.ch0_whc.w := io.regPort.shape_wh0_reg(31,16)
  io.ch0_cstep := io.regPort.shape_cstep0_reg
  io.ch1_dims := io.regPort.shape_bc1_reg(2,0)
  io.ch1_batch := io.regPort.shape_bc1_reg(15,3)
  io.ch1_whc.c := io.regPort.shape_bc1_reg(31,16)
  io.ch1_whc.h := io.regPort.shape_wh1_reg(15,0)
  io.ch1_whc.w := io.regPort.shape_wh1_reg(31,16)
  io.ch1_cstep := io.regPort.shape_cstep1_reg

  //mat_addr_reg
  io.ch0_src0_addr := io.regPort.src0_addr0_reg
  io.ch0_src1_addr := io.regPort.src1_addr0_reg
  io.ch0_dst_addr := io.regPort.dst_addr0_reg
  io.ch1_src0_addr := io.regPort.src0_addr1_reg
  io.ch1_src1_addr := io.regPort.src1_addr1_reg
  io.ch1_dst_addr := io.regPort.dst_addr1_reg

  //alu_reg
  io.alu_en := io.regPort.alu_ctrl_reg(0)
  io.alu_op := io.regPort.alu_ctrl_reg(6,1)
  io.alu_type := io.regPort.alu_ctrl_reg(9,7)
  io.alu_channels := io.regPort.alu_ctrl_reg(11,10)
  io.alu_veclen_0 := io.regPort.alu_veclen0_reg
  io.alu_veclen_1 := io.regPort.alu_veclen1_reg

  //pool_reg
  io.pool_en := io.regPort.pool_ctrl_reg(0)
  io.pool_type := io.regPort.pool_ctrl_reg(2,1)

  //gemm_ctrl
  io.gemm_en := io.regPort.gemm_ctrl_reg(0)
  io.gemm_format := io.regPort.gemm_ctrl_reg(2,1)
  io.kernel := io.regPort.gemm_ctrl_reg(5,3)
  io.stride := io.regPort.gemm_ctrl_reg(8,6)
  io.padding_mode := io.regPort.gemm_ctrl_reg(10,9)
  io.padding_left := io.regPort.gemm_ctrl_reg(12,11)
  io.padding_right := io.regPort.gemm_ctrl_reg(14,13)
  io.padding_top := io.regPort.gemm_ctrl_reg(16,15)
  io.padding_down := io.regPort.gemm_ctrl_reg(18,17)
  io.ofm_whc.c := io.regPort.gemm_ctrl_reg(30,19)
  io.bias_en := io.regPort.opfusion_ctrl_reg(0,0)
  io.activate_type := io.regPort.opfusion_ctrl_reg(3,1)
  io.requant_en := io.regPort.opfusion_ctrl_reg(4,4)
  io.ofm_whc.w := io.regPort.opfusion_ctrl_reg(16,5)
  io.ofm_whc.h := io.regPort.opfusion_ctrl_reg(28,17)
  io.quant_scale := io.regPort.quant_scale_reg
  io.dequant_scale := io.regPort.dequant_scale_reg
  io.requant_scale := io.regPort.requant_scale_reg
  io.bias_addr := io.regPort.bias_addr_reg
  io.leakyrelu_param := io.regPort.leakyrelu_param_reg
}