import processing.serial.*;
import javax.sound.midi.*;
Serial serialPort;
Receiver midiReceiver;
String inputBuffer = "";
boolean lastButtonAState = false;
boolean lastButtonBState = false;
boolean lastUpState = false;
boolean lastDownState = false;
boolean lastLeftState = false;
boolean lastRightState = false;
boolean lastFire1State = false;  // Add state tracking for FIRE1 button
boolean lastFire2State = false;  // Add state tracking for FIRE2 button

// Track current channel - adjust to correct channel numbers
int currentChannel = 0;
int kickChannel = 0;    // Channel for 808 Kick
int clapChannel = 1;    // Channel for 808 Clap
int hihatChannel = 2;   // Channel for 808 HiHat
int snareChannel = 3;   // Channel for 808 Snare
int bassChannel = 4;    // Channel for FLEX Bass

String[] channelNames = {
    "808 Kick",
    "808 Clap",
    "808 HiHat",
    "808 Snare",
    "FLEX Bass"
};

// Filter control vars
int lastLightValue = 0;
int currentLightValue = 0;
int filterCutoffCC = 16;  // MIDI CC number for filter cutoff control
boolean filterActive = false;
final int LIGHT_THRESHOLD = 30;  // Light threshold for filter effect

// Tempo control vars
int currentTempo = 130;  // Default tempo (BPM)
int tempoCC = 17;        // MIDI CC for tempo control
int tempoStep = 10;      // Tempo change amount

// Visualization variables
float[] historyValues = new float[100];  // Store past light values
int historyIndex = 0;

// Motor control variables
boolean motorEnabled = false;
int motorIntensity = 600;  // 0-1023 (adjust based on your motor)
int lastMotorUpdate = 0;

void setup() {
    size(600, 400);
    
    // Setup serial connection to microbit
    println("Available Serial Ports:");
    printArray(Serial.list());
    
    if (Serial.list().length > 0) {    
        serialPort = new Serial(this, "/dev/cu.usbmodem102", 115200);
        println("Connected to Serial Port: /dev/cu.usbmodem102");
    }
    
    // Setup MIDI
    try {
        MidiDevice.Info[] devices = MidiSystem.getMidiDeviceInfo();
        println("Available MIDI Devices:");
        for (MidiDevice.Info device : devices) {
            println("  " + device.getName());
        }
        
        // Try to connect to the MIDI device
        boolean deviceFound = false;
        for (MidiDevice.Info device : devices) {
            if (device.getName().contains("Microbit") || device.getName().contains("Bus")) {
                MidiDevice midiDevice = MidiSystem.getMidiDevice(device);
                midiDevice.open();
                midiReceiver = midiDevice.getReceiver();
                println("Successfully connected to MIDI: " + device.getName());
                deviceFound = true;
                break;
            }
        }
        
        if (!deviceFound && devices.length > 0) {
            // Fallback to first available device
            MidiDevice midiDevice = MidiSystem.getMidiDevice(devices[0]);
            midiDevice.open();
            midiReceiver = midiDevice.getReceiver();
            println("Fallback: connected to MIDI: " + devices[0].getName());
        }
    } catch (Exception e) {
        println("Error setting up MIDI:");
        e.printStackTrace();
    }
    
    // Initialize history
    for (int i = 0; i < historyValues.length; i++) {
        historyValues[i] = 0;
    }
    
    // Send initial tempo value
    sendTempoChange(currentTempo);
}

void increaseTempo() {
    currentTempo += tempoStep;
    if (currentTempo > 300) currentTempo = 300;  // Max reasonable tempo
    println("Tempo increased to: " + currentTempo + " BPM");
    sendTempoChange(currentTempo);
}

void decreaseTempo() {
    currentTempo -= tempoStep;
    if (currentTempo < 40) currentTempo = 40;  // Min reasonable tempo
    println("Tempo decreased to: " + currentTempo + " BPM");
    sendTempoChange(currentTempo);
}

void sendTempoChange(int tempo) {
    try {
        if (midiReceiver != null) {
            // Convert tempo to MIDI range (0-127)
            int tempoValue = int(map(tempo, 40, 300, 0, 127));
            
            // Send as CC message
            ShortMessage ccMessage = new ShortMessage();
            ccMessage.setMessage(ShortMessage.CONTROL_CHANGE, 0, tempoCC, tempoValue);
            midiReceiver.send(ccMessage, -1);
            println("Sent Tempo Change: " + tempo + " BPM (MIDI value: " + tempoValue + ")");
            
            // Update motor pulse timing if enabled
            if (motorEnabled) {
                updateMotor();
            }
        }
    } catch (InvalidMidiDataException e) {
        println("Error sending tempo change:");
        e.printStackTrace();
    }
}

