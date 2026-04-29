# LEGO SPIKE Prime 3.x hub controller — Phase 1 (single + paired motor drive).
#
# Command protocol (all via TunnelMessage):
#   Single motor : "A+050"          port A, +50% speed
#   Pair config  : "PAIR:A:B"       set A=left, B=right motor for drive commands
#   Drive forward: "FWD:050"        both motors forward at 50%
#   Drive back   : "BWD:050"        both motors backward
#   Turn left    : "LFT:050"        tank-turn left at 50%
#   Turn right   : "RGT:050"        tank-turn right
#   Stop all     : "STP"            stop paired motors
from hub import light_matrix, port
import hub
import motor
import motor_pair

light_matrix.set_pixel(2, 2, 100)   # centre LED = program running

tunnel = hub.config['module_tunnel']

PORTS = {'A': port.A, 'B': port.B, 'C': port.C, 'D': port.D, 'E': port.E, 'F': port.F}

# Default drive pair: A = left motor, B = right motor
motor_pair.pair(motor_pair.PAIR_1, port.A, port.B)


def on_message(data):
    if not isinstance(data, str):
        data = ''.join(chr(b) for b in data)

    if data.startswith('PAIR:') and len(data) == 8:
        lp, rp = data[5], data[7]
        if lp in PORTS and rp in PORTS:
            motor_pair.pair(motor_pair.PAIR_1, PORTS[lp], PORTS[rp])

    elif data.startswith('FWD:') and len(data) == 7 and data[4:7].isdigit():
        motor_pair.move(motor_pair.PAIR_1, 0, velocity=int(data[4:7]) * 11)

    elif data.startswith('BWD:') and len(data) == 7 and data[4:7].isdigit():
        motor_pair.move(motor_pair.PAIR_1, 0, velocity=-int(data[4:7]) * 11)

    elif data.startswith('LFT:') and len(data) == 7 and data[4:7].isdigit():
        v = int(data[4:7]) * 11
        motor_pair.move_tank(motor_pair.PAIR_1, -v, v)

    elif data.startswith('RGT:') and len(data) == 7 and data[4:7].isdigit():
        v = int(data[4:7]) * 11
        motor_pair.move_tank(motor_pair.PAIR_1, v, -v)

    elif data == 'STP':
        motor_pair.stop(motor_pair.PAIR_1)

    elif len(data) == 5 and data[0] in PORTS and data[1] in ('+', '-') and data[2:5].isdigit():
        motor.run(PORTS[data[0]], int(data[1] + data[2:5]) * 11)

    tunnel.send(b'rdy')


tunnel.callback(on_message)
tunnel.send(b'rdy')

while True:
    pass
