import socket
import sys
import time
import json
from threading import Thread
import keras
import numpy as np
from point2d_model_env import PointModel2dEnv

# todo: I'm assuming that state is only the two positions (excitations are not part of state)


def calculate_excitations(self, ref_pos, follow_pos):
    # forward pass of NN with positions to get values
    values = [0, 0, 0, 0,
              0, 1, 0, 0,
              0, 0, 0, 1,
              0, 0, 0, 0]
    return dict(zip(muscle_labels, values))



def main():
    # in this particular case, not looking for multithreading:
    # loop
    #   wait for new location, block until received
    #   send excitations (action)
    #   wait for new location to calculate the reward of the action



    while True:
        try:
            sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            server_address = ('localhost', 6611)
            sock.connect(server_address)
            env = PointModel2dEnv(sock)

            while True:
                try:
                    data = sock.recv(1024).decode("utf-8")
                    if data is '':
                        break
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

                        if data_dict_result['type'] == 'results':
                            ref_pos = np.array([float(str) for str in data_dict_result['ref_pos'].split(" ")])
                            follow_pos = np.array([float(str) for str in data_dict_result['follow_pos'].split(" ")])
                            reward = calculate_reward(ref_pos, follow_pos)
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
