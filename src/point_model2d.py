from gi.overrides import override
from keras.models import Sequential
from keras.layers import Dense, Activation, Flatten
from keras.optimizers import Adam

import time
from socket import timeout as TimeoutException
from rl.policy import BoltzmannQPolicy
from rl.memory import SequentialMemory
from rl.core import Env
from rl.core import Space
from rl.core import Processor
import json
import numpy as np
from src.consts import EPSILON
import socket
from typing import Union
# from typing import get_type_hints
from rl.agents import DDPGAgent
from pathlib import Path
from keras.layers import Dense, Activation, Flatten, Input, Concatenate
import time
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


muscle_labels = ["n", "nne", "ne", "ene",
                 "e", "ese", "se", "sse",
                 "s", "ssw", "sw", "wsw",
                 "w", "wnw", "nw", "nnw"]


class ActionSpace(Space):
    def __init__(self, dof_action=12):
        self.shape = (dof_action,)
        self.dof_action = dof_action

    def sample(self, seed=None):
        if seed is not None:
            np.random.seed(seed)
        values = np.random.rand(self.dof_action)
        return dict(zip(muscle_labels, values))

    def contains(self, x):
        if x.ndim != 1:
            return False
        if x.shape[0] != self.dof_action:
            return False
        if np.max(x) > 1 or np.min(x) < 0:
            return False
        return True


class ObservationSpace(Space):
    def __init__(self, dof_obs=6, radius=4.11):
        self.shape = (dof_obs,)
        self.dof_obs = dof_obs
        self.radius = radius

    def sample(self, seed=None):
        if seed is not None:
            np.random.seed(seed)
        return (np.random.rand(self.dof_obs) - 0.5) * self.radius * 2

    def contains(self, x):
        if x.ndim != 1:
            return False
        if x.shape[0] != self.dof_obs:
            return False
        if np.max(x) > self.radius or np.min(x) < -self.radius:
            return False
        return True


class PointModel2dEnv(Env):
    def __init__(self, dof_action=16, dof_observation=6, success_thres=0.1,
                 verbose=2, log_to_file=True, log_file='log'):
        self.sock = None
        self.verbose = verbose
        self.success_thres = success_thres
        self.ref_pos = None
        self.follower_pos = None

        self.action_space = ActionSpace(dof_action)
        self.observation_space = ObservationSpace(dof_observation)  # np.random.rand(dof_observation)
        self.log_to_file = log_to_file
        if log_to_file:
            self.logfile, path = PointModel2dEnv.create_log_file(log_file)
            self.log('Logging into file: ' + path, verbose=1)

    @staticmethod
    def create_log_file(log_file):
        log_counter = 0
        path = Path.cwd() / '..' / 'logs' / (log_file + str(log_counter))
        while Path.exists(path):
            log_counter += 1
            path = Path.cwd() / '..' / 'logs' / (log_file + str(log_counter))
        return open(str(path), "w"), str(path)

    def log(self, obj, verbose=2, same_line=False):
        if self.log_to_file and not self.logfile.closed:
            self.logfile.writelines(str(obj))
            self.logfile.write("\n")

        if same_line:
            print(obj, sep=' ', end='\r', flush=True) if verbose <= self.verbose else lambda: None
            print(obj, sep=' ', end=' ', flush=True) if verbose <= self.verbose else lambda: None
        else:
            print(obj) if verbose <= self.verbose else lambda: None

    def send(self, obj=dict(), message_type=''):
        try:
            obj.update({'type': message_type})
            json_obj = json.dumps(eval(str(obj)), ensure_ascii=False).encode('utf-8')
            objlen = json_obj.__len__()
            self.sock.send(objlen.to_bytes(4, byteorder='big'))
            bytes_sent = self.sock.send(json_obj)
            while bytes_sent < objlen:
                print('Data not sent completely: ' + str(bytes_sent) + ' < ' +
                      str(json_obj.__len__()))
                bytes_sent = self.sock.send(json_obj)
            self.log('obj sent: ' + str(obj))
        except NameError as err:
            self.log('error in send: ' + str(err), verbose=1)

    def receive(self, wait_time=0):
        import struct
        try:
            self.sock.settimeout(wait_time)
            rec_int_bytes = self.sock.recv(4) #.decode("utf-8")
            rec_int = struct.unpack("!i", rec_int_bytes)[0]
            rec = self.sock.recv(rec_int).decode("utf-8")
        except TimeoutException:
            self.log("Error: Socket timeout in receive", verbose=1)
            return None
        finally:
            self.sock.settimeout(0)
        # rec_int = int(rec_int_bytes)  #int.from_bytes(rec_int_bytes, byteorder='big')
        self.log('obj rec: ' + str(rec))
        try:
            data_dict_result = json.loads(rec)
            return data_dict_result
        except json.decoder.JSONDecodeError as e:
            self.log('error in receive: ' + str(e), verbose=1)
            return None

    @staticmethod
    def augment_action(action):
        return dict(zip(muscle_labels, np.nan_to_num(action)))

    def step(self, action):
        # todo: assuming that action is always a numpy array of excitations.
        action = PointModel2dEnv.augment_action(action)
        self.send(action, 'excitations')
        time.sleep(0.2)
        state = self.get_state()
        if state is not None:
            new_ref_pos = state[0:3]
            new_follower_pos = state[3:6]
            distance = self.calculate_distance(new_ref_pos, new_follower_pos)
            # reward = self.calculate_reward(distance)
            if self.follower_pos is not None:
                reward = PointModel2dEnv.calcualte_reward_move(new_ref_pos, self.follower_pos, new_follower_pos)
            else:
                reward = 0
            self.set_state(new_ref_pos, new_follower_pos)
            done = True if distance < self.success_thres else False
            if done:
                self.log('Achieved done state', verbose=0)
                reward = 10
            self.log('Reward: ' + str(reward), verbose=1, same_line=True)
            return state, reward, done, dict()

    def connect(self):
        self.log('Connecting...', verbose=1)
        port_number = 6611
        server_address = ('localhost', port_number)
        self.sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.sock.setblocking(1)
        self.sock.connect(server_address)
        self.log('Conneted to server at: {}'.format(server_address), verbose=1)

    def get_state(self):
        self.send(message_type='getState')
        rec_dict = self.receive(0.5)
        while True:
            try:
                if rec_dict['type'] == 'state':
                    break
                else:
                    raise Exception
            except:
                self.log('Error in get_state receive data. Reconnecting...', verbose=1)
                self.connect()
                self.send(message_type='getState')
                rec_dict = self.receive(2)
        try:
            state = PointModel2dEnv.state_json_to_array(rec_dict)
            return state
        except:
            self.log('Error in parsing get_state', verbose=1)
        return None

    def set_state(self, state):
        self.set_state(state[:3], state[4:])

    def set_state(self, ref_pos, follower_pos):
        self.ref_pos = ref_pos
        self.follower_pos = follower_pos

    @staticmethod
    def state_json_to_array(state_dict: dict):
        ref_pos = np.array([float(s) for s in state_dict['ref_pos'].split(" ")])
        follower_pos = np.array([float(s) for s in state_dict['follow_pos'].split(" ")])
        return np.concatenate((ref_pos, follower_pos))

    def reset(self):
        self.send(message_type='reset')
        self.ref_pos = None
        self.follower_pos = None
        self.log('Reset', verbose=0)
        return self.get_state()

    def render(self, mode='human', close=False):
        # our environment does not need rendering
        pass

    def close(self):
        self.sock.close()

    def seed(self, seed=None):
        np.random.seed(seed)

    def configure(self, *args, **kwargs):
        pass

    def calculate_excitations(self, ref_pos, follow_pos):
        # forward pass of NN with positions to get values
        values = [0, 0, 0, 0,
                  0, 1, 0, 0,
                  0, 0, 0, 1,
                  0, 0, 0, 0]
        return dict(zip(muscle_labels, values))

    @staticmethod
    def calculate_reward_pos(ref_pos, follow_pos, exp=True, constant=1):
        if exp:
            return constant * np.exp(-PointModel2dEnv.calculate_distance(ref_pos, follow_pos)) - 50
        else:
            return constant/(PointModel2dEnv.calculate_distance(ref_pos, follow_pos) + EPSILON)

    @staticmethod
    def calculate_distance(a, b):
        return np.sqrt(np.sum((b - a) ** 2))

    @staticmethod
    def calcualte_reward_move(ref_pos, prev_follow_pos, new_follow_pos):
        prev_dist = PointModel2dEnv.calculate_distance(ref_pos, prev_follow_pos)

        new_dist = PointModel2dEnv.calculate_distance(ref_pos, new_follow_pos)
        if prev_dist - new_dist > 0:
            return np.sign(prev_dist - new_dist) * PointModel2dEnv.calculate_reward_pos(ref_pos,
                                                                                        new_follow_pos,
                                                                                        False,
                                                                                        10)
        else:
            return np.sign(prev_dist - new_dist) * PointModel2dEnv.calculate_reward_pos(ref_pos,
                                                                                        new_follow_pos,
                                                                                        False,
                                                                                        10)




