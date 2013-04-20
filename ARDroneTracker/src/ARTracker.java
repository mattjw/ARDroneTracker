/*
 * ControlTower.java
 *
 * Created on 17.05.2011, 13:41:27
 */


import java.awt.Color;
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.AbstractAction;
import javax.swing.ImageIcon;
import javax.swing.JButton;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import com.codeminders.ardrone.ARDrone;
import com.codeminders.ardrone.ARDrone.VideoChannel;
import com.codeminders.ardrone.DroneStatusChangeListener;
import com.codeminders.ardrone.NavData;
import com.codeminders.ardrone.NavDataListener;
import com.codeminders.controltower.VideoPanel;
import com.codeminders.controltower.config.AssignableControl;

import java.awt.event.ActionListener;
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
    private static float CONTROL_THRESHOLD = 0.5f;
    
    // Status atoms
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean flying = new AtomicBoolean(false);
    
    // Drone control
    private String nextAction = "";
    
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
    	
        setAlwaysOnTop(true);
        initComponents();
        droneConfigWindow = new DroneConfig(this, true);
        //initController(); //MJW
        initDrone();
    }
    
    public ARDrone getARDrone() 
    {
    	return drone;
    }

    public void setNextAction( String nextAction )
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
                    }
                }
            });
            // END: drone status change listener
            
            System.err.println("Connecting to the drone");
            drone.connect();
            drone.waitForReady(CONNECT_TIMEOUT);
            drone.clearEmergencySignal();
            System.err.println("Connected to the drone");
            
            
            /* STUFF CULLED HERE */
            // MAIN EVENT LOOP?
            try 
            {
            	while(running.get())  // "running" indicates ARDrone is running. while "running" the drone may move from "flying" or not 
                {
            		System.out.println( "A1 -- just after running.get()   "+ getAngularSpeed() );
            		
            		//
            		// Process land/takeoff actions
            		try
            		{
	            		if( nextAction.equals("LAND") ) {
	            			if( flying.get() )
	            			{
	            				drone.land();
	            				nextAction = "";
	            			}
	            			else
	            			{
	            				Logger.getLogger(getClass().getName()).error("will not land because already flying");
	            			}
	            		}
	            		
	            		if( nextAction.equals("TAKEOFF") ) {
	            			if( !flying.get() )
	            			{
	            				drone.takeOff();
	            				nextAction = "";
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
	            	}
            		
            		//
            		// Do some stuff while flying (e.g., re-orientation/movement stuff)
            		if( flying.get() )
            		{
            			System.out.println( "A2 -- just after flying.get()" );
            			
                        float left_right_tilt = 0f;
                        float front_back_tilt = 0f;
                        float vertical_speed = 0f;
                        
                        float angular_speed = getAngularSpeed();
                        
                        // if any movement parameters non-zero, then do movement
                        if(left_right_tilt != 0 || front_back_tilt != 0 || vertical_speed != 0 || angular_speed != 0)
                        {
                        	drone.move(left_right_tilt, front_back_tilt, vertical_speed, angular_speed);
                        }
            			else
            			{
            				drone.hover();
            			}
            		}
            		
            		//
            		// End of event loop -- sleep for a bit
            		try
                    {
                        Thread.sleep(READ_UPDATE_DELAY_MS);
                    }
                    catch(InterruptedException e) {}
                
                } // end whole loop
            }
            finally
            {
                drone.disconnect();
            }
        }
        catch(Throwable e)
        {
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

    public void setControlThreshold(float sens)
    {
        CONTROL_THRESHOLD = sens;
    }

    /**
     * This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {
    	
    	//
    	// Components prep
        droneStatus = new javax.swing.JLabel();
        batteryStatus = new javax.swing.JLabel();
        //flipSticksCheckbox = new javax.swing.JCheckBox();
        //mappingButton = new javax.swing.JButton();
        //instrumentButton = new javax.swing.JButton();
        videoPanel = new javax.swing.JPanel();
        
        // 
        // Frame flavour
        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setTitle("Control Tower");
        
        //
        // Layout...
        setLayout( new java.awt.BorderLayout() );
        
        javax.swing.JPanel videosSuperPanel = new javax.swing.JPanel();  // container for multiple video streams
        videosSuperPanel.setLayout( new java.awt.GridLayout(0,1) );
        
        javax.swing.JPanel guiControlsPanel = new javax.swing.JPanel();
        
        this.add(videosSuperPanel, java.awt.BorderLayout.CENTER);
        this.add(guiControlsPanel, java.awt.BorderLayout.SOUTH);
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
    }// </editor-fold>//GEN-END:initComponents

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
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel batteryStatus;
    private javax.swing.JLabel droneStatus;
    private javax.swing.JPanel videoPanel;
    // End of variables declaration//GEN-END:variables
    
    
    
    // DRONE CONTROL
    private float getAngularSpeed()
    {
    	double targetX = processedVideoStreamPanel.getTargetX();
    	double delta = targetX - 160;
    	
    	float speed = (float)(delta / 160);
    	speed = speed*1.5;
    	
    	if( speed > 1 )
    		speed = 1;
    	
    	if( speed < -1 )
    		speed = -1;
    	
    	return speed;
    	
    	
    	/*
    	double hitBoxWidth = 120f;
    	float stepSpeed = 0.99f;
    	
    	
    	double targetX = processedVideoStreamPanel.getTargetX();
    	if( targetX < hitBoxWidth )
    		// negative angular means spin left
    		return -stepSpeed;
    	
    	if( targetX > (320-hitBoxWidth) )
    		// positive angular means spin right
    		return stepSpeed;
    	
    	return 0.0f;
    	*/
    }
}

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
		Logger.getLogger(AssignableControl.class.getName()).debug("Sending land");
		System.out.println( "LAND" );
		art.setNextAction( "LAND" );
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
		Logger.getLogger(AssignableControl.class.getName()).debug("Sending takeoff");
		System.out.println( "TAKEOFF" );
        art.setNextAction( "TAKEOFF" );
	}
}