void toggleFilter() {
    filterActive = !filterActive;
    println(filterActive ? "Filter Control Activated" : "Filter Control Deactivated");
}

void toggleMotorFeedback() {
    motorEnabled = !motorEnabled;
    println(motorEnabled ? "Motor Tempo Feedback Activated" : "Motor Tempo Feedback Deactivated");
    updateMotor();
}

void updateMotor() {
    if (serialPort != null) {
        if (motorEnabled) {
            // Calculate interval in ms based on current tempo (BPM)
            int beatInterval = (int)(60000.0 / currentTempo);
            
            // Send motor command with intensity and interval
            serialPort.write("M:" + motorIntensity + "," + beatInterval + "\n");
            println("Motor pulse updated: " + motorIntensity + " strength, " + beatInterval + "ms interval");
        } else {
            // Turn off motor
            serialPort.write("M:0,1000\n");
            println("Motor disabled");
        }
    }
}

// Add this function for the note feedback motor pulse
void triggerMotorPulse() {
    if (serialPort != null) {
        // Send a command to trigger a short motor pulse
        // Format: P:intensity,duration
        int pulseIntensity = 800;  // 0-1023 (vibration strength)
        int pulseDuration = 100;   // milliseconds
        
        serialPort.write("P:" + pulseIntensity + "," + pulseDuration + "\n");
        println("Triggered motor pulse: " + pulseIntensity + " strength, " + pulseDuration + "ms duration");
    }
}

void setChannel(int channelIndex) {
    if (channelIndex >= 0 && channelIndex < channelNames.length) {
        currentChannel = channelIndex;
        println("Switched to channel: " + (currentChannel) + " - " + channelNames[currentChannel]);
    }
}

void sendNote() {
    sendNote(currentChannel);
}

void sendNote(int channel) {
    try {
        if (midiReceiver != null) {
            ShortMessage noteOn = new ShortMessage();
            noteOn.setMessage(ShortMessage.NOTE_ON, channel, 60, 100);
            midiReceiver.send(noteOn, -1);
            println("Sent Note ON on channel: " + (channel) + " (" + channelNames[channel] + ")");
            
            // Trigger motor pulse when a note is played
            triggerMotorPulse();
            
            delay(100);
            
            ShortMessage noteOff = new ShortMessage();
            noteOff.setMessage(ShortMessage.NOTE_OFF, channel, 60, 0);
            midiReceiver.send(noteOff, -1);
        }
    } catch (InvalidMidiDataException e) {
        println("Error sending MIDI note:");
        e.printStackTrace();
    }
}

void sendLightAsFilterControl(int value) {
    try {
        if (midiReceiver != null && filterActive) {
            // REVERSED MAPPING: 
            // Light value 0 = max filter (127), Light value THRESHOLD+ = no filter (0)
            int remappedValue;
            if (value <= LIGHT_THRESHOLD) {
                remappedValue = int(map(value, 0, LIGHT_THRESHOLD, 127, 0));
            } else {
                remappedValue = 0;
            }
            
            ShortMessage ccMessage = new ShortMessage();
            ccMessage.setMessage(ShortMessage.CONTROL_CHANGE, 0, filterCutoffCC, remappedValue);
            midiReceiver.send(ccMessage, -1);
            
            // Store value in history for visualization
            historyValues[historyIndex] = value;
            historyIndex = (historyIndex + 1) % historyValues.length;
            
            // Print values for debugging
            println("Light: " + value + ", Filter: " + remappedValue + " (Threshold: " + LIGHT_THRESHOLD + ")");
        }
    } catch (InvalidMidiDataException e) {
        println("Error sending CC message:");
        e.printStackTrace();
    }
}

void serialEvent(Serial port) {
    while (port.available() > 0) {
        char inChar = (char)port.read();
        if (inChar == '\n' || inChar == '\r') {
            if (inputBuffer.length() > 0) {
                processLine(inputBuffer);
                inputBuffer = "";
            }
        } else {
            inputBuffer += inChar;
        }
    }
}