class PointModel2dProcessor(Processor):
    """Abstract base class for implementing processors.
        A processor acts as a coupling mechanism between an `Agent` and its `Env`. This can
        be necessary if your agent has different requirements with respect to the form of the
        observations, actions, and rewards of the environment. By implementing a custom processor,
        you can effectively translate between the two without having to change the underlaying
        implementation of the agent or environment.
        Do not use this abstract base class directly but instead use one of the concrete implementations
        or write your own.
        """

    def process_step(self, observation, reward, done, info):
        """Processes an entire step by applying the processor to the observation, reward, and info arguments.
        # Arguments
            observation (object): An observation as obtained by the environment.
            reward (float): A reward as obtained by the environment.
            done (boolean): `True` if the environment is in a terminal state, `False` otherwise.
            info (dict): The debug info dictionary as obtained by the environment.
        # Returns
            The tupel (observation, reward, done, reward) with with all elements after being processed.
        """
        observation = self.process_observation(observation)
        reward = self.process_reward(reward)
        info = self.process_info(info)
        return observation, reward, done, info

    def process_observation(self, observation):
        """Processes the observation as obtained from the environment for use in an agent and
        returns it.
        """
        return observation

    def process_reward(self, reward):
        """Processes the reward as obtained from the environment for use in an agent and
        returns it.
        """
        return reward

    def process_info(self, info):
        """Processes the info as obtained from the environment for use in an agent and
        returns it.
        """
        return info

    def process_action(self, action):
        """Processes an action predicted by an agent but before execution in an environment.
        """
        return action

    def process_state_batch(self, batch):
        """Processes an entire batch of states and returns it.
        """
        return batch

    @property
    def metrics(self):
        """The metrics of the processor, which will be reported during training.
        # Returns
            List of `lambda y_true, y_pred: metric` functions.
        """
        return []

    @property
    def metrics_names(self):
        """The human-readable names of the agent's metrics. Must return as many names as there
        are metrics (see also `compile`).
        """
        return []
