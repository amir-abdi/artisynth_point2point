import time
import json
import sys
import socket
import numpy as np
from socket import timeout as TimeoutException

from rl.core import Env
from rl.core import Space
from rl.core import Processor

from common.utilities import begin_time
from common import config as c

EPSILON = 1E-12


class PointModel2dEnv(Env):
    class ActionSpace(Space):
        def __init__(self, muscle_labels):
            self.dof_action = len(muscle_labels)
            self.shape = (self.dof_action,)
            self.muscle_labels = muscle_labels

        def sample(self, seed=None):
            if seed is not None:
                np.random.seed(seed)
            values = np.random.rand(self.dof_action)
            return dict(zip(self.muscle_labels, values))

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

    def __init__(self, muscle_labels=None, dof_observation=6, success_thres=0.1,
                 verbose=2, log_to_file=True, log_file='log', agent=None,
                 include_follow=True, port=6006):
        self.sock = None
        self.verbose = verbose
        self.success_thres = success_thres
        self.ref_pos = None

        self.action_space = type(self).ActionSpace(muscle_labels)
        self.observation_space = type(self).ObservationSpace(
            dof_observation)  # np.random.rand(dof_observation)
        self.log_to_file = log_to_file
        self.log_file_name = log_file
        if log_to_file:
            self.logfile, path = type(self).create_log_file(log_file)
            self.log('Logging into file: ' + path, verbose=1)
        self.agent = agent
        self.include_follow = include_follow
        self.port = port
        self.prev_distance = None
        self.muscle_labels = muscle_labels

    @staticmethod
    def create_log_file(log_file):
        log_folder = c.env_log_directory
        path = log_folder / (log_file + begin_time)
        return open(str(path), "w"), str(path)

    def log(self, obj, verbose=2, same_line=False):
        if self.log_to_file and not self.logfile.closed:
            self.logfile.writelines(str(obj))
            self.logfile.write("\n")

        if same_line:
            print(obj, sep=' ', end='\r',
                  flush=True) if verbose <= self.verbose else lambda: None
            print(obj, sep=' ', end=' ',
                  flush=True) if verbose <= self.verbose else lambda: None
        else:
            print(obj) if verbose <= self.verbose else lambda: None

    def send(self, obj=None, message_type=''):
        if not obj:
            obj = dict()
        try:
            obj.update({'type': message_type})
            json_obj = json.dumps(eval(str(obj)), ensure_ascii=False).encode(
                'utf-8')
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

    def receive(self, wait_time=0.0):
        # import struct
        try:
            self.sock.settimeout(wait_time)
            rec_int_bytes = []
            while len(rec_int_bytes) < 4:
                rec_int_bytes.extend(self.sock.recv(4 - len(rec_int_bytes)))
                if rec_int_bytes[0] == 10:
                    rec_int_bytes = rec_int_bytes[1:]
            rec_int = int(bytearray(rec_int_bytes).decode('utf-8'))
            if rec_int_bytes[:1] == b'\n':
                rec_int += 1
            rec_bytes = []
            while len(rec_bytes) < rec_int:
                rec_bytes.extend(self.sock.recv(rec_int - len(rec_bytes)))
            rec = bytearray(rec_bytes).decode("utf-8")
        except TimeoutException:
            self.log("Error: Socket timeout in receive", verbose=1)
            return None
        except ValueError as err:
            if rec_int_bytes == b'\n':
                return None
            else:
                self.log(err)
                raise err
        except:
            self.log("Unexpected error:" + str(sys.exc_info()), verbose=1)
            raise
        finally:
            self.sock.settimeout(0)
        self.log('obj rec: ' + str(rec))
        try:
            data_dict_result = json.loads(rec.strip())
            return data_dict_result
        except json.decoder.JSONDecodeError as e:
            self.log('error in receive: ' + str(e), verbose=1)
            return None

    def augment_action(self, action):
        return dict(zip(self.muscle_labels, np.nan_to_num(action)))

    def connect(self):
        self.log('Connecting...', verbose=1)
        port_number = self.port
        server_address = ('localhost', port_number)
        self.sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.sock.setblocking(1)
        self.sock.connect(server_address)
        self.log('Conneted to server at: {}'.format(server_address), verbose=1)

        import re
        send_name = re.sub('[./:*?]', '',
                           str(self.log_file_name).strip('0123456789'))
        self.set_name(send_name)

    def set_name(self, name):
        self.send({'name': name}, message_type='setName')

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
                self.log('Error in get_state receive data. Reconnecting...',
                         verbose=1)
                self.send(message_type='getState')
                rec_dict = self.receive(2)
        try:
            state = self.parse_state(rec_dict)
            return state
        except:
            self.log('Error in parsing get_state: ' + str(sys.exc_info()),
                     verbose=1)
            raise

    def set_state(self, state):
        self.set_state(state[:3], state[4:])

    def set_state(self, ref_pos, follower_pos):
        self.ref_pos = ref_pos
        self.follower_pos = follower_pos

    @staticmethod
    def parse_state(state_dict: dict):
        state = {'ref_pos': np.array(
            [float(s) for s in state_dict['ref_pos'].split(" ")]),
            'follow_pos': np.array(
                [float(s) for s in state_dict['follow_pos'].split(" ")])}
        return state

    def state_json_to_array(self, state_dict: dict):
        state_arr = state_dict['ref_pos']
        if self.include_follow:
            state_arr = np.concatenate((state_arr, state_dict['follow_pos']))
        return state_arr

    def reset(self):
        self.send(message_type='reset')
        self.ref_pos = None
        self.prev_distance = None
        self.log('Reset', verbose=0)
        state = self.get_state()
        state = self.state_json_to_array(state)
        return state

    def render(self, mode='human', close=False):
        # our environment does not need rendering
        pass

    def close(self):
        self.sock.close()

    def seed(self, seed=None):
        np.random.seed(seed)

    def configure(self, *args, **kwargs):
        pass

    def calculate_reward_pos(self, ref_pos, follow_pos, exp=True, constant=1):
        distance = type(self).calculate_distance(ref_pos, follow_pos)
        if distance < self.success_thres:
            # achieved done state
            return 1, True
        else:
            if exp:
                return constant * np.exp(-distance) - 50, False
            else:
                return constant / (distance + EPSILON), False

    @staticmethod
    def calculate_distance(a, b):
        return np.sqrt(np.sum((b - a) ** 2))

    def step(self, action):
        action = self.augment_action(action)
        self.send(action, 'excitations')
        # time.sleep(0.3)
        state = self.get_state()
        if state is not None:
            new_ref_pos = state['ref_pos']
            new_follower_pos = state['follow_pos']

            distance = self.calculate_distance(new_ref_pos, new_follower_pos)
            if self.prev_distance is not None:
                reward, done = self.calcualte_reward_time_5(distance,
                                                            self.prev_distance)  # r4
            else:
                reward, done = (0, False)
            self.prev_distance = distance
            if done:
                self.log('Achieved done state', verbose=0)
            self.log('Reward: ' + str(reward), verbose=1, same_line=True)

            state_arr = self.state_json_to_array(state)
            info = {'distance': distance}

        return state_arr, reward, done, info

    def calcualte_reward_move(self, ref_pos, prev_follow_pos,
                              new_follow_pos):  # r1
        prev_dist = type(self).calculate_distance(ref_pos, prev_follow_pos)

        new_dist = type(self).calculate_distance(ref_pos, new_follow_pos)
        if new_dist < self.success_thres:
            # Achieved done state
            return 1, True
        else:
            if prev_dist - new_dist > 0:
                return np.sign(
                    prev_dist - new_dist) * self.calculate_reward_pos(
                    ref_pos,
                    new_follow_pos,
                    False,
                    10), False
            else:
                return np.sign(
                    prev_dist - new_dist) * self.calculate_reward_pos(
                    ref_pos,
                    new_follow_pos,
                    False,
                    10), False

    def calcualte_reward_time_n5(self, new_dist, prev_dist):  # r2
        if new_dist < self.success_thres:
            # achieved done state
            return 5 / self.agent.episode_step, True
        else:
            if prev_dist - new_dist > 0:
                return 1 / self.agent.episode_step, False
            else:
                return -1, False

    def calcualte_reward_time_dist_n5(self, new_dist, prev_dist):  # r3
        if new_dist < self.success_thres:
            # achieved done state
            return 5 / self.agent.episode_step, True
        else:
            if prev_dist - new_dist > 0:
                return 1 / (self.agent.episode_step * new_dist), False
            else:
                return -1, False

    def calcualte_reward_time_5(self, new_dist, prev_dist):  # r4
        if new_dist < self.success_thres:
            # achieved done state
            return 5, True
        else:
            if prev_dist - new_dist > 0:
                return 1 / self.agent.episode_step, False
            else:
                return -1, False

    def calcualte_reward_time_dist_nn5(self, new_dist, prev_dist):  # r5
        if new_dist < self.success_thres:
            # achieved done state
            return 5 / (self.agent.episode_step * new_dist), True
        else:
            if prev_dist - new_dist > 0:
                return 1 / (self.agent.episode_step * new_dist), False
            else:
                return -1, False


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
