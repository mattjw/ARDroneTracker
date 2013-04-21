import java.awt.image.BufferedImage;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.imageio.*;
import java.io.*;
import java.util.*;
import java.math.*;
import java.awt.image.WritableRaster;


class DroneTracker {
    private static BufferedImage image;
    private static String imgpath = "./data";
    private static int WIDTH = 320;
    private static int HEIGHT = 240;
    private static double TGT_R = 115.0 / 255.0;
    private static double TGT_G = 49.0 / 255.0;
    private static double TGT_B = 75.0 / 255.0;
    private static double DIST_THR = 0.07;
    private static double CONV_THR = 0.1;
    private static int CONV_R = 10;

    // Results
    private static double tgt_x;
    private static double tgt_y;
    private static double tgt_r;
    private static BufferedImage processedImage;
    private static boolean success;
    //

    private static double[][] buf = new double[HEIGHT][WIDTH];
    private static double[][] conv = new double[HEIGHT][WIDTH];
    private static int count = 0;

    private static void clearBuffer() {
        for (int row = 0; row < HEIGHT; ++row) {
            for (int col = 0; col < WIDTH; ++col) {
                buf[row][col] = 0.0;
            }
        }

    }

    private static double sq(double x) {
        return x * x;
    }

    public static BufferedImage getImageFromArray(double[][] pixels, int width, int height) {
        int[] tmp = new int[3 * height * width];
        // int count = 0;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);

        double max = 0.0;
        for (int row = 0; row < height; ++row) {
            for (int col = 0; col < width; ++col) {
                max = Math.max(max, pixels[row][col]);
            }
        }
        double scale = 1.0 / max;
        for (int row = 0; row < height; ++row) {
            for (int col = 0; col < width; ++col) {
                // (int)(255.0 * pixels[row][col])
                image.setRGB(col, row, 0x00010101 * (int)(255.0 * scale * pixels[row][col]));
                // tmp[count+0] = (int)(255.0 * pixels[row][col]);
                // tmp[count+1] = (int)(255.0 * pixels[row][col]);
                // tmp[count+2] = (int)(255.0 * pixels[row][col]);
                // count += 3;
            }
        }

        // image.setRGB(0, 0, width, height, tmp, 0, width);

        // BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        // WritableRaster raster = (WritableRaster) image.getData();
        // raster.setPixels(0,0,width,height,pixels);
        return image;
    }

    private static void computeDifference() {
        for (int j = 0; j < HEIGHT; ++j) {
            for (int i = 0; i < WIDTH; ++i) {
                Color col = new Color(image.getRGB(i, j));
                double r = col.getRed()   / 255.0;
                double g = col.getGreen() / 255.0;
                double b = col.getBlue()  / 255.0;
                double diff = Math.sqrt(sq(r - TGT_R) + sq(g - TGT_G) + sq(b - TGT_B)) / Math.sqrt(3.0);
                if (diff < DIST_THR) {
                    buf[j][i] = 1.0;
                }
            }
        }
    }

    private static void convolve() {
        double[][] mask = new double[CONV_R*2+1][CONV_R*2+1];
                int count = 0;
        for (int dj = -CONV_R; dj <= CONV_R; ++dj) {
            for (int di = -CONV_R; di <= CONV_R; ++di) {
                if (Math.sqrt(sq((double)dj) + sq((double)di)) <= CONV_R) {
                    mask[dj + CONV_R][di + CONV_R] = 1.0;
					++count;
                }
            }
        }


        for (int j = 0; j < HEIGHT; ++j) {
            for (int i = 0; i < WIDTH; ++i) {
                double sum = 0.0;
                for (int dj = -CONV_R; dj <= CONV_R; ++dj) {
                    for (int di = -CONV_R; di <= CONV_R; ++di) {
                        int ii = Math.max(0, Math.min(WIDTH - 1, i + di));
                        int jj = Math.max(0, Math.min(HEIGHT - 1, j + dj));
                        // if (Math.sqrt(sq((double)dj) + sq((double)di)) <= CONV_R) {
                            sum += mask[dj + CONV_R][di + CONV_R] * buf[jj][ii];
                            // ++count;
                        // }
                    }
                }
                conv[j][i] = sum / (double)count;
                if (conv[j][i] < CONV_THR) {
                    conv[j][i] = 0.0;
                }
            }
        }
    }

    private static void findTarget() {
        // count = 0;
        tgt_x = 0.0;
        tgt_y = 0.0;
        double tconv = 0.0;
        for (int j = 0; j < HEIGHT; ++j) {
            for (int i = 0; i < WIDTH; ++i) {
                tgt_x += i * conv[j][i];
                tgt_y += j * conv[j][i];
                tconv += conv[j][i];
            }
        }
        tgt_x /= tconv;
        tgt_y /= tconv;


        tgt_r = Math.max(1, 4 * Math.sqrt(tconv));
        success = (tgt_r < 4);
    }

    private static void processImage(BufferedImage image) {
        clearBuffer();
        int height = image.getHeight();
        int width = image.getWidth();
        processedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        count = 0;

        computeDifference();
        convolve();
        findTarget();

        processedImage = getImageFromArray(conv, WIDTH, HEIGHT);





    }

    public static void main(String[] args) throws java.io.IOException
    {
        String fn;
        File folder = new File(imgpath);
        File[] listOfFiles = folder.listFiles();
        Arrays.sort(listOfFiles);

        for (int i = 0; i < listOfFiles.length; i++) {
            if (listOfFiles[i].isFile()) {
                fn = listOfFiles[i].getName();
                image = ImageIO.read(new File(imgpath + "/" + fn));
                processImage(image);
                System.out.format("%f %f %f\n", tgt_x, tgt_y, tgt_r);

                // Draw centre
                // if (success) {
                //     processedImage.setRGB((int)tgt_x, (int)tgt_y, Color.yellow.getRGB());
                // }
                ImageIO.write(processedImage, "png", new File("out" + "/" + fn));

            }
        }
    }
}