void processLine(String line) {
    try {
        //println("Received: " + line);  // For debugging
        
        // Format: L:value,A:state,B:state,U:state,D:state,L:state,R:state,F1:state,F2:state
        if (line.startsWith("L:")) {
            String[] parts = line.split(",");
            
            // Extract light value
            String lightPart = parts[0].substring(2);  // Remove "L:"
            currentLightValue = Integer.parseInt(lightPart.trim());
            
            // Extract microbit button states
            boolean buttonA = false;
            boolean buttonB = false;
            
            if (parts.length >= 3) {
                buttonA = parts[1].substring(2).trim().equals("True");
                buttonB = parts[2].substring(2).trim().equals("True");
            }
            
            // Extract gamepad button states if available
            boolean buttonUp = false;
            boolean buttonDown = false;
            boolean buttonLeft = false;
            boolean buttonRight = false;
            boolean buttonFire1 = false;
            boolean buttonFire2 = false;
            
            if (parts.length >= 7) {
                buttonUp = parts[3].substring(2).trim().equals("1");
                buttonDown = parts[4].substring(2).trim().equals("1");
                buttonLeft = parts[5].substring(2).trim().equals("1");
                buttonRight = parts[6].substring(2).trim().equals("1");
                
                // Check for FIRE1 button if available
                if (parts.length >= 8) {
                    buttonFire1 = parts[7].substring(3).trim().equals("1");
                    
                    // FIRE1 button increases tempo
                    if (buttonFire1 && !lastFire1State) {
                        println("FIRE1 button pressed - Increasing Tempo");
                        increaseTempo();
                    }
                    
                    lastFire1State = buttonFire1;
                }
                
                // Check for FIRE2 button if available
                if (parts.length >= 9) {
                    buttonFire2 = parts[8].substring(3).trim().equals("1");
                    
                    // FIRE2 button decreases tempo
                    if (buttonFire2 && !lastFire2State) {
                        println("FIRE2 button pressed - Decreasing Tempo");
                        decreaseTempo();
                    }
                    
                    lastFire2State = buttonFire2;
                }
                
                // Direct instrument selection with D-pad AND play sound immediately
                if (buttonUp && !lastUpState) {
                    println("UP button pressed - Playing 808 Kick");
                    setChannel(kickChannel);  // Select Kick
                    sendNote(kickChannel);    // Play Kick immediately
                }
                
                if (buttonRight && !lastRightState) {
                    println("RIGHT button pressed - Playing 808 Clap");
                    setChannel(clapChannel);  // Select Clap
                    sendNote(clapChannel);    // Play Clap immediately
                }
                
                if (buttonDown && !lastDownState) {
                    println("DOWN button pressed - Playing 808 HiHat");
                    setChannel(hihatChannel);  // Select HiHat
                    sendNote(hihatChannel);    // Play HiHat immediately
                }
                
                if (buttonLeft && !lastLeftState) {
                    println("LEFT button pressed - Playing 808 Snare");
                    setChannel(snareChannel);  // Select Snare
                    sendNote(snareChannel);    // Play Snare immediately
                }
                
                // Update button states
                lastUpState = buttonUp;
                lastDownState = buttonDown;
                lastLeftState = buttonLeft;
                lastRightState = buttonRight;
            }
            
            // Both buttons pressed together - toggle filter
            if (buttonA && buttonB) {
                // If we just detected both buttons pressed
                if (!lastButtonAState || !lastButtonBState) {
                    toggleFilter();
                }
            }
            // Handle button A press (play note)
            else if (buttonA && !lastButtonAState) {
                sendNote();
            }
            // Handle button B press (FLEX Bass)
            else if (buttonB && !lastButtonBState) {
                // Select FLEX Bass and play it
                setChannel(bassChannel);  // FLEX Bass
                sendNote(bassChannel);    // Play Bass immediately
            }
            
            // Only send CC if value changed (to avoid MIDI flood)
            if (currentLightValue != lastLightValue) {
                sendLightAsFilterControl(currentLightValue);
                lastLightValue = currentLightValue;
            }
            
            lastButtonAState = buttonA;
            lastButtonBState = buttonB;
        }
        // Handle original format as fallback
        else if (line.contains(",")) {
            String[] parts = line.split(",");
            if (parts.length >= 5) {
                boolean buttonA = parts[3].trim().equals("True");
                boolean buttonB = parts[4].trim().equals("True");
                
                // Both buttons pressed - toggle filter
                if (buttonA && buttonB) {
                    // If we just detected both buttons pressed
                    if (!lastButtonAState || !lastButtonBState) {
                        toggleFilter();
                    }
                }
                // Button A plays note on current channel
                else if (buttonA && !lastButtonAState) {
                    sendNote();
                }
                // Button B just selects FLEX Bass
                else if (buttonB && !lastButtonBState) {
                    setChannel(bassChannel);  // FLEX Bass
                    sendNote(bassChannel);    // Play Bass immediately
                }
                
                lastButtonAState = buttonA;
                lastButtonBState = buttonB;
            }
        }
    } catch (Exception e) {
        println("Error processing line: " + line);
        e.printStackTrace();
    }
}

