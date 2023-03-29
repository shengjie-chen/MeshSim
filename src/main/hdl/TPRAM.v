module TPRAM
#(
    parameter DATA_WIDTH = 32,
    parameter DEPTH = 1024,
    parameter RAM_STYLE_VAL = "block"
)
(
    input clock,
    input wen,
    input ren,
    input [$clog2(DEPTH)-1:0] waddr,
    input [$clog2(DEPTH)-1:0] raddr,
    input [DATA_WIDTH-1:0] wdata,
    output reg [DATA_WIDTH-1:0] rdata
);

(*ram_style = RAM_STYLE_VAL*) reg [DATA_WIDTH-1:0] mem[DEPTH-1:0];

always @(posedge clock) begin
    if(wen)
        mem[waddr] <= wdata;
end

always @(posedge clock) begin
    if(ren)
        rdata <= mem[raddr];
end


endmodule