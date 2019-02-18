#!/usr/bin/env python
import sys
import os
from absl import app
from absl import flags


flags.DEFINE_string('alg', default='ppo', help='Name of the algorithm')

FLAGS = flags.FLAGS


def main(argv):
    pass


if __name__ == '__main__':
    app.run(main)
