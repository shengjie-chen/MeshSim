module SPRAM
#(
    parameter DATA_WIDTH = 32,
    parameter DEPTH = 1024,
    parameter RAM_STYLE_VAL = "block"
)
(
    input clock,
    input en,
    input wr,
    input [$clog2(DEPTH)-1:0] addr,
    input [DATA_WIDTH-1:0] wdata,
    output reg [DATA_WIDTH-1:0] rdata
);

(*ram_style = RAM_STYLE_VAL*) reg [DATA_WIDTH-1:0] mem[DEPTH-1:0];

always @(posedge clock) begin
    if(en && !wr)
        mem[addr] <= wdata;
end

always @(posedge clock) begin
    if(en && wr) 
        rdata <= mem[addr];
    else
        rdata <= 0;
end

endmodule