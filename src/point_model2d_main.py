from fileinput import filename
from pathlib import Path
import socket
import sys
from src.config import keras_rl_path
from src.config import trained_directory_path
sys.path.append(keras_rl_path)
import numpy as np
from src.point_model2d import *
from rl.agents.dqn import DQNAgent
from rl.agents.dqn import NAFAgent
from rl.agents.ddpg import DDPGAgent
from rl.random import OrnsteinUhlenbeckProcess
from keras.utils.generic_utils import get_custom_objects
from pathlib import Path


def load_weights(agent, weight_filename):
    import os
    filename_temp, extension = os.path.splitext(weight_filename)
    trained_dir = (Path.cwd() / trained_directory_path).absolute()
    if Path.exists((trained_dir / (filename_temp + '_actor.h5f'))):
        agent.load_weights(str(trained_dir / weight_filename))
        print('weights loaded from ', str(trained_dir / weight_filename ))


def main():
    get_custom_objects().update({'mylogistic': Activation(mylogistic)})

    while True:
        try:
            env = PointModel2dEnv(verbose=1, success_thres=0.5)
            env.connect()
            # sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            # server_address = ('localhost', 6611)
            # sock.setblocking(1)
            # sock.connect(server_address)
            break
        except ConnectionRefusedError as e:
            print("Server not started: ", e)
            env.sock.close()
            time.sleep(10)
    try:
        env.seed(123)
        nb_actions = env.action_space.shape[0]
        memory = SequentialMemory(limit=50000, window_length=1)

        model_name = 'PointModel2D_middleSizeNet_myLogistic_moveReward'
        weight_filename = 'AC_{}_weights.h5f'.format(model_name)

        # DQNAgent
        # model = my_model(env)
        # policy = MyBoltzmannQPolicy()
        # agent = DQNAgent(model=model, nb_actions=nb_actions, memory=memory, nb_steps_warmup=10,
        #                target_model_update=1e-2, policy=policy)

        action_input = Input(shape=(env.action_space.shape[0],), name='action_input')
        actor = my_actor(env)
        critic = my_critic(env, action_input)
        random_process = OrnsteinUhlenbeckProcess(size=nb_actions, theta=.25, mu=0., sigma=.25, dt=1e-1)
        agent = MyDDPGAgent(nb_actions=nb_actions, actor=actor, critic=critic, critic_action_input=action_input,
                          memory=memory, nb_steps_warmup_critic=100, nb_steps_warmup_actor=100,
                          random_process=random_process, gamma=.99, target_model_update=1e-3,
                          )

        # dqn.processor = PointModel2dProcessor()
        agent.compile(Adam(lr=1e+0), metrics=['mae'])
        load_weights(agent, weight_filename)

        agent.fit(env, nb_steps=500000, visualize=False, verbose=2, nb_max_episode_steps=2000)

        print('Training complete')

        agent.save_weights(weight_filename, overwrite=True)
        print('results saved to ', weight_filename)

        # test code
        # env.log_to_file = False

        # agent.load_weights(str(Path.cwd() / filename))
        # agent.test(env, nb_episodes=5, visualize=True)

    except Exception as e:
        print("Error in main code:", str(e))
        agent.save_weights(weight_filename, overwrite=True)
        print('results saved to ', weight_filename)
        env.sock.close()
        raise e


if __name__ == "__main__":
    main()



