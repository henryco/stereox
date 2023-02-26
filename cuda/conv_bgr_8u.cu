extern "C"
__global__ void conv (
  unsigned char* src,
  unsigned char* dst,
  int src_step,
  int dst_step,
  int width,
  int height
) {
  const int col = blockIdx.x * blockDim.x + threadIdx.x;
  const int row = blockIdx.y * blockDim.y + threadIdx.y;

  const int src_p = (row * src_step) + (col * 3);
  const int dst_p = (row * dst_step) + (col * 3);

  /*B*/ dst[dst_p + 0] = src[src_p + 0] * 0.5;
  /*G*/ dst[dst_p + 1] = src[src_p + 1] * 0.5;
  /*R*/ dst[dst_p + 2] = src[src_p + 2] * 0.5;
}