//// See README.md for license details.
//package GEMM
//
//import chisel3.{RegInit, _}
//import chisel3.util._
//
//class ifm_r_io(ifm_buffer_size:Int,ifm_buffer_width:Int) extends Bundle{
//  val ren = Input(Bool())
//  val raddr = Input(UInt(log2Ceil(ifm_buffer_size).W))
//  val rdata = Output(UInt((ifm_buffer_width).W))
//}
//
//class whc extends Bundle{
//  val w = Input(UInt(16.W))
//  val h = Input(UInt(16.W))
//  val c = Input(UInt(16.W))
//}
//
//class IfmBuffer extends Module with buffer_config with gemm_config {
//  val io = IO(new Bundle {
//    val cache_whc = new whc
//    val ifm_whc = new whc
//    val start = Input(Bool())
//    val format = Input(UInt(2.W))
//    val kernel = Input(UInt(3.W))
//    val p_mode = Input(UInt(2.W))
//    val p_dir = Input(UInt(2.W))
//    val ifm_r_io = Flipped(new ifm_r_io(ifm_buffer_size,ifm_buffer_width))
//
//    val out_ifm_date = Decoupled(UInt(1024.W))
//  })
//  val en  = RegInit(false.B)
//  val start_s1 = riseEdge(io.start)
//  val start_s2 = RegNext(start_s1)
//
//
//  val w = RegEnable(io.ifm_whc.w,io.start)
//  val h = RegEnable(io.ifm_whc.h,io.start)
//  val c = RegEnable(io.ifm_whc.c,io.start)
//
//  // int8 conv (format==IFM_INT8)
//  val icg = RegEnable((c+63.U)>>6.U,start_s1)//////////////////? ic group
//  val format = RegEnable(io.format,io.start)
//  val k = RegEnable(io.kernel,io.start)
//  val cache_whc = RegNext(cache_whc)
//  val p_mode = RegEnable(io.p_mode,io.start)
//  val p_dir  = RegEnable(io.p_dir,io.start)
//
//  val ic_cnt = RegInit(0.U(8.W)) //ic group count
//  val w_cnt = RegInit(0.U(8.W))
//  val h_cnt = RegInit(0.U(8.W))
//  val a = RegInit(0.U(UInt(log2Ceil(ifm_buffer_size).W)))
//  val accrow_cnt = RegInit(0.U(UInt(log2Ceil(accMemRow).W)))
//  val kw_cnt = RegInit(0.U(3.W))
//  val kh_cnt = RegInit(0.U(3.W))
//
//  val raddr = RegInit(0.U(UInt(log2Ceil(ifm_buffer_size).W)))
//  val ren = RegInit(false.B)
//  val rdata_valid = RegNext(ren)
//
//  io.ifm_r_io.raddr := raddr
//  io.ifm_r_io.ren := ren
//
////  when((w_cnt === 0.U && kw_cnt === 0.U) || (w_cnt === w - k + 2.U && kw_cnt === k - 1.U) || (h_cnt === 0.U && kh_cnt === 0.U) || (h_cnt === h - k + 2.U && kh_cnt === k - 1.U)) {
////    raddr := Mux(p_mode === 1.U, a0, a)
////  }.elsewhen {
////    raddr := a - ((k - 1.U) + (k - 1.U) * w) * icg //ic补齐？
////  }
//
//  // Mat32 (format==Mat32_INT32)
//  val wg = RegEnable(((w+31.U)>>5.U).asUInt,start_s1) //w group   MeshRow==32
//  val w0 = RegEnable((((w+31.U)>>5.U)<<5.U).asUInt,start_s1) //w after add 0
//  val hg = RegEnable(((h+63.U)>>6.U).asUInt,start_s1) //h group   AccMemRow==64
//  val h0 = RegEnable((((h+31.U)>>6.U)<<6.U).asUInt,start_s1) //h after add 0
//
//  val wg_cnt = RegInit(0.U(8.W)) //w group count
//  val hg_cnt = RegInit(0.U(8.W)) //h group count
//  val one_mesh_end = RegInit(false.B)
//  val read_mat_done = RegInit(false.B)
//  val mesh_first_raddr = RegInit(0.U(UInt(log2Ceil(ifm_buffer_size).W)))
//
//  ren := Mux(start_s2 && io.cache_whc.w >= w && io.cache_whc.h >= h , true.B, Mux(read_mat_done,false.B,ren))
//
//  when(w_cnt===0.U && h_cnt===0.U){
//    mesh_first_raddr := raddr
//  }
//
//  when(ren){
//    one_mesh_end := false.B
//    read_mat_done := false.B
//
//    when(w_cnt===1.U){
//      w_cnt:=0.U
//      when(h_cnt===63.U){
//        h_cnt := 0.U
//        one_mesh_end := true.B
//
//        when(wg_cnt===wg-1.U){
//          wg_cnt := 0.U
//          when(hg_cnt===hg-1.U){
//            hg_cnt := 0.U
//            raddr := 0.U
//            read_mat_done := true.B
//          }.otherwise{
//            hg_cnt := hg_cnt+1.U
//            raddr := mesh_first_raddr + (wg<<7).asUInt - ((wg-1.U)<<1).asUInt//mesh_first_raddr + 2wg*64 - 2*(wg-1)
//          }
//        }.otherwise{
//          wg_cnt := wg_cnt+1.U
//          raddr := mesh_first_raddr + 2.U
//        }
//      }.otherwise{
//        h_cnt := h_cnt + 1.U
//        raddr := raddr - 1.U+ (wg<<1.U).asUInt
//      }
//    }.otherwise{
//      w_cnt:=1.U
//      raddr := raddr+1.U
//    }
//  }
//
//  val ifm_buffer = RegInit(0.U(1024.W))
//  val fifo = Module(new Queue(UInt(1024.W),256))
//
//}
