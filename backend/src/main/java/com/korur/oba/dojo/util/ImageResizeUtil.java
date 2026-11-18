package com.korur.oba.dojo.util;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Iterator;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

// 4. Internal

/**
 * OOM-safe helpers for reading large image bytes without materialising the full-resolution
 * BufferedImage in memory. Used by the image preprocessor's PDF path: PDFs frequently embed
 * 20+ megapixel images that would blow the heap if decoded at full size before downscaling.
 *
 * Subsampled reading produces an image that is already close to the target dimension —
 * a 20MP image with maxDimension=2048 uses roughly 16MB instead of ~80MB.
 */
public final class ImageResizeUtil {

    private ImageResizeUtil() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Reads an image using ImageIO with subsampling so the returned BufferedImage is at
     * reduced resolution. Suitable for any format ImageIO recognises (JPEG, PNG, BMP, etc.).
     *
     * @param imageBytes   the encoded image bytes
     * @param maxDimension the target maximum width/height; subsample factor is derived from this
     * @return a BufferedImage at reduced resolution, or null if the format is unsupported or decoding fails
     */
    public static BufferedImage readImageSubsampled(byte[] imageBytes, int maxDimension) {
        try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(imageBytes))) {
            if (iis == null) {
                return null;
            }

            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                return null;
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(iis);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);

                int subsample = calculateSubsampleFactor(width, height, maxDimension);

                ImageReadParam param = reader.getDefaultReadParam();
                param.setSourceSubsampling(subsample, subsample, 0, 0);

                return reader.read(0, param);
            } finally {
                reader.dispose();
            }
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Calculates the integer subsample factor needed to fit an image within maxDimension.
     * Returns 1 when the image already fits.
     *
     * @param width        source image width in pixels
     * @param height       source image height in pixels
     * @param maxDimension target maximum width or height
     * @return the smallest integer factor so that width/factor and height/factor both fit
     */
    public static int calculateSubsampleFactor(int width, int height, int maxDimension) {
        if (width <= maxDimension && height <= maxDimension) {
            return 1;
        }
        double ratio = Math.max((double) width / maxDimension, (double) height / maxDimension);
        return (int) Math.ceil(ratio);
    }
}
