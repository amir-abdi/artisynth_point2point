from gi.overrides import override
from keras.models import Sequential
from keras.layers import Dense, Activation, Flatten
from keras.optimizers import Adam


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

muscle_labels = ["n", "nne", "ne", "ene",
                 "e", "ese", "se", "sse",
                 "s", "ssw", "sw", "wsw",
                 "w", "wnw", "nw", "nnw"]

'''
Here, I'm assuming each episode to run until it reaches the ref_pos
So, nb_max_episode_steps is going to be None

Once we reach the terminal state, 'done' is set to True
'''


class MyDDPGAgen(DDPGAgent):
    def select_action(self, state):
        batch = self.process_state_batch([state])
        action = self.actor.predict_on_batch(batch).flatten()
        assert action.shape == (self.nb_actions,)

        # Apply noise, if a random process is set.
        if self.training and self.random_process is not None:
            noise = self.random_process.sample()
            assert noise.shape == action.shape
            action += noise
            action = np.clip(action, 0, 1)  # to avoid using negative and above 1 values for excitations

        return action



class MyBoltzmannQPolicy(BoltzmannQPolicy):
    def select_action(self, q_values):
        assert q_values.ndim == 1
        q_values = q_values.astype('float64')
        q_values = np.clip(q_values, 0, 1)
        return dict(zip(muscle_labels, q_values))


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
    def __init__(self, sock: socket, dof_action=16, dof_observation=6, success_thres=0.1,
                 verbose=2, log_to_file=True, log_file='log'):
        self.sock = sock
        self.verbose = verbose
        self.success_thres = success_thres
        self.state = None

        self.action_space = ActionSpace(dof_action)
        self.observation_space = ObservationSpace(dof_observation) #np.random.rand(dof_observation)
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

    def log(self, obj, verbose=2):
        if self.log_to_file and not self.logfile.closed:
            self.logfile.writelines(str(obj))
            self.logfile.write("\n")
        print(obj) if verbose <= self.verbose else lambda: None

    def send(self, obj=dict(), message_type=''):
        obj.update({'type': message_type})
        json_obj = json.dumps(eval(str(obj)), ensure_ascii=False).encode('utf-8')
        bytes_sent = self.sock.send(json_obj)
        if bytes_sent < json_obj.__len__():
            print('Data not sent completely: ' + str(bytes_sent) + ' < ' +
                  str(json_obj.__len__()))
        self.log('obj sent: ' + str(obj))

    def receive(self):
        res = self.sock.recv(1024).decode("utf-8")
        self.log('obj rec: ' + str(res))
        try:
            data_dict_result = json.loads(res)
            return data_dict_result
        except json.decoder.JSONDecodeError as e:
            self.log('error in receive: ' + str(e))
            return None

    @staticmethod
    def augment_action(action):
        return dict(zip(muscle_labels, action))

    def step(self, action):
        # todo: assuming that action is always a numpy array of excitations.
        action = PointModel2dEnv.augment_action(action)
        self.send(action, 'excitations')
        state = self.get_state()
        if state is not None:
            self.set_state(state)
            ref_pos = state[0:3]
            follower_pos = state[3:6]
            distance = self.calculate_distance(ref_pos, follower_pos)
            reward = self.calculate_reward(distance)
            done = True if distance < self.success_thres else False
            if done:
                self.log('Achieved done state')
            return state, reward, done, dict()

    def get_state(self):
        self.send(message_type='getState')
        rec_dict = self.receive()
        try:
            if rec_dict['type'] == 'state':
                state = PointModel2dEnv.parse_state(rec_dict)
                return state
        except:
            self.log('Error in get_state')
        return None

    def set_state(self, state):
        self.state = state

    @staticmethod
    def parse_state(state_dict: dict):
        ref_pos = np.array([float(s) for s in state_dict['ref_pos'].split(" ")])
        follower_pos = np.array([float(s) for s in state_dict['follow_pos'].split(" ")])
        return np.concatenate((ref_pos, follower_pos))

    def reset(self):
        self.send(message_type='reset')
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
    def calculate_reward(ref_pos, follow_pos):
        return 1/(PointModel2dEnv.calculate_distance(ref_pos, follow_pos) + EPSILON)

    @staticmethod
    def calculate_reward(distance):
        return 1/(distance + EPSILON)

    @staticmethod
    def calculate_distance(a, b):
        return np.sqrt(np.sum((b - a) ** 2))




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
