import os
import subprocess
import time
import signal
import logging
import json

import gym
from gym import error, spaces, utils
from gym.utils import seeding
from gym import error, spaces
from gym import utils
from gym.utils import seeding

import torch

from common.config import env_log_directory
from common import constants as c
from common.net import Net
import numpy as np

logger = logging.getLogger()

LOW_MUSCLE = 0
HIGH_MUSCLE = 1
LOW_POS = -10
HIGH_POS = +10
LOW_VEL = -100
HIGH_VEL = +100
LOW_EXCITATION = 0
HIGH_EXCITATION = 1
NUM_TARGETS = 2
NUM_MUSCLES = 119
POSITION_DIM = 3
VELOCITY_DIM = 3

WAIT_AFTER_ACTION = 0.3


class SpineEnv(gym.Env):
    metadata = {'render.modes': ['cmd', 'gui']}

    def __init__(self, name, ip, port):
        self.sock = None
        self.net = Net(ip, port)

        # init observation space
        # todo: set the right limits for velocity and position as we normalize values
        # before sending to agent
        state_size = self.get_state_size()
        logger.debug('state size %i', state_size)
        # self.observation_space = spaces.Tuple((
        #     spaces.Box(low=LOW_VEL, high=HIGH_VEL, shape=(NUM_TARGETS*3,)),
        #     spaces.Box(low=LOW_POS, high=HIGH_POS, shape=(NUM_TARGETS*3,)),
        #     spaces.Box(low=LOW_EXCITATION, high=HIGH_EXCITATION, shape=(NUM_MUSCLES,))
        # ))
        self.observation_space = np.zeros(state_size)

        # init action space
        self.action_space = spaces.Box(low=LOW_EXCITATION, high=HIGH_EXCITATION, shape=(NUM_MUSCLES,))

    def get_state_dict(self):
        self.net.send(message_type=c.GET_STATE_STR)
        return self.net.receive_message(c.STATE_STR)

    def get_state_size(self):
        self.net.send(message_type=c.GET_STATE_SIZE_STR)
        rec_dict = self.net.receive_message(c.STATE_SIZE_STR, c.GET_STATE_SIZE_STR)
        return rec_dict[c.STATE_SIZE_STR]

    def state_dict2tensor(self, state):
        return torch.tensor(self.state_json_to_array(state))

    def get_state_tensor(self):
        state_dict = self.get_state_dict()
        return self.state_dict2tensor(state_dict)

    def take_action(self, action):
        # action = action.cpu().numpy()[0].tolist()
        self.net.send({'excitations': action.tolist()}, message_type='setExcitations')

    def calc_reward(self, state):
        reward = 0
        penalty = 0
        for target in state['sources']:
            pos = np.asarray(target['posVel'][:3])
            vel = np.asarray(target['posVel'][3:])
            penalty += np.linalg.norm(pos)
            penalty += np.linalg.norm(vel)

        excitations = np.asarray(state['excitations'])
        penalty += excitations.sum()

        return reward - penalty, False

    def step(self, action):
        self.take_action(action)
        time.sleep(WAIT_AFTER_ACTION)
        state = self.get_state_dict()

        if state is not None:
            reward, done = self.calc_reward(state)
            if done:
                logger.info('Done State')
            logger.info('Reward: ' + str(reward))
            # info = {'distance': distance}

            state_tensor = self.state_dict2tensor(state)
            reward_tensor = torch.tensor(reward)
        else:
            reward = 0
            done = False

        # todo: make sure state and reward do not need to be 2D list, same as [done] and [{}]!
        return state_tensor, reward, done, {}

    def reset(self):
        self.net.send(message_type='reset')
        logger.info('Reset')
        return self.get_state_tensor()

    def state_json_to_array(self, js):
        logger.debug('state json: %s', str(js))

        count = 0
        for target in js['targets']:
            pos_vel = np.asarray(target['posVel'])
            self.observation_space[count:count + POSITION_DIM + VELOCITY_DIM] = pos_vel
            count += POSITION_DIM + VELOCITY_DIM

        for target in js['sources']:
            pos_vel = np.asarray(target['posVel'])
            self.observation_space[count:count + POSITION_DIM + VELOCITY_DIM] = pos_vel
            count += POSITION_DIM + VELOCITY_DIM

        excitations = np.asarray(js['excitations'])
        self.observation_space[count:] = excitations
        count += excitations.shape[0]

        assert count == self.observation_space.shape[0]

        return self.observation_space

    def render(self, mode='gui', close=False):
        pass
