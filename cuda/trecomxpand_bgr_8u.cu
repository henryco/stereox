extern "C"
__global__ void compress_expand (
  unsigned char* src,
  unsigned char* dst,
  int threshold,
  int src_step,
  int dst_step,
  int width,
  int height
) {
  const int x = blockIdx.x * blockDim.x + threadIdx.x;
  const int y = blockIdx.y * blockDim.y + threadIdx.y;

  if (x > width || y > height)
    return;

  const int src_p = (y * src_step) + (x * 3);
  const int dst_p = (y * dst_step) + (x * 3);
//  /*B*/ dst[dst_p + 0] = src[src_p + 0] * 0.1;
//  /*G*/ dst[dst_p + 1] = src[src_p + 1] * 0.1;
//  /*R*/ dst[dst_p + 2] = src[src_p + 2] * 0.1;

  /*B*/ dst[dst_p + 0] = 255;
  /*G*/ dst[dst_p + 1] = threshold;
  /*R*/ dst[dst_p + 2] = 255;
}