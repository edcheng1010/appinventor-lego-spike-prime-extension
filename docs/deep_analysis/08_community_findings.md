# Findings from scan_community_resources.json

## App Inventor Community SPIKE Prime Thread
- Relevance: 9/10
### Technical Details
The `PeterStaev/lego-spikeprime-mindstorms-vscode` repository provides the most technical details. It includes:
- **BLE Communication**: The extension communicates with the SPIKE Prime hub over BLE. The source code of the extension would contain the specific BLE services and characteristics used for communication.
- **Program Upload**: The extension can upload Python programs to the hub. This is done by sending the program to a specific characteristic on the hub. The extension also supports compiling the Python code to MPY format before uploading.
- **Program Execution**: The extension can start and stop programs on the hub. This is done by sending specific commands to the hub over BLE.
- **HubOS3 Support**: The latest version of the extension only supports HubOS3. This indicates that there might be differences in the BLE protocol between HubOS2 and HubOS3.
- **Preprocessor**: The extension has a preprocessor that combines multiple Python files into a single file before uploading. This is a useful feature for larger projects.

### Reusable/Actionable
The most actionable insight is the existence of a comprehensive Python API for controlling the SPIKE Prime hub, which can be found in the `GO-Robot-FLL/Python-for-Spike-Prime` repository. This API can be used as a reference for creating a similar API in Java for the App Inventor extension. The `PeterStaev/lego-spikeprime-mindstorms-vscode` repository provides valuable information on how to communicate with the hub via BLE, including how to upload and start programs. The preprocessor logic in this extension for handling multi-file projects is also a useful pattern to consider.

### Hub Python Code
```python
The `GO-Robot-FLL/Python-for-Spike-Prime` repository contains a comprehensive Python file with classes for controlling the SPIKE Prime hub. This includes classes for `App`, `DistanceSensor`, `ColorSensor`, `ForceSensor`, `Motor`, `MotorPair`, `Speaker`, `LightMatrix`, `StatusLight`, `Timer`, and `Button`. Each class has a set of methods for controlling the corresponding hardware component. For example, the `Motor` class has methods for running the motor for a specific distance, for a specific time, or at a specific speed. The `ColorSensor` class has methods for reading the color, reflected light intensity, and ambient light intensity. The entire python file is too large to be included here, but it can be found at the provided URL.
```

### Warnings/Dead Ends
The main dead end is that there is currently no off-the-shelf App Inventor extension for the LEGO SPIKE Prime. The only way to control it is by using Python or by building a custom extension from scratch. The `dctian/lego-spike-prime-py` repository is only for autocompletion and does not contain any functional code.

## About the Extension Development category
- Relevance: 7/10
### Technical Details
The documents provide extensive technical details on App Inventor extension development, including:

*   **App Inventor extension development patterns:** The documents describe the process of creating extension components, which involves creating a Java class that extends one of the App Inventor component classes, and using annotations to define the component's properties, methods, and events.
*   **BLE extension API methods and events:** While there is no specific information about BLE extensions, the documents describe how to define methods and events for extensions in general. This information can be applied to create a BLE extension.
*   **App Inventor Annotations:** The 'App Inventor Annotations' document provides a detailed list of all the annotations that can be used to define the behavior of an extension, including annotations for defining properties, methods, events, permissions, and more.

### Reusable/Actionable
The primary actionable insights come from the 'How to Add a Component' and 'App Inventor Annotations' documents. These provide a detailed guide on creating new components for App Inventor, which is directly applicable to creating a SPIKE Prime extension. The documents outline the necessary Java class hierarchy, the use of annotations to define properties, methods, and events, and the process for packaging an extension. The 'How to build App Inventor from the MIT sources' document is also useful for setting up a local development environment to build and test the extension.

### Warnings/Dead Ends
The documents warn that the extension system is still experimental and that the internal format of extensions is subject to change. This means that extensions may break when App Inventor is updated, and developers will need to update their extensions accordingly. It is also mentioned that App Inventor classic is obsolete and will no longer build.

## BluetoothLE Updates 2024 - Extensions - MIT App Inventor Community
- Relevance: 9/10
### Technical Details
The main technical detail is the Java runtime error, which points to a class loading issue with the `edu.mit.appinventor.ble.BluetoothLE` class. This is a significant clue for debugging the extension. There is no mention of COBS, CRC32, TunnelMessage, or specific SPIKE Prime communication protocols.

