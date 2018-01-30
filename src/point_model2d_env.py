from keras.models import Sequential
from keras.layers import Dense, Activation, Flatten
from keras.optimizers import Adam

from rl.agents.dqn import DQNAgent
from rl.policy import BoltzmannQPolicy
from rl.memory import SequentialMemory
from rl.core import Env
import json
import numpy as np
from consts import EPSILON

muscle_labels = ["n", "nne", "ne", "ene",
                 "e", "ese", "se", "sse",
                 "s", "ssw", "sw", "wsw",
                 "w", "wnw", "nw", "nnw"]

'''
Here, I'm assuming each episode to run until it reaches the ref_pos
So, nb_max_episode_steps is going to be None

Once we reach the terminal state, 'done' is set to True
'''


class PointModel2dEnv(Env):
    def __init__(self, socket, success_thres=0.1, verbose=True):
        self.socket = socket
        self.verbose = verbose
        self.success_thres = success_thres

    def log(self, obj):
        print(obj) if self.verbose else lambda: None

    def step(self, action):
        excitations_json = json.dumps(action['excitations'], ensure_ascii=False).encode('utf-8')
        self.socket.send(excitations_json)
        self.log('Excitations sent')

        data_result = self.socket.recv(1024).decode("utf-8")
        data_dict_result = json.loads(data_result)

        if data_dict_result['type'] == 'results':
            ref_pos = np.array([float(str) for str in data_dict_result['ref_pos'].split(" ")])
            follow_pos = np.array([float(str) for str in data_dict_result['follow_pos'].split(" ")])
            distance = self.calculate_distance(ref_pos, follow_pos)
            reward = self.calculate_reward(distance)
            done = True if distance < self.success_thres else False
            return tuple([ref_pos, follow_pos], reward, done, '')

    @staticmethod
    def calculate_reward(self, ref_pos, follow_pos):
        return 1/(self.calculate_distance(ref_pos, follow_pos) + EPSILON)

    @staticmethod
    def calculate_reward(self, distance):
        return 1/(distance + EPSILON)

    @staticmethod
    def calculate_distance(a, b):
        return np.sqrt(np.sum((b - a) ** 2))

    def reset(self):

        pass

    def render(self, mode='human', close=False):
        pass

    def close(self):
        pass

    def seed(self, seed=None):
        pass

    def configure(self, *args, **kwargs):
        pass