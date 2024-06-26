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
	sed -i "s/#define TOPNAME .*/#define TOPNAME V$(TOPNAME)/g" ./src/main/csrc/config.h
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

sim_vcd_no_regen: set_para
	rm -rf $(OBJ_DIR) $(BIN_VCD)
	mkdir -p $(OBJ_DIR)
	echo $(CSRCS_VCD)
	@#verilator -MMD --cc $(VSRCS) --Mdir $(OBJ_DIR) --trace-fst --exe --build $(CSRCS_VCD) -o $(abspath $(BIN_VCD)) --LDFLAGS -fsanitize=address -CFLAGS -fsanitize=address
	@verilator -MMD --cc $(VSRCS) --Mdir $(OBJ_DIR) --trace-fst --exe --build $(CSRCS_VCD) -o $(abspath $(BIN_VCD))
	$(BIN_VCD) # > /dev/null
	gtkwave $(GEN_DIR)/$(TOPNAME).wave $(GEN_DIR)/$(TOPNAME).sav

sim_vcd_no_regen_gtk: set_para
	rm -rf $(OBJ_DIR) $(BIN_VCD)
	mkdir -p $(OBJ_DIR)
	echo $(CSRCS_VCD)
	@#verilator -MMD --cc $(VSRCS) --Mdir $(OBJ_DIR) --trace-fst --exe --build $(CSRCS_VCD) -o $(abspath $(BIN_VCD)) --LDFLAGS -fsanitize=address -CFLAGS -fsanitize=address
	@verilator -MMD --cc $(VSRCS) --Mdir $(OBJ_DIR) --trace-fst --exe --build $(CSRCS_VCD) -o $(abspath $(BIN_VCD))
	$(BIN_VCD) # > /dev/null

copy_from_qinpro:
	cp $(COPY_DIR)/* $(SCALA_SRC_DIR)/

copy_to_qinpro:
	cp $(SCALA_SRC_DIR)/Mesh.scala $(SCALA_SRC_DIR)/PE.scala $(SCALA_SRC_DIR)/AccMem.scala $(SCALA_SRC_DIR)/IfmBuffer.scala $(COPY_DIR)/

clean:
	rm -rf $(OBJ_DIR) $(VERILOG_DIR) $(BIN_VCD)

gtk:
	gtkwave $(GEN_DIR)/$(TOPNAME).wave $(GEN_DIR)/$(TOPNAME).sav

random_test:
	@sed -i 's/^#define DEBUG_MODE/\/\/ #define DEBUG_MODE/' ./src/main/csrc/config.h
	@sed -i 's/^#define EXPORT_VCD/\/\/ #define EXPORT_VCD/' ./src/main/csrc/config.h
	@for i in {1..10}; do \
		ACCEL_ifm_w=$$(($$RANDOM % $$(($(mat_size)*5)) + 1)); \
		ACCEL_ifm_h=$$(($$RANDOM % $$(($(mat_size)*5)) + 1)); \
		ACCEL_ifm_c=$$(($$RANDOM % 10 + 1)); \
		ACCEL_ofm_c=$$(($$RANDOM % 10 + 1)); \
		echo "ACCEL_ifm_w -> $$ACCEL_ifm_w"; \
		echo "ACCEL_ifm_h -> $$ACCEL_ifm_h"; \
		echo "ACCEL_ifm_c/$(mat_size) -> $$ACCEL_ifm_c"; \
		echo "ACCEL_ofm_c/$(mat_size) -> $$ACCEL_ofm_c"; \
		sed -i "s/^#define ACCEL_ifm_w .*/#define ACCEL_ifm_w $$ACCEL_ifm_w/" ./src/main/csrc/config.h; \
		sed -i "s/^#define ACCEL_ifm_h .*/#define ACCEL_ifm_h $$ACCEL_ifm_h/" ./src/main/csrc/config.h; \
		sed -i "s/^#define ACCEL_ifm_c .*/#define ACCEL_ifm_c (ACCEL_mesh_size * $$ACCEL_ifm_c)/" ./src/main/csrc/config.h; \
		sed -i "s/^#define ACCEL_ofm_c .*/#define ACCEL_ofm_c (ACCEL_mesh_size * $$ACCEL_ofm_c)/" ./src/main/csrc/config.h; \
		make sim_vcd_no_regen_gtk; \
		if [ $$? -eq 1 ]; then \
        	exit 1; \
        fi; \
	done; \

gen_random:
	ACCEL_ifm_w=$$(($$RANDOM % $$(($(mat_size)*2)) + 1)); \
    ACCEL_ifm_h=$$(($$RANDOM % $$(($(mat_size)*2)) + 1)); \
    ACCEL_ifm_c=$$(($$RANDOM % 5 + 1)); \
    ACCEL_ofm_c=$$(($$RANDOM % 5 + 1)); \
    echo "ACCEL_ifm_w -> $$ACCEL_ifm_w"; \
    echo "ACCEL_ifm_h -> $$ACCEL_ifm_h"; \
    echo "ACCEL_ifm_c/$(mat_size) -> $$ACCEL_ifm_c"; \
    echo "ACCEL_ofm_c/$(mat_size) -> $$ACCEL_ofm_c"; \
    sed -i "s/^#define ACCEL_ifm_w .*/#define ACCEL_ifm_w $$ACCEL_ifm_w/" ./src/main/csrc/config.h; \
    sed -i "s/^#define ACCEL_ifm_h .*/#define ACCEL_ifm_h $$ACCEL_ifm_h/" ./src/main/csrc/config.h; \
    sed -i "s/^#define ACCEL_ifm_c .*/#define ACCEL_ifm_c (ACCEL_mesh_size * $$ACCEL_ifm_c)/" ./src/main/csrc/config.h; \
    sed -i "s/^#define ACCEL_ofm_c .*/#define ACCEL_ofm_c (ACCEL_mesh_size * $$ACCEL_ofm_c)/" ./src/main/csrc/config.h; \




