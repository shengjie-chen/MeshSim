#include <stdio.h>

#define MAT_SIZE 3
#define def_io_w_bits(a) int io_w_bits_##a = 0;
#define assgin_0() 
#define name(a, b) a##b

#if (MAT_SIZE == 2)
#define LOOP(f) f(0) f(1)
#elif (MAT_SIZE == 3)
#define LOOP(f) f(0) f(1) f(2)
#endif

int main() {

  // LOOP(def_val(int io_ifm_bits_, = 0;))

//   int name(io_w_bits_, 0) = 0;

LOOP(def_io_w_bits)


//   def_val(name(bits, 1))

      // int name(bits,1) = 0;
      // int name(bits,1) = 0;

      // printf("name(bits,1):%d\n",name(bits,1));
      return 0;
}