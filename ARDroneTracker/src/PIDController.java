class PIDController 
{
    private double Kp = 1.0;
    private double Ki = 0.5;
    private double Kd = 0.5;

    private double integral = 0.0;
    private double laste = 0.0;
    
    private double maxControlMagnitude = 1.0; 
    // NOT used in the control process
    // the control output will just be cutoff at this value
    
    public PIDController() {}
    
    public PIDController( double Kp, double Ki, double Kd, double maxControlMagnitude )
    {
    	assert maxControlMagnitude >= 0;
    	
    	this.Kp = Kp;
    	this.Ki = Ki;
    	this.Kd = Kd;
    	this.maxControlMagnitude = maxControlMagnitude;
    }
    
    // let dt be zero because equal sampling
    // NOT used in this method anyway!
    public double control(double actual, double desired, double dt) 
    {
        double error = actual - desired;
        integral += error;
        double derivative = error - laste;
        laste = error;
        
        double control = Kp*error + Ki*integral + Kd*derivative;
        
        double adjustedControl;
        
        // Handle capping
        if( control > maxControlMagnitude )
        	adjustedControl = maxControlMagnitude;
        else if( control < -maxControlMagnitude)
        	adjustedControl = -maxControlMagnitude;
        else
        	adjustedControl = control;
        
        /*
        System.out.println( String.format(
        		"control  %.3f  |   adj cont  %.3f   |  error %.3f  |  itnegral %.3f  |  deriv %.3f |    maxControlMagnitude %.3f",
        		control, adjustedControl, error, integral, derivative,
        		this.maxControlMagnitude) );
        */
        
        return adjustedControl;
    }
}
