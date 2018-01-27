from artisynth_helper.libraries import *
from consts import *


def set_muscle_excitation(mech_model, name, value):
    value = 1 if value > 1 else value
    value = 0 if value < 0 else value
    mech_model.axialSprings().get(name).setExcitation(value)
    # todo: shall we increase excitation gradually?


def get_follower_position(mech_model):
    return mech_model.rigidBodies().get("body_follower").getPosition()


def get_ref_position(mech_model):
    return mech_model.rigidBodies().get("body_ref").getPosition()


def reward(mech_model):
    # todo: I'm wondering if we should be generating negative reward when the distance is above a certain value
    ref_pos = get_ref_position(mech_model)
    f_pos = get_follower_position(mech_model)
    return 1/(ref_pos.distance(f_pos) + EPSILON)


# we need to create a separate thread in python
# to constantly call get the positions of ref and follower bodies from artisynth

# later we will send these positions to NN, and get the excitations from the NN
# and constantly (in the same thread maybe... or another thread... don't know yet)
# will set the excitations of all the muscles (based on the NN outputs)


import sys
import threading
import time

import numpy

def main():
    artisynth_main = start_artisynth()
    jythonInit.Main = artisynth_main

    print(artisynth_main)
    loadModel('artisynth.models.AHA.rl.PointModel2dRl')

    root_model = artisynth_main.getRootModel()
    mech_model = root_model.models().get("mech")

    print(mech_model.rigidBodies().get("body_follower").getPosition())
    print(mech_model.rigidBodies().get("body_ref").getPosition())

    mech_model.axialSprings().get("n").setExcitation(1)

    play()  # 1.5 seconds

    import time
    time.sleep(1)

    print(mech_model.axialSprings().get("n").getExcitation())
    print('reward: ', reward(mech_model))


if __name__ == '__main__':
    main()

# if __name__ == '__main__':
#     p1 = Process(target = thread1)
#     p1.start()
#     p2 = Process(target = thread2)
#     p2.start()


# def thread2():
#     artisynth_main1 = start_artisynth()
#     jythonInit.Main = artisynth_main1
#
#     print(artisynth_main1)
#     loadModel('artisynth.models.AHA.rl.PointModel2dRl')
#
#     root_model = artisynth_main1.getRootModel()
#     mech_model = root_model.models().get("mech")
#
#     print(mech_model.rigidBodies().get("body_follower").getPosition())
#     print(mech_model.rigidBodies().get("body_ref").getPosition())
#
#     mech_model.axialSprings().get("nne").setExcitation(1)
#
#     play()  # 1.5 seconds
#
#     time.sleep(1)
#
#     print(mech_model.axialSprings().get("nne").getExcitation())
#     print('reward: ', reward(mech_model))
#
#
#
#
#
#
# def thread1():
#     artisynth_main = start_artisynth()  #returns a java class
#     jythonInit.Main = artisynth_main
#
#     print(artisynth_main)
#     loadModel('artisynth.models.AHA.rl.PointModel2dRl')
#
#     root_model = artisynth_main.getRootModel()
#     mech_model = root_model.models().get("mech")
#
#     print(mech_model.rigidBodies().get("body_follower").getPosition())
#     print(mech_model.rigidBodies().get("body_ref").getPosition())
#
#     mech_model.axialSprings().get("n").setExcitation(1)
#
#     play()  # 1.5 seconds
#
#     import time
#     time.sleep(1)
#
#     print(mech_model.axialSprings().get("n").getExcitation())
#     print('reward: ', reward(mech_model))

