/*
 * Adapted from ControlTower.java.
 * URL: https://code.google.com/p/javadrone/
 */

import java.awt.Color;
import java.awt.GridLayout;
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.AbstractAction;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import com.codeminders.ardrone.ARDrone;
import com.codeminders.ardrone.ARDrone.VideoChannel;
import com.codeminders.ardrone.DroneStatusChangeListener;
import com.codeminders.ardrone.NavData;
import com.codeminders.ardrone.NavDataListener;

import java.awt.event.ActionEvent;

/**
 * The central class that represents the main window and also manages the
 * drone update loop.
 *
 * @author normenhansen
 */
@SuppressWarnings("serial")
public class ARTracker extends javax.swing.JFrame implements DroneStatusChangeListener, NavDataListener
{	
	
	// Icon assets
    private final ImageIcon droneOn = new ImageIcon(
        getClass().getResource("/com/codeminders/controltower/images/drone_on.gif"));
    private final ImageIcon droneOff = new ImageIcon(
        getClass().getResource("/com/codeminders/controltower/images/drone_off.gif"));
    
    // Consts
    private static final long READ_UPDATE_DELAY_MS = 5L;
    private static final long CONNECT_TIMEOUT = 8000L;
    
    // Status atoms
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean flying = new AtomicBoolean(false);
    
    // Drone control
    private DroneAction nextAction = DroneAction.NONE;
    
    // ARDrone stuff
    private ARDrone drone;
    private final VideoPanel droneVideoStreamPanel = new VideoPanel();
    private final ProcessedVideoPanel processedVideoStreamPanel = new ProcessedVideoPanel();
    private final DroneConfig droneConfigWindow;
    
    /**
     * Creates new form ControlTower
     */
    public ARTracker()
    {
    	BasicConfigurator.configure(); //~mjw
    	
        setAlwaysOnTop(false);
        initComponents();
        droneConfigWindow = new DroneConfig(this, true);
        //initController(); //MJW
        initDrone();
        setSize( 800, 800 );
    }
    
    public ARDrone getARDrone() 
    {
    	return drone;
    }

    public void setNextAction( DroneAction nextAction )
    {
    	this.nextAction = nextAction;
    }
    
    private void initDrone()
    {
        try
        {
            drone = new ARDrone();
        }
        catch(UnknownHostException ex)
        {
            Logger.getLogger(ARTracker.class.getName()).error("Error creating drone object!", ex);
            System.err.println( "x1" + ex );
            return;
        }
        droneConfigWindow.setDrone(drone);
        droneVideoStreamPanel.setDrone(drone);
        processedVideoStreamPanel.setDrone(drone);
        drone.addStatusChangeListener(this);
        drone.addNavDataListener(this);
    }


