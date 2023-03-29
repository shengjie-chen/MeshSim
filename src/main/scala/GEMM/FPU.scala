//
//package GEMM
//
//import chisel3.Bundle
//import chisel3._
//import hardfloat._
////case class Float(expWidth :Int=8,sigWidth:Int=io.a.sig_width) extends Bundle{
////  val bits=UInt((expWidth+sigWidth).W)
////  val bias:Int=(1<<(expWidth-1))-1
////
////}
//class FP_MAC(exp_width:Int=8,sig_width:Int=24) extends Module{
//  val io=IO(new Bundle{
//    val a=Input(new Float(exp_width,sig_width))
//    val b=Input(new Float(exp_width,sig_width))
//    val c=Input(new Float(exp_width,sig_width))
//    val out=Output(new Float(exp_width,sig_width))
//    })
//  // Recode all operands
////  io.a.bits
//  val a_rec = recFNFromFN(io.a.exp_width, io.a.sig_width, io.a.bits)
//  val b_rec = recFNFromFN(io.b.exp_width, io.b.sig_width, io.b.bits)
//  val c_rec = recFNFromFN(io.c.exp_width, io.c.sig_width, io.c.bits)
//  // Resize io.a to io.c's width
//  val a_resizer = Module(new RecFNToRecFN(io.a.exp_width, io.a.sig_width, io.c.exp_width, io.c.sig_width))
//  a_resizer.io.in := a_rec
//  a_resizer.io.roundingMode := consts.round_near_even // consts.round_near_maxMag
//  a_resizer.io.detectTininess := consts.tininess_afterRounding
//  val a_rec_resized = a_resizer.io.out
//  // Resize io.b to io.c's width
//  val b_resizer = Module(new RecFNToRecFN(io.b.exp_width, io.b.sig_width, io.c.exp_width, io.c.sig_width))
//  b_resizer.io.in := b_rec
//  b_resizer.io.roundingMode := consts.round_near_even // consts.round_near_maxMag
//  b_resizer.io.detectTininess := consts.tininess_afterRounding
//  val b_rec_resized = b_resizer.io.out
//
//  // Perform multiply-add
//  val muladder = Module(new MulAddRecFN(io.c.exp_width, io.c.sig_width))
//
//  muladder.io.op := 0.U
////  muladder.io.roundingMode := consts.round_near_even // consts.round_near_maxMag
//  muladder.io.roundingMode := consts.round_near_even
//  muladder.io.detectTininess := consts.tininess_afterRounding
//
//  muladder.io.a := a_rec_resized
//  muladder.io.b := b_rec_resized
//  muladder.io.c := c_rec
//
//  // Convert result to standard format // TODO remove these intermediate recodings
////  val out = Wire(Float(io.a.exp_width, io.a.sig_width))
////  out.bits := fNFromRecFN(io.a.exp_width, io.a.sig_width, muladder.io.out)
////  printf()
// io.out.bits :=fNFromRecFN(io.a.exp_width, io.a.sig_width, muladder.io.out)
//}