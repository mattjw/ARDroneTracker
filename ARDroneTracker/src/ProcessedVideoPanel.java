/*
 * VideoPanel.java
 * 
 * Created on 21.05.2011, 18:42:10
 */



import com.codeminders.ardrone.ARDrone;
import com.codeminders.ardrone.DroneVideoListener;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.JPanel;

/**
 * 
 * @author normenhansen
 */
@SuppressWarnings("serial")
public class ProcessedVideoPanel extends JPanel implements DroneVideoListener
{
    private AtomicReference<BufferedImage> atomImage          = new AtomicReference<BufferedImage>();  // used for output when displaying the video stream. this variable is the frame that'll be displayed 
    private AtomicBoolean                  preserveAspect = new AtomicBoolean(true);  
    private BufferedImage                  noConnection   = new BufferedImage(320, 240, BufferedImage.TYPE_INT_RGB);

    /** Creates new form VideoPanel */
    public ProcessedVideoPanel()
    {
        initComponents();
        Graphics2D g2d = (Graphics2D) noConnection.getGraphics();
        Font f = g2d.getFont().deriveFont(24.0f);
        g2d.setFont(f);
        g2d.drawString("No video connection", 40, 110);
        atomImage.set(noConnection);
        
        // Some default params...
        // But it's better to have these set by the application using this class.
        // See: setParams(...)
        TGT_R = 0.188;
        TGT_G = 0.494;
        TGT_B = 0.360;
        DIST_THR = 0.06;
        CONV_THR = 0.5;
    }

    public void setDrone(ARDrone drone)
    {
        drone.addImageListener(this);
    }

    public void setPreserveAspect(boolean preserve)
    {
        preserveAspect.set(preserve);
    }


    @Override
    public void paintComponent(Graphics g)
    {
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int width = getWidth();
        int height = getHeight();
        drawDroneImage(g2d, width, height);
    }

    private void drawDroneImage(Graphics2D g2d, int width, int height)
    {
        BufferedImage im = atomImage.get();
        if(im == null)
        {
            return;
        }
        int xPos = 0;
        int yPos = 0;
        if(preserveAspect.get())
        {
            g2d.setColor(Color.BLACK);
            g2d.fill3DRect(0, 0, width, height, false);
            float widthUnit = ((float) width / 4.0f);
            float heightAspect = (float) height / widthUnit;
            float heightUnit = ((float) height / 3.0f);
            float widthAspect = (float) width / heightUnit;

            if(widthAspect > 4)
            {
                xPos = (int) (width - (heightUnit * 4)) / 2;
                width = (int) (heightUnit * 4);
            } else if(heightAspect > 3)
            {
                yPos = (int) (height - (widthUnit * 3)) / 2;
                height = (int) (widthUnit * 3);
            }
        }
        if(im != null)
        {
            g2d.drawImage(im, xPos, yPos, width, height, null);
        }
    }

    private void initComponents()
    {
        setLayout(new java.awt.GridLayout(4, 6));
    }
    
    
    
    ////////////////////////////////////////
    //////////// WRAPPED ON KAS ////////////
    ////////////////////////////////////////
	
    public void setParams( float TGT_R, float TGT_G, float TGT_B, float DIST_THR )
    {
    	this.TGT_R = TGT_R;
    	this.TGT_G = TGT_G;
    	this.TGT_B = TGT_B;
    	this.DIST_THR = DIST_THR;
    }
    
    private int frameSkipCount = 0;
    private final int nFrameSkip = 3;  
    // frameSkipCount: cycles through 0, 1, 2, ..., nFrameSkip-1, 0, 1, 2, ...
    //                 frame will not be processed until unless frameSkipCount==0;
    // nFrameSkip: skip `nFrameSkip` frames before re-processing
    
	public boolean isTargetFound()
	{
		return success;
	}
	
	public double getTargetX()
    {
    	return tgt_x; 
    }
	
	public double getTargetY()
    {
    	return tgt_y; 
    }
    
	public double getTargetExtent()
	{
		// Some sort of measure of the size of the target. Unit unknown.
		// Let's call it the target's "extent". 
		return tgt_r;
	}
	
	@Override
    public void frameReceived(int startX, int startY, int w, int h, int[] rgbArray, int offset, int scansize)
    {
		if( frameSkipCount == 0 )
		{
	        BufferedImage im = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);  // create blank frame
	        im.setRGB(startX, startY, w, h, rgbArray, offset, scansize);  // copy pixels across 
	
	        processImage( im );
	        
	        atomImage.set( processedImage );
	        repaint();
		}
        
