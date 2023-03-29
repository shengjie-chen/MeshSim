module DPRAM
#(
    parameter DATA_WIDTH = 32,
    parameter DEPTH = 1024,
    parameter RAM_STYLE_VAL = "block"
)
(
    input clock,
    input wr_a,
    input wr_b,
    input en_a,
    input en_b,
    input [$clog2(DEPTH)-1:0] addr_a,
    input [$clog2(DEPTH)-1:0] addr_b,
    input [DATA_WIDTH-1:0] wdata_a,
    input [DATA_WIDTH-1:0] wdata_b,
    output reg [DATA_WIDTH-1:0] rdata_a,
    output reg [DATA_WIDTH-1:0] rdata_b
);

(*ram_style = RAM_STYLE_VAL*) reg [DATA_WIDTH-1:0] mem[DEPTH-1:0];

always @(posedge clock) begin
    if(en_a && !wr_a)
        mem[addr_a] <= wdata_a;
end

always @(posedge clock) begin
    if(en_b && !wr_b)
        mem[addr_b] <= wdata_b;
end

always @(posedge clock) begin
    if(en_a && wr_a)
        rdata_a <= mem[addr_a];
    else
        rdata_a <= 0;
end

always @(posedge clock) begin
    if(en_b && wr_b)
        rdata_b <= mem[addr_b];
    else
        rdata_b <= 0;
end

endmodule