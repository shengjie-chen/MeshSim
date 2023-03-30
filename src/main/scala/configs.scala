import chisel3._
import chisel3.util._

trait sim_config{
  val simMode = true
  val fp32_adder_sim = false
  val fp32_multiplier_sim = false
  val pe_sim  = true
}

trait dma_config extends sim_config{
  val dmaAddrWidth = if (simMode) 64 else 32
  val dmaSizeWidth = 32
  val dmaDataWidth = 128
  val id = Map("alu"->1,"pool"->2,"im2col"->3, "wgtBuf"->4, "opfusion"->5)
  val dma_ch0_en_cfg = Map("alu"->true,"pool"->false,"im2col"->true, "wgtBuf"->false, "opfusion"->false)
  val dma_ch1_en_cfg = Map("alu"->true,"pool"->false,"im2col"->false,"wgtBuf"->true, "opfusion"->true)
  val dma_ch_width = 2
}

trait buffer_config {
  //ifm_mem * 2
  val ifm_buffer_size =  32768   //1MB
  val ifm_buffer_width = 256     //64Bytes
}

trait hw_config extends dma_config with buffer_config {
  val ACCEL_AXI_DATA_WIDTH = 32
  val ACCEL_AXI_ADDR_WIDTH = 7
  val MATH_AXI_DATA_WIDTH = 32
  val MATH_AXI_ADDR_WIDTH = 4
  val alu_mathfunc_en = false
  val alu_mat_en = false
  val im2col_en = false
  val wgtBuf_en = false
  val opfusion_en = false
  val dma_en = alu_mat_en || im2col_en || wgtBuf_en || opfusion_en
}

trait alu_mat_config extends dma_config{
  val alu_add_id = 0.U
  val alu_mul_id = 1.U
  val alu_abs_id = 2.U
  val ALU_DATA_WIDTH = dmaDataWidth
  val ALU_BURST_LEN = 256
  val alu_format_int32 = 1.U
  val alu_format_float32 = 2.U
  val alu_add_en = true
  val alu_mul_en = true
  val alu_abs_en = false
}

trait alu_mathfunc_config{
  val alu_sin_id = 0.U
  val alu_cos_id = 1.U
  val alu_atan_id = 2.U
  val alu_sqrt_id = 3.U
  val alu_iexp_id = 4.U
  val alu_log_id = 5.U
  val alu_sin_cos_en = true
  val alu_atan_en = true
  val alu_sqrt_en = true
  val alu_iexp_en = true
  val alu_log_en = true
}

trait cal_cell_params{
  val fp32_add_cycles = 2
  val fp32_mul_cycles = 2
  val fp32_addmul_cycles = 3
  val int32_add_cycles = 1
  val int32_mul_cycles = 1
  val in32_addmul_cycles = 1
  val sint_to_fp32_cycles = 1
  val fp32_to_sint_cycles = 1
  val relu_cycles = 0
  val leakyrelu_cycles = fp32_mul_cycles
}

trait activation_config{
  val None = 0.U
  val ReLU = 1.U
  val LeakyReLU = 2.U
}

trait gemm_config{
  val Mat32_FP32 = 0.U
  val Mat32_INT32 = 1.U
  val IFM_FP32 = 2.U
  val IFM_INT8 = 3.U
  val gemm_ch0_pipeline_width = 128
  val meshRows = 32
  val meshColumns = 32
  val accMemRow = 64
}

trait pe_config {
  //float int8 int32
  //int8 int32
//  val data_type = 2.U
  val pe_data_w = 32
}

trait mesh_config extends pe_config {
  val mesh_rows = 3
  val mesh_columns = 3
  val conti_level = 2 // Continuous level, compute c_l block in single SA without changing weight
  val ofm_buffer_addr_w = log2Up(mesh_rows)

}