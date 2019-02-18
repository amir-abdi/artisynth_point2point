from gym.envs.registration import register

register(
    id='SpineEnv-v0',
    entry_point='artisynth.envs:SpineEnv',
    nondeterministic=False
)