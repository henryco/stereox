extern "C"
__global__ void vector_add(int n, int *a, int *b, int *c) {
  int index = blockIdx.x * blockDim.x + threadIdx.x;
  int stride = blockDim.x * gridDim.x;
  for( int i = index; i < n; i+= stride )
  c[i] = a[i] + b[i];
}