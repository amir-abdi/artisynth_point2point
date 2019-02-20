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

LOW_POS = -0.3
HIGH_POS = +1.8
LOW_VEL = -3
HIGH_VEL = +3
LOW_EXCITATION = 0
HIGH_EXCITATION = 1
NUM_TARGETS = 2
NUM_MUSCLES = 119
POSITION_DIM = 3
VELOCITY_DIM = 3

COMPS = ['ribcageMotionTarget_l',
         'ribcageMotionTarget_r',
         'ribcageMotionTarget_l_ref',
         'ribcageMotionTarget_r_ref']


class SpineEnv(gym.Env):
    def __init__(self, name, ip, port, wait_action):
        self.sock = None
        self.net = Net(ip, port)
        self.wait_action = wait_action

        # init observation space
        # todo: set the right limits for velocity and position as we normalize values
        # before sending to agent

        self.state_size = self.get_state_size()
        
        # logger.debug('state size %i', state_size)
        # self.observation_space = spaces.Tuple((
        #     spaces.Box(low=LOW_VEL, high=HIGH_VEL, shape=(NUM_TARGETS*3,)),
        #     spaces.Box(low=LOW_POS, high=HIGH_POS, shape=(NUM_TARGETS*3,)),
        #     spaces.Box(low=LOW_EXCITATION, high=HIGH_EXCITATION, shape=(NUM_MUSCLES,))
        # ))

        # self.observation_space = spaces.Dict({
        #     'ribcageMotionTarget_l': spaces.Box(low=LOW_VEL, high=HIGH_VEL, shape=(6,)),
        #     'ribcageMotionTarget_r': spaces.Box(low=LOW_VEL, high=HIGH_VEL, shape=(6,)),
        #     'ribcageMotionTarget_l_ref': spaces.Box(low=LOW_VEL, high=HIGH_VEL, shape=(6,)),
        #     'ribcageMotionTarget_r_ref': spaces.Box(low=LOW_VEL, high=HIGH_VEL, shape=(6,)),
        #     'excitations': spaces.Box(low=LOW_EXCITATION, high=HIGH_EXCITATION, shape=(NUM_MUSCLES,))
        # })

        # self.observation_space = np.zeros(self.state_size)

        # todo: fix this!!!
        self.observation_space = spaces.Box(low=LOW_VEL, high=HIGH_VEL,
                                            shape=[self.state_size], dtype=np.float32)
        self.observation_space.shape = (self.state_size,)

        # init action space
        self.action_space = spaces.Box(low=LOW_EXCITATION, high=HIGH_EXCITATION, shape=(NUM_MUSCLES,),
                                       dtype=np.float32)

    def get_state_dict(self):
        self.net.send(message_type=c.GET_STATE_STR)
        state_dict = self.net.receive_message(c.STATE_STR, retry_type=c.GET_STATE_STR)
        # self.observation_space.from_jsonable(state_dict)
        # print('self.observation_space', self.observation_space)
        return state_dict

    def get_state_size(self):
        self.net.send(message_type=c.GET_STATE_SIZE_STR)
        rec_dict = self.net.receive_message(c.STATE_SIZE_STR, c.GET_STATE_SIZE_STR)
        return rec_dict[c.STATE_SIZE_STR]

    def get_state_array(self):
        state_dict = self.get_state_dict()
        return self.state_dict2array(state_dict)

    def state_dict2tensor(self, state):
        return torch.tensor(self.state_json_to_array(state))

    def get_state_tensor(self):
        state_dict = self.get_state_dict()
        return self.state_dict2tensor(state_dict)

    def take_action(self, action):
        # action = action.cpu().numpy()[0].tolist()
        logger.debug('take_action before clipping: {}'.format(action))
        action = np.clip(action, LOW_EXCITATION, HIGH_EXCITATION)
        self.net.send({'excitations': action.tolist()}, message_type='setExcitations')

    def calc_reward(self, state):
        reward = 0
        penalty = 0

        for i in range(4):
            pos = np.asarray(state[COMPS[i]][:3])
            logger.debug('pos {}:{}'.format(COMPS[i], pos))

        for i in range(2):
            pos_diff = np.asarray(state[COMPS[i]][:3]) - np.asarray(state[COMPS[i+2]][:3])
            penalty += np.linalg.norm(pos_diff)

            vel = np.asarray(state[COMPS[i]][3:])
            penalty += np.linalg.norm(vel)

            logger.log(msg="pos_diff={}, vel={}, penalty={}".format(pos_diff, vel, penalty),
                       level=18)

        # excitations = np.asarray(state['excitations'])
        # penalty += excitations.sum()

        return reward - penalty, False

    def step(self, action):
        self.take_action(action)
        time.sleep(self.wait_action)
        state = self.get_state_dict()

        if state is not None:
            reward, done = self.calc_reward(state)
            if done:
                logger.info('Done State')
            logger.log(msg='Reward: ' + str(reward), level=19)
            # info = {'distance': distance}

            state_tensor = self.state_dict2tensor(state)
            # reward_tensor = torch.tensor(reward)
        else:
            reward = 0
            done = False

        # todo: make sure state and reward do not need to be 2D list, same as [done] and [{}]!
        return state_tensor, reward, done, {}

    def reset(self):
        self.net.send(message_type='reset')
        logger.info('Reset')
        # return self.get_state_tensor()
        state_dict = self.get_state_dict()
        return self.state_json_to_array(state_dict)
        # return state_dict

    def seed(self, seed=None):
        self.np_random, seed = seeding.np_random(seed)
        return [seed]

    def state_json_to_array(self, js):
        logger.debug('state json: %s', str(js))
        observation_vector = np.zeros(self.state_size)

        count = 0
        for comp in COMPS:
            observation_vector[count * 6:(count + 1) * 6] = js[comp]
            count += 1
        assert count == len(COMPS)

        excitations = np.asarray(js['excitations'])
        observation_vector[count * 6:] = excitations
        assert count*6 + len(excitations) == self.observation_space.shape[0]

        return observation_vector

    def state_json_to_array_old(self, js):
        logger.debug('state json: %s', str(js))
        observation_vector = np.zeros(self.state_size)

        count = 0
        for target in js['targets']:
            pos_vel = np.asarray(target['posVel'])
            observation_vector[count:count + POSITION_DIM + VELOCITY_DIM] = pos_vel
            count += POSITION_DIM + VELOCITY_DIM

        for target in js['sources']:
            pos_vel = np.asarray(target['posVel'])
            observation_vector[count:count + POSITION_DIM + VELOCITY_DIM] = pos_vel
            count += POSITION_DIM + VELOCITY_DIM

        excitations = np.asarray(js['excitations'])
        observation_vector[count:] = excitations
        count += excitations.shape[0]

        assert count == observation_vector.shape[0]

        return observation_vector

    # def state_json_to_dict(self, js):
    #     logger.debug('state json: %s', str(js))
    #
    #     count = 0
    #     for target in js['targets']:
    #         pos_vel = np.asarray(target['posVel'])
    #         self.observation_space['pos'][POSITION_DIM * count:POSITION_DIM * (count + 1)] = \
    #             pos_vel[:POSITION_DIM]
    #         self.observation_space['vel'][POSITION_DIM * count:POSITION_DIM * (count + 1)] = \
    #             pos_vel[POSITION_DIM:POSITION_DIM + VELOCITY_DIM]
    #         count += 1
    #
    #     for target in js['sources']:
    #         pos_vel = np.asarray(target['posVel'])
    #         self.observation_space['pos'][POSITION_DIM * count:POSITION_DIM * (count + 1)] = \
    #             pos_vel[:POSITION_DIM]
    #         self.observation_space['vel'][POSITION_DIM * count:POSITION_DIM * (count + 1)] = \
    #             pos_vel[POSITION_DIM:POSITION_DIM + VELOCITY_DIM]
    #         count += 1
    #
    #     assert count == NUM_TARGETS * 2
    #
    #     self.observation_space['excitations'] = np.asarray(js['excitations'])
    #
    #     return self.observation_space

    def render(self, mode='gui', close=False):
        pass