### Reusable/Actionable
- The primary issue seems to be a packaging or class loading problem with the BLE extension, especially when used with the companion app.
- The fact that building an APK works is a crucial piece of information, suggesting the issue is with the live development environment (companion).
- The Java error trace is a valuable starting point for debugging. It clearly indicates the `BluetoothLE` class is not being found.

### Warnings/Dead Ends
The thread doesn't explicitly mention dead ends, but it warns that the latest BLE extension version (20240822) has compatibility issues and that the documentation is outdated. The issue with the extension not showing blocks in the designer unless "Any Component" is used is also a warning.

## SPIKE Community Facebook Post: Connecting Spike Prime to MIT App Inventor
- Relevance: 8/10
### Technical Details
No specific technical details about the BLE protocol, COBS, CRC, UUIDs, motor control, or TunnelMessage were found. The only significant technical detail is the confirmation that SPIKE Prime Hub firmware version 3.x is incompatible with the existing App Inventor integration, and a downgrade to a 2.x version is required for it to work.

### Reusable/Actionable
The most critical insight is the firmware incompatibility between SPIKE Prime Hub firmware 3.x and the existing MIT App Inventor integration. This implies that for our extension to work, we must either target the older 2.x firmware, which may limit the features we can use, or we need to develop a new communication protocol for the 3.x firmware, which the linked video suggests is possible but does not explain how. The video by '퓨너스FUNERS' shows a custom app, not App Inventor, controlling the hub, which might be a path to investigate: creating a companion app that bridges communication between the App Inventor extension and the hub.

### Warnings/Dead Ends
The primary warning is that LEGO SPIKE Prime hubs running firmware version 3.x are not compatible with the existing MIT App Inventor connectivity solution. Attempting to connect a 3.x hub directly to App Inventor will likely fail. This is a significant dead end for users with updated hubs.

## RemoteBrick Reddit Thread and GitHub Repository
- Relevance: 6/10
### Technical Details
- **Communication Protocol:** The library uses Bluetooth Classic with the Serial Port Profile (SPP) for communication, not Bluetooth Low Energy (BLE).
- **Message Format:** Communication is achieved by sending JSON-formatted messages to the hub. The specific structure of these JSON messages is not detailed in the Reddit thread or the GitHub README, but could be found by analyzing the source code.
- **Platform:** The library is explicitly stated to be for Windows only, relying on a custom C++ library (and likely a DLL) for Bluetooth communication.
- **Firmware:** It works with the original, unmodified LEGO firmware on the hub.
- **Unsupported Topics:** There is no information regarding COBS encoding, CRC32, the `TunnelMessage` (0x32) protocol, `hub.config['module_tunnel']`, or the specifics of the LEGO BLE GATT services and characteristics.

### Reusable/Actionable
The most significant insight is the use of JSON messages over Bluetooth Classic (SPP) as a method to control the SPIKE hub. This presents a potential alternative to the more complex, lower-level BLE protocol that we have been investigating. If App Inventor's Bluetooth components can handle SPP communication, we could potentially build a simpler extension by reverse-engineering the JSON message format used by RemoteBrick. This could significantly reduce the complexity of handling BLE services, characteristics, and data encoding like COBS and CRC32. The project provides a proof-of-concept that high-level control is possible without flashing custom firmware like Pybricks.

### Warnings/Dead Ends
- The library's reliance on a Windows-specific C++ library means the native code is not directly reusable for an App Inventor extension, which needs to be cross-platform (Android/iOS).
- The use of Bluetooth Classic (SPP) might be a limitation. While many devices support it, the trend is towards BLE, and we need to confirm that App Inventor's Bluetooth components can reliably handle SPP for this purpose.
- This resource is a dead end for finding information on the specific LEGO BLE protocol details like COBS, CRC32, and the `TunnelMessage` (0x32), as it uses a completely different communication method.

