import socket
import sys
import time
import json

def main():
    while True:
        try:
            sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            server_address = ('localhost', 9898)
            sock.connect(server_address)
            data = sock.recv(4094)
            data = json.loads(data)

            print(data)
        except:
            print("server not started")
            time.sleep(10)


if __name__ == "__main__":
    main()
