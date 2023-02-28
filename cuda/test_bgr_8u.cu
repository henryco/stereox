extern "C"
__global__ void test (
  unsigned char* dst,
  int dst_step,
  int width,
  int height
) {
  const int x = blockIdx.x * blockDim.x + threadIdx.x;
  const int y = blockIdx.y * blockDim.y + threadIdx.y;
  if (x > width || y > height)
    return;

  const int dst_p = (y * dst_step) + (x * 3);
  /*B*/ dst[dst_p + 0] = 255;
  /*G*/ dst[dst_p + 1] = 0;
  /*R*/ dst[dst_p + 2] = 0;

//  const int dst_p = x + y * (width * height);
//  dst[dst_p] = 200;
}