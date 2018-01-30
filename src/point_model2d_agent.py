import socket
import sys
import time
import json
from threading import Thread

# todo: I'm assuming that state is only the two positions (excitations are not part of state)

muscle_labels = ["n", "nne", "ne", "ene",
                 "e", "ese", "se", "sse",
                 "s", "ssw", "sw", "wsw",
                 "w", "wnw", "nw", "nnw"]


def calculate_excitations(ref_pos, follow_pos):
    # forward pass of NN with positions to get values
    values = [0, 0, 0, 0,
              0, 1, 0, 0,
              0, 0, 0, 1,
              0, 0, 0, 0]
    return dict(zip(muscle_labels, values))


def calculate_reward(ref_pos, follow_pos):
    return 3


def main():
    # in this particular case, not looking for multithreading:
    # loop
    # wait for new location, block until received
    # send excitations (action)
    # wait for new location to calculate the reward of the action
    # loop
    while True:
        try:
            sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            server_address = ('localhost', 6611)
            sock.connect(server_address)

            while True:
                try:
                    data = sock.recv(1024).decode("utf-8")
                    data_dict = json.loads(data)
                    print('positions1: ', data_dict)
                    if data_dict['type'] == 'state':
                        excitations = calculate_excitations(data_dict['ref_pos'],
                                                            data_dict['follow_pos'])
                        excitations['type'] = 'excitations'
                        excitations_json = json.dumps(excitations, ensure_ascii=False).encode('utf-8')
                        sock.send(excitations_json)
                        print('Excitations sent')

                        data_result = sock.recv(1024).decode("utf-8")
                        data_dict_result = json.loads(data_result)
                        print('positions2: ', data_dict_result)

                        if data_dict['type'] == 'results':
                            reward = calculate_reward(data_dict_result['ref_pos'],
                                                      data_dict_result['follow_pos'])
                            # backpropagate through NN
                except Exception as e:
                    print(e)
                    # sock.close()
                    # time.sleep(10)

        except ConnectionRefusedError as e:
            print("Server not started: ", e)
            sock.close()
            time.sleep(10)



if __name__ == "__main__":
    main()