void keyPressed() {
    if (key == 'm' || key == 'M') {
        toggleMotorFeedback();
    }
}

void drawFilterCurve() {
    // Draw the filter response curve for a high-pass filter
    // Now with reversed mapping (light = no filter, dark = max filter)
    stroke(255, 200, 0, 200);
    strokeWeight(2);
    noFill();
    
    beginShape();
    // High-pass filter curve with reversed mapping
    float cutoff = map(lastLightValue, 0, LIGHT_THRESHOLD, width/2, 0);
    if (lastLightValue > LIGHT_THRESHOLD) cutoff = 0;
    
    for (int x = 0; x < width; x++) {
        float y;
        if (x < cutoff) {
            // Attenuated range
            y = height/2 + 50 + 30 * sin(x * 0.1) * (x / max(1, cutoff));
        } else {
            // Passed range
            y = height/2 + 50 - 30 * sin(x * 0.05);
        }
        vertex(x, y);
    }
    endShape();
}


void draw() {
    background(0);
    
    // Draw MIDI status indicator
    if (midiReceiver != null) {
        fill(0, 255, 0);
    } else {
        fill(255, 0, 0);
    }
    
    ellipse(width/2, height/2, 50, 50);
    // Draw filter status
    if (filterActive) {
        fill(255, 200, 0);
        ellipse(width/2 + 60, height/2, 30, 30);
        fill(255);
        textAlign(CENTER);
        textSize(12);
        text("FILTER ON", width/2 + 60, height/2 + 40);
    }
    
    // Draw current light sensor value
    fill(255, 255, 0);
    textAlign(CENTER);
    textSize(18);
    text("Light: " + currentLightValue, width/2, height/2 - 140);
    
    // Draw current channel
    fill(0, 255, 255);
    text("Channel: " + channelNames[currentChannel], width/2, height/2 + 90);
    
   
    
    // Draw the light sensor value bar with threshold indicator
    fill(255, 200, 0);
    rect(width/2 - 100, height/2 - 80, map(currentLightValue, 0, 127, 0, 200), 20);
    
    // Add threshold indicator line on the bar
    stroke(255, 0, 0);
    strokeWeight(2);
    float thresholdX = width/2 - 100 + map(LIGHT_THRESHOLD, 0, 127, 0, 200);
    line(thresholdX, height/2 - 85, thresholdX, height/2 - 55);
    
    if (filterActive) {
        // Draw filter visualization
        drawFilterCurve();
        
        // Draw history graph for filter values
        stroke(0, 255, 255);
        strokeWeight(2);
        noFill();
        beginShape();
        for (int i = 0; i < historyValues.length; i++) {
            int idx = (historyIndex - i + historyValues.length) % historyValues.length;
            float x = width - i * (width / historyValues.length);
            float y = height/2 + 150 - historyValues[idx] * 0.7;
            vertex(x, y);
        }
        endShape();
    }
    
    // Draw instructions
    fill(255);
    textAlign(CENTER);
    textSize(14);
    text("Button A: Play Current Instrument", width/2, height-140);
    text("Button B: Select & Play FLEX Bass", width/2, height-120);
    text("FIRE1 (SW1): Increase Tempo by 10 BPM", width/2, height-100);
    text("FIRE2 (SW2): Decrease Tempo by 10 BPM", width/2, height-80);
    text("Hold A+B Together: Toggle Filter ON/OFF", width/2, height-60);
    text("Light Sensor: Cover = Max Filter, Light > " + LIGHT_THRESHOLD + " = No Filter", width/2, height-40);
    text("Press 'M' to Toggle Motor Tempo Feedback", width/2, height-20);
}