		frameSkipCount = (frameSkipCount+1) % nFrameSkip;
    }
    
	
	
    ///////////////////////////////////////
    //////////// KAS PROC /////////////////
    ///////////////////////////////////////
    
    private BufferedImage rawImage;
    private String imgpath = "./data";
    private int WIDTH = 320;
    private int HEIGHT = 240;
    
    // colour properties of the target...
    // (Not necessarily red/green/blue values. Actually,
    // RGB ratios.)
    private double TGT_R;
    private double TGT_G;
    private double TGT_B;
    
    // thresholds
    private double DIST_THR;// = 0.10; //.12 //11; // 0.07;   colour distance thresh
    private double CONV_THR;// = 0.5; //0.1   // false pos thresh
    
    // other params
    private int CONV_R = 10;  // convolution circle mask' radius or diam

    // Results
    private double tgt_x;
    private double tgt_y;
    private double tgt_r;  // target radius/diameter/extent
    private BufferedImage processedImage;
    private boolean success;

    // Temporary buffers
    private double[][] buf = new double[HEIGHT][WIDTH];
    private double[][] conv = new double[HEIGHT][WIDTH];
    private int count = 0;

    private void clearBuffer() {
        for (int row = 0; row < HEIGHT; ++row) {
            for (int col = 0; col < WIDTH; ++col) {
                buf[row][col] = 0.0;
            }
        }
    }

    private double sq(double x) {
        return x * x;
    }

    public static BufferedImage getImageFromArray(double[][] pixels, int width, int height) {
        int[] tmp = new int[3 * height * width];
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
                image.setRGB(col, row, 0x00010101 * (int)(255.0 * scale * pixels[row][col]));
            }
        }
        return image;
    }

    private void computeDifference() {
        for (int j = 0; j < HEIGHT; ++j) {
            for (int i = 0; i < WIDTH; ++i) {
                Color col = new Color(rawImage.getRGB(i, j));
                
                double r = col.getRed()   / 255.0;
                double g = col.getGreen() / 255.0;
                double b = col.getBlue()  / 255.0;
                
                double br = (r + g + b) / 3.0;
                r = r/br;
                g = g/br;
                b = b/br;
                double diff = Math.sqrt(sq(r - TGT_R) + sq(g - TGT_G) + sq(b - TGT_B)) / Math.sqrt(3.0);
                
                if (diff < DIST_THR ) {
                    buf[j][i] = 1.0;
                }
                
                // debug
                if( (i==160) && (j==120) )
                {
                	System.out.println( String.format("r  %.3f   g  %.3f    b %.3f  %f", r, g, b, DIST_THR) );
                	System.out.println( String.format("tgtr  %.3f   tgtg  %.3f    tgtb %.3f  %f", TGT_R, TGT_G, TGT_B, DIST_THR) );
                }
                
                // Retain only central cam circle/cone
                // the corners of the camera are especially poor quality, so
                // we discard these corners by retaining only a central cone
                if( Math.sqrt( sq(i-160) + sq(j-120) ) > 160 )   // bigger then 160 px from centre culled
                	buf[j][i] = 0.0;  // 0 means flagged as NOT interesting for detection
            }
        }
    }

    private void convolve() {
        double[][] mask = new double[CONV_R*2+1][CONV_R*2+1];
        int c = 0;
        for (int dj = -CONV_R; dj <= CONV_R; ++dj) {
            for (int di = -CONV_R; di <= CONV_R; ++di) {
                if (Math.sqrt(sq((double)dj) + sq((double)di)) <= CONV_R) {
                    mask[dj + CONV_R][di + CONV_R] = 1.0;
                    ++c;
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
                        sum += mask[dj + CONV_R][di + CONV_R] * buf[jj][ii];
                    }
                }

                conv[j][i] = sum / (double)c;

                if (conv[j][i] < CONV_THR) {
                    conv[j][i] = 0.0;
                }
            }
        }
    }

    private void findTarget() {
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


        tgt_r = Math.max(1, 3 * Math.sqrt(tconv));
        success = (tgt_r > 10);
    }

    private void visualise(BufferedImage image) {

        processedImage = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);

        double maxconv = 0.0;
        for (int j = 0; j < HEIGHT; ++j) {
            for (int i = 0; i < WIDTH; ++i) {
                maxconv = Math.max(maxconv, conv[j][i]);
            }
        }
        for (int j = 0; j < HEIGHT; ++j) {
            for (int i = 0; i < WIDTH; ++i) {
                Color col = new Color(image.getRGB(i, j));
                double r = col.getRed()   / 255.0;
                double g = col.getGreen() / 255.0;
                double b = col.getBlue()  / 255.0;
                double gray = Math.min(1.0, 0.21 * r + 0.71 * g + 0.07 * b);
                double val = conv[j][i] / maxconv;
                int cc = Color.HSBtoRGB((float)val, (float)val, (float)gray);
                processedImage.setRGB(i, j, cc);
                
                if( (i==160) && (j==120) )
                {
                	processedImage.setRGB(i, j, 0xFFFFFF );
                	// for reference purposes, set the center pixel to white
                }
            }
        }

        Graphics2D g = processedImage.createGraphics();
        g.setColor(Color.WHITE);
        g.drawOval((int)Math.floor(tgt_x - tgt_r/4), (int)Math.floor(tgt_y - tgt_r/4), (int)Math.round(tgt_r/2.0), (int)Math.round(tgt_r/2.0));

    }

    private void processImage(BufferedImage image) {
    	rawImage = image;  // MJW
    	
        clearBuffer();
        int height = image.getHeight();
        int width = image.getWidth();
        processedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        count = 0;

        computeDifference();
        convolve();
        findTarget();
        visualise(image);
    }
}
