# Findings from scan_github_repos.json

## etomasfe/SpikeRemoteControl
- Relevance: 9/10
- Connection: BLE
- Firmware: Unknown
### Technical Details
The project uses Web Bluetooth to connect to the SPIKE Hub. 

**BLE Service and Characteristic UUIDs:**
- LEGO SPIKE Prime Service UUID: `00001623-1212-efde-1623-785feabcd123`
- RX Characteristic UUID: `00001624-1212-efde-1623-785feabcd123`
- TX Characteristic UUID: `00001625-1212-efde-1623-785feabcd123`

**COBS Encoding:**
A custom COBS-like implementation is used for framing data. Key parameters:
- `DELIMITER = 0x02`
- `NO_DELIMITER = 255`
- `MAX_BLOCK_SIZE = 84`
- `COBS_CODE_OFFSET = 2`
- `XOR = 0x03` (data is XORed with this value before encoding)

**CRC32:**
- The code includes Javascript functions `crc32Uint8Array` and `crc32String` for calculating CRC32 checksums. The implementation appears to be a standard CRC32 algorithm.

**TunnelMessage:**
- The code uses `hub.config['module_tunnel']` to establish a data tunnel between the web browser and the Python script running on the hub. This is used for sending motor commands.

**Motor Control:**
- Motor commands are sent as strings. The first character represents the motor port ('A' through 'F'), and the following characters represent the speed. The Python script on the hub parses these strings and uses the `motor.run()` function to control the motors.

### Reusable/Actionable
The COBS encoding and decoding logic can be adapted for our Java/App Inventor implementation. The CRC32 functions are also reusable. The overall program upload flow (clearing a slot, sending the program in chunks, and starting the program) provides a good reference. The use of the `module_tunnel` for sending commands is a key insight.

### Hub Python Code
```python
from hub import light_matrix
import motor
from hub import port
light_matrix.set_pixel(1,1,100)
import hub
tunnel = hub.config['module_tunnel']
def receive_tunnel_message(data):
    if data==b"bye.bye.AB":
        quit()    
    if True:
        n=''
        for i in range(1,5):
            n=n+chr(data[i])
        mA=int(n)*10
        n=''
        for i in range(6,10):
            n=n+chr(data[i])
        mB=int(n)*10
        if chr(data[0])=='A':
            motor.run(port.A,mA)
        elif chr(data[0])=='B':
            motor.run(port.B,mA)
        elif chr(data[0])=='C':
            motor.run(port.C,mA)
        elif chr(data[0])=='D':
            motor.run(port.D,mA)
        elif chr(data[0])=='E':
            motor.run(port.E,mA)
        elif chr(data[0])=='F':
            motor.run(port.F,mA)
        if chr(data[5])=='A':
            motor.run(port.A,mB)
        elif chr(data[5])=='B':
            motor.run(port.B,mB)
        elif chr(data[5])=='C':
            motor.run(port.C,mB)
        elif chr(data[5])=='D':
            motor.run(port.D,mB)
        elif chr(data[5])=='E':
            motor.run(port.E,mB)
        elif chr(data[5])=='F':
            motor.run(port.F,mB)

tunnel.message_handler(receive_tunnel_message)

```

### Warnings/Dead Ends
The author notes that the code is a 'first attempt' and 'a bit confused'. The `crc32String` function includes a warning that results may be incorrect for non-ASCII characters.

## gpdaniels/spike-prime
- Relevance: 8/10
- Connection: Classic_BT_SPP
- Firmware: Both
### Technical Details
The repository uses Classic Bluetooth SPP/RFCOMM for communication. The Android controller example in `bluetooth.java` uses `device.createInsecureRfcommSocketToServiceRecord(uuid)` to establish a connection. The UUID is obtained dynamically from the device using `device.getUuids()[0].getUuid()`. The communication is handled by reading and writing single bytes to the Bluetooth socket's input and output streams. The `api.java` file demonstrates a simple loop that reads data from the socket and logs it as a UTF-8 string, but does not implement any specific command protocol for motor control or sensor reading.

### Reusable/Actionable
The `bluetooth.java` file is a valuable resource for our App Inventor extension. It provides a clear and concise example of how to discover paired SPIKE Prime hubs and establish a Classic Bluetooth connection on Android. The use of `createInsecureRfcommSocketToServiceRecord` is a key technical detail that can be directly applied to our Java implementation. The method for iterating through paired devices and extracting their names and addresses is also reusable.

### Hub Python Code
```python
import firmware
firmware.flash_read(BYTE_NUMBER)
```

### Warnings/Dead Ends
The author notes in the README that Bluetooth on Linux can be unreliable. The Android controller is a basic implementation that only logs received data and does not provide any control functionality. The project does not seem to be actively maintained.

## gpdaniels/spike-prime
- Relevance: 9/10
- Connection: Classic_BT_SPP
- Firmware: Both
### Technical Details
The repository uses Classic Bluetooth (SPP/RFCOMM) for communication. The Java code dynamically retrieves the UUID from the device for creating the RFCOMM socket. The README provides detailed hardware specifications for the SPIKE Prime hub, including CPU, memory, and wireless connectivity details. It also outlines the process for connecting to the hub via both USB and Bluetooth on a Linux system.

### Reusable/Actionable
The 'bluetooth.java' file is a significant resource, offering a practical example of establishing a Classic Bluetooth connection to the SPIKE Prime hub within a Java for Android environment. This can be directly adapted for use in an App Inventor extension. Additionally, the Python scripts located in the 'simulator/scripts' directory serve as valuable references for understanding the commands required to control motors and read sensor data.

### Hub Python Code
```python
import firmware
firmware.flash_read(BYTE_NUMBER)
```

### Warnings/Dead Ends
The README.md file notes that Bluetooth connectivity on Linux can be unreliable.

## JuniorJacki/RemoteBrick
- Relevance: 8/10
- Connection: Classic_BT_SPP
- Firmware: Unknown
### Technical Details
The project uses Bluetooth Classic (SPP/RFCOMM) for communication, not BLE. It communicates with the hub using a JSON-RPC-like protocol. Commands are sent as JSON strings with a method name (`m`) and parameters (`p`). For example: `{"m":"scratch.motor_start","p":{"port":"A","speed":100}}`. Each command has a unique 4-character ID (`i`) for tracking responses. The hub sends back JSON messages with information about sensor data, hub state, and command responses. The `Hub.java` file manages the connection and message parsing. The `HubConnector.dll` is a native library used for Bluetooth communication on Windows. No BLE UUIDs are used. No COBS or CRC32 encoding is used. Motor commands include `start`, `stop`, `run_for_degrees`, `run_for_time`, etc. Sensor data for color, distance, and hub orientation are parsed from incoming JSON messages.

### Reusable/Actionable
The JSON-based protocol is a key finding. While we are using BLE, understanding this higher-level protocol that the official firmware uses is very valuable. It's likely the same or a similar JSON protocol is used over the BLE UART service. The list of 'scratch' commands for motors, sensors, and display is directly reusable. We can implement these commands in our App Inventor extension. The parsing logic for sensor data and hub state from the JSON messages can be adapted to our Java implementation. The project provides a good reference for the data format of sensor readings.

### Warnings/Dead Ends
The library is for Windows only due to the native `HubConnector.dll`. The `CONTRIBUTING.md` file mentions that macOS and Linux support is needed.

## tuftsceeo/SPIKEPythonDocs
- Relevance: 7/10
- Connection: Unknown
- Firmware: Both
### Technical Details
The repository is a documentation dump of the official LEGO SPIKE Python knowledge base. It does not contain any implementation details about the underlying communication protocol. It mentions that the Hub can be connected via Bluetooth or USB, but it does not provide any specifics about the Bluetooth services, characteristics, or data exchange protocols (like BLE UUIDs, COBS, or TunnelMessage). The focus is purely on the high-level Python API for controlling the Hub' Hub's hardware.

### Reusable/Actionable
The repository provides extensive Python code examples for controlling motors and reading sensors on both SPIKE 2.x and 3.x firmware. These examples are invaluable for understanding the high-level Python API that the SPIKE Hub exposes. This knowledge can be used to inform the design of the App Inventor extension by providing a clear picture of the functionalities that should be exposed to the user and how they are intended to be used.

### Hub Python Code
```python
from hub import light_matrix

light_matrix.write('Hello, World!')
```

## tuftsceeo/SPIKE-Web-Interface
- Relevance: 7/10
- Connection: USB_Serial
- Firmware: SPIKE_2x
### Technical Details
The repository uses the Web Serial API to communicate with the SPIKE Prime hub over a USB connection at a baud rate of 115200. The communication protocol is text-based, using UJSON-RPC (a variant of JSON-RPC) with commands and responses delimited by carriage returns. The system can also drop into a raw MicroPython REPL. There is no evidence of BLE, COBS encoding, CRC32, or TunnelMessage usage. The firmware targeted is an older version from 2020, indicating it's for SPIKE 2.x. The program upload process involves wrapping the user's Python code in a specific template that utilizes a `VirtualMachine` from a `runtime` module, then sending the code in base64 encoded chunks.

### Reusable/Actionable
The project provides a complete JavaScript implementation of the UJSON-RPC protocol over Web Serial, which serves as an excellent reference for understanding the communication flow. The program upload mechanism, including the Python code wrapping, base64 encoding, and chunking, is a significant learning point. The comprehensive list of UJSON-RPC commands for controlling motors, reading sensors, and interacting with the hub provides a solid foundation for a Java/App Inventor implementation. The error handling and the logic for parsing the JSON-RPC responses are also reusable concepts.

### Hub Python Code
```python
from runtime import VirtualMachine

# Stack for execution:
async def stack_1(vm, stack):
    # User's Python code is inserted here

# Setup for execution:
def setup(rpc, system, stop):

    # Initialize VM:
    vm = VirtualMachine(rpc, system, stop, "Target__1")

    # Register stack on VM:
    vm.register_on_start("stack_1", stack_1)

    return vm
```

