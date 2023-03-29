import chisel3._
import chisel3.stage.ChiselGeneratorAnnotation
import chisel3.util._

//dma read port
class dmaR_io(dmaAddrWidth:Int,dmaDataWidth:Int) extends Bundle{
  val addr = Output(UInt(dmaAddrWidth.W))
  val areq = Output(Bool())
  val size = Output(UInt(32.W))
  val busy = Input(Bool())
  val data = Input(UInt(dmaDataWidth.W))
  val valid = Input(Bool())
  val ready = Output(Bool())
}

//dma write port
class dmaW_io(dmaAddrWidth:Int,dmaDataWidth:Int) extends Bundle{
  val addr = Output(UInt(dmaAddrWidth.W))
  val areq = Output(Bool())
  val size = Output(UInt(32.W))
  val busy = Input(Bool())
  val data = Output(UInt(dmaDataWidth.W))
  val valid = Input(Bool())
  val ready = Output(Bool())
}

class dmaCtrl_io(dmaSizeWidth:Int,dmaAddrWidth:Int) extends Bundle{
  val dmaEn = Output(Bool())
  val dmaAreq = Output(Bool())
  val dmaSize = Output(UInt(dmaSizeWidth.W))
  val dmaAddr = Output(UInt(dmaAddrWidth.W))
}

class dmaRData_io(dmaDataWidth:Int) extends Bundle{
  val valid = Input(Bool())
  val data = Input(UInt(dmaDataWidth.W))
}

class dmaWData_io(dmaDataWidth:Int) extends Bundle{
  val valid = Input(Bool())
  val data = Output(UInt(dmaDataWidth.W))
}

class dmaAreqAbrit extends Module with dma_config {
  val io = IO(new Bundle() {
    val aluAreq = Flipped(new dmaCtrl_io(dmaSizeWidth, dmaAddrWidth))
    val im2colAreq = Flipped(new dmaCtrl_io(dmaSizeWidth, dmaAddrWidth))
    val wgtBufAreq = Flipped(new dmaCtrl_io(dmaSizeWidth, dmaAddrWidth))
    val opfusionAreq = Flipped(new dmaCtrl_io(dmaSizeWidth, dmaAddrWidth))
    //...
    val sel          = Output(UInt(log2Ceil(id.values.toList.max+1).W))
    val toDma        = new dmaCtrl_io(dmaSizeWidth, dmaAddrWidth)
  })

  val sChose :: sKeep :: sFinish :: Nil = Enum(3)
  val state = RegInit(sChose)

  val sel = RegInit(0.U)
  val addr = RegInit(0.U)
  val size = RegInit(0.U)
  val en = RegInit(0.U)
  val areq = RegInit(0.U)

  //dmaEn must be high during select
  switch(state){
    is(sChose){
      sel := 0.U
      state := sChose
      when(io.aluAreq.dmaEn){
        sel := id("alu").U
        state := sKeep
      }.elsewhen(io.im2colAreq.dmaEn){
        sel := id("im2col").U
        state := sKeep
      }.elsewhen(io.wgtBufAreq.dmaEn){
        sel := id("wgtBuf").U
        state := sKeep
      }.elsewhen(io.opfusionAreq.dmaEn){
        sel := id("opfusion").U
        state := sKeep
      }
      //...
    }
    is(sKeep)   {state := Mux(fallEdge(io.toDma.dmaEn),sFinish,sKeep)}
    is(sFinish) {state := sChose}
  }

  //allocate data based on sel
  switch(sel){
    is(0.U){  //no effect
      en := false.B
    }
    is(id("alu").U){  //dma_alu
      en := io.aluAreq.dmaEn
      addr := io.aluAreq.dmaAddr
      size := io.aluAreq.dmaSize
      areq := io.aluAreq.dmaAreq
    }
    is(id("im2col").U) { //dma_im2col
      en := io.im2colAreq.dmaEn
      addr := io.im2colAreq.dmaAddr
      size := io.im2colAreq.dmaSize
      areq := io.im2colAreq.dmaAreq
    }
    is(id("wgtBuf").U) { //dma_wgtBuf
      en := io.wgtBufAreq.dmaEn
      addr := io.wgtBufAreq.dmaAddr
      size := io.wgtBufAreq.dmaSize
      areq := io.wgtBufAreq.dmaAreq
    }
    is (id("opfusion").U) { //dma_opfusion
      en := io.opfusionAreq.dmaEn
      addr := io.opfusionAreq.dmaAddr
      size := io.opfusionAreq.dmaSize
      areq := io.opfusionAreq.dmaAreq
    }
    //...
  }
  io.sel := sel;
  io.toDma.dmaAddr := addr
  io.toDma.dmaSize := size
  io.toDma.dmaEn := en
  io.toDma.dmaAreq := areq
}