    private void updateLoop()
    {
        if(running.get())
        {
            return;
        }
        running.set(true);
        resetStatus();
        try
        {
        	
        	// BEGIN: drone status change listener
            drone.addStatusChangeListener(new DroneStatusChangeListener()
            {

                @Override
                public void ready()
                {
                    try
                    {
                        Logger.getLogger(getClass().getName()).debug("updateLoop::ready() --> 'Configure'");
                        droneConfigWindow.updateDrone();
                        drone.selectVideoChannel(VideoChannel.HORIZONTAL_ONLY);
                        drone.setCombinedYawMode(true);
                        drone.trim();
                    }
                    catch(IOException e)
                    {
                        drone.changeToErrorState(e);
                        System.err.println( "x2" + e );
                    }
                }
            });
            // END: drone status change listener
            
            System.out.println("Connecting to the drone");
            drone.connect();
            drone.waitForReady(CONNECT_TIMEOUT);
            drone.clearEmergencySignal();
            System.out.println("Connected to the drone");
            
            
            /* --PID controllers not being used--
            //
            // Prep PID controllers
            double dt = 0.001; // some measure of time between two frames
            
            double angspdKp = 1.0 / 160.0;  // up to 160 pixels left, up to 160 
            double angspdKi = 0.0005 * dt;  // keep this small; close to zero 
            double angspdKd = 0; // 0.1 / dt;
            pidAngularSpeed = new PIDController( angspdKp, angspdKi, angspdKd,   1.0    );
            
            double frobaKp = 1.0 / 160.0;  // up to 160 pixels left, up to 160 
            double frobaKi = 0.000000001 * dt;  // keep this small; close to zero 
            double frobaKd = 0; // 0.1 / dt;
            pidFrontBackTilt = new PIDController( frobaKp, frobaKi, frobaKd,   0.12    );
            */
            
            
            // MAIN EVENT LOOP
            try 
            {
            	while(running.get())  // "running" indicates ARDrone is running. while "running" the drone may move from "flying" or not 
                {
            		//
            		// Update params
            		try
            		{
	            		float r = Float.valueOf( jtR.getText() );
	            		float g = Float.valueOf( jtG.getText() );
	            		float b = Float.valueOf( jtB.getText() );
	            		float distThresh = Float.valueOf( jtDistThresh.getText() );
	            		
	            		processedVideoStreamPanel.setParams( r, g, b, distThresh );
            		}
            		catch( NumberFormatException ex )
            		{
            			System.err.println( ex );
            			// number format exception while someone is entering values
            		}
            		
            		//
            		// Update control
            		// First thing to do: handle control params
            		// Note: if there are any PIDs here, this WILL cause them to update their states
            		float left_right_tilt = 0f;
                    float front_back_tilt = 0f;
                    float vertical_speed = 0f;
                    float angular_speed = 0f;
                    
                    if( processedVideoStreamPanel.isTargetFound() )
                    {
                    	// motion control
                    	left_right_tilt = 0f;
                        front_back_tilt = getFrontBackTilt();
                        vertical_speed = getVerticalSpeed();
                        angular_speed = getAngularSpeed();
                        
                        // LED control -- target found, so make green
                        drone.playLED(ARDrone.LED.GREEN, 100000, 10);
                    }
                    else
                    {
                    	// target not found -- make LEDs red
                    	drone.playLED(ARDrone.LED.RED, 100000, 10);
                    }
                    
                    //
                    // Print out debug info
            		if( debugLabel != null )
            		{
            			String msg = "";
            			msg += String.format( "angular speed:  %.3f \t\t", angular_speed );
            			msg += String.format( "vertical speed: %.3f \t\t", vertical_speed );
            			msg += String.format( "f/b tilt:       %.3f \n", front_back_tilt );
            			msg += String.format( "target:         %.3f, %.3f \t", processedVideoStreamPanel.getTargetX(), processedVideoStreamPanel.getTargetY() );
            			msg += String.format( "targ extent:    %.3f \n", processedVideoStreamPanel.getTargetExtent() );
            			msg += String.format( "targ is found:  %s", processedVideoStreamPanel.isTargetFound() );
            			debugLabel.setText( msg );
            		}
            		
            		//
            		// Process land/takeoff actions
            		try
            		{
	            		if( nextAction == DroneAction.LAND) {
	            			if( flying.get() )
	            			{
	            				drone.land();
	            				nextAction = DroneAction.NONE;
	            			}
	            			else
	            			{
	            				Logger.getLogger(getClass().getName()).error("will not land because already flying");
	            			}
	            		}
	            		
	            		if( nextAction == DroneAction.TAKEOFF ) {
	            			if( !flying.get() )
	            			{
	            				drone.takeOff();
	            				nextAction = DroneAction.NONE;
	            			}
	            			else
	            			{
	            				Logger.getLogger(getClass().getName()).error("will not take off because already flying");
	            			}
	            		}
            		}
	            	catch( IOException ex )
	            	{
	            		System.err.println( "Problem with drone.takeOff or drone.land" );
	            		System.err.println( "x3" + ex );
	            	}
            		
            		//
            		// Do some stuff while flying (e.g., re-orientation/movement stuff)
            		if( flying.get() )
            		{	
            			if( !processedVideoStreamPanel.isTargetFound() )
            			{
            				// if unsure of target's location, don't do anything
            				drone.hover();
            			}
            			else
            			{
	                        if(left_right_tilt != 0 || front_back_tilt != 0 || vertical_speed != 0 || angular_speed != 0)
	                        {
	                        	// if any movement parameters non-zero, then do movement
	                        	drone.move(left_right_tilt, front_back_tilt, vertical_speed, angular_speed);
	                        }
	            			else
	            			{
	            				drone.hover();
	            			}
            			}
            		}
            		
            		//
            		// End of event loop -- sleep for a bit
            		try
                    {
                        Thread.sleep(READ_UPDATE_DELAY_MS);
                    }
                    catch(InterruptedException e) {
                    	System.err.println( "x4" + e );
                    }
                
                } // end whole loop
            }
            finally
            {
                drone.disconnect();
            }
        }
        catch(Throwable e)
        {
        	System.err.println( "y1" + e );
            e.printStackTrace();
        }
        resetStatus();
        running.set(false);
    }

    private void startUpdateLoop()
    {
        Thread thread = new Thread(new Runnable()
        {

            @Override
            public void run()
            {
                updateLoop();
            }
        });
        thread.setName("ARDrone Control Loop");
        thread.start();
    }

    /**
     * Updates the drone status in the UI, queues command to AWT event dispatch thread
     *
     * @param available
     */
    private void updateDroneStatus(final boolean available)
    {
        java.awt.EventQueue.invokeLater(new Runnable()
        {

            @Override
            public void run()
            {
                if(!available)
                {
                    droneStatus.setForeground(Color.RED);
                    droneStatus.setIcon(droneOff);
                }
                else
                {
                    droneStatus.setForeground(Color.GREEN);
                    droneStatus.setIcon(droneOn);
                }
            }
        });

    }

