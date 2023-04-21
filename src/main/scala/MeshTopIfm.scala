import chisel3._
import chisel3.stage.ChiselGeneratorAnnotation
import chisel3.util._

class MeshTopIfm extends Module with mesh_config with buffer_config{
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

    val w = Flipped(Decoupled(Vec(mesh_columns, UInt(pe_data_w.W))))
    val out = Valid(new ofm_data)
  })

  val ifm_buffer = Module(new IfmBufferSimple)
  val mesh = Module(new Mesh)
  val acc_mem = Module(new AccMem)

// connect ifm_buffer and io
  ifm_buffer.io.im2col_format := io.im2col_format
  ifm_buffer.io.kernel := io.kernel
  ifm_buffer.io.stride := io.stride
  ifm_buffer.io.padding_mode := io.padding_mode
  ifm_buffer.io.padding_left := io.padding_left
  ifm_buffer.io.padding_right := io.padding_right
  ifm_buffer.io.padding_top := io.padding_top
  ifm_buffer.io.padding_down := io.padding_down
  ifm_buffer.io.ifm_size := io.ifm_size
  ifm_buffer.io.ofm_size := io.ofm_size
  ifm_buffer.io.ifm_read_port0 <> io.ifm_read_port0
  ifm_buffer.io.ifm_read_port1 <> io.ifm_read_port1
  ifm_buffer.io.task_done := io.task_done

  mesh.io.w <> io.w
  mesh.io.ifm <> ifm_buffer.io.ifm
  mesh.io.last_in <> ifm_buffer.io.last_in

  mesh.io.last_out <> acc_mem.io.last
  mesh.io.ofm <> acc_mem.io.ofm

  acc_mem.io.out <> io.out
}

object MeshTopIfmGen extends App {
  (new chisel3.stage.ChiselStage)
    .execute(args, Seq(chisel3.stage.ChiselGeneratorAnnotation(() => new MeshTopIfm)))
}