//----------------------read channel----------------------//
class dmaRBufSel extends Module with dma_config{
  val io = IO(new Bundle() {
    val sel = Input(UInt(log2Ceil(id.values.toList.max+1).W))
    val dataIn = new dmaRData_io(dmaDataWidth)
    val aluData = Flipped(new dmaRData_io(dmaDataWidth))
    val im2colData = Flipped(new dmaRData_io(dmaDataWidth))
    val wgtBufData = Flipped(new dmaRData_io(dmaDataWidth))
    val opfusionData = Flipped(new dmaRData_io(dmaDataWidth))
    //...
  })
  io.aluData.data := Mux(io.sel === id("alu").U,io.dataIn.data,0.U)
  io.aluData.valid  :=  Mux(io.sel === id("alu").U,io.dataIn.valid,0.U)
  io.im2colData.data := Mux(io.sel === id("im2col").U, io.dataIn.data, 0.U)
  io.im2colData.valid := Mux(io.sel === id("im2col").U, io.dataIn.valid, 0.U)
  io.wgtBufData.data := Mux(io.sel === id("wgtBuf").U, io.dataIn.data, 0.U)
  io.wgtBufData.valid := Mux(io.sel === id("wgtBuf").U, io.dataIn.valid, 0.U)
  io.opfusionData.data := Mux(io.sel === id("opfusion").U, io.dataIn.data, 0.U)
  io.opfusionData.valid := Mux(io.sel === id("opfusion").U, io.dataIn.valid, 0.U)
  //...
}

class dmaR extends Module with dma_config{
  val io = IO(new Bundle() {
    val dma_r = new dmaR_io(dmaAddrWidth,dmaDataWidth)
    val dataOut = Flipped(new dmaRData_io(dmaDataWidth))
    val dmaCtrl = Flipped(new dmaCtrl_io(dmaSizeWidth, dmaAddrWidth))  //ctrl input
    val dmaRbusy = Output(Bool())
  })
  io.dma_r.ready := 1.U
  io.dma_r.areq := io.dmaCtrl.dmaAreq
  io.dma_r.addr := io.dmaCtrl.dmaAddr
  io.dma_r.size := io.dmaCtrl.dmaSize
  io.dataOut.valid := io.dma_r.valid
  io.dataOut.data := io.dma_r.data
  io.dmaRbusy := io.dma_r.busy
}

//----------------------write channel----------------------//
class dmaWBufSel extends Module with dma_config{
  val io = IO(new Bundle() {
    val sel = Input(UInt(log2Ceil(id.values.toList.max+1).W))
    val dataOut = new dmaWData_io(dmaDataWidth)
    val aluData = Flipped(new dmaWData_io(dmaDataWidth))
    //...
  })

  io.dataOut.data := 0.U
  io.aluData.valid := 0.U
  when(io.sel === id("alu").U) {
    io.dataOut.data := io.aluData.data
    io.aluData.valid := io.dataOut.valid
  }
  //...
}

class dmaW extends Module with dma_config{
  val io = IO(new Bundle() {
    val dma_w = new dmaW_io(dmaAddrWidth,dmaDataWidth)
    val dataIn = Flipped(new dmaWData_io(dmaDataWidth))
    val dmaCtrl = Flipped(new dmaCtrl_io(dmaSizeWidth, dmaAddrWidth))  //ctrl input
    val dmaWbusy = Output(Bool())
  })
  io.dma_w.ready := 1.U
  io.dma_w.areq := io.dmaCtrl.dmaAreq
  io.dma_w.addr := io.dmaCtrl.dmaAddr
  io.dma_w.size := io.dmaCtrl.dmaSize
  io.dma_w.data := io.dataIn.data
  io.dataIn.valid := io.dma_w.valid
  io.dmaWbusy := io.dma_w.busy
}

