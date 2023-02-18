package jstereox;

import javafx.scene.image.*;
import javafx.scene.paint.Color;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameConverter;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;

public class BetterFxFrameConverter extends FrameConverter<Image> {

    private static ByteBuffer copy(ByteBuffer bb) {
//        System.out.println("limit: " + bb.limit());
        int capacity = bb.limit();
//        System.out.println("1");
        int pos = bb.position();
//        System.out.println("2");
        final ByteOrder order = bb.order();
//        System.out.println("3");
        final ByteBuffer copy = ByteBuffer.allocate(capacity);
//        System.out.println("4");
        bb.rewind();
//        System.out.println("5");
        copy.order(order);
//        System.out.println("6");
        copy.put(bb);
//        System.out.println("7");
        copy.position(pos);
//        System.out.println("8");
        bb.position(pos);
//        System.out.println("9");
        return copy;
    }

    @Override
    public Frame convert(Image image) {
        throw new UnsupportedOperationException("conversion from Image to Frame not supported");
    }

    @Override
    public Image convert(Frame frame) {
        int iw = frame.imageWidth;
        int ih = frame.imageHeight;
        PixelReader pr = new FramePixelReader(frame);
        return new WritableImage(pr, iw, ih);
    }

    private static class FramePixelReader implements PixelReader, AutoCloseable {
        private final ByteBuffer buffer;
        private final int fss;

        private FramePixelReader(Frame frame) {
            if (frame.imageChannels != 3)
                throw new UnsupportedOperationException("We only support frames with imageChannels = 3 (BGR)");
            this.buffer = copy((ByteBuffer) frame.image[0]);
            this.fss = frame.imageStride;
        }

        @Override
        public void close() {
            this.buffer.clear();
        }

        @Override
        public <T extends Buffer> void getPixels(int x, int y, int w, int h, WritablePixelFormat<T> pixelformat, T buffer, int scanlineStride) {
            if (!(buffer instanceof ByteBuffer))
                throw new UnsupportedOperationException ("We only support bytebuffers at the moment");
            final ByteBuffer bb = (ByteBuffer) buffer;
            for (int i = y; i < y + h; i++) {
                for (int j = x; j < x + w; j++) {
                    int base = 3 * j;
                    bb.put(this.buffer.get(fss * i + base));
                    bb.put(this.buffer.get(fss * i + base + 1));
                    bb.put(this.buffer.get(fss * i + base + 2));
                    bb.put((byte) 255);
                }
            }
        }

        @Override
        public PixelFormat<?> getPixelFormat() {
            throw new UnsupportedOperationException("not supported yet.");
        }

        @Override
        public int getArgb(int x, int y) {
            throw new UnsupportedOperationException("not supported yet.");
        }

        @Override
        public Color getColor(int x, int y) {
            throw new UnsupportedOperationException("not supported yet.");
        }

        @Override
        public void getPixels(int x, int y, int w, int h, WritablePixelFormat<ByteBuffer> pixelformat, byte[] buffer, int offset, int scanlineStride) {
            throw new UnsupportedOperationException("not supported yet.");
        }

        @Override
        public void getPixels(int x, int y, int w, int h, WritablePixelFormat<IntBuffer> pixelformat, int[] buffer, int offset, int scanlineStride) {
            throw new UnsupportedOperationException("not supported yet.");
        }

    }
}