## LEGO Education SPIKE Prime Python Documentation (SPIKE 3)
- Relevance: 8/10
### Technical Details
The documentation does not contain any specific details about the BLE protocol, services, or characteristics used for communication with the SPIKE Prime hub. It mentions a `device_uuid()` function in the `hub` module, which might be related to BLE, but no further details are provided. There is no mention of COBS, CRC32, or TunnelMessage (0x32) in the documentation. The `motor` and `motor_pair` modules provide extensive functions for controlling motors. This includes running motors at a specific velocity, for a certain duration or number of degrees, and moving to absolute or relative positions. The documentation details the various parameters for these functions, such as `velocity`, `acceleration`, `deceleration`, and `stop` modes (BRAKE, COAST, HOLD). The documentation covers reading data from various sensors, including the Color Sensor, Distance Sensor, and Force Sensor. It provides functions to get raw sensor data, as well as processed values like color, distance, and force. The documentation does not describe the program upload process. It assumes the user is using the LEGO Education SPIKE App to write and upload code. There is no mention of `hub.config['module_tunnel']`. This documentation is focused on Python for SPIKE Prime and does not contain any information about App Inventor extension development.

### Reusable/Actionable
The Python API documentation provides a clear blueprint for the functionality that needs to be implemented in the App Inventor extension. The methods and events in the extension should mirror the functions and capabilities of the Python modules. The motor control functions and parameters are particularly important and should be replicated in the extension to provide fine-grained control over the motors. The sensor reading functions will be the basis for the sensor-related blocks in the App Inventor extension. The use of `async` and `await` with the `runloop` module for concurrent operations is a key programming paradigm on the SPIKE Hub. The App Inventor extension should handle this complexity internally to provide a simpler, event-based model for the user. The documentation provides a list of constants for colors, motor stop modes, gestures, etc. These should be exposed as dropdowns or properties in the App Inventor extension.

### Hub Python Code
```python
import motor
import motor_pair
import color_sensor
from hub import port
import runloop

# Example of running a single motor
async def run_motor_example():
    await motor.run_for_degrees(port.A, 360, 720)

# Example of using a motor pair
async def move_robot_example():
    motor_pair.pair(motor_pair.PAIR_1, port.A, port.B)
    await motor_pair.move_for_degrees(motor_pair.PAIR_1, 360, 0, velocity=280)

# Example of reading a color sensor
def is_color_red():
    return color_sensor.color(port.C) is color.RED

async def sensor_example():
    await runloop.until(is_color_red)
    print("Red!")

# Run the examples
runloop.run(run_motor_example(), move_robot_example(), sensor_example())
```

### Warnings/Dead Ends
The documentation does not provide any information on the low-level BLE communication protocol. This means that we will need to find this information elsewhere, or reverse-engineer it. The documentation is also not a guide for building extensions, so it does not offer any patterns for that.

## Remote Control Robot Inventor car with an Android app
- Relevance: 8/10
### Technical Details
The article explains that the app uses a BLE UART link to communicate with the hub. It sends compressed data packets 50 times per second, which are then unpacked on the hub using `struct.unpack("bbbbBBhhB", data)`. The Python code demonstrates motor control using the `Motor` class from `projects.mpy_robot_tools.pybricks`, with functions like `run_until_stalled`, `reset_angle`, `run_target`, `track_target`, and `dc`. The article does not contain details on COBS, CRC32, TunnelMessage, or `hub.config['module_tunnel']`.

### Reusable/Actionable
The article demonstrates a successful implementation of remote control via a BLE UART link. The use of `struct.unpack` for data compression is a valuable technique for our project. The provided Python code for motor control can be adapted for our extension. The `mpy-robot-tools` library appears to be a crucial component for BLE communication with the SPIKE hub.