    /**
     * Updates the battery status in the UI, queues command to AWT event dispatch thread
     *
     * @param value
     */
    private void updateBatteryStatus(final int value)
    {
        java.awt.EventQueue.invokeLater(new Runnable()
        {

            @Override
            public void run()
            {
                batteryStatus.setText(value + "%");
                if(value < 15)
                {
                    batteryStatus.setForeground(Color.RED);
                }
                else if(value < 50)
                {
                    batteryStatus.setForeground(Color.ORANGE);
                }
                else
                {
                    batteryStatus.setForeground(Color.GREEN);
                }
            }
        });
    }

    /**
     * Resets the UI, queues command to AWT event dispatch thread
     */
    private void resetStatus()
    {
        java.awt.EventQueue.invokeLater(new Runnable()
        {

            @Override
            public void run()
            {
                droneStatus.setForeground(Color.RED);
                droneStatus.setIcon(droneOff);
                batteryStatus.setForeground(Color.RED);
                batteryStatus.setText("0%");
            }
        });

    }

    @Override
    public void ready()
    {
        Logger.getLogger(getClass().getName()).debug("ready()");
        updateDroneStatus(true);
    }

    @Override
    public void navDataReceived(NavData nd)
    {
        Logger.getLogger(getClass().getName()).debug("navDataReceived()");
        updateBatteryStatus(nd.getBattery());
        this.flying.set(nd.isFlying());
    }
    
    // This method likely redundant. Required for DroneConfig compatibility (which may
    // also be redundant).
    public void setControlThreshold(float sens)
    {
        //CONTROL_THRESHOLD = sens;
    }
    
    //
    // MAIN
    //
    
    /**
     * @param args the command line arguments
     */
    public static void main(String args[])
    {
    	Logger.getRootLogger().setLevel(Level.FATAL);
    	//TRACE, DEBUG, INFO, WARN, ERROR, FATAL
    	
    	
        final ARTracker tower = new ARTracker();
        java.awt.EventQueue.invokeLater(new Runnable()
        {

            @Override
            public void run()
            {
                tower.setLocationRelativeTo(null);
                tower.setVisible(true);
            }
        });
        tower.startUpdateLoop();
    }
    
    
    ///
    /// GUI SET UP
    ///
    
    private javax.swing.JLabel batteryStatus;
    private javax.swing.JLabel droneStatus;
    private javax.swing.JPanel videoPanel;
    private javax.swing.JTextArea debugLabel;
    
    private javax.swing.JTextField jtR, jtG, jtB, jtDistThresh;
    
    private void initComponents() {
    	
    	//
    	// Components prep
        droneStatus = new javax.swing.JLabel();
        batteryStatus = new javax.swing.JLabel();
        //flipSticksCheckbox = new javax.swing.JCheckBox();
        //mappingButton = new javax.swing.JButton();
        //instrumentButton = new javax.swing.JButton();
        videoPanel = new javax.swing.JPanel();
        debugLabel = new javax.swing.JTextArea();
        
        // 
        // Frame flavour
        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setTitle("ARDrone Target Tracker");
        
        //
        // Layout...
        setLayout( new java.awt.BorderLayout() );
        
        javax.swing.JPanel northPanel = new javax.swing.JPanel();  // panel above videos for debug info etc.
        northPanel.setLayout( new java.awt.GridLayout(2,1) );  // 2x1. first = input. second = debug.
        
        javax.swing.JPanel videosSuperPanel = new javax.swing.JPanel();  // container for multiple video streams
        videosSuperPanel.setLayout( new java.awt.GridLayout(1,0) );
        
        javax.swing.JPanel guiControlsPanel = new javax.swing.JPanel();  // panel below the videos for control/interaction w/ drone
        
        this.add(northPanel, java.awt.BorderLayout.NORTH);
        this.add(videosSuperPanel, java.awt.BorderLayout.CENTER);
        this.add(guiControlsPanel, java.awt.BorderLayout.SOUTH);
        
        //
        // North panel area
        JPanel inputFieldsPanel = new JPanel();
        inputFieldsPanel.setLayout( new GridLayout(1,0) );
        jtR = new JTextField("0.400");
        jtG = new JTextField("1.85");
        jtB = new JTextField("0.7");
        jtDistThresh = new JTextField("0.48");
        
        inputFieldsPanel.add( new JLabel("R", JLabel.RIGHT) ); inputFieldsPanel.add( jtR );
        inputFieldsPanel.add( new JLabel("G", JLabel.RIGHT) ); inputFieldsPanel.add( jtG );
        inputFieldsPanel.add( new JLabel("B", JLabel.RIGHT) ); inputFieldsPanel.add( jtB );
        inputFieldsPanel.add( new JLabel("DistThresh", JLabel.RIGHT) ); inputFieldsPanel.add( jtDistThresh );
        
        northPanel.add( inputFieldsPanel );
        
        debugLabel = new javax.swing.JTextArea("");
        debugLabel.setEditable( false );
        debugLabel.setLineWrap( true );
        northPanel.add(debugLabel);
        
        // 
        // Video streams
        
        // Panel (JPanel videoPanel) and video component (VideoPanel video) 
        videoPanel.add(droneVideoStreamPanel);
        videoPanel.setBackground(new java.awt.Color(102, 102, 102));
        videoPanel.setPreferredSize(new java.awt.Dimension(320, 240));
        videoPanel.setLayout(new javax.swing.BoxLayout(videoPanel, javax.swing.BoxLayout.LINE_AXIS));
        videosSuperPanel.add(videoPanel);
        
        // Processed video panel
        videosSuperPanel.add(processedVideoStreamPanel);
        
        //
        // Control area
        guiControlsPanel.add(droneStatus);
        guiControlsPanel.add(batteryStatus);
        
        JButton landButton = new JButton( new LandAction(this) );
        guiControlsPanel.add( landButton );
        
        JButton takeoffButton = new JButton( new TakeOffAction(this) );
        guiControlsPanel.add( takeoffButton );
                
        pack();
    }

    
    //
    // DRONE CONTROL
    //
    private PIDController pidAngularSpeed;  // currently not used
    private PIDController pidFrontBackTilt;  // currently not used
    
