import chisel3._
import chisel3.util._

class regPort(dmaAddrWidth: Int) extends Bundle {
  val shape_src_bc0_reg = Input(UInt(32.W))
  val shape_src_wh0_reg = Input(UInt(32.W))
  val shape_src_cstep0_reg = Input(UInt(32.W))
  val shape_src_bc1_reg = Input(UInt(32.W))
  val shape_src_wh1_reg = Input(UInt(32.W))
  val shape_src_cstep1_reg = Input(UInt(32.W))
  val shape_dst_bc0_reg = Input(UInt(32.W))
  val shape_dst_wh0_reg = Input(UInt(32.W))
  val shape_dst_cstep0_reg = Input(UInt(32.W))
  val shape_dst_bc1_reg = Input(UInt(32.W))
  val shape_dst_wh1_reg = Input(UInt(32.W))
  val shape_dst_cstep1_reg = Input(UInt(32.W))
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
    val ch0_src_dims = Output(UInt(3.W))
    val ch0_src_batch = Output(UInt(13.W))
    val ch0_src_whc = Flipped(new whc)
    val ch0_src_cstep = Output(UInt(32.W))
    val ch1_src_dims = Output(UInt(3.W))
    val ch1_src_batch = Output(UInt(13.W))
    val ch1_src_whc = Flipped(new whc)
    val ch1_src_cstep = Output(UInt(32.W))
    val ch0_dst_dims = Output(UInt(3.W))
    val ch0_dst_batch = Output(UInt(13.W))
    val ch0_dst_whc = Flipped(new whc)
    val ch0_dst_cstep = Output(UInt(32.W))
    val ch1_dst_dims = Output(UInt(3.W))
    val ch1_dst_batch = Output(UInt(13.W))
    val ch1_dst_whc = Flipped(new whc)
    val ch1_dst_cstep = Output(UInt(32.W))

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
    val pool_channels = Output(UInt(2.W))
    val pool_kernel_w = Output(UInt(2.W))
    val pool_kernel_h = Output(UInt(2.W))
    val pool_stride_w = Output(UInt(2.W))
    val pool_stride_h = Output(UInt(2.W))
    val pool_pad_mode = Output(UInt(1.W))
    val pool_pad_left = Output(UInt(2.W))
    val pool_pad_right = Output(UInt(2.W))
    val pool_pad_top = Output(UInt(2.W))
    val pool_pad_bottom = Output(UInt(2.W))

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
  io.ch0_src_dims  := io.regPort.shape_src_bc0_reg(2, 0)
  io.ch0_src_batch := io.regPort.shape_src_bc0_reg(15, 3)
  io.ch0_src_whc.c := io.regPort.shape_src_bc0_reg(31, 16)
  io.ch0_src_whc.h := io.regPort.shape_src_wh0_reg(15, 0)
  io.ch0_src_whc.w := io.regPort.shape_src_wh0_reg(31, 16)
  io.ch0_src_cstep   := io.regPort.shape_src_cstep0_reg
  io.ch1_src_dims    := io.regPort.shape_src_bc1_reg(2, 0)
  io.ch1_src_batch  := io.regPort.shape_src_bc1_reg(15, 3)
  io.ch1_src_whc.c  := io.regPort.shape_src_bc1_reg(31, 16)
  io.ch1_src_whc.h := io.regPort.shape_src_wh1_reg(15, 0)
  io.ch1_src_whc.w := io.regPort.shape_src_wh1_reg(31, 16)
  io.ch1_src_cstep := io.regPort.shape_src_cstep1_reg
  io.ch0_dst_dims := io.regPort.shape_dst_bc0_reg(2, 0)
  io.ch0_dst_batch := io.regPort.shape_dst_bc0_reg(15, 3)
  io.ch0_dst_whc.c := io.regPort.shape_dst_bc0_reg(31, 16)
  io.ch0_dst_whc.h := io.regPort.shape_dst_wh0_reg(15, 0)
  io.ch0_dst_whc.w := io.regPort.shape_dst_wh0_reg(31, 16)
  io.ch0_dst_cstep := io.regPort.shape_dst_cstep0_reg
  io.ch1_dst_dims := io.regPort.shape_dst_bc1_reg(2, 0)
  io.ch1_dst_batch := io.regPort.shape_dst_bc1_reg(15, 3)
  io.ch1_dst_whc.c := io.regPort.shape_dst_bc1_reg(31, 16)
  io.ch1_dst_whc.h := io.regPort.shape_dst_wh1_reg(15, 0)
  io.ch1_dst_whc.w := io.regPort.shape_dst_wh1_reg(31, 16)
  io.ch1_dst_cstep := io.regPort.shape_dst_cstep1_reg

