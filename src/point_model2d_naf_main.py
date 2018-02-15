from src.import_file import *


def my_V_model(env):
    # Next, we build a very simple model.
    V_model = Sequential()
    V_model.add(Flatten(input_shape=(1,) + env.observation_space.shape, name='FirstFlatten'))
    V_model.add(Dense(32))
    V_model.add(Activation('relu'))
#    V_model.add(Dense(32))
#    V_model.add(Activation('relu'))
    V_model.add(Dense(1))
    V_model.add(Activation('relu'))
    #V_model.add(Dense(env.action_space.shape[0]))
    #V_model.add(Activation('relu'))
    print(V_model.summary())
    return V_model


def my_mu_model(env):
    mu_model = Sequential()
    mu_model.add(Flatten(input_shape=(1,) + env.observation_space.shape, name='FirstFlatten'))
#    mu_model.add(Dense(32))
#    mu_model.add(Activation('relu'))
#    mu_model.add(Dense(32))
#    mu_model.add(Activation('relu'))
    mu_model.add(Dense(32))
    mu_model.add(Activation('relu'))
    mu_model.add(Dense(env.action_space.shape[0]))
    mu_model.add(Activation('relu'))
    print(mu_model.summary())
    return mu_model


def my_L_model(env):
    nb_actions = env.action_space.shape[0]
    action_input = Input(shape=(nb_actions,), name='action_input')
    observation_input = Input(shape=(1,) + env.observation_space.shape, name='observation_input')
    x = Concatenate()([action_input, Flatten()(observation_input)])
    x = Dense(32)(x)
    x = Activation('relu')(x)
    x = Dense(32)(x)
    x = Activation('relu')(x)
    x = Dense(32)(x)
    x = Activation('relu')(x)
    x = Dense(((nb_actions * nb_actions + nb_actions) // 2))(x)
    x = Activation('linear')(x)
    L_model = Model(inputs=[action_input, observation_input], outputs=x)
    print(L_model.summary())
    return L_model


class MyNAFAgent(NAFAgent):
    def select_action(self, state):
        batch = self.process_state_batch([state])
        action = self.mu_model.predict_on_batch(batch).flatten()
        assert action.shape == (self.nb_actions,)

        # Apply noise, if a random process is set.
        if self.training and self.random_process is not None:
            noise = self.random_process.sample()
            assert noise.shape == action.shape
            action += noise
            # the below is necessary even if using logistic or sigmoid activations (because of noise)
            action = np.clip(action, 0, 1)  # to avoid using negative and above 1 values for excitations

        return action


def main(train_test='train'):
    get_custom_objects().update({'mylogistic': Activation(mylogistic)})

    while True:
        try:
            env = PointModel2dEnv(verbose=1, success_thres=0.5)
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

        model_name = 'PointModel2D_NAF'
        weight_filename = str(Path.cwd() / trained_directory_path / 'AC_{}_weights.h5f'.
                              format(model_name))

        action_input = Input(shape=(env.action_space.shape[0],), name='action_input')

        mu_model = my_mu_model(env)
        V_model = my_V_model(env)
        L_model = my_L_model(env)

        random_process = OrnsteinUhlenbeckProcess(size=nb_actions, theta=.15, mu=0., sigma=.15, dt=1e-1)

        processor = PointModel2dProcessor()
        agent = NAFAgent(nb_actions=nb_actions, V_model=V_model, L_model=L_model, mu_model=mu_model,
                         memory=memory, nb_steps_warmup=100, random_process=random_process,
                         gamma=.99,
                         target_model_update=100,  # 1e-2,
                         processor=processor)
          
        agent.compile(Adam(lr=1e-4), metrics=['mae'])
        load_weights(agent, weight_filename)

        if train_test == 'train':
            # train code
            agent.fit(env, nb_steps=50000, visualize=True, verbose=2, nb_max_episode_steps=200)
            print('Training complete')
            agent.save_weights(weight_filename, overwrite=True)
            print('results saved to ', weight_filename)
        elif train_test == 'test':
            # test code
            env.log_to_file = False
            agent.test(env, nb_episodes=5, visualize=True)

    except Exception as e:
        agent.save_weights(weight_filename, overwrite=True)
        print('results saved to ', weight_filename)
        print("Error in main code:", str(e))
        env.sock.close()
        raise e


if __name__ == "__main__":
    main('train')