    /*
     * Control value for up/down motion.
     */
    private float getVerticalSpeed()
    {
    	double MAX = 240;
    	double MIDDLE = MAX/2;
    	
    	double targetY = processedVideoStreamPanel.getTargetY();
    	double delta = targetY - MIDDLE;
    	
    	float speed = (float)(delta / MIDDLE);
    	speed = speed*1.0f;
    	
    	float MAX_SPEED = 0.3f;
    	if( speed > MAX_SPEED )
    		speed = MAX_SPEED;
    	
    	if( speed < -MAX_SPEED )
    		speed = -MAX_SPEED;
    	
    	// positive vertical speed = rise
    	// negative vertical speed = descend
    	
    	speed = -speed;  // flip 
    	return speed;
    }
    
    
    /*
     * Control value for left/right motion.
     */
    private float getAngularSpeed()
    {
    	double targetX = processedVideoStreamPanel.getTargetX();
    	double delta = targetX - 160;
    	
    	float speed = (float)(delta / 160);
    	speed = speed*1f;
    	
    	float max = 0.5f; 
    	
    	if( speed > max )
    		speed = max;
    	
    	if( speed < -max )
    		speed = -max;
    	
    	return speed;
    	// negative return -> negative angular speed -> a negative value makes it spin left
    }
    
    /*
     * Control value for forwards/backwards motion.
     */
    private float getFrontBackTilt()
    {
    	double actualExtent = processedVideoStreamPanel.getTargetExtent();
    	double idealExtent = 100;  // with paddle = 100  // with cup = 40
    	
    	double tolerance = 30;
    	
    	double control;
    	double stepControl = 0.12;
    	
    	if( actualExtent < (idealExtent-(tolerance)) )
    	{
    		// move drone away
    		control = -stepControl;
    	}
    	else if( actualExtent > (idealExtent+(tolerance)) )
    	{
    		// move drone closer
    		control = +stepControl;
    	}
    	else
    	{
    		control = 0;
    	}
    	
    	// A negative value makes the drone lower its nose, thus flying frontward.
    	// A positive value makes the drone raise its nose, thus flying backward.
    	return (float)control;
    }
}


//
// REPRESENT ACTIONS 
// 

enum DroneAction {
    LAND, TAKEOFF, NONE
}


//
// INTERACTION HANDLERS
//

class LandAction extends AbstractAction
{
	private ARTracker art;
	
	public LandAction( ARTracker art )
	{
		super( "Land" );
		this.art = art;
	}
	
	public void actionPerformed( ActionEvent evt )
	{
		Logger.getLogger(LandAction.class.getName()).debug("Sending land");
		System.out.println( "LAND" );
		art.setNextAction( DroneAction.LAND );
	}
}

class TakeOffAction extends AbstractAction
{
	private ARTracker art;
	
	public TakeOffAction( ARTracker art )
	{
		super( "Take Off" );
		this.art = art;
	}
	
	public void actionPerformed( ActionEvent evt )
	{
		Logger.getLogger(TakeOffAction.class.getName()).debug("Sending takeoff");
		System.out.println( "TAKEOFF" );
        art.setNextAction( DroneAction.TAKEOFF );
	}
}









