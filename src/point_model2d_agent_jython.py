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