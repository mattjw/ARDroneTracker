class PID {
    private double Kp = 1.0;
    private double Ki = 0.5;
    private double Kd = 0.5;

    private double integral = 0.0;
    private double laste = 0.0;
    public double control(double actual, double desired, double dt) {
        double error = desired - actual;
        integral += error;
        double derivative = error - laste;
        laste = error;
        return Kp*error + Ki*integral + Kd*derivative;
    }
}
