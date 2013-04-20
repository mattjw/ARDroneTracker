import java.awt.image.BufferedImage;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.imageio.*;
import java.io.*;
import java.util.*;

class DroneTracker {
    private static BufferedImage image;
    private static String imgpath = "./data";
    private static double mnr = 115;
    private static double mng = 49;
    private static double mnb = 75;
    private static double str = 4;
    private static double stg = 7;
    private static double stb = 5;
    private static double slack = 4.0;

    // Results
    private static double tgt_x;
    private static double tgt_y;
    private static BufferedImage processedImage;
    private static boolean success;

    private static int count = 0;

    private static boolean isPixelTarget(int r, int g, int b) {
        // System.out.format("%d %d %d\n", r, g, b);
        if (r < (mnr - str * slack)) return false;
        if (r > (mnr + str * slack)) return false;
        if (g < (mng - stg * slack)) return false;
        if (g > (mng + stg * slack)) return false;
        if (b < (mnb - stb * slack)) return false;
        if (b > (mnb + stb * slack)) return false;
        return true;
    }

    private static void processImage(BufferedImage image) {
        int height = image.getHeight();
        int width = image.getWidth();
        processedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        // double tgt_x = 0.0;
        // double tgt_y = 0.0;
        count = 0;
        for (int j = 0; j < height; ++j) {
            for (int i = 0; i < width; ++i) {
                Color col = new Color(image.getRGB(i, j));
                int r = col.getRed();
                int g = col.getGreen();
                int b = col.getBlue();

                if (isPixelTarget(r, g, b)) {
                    tgt_x += i;
                    tgt_y += j;
                    ++count;
                    processedImage.setRGB(i, j, Color.green.getRGB());
                }
                else {
                    processedImage.setRGB(i, j, col.getRGB());
                    // processedImage.setRGB(i, j, col.getRGB());
                }

            }
        }

        if (count > 0) {
            tgt_x /= (double)count;
            tgt_y /= (double)count;
            success = true;
        } else {
            success = false;
        }



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
                System.out.format("%f %f (%d)\n", tgt_x, tgt_y, count);

                // Draw centre
                if (success) {
                    processedImage.setRGB((int)tgt_x, (int)tgt_y, Color.yellow.getRGB());
                }
                ImageIO.write(processedImage, "png", new File(imgpath + "/_" + fn));

            }
        }
    }
}
