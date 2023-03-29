//
//import chisel3._
//import chisel3.stage.ChiselGeneratorAnnotation
//import chisel3.util.{Decoupled, ShiftRegister}
//import chisel3.util.experimental.forceName
//
//class tcp extends Module with hw_config with sim_config{
//  val io = IO(new Bundle() {
//    /********************************************* hardware ************************************************/
//    //axi_dma
//    val axi_dma_0 = if(!simMode) Some(new axi_full_io(dma_ch_width,dmaAddrWidth,dmaDataWidth)) else None
//    val axi_dma_1 = if(!simMode) Some(new axi_full_io(dma_ch_width,dmaAddrWidth,dmaDataWidth)) else None
//    //intr
//    val alu_mat_task_done = Output(Bool())
//    //axi_lite
//    val accel_axi_lite = if(!simMode) Some(new axi_lite_io(ACCEL_AXI_DATA_WIDTH,ACCEL_AXI_ADDR_WIDTH)) else None
//
//    /********************************************* simulation ************************************************/
//    //dma_sim
//    val dma_ch0_r = if (simMode & dma_en) Some(new dmaR_io(dmaAddrWidth, dmaDataWidth)) else None
//    val dma_ch0_w = if (simMode & dma_en) Some(new dmaW_io(dmaAddrWidth, dmaDataWidth)) else None
//    val dma_ch1_r = if (simMode & dma_en) Some(new dmaR_io(dmaAddrWidth, dmaDataWidth)) else None
//    val dma_ch1_w = if (simMode & dma_en) Some(new dmaW_io(dmaAddrWidth, dmaDataWidth)) else None
//    val sim_accel_reg = if(simMode) Some(new regPort(dmaAddrWidth)) else None
//    //im2col_sim
//    val ifm_r = if(im2col_en && simMode) Some(new ifm_r_io(ifm_buffer_size, ifm_buffer_width*2)) else None
//    val im2col_task_done = if (im2col_en && simMode) Some(Output(Bool())) else None
//    //wgtBuf_sim
//    val wgt_fifo = if(wgtBuf_en && simMode) Some(Decoupled(UInt(1024.W))) else None
//    val wgt_task_done = if(wgtBuf_en && simMode) Some(Output(Bool())) else  None
//    //math_sim
//    val alu_math_task_done = if(simMode) Some(Output(Bool())) else None
//    val sim_math_reg_ctrl = if (simMode) Some(Input(UInt(32.W))) else None
//    val sim_math_reg_data_i = if (simMode) Some(Input(UInt(32.W))) else None
//    val sim_math_reg_data_o = if (simMode) Some(Output(UInt(32.W))) else None
//    //fp32_sim
//    val fp32_a = if ((fp32_adder_sim | fp32_multiplier_sim) & simMode) Some(Input(UInt(32.W))) else None
//    val fp32_b = if ((fp32_adder_sim | fp32_multiplier_sim) & simMode) Some(Input(UInt(32.W))) else None
//    val fp32_c = if ((fp32_adder_sim | fp32_multiplier_sim) & simMode) Some(Output(UInt(32.W))) else None
//    val fp32_en = if ((fp32_adder_sim | fp32_multiplier_sim) & simMode) Some(Input(Bool())) else None
//    val fp32_test_done = if ((fp32_adder_sim | fp32_multiplier_sim) & simMode) Some(Output(Bool())) else None
//    val fp32_test_valid = if ((fp32_adder_sim | fp32_multiplier_sim) & simMode) Some(Output(Bool())) else None
//    //pe_sim
//    val pe_a = if (pe_sim  & simMode) Some(Input(UInt(32.W))) else None
//    val pe_b = if (pe_sim  & simMode) Some(Input(UInt(32.W))) else None
//    val pe_c0 = if (pe_sim  & simMode) Some(Input(UInt(32.W))) else None
//    val pe_c1 = if (pe_sim  & simMode) Some(Input(UInt(32.W))) else None
//    val pe_i_valid = if (pe_sim  & simMode) Some(Input(Bool())) else None
//    val pe_d0 = if (pe_sim & simMode) Some(Output(UInt(32.W))) else None
//    val pe_d1 = if (pe_sim & simMode) Some(Output(UInt(32.W))) else None
//    val pe_o_valid = if (pe_sim  & simMode) Some(Output(Bool())) else None
//
//    //opfusion_sim
//    val opfusion_i_data = if(simMode && opfusion_en) Some(Input(UInt(1024.W))) else None
//    val opfusion_i_valid = if(simMode && opfusion_en) Some(Input(Bool())) else None
//    val opfusion_o_ifm_int8 = if(simMode && opfusion_en) Some(Output(UInt(256.W))) else None
//    val opfusion_o_ifm_fp32 = if(simMode && opfusion_en) Some(Output(UInt(1024.W))) else None
//    val opfusion_o_mat32 = if(simMode && opfusion_en) Some(Output(UInt(1024.W))) else None
//    val opfusion_o_valid = if(simMode && opfusion_en) Some(Output(Bool())) else None
//    val opfusion_o_ready = if(simMode && opfusion_en) Some(Output(Bool())) else None
//    val opfusion_cur_c_index = if(simMode && opfusion_en) Some(Input(UInt(6.W))) else None
//  })
//
//  /********************************************* dma ************************************************/
//  val dma_ch0 = if (dma_en) Some(Module(new dma_ch(dma_ch0_en_cfg))) else None
//  val dma_ch1 = if (dma_en) Some(Module(new dma_ch(dma_ch1_en_cfg))) else None
//  val axi_dma_0 = if (!simMode) Some(Module(new axi_dma(0, dma_ch_width, dmaAddrWidth, dmaDataWidth))) else None
//  val axi_dma_1 = if (!simMode) Some(Module(new axi_dma(1, dma_ch_width, dmaAddrWidth, dmaDataWidth))) else None
//  if (!simMode) {
//    axi_dma_0.get.io.M <> io.axi_dma_0.get
//    axi_dma_0.get.io.M_AXI_ACLK <> clock
//    axi_dma_0.get.io.M_AXI_ARESETN <> ~reset.asBool
//    axi_dma_1.get.io.M <> io.axi_dma_1.get
//    axi_dma_1.get.io.M_AXI_ACLK <> clock
//    axi_dma_1.get.io.M_AXI_ARESETN <> ~reset.asBool
//    dma_ch0.get.io.dmaR <> axi_dma_0.get.io.fdma_r
//    dma_ch1.get.io.dmaR <> axi_dma_1.get.io.fdma_r
//    dma_ch0.get.io.dmaW <> axi_dma_0.get.io.fdma_w
//    dma_ch1.get.io.dmaW <> axi_dma_1.get.io.fdma_w
//  } else if (dma_en) {
//    dma_ch0.get.io.dmaR <> io.dma_ch0_r.get
//    dma_ch1.get.io.dmaR <> io.dma_ch1_r.get
//    dma_ch0.get.io.dmaW <> io.dma_ch0_w.get
//    dma_ch1.get.io.dmaW <> io.dma_ch1_w.get
//  }
//
//  /********************************************* axi-lite ************************************************/
//  val regMap = Module(new regMap)
//  if(!simMode) {
//    val axi_lite_accel = Module(new axi_lite_accel)
//    axi_lite_accel.io.S <> io.accel_axi_lite.get
//    axi_lite_accel.io.S_AXI_ACLK <> clock
//    axi_lite_accel.io.S_AXI_ARESETN <> ~reset.asBool
//    regMap.io.regPort.shape_bc0_reg := axi_lite_accel.io.o_slv.reg0
//    regMap.io.regPort.shape_wh0_reg := axi_lite_accel.io.o_slv.reg1
//    regMap.io.regPort.shape_cstep0_reg := axi_lite_accel.io.o_slv.reg2
//    regMap.io.regPort.shape_bc1_reg := axi_lite_accel.io.o_slv.reg3
//    regMap.io.regPort.shape_wh1_reg := axi_lite_accel.io.o_slv.reg4
//    regMap.io.regPort.shape_cstep1_reg := axi_lite_accel.io.o_slv.reg5
//    regMap.io.regPort.src0_addr0_reg := axi_lite_accel.io.o_slv.reg6
//    regMap.io.regPort.src1_addr0_reg := axi_lite_accel.io.o_slv.reg7
//    regMap.io.regPort.dst_addr0_reg := axi_lite_accel.io.o_slv.reg8
//    regMap.io.regPort.src0_addr1_reg := axi_lite_accel.io.o_slv.reg9
//    regMap.io.regPort.src1_addr1_reg := axi_lite_accel.io.o_slv.reg10
//    regMap.io.regPort.dst_addr1_reg := axi_lite_accel.io.o_slv.reg11
//    regMap.io.regPort.alu_ctrl_reg := axi_lite_accel.io.o_slv.reg12
//    regMap.io.regPort.alu_veclen0_reg := axi_lite_accel.io.o_slv.reg13
//    regMap.io.regPort.alu_veclen1_reg := axi_lite_accel.io.o_slv.reg14
//    regMap.io.regPort.pool_ctrl_reg := axi_lite_accel.io.o_slv.reg15
//    regMap.io.regPort.gemm_ctrl_reg := axi_lite_accel.io.o_slv.reg16
//    regMap.io.regPort.opfusion_ctrl_reg := axi_lite_accel.io.o_slv.reg17
//    regMap.io.regPort.quant_scale_reg := axi_lite_accel.io.o_slv.reg18
//    regMap.io.regPort.dequant_scale_reg := axi_lite_accel.io.o_slv.reg19
//    regMap.io.regPort.requant_scale_reg := axi_lite_accel.io.o_slv.reg20
//    regMap.io.regPort.bias_addr_reg := axi_lite_accel.io.o_slv.reg21
//    regMap.io.regPort.leakyrelu_param_reg := axi_lite_accel.io.o_slv.reg22
//  }
//  else{
//    regMap.io.regPort.shape_bc0_reg := io.sim_accel_reg.get.shape_bc0_reg
//    regMap.io.regPort.shape_wh0_reg := io.sim_accel_reg.get.shape_wh0_reg
//    regMap.io.regPort.shape_cstep0_reg := io.sim_accel_reg.get.shape_cstep0_reg
//    regMap.io.regPort.shape_bc1_reg := io.sim_accel_reg.get.shape_bc1_reg
//    regMap.io.regPort.shape_wh1_reg := io.sim_accel_reg.get.shape_wh1_reg
//    regMap.io.regPort.shape_cstep1_reg := io.sim_accel_reg.get.shape_cstep1_reg
//    regMap.io.regPort.src0_addr0_reg := io.sim_accel_reg.get.src0_addr0_reg
//    regMap.io.regPort.src1_addr0_reg := io.sim_accel_reg.get.src1_addr0_reg
//    regMap.io.regPort.dst_addr0_reg := io.sim_accel_reg.get.dst_addr0_reg
//    regMap.io.regPort.src0_addr1_reg := io.sim_accel_reg.get.src0_addr1_reg
//    regMap.io.regPort.src1_addr1_reg := io.sim_accel_reg.get.src1_addr1_reg
//    regMap.io.regPort.dst_addr1_reg := io.sim_accel_reg.get.dst_addr1_reg
//    regMap.io.regPort.alu_ctrl_reg := io.sim_accel_reg.get.alu_ctrl_reg
//    regMap.io.regPort.alu_veclen0_reg := io.sim_accel_reg.get.alu_veclen0_reg
//    regMap.io.regPort.alu_veclen1_reg := io.sim_accel_reg.get.alu_veclen1_reg
//    regMap.io.regPort.pool_ctrl_reg := io.sim_accel_reg.get.pool_ctrl_reg
//    regMap.io.regPort.gemm_ctrl_reg := io.sim_accel_reg.get.gemm_ctrl_reg
//    regMap.io.regPort.opfusion_ctrl_reg := io.sim_accel_reg.get.opfusion_ctrl_reg
//    regMap.io.regPort.quant_scale_reg := io.sim_accel_reg.get.quant_scale_reg
//    regMap.io.regPort.dequant_scale_reg := io.sim_accel_reg.get.dequant_scale_reg
//    regMap.io.regPort.requant_scale_reg := io.sim_accel_reg.get.requant_scale_reg
//    regMap.io.regPort.bias_addr_reg := io.sim_accel_reg.get.bias_addr_reg
//    regMap.io.regPort.leakyrelu_param_reg := io.sim_accel_reg.get.leakyrelu_param_reg
//  }
//  /********************************************* math ************************************************/
//  if(simMode){
//    val alu_func = Module(new alu_math)
//    alu_func.io.math_ctrl_reg := RegNext(io.sim_math_reg_ctrl.get)
//    alu_func.io.data_i := RegNext(io.sim_math_reg_data_i.get)
//    io.sim_math_reg_data_o.get := alu_func.io.data_o
//    io.alu_math_task_done.get := alu_func.io.task_done
//  }
//
//
//
//  /********************************************* alu ************************************************/
//  val alu_mat = if(alu_mat_en) Some(Module(new alu_mat)) else None
//  io.alu_mat_task_done := (if(alu_mat_en) alu_mat.get.io.task_done else 0.U)
//  if (alu_mat_en) {
//    alu_mat.get.io.dma_rid_ch0 := dma_ch0.get.io.rid
//    alu_mat.get.io.dma_rbusy_ch0 := dma_ch0.get.io.dmaRbusy
//    alu_mat.get.io.dma_rdata_ch0 <> dma_ch0.get.io.aluRData.get
//    alu_mat.get.io.dma_rareq_ch0 <> dma_ch0.get.io.aluRAreq.get
//    alu_mat.get.io.dma_rid_ch1 := dma_ch1.get.io.rid
//    alu_mat.get.io.dma_rbusy_ch1 := dma_ch1.get.io.dmaRbusy
//    alu_mat.get.io.dma_rdata_ch1 <> dma_ch1.get.io.aluRData.get
//    alu_mat.get.io.dma_rareq_ch1 <> dma_ch1.get.io.aluRAreq.get
//
//    alu_mat.get.io.dma_wid_ch0 := dma_ch0.get.io.wid
//    alu_mat.get.io.dma_wbusy_ch0 := dma_ch0.get.io.dmaWbusy
//    alu_mat.get.io.dma_wdata_ch0 <> dma_ch0.get.io.aluWData.get
//    alu_mat.get.io.dma_wareq_ch0 <> dma_ch0.get.io.aluWAreq.get
//    alu_mat.get.io.dma_wid_ch1 := dma_ch1.get.io.wid
//    alu_mat.get.io.dma_wbusy_ch1 := dma_ch1.get.io.dmaWbusy
//    alu_mat.get.io.dma_wdata_ch1 <> dma_ch1.get.io.aluWData.get
//    alu_mat.get.io.dma_wareq_ch1 <> dma_ch1.get.io.aluWAreq.get
//
//    alu_mat.get.io.ch0_src0_addr := regMap.io.ch0_src0_addr
//    alu_mat.get.io.ch0_src1_addr := regMap.io.ch0_src1_addr
//    alu_mat.get.io.ch0_dst_addr := regMap.io.ch0_dst_addr
//    alu_mat.get.io.ch1_src0_addr := regMap.io.ch1_src0_addr
//    alu_mat.get.io.ch1_src1_addr := regMap.io.ch1_src1_addr
//    alu_mat.get.io.ch1_dst_addr := regMap.io.ch1_dst_addr
//    alu_mat.get.io.alu_en := regMap.io.alu_en
//    alu_mat.get.io.alu_op := regMap.io.alu_op
//    alu_mat.get.io.alu_type := regMap.io.alu_type
//    alu_mat.get.io.alu_channels := regMap.io.alu_channels
//    alu_mat.get.io.alu_veclen_0 := regMap.io.alu_veclen_0
//    alu_mat.get.io.alu_veclen_1 := regMap.io.alu_veclen_1
//  }
//  else if(dma_en){
//    dma_ch0.get.io.aluRAreq.get <> 0.U.asTypeOf(new dmaCtrl_io(dmaSizeWidth, dmaAddrWidth))
//    dma_ch0.get.io.aluWAreq.get <> 0.U.asTypeOf(new dmaCtrl_io(dmaSizeWidth, dmaAddrWidth))
//    dma_ch1.get.io.aluRAreq.get <> 0.U.asTypeOf(new dmaCtrl_io(dmaSizeWidth, dmaAddrWidth))
//    dma_ch1.get.io.aluWAreq.get <> 0.U.asTypeOf(new dmaCtrl_io(dmaSizeWidth, dmaAddrWidth))
//    dma_ch0.get.io.aluWData.get.data := 0.U
//    dma_ch1.get.io.aluWData.get.data := 0.U
//  }
//
//  /********************************************* im2col ************************************************/
//  val im2col = if(im2col_en) Some(Module(new im2col)) else None
//  if (im2col_en) {
//    im2col.get.io.dma_ch0_rid := dma_ch0.get.io.rid
//    im2col.get.io.dma_ch0_rbusy := dma_ch0.get.io.dmaRbusy
//    im2col.get.io.dma_ch0_rdata <> dma_ch0.get.io.im2colRData.get
//    im2col.get.io.dma_ch0_rareq <> dma_ch0.get.io.im2colRAreq.get
//    im2col.get.io.clr := 0.U
//
//    im2col.get.io.ch0_whc := regMap.io.ch0_whc
//    im2col.get.io.ch0_cstep := regMap.io.ch0_cstep
//    im2col.get.io.im2col_en := regMap.io.gemm_en
//    im2col.get.io.quant_scale := regMap.io.quant_scale
//    im2col.get.io.ch0_src0_addr := regMap.io.ch0_src0_addr
//    im2col.get.io.im2col_format := regMap.io.gemm_format
//
//
//    dontTouch(im2col.get.io.cache_valid)
//    dontTouch(im2col.get.io.cache_whc)
//    if(simMode){
//      io.ifm_r.get <> im2col.get.io.ifm_read_port
//      io.im2col_task_done.get := im2col.get.io.task_done
//    }
//    else{
//      im2col.get.io.ifm_read_port.ren := 0.U
//      im2col.get.io.ifm_read_port.raddr := 0.U
//      dontTouch(im2col.get.io.ifm_read_port.rdata)
//    }
//  }
//  else if(dma_en){
//    dma_ch0.get.io.im2colRAreq.get <> 0.U.asTypeOf(new dmaCtrl_io(dmaSizeWidth, dmaAddrWidth))
//  }
//
//  /********************************************* weight_buffer ************************************************/
//  val wgtBuf = if(wgtBuf_en) Some(Module(new weightBuffer)) else None
//  if(wgtBuf_en) {
//    wgtBuf.get.io.start := regMap.io.gemm_en
//    wgtBuf.get.io.format := regMap.io.gemm_format
//    wgtBuf.get.io.kernel := regMap.io.kernel
//    wgtBuf.get.io.ch0_whc := regMap.io.ch0_whc
//    wgtBuf.get.io.ch1_whc := regMap.io.ch1_whc
//    wgtBuf.get.io.ch1_cstep := regMap.io.ch1_cstep
//    wgtBuf.get.io.ofm_whc := regMap.io.ofm_whc
//    wgtBuf.get.io.src_addr := regMap.io.ch1_src0_addr
//
//    wgtBuf.get.io.dma_rid := dma_ch1.get.io.rid
//    wgtBuf.get.io.dma_rbusy := dma_ch1.get.io.dmaRbusy
//    wgtBuf.get.io.dma_rdata <> dma_ch1.get.io.wgtBufRData.get
//    wgtBuf.get.io.dma_rareq <> dma_ch1.get.io.wgtBufRAreq.get
//
//    if(simMode){
//      io.wgt_fifo.get <> wgtBuf.get.io.o_fifo
//      io.wgt_task_done.get := wgtBuf.get.io.task_done
//    }
//    else{
//      wgtBuf.get.io.o_fifo.ready := 0.U
//    }
//  }
//  else if(dma_en){
//    dma_ch1.get.io.wgtBufRAreq.get <> 0.U.asTypeOf(new dmaCtrl_io(dmaSizeWidth, dmaAddrWidth))
//  }
//
//  /** ******************************************* fp32_cal *********************************************** */
//  if(fp32_adder_sim && simMode){
//    val fp32_adder = Module(new FP32_Adder)
//    fp32_adder.io.x := io.fp32_a.get
//    fp32_adder.io.y := io.fp32_b.get
//    io.fp32_c.get := fp32_adder.io.z
//    fp32_adder.io.valid_in := io.fp32_en.get
//    io.fp32_test_done.get := fallEdge(fp32_adder.io.valid_out.asBool)
//    io.fp32_test_valid.get := fp32_adder.io.valid_out.asBool
//  }
//  else if(fp32_multiplier_sim && simMode){
//    val fp32_multiplier = Module(new FP32_Multiplier)
//    fp32_multiplier.io.x := io.fp32_a.get
//    fp32_multiplier.io.y := io.fp32_b.get
//    io.fp32_c.get := RegNext(fp32_multiplier.io.z)
//    io.fp32_test_valid.get := RegNext(io.fp32_en.get)
//    io.fp32_test_done.get := fallEdge(io.fp32_test_valid.get)
//  }
//
//  /** ******************************************* pe *********************************************** */
//  val pe = if(pe_sim & simMode) Some(Module(new pe)) else None
//  if(pe_sim & simMode){
//    pe.get.io.a := io.pe_a.get
//    pe.get.io.b := io.pe_b.get
//    pe.get.io.c0 := io.pe_c0.get
//    pe.get.io.c1 := io.pe_c1.get
//    io.pe_d0.get := pe.get.io.d0
//    io.pe_d1.get := pe.get.io.d1
//    io.pe_o_valid.get := ShiftRegister(io.pe_i_valid.get,4)
//  }
//
//  /** ******************************************* opfusion *********************************************** */
//  val opfusion = if(opfusion_en) Some(Module(new opfusion)) else None
//  if(opfusion_en){
//    opfusion.get.io.i_start := regMap.io.gemm_en
//    opfusion.get.io.i_mode := regMap.io.gemm_format
//    opfusion.get.io.i_oscale := regMap.io.dequant_scale
//    opfusion.get.io.i_bias := regMap.io.bias_addr
//    opfusion.get.io.i_bias_en := regMap.io.bias_en
//    opfusion.get.io.i_act_mode := regMap.io.activate_type
//    opfusion.get.io.i_leakyrelu_param := regMap.io.leakyrelu_param
//    opfusion.get.io.i_rescale := regMap.io.requant_scale
//    opfusion.get.io.i_rescale_en := regMap.io.requant_en
//    opfusion.get.io.i_oc := regMap.io.ofm_whc.c
//
//    opfusion.get.io.dma_rid := dma_ch1.get.io.rid
//    opfusion.get.io.dma_rbusy := dma_ch1.get.io.dmaRbusy
//    opfusion.get.io.dma_rdata <> dma_ch1.get.io.opfusionRData.get
//    opfusion.get.io.dma_rareq <> dma_ch1.get.io.opfusionRAreq.get
//
//    opfusion.get.io.i_data := io.opfusion_i_data.get
//    opfusion.get.io.i_valid := io.opfusion_i_valid.get
//    io.opfusion_o_ifm_int8.get := opfusion.get.io.o_ifm_int8
//    io.opfusion_o_ifm_fp32.get := opfusion.get.io.o_ifm_fp32
//    io.opfusion_o_mat32.get := opfusion.get.io.o_mat32
//    io.opfusion_o_valid.get := opfusion.get.io.o_valid
//    io.opfusion_o_ready.get := opfusion.get.io.o_ready
//    opfusion.get.io.cur_c_index := io.opfusion_cur_c_index.get
//  }
//  else if(dma_en){
//    dma_ch1.get.io.opfusionRAreq.get <> 0.U.asTypeOf(new dmaCtrl_io(dmaSizeWidth, dmaAddrWidth))
//  }
//}
//
//
//class math extends Module with hw_config with sim_config{
//  val io = IO(new Bundle() {
//    val math_axi_lite = if (!simMode) Some(new axi_lite_io(MATH_AXI_DATA_WIDTH, MATH_AXI_ADDR_WIDTH)) else None
//    val alu_math_task_done = Output(Bool())
//    val sim_math_reg_ctrl = if (simMode) Some(Input(UInt(32.W))) else None
//    val sim_math_reg_data_i = if (simMode) Some(Input(UInt(32.W))) else None
//    val sim_math_reg_data_o = if (simMode) Some(Output(UInt(32.W))) else None
//  })
//
//  /** ******************************************* math *********************************************** */
//  if (alu_mathfunc_en) {
//    if (!simMode) {
//      val alu_func = Module(new alu_math)
//      val axi_lite_math = Module(new axi_lite_math)
//      axi_lite_math.io.S <> io.math_axi_lite.get
//      axi_lite_math.io.S_AXI_ACLK <> clock
//      axi_lite_math.io.S_AXI_ARESETN <> ~reset.asBool
//      alu_func.io.math_ctrl_reg := axi_lite_math.io.o_slv.reg0
//      alu_func.io.data_i := axi_lite_math.io.o_slv.reg1
//      axi_lite_math.io.i_slv.reg1 := alu_func.io.data_o
//      axi_lite_math.io.i_slv.reg1_valid := alu_func.io.valid_o
//      io.alu_math_task_done := alu_func.io.task_done
//    }
//    else {
//      val alu_func = Module(new alu_math)
//      alu_func.io.math_ctrl_reg := RegNext(io.sim_math_reg_ctrl.get)
//      alu_func.io.data_i := RegNext(io.sim_math_reg_data_i.get)
//      io.sim_math_reg_data_o.get := alu_func.io.data_o
//      io.alu_math_task_done := alu_func.io.task_done
//    }
//  }
//}
//
//object accel_gen extends App{
//  new (chisel3.stage.ChiselStage).execute(Array("--target-dir","./verilog/accel"),Seq(ChiselGeneratorAnnotation(()=>new tcp)))
//}
//
//object math_gen extends App{
//  new (chisel3.stage.ChiselStage).execute(Array("--target-dir","./verilog/math"),Seq(ChiselGeneratorAnnotation(()=>new math)))
//}
//
//
//
//