### Hub Python Code
```python
# Author: Anton's Mindstorms (Anton Vanhoucke)
# https://antonsmindstorms.com

# This code is meant to run on a hotrod model with LEGO MINDSTORMS Robot Inventor,
# in combination with the Transmitter model and code

# Building instructions for the transmitter:
# https://antonsmindstorms.com/product/remote-control-transmitter-with-mindstorms-51515/
#
# Building instructions for the hot rod:
# https://antonsmindstorms.com/product/remote-controlled-hot-rod-with-51515/
#
# mpy_robot_tools installer:
# https://github.com/antonvh/mpy-robot-tools/blob/master/Installer/install_mpy_robot_tools.py

from hub import sound
from projects.mpy_robot_tools.rc import RCReceiver, L_STICK_VER, R_STICK_HOR, SETTING1, L_TRIGGER, BUTTONS
from projects.mpy_robot_tools.pybricks import Port, wait, Motor
from time import sleep

rcv = RCReceiver(name="robot")

steer = Motor(Port.A)
propulsion = Motor(Port.B)
steer.run_until_stalled(200)
steer.reset_angle(270)
steer.run_target(500, 0)
wait(500)

while 1:
    if rcv.is_connected():
        steer_target, speed_target, trim, thumb = rcv.controller_state(R_STICK_HOR, L_STICK_VER, SETTING1, L_TRIGGER)
        steer.track_target(steer_target*-1 - trim/300)
        propulsion.dc(speed_target*-1)
    else:
        steer_target, speed_target, trim = (0,0,0)
        steer.stop()
        propulsion.stop()

    if rcv.button_pressed(6):
        sound.beep(300,200)
        wait(200)
```

### Warnings/Dead Ends
A comment on the article warns that this solution only works with SPIKE 2.0 firmware and not the latest version. It is possible to downgrade the firmware using Pybricks.

## [SPIKE Prime X App Inventor] This is going to happen?? Connecting a hub with a smartphone!
- Relevance: 3/10
### Reusable/Actionable
The video confirms the feasibility of creating a mobile application to control a SPIKE Prime robot. The application in the video is named 'SPIKE prime Controller', which could be a useful search term for further research. A comment asking about Bluetooth UART suggests a potential communication protocol to explore for our project.

### Warnings/Dead Ends
This video is a dead end for acquiring technical implementation details. It serves only as a conceptual demonstration.

## app inventor Steering Car Controller - YouTube
- Relevance: 5/10
### Technical Details
The video demonstrates a Bluetooth Low Energy (BLE) connection between the App Inventor app and the SPIKE Prime hub. The app is used to control the robot's motors for steering and movement. No specific details about the BLE protocol, COBS, CRC32, TunnelMessage, or sensor reading are provided in the video or the accompanying text.

### Reusable/Actionable
The primary actionable insight is the confirmation that it is possible to build an MIT App Inventor application that successfully connects to and controls a LEGO SPIKE Prime robot via Bluetooth, specifically managing drive and steering motors. The user interface design (a steering wheel for direction and a multi-state button/slider for speed) provides a practical example for similar projects.

## How LEGO® Education uses the Web Bluetooth and the Web Serial APIs
- Relevance: 7/10
### Technical Details
Web Bluetooth API Connection:
- `navigator.bluetooth.requestDevice` is used to request the device.
- The filter includes a `namePrefix` of 'GDX'.
- An `optionalServices` UUID is specified: 'd91714ef-28b9-4f91-ba16-f0d9a604f112'.

Web Serial API Connection:
- `navigator.serial.requestPort` is used to request the port.
- The filter includes a `usbVendorId` for LEGO, which is 1684.
- The connection is opened with a `baudRate` of 115200.

### Reusable/Actionable
The provided `optionalServices` UUID 'd91714ef-28b9-4f91-ba16-f0d9a604f112' is a crucial piece of information for our App Inventor extension to connect to the SPIKE Prime hub. The `usbVendorId` (1684) and `baudRate` (115200) are important for serial communication. The article points to community projects like PyREPL-JS and Web-Interfaces for SPIKE Prime, which could be valuable resources for further investigation into the communication protocol.

### Warnings/Dead Ends
The article does not explicitly mention any dead ends or warnings. However, it's worth noting that the article focuses on web development, so the direct applicability to Java-based Android development for App Inventor might be limited. The provided code snippets are for Javascript and not directly usable in Java.

## spikedev documentation
- Relevance: 9/10
### Technical Details
The documentation provides detailed information on connecting to the SPIKE Prime hub via Bluetooth on Ubuntu. It specifies the use of `bluetoothctl` for pairing and `rfcomm` to bind the Bluetooth device to a serial port (`/dev/rfcomm0`). The UUID for the serial port is explicitly mentioned as `00001101-0000-1000-8000-00805f9b34fb`. The `spikedev.motor` module documentation details the classes and methods for controlling motors, including different speed units (percentage, RPS, RPM, DPS, DPM) and functions for running motors for a specific duration, number of degrees, or to a target position. The workflow for running MicroPython programs on the hub involves using the `ampy` tool to execute scripts from a connected computer.

