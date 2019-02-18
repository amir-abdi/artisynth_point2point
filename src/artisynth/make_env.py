import os

import gym
import numpy as np
import torch
from gym.spaces.box import Box

from baselines import bench
from baselines.common.atari_wrappers import make_atari, wrap_deepmind
from baselines.common.vec_env import VecEnvWrapper
from baselines.common.vec_env.subproc_vec_env import SubprocVecEnv
from baselines.common.vec_env.dummy_vec_env import DummyVecEnv
from baselines.common.vec_env.vec_normalize import VecNormalize as VecNormalize_, VecNormalize

from a2c_ppo_acktr.envs import VecPyTorch, VecPyTorchFrameStack


def make_env(env_id, seed, rank, log_dir, add_timestep, allow_early_resets, **kwargs):
    def _thunk():
        env = gym.make(env_id, **kwargs)

        env.seed(seed + rank)

        # obs_shape = env.observation_space.shape

        # amirabdi: my understanding is that timestep is when "time" is part of the
        # state definition... I don't see it play a role anytime soon.
        # if add_timestep and len(obs_shape) == 1 and str(env).find('TimeLimit') > -1:
        #     env = AddTimestep(env)

        if log_dir is not None:
            env = bench.Monitor(env, os.path.join(log_dir, str(rank)),
                                allow_early_resets=allow_early_resets)
        return env

    return _thunk


def make_vec_envs(env_name, seed, num_processes, gamma, log_dir, add_timestep,
                  device, allow_early_resets, num_frame_stack=None,
                  ip='localhost', port=8097):
    envs = [make_env(env_name, seed, i, log_dir, add_timestep, allow_early_resets,
                     ip=ip, port=port, name=env_name)
            for i in range(num_processes)]

    if len(envs) > 1:
        envs = SubprocVecEnv(envs)
    else:
        envs = DummyVecEnv(envs)

    if len(envs.observation_space.shape) == 1:
        if gamma is None:
            envs = VecNormalize(envs, ret=False)
        else:
            envs = VecNormalize(envs, gamma=gamma)

    envs = VecPyTorch(envs, device)

    if num_frame_stack is not None:
        envs = VecPyTorchFrameStack(envs, num_frame_stack, device)
    elif len(envs.observation_space.shape) == 3:
        envs = VecPyTorchFrameStack(envs, 4, device)

    return envs