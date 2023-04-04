BUILD_DIR = ./build
TOPNAME = Mesh
TOPMODULE_GEN = $(TOPNAME)Gen
# scala src dir
SRC_DIR = ./src/main/scala

GEN_DIR = $(BUILD_DIR)/$(TOPNAME)
OBJ_DIR = $(GEN_DIR)/obj_dir
VERILOG_DIR = $(GEN_DIR)/verilog
BIN_VCD = $(GEN_DIR)/$(TOPNAME)

VERILATOR = verilator
VERILATOR_CFLAGS += -MMD --build -cc  \
					-O3 --x-assign fast --x-initial fast --noassert

VSRCS = $(shell find $(abspath $(VERILOG_DIR)) -name  "*.v")
CSRCS_VCD =$(abspath $(shell find $(abspath $(SRC_CODE_DIR)) -name  "$(TOPNAME)_sim.cpp"))

.PHONY: verilog

verilog:
	#echo $(SRC_DIR)
	#echo $(VERILOG_DIR)
	@rm -rf $(VERILOG_DIR)
	mkdir -p $(VERILOG_DIR)
	sbt "test:runMain $(TOPMODULE_GEN)  -td $(VERILOG_DIR)"

sim_vcd: verilog
	mkdir -p $(OBJ_DIR)
	echo $(CSRCS_VCD)
	verilator -MMD -O2 --cc $(VSRCS) --Mdir $(OBJ_DIR) --trace --exe --build $(CSRCS_VCD) -o $(abspath $(BIN_VCD))
	$(BIN_VCD)
	gtkwave $(GEN_DIR)/$(TOPNAME).wave $(GEN_DIR)/$(TOPNAME).sav

sim_vcd_no_regen:
	rm -rf $(OBJ_DIR) $(BIN_VCD)
	mkdir -p $(OBJ_DIR)
	echo $(CSRCS_VCD)
	verilator -MMD -O2 --cc $(VSRCS) --Mdir $(OBJ_DIR) --trace --exe --build $(CSRCS_VCD) -o $(abspath $(BIN_VCD))
	$(BIN_VCD)
	#gtkwave $(GEN_DIR)/$(TOPNAME).wave $(GEN_DIR)/$(TOPNAME).sav

