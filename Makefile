TOPNAME = MeshTop
TOPMODULE_GEN = $(TOPNAME)Gen
# scala src dir
SCALA_SRC_DIR = ./src/main/scala
# C src dir
C_SRC_DIR = ./src/main/csrc
COPY_DIR = ../QinPro/hw_ncnnAccel/src/main/scala

BUILD_DIR = ./build
GEN_DIR = $(BUILD_DIR)/$(TOPNAME)
OBJ_DIR = $(GEN_DIR)/obj_dir
VERILOG_DIR = $(GEN_DIR)/verilog
BIN_VCD = $(GEN_DIR)/$(TOPNAME)

VERILATOR = verilator
VERILATOR_CFLAGS += -MMD --build -cc  \
					-O3 --x-assign fast --x-initial fast --noassert

VSRCS = $(shell find $(abspath $(VERILOG_DIR)) -name  "$(TOPNAME).v")
VSRCS += $(filter-out $(shell find $(abspath $(VERILOG_DIR)) -name  "$(TOPNAME).v"), $(shell find $(abspath $(VERILOG_DIR)) -name  "*.v"))
CSRCS_VCD =$(abspath $(shell find $(abspath $(C_SRC_DIR)) -name  "$(TOPNAME)_sim.cpp"))
#CSRCS_VCD +=$(abspath $(shell find $(abspath $(C_SRC_DIR)) -name  "config.h"))

.PHONY: verilog

# 从config.h中提取mat_size的值
mat_size=$(shell grep -oP '#define ACCEL_mesh_size \d+' ./src/main/csrc/config.h | grep -oP '\d+')
set_para:
    # 更新config.scala文件中的mesh_size值
	sed -i "s/val mesh_size = .*/val mesh_size = $(mat_size)/" ./src/main/scala/configs.scala

verilog: set_para
	@#echo $(SCALA_SRC_DIR)
	@#echo $(VERILOG_DIR)
	@rm -rf $(VERILOG_DIR)
	mkdir -p $(VERILOG_DIR)
	sbt "test:runMain $(TOPMODULE_GEN)  -td $(VERILOG_DIR)"

sim_vcd: clean verilog
	mkdir -p $(OBJ_DIR)
	echo $(CSRCS_VCD)
	@#verilator -MMD --cc $(VSRCS) --Mdir $(OBJ_DIR) --trace-fst --exe --build $(CSRCS_VCD) -o $(abspath $(BIN_VCD)) # --LDFLAGS -fsanitize=address -CFLAGS -fsanitize=address
	verilator -MMD --cc $(VSRCS) --Mdir $(OBJ_DIR) --trace-fst --exe --build $(CSRCS_VCD) -o $(abspath $(BIN_VCD))
	$(BIN_VCD)
	gtkwave $(GEN_DIR)/$(TOPNAME).wave $(GEN_DIR)/$(TOPNAME).sav

sim_vcd_no_regen:
	rm -rf $(OBJ_DIR) $(BIN_VCD)
	mkdir -p $(OBJ_DIR)
	echo $(CSRCS_VCD)
	@#verilator -MMD --cc $(VSRCS) --Mdir $(OBJ_DIR) --trace-fst --exe --build $(CSRCS_VCD) -o $(abspath $(BIN_VCD)) --LDFLAGS -fsanitize=address -CFLAGS -fsanitize=address
	@verilator -MMD --cc $(VSRCS) --Mdir $(OBJ_DIR) --trace-fst --exe --build $(CSRCS_VCD) -o $(abspath $(BIN_VCD))
	$(BIN_VCD) # > /dev/null
	gtkwave $(GEN_DIR)/$(TOPNAME).wave $(GEN_DIR)/$(TOPNAME).sav

copy_from_qinpro:
	cp $(COPY_DIR)/* $(SCALA_SRC_DIR)/

copy_to_qinpro:
	cp $(SCALA_SRC_DIR)/Mesh.scala $(SCALA_SRC_DIR)/PE.scala $(SCALA_SRC_DIR)/AccMem.scala $(SCALA_SRC_DIR)/IfmBuffer.scala $(COPY_DIR)/

clean:
	rm -rf $(OBJ_DIR) $(VERILOG_DIR) $(BIN_VCD)

gtk:
	gtkwave $(GEN_DIR)/$(TOPNAME).wave $(GEN_DIR)/$(TOPNAME).sav




