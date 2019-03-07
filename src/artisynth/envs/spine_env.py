import os
import time
import logging

import gym
from gym import error, spaces
from gym.utils import seeding

import torch

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
POSITION_DIM = 3
VELOCITY_DIM = 3

COMPS = ['ribcageMotionTarget_l',
         'ribcageMotionTarget_r',
         'ribcageMotionTarget_l_ref',
         'ribcageMotionTarget_r_ref']


class SpineEnv(gym.Env):
    def __init__(self, ip, port, wait_action, eval_mode=False, reset_step=20,
                 init_artisynth=False,
                 include_excitations=True, include_current=True, include_velocity=True):
        self.sock = None

        self.episode_counter = 0
        self.reset_step = reset_step
        self.eval_mode = eval_mode

        if init_artisynth:
            logger.info('Running artisynth')
            self.run_artisynth(ip, port)

        self.ip = ip
        self.port = port

        self.net = Net(ip, port)
        self.wait_action = wait_action

        self.include_excitations = include_excitations
        self.include_current = include_current
        self.include_velocity = include_velocity

        self.action_size = self.get_action_size()
        obs = self.reset()
        logger.info('State array size: {}'.format(obs.shape))
        self.obs_size = obs.shape[0]

        self.observation_space = spaces.Box(low=-0.2, high=+0.2,
                                            shape=[self.obs_size], dtype=np.float32)

        self.observation_space.shape = (self.obs_size,)

        # init action space
        self.action_space = spaces.Box(low=LOW_EXCITATION, high=HIGH_EXCITATION,
                                       shape=(self.action_size,),
                                       dtype=np.float32)

    def run_artisynth(self, ip, port):
        if ip != 'localhost':
            raise NotImplementedError

        command = 'artisynth -model artisynth.models.lumbarSpine.RlLumbarSpine ' + \
                  '[ -port {} ] -play -noTimeline'. \
                      format(port)
        command_list = command.split(' ')

        import subprocess
        FNULL = open(os.devnull, 'w')
        subprocess.Popen(command_list, stdout=FNULL, stderr=subprocess.STDOUT)
        time.sleep(3)

    def get_state_dict(self):
        self.net.send(message_type=c.GET_STATE_STR)
        state_dict = self.net.receive_message(c.STATE_STR, retry_type=c.GET_STATE_STR)
        # self.observation_space.from_jsonable(state_dict)
        # print('self.observation_space', self.observation_space)
        return state_dict

    def get_state_size(self):
        self.net.send(message_type=c.GET_STATE_SIZE_STR)
        rec_dict = self.net.receive_message(c.STATE_SIZE_STR, c.GET_STATE_SIZE_STR)
        logger.info('State size: {}'.format(rec_dict[c.STATE_SIZE_STR]))
        return rec_dict[c.STATE_SIZE_STR]

    def get_action_size(self):
        self.net.send(message_type=c.GET_ACTION_SIZE_STR)
        rec_dict = self.net.receive_message(c.ACTION_SIZE_STR, c.GET_ACTION_SIZE_STR)
        logger.info('Action size: {}'.format(rec_dict[c.ACTION_SIZE_STR]))
        return rec_dict[c.ACTION_SIZE_STR]

    def state_dict2tensor(self, state):
        return torch.tensor(self.state_dic_to_array(state))

    def get_state_tensor(self):
        state_dict = self.get_state_dict()
        return self.state_dict2tensor(state_dict)

    def take_action(self, action):
        action = np.clip(action, LOW_EXCITATION, HIGH_EXCITATION)
        self.net.send({'excitations': action.tolist()}, message_type='setExcitations')

    def calc_reward(self, state):
        dist_thres = 0.013  # 3 cm
        vel_thres = 1.02
        done_reward = 5
        exc_coef = 0.1

        done = False
        reward = 0
        penalty = 0
        info = {'distance': 0,
                'vel': 0}

        # for i in range(4):
        #     pos = np.asarray(state[COMPS[i]][:3])
        #     logger.debug('pos {}:{}'.format(COMPS[i], pos))

        total_vel = 0
        for i in range(NUM_TARGETS):
            dist_vec = np.asarray(state[COMPS[i]][:3]) - np.asarray(state[COMPS[i + 2]][:3])
            pos_diff = np.linalg.norm(dist_vec)
            vel = np.linalg.norm(np.asarray(state[COMPS[i]][3:]))

            info['distance'] += pos_diff
            info['vel'] += vel

            # penalty += pos_diff * 10

            # todo: for now, no penalty for velocity is OK... maybe even never!
            # vel = np.asarray(state[COMPS[i]][3:])
            # penalty += np.linalg.norm(vel)

        info['distance'] /= NUM_TARGETS
        info['vel'] /= NUM_TARGETS

        # reward += 1 / np.power(100, info['distance'])

        if self.include_excitations:
            excitations = np.asarray(state['excitations'])
            penalty += np.linalg.norm(excitations) * exc_coef

        if info['distance'] <= dist_thres and \
                info['vel'] <= vel_thres:
            reward += done_reward
            done = True
            logger.info('** Done **')
        # print(info)

        logger.log(msg="dist={}, vel={}, penalty={}, reward={}".format(
            info['distance'], info['vel'], penalty, reward), level=18)

        # reward += 2
        return reward - penalty, done, info

    # todo: take this out of here!
    def step(self, action):
        self.episode_counter += 1

        self.take_action(action)
        time.sleep(self.wait_action)
        state = self.get_state_dict()

        if state is not None:
            reward, done, info = self.calc_reward(state)
            logger.log(msg='Reward: ' + str(reward), level=19)
            # info = {'distance': distance}

            state_array = self.state_dic_to_array(state)
            # reward_tensor = torch.tensor(reward)
        else:
            reward = 0
            done = False
            state_array = np.zeros(self.obs_size)
            info = {}

        # todo: get rid of this hack!
        if not self.eval_mode and \
                (done or self.episode_counter >= self.reset_step):
            self.episode_counter = 0
            done = True
            info['episode_'] = {}
            info['episode_']['distance'] = info['distance']

        if self.eval_mode:
            done = False

        # todo: make sure state and reward do not need to be 2D list, same as [done] and [{}]!
        return state_array, reward, done, info

    def reset(self):
        self.net.connect()
        self.net.send(message_type=c.RESET_STR)
        # logger.info('Reset')
        state_dict = self.get_state_dict()
        logger.log(msg=['Targets: %s %s',
                        (['{:.4f}'.format(x) for x in state_dict[COMPS[2]]]),
                        ['{:.4f}'.format(x) for x in state_dict[COMPS[3]]]], level=15)
        return self.state_dic_to_array(state_dict)

    def seed(self, seed=None):
        self.np_random, seed = seeding.np_random(seed)
        return [seed]

    def state_dic_to_array(self, js):
        logger.debug('state json: %s', str(js))

        observation_vector = []  # np.zeros(self.obs_size)

        count = 0

        start_index = 0
        if not self.include_current:
            start_index = 2

        for i in range(start_index, 4):
            if self.include_velocity and i < 2:
                observation_vector.extend(js[COMPS[i]])
            else:
                observation_vector.extend(js[COMPS[i]][:3])
            count += 1

        return np.asarray(observation_vector)

    def state_json_to_array_old(self, js):
        logger.debug('state json: %s', str(js))
        observation_vector = np.zeros(self.obs_size)

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

    def render(self, mode='gui', close=False):
        pass