  //mat_addr_reg
  io.ch0_src0_addr := io.regPort.src0_addr0_reg
  io.ch0_src1_addr := io.regPort.src1_addr0_reg
  io.ch0_dst_addr := io.regPort.dst_addr0_reg
  io.ch1_src0_addr := io.regPort.src0_addr1_reg
  io.ch1_src1_addr := io.regPort.src1_addr1_reg
  io.ch1_dst_addr := io.regPort.dst_addr1_reg

  //alu_reg
  io.alu_en := io.regPort.alu_ctrl_reg(0)
  io.alu_op := io.regPort.alu_ctrl_reg(6, 1)
  io.alu_type := io.regPort.alu_ctrl_reg(9, 7)
  io.alu_channels := io.regPort.alu_ctrl_reg(11, 10)
  io.alu_veclen_0 := io.regPort.alu_veclen0_reg
  io.alu_veclen_1 := io.regPort.alu_veclen1_reg

  //pool_reg
  io.pool_en := io.regPort.pool_ctrl_reg(0)
  io.pool_type := io.regPort.pool_ctrl_reg(2, 1)
  io.pool_channels := io.regPort.pool_ctrl_reg(4, 3)
  io.pool_kernel_w := io.regPort.pool_ctrl_reg(6, 5)
  io.pool_kernel_h := io.regPort.pool_ctrl_reg(8, 7)
  io.pool_stride_w := io.regPort.pool_ctrl_reg(10, 9)
  io.pool_stride_h := io.regPort.pool_ctrl_reg(12, 11)
  io.pool_pad_mode := io.regPort.pool_ctrl_reg(24)
  io.pool_pad_left := io.regPort.pool_ctrl_reg(23, 22)
  io.pool_pad_right := io.regPort.pool_ctrl_reg(21, 20)
  io.pool_pad_top := io.regPort.pool_ctrl_reg(19, 18)
  io.pool_pad_bottom := io.regPort.pool_ctrl_reg(17, 16)

  //gemm_ctrl
  io.gemm_en := io.regPort.gemm_ctrl_reg(0)
  io.gemm_format := io.regPort.gemm_ctrl_reg(2, 1)
  io.kernel := io.regPort.gemm_ctrl_reg(5, 3)
  io.stride := io.regPort.gemm_ctrl_reg(8, 6)
  io.padding_mode := io.regPort.gemm_ctrl_reg(10, 9)
  io.padding_left := io.regPort.gemm_ctrl_reg(12, 11)
  io.padding_right := io.regPort.gemm_ctrl_reg(14, 13)
  io.padding_top := io.regPort.gemm_ctrl_reg(16, 15)
  io.padding_down := io.regPort.gemm_ctrl_reg(18, 17)
  io.bias_en := io.regPort.gemm_ctrl_reg(19, 19)
  io.activate_type := io.regPort.gemm_ctrl_reg(22, 20)
  io.requant_en := io.regPort.gemm_ctrl_reg(23, 23)

  io.quant_scale := io.regPort.quant_scale_reg
  io.dequant_scale := io.regPort.dequant_scale_reg
  io.requant_scale := io.regPort.requant_scale_reg
  io.bias_addr := io.regPort.bias_addr_reg
  io.leakyrelu_param := io.regPort.leakyrelu_param_reg
}