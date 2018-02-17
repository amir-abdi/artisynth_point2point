from src.import_file import *

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
    model.add(Activation('linear'))
    print(model.summary())
    return model


class MyBoltzmannQPolicy(BoltzmannQPolicy):
    def select_action(self, q_values):
        assert q_values.ndim == 1
        q_values = q_values.astype('float64')
        q_values = np.clip(q_values, 0, 1)
        return dict(zip(muscle_labels, q_values))


def main():
    while True:
        try:
            env = PointModel2dEnv(verbose=0, success_thres=0.5)
            env.connect()
            break
        except ConnectionRefusedError as e:
            print("Server not started: ", e)
            env.sock.close()
            time.sleep(10)
    try:
        env.seed(123)
        nb_actions = env.action_space.shape[0]
        memory = SequentialMemory(limit=50000, window_length=1)

        model_name = 'PointModel2D_middleSizeNet_myLogistic_moveReward_tanh'
        weight_filename = str(Path.cwd() / trained_directory / 'AC_{}_weights.h5f'.format(model_name))

        DQNAgent
        model = my_model(env)
        policy = MyBoltzmannQPolicy()
        agent = DQNAgent(model=model, nb_actions=nb_actions, memory=memory, nb_steps_warmup=10,
                       target_model_update=1e-2, policy=policy)

        action_input = Input(shape=(env.action_space.shape[0],), name='action_input')
        actor = my_actor(env)
        critic = my_critic(env, action_input)
        random_process = OrnsteinUhlenbeckProcess(size=nb_actions, theta=.25, mu=0., sigma=.25, dt=1e-1)
        agent = MyDDPGAgent(nb_actions=nb_actions, actor=actor, critic=critic, critic_action_input=action_input,
                          memory=memory, nb_steps_warmup_critic=100, nb_steps_warmup_actor=100,
                          random_process=random_process, gamma=.99, target_model_update=1e-3,
                          )

        # dqn.processor = PointModel2dProcessor()
        agent.compile(Adam(lr=1e-2), metrics=['mse'])
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



