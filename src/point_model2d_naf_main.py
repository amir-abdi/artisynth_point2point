from src.import_file import *


def write_summaries(self, model):
    import keras.backend as tf
    sess = tf.InteractiveSession()
    train_writer = tf.summary.FileWriter(tensorboard_log_directory, sess.graph)


def variable_summaries(var):
    """Attach a lot of summaries to a Tensor (for TensorBoard visualization)."""
    with tf.name_scope('summaries'):
        mean = tf.reduce_mean(var)
        tf.summary.scalar('mean', mean)
        with tf.name_scope('stddev'):
            stddev = tf.sqrt(tf.reduce_mean(tf.square(var - mean)))
        tf.summary.scalar('stddev', stddev)
        tf.summary.scalar('max', tf.reduce_max(var))
        tf.summary.scalar('min', tf.reduce_min(var))
        tf.summary.histogram('histogram', var)


def my_V_model(env):
    # Next, we build a very simple model.
    V_model = Sequential()
    V_model.add(Flatten(input_shape=(1,) + env.observation_space.shape, name='FirstFlatten'))
    # variable_summaries(V_model.layers[-1].get_weights())
    V_model.add(Dense(32))
    V_model.add(Activation('relu'))
#    V_model.add(Dense(32))
#    V_model.add(Activation('relu'))
    V_model.add(Dense(1))
    V_model.add(Activation('relu', name='V_final'))
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
    # mu_model.add(Activation('relu'))
    mu_model.add(Activation('sigmoid', name='mu_final'))
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
    x = Activation('linear', name='L_final')(x)
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

        model_name = 'PointModel2D_NAF_sigmoid'
        weight_filename = str(trained_directory / 'AC_{}_weights.h5f'.format(model_name))

        mu_model = my_mu_model(env)
        V_model = my_V_model(env)
        L_model = my_L_model(env)

        random_process = OrnsteinUhlenbeckProcess(size=nb_actions, theta=.35, mu=0., sigma=.35, dt=1e-1)
        processor = PointModel2dProcessor()
        agent = NAFAgent(nb_actions=nb_actions, V_model=V_model, L_model=L_model, mu_model=mu_model,
                         memory=memory,
                         nb_steps_warmup=200,
                         random_process=random_process,
                         gamma=.99,  # discount
                         target_model_update=200,  # 1e-2,
                         processor=processor)

        agent.compile(Adam(lr=1e-3), metrics=['mae'])
        import pprint
        pprint.pprint(agent.get_config(False))
        load_weights(agent, weight_filename)

        tensorboard = MyTensorBoard(
            log_dir=str(tensorboard_log_directory / "{}".format(datetime.now().strftime('%d-%m-%y %H%M'))),
            histogram_freq=1, batch_size=32, write_graph=True,
            write_grads=True, write_images=False, embeddings_freq=0,
            embeddings_layer_names=None, embeddings_metadata=None,
            agent=agent)

        if train_test == 'train':
            # train code
            training = True
            agent.fit(env,
                      nb_steps=5000000,
                      visualize=False,
                      verbose=2,
                      nb_max_episode_steps=200,
                      callbacks=[tensorboard])
            print('Training complete')
            save_weights(agent, weight_filename)
        elif train_test == 'test':
            # test code
            training = False
            env.log_to_file = False
            agent.test(env, nb_episodes=10, visualize=True, nb_max_episode_steps=10)

    except Exception as e:
        if training:
            save_weights(agent, weight_filename)
        print("Error in main code:", str(e))
        env.sock.close()
        raise e


if __name__ == "__main__":
    main('train')



