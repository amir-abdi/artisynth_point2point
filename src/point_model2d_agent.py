from fileinput import filename
from pathlib import Path
import socket
import sys
from src.config import keras_rl_path
sys.path.append(keras_rl_path)
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
import numpy as np
from src.point_model2d_env import *
from rl.agents.dqn import DQNAgent
from rl.agents.dqn import NAFAgent
from rl.agents.ddpg import DDPGAgent
from rl.random import OrnsteinUhlenbeckProcess
from keras import backend as K
from keras.utils.generic_utils import get_custom_objects
from pathlib import Path

# todo: I'm assuming that state is only the two positions (excitations are not part of state)


def my_model(env):
    # Next, we build a very simple model.
    model = Sequential()
    model.add(Flatten(input_shape=(1,) + env.observation_space.shape, name='FirstFlatten'))
    model.add(Dense(16))
    model.add(Activation('relu'))
    model.add(Dense(16))
    model.add(Activation('relu'))
    model.add(Dense(16))
    model.add(Activation('relu'))
    model.add(Dense(env.action_space.shape[0]))
    model.add(Activation('relu'))
    print(model.summary())
    return model


def mylogistic(x):
    return 1 / (1 + K.exp(-0.1 * x))


def my_actor(env):
    actor = Sequential()
    actor.add(Flatten(input_shape=(1,) + env.observation_space.shape))
    actor.add(Dense(16))
    actor.add(Activation('relu'))
    # actor.add(Dense(16))
    # actor.add(Activation('relu'))
    # actor.add(Dense(16))
    # actor.add(Activation('relu'))
    actor.add(Dense(env.action_space.shape[0]))

    actor.add(Activation(mylogistic))
    print(actor.summary())
    return actor


def my_critic(env, action_input):
    observation_input = Input(shape=(1,) + env.observation_space.shape, name='observation_input')
    flattened_observation = Flatten()(observation_input)
    x = Concatenate()([action_input, flattened_observation])
    # x = Dense(32)(x)
    # x = Activation('relu')(x)
    # x = Dense(32)(x)
    # x = Activation('relu')(x)
    x = Dense(32)(x)
    x = Activation('relu')(x)
    x = Dense(1)(x)
    x = Activation('relu')(x)  # Since reward=1/distance, it's always going to be positive
    critic = Model(inputs=[action_input, observation_input], outputs=x)
    print(critic.summary())
    return critic


def load_weights(agent, weight_filename ):
    import os
    filename_temp, extension = os.path.splitext(weight_filename )
    if Path.exists((Path.cwd() / (filename_temp + '_actor.h5f'))):
        agent.load_weights(str(Path.cwd() / weight_filename ))
        print('weights loaded from ', str(Path.cwd() / weight_filename ))

def main():
    get_custom_objects().update({'mylogistic': Activation(mylogistic)})

    while True:
        try:
            sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            server_address = ('localhost', 6611)
            sock.setblocking(1)
            sock.connect(server_address)
            break
        except ConnectionRefusedError as e:
            print("Server not started: ", e)
            sock.close()
            time.sleep(10)
    try:
        env = PointModel2dEnv(sock, verbose=1)
        env.seed(123)
        nb_actions = env.action_space.shape[0]
        memory = SequentialMemory(limit=50000, window_length=1)

        model_name = 'PointModel2D_tinyNet'
        weight_filename = 'AC_{}_weights.h5f'.format(model_name)

        # DQNAgent
        # model = my_model(env)
        # policy = MyBoltzmannQPolicy()
        # agent = DQNAgent(model=model, nb_actions=nb_actions, memory=memory, nb_steps_warmup=10,
        #                target_model_update=1e-2, policy=policy)

        action_input = Input(shape=(env.action_space.shape[0],), name='action_input')
        actor = my_actor(env)
        critic = my_critic(env, action_input)
        random_process = OrnsteinUhlenbeckProcess(size=nb_actions, theta=.15, mu=0., sigma=.15, dt=1e-1)
        agent = MyDDPGAgent(nb_actions=nb_actions, actor=actor, critic=critic, critic_action_input=action_input,
                          memory=memory, nb_steps_warmup_critic=100, nb_steps_warmup_actor=100,
                          random_process=random_process, gamma=.99, target_model_update=1e-3,
                          )
        # dqn.processor = PointModel2dProcessor()
        agent.compile(Adam(lr=1e-4), metrics=['mae'])
        load_weights(agent, weight_filename)

        agent.fit(env, nb_steps=500000, visualize=False, verbose=2, nb_max_episode_steps=300)
        print('Training complete')
        agent.save_weights(weight_filename, overwrite=True)
        print('results saved to ', weight_filename)

        # test code
        # env.log_to_file = False

        # agent.load_weights(str(Path.cwd() / filename))
        # agent.test(env, nb_episodes=5, visualize=True)

    except Exception as e:
        print("Error in main code:", str(e))
        raise e


if __name__ == "__main__":
    main()



