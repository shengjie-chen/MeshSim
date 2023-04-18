import chisel3._
import chisel3.stage.ChiselGeneratorAnnotation
import chisel3.util._

class IfmBuffer extends Module with mesh_config with buffer_config {
  val io = IO(new Bundle {
    //axi-lite reg
    val im2col_format = Input(UInt(2.W))
    val kernel = Input(UInt(3.W))
    val stride = Input(UInt(3.W))
    val padding_mode = Input(UInt(2.W))
    val padding_left = Input(UInt(2.W))
    val padding_right = Input(UInt(2.W))
    val padding_top = Input(UInt(2.W))
    val padding_down = Input(UInt(2.W))
    val ifm_size = new(whc)
    val ofm_size = new(whc)
    //ifm buffer
    val ifm_read_port0 = Flipped(new ifm_r_io(ifm_buffer_size, ifm_buffer_width))
    val ifm_read_port1 = Flipped(new ifm_r_io(ifm_buffer_size, ifm_buffer_width))
    val task_done = Input(Bool())
    // mesh
    val ifm = Decoupled(Vec(mesh_rows, UInt(pe_data_w.W)))
    val last_in = Output(Bool())
  })
  assert(io.im2col_format === 3.U, "currently im2col_format only support 3(ifm_int8)")
  assert(io.ifm_size.c % 32.U === 0.U, "ifm channel must be 32 aligned")
  // ################ const ################
  // ifm
  val low_w = io.padding_left
  val high_w = io.padding_left + io.ifm_size.w - 1.U
  val low_h = io.padding_top
  val high_h = io.padding_top + io.ifm_size.h - 1.U
  val ifm_pd_w = io.ifm_size.w + io.padding_left + io.padding_right
  val ifm_pd_h = io.ifm_size.h + io.padding_top + io.padding_down
  // im2col
  val ic_align_div32 = io.ifm_size.c(15,5)  // / 32.U  11bit
  val im2col_w_block_num = io.kernel * io.kernel * ic_align_div32
  val im2col_h_block_num = io.ofm_size.h * io.ofm_size.w / 32.U
  val im2col_h_cnt = RegInit(0.U(24.W))
  val im2col_w_cnt_div32 = RegInit(0.U(19.W))

  val ow_cnt = im2col_h_cnt % io.ofm_size.w
  val oh_cnt = im2col_h_cnt / io.ofm_size.w
  val kw_cnt = (im2col_w_cnt_div32 / ic_align_div32) % io.kernel
  val kh_cnt = (im2col_w_cnt_div32 / ic_align_div32) / io.kernel
  val iw_cnt = ow_cnt * io.stride + kw_cnt
  val ih_cnt = oh_cnt * io.stride + kh_cnt
  val ifm_buffer_addr_base = (ih_cnt * io.ifm_size.w + iw_cnt) * ic_align_div32
  val ifm_buffer_addr_offset = im2col_w_cnt_div32 % ic_align_div32
  val ifm_buffer_addr = ifm_buffer_addr_base + ifm_buffer_addr_offset




//  val ow_cnt = RegInit(0.U(12.W))
//  val oh_cnt = RegInit(0.U(12.W))
//  val kw_cnt = RegInit(0.U(3.W))
//  val kh_cnt = RegInit(0.U(3.W))
//  val ic_cnt_div32 = RegInit(0.U((16 - 5).W))

//  when(riseEdge(io.task_done)) {
//    ow_cnt := 0.U
//    oh_cnt := 0.U
//  }.elsewhen(io.ifm.ready) {
//    ow_cnt := ow_cnt + 1.U
//    when(io.ofm_size.w >= 32.U)
//
//
//
//    when(io.last_in){
//      when(ow_cnt === 31.U || ow_cnt === (io.ofm_size.w - 1.U)) {
//        ow_cnt := 0.U
//      }
//    }.otherwise{
//      when(ow_cnt === 31.U) {
//        ow_cnt := 0.U
//      }
//    }
//
//    when(ow_cnt === io.ifm_size.w - 1.U) {
//      ow_cnt := 0.U
//      oh_cnt := oh_cnt + 1.U
//    }
//  }
//
//  when(riseEdge(io.task_done)) {
//    ow_cnt := 0.U
//    oh_cnt := 0.U
//    kw_cnt := 0.U
//    kh_cnt := 0.U
//    ic_cnt_div32 := 0.U
//  }.elsewhen(io.ifm.ready) {
//    when(ic_cnt_div32 === ic_align_div32 - 1.U) {
//      ic_cnt_div32 := 0.U
//      when()
//    }.otherwise {
//      ic_cnt_div32 := ic_cnt_div32 + 1.U
//    }


  }



  val ifm_pd_w_base = ow_cnt * io.stride + kw_cnt
  val ifm_pd_h_base = oh_cnt * io.stride + kh_cnt
  val ifm_buffer_addr_offset = ic_cnt / 32.U

  val ifm_index_w = ifm_pd_w_base - low_w
  val ifm_index_h = ifm_pd_h_base - low_h
  val ifm_buffer_addr_base = (ifm_index_h * io.ifm_size.w + ifm_index_w) * ic_align_div32
  val ifm_buffer_addr = ifm_buffer_addr_base + ifm_buffer_addr_offset

  for (i <- 0 until mesh_rows) {
    when(ifm_pd_w_base < low_w || ifm_pd_w_base > high_w || ifm_pd_h_base < low_h || ifm_pd_h_base > high_h) {
      io.ifm.bits(i) := RegNext(0.U)
    }.otherwise {
      io.ifm.bits(i) := io.ifm_read_port1.rdata(i * 8 + 7, i * 8) ## 0.U(8.W) ## io.ifm_read_port0.rdata(i * 8 + 7, i * 8)
    }
  }


}
