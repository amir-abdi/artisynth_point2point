from src.import_file import *
from src.utilities import *
import src.config as c
# from rl.callbacks import RlTensorBoard


class MyDDPGAgent(DDPGAgent):
    def select_action(self, state):
        batch = self.process_state_batch([state])
        action = self.actor.predict_on_batch(batch).flatten()
        assert action.shape == (self.nb_actions,)

        # Apply noise, if a random process is set.
        if self.training and self.random_process is not None:
            noise = self.random_process.sample()
            assert noise.shape == action.shape
            action += noise
            # the below is necessary even if using logistic or sigmoid activations (because of noise)
            action = np.clip(action, 0, 1)  # to avoid using negative and above 1 values for excitations

        return action


def my_actor(env):
    actor = Sequential()
    actor.add(Flatten(input_shape=(1,) + env.observation_space.shape))
    actor.add(Dense(128))
    actor.add(Activation('tanh'))
    actor.add(Dense(128))
    actor.add(Activation('tanh'))
    # actor.add(Dense(16))
    # actor.add(Activation('relu'))
    actor.add(Dense(env.action_space.shape[0]))

    actor.add(Activation('sigmoid'))
    # actor.add(Activation('linear'))
    print(actor.summary())
    return actor


def my_critic(env, action_input):
    observation_input = Input(shape=(1,) + env.observation_space.shape, name='observation_input')
    flattened_observation = Flatten()(observation_input)
    x = Concatenate()([action_input, flattened_observation])
    # x = Dense(32)(x)
    # x = Activation('relu')(x)
    x = Dense(64)(x)
    x = Activation('tanh')(x)
    x = Dense(64)(x)
    x = Activation('tanh')(x)
    x = Dense(1)(x)
    x = Activation('linear')(x)  # Since reward=1/distance, it's always going to be positive
    critic = Model(inputs=[action_input, observation_input], outputs=x)
    print(critic.summary())
    return critic


def main(train_test):
    training = False
    get_custom_objects().update({'mylogistic': Activation(mylogistic)})

    while True:
        try:
            env = PointModel2dEnv(verbose=2, success_thres=0.5)
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
        weight_filename = str(c.trained_directory / 'AC_{}_weights.h5f'.format(model_name))

        action_input = Input(shape=(env.action_space.shape[0],), name='action_input')
        actor = my_actor(env)
        critic = my_critic(env, action_input)
        random_process = OrnsteinUhlenbeckProcess(size=nb_actions, theta=.25, mu=0., sigma=.25, dt=1e-1)
        agent = MyDDPGAgent(nb_actions=nb_actions, actor=actor, critic=critic, critic_action_input=action_input,
                          memory=memory, nb_steps_warmup_critic=100, nb_steps_warmup_actor=100,
                          random_process=random_process, gamma=.99, target_model_update=1e-3,
                          )

        agent.compile(Adam(lr=1e-3), metrics=['mae'])
        env.agent = agent
        # pprint.pprint(agent.get_config(False))
        load_weights(agent, weight_filename)

        if train_test == 'train':
            # train code
            training = True
            agent.fit(env, nb_steps=500000, visualize=False, verbose=2, nb_max_episode_steps=2000,
                      nb_max_start_steps=0)
            print('Training complete')
            save_weights(agent, weight_filename)
        elif train_test == 'test':
            # test code
            training = False
            env.log_to_file = False
            agent.test(env, nb_episodes=5, visualize=True, nb_max_start_steps=0)

    except Exception as e:
        if training:
            save_weights(agent, weight_filename)
        print("Error in main code:", str(e))
        env.sock.close()
        raise e


if __name__ == "__main__":
    main('train')



