from gi import overrides

from src.point_model2d import PointModel2dEnv
import time
from socket import timeout as TimeoutException
import json
from src.consts import EPSILON
import socket
from typing import Union
# from typing import get_type_hints
from rl.agents import DDPGAgent
from pathlib import Path
from keras.layers import Dense, Activation, Flatten, Input, Concatenate
import json
from threading import Thread
import keras
from keras.models import Sequential
from keras.layers import Dense, Activation, Flatten
from keras.optimizers import Adam
from keras.models import Sequential, Model
from keras.layers import Dense, Activation, Flatten, Input, Concatenate
from keras.optimizers import Adam
from keras import backend as K
from rl.policy import BoltzmannQPolicy
from rl.memory import SequentialMemory
from rl.core import Env
from rl.core import Space
from rl.core import Processor
import numpy as np
from src.utilities import begin_time
import src.config as c


class JawModelEnv(PointModel2dEnv):

    def __init__(self, muscle_labels=None, dof_observation=6, success_thres=0.1,
                 verbose=2, log_to_file=True, log_file='log_jaw', agent=None,
                 include_follow=True, port=6006, point_labels=None):
        super(JawModelEnv, self).__init__(muscle_labels, dof_observation, success_thres,
                                          verbose, log_to_file, log_file, agent,
                                          include_follow, port)
        # self.prev_distance = None
        self.point_labels = point_labels

    def step(self, action):
        action = self.augment_action(action)
        self.send(action, 'excitations')
        time.sleep(2)
        state = self.get_state()
        if state is not None:
            distance = self.calculate_distance(state)
            if self.prev_distance is not None:
                reward, done = self.calcualte_reward_time_dist(distance, self.prev_distance)
            else:
                reward, done = (0, False)
            self.prev_distance = distance
            if done:
                self.log('Achieved done state', verbose=0)
            self.log('****Reward: ' + str(reward), verbose=1, same_line=True)

            state_arr = self.state_json_to_array(state)
            info = {'distance': distance}

        return state_arr, reward, done, info

    def calculate_distance(self, state):
        dist = 0
        for i in range(3):
            dist += np.sum((state[self.point_labels[i]] -
                            state[self.point_labels[i+3]]) ** 2)
        return np.sqrt(dist/3)

    def parse_state(self, state_dict: dict):
        state = dict()
        for point_name in self.point_labels:
            pos = np.array([float(s) for s in state_dict[point_name].split(" ")])
            state.update({point_name: pos})
        return state

    def state_json_to_array(self, state_dict: dict):
        state_arr = []
        for i in range(3):
            state_arr.extend(state_dict[self.point_labels[i]])
        if self.include_follow:
            for i in range(3, 6):
                state_arr.append(state_dict[self.point_labels[i]])
        return np.array(state_arr)

