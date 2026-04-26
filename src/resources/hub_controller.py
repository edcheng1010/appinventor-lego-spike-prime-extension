# LEGO SPIKE Prime 3.x hub-side controller.
# Receives TunnelMessage payloads and drives motors on ports A-F.
# Command format — one or more 5-char chunks: {port A-F}{+ or -}{NNN deg/s}
# Example: "A+050B-030" → port A +50 deg/s, port B -30 deg/s; "A+000" → stop A.
import hub
from hub import port

# NOTE: The correct API for receiving tunnel messages is hub.config['module_tunnel']
# with tunnel.callback().  hub.ble.register_message_handler() does not exist in
# SPIKE Prime 3.x firmware — confirmed by the etomasfe working reference implementation.
tunnel = hub.config['module_tunnel']

PORTS = {
    'A': port.A, 'B': port.B, 'C': port.C,
    'D': port.D, 'E': port.E, 'F': port.F,
}


def on_message(data):
    if not data:
        return
    # Tunnel delivers raw bytes; decode to string for character-level parsing.
    if isinstance(data, (bytes, bytearray)):
        data = data.decode('utf-8')
    # Walk through 5-char chunks: {port}{sign}{NNN}
    i = 0
    while i + 5 <= len(data):
        p, s, n = data[i], data[i + 1], data[i + 2:i + 5]
        if p in PORTS and s in ('+', '-') and n.isdigit():
            PORTS[p].motor.run(int(s + n))
        i += 5
    tunnel.send(b'rdy')


tunnel.callback(on_message)
tunnel.send(b'rdy')   # signal readiness as soon as the program starts

while True:
    pass