//-------------------------axi_full--------------------------//
class axi_full_io(id_width:Int, axi_addr_width:Int = 32, axi_data_width:Int = 128) extends Bundle{
  val AXI_AWID = Output(UInt(id_width.W))
  val AXI_AWADDR = Output(UInt(axi_addr_width.W))
  val AXI_AWLEN = Output(UInt(8.W))
  val AXI_AWSIZE = Output(UInt(3.W))
  val AXI_AWBURST = Output(UInt(2.W))
  val AXI_AWLOCK = Output(Bool())
  val AXI_AWCACHE = Output(UInt(4.W))
  val AXI_AWPROT = Output(UInt(3.W))
  val AXI_AWQOS = Output(UInt(4.W))
  val AXI_AWVALID = Output(Bool())
  val AXI_AWREADY = Input(Bool())
  val AXI_WID = Output(UInt(id_width.W))
  val AXI_WDATA = Output(UInt(axi_data_width.W))
  val AXI_WSTRB = Output(UInt((axi_data_width / 8).W))
  val AXI_WLAST = Output(Bool())
  val AXI_WVALID = Output(Bool())
  val AXI_WREADY = Input(Bool())
  val AXI_BID = Input(UInt(id_width.W))
  val AXI_BRESP = Input(UInt(2.W))
  val AXI_BVALID = Input(Bool())
  val AXI_BREADY = Output(Bool())
  val AXI_ARID = Output(UInt(id_width.W))

  val AXI_ARADDR = Output(UInt(axi_addr_width.W))
  val AXI_ARLEN = Output(UInt(8.W))
  val AXI_ARSIZE = Output(UInt(3.W))
  val AXI_ARBURST = Output(UInt(2.W))
  val AXI_ARLOCK = Output(Bool())
  val AXI_ARCACHE = Output(UInt(4.W))
  val AXI_ARPROT = Output(UInt(3.W))
  val AXI_ARQOS = Output(UInt(4.W))
  val AXI_ARVALID = Output(Bool())
  val AXI_ARREADY = Input(Bool())
  val AXI_RID = Input(UInt(id_width.W))
  val AXI_RDATA = Input(UInt(axi_data_width.W))
  val AXI_RRESP = Input(UInt(2.W))
  val AXI_RLAST = Input(Bool())
  val AXI_RVALID = Input(Bool())
  val AXI_RREADY = Output(Bool())
}

class axi_dma(id:Int, id_width:Int, axi_addr_width:Int = 32, axi_data_width:Int = 128) extends BlackBox(
  Map("M_AXI_ID"->id, "M_AXI_ID_WIDTH"->id_width, "M_AXI_ADDR_WIDTH"->axi_addr_width, "M_AXI_DATA_WIDTH"->axi_data_width)) with HasBlackBoxPath{
  val io = IO(new Bundle{
    val M_AXI_ACLK = Input(Clock())
    val M_AXI_ARESETN = Input(Bool())
    val fdma_w = Flipped(new dmaW_io(axi_addr_width,axi_data_width))
    val fdma_r = Flipped(new dmaR_io(axi_addr_width,axi_data_width))
    val M = new axi_full_io(id_width, axi_addr_width, axi_data_width)
  })
  addPath("./src/main/hdl/axi_dma.v")
}

class dma_ch(en_cfg:Map[String,Boolean]) extends  Module with hw_config{
  val io = IO(new  Bundle() {
    //----------------------read channel----------------------//
    val dmaR = new dmaR_io(dmaAddrWidth, dmaDataWidth)
    val dmaRbusy = Output(Bool())
    val rid = Output(UInt(log2Ceil(id.values.toList.max+1).W))
    val aluRAreq = if(en_cfg("alu")) Some(Flipped(new dmaCtrl_io(dmaSizeWidth, dmaAddrWidth))) else None
    val aluRData = if(en_cfg("alu")) Some(Flipped(new dmaRData_io(dmaDataWidth))) else None
    val im2colRAreq = if(en_cfg("im2col")) Some(Flipped(new dmaCtrl_io(dmaSizeWidth, dmaAddrWidth))) else None
    val im2colRData = if(en_cfg("im2col")) Some(Flipped(new dmaRData_io(dmaDataWidth))) else None
    val wgtBufRAreq = if (en_cfg("wgtBuf")) Some(Flipped(new dmaCtrl_io(dmaSizeWidth, dmaAddrWidth))) else None
    val wgtBufRData = if (en_cfg("wgtBuf")) Some(Flipped(new dmaRData_io(dmaDataWidth))) else None
    val opfusionRAreq = if (en_cfg("opfusion")) Some(Flipped(new dmaCtrl_io(dmaSizeWidth, dmaAddrWidth))) else None
    val opfusionRData = if (en_cfg("opfusion")) Some(Flipped(new dmaRData_io(dmaDataWidth))) else None
    //...

    //----------------------write channel----------------------//
    val dmaW = new dmaW_io(dmaAddrWidth,dmaDataWidth)
    val dmaWbusy = Output(Bool())
    val wid = Output(UInt(log2Ceil(id.values.toList.max+1).W))
    val aluWAreq = if(en_cfg("alu")) Some(Flipped(new dmaCtrl_io(dmaSizeWidth, dmaAddrWidth))) else None
    val aluWData = if(en_cfg("alu")) Some(Flipped(new dmaWData_io(dmaDataWidth))) else None
    //...
  })

