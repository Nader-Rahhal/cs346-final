from microbit import *
uart.init(baudrate=115200)

# Define motor pin
MOTOR = pin1
BUZZER = pin2  # For future use

# Define Kitronik gamepad pins
UP = pin8
DOWN = pin14
LEFT = pin12
RIGHT = pin13
FIRE1 = pin15  # SW1 button
FIRE2 = pin16  # SW2 button (adjust if needed)

# Configure motor pin as output
MOTOR.set_analog_period(20)  # Set PWM period 

# Set pins as inputs with pull-ups
UP.set_pull(UP.PULL_UP)
DOWN.set_pull(DOWN.PULL_UP)
LEFT.set_pull(LEFT.PULL_UP)
RIGHT.set_pull(RIGHT.PULL_UP)
FIRE1.set_pull(FIRE1.PULL_UP)
FIRE2.set_pull(FIRE2.PULL_UP)

# Variables for smoother readings
light_readings = [0, 0, 0, 0, 0]  # Store last 5 readings
reading_index = 0
last_send_time = 0
send_interval = 50  # Send data every 50ms

# Motor control variables
motor_on = False
motor_intensity = 0
motor_pulse_end_time = 0  # Time when current pulse should end

while True:
    current_time = running_time()
    
    # Get current light level (0-255)
    current_light = display.read_light_level()
    
    # Store in our readings array for smoothing
    light_readings[reading_index] = current_light
    reading_index = (reading_index + 1) % len(light_readings)
    
    # Calculate average (smoother) reading
    avg_light = sum(light_readings) // len(light_readings)
    
    # Map to 0-127 range for MIDI
    midi_value = min(127, avg_light // 2)
    
    # Read all button states
    button_a_state = button_a.is_pressed()
    button_b_state = button_b.is_pressed()
    
    # Read gamepad buttons (they are active LOW - 0 means pressed)
    # Convert to string "1" for pressed, "0" for not pressed
    up_state = "1" if UP.read_digital() == 0 else "0"
    down_state = "1" if DOWN.read_digital() == 0 else "0"
    left_state = "1" if LEFT.read_digital() == 0 else "0"
    right_state = "1" if RIGHT.read_digital() == 0 else "0"
    fire1_state = "1" if FIRE1.read_digital() == 0 else "0"
    fire2_state = "1" if FIRE2.read_digital() == 0 else "0"
    
    # Check for commands from Processing
    if uart.any():
        data = uart.read()
        try:
            cmd = str(data, 'utf-8').strip()
            
            # Handle motor pulse command (P:intensity,duration)
            if cmd.startswith("P:"):
                parts = cmd.split(":")
                if len(parts) >= 2:
                    params = parts[1].split(",")
                    if len(params) >= 2:
                        intensity = min(1023, max(0, int(params[0])))
                        duration = int(params[1])
                        
                        # Start the motor pulse
                        MOTOR.write_analog(intensity)
                        motor_on = True
                        motor_pulse_end_time = current_time + duration
        except:
            pass
    
    # Turn off motor if pulse duration has elapsed
    if motor_on and current_time >= motor_pulse_end_time:
        MOTOR.write_analog(0)
        motor_on = False
    
    # Show light level on display
    display.clear()
    
    # Light level indicator (intensity in middle pixel)
    brightness = min(9, 1 + (avg_light // 28))  # Map to LED brightness (1-9)
    display.set_pixel(2, 2, brightness)
    
    # Show directional button presses on display
    if up_state == "1":
        display.set_pixel(2, 0, 9)  # Up
    if down_state == "1":
        display.set_pixel(2, 4, 9)  # Down
    if left_state == "1":
        display.set_pixel(0, 2, 9)  # Left
    if right_state == "1":
        display.set_pixel(4, 2, 9)  # Right
    if fire1_state == "1":
        display.set_pixel(4, 0, 9)  # FIRE1
    if fire2_state == "1":
        display.set_pixel(0, 4, 9)  # FIRE2
    
    # Show motor status
    if motor_on:
        display.set_pixel(0, 0, 9)  # Motor indicator
    
    # Only send data periodically to avoid flooding
    if current_time - last_send_time >= send_interval:
        uart.write("L:%d,A:%s,B:%s,U:%s,D:%s,L:%s,R:%s,F1:%s,F2:%s\n" % 
                  (midi_value, str(button_a_state), str(button_b_state),
                   up_state, down_state, left_state, right_state, fire1_state, fire2_state))
        last_send_time = current_time
    
    sleep(10)  # Short sleep for responsive motor control
