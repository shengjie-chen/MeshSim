import chisel3._
import chisel3.stage.ChiselGeneratorAnnotation
import chisel3.util._

class MeshTop extends Module with mesh_config {
  val io = IO(new Bundle {
    val w        = Flipped(Decoupled(Vec(mesh_columns, UInt(pe_data_w.W))))
    val ifm      = Flipped(Decoupled(Vec(mesh_rows, UInt(pe_data_w.W))))
    val last_in  = Input(Bool())
    val stop     = Input(Bool())
    val w_finish = Input(Bool())

    val out  = Vec(mesh_columns, Valid(new acc_data))
  })

  val mesh    = Module(new Mesh)
  val acc_mem = Module(new AccMem)

  mesh.io.w        <> io.w
  mesh.io.ifm      <> io.ifm
  mesh.io.last_in  <> io.last_in
  mesh.io.stop     <> io.stop
  mesh.io.w_finish <> io.w_finish

  acc_mem.io.ofm  <> mesh.io.ofm
  acc_mem.io.stop <> io.stop

  io.out <> acc_mem.io.out
}

object MeshTopGen extends App {
  (new chisel3.stage.ChiselStage)
    .execute(args, Seq(chisel3.stage.ChiselGeneratorAnnotation(() => new MeshTop)))
}
