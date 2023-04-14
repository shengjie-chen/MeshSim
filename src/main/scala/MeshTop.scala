import chisel3._
import chisel3.stage.ChiselGeneratorAnnotation
import chisel3.util._

class MeshTop extends Module with mesh_config{
  val io = IO(new Bundle {
    val w = Flipped(Decoupled(Vec(mesh_columns, UInt(pe_data_w.W))))
    val ifm = Flipped(Decoupled(Vec(mesh_rows, UInt(pe_data_w.W))))
    val last_in = Input(Bool())

    val out = Valid(new ofm_data)
  })

  val mesh = Module(new Mesh)
  val acc_mem = Module(new AccMem)

  mesh.io.w <> io.w
  mesh.io.ifm <> io.ifm
  mesh.io.last_in <> io.last_in

  mesh.io.last_out <> acc_mem.io.last
  mesh.io.ofm <> acc_mem.io.ofm

  acc_mem.io.out <> io.out
}

object MeshTopGen extends App {
  (new chisel3.stage.ChiselStage)
    .execute(args, Seq(chisel3.stage.ChiselGeneratorAnnotation(() => new MeshTop)))
}