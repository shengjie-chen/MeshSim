import chisel3._
import chisel3.stage.ChiselGeneratorAnnotation
import chisel3.util._

class IfmBufferSimple extends Module with mesh_config with buffer_config {
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
    val ifm_size = new (whc)
    val ofm_size = new (whc)
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
  val high_w = io.padding_left + io.ifm_size.w
  val low_h = io.padding_top
  val high_h = io.padding_top + io.ifm_size.h
  val ifm_pd_w = io.ifm_size.w + io.padding_left + io.padding_right
  val ifm_pd_h = io.ifm_size.h + io.padding_top + io.padding_down
  // im2col
  val ic_align_div32 = io.ifm_size.c(15, 5) // / 32.U  11bit
  val im2col_w_block_num = io.kernel * io.kernel * ic_align_div32
  val im2col_h_block_num = io.ofm_size.h * io.ofm_size.w / 32.U
  val im2col_h_cnt = RegInit(0.U(24.W))
  val im2col_w_block_cnt = RegInit(0.U(19.W))

  // ################ compute addr ################
  // to optimization
  val kw_cnt = (im2col_w_block_cnt / ic_align_div32) % io.kernel
  val kh_cnt = (im2col_w_block_cnt / ic_align_div32) / io.kernel

  val ow_cnt0 = im2col_h_cnt % io.ofm_size.w
  val oh_cnt0 = im2col_h_cnt / io.ofm_size.w
  val ow_cnt1 = (im2col_h_cnt + 32.U) % io.ofm_size.w
  val oh_cnt1 = (im2col_h_cnt + 32.U) / io.ofm_size.w

  val iw_pd_cnt0 = ow_cnt0 * io.stride + kw_cnt
  val ih_pd_cnt0 = oh_cnt0 * io.stride + kh_cnt
  val iw_pd_cnt1 = ow_cnt1 * io.stride + kw_cnt
  val ih_pd_cnt1 = oh_cnt1 * io.stride + kh_cnt


  val iw_cnt0 = Mux(iw_pd_cnt0 < low_w || iw_pd_cnt0 >= high_w, 0.U, iw_pd_cnt0 - low_w)
  val ih_cnt0 = Mux(ih_pd_cnt0 < low_h || ih_pd_cnt0 >= high_h, 0.U, ih_pd_cnt0 - low_h)
  val iw_cnt1 = Mux(iw_pd_cnt1 < low_w || iw_pd_cnt1 >= high_w, 0.U, iw_pd_cnt1 - low_w)
  val ih_cnt1 = Mux(ih_pd_cnt1 < low_h || ih_pd_cnt1 >= high_h, 0.U, ih_pd_cnt1 - low_h)

  val ifm_buffer_addr_base0 = (ih_cnt0 * io.ifm_size.w + iw_cnt0) * ic_align_div32
  val ifm_buffer_addr_offset0 = im2col_w_block_cnt % ic_align_div32
  val ifm_buffer_addr_base1 = (ih_cnt1 * io.ifm_size.w + iw_cnt1) * ic_align_div32
  val ifm_buffer_addr_offset1 = im2col_w_block_cnt % ic_align_div32
  // dont need optimization
  val ifm_buffer_addr0 = ifm_buffer_addr_base0 + ifm_buffer_addr_offset0
  val ifm_buffer_addr1 = ifm_buffer_addr_base1 + ifm_buffer_addr_offset1

  when(riseEdge(io.task_done)) {
    im2col_h_cnt := 0.U
  }.elsewhen(io.ifm.ready) {
    im2col_h_cnt := im2col_h_cnt + 1.U
    when(im2col_h_cnt % 64.U === 31.U) {
      im2col_h_cnt := im2col_h_cnt - 31.U
      when(im2col_w_block_cnt === im2col_w_block_num - 1.U) {
        im2col_h_cnt := im2col_h_cnt + 33.U
      }
    }
  }

  when(riseEdge(io.task_done)) {
    im2col_w_block_cnt := 0.U
  }.elsewhen(io.ifm.ready) {
    when(im2col_h_cnt % 64.U === 31.U) {
      im2col_w_block_cnt := im2col_w_block_cnt + 1.U
      when(im2col_w_block_cnt === im2col_w_block_num - 1.U) {
        im2col_w_block_cnt := 0.U
      }
    }
  }

  // ################ ifm buffer read ################
  io.ifm_read_port0.raddr := ifm_buffer_addr0
  io.ifm_read_port1.raddr := ifm_buffer_addr1
  io.ifm_read_port0.ren := io.task_done
  io.ifm_read_port1.ren := io.task_done

  // ################ mesh write ################
  io.ifm.valid := io.task_done
  val write_padding_0 = RegNext(iw_pd_cnt0 < low_w || iw_pd_cnt0 >= high_w || ih_pd_cnt0 < low_h || ih_pd_cnt0 >= high_h)
  val write_padding_1 = RegNext(iw_pd_cnt1 < low_w || iw_pd_cnt1 >= high_w || ih_pd_cnt1 < low_h || ih_pd_cnt1 >= high_h)
  for (i <- 0 until mesh_rows) {
    io.ifm.bits(i) := (io.ifm_read_port1.rdata(i * 8 + 7, i * 8) & Fill(8, !write_padding_1)) ## 0.U(8.W) ##
      (io.ifm_read_port0.rdata(i * 8 + 7, i * 8) & Fill(8, !write_padding_0))
  }
  io.last_in := RegNext(im2col_w_block_cnt === im2col_w_block_num - 1.U)

}

object IfmBufferSimple_gen extends App {
  new (chisel3.stage.ChiselStage).execute(Array("--target-dir", "./verilog/ifmbuffer"), Seq(ChiselGeneratorAnnotation(() => new IfmBufferSimple)))
}