### Reusable/Actionable
The `spikedev` library serves as an excellent reference for understanding how to interact with the SPIKE Prime hub using MicroPython. The library's source code, available on GitHub, can be analyzed to understand the underlying communication protocols and device control mechanisms. The documentation's clear explanation of connecting to the hub via Bluetooth on Ubuntu, including the specific UUID for the serial port, is directly applicable to our project. The various motor control classes and methods can be used as a model for our own App Inventor extension's API.

### Hub Python Code
```python
# Run via
#  sudo ampy --port <your-dev-here> run ./demo/tank/advanced-driving-base.py
import hub
import math
from spikedev.motor import MotorSpeedPercent, SpikeLargeMotor, SpikeMediumMotor
from spikedev.sensor import ColorSensor
from spikedev.tank import MoveDifferential
from spikedev.unit import DistanceInches, DistanceStuds
from spikedev.wheel import SpikeLargeWheel

adb = MoveDifferential(
   left_motor_port=hub.port.A,
   right_motor_port=hub.port.E,
   wheel_class=SpikeLargeWheel,
   wheel_distance=DistanceStuds(19),
   motor_class=SpikeLargeMotor
)
adb.rear_motor = SpikeMediumMotor(hub.port.C)
adb.front_motor = SpikeMediumMotor(hub.port.D)
adb.left_color_sensor = ColorSensor(hub.port.B)
adb.right_color_sensor = ColorSensor(hub.port.F)

adb.turn_right(90, MotorSpeedPercent(20))
adb.run_for_distance(DistanceInches(12), MotorSpeedPercent(50))
adb.run_arc_right(DistanceInches(8), DistanceInches(8) * math.pi, MotorSpeedPercent(20))
```

### Warnings/Dead Ends
The documentation for connecting to the SPIKE hub on Windows and Mac operating systems is marked as 'TBD' (To Be Determined), which means it is incomplete. This could be a significant roadblock for developers using these platforms. Additionally, the need to bypass the default `main.py` script on the SPIKE hub by holding down the left button during boot is a workaround that might not be ideal for a seamless user experience.

## lego-spikeprime-mindstorms-vscode
- Relevance: 9/10
### Technical Details
BLE Communication:
- Service UUID: 0000fd02-0000-1000-8000-00805f9b34fb
- RX Characteristic UUID: 0000fd02-0001-1000-8000-00805f9b34fb (for receiving data from the hub)
- TX Characteristic UUID: 0000fd02-0002-1000-8000-00805f9b34fb (for sending data to the hub)

COBS Encoding: A custom COBS-like implementation is used for message framing. The `encode` and `decode` functions are available in `cobs.ts`. It uses a delimiter of `0x02` and a max block size of `84`.

Message Framing: Messages are sent as COBS-encoded byte arrays.

Info Request: An `InfoRequestMessage` with ID `0x00` can be sent to request information from the hub.

Program Flow: A `ProgramFlowRequestMessage` with ID `0x1E` is used to start or stop a program. It includes the slot number and a boolean flag to indicate start or stop.

File Upload: The file upload process is initiated with a `StartFileUploadRequestMessage` (ID `0x0C`), which contains the filename (max 31 bytes), slot number, and a CRC32 checksum.

### Reusable/Actionable
The BLE service and characteristic UUIDs are essential for establishing communication with the SPIKE Prime hub. The COBS implementation in TypeScript can be translated to Java for the App Inventor extension. The message structures for info requests, program flow, and file uploads provide a clear blueprint for implementing these features. The file upload process, including the use of CRC32, is well-defined and can be replicated.

## Android Studio & App Tools
- Relevance: 1/10
### Technical Details
N/A. The page does not contain any technical details about BLE communication with SPIKE Prime, COBS encoding, CRC32, message framing, TunnelMessage (0x32) usage, motor control, sensor reading, program upload to the hub, hub.config['module_tunnel'] usage, App Inventor extension development patterns, or BLE extension API methods and events.

### Reusable/Actionable
There are no actionable insights for building a Java/App Inventor extension for SPIKE Prime from this resource.

### Warnings/Dead Ends
This resource is a dead end for the project. It does not contain any relevant information.