  //----------------------read channel----------------------//
  val dmaR = Module(new dmaR)
  val dmaRBufSel = Module(new dmaRBufSel)
  val dmaRAreqAbrit = Module(new dmaAreqAbrit)
  io.rid := dmaRAreqAbrit.io.sel
  io.dmaRbusy := dmaR.io.dmaRbusy
  dmaR.io.dma_r <> io.dmaR
  dmaR.io.dmaCtrl <> dmaRAreqAbrit.io.toDma
  dmaRBufSel.io.sel := dmaRAreqAbrit.io.sel
  dmaRBufSel.io.dataIn <> dmaR.io.dataOut

  if(en_cfg("alu")){
    io.aluRData.get <> dmaRBufSel.io.aluData
    dmaRAreqAbrit.io.aluAreq <> io.aluRAreq.get
  }else{
    dmaRAreqAbrit.io.aluAreq <> 0.U.asTypeOf(new dmaCtrl_io(dmaSizeWidth, dmaAddrWidth))
  }
  if(en_cfg("im2col")){
    io.im2colRData.get <> dmaRBufSel.io.im2colData
    dmaRAreqAbrit.io.im2colAreq <> io.im2colRAreq.get
  }else{
    dmaRAreqAbrit.io.im2colAreq <> 0.U.asTypeOf(new dmaCtrl_io(dmaSizeWidth, dmaAddrWidth))
  }
  if (en_cfg("wgtBuf")) {
    io.wgtBufRData.get <> dmaRBufSel.io.wgtBufData
    dmaRAreqAbrit.io.wgtBufAreq <> io.wgtBufRAreq.get
  } else {
    dmaRAreqAbrit.io.wgtBufAreq <> 0.U.asTypeOf(new dmaCtrl_io(dmaSizeWidth, dmaAddrWidth))
  }
  if (en_cfg("opfusion")) {
    io.opfusionRData.get <> dmaRBufSel.io.opfusionData
    dmaRAreqAbrit.io.opfusionAreq <> io.opfusionRAreq.get
  } else {
    dmaRAreqAbrit.io.opfusionAreq <> 0.U.asTypeOf(new dmaCtrl_io(dmaSizeWidth, dmaAddrWidth))
  }
  //...

  //----------------------write channel----------------------//
  val dmaW = Module(new dmaW)
  val dmaWBufSel = Module(new dmaWBufSel)
  val dmaWAreqAbrit = Module(new dmaAreqAbrit)
  io.wid := dmaWAreqAbrit.io.sel
  io.dmaWbusy := dmaW.io.dmaWbusy
  dmaW.io.dma_w <> io.dmaW
  dmaW.io.dmaCtrl <> dmaWAreqAbrit.io.toDma
  dmaWBufSel.io.sel := dmaWAreqAbrit.io.sel
  dmaWBufSel.io.dataOut <> dmaW.io.dataIn

  dmaWAreqAbrit.io.im2colAreq <> 0.U.asTypeOf(new dmaCtrl_io(dmaSizeWidth, dmaAddrWidth))
  dmaWAreqAbrit.io.wgtBufAreq <> 0.U.asTypeOf(new dmaCtrl_io(dmaSizeWidth, dmaAddrWidth))
  dmaWAreqAbrit.io.opfusionAreq <> 0.U.asTypeOf(new dmaCtrl_io(dmaSizeWidth, dmaAddrWidth))

  if (en_cfg("alu")) {
    io.aluWData.get <> dmaWBufSel.io.aluData
    dmaWAreqAbrit.io.aluAreq <> io.aluWAreq.get
  }else{
    dmaWAreqAbrit.io.aluAreq <> 0.U.asTypeOf(new dmaCtrl_io(dmaSizeWidth, dmaAddrWidth))
  }
  //...
}