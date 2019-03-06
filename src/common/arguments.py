import argparse

import torch


def get_args():
    parser = argparse.ArgumentParser(description='RL')
    parser.add_argument('--ip', type=str, default='localhost',
                        help='IP of server')
    parser.add_argument('--verbose', type=int, default='20',
                        help='Verbosity level')
    parser.add_argument('--env-name', default='PongNoFrameskip-v4',
                        help='environment to train on (default: PongNoFrameskip-v4)')
    parser.add_argument('--model-name', default='testModel',
                        help='Name of the RL model being trained for logging purposes.')
    parser.add_argument('--load-path', default=None,
                        help='Path to load the trained model.')
    parser.add_argument('--port', type=int, default=4545,
                        help='port to run the server on (default: 4545)')
    parser.add_argument('--visdom-port', type=int, default=8097,
                        help='port to run the server on (default: 8097)')
    parser.add_argument('--episode-log-interval', type=int, default=1,
                        help='log interval for episodes (default: 10)')
    parser.add_argument('--log-interval', type=int, default=10,
                        help='log interval, one log per n updates (default: 10)')
    parser.add_argument('--wait-action', type=float, default=0.0,
                        help='Wait (seconds) for action to take place and environment to stabilize.')
    parser.add_argument('--episodic', action='store_true', default=False,
                        help='Whether task is episodic.')
    parser.add_argument('--test', action='store_true', default=False,
                        help='Only evaluate a trained model.')
    parser.add_argument('--use-wandb', action='store_true', default=False,
                        help='Use wandb for train logging.')
    parser.add_argument('--resume-wandb', action='store_true', default=False,
                        help='Resume the wandb training log.')
    parser.add_argument('--reset-step', type=int, default=-1,
                        help='Reset envs every n iters.')
    parser.add_argument('--hidden-layer-size', type=int, default=64,
                        help='Number of neurons in all hidden layers.')

    parser.add_argument('--algo', default='ppo',
                        help='algorithm to use: a2c | ppo | acktr')
    parser.add_argument('--lr', type=float, default=7e-4,
                        help='learning rate (default: 7e-4)')
    parser.add_argument('--eps', type=float, default=1e-5,
                        help='RMSprop optimizer epsilon (default: 1e-5)')
    parser.add_argument('--alpha', type=float, default=0.99,
                        help='RMSprop optimizer apha (default: 0.99)')
    parser.add_argument('--gamma', type=float, default=0.99,
                        help='discount factor for rewards (default: 0.99)')
    parser.add_argument('--use-gae', action='store_true', default=False,
                        help='use generalized advantage estimation')
    parser.add_argument('--tau', type=float, default=0.95,
                        help='gae parameter (default: 0.95)')
    parser.add_argument('--entropy-coef', type=float, default=0.01,
                        help='entropy term coefficient (default: 0.01)')
    parser.add_argument('--value-loss-coef', type=float, default=0.5,
                        help='value loss coefficient (default: 0.5)')
    parser.add_argument('--max-grad-norm', type=float, default=0.5,
                        help='max norm of gradients (default: 0.5)')
    parser.add_argument('--seed', type=int, default=1,
                        help='random seed (default: 1)')
    parser.add_argument('--cuda-deterministic', action='store_true', default=False,
                        help="sets flags for determinism when using CUDA (potentially slow!)")
    parser.add_argument('--num-processes', type=int, default=1,
                        help='how many training CPU processes to use (default: 16)')
    parser.add_argument('--num-steps', type=int, default=5,
                        help='number of forward steps (default: 5)')
    parser.add_argument('--num-steps-eval', type=int, default=5,
                        help='number of forward steps in evaluation (default: 5)')
    parser.add_argument('--ppo-epoch', type=int, default=4,
                        help='number of ppo epochs (default: 4)')
    parser.add_argument('--num-mini-batch', type=int, default=32,
                        help='number of batches for ppo (default: 32)')
    parser.add_argument('--clip-param', type=float, default=0.2,
                        help='ppo clip parameter (default: 0.2)')
    parser.add_argument('--save-interval', type=int, default=100,
                        help='save interval, one save per n updates (default: 100)')
    parser.add_argument('--eval-interval', type=int, default=100,
                        help='eval interval, one eval per n updates (default: None)')
    parser.add_argument('--vis-interval', type=int, default=100,
                        help='vis interval, one log per n updates (default: 100)')
    parser.add_argument('--num-env-steps', type=int, default=10e6,
                        help='number of environment steps to train (default: 10e6)')
    parser.add_argument('--no-cuda', action='store_true', default=False,
                        help='disables CUDA training')
    parser.add_argument('--add-timestep', action='store_true', default=False,
                        help='add timestep to observations')
    parser.add_argument('--recurrent-policy', action='store_true', default=False,
                        help='use a recurrent policy')
    parser.add_argument('--use-linear-lr-decay', action='store_true', default=False,
                        help='use a linear schedule on the learning rate')
    parser.add_argument('--use-linear-clip-decay', action='store_true', default=False,
                        help='use a linear schedule on the ppo clipping parameter')
    parser.add_argument('--vis', action='store_true', default=False,
                        help='enable visdom visualization')
    args = parser.parse_args()

    args.cuda = not args.no_cuda and torch.cuda.is_available()

    return args