### Warnings/Dead Ends
The documentation mentions that for the Web Serial API to work, Chrome flags need to be enabled (#enable-experimental-web-platform-features and on Windows also #new-usb-backend). It also warns not to have another app or browser connected to the hub simultaneously. The program upload has a 5-second timeout, and if there is no response, it suggests rebooting the hub.

## GO-Robot-FLL/Python-for-Spike-Prime
- Relevance: 6/10
- Connection: USB_Serial
- Firmware: Unknown
### Technical Details
The repository does not contain any information about BLE UUIDs, COBS encoding, CRC32, or TunnelMessage. The connection to the SPIKE Prime hub is established via a USB serial connection, managed by a VS Code extension. The project uses the standard LEGO MicroPython API (`spike` library) for communication. Motor control is implemented using the `Motor` and `MotorPair` classes, with PID control for smooth movement. Sensor data is read from the `ColorSensor` and the hub's built-in gyroscope.

### Reusable/Actionable
The PID control logic for motor movements (pidCalculation, pidCalculationLight) can be a valuable reference for implementing smooth and accurate motor control in a Java/App Inventor environment. The different `stopMethods` also provide a good example of how to create a flexible and modular system for controlling the robot's behavior. The overall structure of the `DriveBase` class and the way it encapsulates the robot's movement functions is a good design pattern to follow.

### Hub Python Code
```python
# LEGO type:standard slot:1 autostart

import math
from spike import PrimeHub, Motor, MotorPair, ColorSensor
from spike.control import wait_for_seconds, Timer
from hub import battery
hub = PrimeHub()


import hub as hub2

import sys

#Preperation for parallel code execution
accelerate = True
run_generator = True
runSmall = True

lastAngle = 0
oldAngle = 0

gyroValue = 0

# Create your objects here.
hub = PrimeHub()

#PID value Definition
pRegler = 0.0
iRegler = 0.0
dRegler = 0.0

pReglerLight = 0.0
iReglerLight = 0.0
dReglerLight = 0.0

"""
Initialize color Sensors
left sensor: port F
right sensor: port E
"""
colorE = ColorSensor('E') #adjust the sensor ports until they match your configuration, we recommend assigning your ports to the ones in the program for ease of use
colorF = ColorSensor('F')
smallMotorA = Motor('A')
smallMotorD = Motor('D')

#Set variables based on robot
circumference = 17.6 #circumference of the wheel powered by the robot in cm
sensordistance = 7 #distance between the two light sensors in cm. Used in Tangent alignment 6.4 in studs



cancel = False
inMain = True

class DriveBase:

    def __init__(self, hub, leftMotor, rightMotor):
        self.hub = hub
        self.leftMotor = Motor(leftMotor)
        self.rightMotor = Motor(rightMotor)
        self.movement_motors = MotorPair(leftMotor, rightMotor) 

    def lineFollower(self, distance, startspeed, maxspeed, endspeed, sensorPort, side, addspeed = 0.2, brakeStart = 0.7 , stopMethod=None, generator = None, stop = True):
        """
            This is the function used to let the robot follow a line until either the entered distance has been achieved or the other sensor of the robot senses a line.
            Like all functions that drive the robot this function has linear acceleration and breaking. It also uses PID values that are automatically set depending on the
            current speed of the robot (See function PIDCalculationLight)
            Parameters
            -------------
            distance: The value tells the program the distance the robot has to drive. Type: Integer. Default: No default value
            speed: The speed which the robot is supposed to start at. Type: Integer. Default: No default value
            maxspeed: The highest speed at which the robot drives. Type: Integer. Default: No default value
            endspeed: The speed which the robot achieves at the end of the function. Type: Integer. Default: No default value
            addspeed: The percentage after which the robot reaches its maxspeed. Type: Float. Default: No default value
            brakeStart: The value which we use to tell the robot after what percentage of the distance we need to slow down. Type: Float. Default: No default value
            stopMethod: the Stopmethod the robot uses to stop. If no stopMethod is passed stopDistance is used instead. Default: stopDistance
            generator:  the generator that runs something parallel while driving. Default: No default value
            stop: the boolean that determines whether the robot should stop the motors after driving or not. Default: True
        """
        
        if cancel:
            return

        global run_generator, runSmall

        if generator == None:
            run_generator = False

        #set the speed the robot starts at
        speed = startspeed
        #reset PID values to eliminate bugs
        change = 0
        old_change = 0
        integral = 0
        #reset the driven distance of the robot to eliminate bugs

        #specifies the color sensor
        colorsensor = ColorSensor(sensorPort)
        #Get degrees of motors turned before robot has moved, allows for distance calculation without resetting motors
        loop = True
        #Going backwards is not supported on our robot due to the motors then being in front of the colour sensors and the program not working
        if distance < 0:
            print('ERR: distance < 0')
            distance = abs(distance)
        #Calculate target values for the motors to turn to
        finalDistance = (distance / 17.6) * 360
        #Calculate after what distance the robot has to reach max speed
        accelerateDistance = finalDistance * addspeed
        deccelerateDistance = finalDistance * (1 - brakeStart)

        invert = 1

        #Calculation of steering factor, depending on which side of the line we are on
        if side == "left":
            invert = 1
        elif side == "right":
            invert = -1
        
        #Calculation of the start of the robot slowing down
        
        self.left_Startvalue = self.leftMotor.get_degrees_counted()
        self.right_Startvalue = self.rightMotor.get_degrees_counted()
        drivenDistance = getDrivenDistance(self)

        brakeStartValue = brakeStart * finalDistance
        while loop:
            if cancel:
                print("cancel")
                break
        
            if run_generator: #run parallel code execution
                next(generator)

            #Checks the driven distance as an average of both motors for increased accuracy
            oldDrivenDistance = drivenDistance
            drivenDistance = getDrivenDistance(self)
            #Calculates target value for Robot as the edge of black and white lines
            old_change = change

            change = colorsensor.get_reflected_light() - 50


            #Steering factor calculation using PID, sets new I value

        
            steering = (((change * pReglerLight) + (integral * iReglerLight) + (dReglerLight * (change - old_change)))) * invert
            integral = change + integral
            #Calculation of current speed for robot, used for acceleratiion, braking etc.
            speed = speedCalculation(speed, startspeed, maxspeed, endspeed, accelerateDistance, deccelerateDistance, brakeStartValue, drivenDistance, oldDrivenDistance)

            pidCalculationLight(speed)
            #PID value updates
            steering = max(-100, min(steering, 100))

            #Driving using speed values calculated with PID and acceleration for steering, use of distance check
            self.movement_motors.start_at_power(int(speed), int(steering))

            if stopMethod != None:
                if stopMethod.loop():
                    loop = False
            else:   
                if finalDistance < drivenDistance:
                    break

        if stop:
            self.movement_motors.stop()
            
        run_generator = True
        runSmall = True
        generator = 0
        return

    def gyroRotation(self, angle, startspeed, maxspeed, endspeed, addspeed = 0.3, brakeStart = 0.7, rotate_mode = 0, stopMethod = None, generator = None, stop = True):
        """
            This is the function that we use to make the robot turn the length of a specific angle or for the robot to turn until it senses a line. Even in this function the robot
            can accelerate and slow down. It also has Gyrosensor calibrations based on our experimental experience.
            Parameters
            -------------
            angle: The angle which the robot is supposed to turn. Use negative numbers to turn counterclockwise. Type: Integer. Default value: No default value
            startspeed: The speed which the robot is supposed to start at. Type: Integer. Default: No default value
            maxspeed: The highest speed at which the robot drives. Type: Integer. Default: No default value
            endspeed: The speed which the robot achieves at the end of the function. Type: Integer. Default: No default value
            addspeed: The percentage after which the robot reaches the maxspeed. Type: Float. Default: No default value
            brakeStart: The percentage after which the robot starts slowing down until it reaches endspeed. Type: Float. Default: No default value
            rotate_mode: Different turning types. 0: Both motors turn, robot turns on the spot. 1: Only the outer motor turns, resulting in a corner. Type: Integer. Default: 0
            stopMethod: the Stopmethod the robot uses to stop. If no stopMethod is passed stopDistance is used instead. Default: stopDistance
            generator:  the generator that runs something parallel while driving. Default: No default value
            stop: the boolean that determines whether the robot should stop the motors after driving or not. Default: True
        """

        if cancel:
            return

        global run_generator, runSmall

        if generator == None:
            run_generator = False

        if rotate_mode == 0:
            startspeed = abs(startspeed)
            maxspeed = abs(maxspeed)
            endspeed = abs(endspeed)

        speed = startspeed

        #set standard variables
        rotatedDistance = 0
        steering = 1

        accelerateDistance = abs(angle * addspeed) 
        deccelerateDistance = abs(angle * (1 - brakeStart))

        #gyro sensor calibration
        angle = angle * (2400/2443) #experimental value based on 20 rotations of the robot

        #Setting variables based on inputs
        loop = True
        gyroStartValue = getGyroValue() #Yaw angle used due to orientation of the self.hub. This might need to be changed
        brakeStartValue = (angle + gyroStartValue) * brakeStart

        #Inversion of steering value for turning counter clockwise
        if angle < 0:
            steering = -1

        #Testing to see if turining is necessary, turns until loop = False

        while loop:
            if cancel:
                break

            if run_generator: #run parallel code execution
                next(generator)

            oldRotatedDistance = rotatedDistance
            rotatedDistance = getGyroValue() #Yaw angle used due to orientation of the self.hub. This might need to be changed
            speed = speedCalculation(speed, startspeed, maxspeed, endspeed, accelerateDistance, deccelerateDistance, brakeStartValue, abs(1), abs(0))
            
            
            #Checking for variants
            #Both Motors turn, robot moves on the spot
            if rotate_mode == 0:
                self.movement_motors.start_tank_at_power(int(speed) * steering, -int(speed) * steering)
            #Only outer motor turns, robot has a wide turning radius
            
            elif rotate_mode == 1:

                if angle * speed > 0:
                    self.leftMotor.start_at_power(- int(speed))
                else:
                    self.rightMotor.start_at_power(int(speed))

            if stopMethod != None:
                if stopMethod.loop():
                    loop = False
            else:
                if abs(angle) <= abs(rotatedDistance - gyroStartValue):
                    loop = False

        if stop:
            self.movement_motors.stop()

        run_generator = True
        runSmall = True
        generator = 0
        return

    def gyroStraightDrive(self, distance, startspeed, maxspeed, endspeed, addspeed = 0.2, brakeStart = 0.7, offset = 0, stopMethod = None, generator = None, stop = True):
        """
            This is the function that we use to make the robot drive in a straight line. This is done by using the gyrosensor to correct the course of the robot. This function
            also has linear acceleration and breaking. It also uses PID values that are automatically set depending on the current speed of the robot (See function PIDCalculation)
            Parameters
            -------------
            distance: The value tells the program the distance the robot has to drive. Type: Integer. Default: No default value
            startspeed: The speed which the robot is supposed to start at. Type: Integer. Default: No default value
            maxspeed: The highest speed at which the robot drives. Type: Integer. Default: No default value
            endspeed: The speed which the robot achieves at the end of the function. Type: Integer. Default: No default value
            addspeed: The percentage after which the robot reaches its maxspeed. Type: Float. Default: No default value
            brakeStart: The value which we use to tell the robot after what percentage of the distance we need to slow down. Type: Float. Default: No default value
            offset: The value which we use to tell the robot to drive at an angle instead of straight. Type: Integer. Default: 0
            stopMethod: the Stopmethod the robot uses to stop. If no stopMethod is passed stopDistance is used instead. Default: stopDistance
            generator:  the generator that runs something parallel while driving. Default: No default value
            stop: the boolean that determines whether the robot should stop the motors after driving or not. Default: True
        """

        if cancel:
            return

        global run_generator, runSmall

        if generator == None:
            run_generator = False

        #set standard variables
        speed = startspeed
        change = 0
        old_change = 0
        integral = 0
        #Get degrees of motors turned before robot has moved, allows for distance calculation without resetting motors
        self.left_Startvalue = self.leftMotor.get_degrees_counted()
        self.right_Startvalue = self.rightMotor.get_degrees_counted()
        #Going backwards is not supported on our robot due to the motors then being in front of the colour sensors and the program not working
        if distance < 0:
            print("ERR: distance < 0")
            distance = abs(distance)
        #Calculate target values for the motors to turn to
        finalDistance = (distance / 17.6) * 360
        #Calculate after what distance the robot has to reach max speed
        accelerateDistance = finalDistance * addspeed
        deccelerateDistance = finalDistance * (1 - brakeStart)

        #Calculation of the start of the robot slowing down
        brakeStartValue = brakeStart * finalDistance
        #get start value of the gyro sensor
        gyroStartValue = getGyroValue()
        loop = True
        drivenDistance = 0
        #Driving until loop = False
        while loop:
            if cancel:
                break

            if run_generator: #run parallel code execution
                next(generator)

            #Checks the driven distance as an average of both motors for increased accuracy
            oldDrivenDistance = drivenDistance
            drivenDistance = getDrivenDistance(self)
            #Calculates target value for Robot as the edge of black and white lines
            old_change = change
            change = getGyroValue() - gyroStartValue - offset
            #Steering factor calculation using PID, sets new I value
            integral = change + integral
            steering = (change * pRegler) + (integral * iRegler) + (dRegler * (change - old_change))
            #Calculation of current speed for robot, used for acceleratiion, braking etc.
            speed = speedCalculation(speed, startspeed, maxspeed, endspeed, accelerateDistance, deccelerateDistance, brakeStartValue, drivenDistance, oldDrivenDistance)
            pidCalculation(speed)
            #Driving using speed values calculated with PID and acceleration for steering, use of distance check
            self.movement_motors.start_at_power(int(speed), int(steering))

            if stopMethod != None:
                if stopMethod.loop():
                    loop = False
            else:
                if finalDistance < drivenDistance:
                    loop = False

        if stop:
            self.movement_motors.stop()

        run_generator = True
        runSmall = True
        generator = 0
        return

    def arcRotation(self, radius, angle, startspeed, maxspeed, endspeed, addspeed = 0.2, brakeStart = 0.7, stopMethod = None, generator = None, stop = True):
        """
            This is the function that we use to make the robot drive in a large arc. This is done by using the gyrosensor to correct the course of the robot. This function
            also has linear acceleration and breaking. It also uses PID values that are automatically set depending on the current speed of the robot (See function PIDCalculation)
            Parameters
            -------------
            radius: The radius of the arc the robot is supposed to drive in. Type: Integer. Default: No default value
            angle: The angle which the robot is supposed to turn. Use negative numbers to turn counterclockwise. Type: Integer. Default value: No default value
            startspeed: The speed which the robot is supposed to start at. Type: Integer. Default: No default value
            maxspeed: The highest speed at which the robot drives. Type: Integer. Default: No default value
            endspeed: The speed which the robot achieves at the end of the function. Type: Integer. Default: No default value
            addspeed: The percentage after which the robot reaches its maxspeed. Type: Float. Default: No default value
            brakeStart: The value which we use to tell the robot after what percentage of the distance we need to slow down. Type: Float. Default: No default value
            stopMethod: the Stopmethod the robot uses to stop. If no stopMethod is passed stopDistance is used instead. Default: stopDistance
            generator:  the generator that runs something parallel while driving. Default: No default value
            stop: the boolean that determines whether the robot should stop the motors after driving or not. Default: True
        """

        if cancel:
            return

        global run_generator, runSmall

        if generator == None:
            run_generator = False

        #set standard variables
        speed = startspeed
        change = 0
        old_change = 0
        integral = 0
        #Get degrees of motors turned before robot has moved, allows for distance calculation without resetting motors
        self.left_Startvalue = self.leftMotor.get_degrees_counted()
        self.right_Startvalue = self.rightMotor.get_degrees_counted()
        #Going backwards is not supported on our robot due to the motors then being in front of the colour sensors and the program not working
        if angle < 0:
            print("ERR: angle < 0")
            angle = abs(angle)
        #Calculate target values for the motors to turn to
        finalDistance = (angle / 17.6) * 360
        #Calculate after what distance the robot has to reach max speed
        accelerateDistance = finalDistance * addspeed
        deccelerateDistance = finalDistance * (1 - brakeStart)

        #Calculation of the start of the robot slowing down
        brakeStartValue = brakeStart * finalDistance
        #get start value of the gyro sensor
        gyroStartValue = getGyroValue()
        loop = True
        drivenDistance = 0
        #Driving until loop = False
        while loop:
            if cancel:
                break

            if run_generator: #run parallel code execution
                next(generator)

            #Checks the driven distance as an average of both motors for increased accuracy
            oldDrivenDistance = drivenDistance
            drivenDistance = getDrivenDistance(self)
            #Calculates target value for Robot as the edge of black and white lines
            old_change = change
            change = getGyroValue() - gyroStartValue
            #Steering factor calculation using PID, sets new I value
            integral = change + integral
            steering = (change * pRegler) + (integral * iRegler) + (dRegler * (change - old_change))
            #Calculation of current speed for robot, used for acceleratiion, braking etc.
            speed = speedCalculation(speed, startspeed, maxspeed, endspeed, accelerateDistance, deccelerateDistance, brakeStartValue, drivenDistance, oldDrivenDistance)
            pidCalculation(speed)
            #Driving using speed values calculated with PID and acceleration for steering, use of distance check
            self.movement_motors.start_at_power(int(speed), int(steering))

            if stopMethod != None:
                if stopMethod.loop():
                    loop = False
            else:
                if finalDistance < drivenDistance:
                    loop = False

        if stop:
            self.movement_motors.stop()

        run_generator = True
        runSmall = True
        generator = 0
        return


def getGyroValue():
    global gyroValue, lastAngle, oldAngle

    #This function makes sure that the gyro value doesn't reset when it reaches 180 degrees
    angle = hub2.motion.yaw_pitch_roll(True)[0]

    if lastAngle > 150 and angle < -150:
        gyroValue += 360
    elif lastAngle < -150 and angle > 150:
        gyroValue -= 360   
        angle = 0

    return gyroValue + angle

def getDrivenDistance(data):


    #print(str(abs(data.leftMotor.get_degrees_counted() - data.left_Startvalue)) + " .:. " + str(abs(data.rightMotor.get_degrees_counted() - data.right_Startvalue)))

    drivenDistance = (                    abs(data.leftMotor.get_degrees_counted() - data.left_Startvalue) +                     abs(data.rightMotor.get_degrees_counted() - data.right_Startvalue)) / 2

    return drivenDistance

def defaultClass(object, db):
    object.db = db
    object.leftMotor = db.leftMotor
    object.rightMotor = db.rightMotor

    object.left_Startvalue = abs(db.leftMotor.get_degrees_counted())
    object.right_Startvalue = abs(db.rightMotor.get_degrees_counted())
    return object

class stopMethods(): #This class has all our stopmethods for easier coding and less redundancy
    
    class stopLine():
        """
            Drive until a Line is detected
            Parameters
            -------------
            db: the drivebase of the robot
            port: Port to detect line on
            lightvalue: Value of the light to detect
            detectLineDistance: Distance until start detecting a line
            """
        def __init__(self, db, port, lightvalue, detectLineDistance):
            self = defaultClass(self, db)            

            self.port = port
            self.detectLineDistance = (detectLineDistance / 17.6) * 360

            #if lightvalue bigger 50 stop when lightvalue is higher
            self.lightvalue = lightvalue


        def loop(self):


            drivenDistance = getDrivenDistance(self)

            if abs(self.detectLineDistance) < abs(drivenDistance):
                if self.lightvalue > 50:
                    if ColorSensor(self.port).get_reflected_light() > self.lightvalue:
                        return True
                else:
                    if ColorSensor(self.port).get_reflected_light() < self.lightvalue:
                        return True

            return False
    
    class stopAlign():
        """
            Drive until a Line is detected
            Parameters
            -------------
            db: the drivebase of the robot
            port: Port to detect line on
            lightvalue: Value of the light to detect
            speed: speed at which the robot searches for other line
            """
        def __init__(self, db, lightvalue, speed):
            self = defaultClass(self, db)    
            self.speed = speed


            #if lightvalue bigger 50 stop when lightvalue is higher
            self.lightValue = lightvalue


        def loop(self):

            if colorE.get_reflected_light() < self.lightValue:
                self.rightMotor.stop()
                #Turning robot so that other colour sensor is over line
                while True:

                    self.leftMotor.start_at_power(-int(self.speed))

                    #Line detection and stopping
                    if colorF.get_reflected_light() < self.lightValue or cancel:
                        self.leftMotor.stop()
                        return True
                

            #Colour sensor F sees line first
            elif colorF.get_reflected_light() < self.lightValue:

                self.leftMotor.stop()

                #Turning robot so that other colour sensor is over line
                while True:
                    self.rightMotor.start_at_power(int(self.speed))

                    #Line detection and stopping
                    if colorE.get_reflected_light() < self.lightValue or cancel:
                        self.rightMotor.stop()
                        return True
            

            return False

    class stopTangens():
        """
            Drive until a Line is detected
            Parameters
            -------------
            db: the drivebase of the robot
            port: Port to detect line on
            lightvalue: Value of the light to detect
            speed: Distance until start detecting a line
            """
        def __init__(self, db, lightvalue, speed):
            self.count = 0
            self = defaultClass(self, db)    
            self.speed = speed
            #if lightvalue bigger 50 stop when lightvalue is higher
            self.lightValue = lightvalue
            self.detectedLineDistance = 0

            self.invert = 1
            if speed < 0:
                self.invert = -1
            
        def loop(self):
            drivenDistance = getDrivenDistance(self)
            if colorE.get_reflected_light() < self.lightValue:
                    #measuring the distance the robot has driven since it has seen the line
                    if(self.detectedLineDistance == 0):
                        self.detectedLineDistance = getDrivenDistance(self)
                        self.detectedPort = 'E'

                    elif self.detectedPort == 'F':
                        db.movement_motors.stop() #Stops robot with sensor F on the line
                        angle = math.degrees(math.atan(((drivenDistance - self.detectedLineDistance) / 360 * circumference) / sensordistance)) #Calculating angle that needs to be turned using tangent
                        #print("angle: " + str(angle))
                        db.gyroRotation(-angle, self.invert * self.speed, self.invert * self.speed, self.invert * self.speed, rotate_mode=1) #Standard gyrorotation for alignment, but inverting speed values if necessary

                        db.movement_motors.stop() #Stopping robot for increased reliability
                        return True

                #Colour sensor F sees line first
            elif colorF.get_reflected_light() < self.lightValue:
                #measuring the distnace the robot has driven since it has seen the line
                if(self.detectedLineDistance == 0):
                    self.detectedLineDistance = drivenDistance
                    self.detectedPort = 'F'

                elif self.detectedPort == 'E':
                    db.movement_motors.stop() #Stops robot with sensor E on the line
                    angle = math.degrees(math.atan(((drivenDistance - self.detectedLineDistance) / 360 * circumference) / sensordistance)) #Calculation angle that needs to be turned using tangent
                    db.gyroRotation(angle, self.invert * self.speed, self.invert * self.speed, self.invert * self.speed, rotate_mode=1) #Standard gyrorotation for alignment, but inverting speed values if necessary
                    db.movement_motors.stop() #Stopping robot for increased reliablity
                    return True

            return False
    class stopDegree():
        """
            Roates until a certain degree is reached
            Parameters            
            -------------
            db: the drivebase of the robot
            angle: the angle to rotate
        """
        def __init__(self, db, angle):
            self.angle = angle * (336/360)
            
            self.gyroStartValue = getGyroValue() #Yaw angle used due to orientation of the self.hub.
            

        def loop(self):
            rotatedDistance = getGyroValue() #Yaw angle used due to orientation of the self.hub. 

            if abs(self.angle) <= abs(rotatedDistance - self.gyroStartValue):
                return True
            else:
                return False

    class stopTime():

        """
            Drive until a certain time is reached
            Parameters
            -------------
            db: the drivebase of the robot
            time: the time to drive
        """

        def __init__(self, db, time) -> None:
            self = defaultClass(self, db)
            self.time = time
            self.timer = Timer()
            self.startTime = self.timer.now()

        def loop(self):
            if self.timer.now() > self.startTime + self.time:
                return True
            else:
                return False
       
    class stopResistance():

        """
            Drive until the Robot doesn't move anymore
            Parameters
            -------------
            db: the drivebase of the robot
            restistance: the value the resistance has to be below to stop              
        """
        def __init__(self, db, resistance):
            self = defaultClass(self, db)
            self.resistance = resistance
            self.timer = Timer()
            
            self.startTime = 0
            self.lower = False
            self.runs = 0

        def loop(self):

            self.runs += 1
            motion = abs(hub2.motion.accelerometer(True)[2])

            if motion < self.resistance:
                self.lower = True

            if self.runs > 15:
                if self.lower:
                    if self.startTime == 0:
                        self.startTime = self.timer.now()

                    if self.timer.now() > self.startTime:
                        return True

                else:
                    self.lower = False
                    return False
                
def motorResistance(speed, port, resistancevalue):
    """
    lets the motor stop when it hits an obstacle
    """
    if abs(resistancevalue) > abs(speed):
        return

        
    if cancel:
        return

    if port == "A":
        smallMotorA.start_at_power(speed)
        while True:
            old_position = smallMotorA.get_position()
            wait_for_seconds(0.4)
            if abs(old_position - smallMotorA.get_position())<resistancevalue or cancel:
                smallMotorA.stop()
                print("detected stalling")
                return

    elif port == "D":
        smallMotorD.start_at_power(speed)
        while True:
            old_position = smallMotorD.get_position()
            wait_for_seconds(0.4)
            if abs(old_position - smallMotorD.get_position())<resistancevalue or cancel:
                smallMotorD.stop()
                print("detected stalling")
                return
    else:
        print("wrong port selected. Select A or D")
        return

def speedCalculation(speed, startspeed, maxspeed, endspeed, accelerateDistance, deccelerateDistance, brakeStartValue, drivenDistance, oldDrivenDistance):
    """
        Used to calculate all the speeds in out programs. Done seperatly to reduce redundancy. Brakes and accelerates
        Parameters
        -------------
        speed: The current speed the robot has
        startspeed: Speed the robot starts at. Type: Integer. Default: No default value.
        maxspeed: The maximum speed the robot reaches. Type: Integer. Default: No default value.
        endspeed: Speed the robot aims for while braking, minimum speed at the end of the program. Type: Integer. Default: No default value.
        addspeed: Percentage of the distance after which the robot reaches the maximum speed. Type: Integer. Default: No default value.
        brakeStartValue: Percentage of the driven distance after which the robot starts braking. Type: Integer. Default: No default value.
        drivenDistance: Calculation of the driven distance in degrees. Type: Integer. Default: No default value.
    """    

    addSpeedPerDegree = (maxspeed - startspeed) / accelerateDistance 
    subSpeedPerDegree = (maxspeed - endspeed) / deccelerateDistance
    

    subtraction = (abs(drivenDistance) - abs(oldDrivenDistance) if abs(drivenDistance) - abs(oldDrivenDistance) >= 1 else 1) * subSpeedPerDegree
    addition = (abs(drivenDistance) - abs(oldDrivenDistance) if abs(drivenDistance) - abs(oldDrivenDistance) >= 1 else 1) * addSpeedPerDegree

    if abs(drivenDistance) > abs(brakeStartValue):

        if abs(speed) > abs(endspeed):
            speed = speed - subtraction
            
    elif abs(speed) < abs(maxspeed):

        speed = speed + addition

    return speed

def breakFunction(args):
    """
    Allows you to manually stop currently executing round but still stays in main. 
    This is much quicker and more reliable than pressing the center button.
    """
    global cancel, inMain
    if not inMain:
        cancel = True

def pidCalculation(speed):
    #golbally sets PID values based on current speed of the robot, allows for fast and accurate driving
    global pRegler
    global iRegler
    global dRegler
    #Important note: These PID values are experimental and based on our design for the robot. You will need to adjust them manually. You can also set them statically as you can see below
    if speed > 0:
        pRegler = -0.17 * speed + 12.83
        iRegler = 12
        dRegler = 1.94 * speed - 51.9
        if pRegler < 3.2:
            pRegler = 3.2
    else:
        pRegler = (11.1 * abs(speed))/(0.5 * abs(speed) -7) - 20
        iRegler = 10
        #iRegler = 0.02
        dRegler = 1.15**(- abs(speed)+49) + 88
    
def pidCalculationLight(speed):
    #Sets the PID values for the lineFollower based on current speed. Allows for accurate and fast driving
    #Important note: these PID values are experimental and based on our design for the robot. You will need to adjust them. See above on how to do so
    global pReglerLight
    global dReglerLight

    pReglerLight = -0.04 * speed + 4.11
    dReglerLight = 0.98 * speed - 34.2
    #set hard bottom for d value, as otherwise the values don't work
    if dReglerLight < 5:
        dReglerLight = 5

def driveMotor(rotations, speed, port):
    """
    Allows you to drive a small motor in parallel to driving with gyroStraightDrive
    Parameters
    -------------
    rotations: the rotations the motor turns
    speed: the speed at which the motor turns
    port: the motor used. Note: this cannot be the same motors as configured in the motor Drivebase
    """
           
    global runSmall
    global run_generator

    if cancel:
        runSmall = False
        run_generator = False

    while runSmall:
        smallMotor = Motor(port)
        smallMotor.set_degrees_counted(0)

        loop_small = True
        while loop_small:
            drivenDistance = smallMotor.get_degrees_counted()
            smallMotor.start_at_power(speed)
            if (abs(drivenDistance) > abs(rotations) * 360):
                loop_small = False
            if cancel:
                loop_small = False
            yield

        smallMotor.stop()
        runSmall = False
        run_generator = False
    yield

hub2.motion.yaw_pitch_roll(0)

db = DriveBase(hub, 'B', 'C') #this lets us conveniently hand over our motors (B: left driver; C: right driver). This is necessary for the cancel function

def exampleOne():
    #This example aims to show all the options for following a line. See the specific documentation of the function for further information.
    db.lineFollower(15, 25, 35, 25, 'E', 'left') #follows the left side of a line on the E sensor for 15cm. Accelerates from speed 25 to 35 and ends on 25 again
    hub.left_button.wait_until_pressed()
    db.lineFollower(15, 25, 35, 25, 'E', 'left', 0.4, 0.6) #same line follower as before but with a longer acceleration and breaking period
    hub.left_button.wait_until_pressed()
    db.lineFollower(15, 25, 35, 25, 'E', 'left', stopMethod=stopMethods.stopLine(db, 'F', 0.7)) #same linefollower as the first, but this time stopping, when the other sensor sees a black line after at least 70% of the driven distance
    hub.left_button.wait_until_pressed()
    db.lineFollower(15, 25, 35, 25, 'E', 'left', stopMethod=stopMethods.stopResistance(db, 20)) #same as first linefollower, but stops when desired resistance is reached. Test the resistance value based on your robot
    hub.left_button.wait_until_pressed()
    generator = driveMotor(5, 100, 'A')
    db.lineFollower(15, 25, 35, 25, 'E', 'left', generator=generator) #same as first linefollower, but drives while turning the A-Motor for 5 rotations
    hub.left_button.wait_until_pressed()
    db.lineFollower(15, 25, 35, 25, 'E', 'left', stop=False) #same as first linefollower, but does not actively brake the motors. The transistion form this action to the next is then smoother
    return

def exampleTwo():
    #This example aims to show all the options for turning the robot. See the specific documentation of the function for further information.
    db.gyroRotation(90, 25, 35, 25) #turns the robot 90° clockwise while accelerating from speed 25 to 35 and back down to 25
    hub.left_button.wait_until_pressed()
    db.gyroRotation(90, 25, 35, 25, 0.4, 0.5) #same turning as in first rotation but with longer acceleration/braking phase
    hub.left_button.wait_until_pressed()
    db.gyroRotation(90, 25, 35, 25, rotate_mode=1) #same turn as in first rotation but this time turning using only one wheel rather than turning on the spot. Your speeds may need to be higher for this
    hub.left_button.wait_until_pressed()
    db.gyroRotation(90, 25, 35, 25, stopMethod=stopMethods.stopAlign(db, 25, 25)) #aligns the robot with a line in turning path
    hub.left_button.wait_until_pressed()
    db.gyroRotation(90, 25, 35, 25, stopMethod=stopMethods.stopLine(db, 'E', 25, 0.7)) #turns until the robot sees a line on sensor E after at least 70% of turning
    hub.left_button.wait_until_pressed()
    db.gyroRotation(90, 25, 35, 25, stopMethod=stopMethods.stopTangens(db, 25, 25)) #aligns the robot like stopAlign but is a bit more precise
    hub.left_button.wait_until_pressed()
    #remaining parameters are the same as in linefollower. Please refer to exampleOne or the documentation of the individual functions
    return

def exampleThree():
    #This example aims to show all the options for driving in a straight line. See the specific documentation of the function for further information.
    db.gyroStraightDrive(30, 25, 35, 25) #drives in a straight line for 30cm
    hub.left_button.wait_until_pressed()
    db.gyroStraightDrive(30, 25, 55, 25, 0.1, 0.9) #same as first drive, but faster and with harder acceleration/braking
    hub.left_button.wait_until_pressed()
    db.gyroStraightDrive(30, 25, 35, 25, offset=15) #same as first drive, but aims 15° in clockwise direction as target orientation
    #remaining features of code are explained in previous examples. Please refer to exampleOne, exampleTwo and additional documentation within individual functions
    return

def exampleFour():
    #This example aims to show all the options for turning in a large curve. See the specific documentation of the function for further information.
    db.arcRotation(5, 35, 25, 30, 25) #robot drives 35° on a circle with a radius of 5cm measured from the inside edge of the robot
    #remaining features of code are explained in previous examples. Please refer to exampleOne, exampleTwo and additional documentation within individual functions
    return
    
def exampleFive():
    #add your own code here
    
    
    return

def exampleSix():
    #add your own code here
    
    
    return

class bcolors:
    BATTERY = '\033[32m'
    BATTERY_LOW = '\033[31m'

    ENDC = '\033[0m'

pReglerLight = 1.6
iReglerLight = 0.009
dReglerLight = 16

accelerate = True

hub2.button.right.callback(breakFunction)
gyroValue = 0


#Battery voltage printout in console for monitoring charge
if battery.voltage() < 8000: #set threshold for battery level
    print(bcolors.BATTERY_LOW + "battery voltage is too low: " + str(battery.voltage()) + " \n ----------------------------- \n >>>> please charge robot <<<< \n ----------------------------- \n"+ bcolors.ENDC)
else:
    print(bcolors.BATTERY + "battery voltage: " + str(battery.voltage()) + bcolors.ENDC)


#User Interface in Program for competition and instant program loading
main = True


programselect = 1 #Set the attachment the selection program starts on
hub.light_matrix.write(programselect)
db.movement_motors.set_stop_action("hold") #hold motors on wait for increased reliability



while main:
    cancel = False
    inMain = True

    #Program selection
    
    if hub.right_button.is_pressed(): #press right button to cycle through programs. cycling back isn't supported yet, but we are working on reallocating the buttons in the file system
        wait_for_seconds(0.15) #waiting prevents a single button press to be registered as multiple clicks
        programselect = programselect + 1
        hub.light_matrix.write(programselect) #show current selcted program
        hub.speaker.beep(85, 0.1) #give audio feedback for user

        if programselect == 1:
            hub.status_light.on('blue')
        elif programselect == 2:
            hub.status_light.on('black')
        elif programselect == 3:
            hub.status_light.on('white')
        elif programselect == 4:
            hub.status_light.on('white')
        elif programselect == 5:
            hub.status_light.on('red')        
        elif programselect == 6:
            hub.status_light.on('orange')
        #cycle to start of stack
        if programselect == 7:
            programselect = 1
            hub.light_matrix.write(programselect)
            hub.status_light.on('blue')

    #Program start
    if hub.left_button.is_pressed():
        inMain = False

        if programselect == 1:
            hub.status_light.on("blue")
            hub.light_matrix.show_image("DUCK")
            exampleOne()
            programselect = 2
            hub.light_matrix.write(programselect)
        elif programselect == 2:
            hub.status_light.on("black")
            hub.light_matrix.show_image("DUCK")
            exampleTwo()
            programselect = 3
            hub.light_matrix.write(programselect)
        elif programselect == 3:
            hub.status_light.on("white")
            hub.light_matrix.show_image("DUCK")
            exampleThree()
            programselect = 5
            hub.light_matrix.write(programselect)
        elif programselect == 4:
            hub.status_light.on('white')
            hub.light_matrix.show_image('DUCK')
            exampleFour()
            programselect = 5
            hub.light_matrix.write(programselect)
        elif programselect == 5:
            hub.status_light.on("red")
            hub.light_matrix.show_image("DUCK")
            exampleFive()
            programselect = 6
            hub.light_matrix.write(programselect)
        elif programselect == 6:
            hub.status_light.on("orange")
            hub.light_matrix.show_image("DUCK")
            exampleSix()
            programselect = 1
            hub.light_matrix.write(programselect)


sys.exit("ended program successfully")

```

### Warnings/Dead Ends
The README.md file explicitly states that the program utilizes 'unofficial and undocumented APIs' which are subject to change without prior notice. The PID values are experimental and require manual adjustment for different robot designs. The gyroRotation function contains a hardcoded calibration value (2400/2443). The functions have been tested on Windows 10/11.

## GianCann/SpikePrimeHub
- Relevance: 5/10
- Connection: BLE
- Firmware: Unknown
### Technical Details
The repository provides a MicroPython script that demonstrates BLE central role capabilities. The script uses the `ubluetooth` library to scan for and connect to BLE peripherals. It does not contain any hardcoded UUIDs, but rather discovers services and characteristics dynamically. The script writes to handle `0x0B` and `0x0C`. The payload is constructed using `struct.pack`. For example, `ar  =struct.pack('<BBBBBBBBBB', 0x0A,0x0,0x41,0x00,0x00,0x01,0x00,0x00,0x00,0x01)`. The README file contains pin mappings for all ports (A-F) of the Spike Prime Hub.

### Reusable/Actionable
The python script `central-role-with-notify.py` is a good reference for understanding the basic BLE central role functionality on the SPIKE Prime hub using MicroPython. It demonstrates how to scan for peripherals, connect to a device, and handle BLE events using the `ubluetooth` library. The `bt_irq` function provides a clear example of an event handler for different BLE events like `_IRQ_SCAN_RESULT`, `_IRQ_PERIPHERAL_CONNECT`, etc. The `adv_decode_name` function is a practical example of parsing BLE advertisement data to extract the device name.

### Hub Python Code
```python
import ubluetooth 
#	import BLE, UUID, FLAG_NOTIFY, FLAG_READ, FLAG_WRITE
import	micropython
#	import const
import 	utime
import struct 

_IRQ_CENTRAL_CONNECT                 = const(1 << 0)
_IRQ_CENTRAL_DISCONNECT              = const(1 << 1)
_IRQ_GATTS_WRITE                     = const(1 << 2)
_IRQ_GATTS_READ_REQUEST              = const(1 << 3)
_IRQ_SCAN_RESULT                     = const(1 << 4)
_IRQ_SCAN_COMPLETE                   = const(1 << 5)
_IRQ_PERIPHERAL_CONNECT              = const(1 << 6)
_IRQ_PERIPHERAL_DISCONNECT           = const(1 << 7)
_IRQ_GATTC_SERVICE_RESULT            = const(1 << 8)
_IRQ_GATTC_CHARACTERISTIC_RESULT     = const(1 << 9)
_IRQ_GATTC_DESCRIPTOR_RESULT         = const(1 << 10)
_IRQ_GATTC_READ_RESULT               = const(1 << 11)
_IRQ_GATTC_WRITE_STATUS              = const(1 << 12)
_IRQ_GATTC_NOTIFY                    = const(1 << 13)
_IRQ_GATTC_INDICATE                  = const(1 << 14)
send_hs = 0
state_c = 0
_COMMAND_1                           = const(1 << 0)
_COMMAND_2                           = const(1 << 1)
_COMMAND_3                           = const(1 << 2)
_COMMAND_4                           = const(1 << 3)
_COMMAND_5                           = const(1 << 4)
_COMMAND_6                           = const(1 << 5)
_COMMAND_7                           = const(1 << 6)

def adv_decode(adv_type, data):
    i = 0
    while i + 1 < len(data):
        if data[i + 1] == adv_type:
            return data[i + 2:i + data[i] + 1]
        i += 1 + data[i]
    return None

def adv_decode_name(data):
    n = adv_decode(0x09, data)
    if n:
        return n.decode('utf-8')
    return data

def bt_irq(event, data):
  global state_c    
  global send_hs    
  if event == _IRQ_SCAN_RESULT:
    print('event == _IRQ_SCAN_RESULT')    
    print('scan --> addr_type, addr, connectable, rssi, adv_data = data')
    # A single scan result.
    addr_type, addr, connectable, rssi, adv_data = data
    print(addr_type, addr, adv_decode_name(adv_data))
  elif event == _IRQ_SCAN_COMPLETE:
    print('event == _IRQ_SCAN_RESULT')    
    # Scan duration finished or manually stopped.
    print('scan complete')
    
  elif event == _IRQ_PERIPHERAL_CONNECT:
    print('event == _IRQ_PERIPHERAL_CONNECT')    
    # A successful gap_connect().
    conn_handle, addr_type, addr = data
    print(conn_handle, addr_type,addr)
    #bt.gattc_discover_services(conn_handle)
    
    ar  =struct.pack('<BBBBBBBBBB', 0x0A,0x0,0x41,0x00,0x00,0x01,0x00,0x00,0x00,0x01)
    bt.gattc_write(conn_handle,0x0B,ar,1)
    utime.sleep(2)
    print('aaaaaa complete..0x0B write 0x0A,0x0,0x41,0x00,0x00,0x01,0x00,0x00,0x00,0x01')
    
    al  =struct.pack('<BBBBBBBBBB', 0x0A,0x0,0x41,0x01,0x00,0x01,0x00,0x00,0x00,0x01)   
    bt.gattc_write(conn_handle,0x0B,al,1)
    utime.sleep(2)
    print('aaaaaa complete..0x0B  write 0x0A,0x0,0x41,0x01,0x00,0x01,0x00,0x00,0x00,0x01')
    
    bt.gattc_write(conn_handle,0x0C,struct.pack('<BB',0x01,0x00),1)
    print('connect complete.activate notifications.write Handel 0x0c data  0100... starting write')
    utime.sleep(2)
    
  elif event == _IRQ_PERIPHERAL_DISCONNECT:
    print('event == _IRQ_PERIPHERAL_DISCONNECT')    
    # Connected peripheral has disconnected.
    conn_handle, addr_type, addr = data
  elif event == _IRQ_GATTC_SERVICE_RESULT:
    print('event == _IRQ_GATTC_SERVICE_RESULT')    
    # Called for each service found by gattc_discover_services().
    conn_handle, start_handle, end_handle, uuid = data
    print(conn_handle, start_handle, end_handle, uuid) 
    print(str(end_handle) + ' check_end_handle')
    #bt.gattc_discover_characteristics(conn_handle, start_handle, end_handle)
    #bt.gattc_discover_descriptors(conn_handle,start_handle,end_handle)
    print('discover service...')
  elif event == _IRQ_GATTC_CHARACTERISTIC_RESULT:
    print('event == IRQ_GATTC_CHARACTERISTIC_RESULT')        
    # Called for each characteristic found by gattc_discover_services().
    conn_handle, def_handle, value_handle, properties, uuid = data
    print(conn_handle, def_handle, value_handle, properties, uuid)
    print('discover char ...')
  elif event == _IRQ_GATTC_DESCRIPTOR_RESULT:
    print('event == _IRQ_GATTC_DESCRIPTOR_RESULT')
    # Called for each descriptor found by gattc_discover_descriptors().
    conn_handle, dsc_handle, uuid = data
    print(conn_handle, dsc_handle, uuid)
    print('discover descript  ...')
  elif event == _IRQ_GATTC_READ_RESULT:
    print('event == _IRQ_GATTC_READ_RESULT')    
    # A gattc_read() has completed.
    conn_handle, value_handle, char_data = data
  elif event == _IRQ_GATTC_WRITE_STATUS:
    print('event == _IRQ_GATTC_WRITE_STATUS')
    # A gattc_write() has completed.
    print (str(send_hs))
    send_hs = 0
    conn_handle, value_handle, status = data
  elif event == _IRQ_GATTC_NOTIFY:
    print('event == _IRQ_GATTC_NOTIFY')
    # A peripheral has sent a notify request.
    conn_handle, value_handle, notify_data = data
    #print(value_handle)
    print(notify_data)
  elif event == _IRQ_GATTC_INDICATE:
    print('event == _IRQ_GATTC_INDICATE')    
    # A peripheral has sent an indicate request.
    conn_handle, value_handle, notify_data = data

# Scan for 10s (at 100% duty cycle)

bt = ubluetooth.BLE()
bt.active(True)
bt.irq(handler=bt_irq)

# Scan for 10s (at 100% duty cycle)
#bt.gap_scan(10000, 30000, 30000)

#Connect to specific BLE Adress
#bt.gap_connect(0,b'\xa4\x34\xf1\x9b\x07\x9e',2000)

```

## faisaltameesh/spikerc
- Relevance: 5/10
- Connection: BLE
- Firmware: SPIKE_2x
### Technical Details
This project uses BLE for communication, implementing the standard Nordic UART Service. It does not use COBS encoding, CRC32, or the LEGO-specific TunnelMessage protocol. Communication is intended to be simple, direct messages over the UART characteristics.

- **Connection Type**: BLE
- **Firmware**: SPIKE 2.x (as it requires the V2 app and Python support that was not in V3 at the time).
- **BLE Service UUID**: `6E400001-B5A3-F393-E0A9-E50E24DCCA9E` (UART_SERVICE_UUID)
- **BLE Characteristic UUIDs**:
  - `6E400002-B5A3-F393-E0A9-E50E24DCCA9E` (UART_RX_CHAR_UUID - for writing data to the hub)
  - `6E400003-B5A3-F393-E0A9-E50E24DCCA9E` (UART_TX_CHAR_UUID - for reading data from the hub)
- **Motor Control**: The hub code is set up to receive single-byte commands (e.g., 'f', 'b', 'l', 'r', 's') to control the motors. However, the provided client code sends two-byte signed integer values for motor power, creating a mismatch.
- **Sensor Reading**: No sensor data is read or transmitted.
- **Hub Python Code**: The file `robot_python_code.py` contains the full MicroPython script to be run on the SPIKE hub.

### Reusable/Actionable
The project provides a clear, albeit non-functional, example of using the standard Nordic UART service for basic remote control. The `BLESimplePeripheral` and `BLEUART` classes in `robot_python_code.py` are a good, simple starting point for understanding how to set up a BLE peripheral on the SPIKE hub using MicroPython. It demonstrates the basic structure of advertising, handling connections, and receiving data. The use of the `bleak` library on the client-side is also a useful reference for BLE communication in Python.

### Hub Python Code
```python
# Note from Faisal - this code is almost entirely stolen from Anton's Mindstorms & Ste7an
# Their copyright is below.
# Made some mods here and there to control from Windows. Originally code was meant to
# control from another Spike brick.

# Don't use V3 of the Spike Prime app for now, run on V2.
# (You might have to downgrade if you already upgraded to V3. There's a tool to do that here):
# https://spikelegacy.legoeducation.com/hubdowngrade/#step-1
# Read the instructions carefully -> you'll need a mac if you have to downgrade the brick.


# This is the car code for the remote controlled Tank-like vehicle
# Build one by slapping two motors on the side of your hub.
# (c) 2021 Anton's Mindstorms & Ste7an

# Use with the the remote control tutorial here:
# [url]

# Most of it is library bluetooth code.
# Scroll to line 200 for the core program.

# ===== Move this to a library import someday ===== #
from hub import display, Image, sound
from spike import PrimeHub
import bluetooth
import random
import struct
import time
from time import sleep_ms
from micropython import const
from machine import Timer

_CONNECT_IMAGES = [
    Image('03579:00000:00000:00000:00000'),
    Image('00357:00000:00000:00000:00000'),
    Image('00035:00000:00000:00000:00000'),
    Image('00003:00000:00000:00000:00000'),
    Image('00000:00009:00000:00000:00000'),
    Image('00000:00000:00097:00000:00000'),
    Image('00000:00000:00000:09753:00000'),
    Image('00000:00000:00000:00000:97530'),
    Image('00000:00000:00000:00000:00000'), # center
    Image('97530:00000:00000:00000:00000'),
    Image('00975:30000:00000:00000:00000'),
    Image('00097:53000:00000:00000:00000'),
    Image('00009:75300:00000:00000:00000'),
    Image('00000:97530:00000:00000:00000'),
    Image('00000:00000:97530:00000:00000'),
    Image('00000:00000:00000:97530:00000'),
    Image('00000:00000:00000:00000:97530')
]

_IRQ_CENTRAL_CONNECT = 1
_IRQ_CENTRAL_DISCONNECT = 2

if 'FLAG_INDICATE' in dir(bluetooth):
    # We're on MINDSTORMS Robot Inventor
    # New version of bluetooth
    _IRQ_GATTS_WRITE = 3
else:
    # We're probably on SPIKE Prime
    _IRQ_GATTS_WRITE = 1<<2

_FLAG_READ = const(0x0002)
_FLAG_WRITE_NO_RESPONSE = const(0x0004)
_FLAG_WRITE = const(0x0008)
_FLAG_NOTIFY = const(0x0010)

# Helpers for generating BLE advertising payloads.
def advertising_payload(limited_discoverable=False, br_edr=False, name=None, services=None, appearance=0):
    payload = bytearray()

    def _append(adv_type, value):
        nonlocal payload
        payload += struct.pack("BB", len(value) + 1, adv_type) + value

    _append(0x01, struct.pack("B", (0x01 if limited_discoverable else 0x02) + (0x18 if br_edr else 0x04)))

    if name:
        _append(0x09, name.encode())

    if services:
        for uuid in services:
            b = bytes(uuid)
            if len(b) == 2:
                _append(0x03, b)
            elif len(b) == 4:
                _append(0x05, b)
            elif len(b) == 16:
                _append(0x07, b)

    # See org.bluetooth.characteristic.gap.appearance.xml
    if appearance:
        _append(0x19, struct.pack("<h", appearance))

    return payload

class BLESimplePeripheral:
    def __init__(self, ble, name="mpy-uart"):
        self._ble = ble
        self._ble.active(True)
        self._ble.irq(self._irq)
        self._name = name
        self._connections = set()
        self._write_callback = None
        self._timer = Timer(0)

    def _irq(self, event, data):
        # Track connections so we can send notifications.
        if event == _IRQ_CENTRAL_CONNECT:
            conn_handle, _, _ = data
            self._connections.add(conn_handle)
            self.on_connect(conn_handle)
        elif event == _IRQ_CENTRAL_DISCONNECT:
            conn_handle, _, _ = data
            self._connections.remove(conn_handle)
            # Start advertising again to allow a new connection.
            self.advertise()
            self.on_disconnect(conn_handle)
        elif event == _IRQ_GATTS_WRITE:
            conn_handle, value_handle = data
            if conn_handle in self._connections and self._write_callback:
                self._write_callback(self._ble.gatts_read(value_handle))

    def on_connect(self, conn_handle):
        pass

    def on_disconnect(self, conn_handle):
        pass

    def on_write(self, callback):
        self._write_callback = callback

    def advertise(self, interval_us=500000):
        raise NotImplementedError

    def stop_advertise(self):
        self._ble.gap_advertise(None)

class BLEUART(BLESimplePeripheral):
    def __init__(self, ble, name="mpy-uart", service_uuid="6E400001-B5A3-F393-E0A9-E50E24DCCA9E", rx_uuid="6E400002-B5A3-F393-E0A9-E50E24DCCA9E", tx_uuid="6E400003-B5A3-F393-E0A9-E50E24DCCA9E"):
        super().__init__(ble, name)
        self._UART_UUID = bluetooth.UUID(service_uuid)
        self._RX_CHAR = (bluetooth.UUID(rx_uuid), _FLAG_WRITE,)
        self._TX_CHAR = (bluetooth.UUID(tx_uuid), _FLAG_READ | _FLAG_NOTIFY,)
        self._UART_SERVICE = (self._UART_UUID, (self._TX_CHAR, self._RX_CHAR),)
        ((self._tx_handle, self._rx_handle),) = self._ble.gatts_register_services((self._UART_SERVICE,))

    def advertise(self, interval_us=100000):
        self._ble.gap_advertise(interval_us, adv_data=advertising_payload(name=self._name, services=[self._UART_UUID]))

    def write(self, v):
        self._ble.gatts_write(self._tx_handle, v)
        for conn_handle in self._connections:
            # Notify connected centrals to issue a read.
            self._ble.gatts_notify(conn_handle, self._tx_handle)

# ===== End of library code ===== #

# Now for the program that uses it

hub = PrimeHub()
hub.light_matrix.show_image('HAPPY')

# You can use any name you want, this is the name that will show up on the other device.
# The other device is the one that will be running the uart_example.py code.
uart = BLEUART(bluetooth, name="spike")

# We'll use this to know when the other device has connected to us
def on_connect(conn_handle):
    print("Connected to", conn_handle)
    hub.light_matrix.show_image('HEART')

uart.on_connect = on_connect

# We'll use this to know when the other device has disconnected from us
def on_disconnect(conn_handle):
    print("Disconnected from", conn_handle)
    hub.light_matrix.show_image('SAD')

uart.on_disconnect = on_disconnect

# This is the function that will be called when the other device sends us data
def on_rx(v):
    print("RX", v)
    # if the message is "f", move forward
    if v == b"f":
        hub.motion_sensor.reset_yaw_angle()
        hub.motor_pair.move(0, 'cm', steering=0, speed=100)
    # if the message is "b", move backward
    elif v == b"b":
        hub.motion_sensor.reset_yaw_angle()
        hub.motor_pair.move(0, 'cm', steering=0, speed=-100)
    # if the message is "l", turn left
    elif v == b"l":
        hub.motion_sensor.reset_yaw_angle()
        hub.motor_pair.move(0, 'cm', steering=-100, speed=100)
    # if the message is "r", turn right
    elif v == b"r":
        hub.motion_sensor.reset_yaw_angle()
        hub.motor_pair.move(0, 'cm', steering=100, speed=100)
    # if the message is "s", stop
    elif v == b"s":
        hub.motor_pair.stop()

uart.on_write(on_rx)

# Start advertising
uart.advertise()

# Keep the program running
while True:
    sleep_ms(100)
```

### Warnings/Dead Ends
The client code in `uart_example.py` sends two-byte motor power values, but the hub code in `robot_python_code.py` expects single-byte commands ('f', 'b', 'l', 'r', 's'). The code as-is in the repository will not work without modification. The README also notes that this requires the V2 Spike Prime app and a downgraded hub firmware, as V3 did not support Python at the time of writing.

## gabrielsessions/pyrepl-js
- Relevance: 7/10
- Connection: USB_Serial
- Firmware: SPIKE_3x
### Technical Details
This project uses the Web Serial API to communicate with the SPIKE Prime hub, not BLE. It establishes a serial connection at a baud rate of 115200. The communication protocol is based on the MicroPython REPL. It uses control characters to manage the REPL session:
- `CTRL-C` (\x03): Interrupts the currently running script.
- `CTRL-E` (\x05): Enters raw REPL mode, which allows for sending Python code programmatically.
- `CTRL-D` (\x04): Exits raw REPL mode and performs a soft reboot with the new code.
The project includes a method to determine the firmware version by sending `import sys; sys.implementation` to the hub and parsing the output. It specifically checks the version number to differentiate between SPIKE 2.x and SPIKE 3.x firmware.

### Reusable/Actionable
While the TypeScript code itself is not directly reusable for a Java/App Inventor project, the underlying REPL interaction logic is highly valuable. We can learn:
1.  The sequence of control characters (`CTRL-E`, code, `CTRL-D`) required to programmatically execute Python code on the hub.
2.  The use of `CTRL-C` to stop a running program before sending a new one.
3.  The technique of querying `sys.implementation` to identify the firmware version of the connected hub. This is a crucial piece of information for handling potential differences between firmware versions.
4.  The general workflow of interacting with the hub's REPL, which is a foundational concept for sending any command, including motor control and sensor reading, by wrapping them in Python code.

### Hub Python Code
```python
import sys
sys.implementation
```

### Warnings/Dead Ends
The GitHub issues for the repository highlight a few limitations:
1.  The functions for writing to the serial port (`rawWriteToPort` and `writeToPort`) are synchronous, but the underlying Web Serial API is asynchronous. This can lead to race conditions where the code assumes a write is complete when it is not. The issue suggests these should be `async` functions.
2.  The `closePort` function throws errors when disconnecting because of improper handling of the underlying streams.
3.  The code contains unfinished functions like `getOS` and `getHubName`, which are not fully implemented or enabled.

## sanjayseshan/spikeprime-tools
- Relevance: 3/10
- Connection: USB_Serial
- Firmware: Unknown
### Technical Details
This project communicates with the SPIKE Prime hub over a USB serial connection using the `pyserial` library. The default serial port is `/dev/ttyACM0` and the baud rate is 115200. The communication protocol is JSON-RPC. JSON messages are sent to the hub, terminated by a carriage return character (\x0d). The `spikejsonrpc.py` script implements methods for listing, uploading, and managing programs on the hub, as well as controlling the LED matrix. The `spike.html` file contains documentation for the MicroPython API available on the hub, which includes classes for controlling motors and reading from the hub motors and reading to sensors.

### Reusable/Actionable
This project uses a USB serial connection, not BLE, so the communication code is not directly reusable for a BLE-based project. However, the documentation of the Python API in `spike.html` provides a good overview of the available functions for controlling motors and reading sensors, which could be a helpful reference for understanding the hub's capabilities. The JSON-RPC protocol structure might also have some similarities with the BLE protocol.

### Hub Python Code
```python
import hub
from runtime import VirtualMachine

# print to console
def printsp(text):
    print("TXTSPTXT"+text+"TXTSPTXT")

# exit program
def endprogram():
    printsp("PROGEXITPROG")

# When program starts
async def on_start(vm, stack):
    # Set LED color
    hub.led(1)
    # log values to console
    printsp(str(1))
    # Sleep 1 second
    yield 1000
    endprogram() # end program

def setup(rpc, system):
    vm = VirtualMachine(rpc, system, "")
    vm.register_on_start("", on_start)
    return vm
```

### Warnings/Dead Ends
The README mentions a potential issue with ModemManager on Linux interfering with the serial connection, and provides a command to disable it.

## sanjayseshan/spikeprime-vscode
- Relevance: 9/10
- Connection: USB_Serial
- Firmware: Unknown
### Technical Details
The project uses a serial connection (USB) to communicate with the SPIKE Prime hub. The communication protocol is JSON-RPC. All messages are JSON objects terminated with a carriage return character (\x0d). The `spikejsonrpc.py` script from the `nutki/spike-tools` repository is used to handle the communication. This script defines several RPC methods for interacting with the hub, including:
- `program_execute`
- `program_terminate`
- `get_storage_status`
- `start_write_program`
- `write_package`
- `move_project`
- `remove_project`
- `get_firmware_info`
- `scratch.display_set_pixel`
- `scratch.display_clear`
- `scratch.display_image`
- `scratch.display_image_for`
- `scratch.display_text`

File content is transferred by encoding it in base64 and sending it as a parameter in the `write_package` RPC call.

### Reusable/Actionable
The JSON-RPC protocol and the list of available methods are highly reusable for our Java/App Inventor implementation. We can create a Java-based JSON-RPC client that communicates with the SPIKE Prime hub over a serial connection. The program upload flow, which involves the `start_write_program` and `write_package` calls, can be directly translated into our implementation. The `program_template.py` provides a good example of a simple program that can be run on the hub.

### Hub Python Code
```python
import hub
from runtime import VirtualMachine

# When program starts
async def on_start(vm, stack):
  for i in range(11):
    # Set LED color
    hub.led(i)
    # Sleep 1 second
    yield 1000

def setup(rpc, system):
  vm = VirtualMachine(rpc, system, "")
  vm.register_on_start("", on_start)
  return vm

```

### Warnings/Dead Ends
The README of the `nutki/spike-tools` repository mentions that on Linux systems, the `ModemManager` service can interfere with the serial communication with the SPIKE Prime hub. It is recommended to disable this service to ensure a stable connection.

## bricklife/LEGO SPIKE Prime JSON command examples
- Relevance: 9/10
- Connection: BLE
- Firmware: Both
### Technical Details
The gist provides a list of JSON-RPC commands for controlling the LEGO SPIKE Prime hub over a BLE connection. The commands are sent as JSON strings and cover various functionalities:

*   **System**: `get_firmware_info`, `trigger_current_state`, `program_modechange`, `set_hub_name`.
*   **Motor**: `scratch.motor_run_for_degrees`, `scratch.motor_run_timed`, `scratch.motor_go_direction_to_position`, `scratch.motor_start`, `scratch.motor_stop`, `scratch.motor_set_position`, `scratch.motor_pwm`.
*   **Movement**: `scratch.move_tank_degrees`, `scratch.move_tank_time`, `scratch.move_start_speeds`, `scratch.move_stop`, `scratch.move_start_powers`.
*   **Light**: `scratch.display_image_for`, `scratch.display_image`, `scratch.display_text`, `scratch.display_clear`, `scratch.display_set_pixel`, `scratch.center_button_lights`, `scratch.ultrasonic_light_up`.
*   **Sound**: `scratch.sound_beep_for_time`, `scratch.sound_beep`, `scratch.sound_off`.
*   **Sensor**: `scratch.reset_yaw`, `reset_program_time`.
*   **Dashboard**: `sync_display`, `move_project`, `remove_project`.
*   **Download Mode**: `start_write_program`, `write_package`, `program_execute`, `program_terminate`.

The author discovered these commands by sniffing the Bluetooth communication between the SPIKE Hub and the official app.

### Reusable/Actionable
The comprehensive list of JSON-RPC commands is highly valuable for our project. We can use it as a reference to implement the communication protocol in our Java/App Inventor extension. The commands' structure and parameters are clearly laid out, which will significantly speed up the development process. The gist also provides insights into the program download and execution flow.

### Warnings/Dead Ends
The comments section indicates that direct communication between two SPIKE hubs is not supported. A user also raises a question about how to send these JSON commands to the hub, suggesting that it might not be a straightforward process and could require a specific mode or setup.

## dctian/lego-spike-prime-py
- Relevance: 6/10
- Connection: BLE
- Firmware: SPIKE_2x
### Technical Details
This repository does not contain any implementation of the SPIKE Prime communication protocol. It is a collection of Python stubs for autocompletion in VSCode. Therefore, no technical details about BLE UUIDs, COBS encoding, CRC32, or TunnelMessage could be extracted.

### Reusable/Actionable
The Python API structure and the function signatures can be a valuable reference for creating a similar API in Java for our App Inventor extension. The docstrings for each function are also very detailed and can be used to understand the expected behavior of each command. The `main.py` file provides a good example of how to structure a program for the SPIKE Prime hub.

### Hub Python Code
```python
# LEGO type:standard slot:0 autostart
# The above line allows VS Code Lego Spike Extension to upload and run code.

# region Imports
from math import *
import sys

from hub import battery
from spike import PrimeHub, LightMatrix, Button, StatusLight, ForceSensor, MotionSensor, Speaker, ColorSensor, App, DistanceSensor, Motor, MotorPair
from spike.control import wait_for_seconds, wait_until, Timer
from spike.operator import equal_to, greater_than, greater_than_or_equal_to, less_than, less_than_or_equal_to, not_equal_to
# endregion

# region Initialization
my_hub = PrimeHub()
distance_sensor = DistanceSensor('C')
right_sensor = ColorSensor('F')
left_sensor = ColorSensor('B')
wheels = MotorPair('A', 'E')
wheels.set_motor_rotation(20)
matrix = LightMatrix()
# endregion

# region Functions


def check_battery():
    BATTERY_OK = '\033[32m'
    BATTERY_LOW = '\033[31m'
    ENDC = '\033[0m'
    VOLTAGE_THRESHOLD = 8000  # Threshold for battery level

    # Battery voltage printout in console for monitoring charge
    if battery.voltage() < VOLTAGE_THRESHOLD:
        print(BATTERY_LOW + "battery voltage is too low: " + str(battery.voltage()) +
              " \n ----------------------------- \n >>>> please charge robot <<<< \n ----------------------------- \n"
              + ENDC)
    else:
        print(BATTERY_OK + "battery voltage: " + str(battery.voltage()) + ENDC)


def print_module_objects_and_attributes(module_name):
    """Print hidden Spike APIs given a module name.

    Usage:
        `print_module_objects_and_attributes('hub')`
    Args:
        module_name (string): i.e. 'hub' or 'spike'
    """
    module = __import__(module_name)
    print("Module: ", module_name)
    objects = dir(module)

    for obj_name in objects:
        obj = getattr(module, obj_name)
        attributes = dir(obj)

        print("Object: ", obj_name)
        for attribute in attributes:
            print("- ", attribute)
        print()


def end_program():
    """Gracefully end the program without any traceback messages.
    """
    try:
        # Code that may raise SystemExit
        sys.exit(0)
    except SystemExit:
        # Handle the SystemExit exception without propagating it
        pass

# endregion

# region Main


def main():
    # ----------------  Put your code logic here  -----------------
    matrix.show_image("CLOCK8")
    wheels.move(5)
    print('distance=%d' % distance_sensor.get_distance_cm())

# endregion


# region DO NOT EDIT ANYTHING HERE
print("\n\nStarting... ")
check_battery()
timer = Timer()
main()
print("Ended program. Elapsed time: " + str(timer.now()))
end_program()
# endregion
```

### Warnings/Dead Ends
The main limitation is that this is not a functional implementation. The disclaimer in the README.md file explicitly states: 'This project is for autocompletion and auto docstring display ONLY, as all the implementations are stubbed out.' and 'This project is not a Spike Prime simulator.'

## pybricks/technical-info
- Relevance: 9/10
- Connection: BLE
- Firmware: Unknown
### Technical Details
This repository provides detailed technical information about the Pybricks firmware and related communication protocols. The `assigned-numbers.md` file lists LEGO-specific and Pybricks-specific BLE UUIDs, as well as Hub Type IDs and I/O Device Type IDs. The `pybricks-ble-profile.md` file describes the Pybricks Bluetooth Low Energy profile, including the Pybricks Service, command/event characteristic, and hub capabilities characteristic. It also details the program download procedure. The `uart-protocol.md` file explains the LEGO Powered Up UART Protocol, which is based on the EV3 UART sensor protocol.

### Reusable/Actionable
The BLE UUIDs for the SPIKE Prime service and characteristics are directly reusable. The documentation on the Pybricks BLE profile provides a good reference for understanding the communication protocol and implementing a client. The information on the UART protocol is also valuable for understanding how sensors and motors communicate with the hub.

