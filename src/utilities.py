from src.import_file import *


def load_weights(agent, weight_filename):
    import os
    filename_temp, extension = os.path.splitext(weight_filename)
    if Path.exists(Path(filename_temp + '.h5f')) or \
        Path.exists(Path(filename_temp + '_actor.h5f')):
        agent.load_weights(str(weight_filename))
        print('weights loaded from ', str(weight_filename))


def save_weights(agent, weight_filename):
    print('Save weights? (Y|N)')
    answer = input()
    if answer.lower() != 'n':
        agent.save_weights(weight_filename, overwrite=True)
        print('results saved to ', weight_filename)
    else:
        print('weights not saved')


get_custom_objects().update({'mylogistic': Activation(mylogistic)})
